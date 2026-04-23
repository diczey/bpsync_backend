package com.example.finalapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.finalapp.ui.component.ActionCard
import com.example.finalapp.ui.component.GlassCard
import com.example.finalapp.ui.localization.rememberIsTurkish
import com.example.finalapp.ui.navigation.Screen
import com.example.finalapp.ui.theme.ActivePink
import com.example.finalapp.ui.theme.BackgroundGradient
import com.example.finalapp.ui.theme.ErrorRed
import com.example.finalapp.ui.theme.ForegroundBlack
import com.example.finalapp.ui.theme.OrangeMain
import com.example.finalapp.ui.theme.PrimaryBlue
import com.example.finalapp.ui.theme.PrimaryGradient
import com.example.finalapp.ui.theme.PurpleMain
import com.example.finalapp.ui.theme.SuccessGreen
import com.example.finalapp.ui.theme.TextSecondary
import com.example.finalapp.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

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

    val bleConnected = uiState.bleConnectedLabel != "Disconnected"
    val hasBloodPressure = uiState.systolic != "--" && uiState.diastolic != "--"
    val heroSubtitle = when {
        uiState.isLoading -> t("Refreshing latest reading", "Son ölçüm yenileniyor")
        hasBloodPressure -> t("Latest reading is ready", "Son ölçüm hazır")
        else -> t("Waiting for the next measurement", "Bir sonraki ölçüm bekleniyor")
    }
    val statusValue = when {
        uiState.errorMessage != null -> t("Check now", "Kontrol et")
        hasBloodPressure -> t("Stable", "Stabil")
        else -> t("Pending", "Bekliyor")
    }
    val bleMetricValue = if (bleConnected) t("Ready", "Hazır") else t("Offline", "Bağlı değil")

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerShape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Text(t("Menu", "Menü"), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text(t("Navigate your health", "Sağlığında gezin"), fontSize = 12.sp, color = TextSecondary)

                    Spacer(modifier = Modifier.height(18.dp))

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
                                Text("${t("Health ID", "Sağlık ID")}: ${uiState.healthId}", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    val menuItems = listOf(
                        Triple(t("Dashboard", "Kontrol Paneli"), Screen.Dashboard, Icons.Default.Dashboard),
                        Triple(t("Health Status", "Sağlık Durumu"), Screen.HealthStatus, Icons.Default.Security),
                        Triple(t("Trends", "Trendler"), Screen.Trends, Icons.Default.ShowChart),
                        Triple(t("Blood Pressure", "Tansiyon"), Screen.BloodPressure, Icons.Default.Favorite),
                        Triple(t("Heart Rate", "Kalp Atışı"), Screen.Pulse, Icons.Default.Favorite),
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
                        menuItems.forEach { (label, screen, icon) ->
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
                    .statusBarsPadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(t("Welcome back", "Tekrar hoş geldin"), fontSize = 12.sp, color = TextSecondary)
                        Text("BP Sync", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = ForegroundBlack)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SurfaceIconButton(
                            onClick = { navController.navigate(Screen.Notifications.route) },
                            backgroundColor = Color.White.copy(alpha = 0.9f)
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
                                Icon(Icons.Default.Notifications, contentDescription = t("Notifications", "Bildirimler"), tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                            }
                        }

                        SurfaceIconButton(
                            onClick = { navController.navigate(Screen.Settings.route) },
                            backgroundColor = Color.White.copy(alpha = 0.9f)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = t("Settings", "Ayarlar"), tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                        }

                        SurfaceIconButton(
                            onClick = {
                                viewModel.logout()
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(Screen.Dashboard.route) { inclusive = true }
                                }
                            },
                            backgroundColor = ErrorRed.copy(alpha = 0.12f)
                        ) {
                            Icon(
                                Icons.Default.ExitToApp,
                                contentDescription = t("Logout", "Çıkış Yap"),
                                tint = ErrorRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                }

                Spacer(modifier = Modifier.height(18.dp))

                BleOverviewCard(
                    title = t("BLE Connection", "BLE Bağlantısı"),
                    status = if (bleConnected) t("Connected", "Bağlı") else t("Not connected", "Bağlı değil"),
                    deviceInfo = uiState.bleDeviceName,
                    bleConnected = bleConnected,
                    onManageClick = { navController.navigate(Screen.BLEConnection.route) }
                )

                Spacer(modifier = Modifier.height(18.dp))

                HeroMeasurementCard(
                    title = t("Your latest reading", "Senin en son ölçümün"),
                    subtitle = heroSubtitle,
                    readingValue = "${uiState.systolic}/${uiState.diastolic}",
                    buttonLabel = if (uiState.isLoading) t("Refreshing", "Yenileniyor") else t("Refresh", "Yenile"),
                    onButtonClick = viewModel::showLatestMeasurement,
                    isTurkish = isTurkish
                )

                Spacer(modifier = Modifier.height(18.dp))

                SectionHeader(
                    title = t("Vital Values", "Vital Değerler"),
                    actionLabel = t("Details", "Detaylar"),
                    onAction = { navController.navigate(Screen.BloodPressure.route) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = t("Pulse", "Nabız"),
                        value = uiState.pulse,
                        subtitle = "bpm",
                        icon = Icons.Default.Favorite,
                        backgroundColor = PrimaryBlue,
                        valueColor = Color.White,
                        metaColor = Color.White.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = t("Status", "Durum"),
                        value = statusValue,
                        subtitle = t("Tracking status", "Takip durumu"),
                        icon = Icons.Default.Security,
                        backgroundColor = Color(0xFFEEF3FF),
                        valueColor = ForegroundBlack,
                        metaColor = TextSecondary,
                        compact = true
                    )
                }

                uiState.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF2F4), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(error, color = ErrorRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SectionHeader(title = t("Quick Access", "Hızlı Erişim"))

                Spacer(modifier = Modifier.height(10.dp))

                QuickAccessCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = t("Measurements", "Ölçümler"),
                    subtitle = t("All records", "Tüm kayıtlar"),
                    icon = Icons.Default.History,
                    iconColor = ActivePink,
                    backgroundColor = Color.White.copy(alpha = 0.96f),
                    onClick = { navController.navigate(Screen.Measurements.route) }
                )

                Spacer(modifier = Modifier.height(18.dp))

                BottomUtilityCard(
                    navController = navController,
                    isTurkish = isTurkish
                )

                Spacer(modifier = Modifier.height(24.dp))

            }
        }
    }
}

