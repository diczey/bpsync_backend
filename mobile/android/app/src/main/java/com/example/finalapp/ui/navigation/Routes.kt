package com.example.finalapp.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Dashboard : Screen("dashboard")
    object HealthStatus : Screen("health_status")
    object BLEConnection : Screen("ble_connection")
    object Trends : Screen("trends")
    object Measurements : Screen("measurements")
    object BloodPressure : Screen("blood_pressure")
    object Pulse : Screen("pulse")
    object PPG : Screen("ppg")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
}
