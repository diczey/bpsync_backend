package com.example.finalapp.ui.screen

import com.example.finalapp.ui.component.*


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.example.finalapp.ui.theme.*

@Composable
fun TrendsScreen(navController: NavController) {
    var selectedPeriod by remember { mutableStateOf("Weekly") }
    val periods = listOf("Daily", "Weekly", "Monthly")

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
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryBlue)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Health Trends", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text("Historical analysis", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Period Selector
            GlassCard(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                Row(modifier = Modifier.padding(4.dp)) {
                    for (period in periods) {
                        val isSelected = selectedPeriod == period
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) PrimaryBlue else Color.Transparent)
                                .clickable { selectedPeriod = period },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                period,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Trend Charts
            TrendChartSection(title = "Blood Pressure", icon = Icons.Default.Favorite, color = ErrorRed, unit = "mmHg")
            Spacer(modifier = Modifier.height(20.dp))
            TrendChartSection(title = "Heart Rate", icon = Icons.Default.FavoriteBorder, color = ActivePink, unit = "BPM")
            Spacer(modifier = Modifier.height(20.dp))
            TrendChartSection(title = "Oxygen Level", icon = Icons.Default.SettingsInputAntenna, color = PrimaryBlue, unit = "%")

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun TrendChartSection(title: String, icon: ImageVector, color: Color, unit: String) {
    PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(8.dp)) {
                    Icon(icon, contentDescription = null, tint = color)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text("Last $unit values", fontSize = 11.sp, color = TextSecondary)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Text("Chart Area ($unit)", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
