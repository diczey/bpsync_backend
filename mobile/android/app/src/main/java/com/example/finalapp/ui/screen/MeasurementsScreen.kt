package com.example.finalapp.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import com.example.finalapp.ui.localization.rememberIsTurkish
import com.example.finalapp.ui.localization.translateStatus
import com.example.finalapp.ui.theme.BackgroundGradient
import com.example.finalapp.ui.theme.ForegroundBlack
import com.example.finalapp.ui.theme.OrangeMain
import com.example.finalapp.ui.theme.PrimaryBlue
import com.example.finalapp.ui.theme.SuccessGreen
import com.example.finalapp.ui.theme.TextMuted
import com.example.finalapp.ui.theme.TextSecondary
import com.example.finalapp.ui.viewmodel.MeasurementsViewModel

@Composable
fun MeasurementsScreen(
    navController: NavController,
    viewModel: MeasurementsViewModel = viewModel()
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
                    Text(t("All Measurements", "Tüm Ölçümler"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text(t("Complete history", "Tüm geçmiş"), fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SummaryItem(modifier = Modifier.weight(1f), label = t("Total", "Toplam"), value = uiState.totalCount.toString(), color = PrimaryBlue)
                SummaryItem(modifier = Modifier.weight(1f), label = t("Normal", "Normal"), value = uiState.normalCount.toString(), color = SuccessGreen)
                SummaryItem(modifier = Modifier.weight(1f), label = t("Alert", "Uyarı"), value = uiState.alertCount.toString(), color = OrangeMain)
            }

            Spacer(modifier = Modifier.height(16.dp))

            uiState.errorMessage?.let { error ->
                Text(text = error, color = androidx.compose.material3.MaterialTheme.colorScheme.error, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (uiState.isLoading && uiState.readings.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(t("Loading measurements...", "Ölçümler yükleniyor..."), color = TextMuted, fontWeight = FontWeight.Bold)
                }
            } else if (uiState.readings.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(t("No measurements yet. Start on Dashboard!", "Henüz ölçüm yok. Kontrol panelinden başla!"), color = TextMuted, fontWeight = FontWeight.Bold)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.readings, key = { it.id }) { reading ->
                        MeasurementCard(
                            date = reading.date,
                            time = reading.time,
                            systolic = reading.systolic,
                            diastolic = reading.diastolic,
                            pulse = reading.pulse,
                            status = translateStatus(reading.status, isTurkish),
                            isTurkish = isTurkish
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryItem(modifier: Modifier, label: String, value: String, color: Color) {
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun MeasurementCard(date: String, time: String, systolic: String, diastolic: String, pulse: String, status: String, isTurkish: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(28.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(date, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                }
                Text(time, fontSize = 12.sp, color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("BP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Text("$systolic/$diastolic", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("BPM", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Text(pulse, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                }
                Box(
                    modifier = Modifier
                        .background(
                            if (status == (if (isTurkish) "Normal" else "Normal")) SuccessGreen.copy(alpha = 0.1f) else OrangeMain.copy(alpha = 0.1f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        status,
                        color = if (status == (if (isTurkish) "Normal" else "Normal")) SuccessGreen else OrangeMain,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
