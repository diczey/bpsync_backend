package com.example.bp.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bp.data.model.DailyBP
import com.example.bp.data.model.WeeklyReport
import com.example.bp.ui.theme.*
import com.example.bp.ui.viewmodel.ReportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    token: String,
    viewModel: ReportViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var isMonthly by remember { mutableStateOf(false) }

    LaunchedEffect(token, isMonthly) {
        viewModel.loadReport(token, isMonthly)
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("Sağlık Raporu", color = TextPrimary, fontWeight = FontWeight.Bold) },
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
            contentPadding = PaddingValues(bottom = 24.dp, top = 16.dp)
        ) {
            // Period Selector
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDark, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val periods = listOf(false to "Haftalık", true to "Aylık")
                    periods.forEach { (monthly, label) ->
                        val isSelected = isMonthly == monthly
                        Button(
                            onClick = { isMonthly = monthly },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) BPBlue else Color.Transparent,
                                contentColor = if (isSelected) Color.White else TextSecondary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            elevation = null
                        ) {
                            Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BPBlue)
                    }
                }
            } else {
                val report = uiState.report
                if (report != null) {
                    item { HealthScoreCard(report.healthScore) }
                    
                    item {
                        Text(
                            "Dönem Özeti (${report.weekStart.split(" ")[0]} - ${report.weekEnd.split(" ")[0]})",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    item { 
                        ReportSummaryStats(report)
                    }

                    item {
                        Text(
                            "Günlük Detaylar",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(report.dailySummaries) { daily ->
                        DailyReportCard(daily)
                    }
                } else {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                            Text(uiState.error ?: "Bu dönem için rapor bulunamadı.", color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HealthScoreCard(score: Int) {
    val scoreColor = when {
        score >= 80 -> BPGreen
        score >= 60 -> BPOrange
        else -> BPRed
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Row(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(scoreColor.copy(alpha = 0.15f), Color.Transparent)))
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Genel Sağlık Puanı", color = TextSecondary, fontSize = 14.sp)
                Text(
                    text = when {
                        score >= 80 -> "Mükemmel"
                        score >= 60 -> "Dikkat Edilmeli"
                        else -> "Riskli"
                    },
                    color = scoreColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
            }
            
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { score / 100f },
                    modifier = Modifier.size(80.dp),
                    color = scoreColor,
                    strokeWidth = 8.dp,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
                Text(score.toString(), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun ReportSummaryStats(report: WeeklyReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SummaryItem("Ort. Tansiyon", "${report.avgSystolic?.toInt() ?: "--"}/${report.avgDiastolic?.toInt() ?: "--"}", "mmHg")
            SummaryItem("Ort. Nabız", "${report.avgHeartRate?.toInt() ?: "--"}", "bpm")
            SummaryItem("Ölçüm Sayısı", "${report.readingsCount}", "adet")
        }
    }
}

@Composable
fun SummaryItem(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextHint, fontSize = 11.sp)
        Text(value, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(unit, color = TextSecondary, fontSize = 10.sp)
    }
}

@Composable
fun DailyReportCard(daily: DailyBP) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(BPBlueDark),
                contentAlignment = Alignment.Center
            ) {
                Text(daily.date.takeLast(2), color = BPBlueLight, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(daily.date, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("${daily.readingCount} ölçüm yapıldı", color = TextHint, fontSize = 12.sp)
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text("${daily.avgSystolic?.toInt() ?: "--"}/${daily.avgDiastolic?.toInt() ?: "--"}", color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("mmHg", color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}
