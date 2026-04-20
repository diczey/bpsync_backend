package com.example.finalapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.finalapp.data.repository.SessionStore
import com.example.finalapp.ui.screen.*

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val startDestination = if (SessionStore.hasActiveSession()) {
        Screen.Dashboard.route
    } else {
        Screen.Login.route
    }

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