@Composable
private fun SurfaceIconButton(
    onClick: () -> Unit,
    backgroundColor: Color,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .background(backgroundColor, RoundedCornerShape(16.dp))
    ) {
        content()
    }
}

@Composable
private fun BottomUtilityCard(
    navController: NavController,
    isTurkish: Boolean
) {
    fun t(english: String, turkish: String): String = if (isTurkish) turkish else english

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ActionCard(
                    modifier = Modifier.weight(1f),
                    title = t("PPG Signal", "PPG Sinyali"),
                    subtitle = t("Live monitoring", "Canlı izleme"),
                    icon = Icons.Default.Wifi,
                    color = OrangeMain,
                    onClick = { navController.navigate(Screen.PPG.route) }
                )
                ActionCard(
                    modifier = Modifier.weight(1f),
                    title = t("Heart Rate", "Nabız"),
                    subtitle = t("Instant measurement", "Anlık ölçüm"),
                    icon = Icons.Default.Favorite,
                    color = SuccessGreen,
                    onClick = { navController.navigate(Screen.Pulse.route) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            ActionCard(
                modifier = Modifier.fillMaxWidth(),
                title = t("Settings", "Ayarlar"),
                subtitle = t("App settings", "Uygulama ayarları"),
                icon = Icons.Default.Settings,
                color = PurpleMain,
                onClick = { navController.navigate(Screen.Settings.route) }
            )
        }
    }
}

