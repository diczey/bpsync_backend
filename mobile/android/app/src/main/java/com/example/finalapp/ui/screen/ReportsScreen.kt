package com.example.finalapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.finalapp.data.model.DailyReportDto
import com.example.finalapp.ui.component.GlassCard
import com.example.finalapp.ui.component.PremiumGlassCard
import com.example.finalapp.ui.localization.rememberIsTurkish
import com.example.finalapp.ui.theme.BackgroundGradient
import com.example.finalapp.ui.theme.ForegroundBlack
import com.example.finalapp.ui.theme.OrangeMain
import com.example.finalapp.ui.theme.PrimaryBlue
import com.example.finalapp.ui.theme.SuccessGreen
import com.example.finalapp.ui.theme.TextSecondary
import com.example.finalapp.ui.viewmodel.ReportsViewModel

@Composable
fun ReportsScreen(
    navController: NavController,
    viewModel: ReportsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isTurkish = rememberIsTurkish()

    fun t(english: String, turkish: String): String = if (isTurkish) turkish else english

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryBlue)
                }
                Spacer(modifier = Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(t("Reports", "Raporlar"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text(t("Weekly and monthly summaries", "Haftalık ve aylık özetler"), fontSize = 12.sp, color = TextSecondary)
                }
                TextButton(onClick = viewModel::refresh) {
                    Text(t("Refresh", "Yenile"), color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ReportRangeButton(
                    modifier = Modifier.weight(1f),
                    label = t("Weekly", "Haftalık"),
                    selected = uiState.selectedRange == "weekly",
                    onClick = { viewModel.selectRange("weekly") }
                )
                ReportRangeButton(
                    modifier = Modifier.weight(1f),
                    label = t("Monthly", "Aylık"),
                    selected = uiState.selectedRange == "monthly",
                    onClick = { viewModel.selectRange("monthly") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            uiState.errorMessage?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            uiState.message?.let { message ->
                Text(
                    when {
                        isTurkish && message == "No data for this week." -> "Bu hafta için veri yok."
                        isTurkish && message == "No data for this month." -> "Bu ay için veri yok."
                        else -> message
                    },
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            uiState.report?.let { report ->
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(PrimaryBlue.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = PrimaryBlue)
                            }
                            Spacer(modifier = Modifier.size(12.dp))
                            Column {
                                Text(t("Report Window", "Rapor Aralığı"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                                Text("${report.weekStart} - ${report.weekEnd}", fontSize = 12.sp, color = TextSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            ReportMetricCard(
                                modifier = Modifier.weight(1f),
                                title = t("Score", "Puan"),
                                value = report.healthScore.toString(),
                                accent = SuccessGreen
                            )
                            ReportMetricCard(
                                modifier = Modifier.weight(1f),
                                title = t("Readings", "Ölçümler"),
                                value = report.readingsCount.toString(),
                                accent = PrimaryBlue
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            ReportMetricCard(
                                modifier = Modifier.weight(1f),
                                title = t("Avg SYS", "Ort. SYS"),
                                value = report.avgSystolic?.toString() ?: "--",
                                accent = OrangeMain
                            )
                            ReportMetricCard(
                                modifier = Modifier.weight(1f),
                                title = t("Avg DIA", "Ort. DIA"),
                                value = report.avgDiastolic?.toString() ?: "--",
                                accent = Color(0xFFB91C1C)
                            )
                            ReportMetricCard(
                                modifier = Modifier.weight(1f),
                                title = t("Avg HR", "Ort. Nabız"),
                                value = report.avgHeartRate?.toString() ?: "--",
                                accent = Color(0xFFDB2777)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(t("Daily Summary", "Günlük Özet"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                Spacer(modifier = Modifier.height(12.dp))

                report.dailySummaries.forEach { summary ->
                    DailySummaryCard(summary, isTurkish)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun ReportRangeButton(modifier: Modifier, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(if (selected) PrimaryBlue else Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else ForegroundBlack, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ReportMetricCard(modifier: Modifier, title: String, value: String, accent: Color) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontSize = 20.sp, color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DailySummaryCard(summary: DailyReportDto, isTurkish: Boolean) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(summary.date, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${if (isTurkish) "SYS" else "SYS"} ${summary.avgSystolic ?: "--"}  ${if (isTurkish) "DIA" else "DIA"} ${summary.avgDiastolic ?: "--"}  ${if (isTurkish) "NABIZ" else "HR"} ${summary.avgHeartRate ?: "--"}",
                fontSize = 13.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                if (isTurkish) "Ölçüm: ${summary.readingCount}" else "Readings: ${summary.readingCount}",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}
