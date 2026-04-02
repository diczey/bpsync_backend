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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.finalapp.ui.theme.*

@Composable
fun SettingsScreen(navController: NavController) {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var weeklyReportsEnabled by remember { mutableStateOf(false) }

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
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryBlue)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Notifications Block
             GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).background(PrimaryBlue.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(10.dp)) {
                             Icon(Icons.Default.Notifications, contentDescription = null, tint = PrimaryBlue)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Notifications", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    ToggleItem(label = "Push Notifications", checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
                    ToggleItem(label = "Weekly Reports", checked = weeklyReportsEnabled, onCheckedChange = { weeklyReportsEnabled = it })
                }
             }

            Spacer(modifier = Modifier.height(20.dp))

            // Language block
             GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).background(PurpleMain.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(10.dp)) {
                             Icon(Icons.Default.Language, contentDescription = null, tint = PurpleMain)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Language", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("English", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack, modifier = Modifier.padding(start = 8.dp))
                }
             }

            Spacer(modifier = Modifier.height(20.dp))

            // More items
            SettingsLinkItem(title = "Privacy Policy", icon = Icons.Default.Security, color = SuccessGreen)
            SettingsLinkItem(title = "Terms of Service", icon = Icons.Default.Description, color = OrangeMain)
            SettingsLinkItem(title = "About BP Sync", icon = Icons.Default.Info, color = CyanMain)

            Spacer(modifier = Modifier.height(40.dp))
            Text("BP Sync Version 1.0.0", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun ToggleItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ForegroundBlack)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryBlue,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.LightGray
            )
        )
    }
}

@Composable
fun SettingsLinkItem(title: String, icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .border(1.dp, Color.White, RoundedCornerShape(20.dp))
            .clickable { /* Link Logic */ }
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(10.dp)) {
                    Icon(icon, contentDescription = null, tint = color)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
        }
    }
}
