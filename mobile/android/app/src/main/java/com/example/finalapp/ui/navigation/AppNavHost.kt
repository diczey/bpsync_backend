package com.example.finalapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.finalapp.ui.screen.*

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Login.route) {
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
        composable(Screen.Settings.route) { SettingsScreen(navController) }
    }
}
