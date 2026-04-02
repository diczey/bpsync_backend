package com.example.finalapp.ui.screen

import com.example.finalapp.ui.component.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.finalapp.data.model.TrendDataPoint
import com.example.finalapp.ui.theme.*
import com.example.finalapp.ui.viewmodel.BloodPressureViewModel

@Composable
fun BloodPressureScreen(
    navController: NavController,
    viewModel: BloodPressureViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    // Derive status color
    val statusColor = when {
        uiState.statusLabel.contains("normal", ignoreCase = true) -> SuccessGreen
        uiState.statusLabel.contains("alert", ignoreCase = true) -> ErrorRed
        else -> TextSecondary
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryBlue)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Blood Pressure", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text(
                        if (uiState.isLoading) "Loading data…" else "Detailed analysis",
                        fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Error banner
            uiState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }

            // Main Reading Card
            PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(ErrorRed.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = ErrorRed, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Latest Reading", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (uiState.isLoading) {
                        CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(40.dp))
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val sysText = if (uiState.latestSystolic > 0) "${uiState.latestSystolic}" else "--"
                            val diaText = if (uiState.latestDiastolic > 0) "${uiState.latestDiastolic}" else "--"
                            Text(sysText, fontSize = 56.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                            Text("/", fontSize = 48.sp, color = TextMuted, modifier = Modifier.padding(horizontal = 8.dp))
                            Text(diaText, fontSize = 56.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                            Text("mmHg", fontSize = 14.sp, color = TextMuted, modifier = Modifier.padding(start = 8.dp, top = 20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            uiState.statusLabel.uppercase(),
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Stats Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "AVG Systolic",
                    value = if (uiState.avgSystolic > 0) "${uiState.avgSystolic.toInt()}" else "--",
                    color = ErrorRed
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "AVG Diastolic",
                    value = if (uiState.avgDiastolic > 0) "${uiState.avgDiastolic.toInt()}" else "--",
                    color = ActivePink
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 7-Day Trend Chart
            Text("7-Day Trend", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack, modifier = Modifier.padding(start = 4.dp))
            Spacer(modifier = Modifier.height(12.dp))

            PremiumGlassCard(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                    when {
                        uiState.isLoading -> CircularProgressIndicator(color = PrimaryBlue)
                        uiState.sysTrendPoints.isEmpty() && uiState.diaTrendPoints.isEmpty() ->
                            Text("No recorded data to chart", color = TextMuted, fontWeight = FontWeight.Medium)
                        else -> {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Draw systolic line (red)
                                drawTrendLine(
                                    points = uiState.sysTrendPoints,
                                    minVal = uiState.sysTrendMin,
                                    maxVal = uiState.sysTrendMax,
                                    color = ErrorRed,
                                    strokeWidth = 4.dp.toPx()
                                )
                                // Draw diastolic line (pink)
                                drawTrendLine(
                                    points = uiState.diaTrendPoints,
                                    minVal = uiState.diaTrendMin,
                                    maxVal = uiState.diaTrendMax,
                                    color = ActivePink,
                                    strokeWidth = 3.dp.toPx()
                                )
                            }
                        }
                    }
                }
            }

            // Legend
            if (uiState.sysTrendPoints.isNotEmpty() || uiState.diaTrendPoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(start = 12.dp)) {
                    LegendDot(color = ErrorRed, label = "Systolic")
                    LegendDot(color = ActivePink, label = "Diastolic")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Health Insights
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(PrimaryBlue.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                            .padding(10.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryBlue)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Healthy Tip", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                        Text(
                            "Consistent readings help track your health effectively.",
                            fontSize = 12.sp, color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// Canvas extension to draw a line chart on the existing DrawScope
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTrendLine(
    points: List<TrendDataPoint>,
    minVal: Float,
    maxVal: Float,
    color: Color,
    strokeWidth: Float
) {
    if (points.isEmpty() || maxVal <= minVal) return
    val range = maxVal - minVal
    val step = if (points.size > 1) size.width / (points.size - 1) else size.width
    val path = Path()
    points.forEachIndexed { i, p ->
        val x = i * step
        val y = size.height - ((p.value - minVal) / range * size.height).coerceIn(0f, size.height)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(50)))
        Text(label, fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StatCard(modifier: Modifier, title: String, value: String, color: Color) {
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .border(1.dp, Color.White, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
            Text("mmHg", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = TextMuted)
        }
    }
}
