package com.example.finalapp.ui.screen

import com.example.finalapp.ui.component.GlassCard
import com.example.finalapp.ui.component.PremiumGlassCard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.finalapp.ui.localization.rememberIsTurkish
import com.example.finalapp.ui.localization.translateStatus
import com.example.finalapp.ui.theme.ActivePink
import com.example.finalapp.ui.theme.BackgroundGradient
import com.example.finalapp.ui.theme.ErrorRed
import com.example.finalapp.ui.theme.ForegroundBlack
import com.example.finalapp.ui.theme.PrimaryBlue
import com.example.finalapp.ui.theme.SuccessGreen
import com.example.finalapp.ui.theme.TextMuted
import com.example.finalapp.ui.theme.TextSecondary
import com.example.finalapp.ui.viewmodel.BloodPressureViewModel

@Composable
fun BloodPressureScreen(
    navController: NavController,
    viewModel: BloodPressureViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val isTurkish = rememberIsTurkish()

    fun t(english: String, turkish: String): String = if (isTurkish) turkish else english

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
                    Text(t("Blood Pressure", "Tansiyon"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text(
                        if (uiState.isLoading) t("Loading data...", "Veri yükleniyor...") else t("Detailed analysis", "Detaylı analiz"),
                        fontSize = 13.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            uiState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }

            PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
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
                    Text(t("Latest Reading", "Son Ölçüm"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
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
                            translateStatus(uiState.statusLabel, isTurkish).uppercase(),
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = t("AVG Systolic", "Ort. Sistolik"),
                    value = if (uiState.avgSystolic > 0) "${uiState.avgSystolic.toInt()}" else "--",
                    color = ErrorRed,
                    isTurkish = isTurkish
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = t("AVG Diastolic", "Ort. Diyastolik"),
                    value = if (uiState.avgDiastolic > 0) "${uiState.avgDiastolic.toInt()}" else "--",
                    color = ActivePink,
                    isTurkish = isTurkish
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                t("7-Day Trend", "7 Günlük Trend"),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = ForegroundBlack,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            PremiumGlassCard(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                    when {
                        uiState.isLoading -> CircularProgressIndicator(color = PrimaryBlue)
                        uiState.sysTrendPoints.isEmpty() && uiState.diaTrendPoints.isEmpty() ->
                            Text(t("No recorded data to chart", "Grafik için kayıtlı veri yok"), color = TextMuted, fontWeight = FontWeight.Medium)

                        else -> {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawTrendLine(
                                    points = uiState.sysTrendPoints,
                                    minVal = uiState.sysTrendMin,
                                    maxVal = uiState.sysTrendMax,
                                    color = ErrorRed,
                                    strokeWidth = 4.dp.toPx()
                                )
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

            if (uiState.sysTrendPoints.isNotEmpty() || uiState.diaTrendPoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(start = 12.dp)) {
                    LegendDot(color = ErrorRed, label = t("Systolic", "Sistolik"))
                    LegendDot(color = ActivePink, label = t("Diastolic", "Diyastolik"))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

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
                        Text(t("Healthy Tip", "Sağlık İpucu"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                        Text(
                            t("Consistent readings help track your health effectively.", "Düzenli ölçümler sağlığını daha iyi takip etmene yardımcı olur."),
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

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
fun StatCard(modifier: Modifier, title: String, value: String, color: Color, isTurkish: Boolean) {
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .border(1.dp, Color.White, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
            Text(if (isTurkish) "mmHg" else "mmHg", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = TextMuted)
        }
    }
}
