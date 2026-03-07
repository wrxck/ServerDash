package com.serverdash.app.presentation.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.serverdash.app.domain.repository.ServerRepository
import com.serverdash.app.presentation.screens.dashboard.DashboardScreen
import com.serverdash.app.presentation.screens.detail.ServiceDetailScreen
import com.serverdash.app.presentation.screens.settings.SettingsScreen
import com.serverdash.app.presentation.screens.setup.SetupScreen
import com.serverdash.app.presentation.screens.terminal.TerminalScreen

sealed class Screen(val route: String) {
    data object Setup : Screen("setup")
    data object Dashboard : Screen("dashboard")
    data object ServiceDetail : Screen("detail/{serviceName}/{serviceType}") {
        fun createRoute(name: String, type: String) = "detail/$name/$type"
    }
    data object Terminal : Screen("terminal")
    data object Settings : Screen("settings")
}

@Composable
fun ServerDashNavHost() {
    val navController = rememberNavController()

    // Determine start destination based on whether server is configured
    // For simplicity, always start at setup if no config exists
    val startDestination = Screen.Setup.route

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToDetail = { name, type ->
                    navController.navigate(Screen.ServiceDetail.createRoute(name, type))
                },
                onNavigateToTerminal = { navController.navigate(Screen.Terminal.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(
            route = Screen.ServiceDetail.route,
            arguments = listOf(
                navArgument("serviceName") { type = NavType.StringType },
                navArgument("serviceType") { type = NavType.StringType }
            )
        ) {
            ServiceDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Terminal.route) {
            TerminalScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onDisconnected = {
                    navController.navigate(Screen.Setup.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
