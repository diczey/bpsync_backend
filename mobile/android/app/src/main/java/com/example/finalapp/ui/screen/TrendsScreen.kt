package com.example.finalapp.ui.screen

import com.example.finalapp.ui.component.*


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.finalapp.ui.theme.*

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finalapp.data.model.TrendDataDto
import com.example.finalapp.ui.localization.rememberIsTurkish
import com.example.finalapp.ui.localization.translateMessage
import com.example.finalapp.ui.viewmodel.TrendsViewModel
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TrendsScreen(
    navController: NavController,
    viewModel: TrendsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val periods = listOf("Daily", "Weekly", "Monthly")
    val isTurkish = rememberIsTurkish()

    fun t(english: String, turkish: String): String = if (isTurkish) turkish else english
    fun periodLabel(period: String): String = when (period) {
        "Daily" -> t("Daily", "Günlük")
        "Weekly" -> t("Weekly", "Haftalık")
        "Monthly" -> t("Monthly", "Aylık")
        else -> period
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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
                    Icon(Icons.Default.ArrowBack, contentDescription = t("Back", "Geri"), tint = PrimaryBlue)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(t("Health Trends", "Sağlık Trendleri"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text(if(uiState.isLoading) t("Loading data...", "Veri yükleniyor...") else t("Historical analysis", "Geçmiş analiz"), fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Period Selector
            GlassCard(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                Row(modifier = Modifier.padding(4.dp)) {
                    for (period in periods) {
                        val isSelected = uiState.selectedPeriod.equals(period, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) PrimaryBlue else Color.Transparent)
                                .clickable { viewModel.setPeriod(period) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                periodLabel(period),
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            uiState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    translateMessage(error, isTurkish),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(8.dp)
                )
            }

            uiState.infoMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = translateMessage(message, isTurkish),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // Extract specific trends from backend DTOs
            val sysTrend = uiState.trends.find { it.type == "systolic" }
            val diaTrend = uiState.trends.find { it.type == "diastolic" }
            val hrTrend = uiState.trends.find { it.type == "heart_rate" }
            // Trend Charts
            TrendChartSection(title = t("Systolic Pressure", "Sistolik Basınç"), icon = Icons.Default.Favorite, color = ErrorRed, unit = "mmHg", trend = sysTrend, isTurkish = isTurkish)
            Spacer(modifier = Modifier.height(20.dp))
            TrendChartSection(title = t("Diastolic Pressure", "Diyastolik Basınç"), icon = Icons.Default.FavoriteBorder, color = OrangeMain, unit = "mmHg", trend = diaTrend, isTurkish = isTurkish)
            Spacer(modifier = Modifier.height(20.dp))
            TrendChartSection(title = t("Heart Rate", "Kalp Atışı"), icon = Icons.Default.FavoriteBorder, color = ActivePink, unit = "BPM", trend = hrTrend, isTurkish = isTurkish)

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun TrendChartSection(title: String, icon: ImageVector, color: Color, unit: String, trend: TrendDataDto?, isTurkish: Boolean) {
    val points = trend?.dataPoints.orEmpty()
    var chartWidth by remember(trend?.type, points.size) { mutableStateOf(0f) }
    var selectedIndex by remember(trend?.type, points.size) {
        mutableStateOf(points.lastIndex.takeIf { it >= 0 })
    }
    val selectedPoint = selectedIndex?.let(points::getOrNull)

    fun t(english: String, turkish: String): String = if (isTurkish) turkish else english

    PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(8.dp)) {
                        Icon(icon, contentDescription = null, tint = color)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                        Text("${t("Avg", "Ort")}: ${trend?.average ?: "--"} $unit", fontSize = 11.sp, color = TextSecondary)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = selectedPoint?.let { "${formatTrendValue(it.value)} $unit" } ?: "--",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForegroundBlack
                    )
                    Text(
                        text = selectedPoint?.let { formatTrendTimestamp(it.timestamp) } ?: t("Tap chart", "Grafiğe dokun"),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                if (trend != null && points.isNotEmpty()) {
                    val minPointValue = points.minOf { it.value }
                    val maxPointValue = points.maxOf { it.value }
                    val paddingValue = ((maxPointValue - minPointValue) * 0.15f).coerceAtLeast(1f)
                    val minVal = minPointValue - paddingValue
                    val maxVal = maxPointValue + paddingValue
                    val valueRange = (maxVal - minVal).coerceAtLeast(1f)

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .onSizeChanged { chartWidth = it.width.toFloat() }
                            .pointerInput(points) {
                                detectTapGestures { offset ->
                                    selectedIndex = nearestTrendPointIndex(
                                        tapX = offset.x,
                                        chartWidth = chartWidth,
                                        pointCount = points.size
                                    )
                                }
                            }
                    ) {
                        val widthOffset = size.width / if (points.size > 1) (points.size - 1) else 1

                        repeat(3) { step ->
                            val y = size.height * step / 2f
                            drawLine(
                                color = TextMuted.copy(alpha = 0.25f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f))
                            )
                        }

                        val path = Path()
                        points.forEachIndexed { index, point ->
                            val x = index * widthOffset
                            val y = size.height - ((point.value - minVal) / valueRange * size.height)
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }

                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(
                                width = 4.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        points.forEachIndexed { index, point ->
                            val x = index * widthOffset
                            val y = size.height - ((point.value - minVal) / valueRange * size.height)
                            val isSelected = index == selectedIndex

                            drawCircle(
                                color = Color.White,
                                radius = if (isSelected) 8.dp.toPx() else 5.dp.toPx(),
                                center = Offset(x, y)
                            )
                            drawCircle(
                                color = color,
                                radius = if (isSelected) 5.dp.toPx() else 3.dp.toPx(),
                                center = Offset(x, y)
                            )
                        }

                        selectedIndex?.let { index ->
                            val selectedPointValue = points.getOrNull(index) ?: return@let
                            val selectedX = index * widthOffset
                            val selectedY = size.height - ((selectedPointValue.value - minVal) / valueRange * size.height)

                            drawLine(
                                color = color.copy(alpha = 0.35f),
                                start = Offset(selectedX, 0f),
                                end = Offset(selectedX, size.height),
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                            )

                            drawCircle(
                                color = color,
                                radius = 7.dp.toPx(),
                                center = Offset(selectedX, selectedY)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3.dp.toPx(),
                                center = Offset(selectedX, selectedY)
                            )
                        }
                    }
                } else {
                    Text(t("No recorded data to chart", "Grafik için kayıtlı veri yok"), color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (points.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTrendAxisLabel(points.first().timestamp),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = t("Tap a point to inspect", "İncelemek için noktaya dokun"),
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Text(
                        text = formatTrendAxisLabel(points.last().timestamp),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

private fun nearestTrendPointIndex(tapX: Float, chartWidth: Float, pointCount: Int): Int {
    if (pointCount <= 1 || chartWidth <= 0f) return 0

    val step = chartWidth / (pointCount - 1)
    val normalized = tapX.coerceIn(0f, chartWidth)
    return (normalized / step).roundToInt().coerceIn(0, pointCount - 1)
}

private fun formatTrendTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return formatter.format(Date(normalizeTrendTimestamp(timestamp)))
}

private fun formatTrendAxisLabel(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM", Locale.getDefault())
    return formatter.format(Date(normalizeTrendTimestamp(timestamp)))
}

private fun formatTrendValue(value: Float): String {
    val rounded = value.roundToInt()
    return if (abs(value - rounded) < 0.05f) {
        rounded.toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", value)
    }
}

private fun normalizeTrendTimestamp(timestamp: Long): Long {
    return if (timestamp < 1_000_000_000_000L) timestamp * 1000 else timestamp
}
