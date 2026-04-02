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
import com.example.finalapp.ui.navigation.Screen
import com.example.finalapp.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(navController: NavController) {
    val scrollState = rememberScrollState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    // Dynamic Measurement States - Start with '--' as requested
    var systolic by remember { mutableStateOf("--") }
    var diastolic by remember { mutableStateOf("--") }
    var pulse by remember { mutableStateOf("--") }
    var spo2 by remember { mutableStateOf("--") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                 modifier = Modifier.width(280.dp),
                 drawerShape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text("Menu", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text("Navigate your health", fontSize = 12.sp, color = TextSecondary)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // User Card
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(48.dp).background(PrimaryGradient, RoundedCornerShape(16.dp)).padding(10.dp)) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("John Doe", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Health ID: BP2024", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    val menuItems = listOf(
                        Triple("Dashboard", Screen.Dashboard, Icons.Default.Dashboard),
                        Triple("Health Status", Screen.HealthStatus, Icons.Default.Security),
                        Triple("Trends", Screen.Trends, Icons.Default.ShowChart),
                        Triple("Blood Pressure", Screen.BloodPressure, Icons.Default.Favorite),
                        Triple("Heart Rate", Screen.Pulse, Icons.Default.FavoriteBorder),
                        Triple("PPG Signal", Screen.PPG, Icons.Default.Wifi),
                        Triple("Measurements", Screen.Measurements, Icons.Default.History),
                        Triple("Profile", Screen.Profile, Icons.Default.Person),
                        Triple("Settings", Screen.Settings, Icons.Default.Settings)
                    )

                    for ((label, screen, icon) in menuItems) {
                         NavigationDrawerItem(
                            label = { Text(label, fontWeight = FontWeight.Bold) },
                            selected = false,
                            onClick = { 
                                scope.launch { drawerState.close() }
                                navController.navigate(screen.route) 
                            },
                            icon = { Icon(icon, contentDescription = null, tint = PrimaryBlue) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(vertical = 4.dp),
                            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                         )
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    
                    Button(
                        onClick = { 
                            scope.launch { drawerState.close() }
                            navController.navigate(Screen.Login.route) 
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.1f))
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, tint = ErrorRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Logout", color = ErrorRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("BP Sync", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                        Text("Health Monitoring", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    }
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = PrimaryBlue, modifier = Modifier.size(28.dp))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // BLE Status
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp), 
                        horizontalArrangement = Arrangement.SpaceBetween, 
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(modifier = Modifier.size(48.dp).background(PrimaryGradient, RoundedCornerShape(16.dp)).padding(10.dp)) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("BP Monitor Pro", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                                Text("Connected • 85%", fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                        
                        Button(
                            onClick = { navController.navigate(Screen.BLEConnection.route) }, 
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue), 
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            Text("Manage", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // BP Reading
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(44.dp).background(ErrorRed.copy(alpha = 0.1f), RoundedCornerShape(16.dp)).padding(10.dp)) {
                                    Icon(Icons.Default.Favorite, contentDescription = null, tint = ErrorRed)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Blood Pressure", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                                    Text("Latest Reading", fontSize = 12.sp, color = TextSecondary)
                                }
                            }
                            TextButton(onClick = { navController.navigate(Screen.BloodPressure.route) }) {
                                Text("Details", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            ReadingValue(value = systolic, label = "Systolic")
                            Text("/", fontSize = 32.sp, color = TextMuted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp).padding(bottom = 8.dp))
                            ReadingValue(value = diastolic, label = "Diastolic")
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider(color = Color.Black.copy(alpha = 0.05f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            InfoItem(icon = Icons.Default.FavoriteBorder, text = "Pulse: $pulse bpm", color = ErrorRed)
                            InfoItem(icon = Icons.Default.SettingsInputAntenna, text = "SpO2: $spo2%", color = PrimaryBlue)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Sync Button
                Button(
                    onClick = { 
                        // Simulate Database Fetch
                        val s = (110..135).random().toString()
                        val d = (70..90).random().toString()
                        val p = (65..85).random().toString()
                        val sp = (95..99).random().toString()
                        
                        systolic = s
                        diastolic = d
                        pulse = p
                        spo2 = sp
                        
                        // Add to shared repository
                        com.example.finalapp.data.repository.ReadingRepository.addReading(s, d, p, sp)
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Show Latest Measurement", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ActionCard(modifier = Modifier.weight(1f), title = "Trends", subtitle = "View History", icon = Icons.Default.ShowChart, color = PurpleMain, onClick = { navController.navigate(Screen.Trends.route) })
                    ActionCard(modifier = Modifier.weight(1f), title = "Health Status", subtitle = "Check Status", icon = Icons.Default.Security, color = SuccessGreen, onClick = { navController.navigate(Screen.HealthStatus.route) })
                }
                Spacer(modifier = Modifier.height(12.dp))
                VitalRow(title = "PPG Signal", subtitle = "Real-time monitoring", icon = Icons.Default.Wifi, color = OrangeMain) { navController.navigate(Screen.PPG.route) }
                VitalRow(title = "Heart Rate", subtitle = if (pulse == "--") "Waiting for data..." else "$pulse bpm", icon = Icons.Default.Favorite, color = ActivePink) { navController.navigate(Screen.Pulse.route) }
                VitalRow(title = "All Measurements", subtitle = "View all data", icon = Icons.Default.History, color = CyanMain) { navController.navigate(Screen.Measurements.route) }
            }
        }
    }
}

@Composable
fun InfoItem(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
    }
}