@Composable
private fun MoreMenuCard(
    navController: NavController,
    isTurkish: Boolean
) {
    fun t(english: String, turkish: String): String = if (isTurkish) turkish else english

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(t("Other Screens", "Diğer Ekranlar"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                Text(t("Shortcuts", "Kısayollar"), fontSize = 12.sp, color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ActionCard(
                    modifier = Modifier.weight(1f),
                    title = t("Blood Pressure", "Tansiyon"),
                    subtitle = t("Details screen", "Detay ekranı"),
                    icon = Icons.Default.Favorite,
                    color = ErrorRed,
                    onClick = { navController.navigate(Screen.BloodPressure.route) }
                )
                ActionCard(
                    modifier = Modifier.weight(1f),
                    title = t("Pulse", "Nabız"),
                    subtitle = t("Live data", "Canlı veri"),
                    icon = Icons.Default.Favorite,
                    color = SuccessGreen,
                    onClick = { navController.navigate(Screen.Pulse.route) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ActionCard(
                    modifier = Modifier.weight(1f),
                    title = t("Profile", "Profil"),
                    subtitle = t("Your info", "Bilgilerin"),
                    icon = Icons.Default.Person,
                    color = PrimaryBlue,
                    onClick = { navController.navigate(Screen.Profile.route) }
                )
                ActionCard(
                    modifier = Modifier.weight(1f),
                    title = t("Settings", "Ayarlar"),
                    subtitle = t("App settings", "Uygulama ayarları"),
                    icon = Icons.Default.Settings,
                    color = PurpleMain,
                    onClick = { navController.navigate(Screen.Settings.route) }
                )
            }
        }
    }
}

@Composable
private fun HeroMeasurementCard(
    title: String,
    subtitle: String,
    readingValue: String,
    buttonLabel: String,
    onButtonClick: () -> Unit,
    isTurkish: Boolean
) {
    fun t(english: String, turkish: String): String = if (isTurkish) turkish else english

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrimaryGradient, RoundedCornerShape(30.dp))
            .padding(22.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(132.dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(92.dp)
                .background(Color.White.copy(alpha = 0.14f), CircleShape)
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                t("Latest Reading", "Son Ölçüm"),
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.82f),
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(99.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(title, fontSize = 29.sp, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.74f), modifier = Modifier.padding(top = 6.dp))

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onButtonClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = PrimaryBlue),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        buttonLabel,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                GlassCard(modifier = Modifier.width(166.dp).height(118.dp)) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(t("Latest", "Son"), fontSize = 12.sp, color = TextSecondary, maxLines = 1, softWrap = false)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            readingValue,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ForegroundBlack,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("mmHg", fontSize = 11.sp, color = TextSecondary, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)

        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = PrimaryBlue, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    backgroundColor: Color,
    valueColor: Color,
    metaColor: Color,
    compact: Boolean = false
) {
    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        if (valueColor == Color.White) Color.White.copy(alpha = 0.16f) else PrimaryBlue.copy(alpha = 0.08f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (valueColor == Color.White) Color.White else PrimaryBlue,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = metaColor)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                value,
                fontSize = if (compact) 18.sp else 28.sp,
                lineHeight = if (compact) 21.sp else 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(subtitle, fontSize = 11.sp, color = metaColor)
        }
    }
}

@Composable
private fun BleOverviewCard(
    title: String,
    status: String,
    deviceInfo: String,
    bleConnected: Boolean,
    onManageClick: () -> Unit
) {
    val isTurkish = rememberIsTurkish()

    fun t(english: String, turkish: String): String = if (isTurkish) turkish else english

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(
                                if (bleConnected) PrimaryBlue.copy(alpha = 0.12f) else Color(0xFFEEF3FF),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(11.dp)
                    ) {
                        Icon(
                            Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = if (bleConnected) PrimaryBlue else TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                        Text(status, fontSize = 12.sp, color = TextSecondary)
                    }
                }

                Button(
                    onClick = onManageClick,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.28f)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.9f),
                        contentColor = PrimaryBlue
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (bleConnected) t("Open BLE", "BLE Aç") else t("Connect BLE", "BLE Bağla"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                deviceInfo,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            InfoChip(
                text = if (bleConnected) t("BLE Connected", "BLE Bağlı") else t("BLE Disconnected", "BLE Çevrimdışı"),
                color = if (bleConnected) SuccessGreen else ErrorRed
            )
        }
    }
}

@Composable
private fun QuickAccessCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    backgroundColor: Color,
    contentColor: Color = ForegroundBlack,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        if (contentColor == Color.White) Color.White.copy(alpha = 0.14f) else iconColor.copy(alpha = 0.12f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (contentColor == Color.White) Color.White else iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                subtitle,
                fontSize = 11.sp,
                color = if (contentColor == Color.White) Color.White.copy(alpha = 0.68f) else TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InfoChip(
    text: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}
