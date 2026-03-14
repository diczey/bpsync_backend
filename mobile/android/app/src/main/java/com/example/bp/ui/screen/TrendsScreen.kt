package com.example.bp.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bp.data.model.TrendDataDto
import com.example.bp.data.model.TrendDataPoint
import com.example.bp.ui.theme.*
import com.example.bp.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    token: String,
    viewModel: DashboardViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var selectedPeriod by remember { mutableStateOf("week") }

    LaunchedEffect(token, selectedPeriod) {
        viewModel.loadTrends(token, selectedPeriod)
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("Sağlık Trendleri", color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = TextPrimary)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(12.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val periods = listOf("day" to "Gün", "week" to "Hafta", "month" to "Ay")
                    periods.forEach { (key, label) ->
                        val isSelected = selectedPeriod == key
                        Button(
                            onClick = { selectedPeriod = key },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) BPBlue else Color.Transparent,
                                contentColor = if (isSelected) Color.White else TextSecondary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp),
                            elevation = null
                        ) {
                            Text(label, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BPBlue)
                    }
                }
                return@LazyColumn
            }

            uiState.trends?.let { trends ->
                items(trends) { trend ->
                    TrendChartCard(trend)
                }
            } ?: item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 100.dp), contentAlignment = Alignment.Center) {
                    Text("Henüz trend verisi yok.", color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun TrendChartCard(trend: TrendDataDto) {
    val color = when (trend.type.lowercase()) {
        "systolic" -> BPRed
        "diastolic" -> BPBlue
        "heart_rate" -> BPOrange
        "spo2" -> BPGreen
        else -> BPBlue
    }

    val typeLabel = when (trend.type.lowercase()) {
        "systolic" -> "Sistolik Tansiyon"
        "diastolic" -> "Diastolik Tansiyon"
        "heart_rate" -> "Nabız hızı"
        "spo2" -> "Oksijen Doygunluğu (SpO2)"
        else -> trend.type
    }

    val unit = when (trend.type.lowercase()) {
        "systolic", "diastolic" -> "mmHg"
        "heart_rate" -> "bpm"
        "spo2" -> "%"
        else -> ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(typeLabel, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Ortalama: ${trend.average.toInt()} $unit", color = TextSecondary, fontSize = 12.sp)
                }
                Text("${trend.min.toInt()} - ${trend.max.toInt()} $unit", color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Simple Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(CardDark, RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                if (trend.dataPoints.size > 1) {
                    SimpleLineChart(
                        points = trend.dataPoints,
                        color = color,
                        minVal = trend.min,
                        maxVal = trend.max
                    )
                } else {
                    Text("Yeterli veri yok", color = TextHint, modifier = Modifier.align(Alignment.Center), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SimpleLineChart(
    points: List<TrendDataPoint>,
    color: Color,
    minVal: Double,
    maxVal: Double
) {
    val range = if (maxVal == minVal) 1.0 else maxVal - minVal
    // Padding for chart
    val verticalPadding = 0.1 * range
    val effectiveMin = minVal - verticalPadding
    val effectiveMax = maxVal + verticalPadding
    val effectiveRange = effectiveMax - effectiveMin

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val stepX = width / (points.size - 1)
        
        val p = Path()
        points.forEachIndexed { index, point ->
            val x = index * stepX
            val y = height - ((point.value - effectiveMin) / effectiveRange * height).toFloat()
            if (index == 0) p.moveTo(x, y) else p.lineTo(x, y)
            
            // Draw points
            drawCircle(color = color, radius = 4f, center = Offset(x, y))
        }
        
        drawPath(
            path = p,
            color = color.copy(alpha = 0.8f),
            style = Stroke(width = 4f)
        )
    }
}
