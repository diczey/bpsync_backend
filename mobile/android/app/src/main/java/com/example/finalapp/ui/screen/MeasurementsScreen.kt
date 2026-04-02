package com.example.finalapp.ui.screen

import com.example.finalapp.ui.component.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.finalapp.ui.theme.*
import com.example.finalapp.data.repository.ReadingRepository

@Composable
fun MeasurementsScreen(navController: NavController) {
    val readings = ReadingRepository.readings
    
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
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryBlue)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("All Measurements", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text("Complete history", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Summary row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryItem(modifier = Modifier.weight(1f), label = "Total", value = readings.size.toString(), color = PrimaryBlue)
                SummaryItem(modifier = Modifier.weight(1f), label = "Normal", value = readings.count { it.status == "Normal" }.toString(), color = SuccessGreen)
                SummaryItem(modifier = Modifier.weight(1f), label = "Alert", value = readings.count { it.status != "Normal" }.toString(), color = OrangeMain)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // History List
            if (readings.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No measurements yet. Start on Dashboard!", color = TextMuted, fontWeight = FontWeight.Bold)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(readings) { reading ->
                        MeasurementCard(
                            date = reading.date,
                            time = reading.time,
                            systolic = reading.systolic,
                            diastolic = reading.diastolic,
                            pulse = reading.pulse,
                            status = reading.status
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
fun MeasurementCard(date: String, time: String, systolic: String, diastolic: String, pulse: String, status: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(28.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp)) 
                    // Note: Would use a calendar icon if matched, using ArrowBack for quick placeholder parity
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
                        .background(if (status == "Normal") SuccessGreen.copy(alpha = 0.1f) else OrangeMain.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(status, color = if (status == "Normal") SuccessGreen else OrangeMain, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
