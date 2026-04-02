package com.example.finalapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.finalapp.ui.component.ActionCard
import com.example.finalapp.ui.component.GlassCard
import com.example.finalapp.ui.component.PremiumGlassCard
import com.example.finalapp.ui.component.ReadingValue
import com.example.finalapp.ui.component.VitalRow
import com.example.finalapp.ui.navigation.Screen
import com.example.finalapp.ui.theme.ActivePink
import com.example.finalapp.ui.theme.BackgroundGradient
import com.example.finalapp.ui.theme.CyanMain
import com.example.finalapp.ui.theme.ErrorRed
import com.example.finalapp.ui.theme.ForegroundBlack
import com.example.finalapp.ui.theme.OrangeMain
import com.example.finalapp.ui.theme.PrimaryBlue
import com.example.finalapp.ui.theme.PrimaryGradient
import com.example.finalapp.ui.theme.PurpleMain
import com.example.finalapp.ui.theme.SuccessGreen
import com.example.finalapp.ui.theme.TextMuted
import com.example.finalapp.ui.theme.TextSecondary
import com.example.finalapp.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = viewModel()
) {
    val scrollState = rememberScrollState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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

                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(PrimaryGradient, RoundedCornerShape(16.dp))
                                    .padding(10.dp)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(uiState.userName, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "Health ID: ${uiState.healthId}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
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

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
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
                                colors = NavigationDrawerItemDefaults.colors(
                                    unselectedContainerColor = Color.Transparent
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            viewModel.logout()
                            scope.launch { drawerState.close() }
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.Dashboard.route) { inclusive = true }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .height(52.dp),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text("BP Sync", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                        Text("Health Monitoring", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(PrimaryGradient, RoundedCornerShape(16.dp))
                                    .padding(10.dp)
                            ) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(uiState.bleDeviceName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                                Text(uiState.bleConnectedLabel, fontSize = 12.sp, color = TextSecondary)
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

                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(ErrorRed.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                        .padding(10.dp)
                                ) {
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ReadingValue(value = uiState.systolic, label = "Systolic")
                            Text(
                                "/",
                                fontSize = 32.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
                            )
                            ReadingValue(value = uiState.diastolic, label = "Diastolic")
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider(color = Color.Black.copy(alpha = 0.05f))
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            InfoItem(icon = Icons.Default.FavoriteBorder, text = "Pulse: ${uiState.pulse} bpm", color = ErrorRed)
                            InfoItem(icon = Icons.Default.SettingsInputAntenna, text = "SpO2: ${uiState.spo2}%", color = PrimaryBlue)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                uiState.errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Button(
                    onClick = viewModel::showLatestMeasurement,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text(
                        if (uiState.isLoading) "Refreshing..." else "Show Latest Measurement",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ActionCard(
                        modifier = Modifier.weight(1f),
                        title = "Trends",
                        subtitle = "View History",
                        icon = Icons.Default.ShowChart,
                        color = PurpleMain,
                        onClick = { navController.navigate(Screen.Trends.route) }
                    )
                    ActionCard(
                        modifier = Modifier.weight(1f),
                        title = "Health Status",
                        subtitle = "Check Status",
                        icon = Icons.Default.Security,
                        color = SuccessGreen,
                        onClick = { navController.navigate(Screen.HealthStatus.route) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                VitalRow(
                    title = "PPG Signal",
                    subtitle = "Real-time monitoring",
                    icon = Icons.Default.Wifi,
                    color = OrangeMain
                ) {
                    navController.navigate(Screen.PPG.route)
                }
                VitalRow(
                    title = "Heart Rate",
                    subtitle = if (uiState.pulse == "--") "Waiting for data..." else "${uiState.pulse} bpm",
                    icon = Icons.Default.Favorite,
                    color = ActivePink
                ) {
                    navController.navigate(Screen.Pulse.route)
                }
                VitalRow(
                    title = "All Measurements",
                    subtitle = "View all data",
                    icon = Icons.Default.History,
                    color = com.example.finalapp.ui.theme.CyanMain
                ) {
                    navController.navigate(Screen.Measurements.route)
                }
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
