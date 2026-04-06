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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.example.finalapp.ui.component.ActionCard
import com.example.finalapp.ui.component.GlassCard
import com.example.finalapp.ui.component.PremiumGlassCard
import com.example.finalapp.ui.component.ReadingValue
import com.example.finalapp.ui.component.VitalRow
import com.example.finalapp.ui.localization.rememberIsTurkish
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val isTurkish = rememberIsTurkish()

    fun t(english: String, turkish: String): String = if (isTurkish) turkish else english

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDashboard()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerShape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text(t("Menu", "Menü"), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text(t("Navigate your health", "Sağlığında gezin"), fontSize = 12.sp, color = TextSecondary)

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
                                    "${t("Health ID", "Sağlık ID")}: ${uiState.healthId}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    val menuItems = listOf(
                        Triple(t("Dashboard", "Kontrol Paneli"), Screen.Dashboard, Icons.Default.Dashboard),
                        Triple(t("Health Status", "Sağlık Durumu"), Screen.HealthStatus, Icons.Default.Security),
                        Triple(t("Trends", "Trendler"), Screen.Trends, Icons.Default.ShowChart),
                        Triple(t("Blood Pressure", "Tansiyon"), Screen.BloodPressure, Icons.Default.Favorite),
                        Triple(t("Heart Rate", "Kalp Atışı"), Screen.Pulse, Icons.Default.FavoriteBorder),
                        Triple(t("PPG Signal", "PPG Sinyali"), Screen.PPG, Icons.Default.Wifi),
                        Triple(t("Measurements", "Ölçümler"), Screen.Measurements, Icons.Default.History),
                        Triple(t("Profile", "Profil"), Screen.Profile, Icons.Default.Person),
                        Triple(t("Settings", "Ayarlar"), Screen.Settings, Icons.Default.Settings)
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
                        Text(t("Logout", "Çıkış Yap"), color = ErrorRed, fontWeight = FontWeight.Bold)
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
                            contentDescription = t("Menu", "Menü"),
                            tint = PrimaryBlue,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text("BP Sync", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                        Text(t("Health Monitoring", "Sağlık Takibi"), fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    }

                    IconButton(
                        onClick = { navController.navigate(Screen.Notifications.route) },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                    ) {
                        BadgedBox(
                            badge = {
                                if (uiState.unreadNotificationCount > 0) {
                                    Badge {
                                        Text(
                                            text = if (uiState.unreadNotificationCount > 99) "99+" else uiState.unreadNotificationCount.toString(),
                                            color = Color.White,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = t("Notifications", "Bildirimler"),
                                tint = PrimaryBlue,
                                modifier = Modifier.size(26.dp)
                            )
                        }
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
                                Text(
                                    text = when (uiState.bleConnectedLabel) {
                                        "Connected" -> t("Connected", "Bağlı")
                                        "Disconnected" -> t("Disconnected", "Bağlı Değil")
                                        else -> uiState.bleConnectedLabel
                                    },
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Button(
                            onClick = { navController.navigate(Screen.BLEConnection.route) },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            Text(t("Manage", "Yönet"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                                    Text(t("Blood Pressure", "Tansiyon"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                                    Text(t("Latest Reading", "Son Ölçüm"), fontSize = 12.sp, color = TextSecondary)
                                }
                            }
                            TextButton(onClick = { navController.navigate(Screen.BloodPressure.route) }) {
                                Text(t("Details", "Detaylar"), color = PrimaryBlue, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ReadingValue(value = uiState.systolic, label = t("Systolic", "Sistolik"))
                            Text(
                                "/",
                                fontSize = 32.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
                            )
                            ReadingValue(value = uiState.diastolic, label = t("Diastolic", "Diyastolik"))
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider(color = Color.Black.copy(alpha = 0.05f))
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            InfoItem(icon = Icons.Default.FavoriteBorder, text = "${t("Pulse", "Nabız")}: ${uiState.pulse} bpm", color = ErrorRed)
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
                        if (uiState.isLoading) t("Refreshing...", "Yenileniyor...") else t("Refresh Latest Measurement", "Son Ölçümü Yenile"),
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
                        title = t("Trends", "Trendler"),
                        subtitle = t("View History", "Geçmişi Gör"),
                        icon = Icons.Default.ShowChart,
                        color = PurpleMain,
                        onClick = { navController.navigate(Screen.Trends.route) }
                    )
                    ActionCard(
                        modifier = Modifier.weight(1f),
                        title = t("Health Status", "Sağlık Durumu"),
                        subtitle = t("Check Status", "Durumu Gör"),
                        icon = Icons.Default.Security,
                        color = SuccessGreen,
                        onClick = { navController.navigate(Screen.HealthStatus.route) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                VitalRow(
                    title = t("PPG Signal", "PPG Sinyali"),
                    subtitle = t("Real-time monitoring", "Gerçek zamanlı izleme"),
                    icon = Icons.Default.Wifi,
                    color = OrangeMain
                ) {
                    navController.navigate(Screen.PPG.route)
                }
                VitalRow(
                    title = t("Heart Rate", "Kalp Atışı"),
                    subtitle = if (uiState.pulse == "--") t("Waiting for data...", "Veri bekleniyor...") else "${uiState.pulse} bpm",
                    icon = Icons.Default.Favorite,
                    color = ActivePink
                ) {
                    navController.navigate(Screen.Pulse.route)
                }
                VitalRow(
                    title = t("All Measurements", "Tüm Ölçümler"),
                    subtitle = t("View all data", "Tüm verileri gör"),
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
