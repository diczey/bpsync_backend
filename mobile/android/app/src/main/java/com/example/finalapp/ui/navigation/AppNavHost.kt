package com.example.finalapp.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.finalapp.data.repository.SessionStore
import com.example.finalapp.ui.localization.rememberIsTurkish
import com.example.finalapp.ui.screen.*
import com.example.finalapp.ui.theme.PrimaryBlue
import com.example.finalapp.ui.theme.PrimaryGradient
import com.example.finalapp.ui.theme.TextMuted

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val startDestination = if (SessionStore.hasActiveSession()) {
        Screen.Dashboard.route
    } else {
        Screen.Login.route
    }
    val showBottomBar = currentRoute != Screen.Login.route && currentRoute != Screen.Register.route
    val contentBottomPadding = if (showBottomBar) 156.dp else 0.dp

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF6F8FD))) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = contentBottomPadding)
        ) {
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

        if (showBottomBar) {
            HealthBottomBar(
                navController = navController,
                currentRoute = currentRoute,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

private data class BottomNavItem(
    val label: String,
    val route: String,
    val icon: ImageVector
)

@Composable
private fun HealthBottomBar(
    navController: NavController,
    currentRoute: String?,
    modifier: Modifier = Modifier
) {
    val isTurkish = rememberIsTurkish()

    fun t(english: String, turkish: String): String = if (isTurkish) turkish else english

    val items = listOf(
        BottomNavItem(t("Home", "Ana Sayfa"), Screen.Dashboard.route, Icons.Default.Home),
        BottomNavItem(t("Blood Pressure", "Tansiyon"), Screen.BloodPressure.route, Icons.Default.LocalHospital),
        BottomNavItem(t("Trends", "Trendler"), Screen.Trends.route, Icons.Default.ShowChart),
        BottomNavItem(t("Profile", "Profil"), Screen.Profile.route, Icons.Default.Person)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 20.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(28.dp),
            color = Color.White.copy(alpha = 0.98f),
            shadowElevation = 18.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomBarItem(
                        item = items[0],
                        selected = currentRoute == items[0].route,
                        onClick = { navigateBottom(navController, items[0].route) }
                    )
                    BottomBarItem(
                        item = items[1],
                        selected = currentRoute == items[1].route,
                        onClick = { navigateBottom(navController, items[1].route) }
                    )
                    Spacer(modifier = Modifier.size(72.dp))
                    BottomBarItem(
                        item = items[2],
                        selected = currentRoute == items[2].route,
                        onClick = { navigateBottom(navController, items[2].route) }
                    )
                    BottomBarItem(
                        item = items[3],
                        selected = currentRoute == items[3].route,
                        onClick = { navigateBottom(navController, items[3].route) }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-24).dp)
                .size(74.dp)
                .clip(CircleShape)
                .background(PrimaryGradient)
                .clickable { navigateBottom(navController, Screen.HealthStatus.route) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = t("Health Status", "Sağlık Durumu"),
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun BottomBarItem(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = if (selected) PrimaryBlue else TextMuted,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.label,
            color = if (selected) PrimaryBlue else TextMuted,
            fontSize = 11.sp
        )
    }
}

private fun navigateBottom(navController: NavController, route: String) {
    navController.navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
    }
}
