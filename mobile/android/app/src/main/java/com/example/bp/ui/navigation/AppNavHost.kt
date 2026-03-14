package com.example.bp.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bp.data.local.TokenManager
import com.example.bp.ui.screen.DashboardScreen
import com.example.bp.ui.screen.LoginScreen
import com.example.bp.ui.screen.ReadingsScreen
import com.example.bp.ui.screen.ProfileScreen
import com.example.bp.ui.screen.TrendsScreen
import com.example.bp.ui.screen.BLEScreen
import com.example.bp.ui.screen.ReportsScreen
import com.example.bp.ui.viewmodel.AuthViewModel
import com.example.bp.ui.viewmodel.AuthViewModelFactory
import com.example.bp.ui.viewmodel.DashboardViewModel
import com.example.bp.ui.viewmodel.ProfileViewModel
import com.example.bp.ui.viewmodel.BLEViewModel
import com.example.bp.ui.viewmodel.ReportViewModel

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }

    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(tokenManager))
    val dashboardViewModel: DashboardViewModel = viewModel()
    val profileViewModel: ProfileViewModel = viewModel()
    val bleViewModel: BLEViewModel = viewModel()
    val reportViewModel: ReportViewModel = viewModel()

    // Check persisted token to determine start destination
    val savedToken by tokenManager.accessToken.collectAsState(initial = null)
    val startDestination = if (savedToken != null) Routes.DASHBOARD else Routes.LOGIN

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = { /* handled inline in LoginScreen */ }
            )
        }
        composable(Routes.DASHBOARD) {
            val token by tokenManager.accessToken.collectAsState(initial = "")
            DashboardScreen(
                token = token ?: "",
                viewModel = dashboardViewModel,
                onNavigateToReadings = { navController.navigate(Routes.READINGS) },
                onNavigateToTrends = { navController.navigate(Routes.TRENDS) },
                onNavigateToProfile = { navController.navigate(Routes.PROFILE) },
                onNavigateToBle = { navController.navigate(Routes.BLE) },
                onNavigateToReports = { navController.navigate(Routes.REPORTS) },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.READINGS) {
            val token by tokenManager.accessToken.collectAsState(initial = "")
            ReadingsScreen(
                token = token ?: "",
                viewModel = dashboardViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.TRENDS) {
            val token by tokenManager.accessToken.collectAsState(initial = "")
            TrendsScreen(
                token = token ?: "",
                viewModel = dashboardViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.PROFILE) {
            val token by tokenManager.accessToken.collectAsState(initial = "")
            ProfileScreen(
                token = token ?: "",
                viewModel = profileViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.BLE) {
            val token by tokenManager.accessToken.collectAsState(initial = "")
            BLEScreen(
                token = token ?: "",
                viewModel = bleViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.REPORTS) {
            val token by tokenManager.accessToken.collectAsState(initial = "")
            ReportsScreen(
                token = token ?: "",
                viewModel = reportViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
