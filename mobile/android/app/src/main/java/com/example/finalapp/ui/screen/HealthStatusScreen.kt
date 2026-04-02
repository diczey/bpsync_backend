package com.example.finalapp.ui.screen

import com.example.finalapp.ui.component.*


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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.finalapp.ui.navigation.Screen
import com.example.finalapp.ui.theme.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finalapp.ui.viewmodel.DashboardViewModel

@Composable
fun HealthStatusScreen(
    navController: NavController,
    viewModel: DashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var dateInput by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(0L) }
    var hours by remember { mutableStateOf(0L) }
    var minutes by remember { mutableStateOf(0L) }
    var seconds by remember { mutableStateOf(0L) }

    val checkupStr = uiState.lastCheckupDate

    LaunchedEffect(checkupStr) {
        if (!checkupStr.isNullOrBlank()) {
            try {
                val lastDate = LocalDate.parse(checkupStr).atStartOfDay()
                while (true) {
                    val now = LocalDateTime.now()
                    val diffSeconds = ChronoUnit.SECONDS.between(lastDate, now)
                    if (diffSeconds > 0) {
                        days = diffSeconds / (24 * 3600)
                        hours = (diffSeconds % (24 * 3600)) / 3600
                        minutes = (diffSeconds % 3600) / 60
                        seconds = diffSeconds % 60
                    } else {
                        days = 0L; hours = 0L; minutes = 0L; seconds = 0L
                    }
                    delay(1000)
                }
            } catch (e: Exception) {
                // Invalid date
            }
        }
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryBlue)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Health Status", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text("Next check-in countdown", fontSize = 13.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Countdown Card
            PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.size(64.dp).background(PrimaryGradient, RoundedCornerShape(24.dp)).padding(16.dp)) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Time Since Last Check-In", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text("Stay consistent with regular check-ups", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)

                    Spacer(modifier = Modifier.height(32.dp))

                    if (checkupStr.isNullOrBlank()) {
                        Text("Please enter your last checkup date:", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ForegroundBlack)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = dateInput,
                            onValueChange = { dateInput = it },
                            placeholder = { Text("YYYY-MM-DD") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.updateLastCheckupDate(dateInput) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text(if (uiState.isLoading) "Saving..." else "Save Date", fontWeight = FontWeight.Bold)
                        }
                        if (uiState.errorMessage != null && uiState.errorMessage!!.contains("save date", ignoreCase = true)) {
                            Text(uiState.errorMessage!!, color = ErrorRed, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CountdownItem(modifier = Modifier.weight(1f), value = days.toString(), label = "Days")
                            CountdownItem(modifier = Modifier.weight(1f), value = hours.toString().padStart(2, '0'), label = "Hours")
                            CountdownItem(modifier = Modifier.weight(1f), value = minutes.toString().padStart(2, '0'), label = "Mins")
                            CountdownItem(modifier = Modifier.weight(1f), value = seconds.toString().padStart(2, '0'), label = "Secs")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Current Status Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(44.dp).background(SuccessGreen.copy(alpha = 0.1f), RoundedCornerShape(16.dp)).padding(10.dp)) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = SuccessGreen)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Current Status", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                            Text(if (uiState.isLoading) "Loading metrics..." else "Based on latest readings", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    val sys = uiState.systolic.toIntOrNull() ?: 0
                    val dia = uiState.diastolic.toIntOrNull() ?: 0
                    val bpStatus = when {
                        sys == 0 || dia == 0 -> "No Data" to TextSecondary
                        sys < 120 && dia < 80 -> "Normal" to SuccessGreen
                        sys in 120..129 && dia < 80 -> "Elevated" to OrangeMain
                        sys in 130..139 || dia in 80..89 -> "Stage 1 High" to ErrorRed
                        sys >= 140 || dia >= 90 -> "Stage 2 High" to ErrorRed
                        else -> "Critical" to ErrorRed
                    }

                    val pulse = uiState.pulse.toIntOrNull() ?: 0
                    val hrStatus = when {
                        pulse == 0 -> "No Data" to TextSecondary
                        pulse in 60..100 -> "Normal" to SuccessGreen
                        pulse < 60 -> "Low" to ActivePink
                        else -> "High" to ErrorRed
                    }

                    val spo2 = uiState.spo2.toIntOrNull() ?: 0
                    val spo2Status = when {
                        spo2 == 0 -> "No Data" to TextSecondary
                        spo2 >= 95 -> "Normal" to PrimaryBlue
                        spo2 in 90..94 -> "Low" to OrangeMain
                        else -> "Critical" to ErrorRed
                    }
                    
                    StatusRow(label = "Blood Pressure", status = bpStatus.first, color = bpStatus.second)
                    StatusRow(label = "Heart Rate", status = hrStatus.first, color = hrStatus.second)
                    StatusRow(label = "Oxygen Level", status = spo2Status.first, color = spo2Status.second)
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))

            // Health Tips
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(44.dp).background(PrimaryBlue.copy(alpha = 0.1f), RoundedCornerShape(16.dp)).padding(10.dp)) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryBlue)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Health Tips", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("• Regular monitoring helps detect health issues early.", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 4.dp))
                    Text("• Maintain a balanced diet and exercise routine.", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 4.dp))
                    Text("• Stay hydrated and get adequate sleep.", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 4.dp))
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun CountdownItem(modifier: Modifier, value: String, label: String) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.6f))
            .border(1.dp, Color.White, RoundedCornerShape(20.dp))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun StatusRow(label: String, status: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(color.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ForegroundBlack)
            Text(status, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
