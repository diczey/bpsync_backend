package com.example.finalapp.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.finalapp.data.repository.SessionStore
import com.example.finalapp.ui.localization.isTurkishSelected
import com.example.finalapp.ui.screen.*
import com.example.finalapp.ui.theme.PrimaryBlue
import com.example.finalapp.ui.theme.TextMuted
import com.example.finalapp.ui.theme.TextSecondary

private val bottomNavRoutes = setOf(
    Screen.Dashboard.route,
    Screen.Trends.route,
    Screen.Measurements.route,
    Screen.Profile.route,
)

private data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val labelEn: String,
    val labelTr: String,
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard.route, Icons.Default.Home, "Home", "Ana Sayfa"),
    BottomNavItem(Screen.Trends.route, Icons.Default.ShowChart, "Trends", "Trendler"),
    BottomNavItem(Screen.Measurements.route, Icons.Default.History, "History", "Geçmiş"),
    BottomNavItem(Screen.Profile.route, Icons.Default.Person, "Profile", "Profil"),
)

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val startDestination = if (SessionStore.hasActiveSession()) {
        Screen.Dashboard.route
    } else {
        Screen.Login.route
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomNav = currentRoute in bottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                BPSyncBottomBar(navController = navController, currentRoute = currentRoute)
            }
        }
    ) { _ ->
        NavHost(navController = navController, startDestination = startDestination) {
            composable(Screen.Login.route) { LoginScreen(navController) }
            composable(Screen.Register.route) { RegisterScreen(navController) }
            composable(Screen.Dashboard.route) { DashboardScreen(navController) }
            composable(Screen.HealthStatus.route) { HealthStatusScreen(navController) }
            composable(Screen.BLEConnection.route) { BLEConnectionScreen(navController) }
            composable(Screen.Trends.route) { TrendsScreen(navController) }
            composable(Screen.Measurements.route) { MeasurementsScreen(navController) }
            composable(Screen.BloodPressure.route) { BloodPressureScreen(navController) }
            composable(Screen.Pulse.route) { PulseScreen(navController) }
            composable(Screen.PPG.route) { PPGScreen(navController) }
            composable(Screen.Profile.route) { ProfileScreen(navController) }
            composable(Screen.Notifications.route) { NotificationsScreen(navController) }
            composable(Screen.Reports.route) { ReportsScreen(navController) }
            composable(Screen.Settings.route) { SettingsScreen(navController) }
        }
    }
}

@Composable
private fun BPSyncBottomBar(navController: NavController, currentRoute: String?) {
    val isTurkish = isTurkishSelected()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.95f))
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left 2 items
            bottomNavItems.take(2).forEach { item ->
                BottomNavItemView(
                    item = item,
                    isSelected = currentRoute == item.route,
                    isTurkish = isTurkish,
                    onClick = {
                        if (currentRoute != item.route) {
                            navController.navigate(item.route) {
                                popUpTo(Screen.Dashboard.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }

            // Center BLE button
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(PrimaryBlue, CircleShape)
                    .clip(CircleShape)
                    .clickable {
                        navController.navigate(Screen.BLEConnection.route) {
                            launchSingleTop = true
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "BLE",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Right 2 items
            bottomNavItems.takeLast(2).forEach { item ->
                BottomNavItemView(
                    item = item,
                    isSelected = currentRoute == item.route,
                    isTurkish = isTurkish,
                    onClick = {
                        if (currentRoute != item.route) {
                            navController.navigate(item.route) {
                                popUpTo(Screen.Dashboard.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun BottomNavItemView(
    item: BottomNavItem,
    isSelected: Boolean,
    isTurkish: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            item.icon,
            contentDescription = if (isTurkish) item.labelTr else item.labelEn,
            tint = if (isSelected) PrimaryBlue else TextMuted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = if (isTurkish) item.labelTr else item.labelEn,
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) PrimaryBlue else TextSecondary,
            maxLines = 1
        )
    }
}
