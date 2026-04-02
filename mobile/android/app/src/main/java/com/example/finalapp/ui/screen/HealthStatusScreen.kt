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

@Composable
fun HealthStatusScreen(navController: NavController) {
    var days by remember { mutableStateOf(2) }
    var hours by remember { mutableStateOf(23) }
    var minutes by remember { mutableStateOf(45) }
    var seconds by remember { mutableStateOf(30) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (seconds > 0) seconds--
            else if (minutes > 0) { minutes--; seconds = 59 }
            else if (hours > 0) { hours--; minutes = 59; seconds = 59 }
            else if (days > 0) { days--; hours = 23; minutes = 59; seconds = 59 }
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
                    Text("Time Until Check-In", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text("Stay healthy with regular monitoring", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                         CountdownItem(modifier = Modifier.weight(1f), value = days.toString(), label = "Days")
                         CountdownItem(modifier = Modifier.weight(1f), value = hours.toString().padStart(2, '0'), label = "Hours")
                         CountdownItem(modifier = Modifier.weight(1f), value = minutes.toString().padStart(2, '0'), label = "Mins")
                         CountdownItem(modifier = Modifier.weight(1f), value = seconds.toString().padStart(2, '0'), label = "Secs")
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
                            Text("All metrics normal", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    StatusRow(label = "Blood Pressure", status = "Normal", color = SuccessGreen)
                    StatusRow(label = "Heart Rate", status = "Normal", color = SuccessGreen)
                    StatusRow(label = "Oxygen Level", status = "Normal", color = SuccessGreen)
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
