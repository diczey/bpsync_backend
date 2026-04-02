package com.example.finalapp.ui.screen

import com.example.finalapp.ui.component.*


import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.finalapp.ui.navigation.Screen
import com.example.finalapp.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun BLEConnectionScreen(navController: NavController) {
    var scanning by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<String?>(null) }
    var devices by remember { mutableStateOf<List<String>>(emptyList()) }

    val scope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current

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
                    Text("Bluetooth Connection", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text("Device pairing", fontSize = 13.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Main Status Card
            PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(if (connected) SuccessGreen else PrimaryBlue, RoundedCornerShape(24.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (connected) Icons.Default.CheckCircle else Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        if (connected) "Connected to BP Monitor" else if (scanning) "Scanning for Devices..." else "Device Disconnected",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForegroundBlack
                    )
                    Text(
                        if (connected) "Active and ready to sync" else "Ensure your device is turned on",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (!connected) {
                        Button(
                            onClick = {
                                scanning = true
                                devices = emptyList()
                                // Simulate scanning or show toast using 'context'
                            },
                            enabled = !scanning,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                             if (scanning) {
                                 CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 3.dp)
                             } else {
                                Text("Scan for Devices", fontWeight = FontWeight.Bold)
                             }
                        }
                    } else {
                         OutlinedButton(
                            onClick = { connected = false; selectedDevice = null },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed)
                        ) {
                            Text("Disconnect Device", color = ErrorRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Mock device list update
            LaunchedEffect(scanning) {
                if (scanning) {
                    delay(2000)
                    devices = listOf("BP Monitor Pro", "HealthSync Smart", "VitalsCheck V2")
                    scanning = false
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Found Devices List
            if (devices.isNotEmpty() && !connected) {
                Text("Found Devices", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.align(Alignment.Start).padding(start = 4.dp))
                Spacer(modifier = Modifier.height(12.dp))
                
                for (device in devices) {
                    DeviceItem(name = device) {
                        connected = true
                        devices = emptyList()
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun DeviceItem(name: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.6f))
            .border(1.dp, Color.White, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(name, fontWeight = FontWeight.Bold, color = ForegroundBlack)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
        }
    }
}
