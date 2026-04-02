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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.finalapp.ui.navigation.Screen
import com.example.finalapp.ui.theme.*

@Composable
fun PPGScreen(navController: NavController) {
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
                    modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = CyanMain)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("PPG Signal", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text("Photoplethysmography", fontSize = 13.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // PPG Info Card
            PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(64.dp).background(CyanMain.copy(alpha = 0.1f), RoundedCornerShape(20.dp)).padding(16.dp)) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = CyanMain, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f).background(CyanMain.copy(alpha = 0.05f), RoundedCornerShape(16.dp)).padding(16.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("87", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = CyanMain)
                                Text("Average", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                            }
                        }
                        Box(modifier = Modifier.weight(1f).background(SuccessGreen.copy(alpha = 0.05f), RoundedCornerShape(16.dp)).padding(16.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("94%", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                                Text("Quality", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Signal Trend Placeholder
            Text("Signal Trend", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack, modifier = Modifier.padding(start = 4.dp))
            Spacer(modifier = Modifier.height(12.dp))

            PremiumGlassCard(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("PPG Signal Graph Placeholder", color = TextMuted, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // info Section
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = CyanMain, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("What is PPG?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Photoplethysmography (PPG) is an optical technique used to detect blood volume changes in the tissue.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
