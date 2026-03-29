package com.example.bp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.example.bp.ui.theme.*
import com.example.bp.ui.viewmodel.DashboardViewModel

// Color tokens matching React code
private val BgFrom = Color(0xFFE9F1FC)
private val BgVia = Color(0xFFF5F8FB)
private val BgTo = Color(0xFFDDE5EF)
private val PrimaryBlue = Color(0xFF2D59F0)
private val PrimaryBlueDeep = Color(0xFF1E40AF)
private val TextBlack = Color(0xFF000000)
private val TextGray = Color(0xFF6B7280)

@Composable
fun DashboardScreen(
    token: String,
    viewModel: DashboardViewModel,
    onNavigateToReadings: () -> Unit,
    onNavigateToTrends: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToBle: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToBloodPressure: () -> Unit,
    onNavigateToPulse: () -> Unit,
    onNavigateToPpg: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Load real data whenever token changes
    LaunchedEffect(token) {
        if (token.isNotEmpty()) {
            viewModel.loadAll(token)
        }
    }

    // Use API data when available, fall back to defaults
    val systolic  = uiState.summary?.latestSystolic  ?: 0
    val diastolic = uiState.summary?.latestDiastolic ?: 0
    val pulse     = uiState.summary?.latestHeartRate  ?: 0
    val spo2      = uiState.summary?.latestSpo2       ?: 0

    var menuOpen by remember { mutableStateOf(false) }

    val bleConnected = true
    val bleDeviceName = "BP Monitor Pro"
    val bleBattery = 85

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgFrom, BgVia, BgTo)))
    ) {
        // Shared Medical Patterns Overlay
        BackgroundIllustrations()

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar - Glass Effect
            Surface(
                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                color = Color.White.copy(alpha = 0.8f),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("BP Sync", color = TextBlack, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.5).sp)
                        Text("Health Monitoring", color = TextGray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(PrimaryBlue.copy(alpha = 0.1f))
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = PrimaryBlue)
                    }
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // BLE Status - Glass Card
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Brush.linearGradient(listOf(PrimaryBlue, PrimaryBlueDeep))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color.White)
                                }
                                if (bleConnected) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = 4.dp, y = (-4).dp)
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981))
                                            .border(2.dp, Color.White, CircleShape)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(bleDeviceName, fontWeight = FontWeight.SemiBold, color = TextBlack, fontSize = 15.sp)
                                Text(
                                    "${if (bleConnected) "Connected" else "Disconnected"} • $bleBattery%",
                                    fontSize = 12.sp, color = TextGray, fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Button(
                            onClick = onNavigateToBle,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("Manage", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Blood Pressure Card - Premium Glass
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = Color.White.copy(alpha = 0.65f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                    shadowElevation = 8.dp
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626)))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = Color.White)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Blood Pressure", fontWeight = FontWeight.Bold, color = TextBlack, fontSize = 17.sp)
                                    Text("Latest Reading", fontSize = 12.sp, color = TextGray, fontWeight = FontWeight.Medium)
                                }
                            }
                            TextButton(onClick = onNavigateToBloodPressure) {
                                Text("Details", color = PrimaryBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$systolic", fontWeight = FontWeight.Bold, fontSize = 48.sp, color = PrimaryBlue, letterSpacing = (-1).sp)
                                Text("SYSTOLIC", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextGray, letterSpacing = 1.sp)
                            }
                            Text(" / ", fontSize = 32.sp, fontWeight = FontWeight.Light, color = Color(0xFF9CA3AF), modifier = Modifier.padding(horizontal = 32.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$diastolic", fontWeight = FontWeight.Bold, fontSize = 48.sp, color = PrimaryBlue, letterSpacing = (-1).sp)
                                Text("DIASTOLIC", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextGray, letterSpacing = 1.sp)
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        Divider(color = Color(0xFFE5E7EB).copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Pulse: $pulse bpm", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextGray)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Sensors, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("SpO2: $spo2%", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextGray)
                            }
                        }
                    }
                }

                // Loading / Error state
                if (uiState.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = PrimaryBlue
                    )
                }
                uiState.error?.let { errorMsg ->
                    Text(
                        text = errorMsg,
                        color = Color(0xFFEF4444),
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Refresh Button
                Button(
                    onClick = { viewModel.loadAll(token) },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Refresh Measurements", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                // Quick Actions Grid
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Trends
                    Surface(
                        modifier = Modifier.weight(1f).clickable { onNavigateToTrends() },
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.8f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF7C3AED)))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color.White)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Trends", fontWeight = FontWeight.SemiBold, color = TextBlack, fontSize = 14.sp)
                            Text("View History", fontSize = 11.sp, color = TextGray, fontWeight = FontWeight.Medium)
                        }
                    }
                    // Health Status
                    Surface(
                        modifier = Modifier.weight(1f).clickable { onNavigateToReports() },
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.8f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Health Status", fontWeight = FontWeight.SemiBold, color = TextBlack, fontSize = 14.sp)
                            Text("Check Status", fontSize = 11.sp, color = TextGray, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Other Vitals Cards
                VitalCard("PPG Signal", "Real-time monitoring", Icons.Default.Timeline, listOf(Color(0xFFF59E0B), Color(0xFFD97706)), onNavigateToPpg)
                VitalCard("Heart Rate", "$pulse bpm", Icons.Default.Favorite, listOf(Color(0xFFEC4899), Color(0xFFDB2777)), onNavigateToPulse)
                VitalCard("All Measurements", "View all data", Icons.Default.List, listOf(Color(0xFF06B6D4), Color(0xFF0891B2)), onNavigateToReadings)
            }
        }
        
        // Side Menu simple simulation
        if (menuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { menuOpen = false },
                contentAlignment = Alignment.CenterStart
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(280.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    color = Color.White,
                    shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp).statusBarsPadding()) {
                        Text("Menu", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TextBlack)
                        Spacer(Modifier.height(24.dp))
                        SideMenuItem("Profile", Icons.Default.Person) { menuOpen = false; onNavigateToProfile() }
                        SideMenuItem("Settings", Icons.Default.Settings) { menuOpen = false; onNavigateToSettings() }
                        Spacer(Modifier.weight(1f))
                        SideMenuItem("Logout", Icons.Default.ExitToApp, Color.Red) { menuOpen = false; onLogout() }
                    }
                }
            }
        }
    }
}

@Composable
fun VitalCard(title: String, subtitle: String, icon: ImageVector, colors: List<Color>, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.8f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(colors)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, fontWeight = FontWeight.SemiBold, color = TextBlack, fontSize = 14.sp)
                    Text(subtitle, fontSize = 11.sp, color = TextGray, fontWeight = FontWeight.Medium)
                }
            }
            Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun SideMenuItem(title: String, icon: ImageVector, color: Color = TextBlack, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

