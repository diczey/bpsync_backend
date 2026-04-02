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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.finalapp.ui.navigation.Screen
import com.example.finalapp.ui.theme.*

@Composable
fun BloodPressureScreen(navController: NavController) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
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
                    Text("Blood Pressure", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text("Detailed analysis", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Reading Card
            PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(), 
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.size(64.dp).background(ErrorRed.copy(alpha = 0.1f), RoundedCornerShape(20.dp)).padding(16.dp)) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = ErrorRed, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Latest Reading", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("120", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                        Text("/", fontSize = 48.sp, color = TextMuted, modifier = Modifier.padding(horizontal = 8.dp))
                        Text("80", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                        Text("mmHg", fontSize = 14.sp, color = TextMuted, modifier = Modifier.padding(start = 8.dp, top = 20.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.background(SuccessGreen.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text("NORMAL", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // stats Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard(modifier = Modifier.weight(1f), title = "AVG Systolic", value = "118", color = ErrorRed)
                StatCard(modifier = Modifier.weight(1f), title = "AVG Diastolic", value = "79", color = ActivePink)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Charts Section Placeholder
            Text("7-Day Trend", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack, modifier = Modifier.padding(start = 4.dp))
            Spacer(modifier = Modifier.height(12.dp))
            
            PremiumGlassCard(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Trend Chart Placeholder", color = TextMuted, fontWeight = FontWeight.Medium)
                    // Real implementation would use MPAndroidChart here
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Health Insights
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                   Box(modifier = Modifier.size(44.dp).background(PrimaryBlue.copy(alpha = 0.1f), RoundedCornerShape(14.dp)).padding(10.dp)) {
                       Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryBlue)
                   }
                   Spacer(modifier = Modifier.width(16.dp))
                   Column(modifier = Modifier.weight(1f)) {
                       Text("Healthy Tip", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                       Text("Consistent readings help track your health effectively.", fontSize = 12.sp, color = TextSecondary)
                   }
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun StatCard(modifier: Modifier, title: String, value: String, color: Color) {
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .border(1.dp, Color.White, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
            Text("mmHg", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = TextMuted)
        }
    }
}
