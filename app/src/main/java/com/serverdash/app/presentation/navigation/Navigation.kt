package com.serverdash.app.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.serverdash.app.domain.repository.ServerRepository
import com.serverdash.app.presentation.screens.about.AboutScreen
import com.serverdash.app.presentation.screens.claudecode.ClaudeCodeScreen
import com.serverdash.app.presentation.screens.claudeterminal.ClaudeTerminalScreen
import com.serverdash.app.presentation.screens.claudeterminal.ImmersiveTerminalContainer
import com.serverdash.app.presentation.screens.dashboard.DashboardScreen
import com.serverdash.app.presentation.screens.detail.ServiceDetailScreen
import com.serverdash.app.data.encryption.EncryptionManager
import com.serverdash.app.presentation.screens.fleet.FleetScreen
import com.serverdash.app.presentation.screens.privacy.PrivacyScreen
import com.serverdash.app.presentation.screens.git.GitScreen
import com.serverdash.app.presentation.screens.guardian.GuardianScreen
import com.serverdash.app.presentation.screens.security.SecurityScreen
import com.serverdash.app.presentation.screens.settings.SettingsScreen
import com.serverdash.app.presentation.screens.server.ServerScreen
import com.serverdash.app.presentation.screens.setup.SetupScreen
import com.serverdash.app.presentation.screens.terminal.TerminalScreen
import com.serverdash.app.presentation.screens.theme.ThemeScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class Screen(val route: String) {
    data object Setup : Screen("setup")
    data object Dashboard : Screen("dashboard")
    data object ServiceDetail : Screen("detail/{serviceName}/{serviceType}") {
        fun createRoute(name: String, type: String) = "detail/$name/$type"
    }
    data object Terminal : Screen("terminal")
    data object Settings : Screen("settings")
    data object ClaudeCode : Screen("claude_code")
    data object Fleet : Screen("fleet")
    data object Guardian : Screen("guardian")
    data object Security : Screen("security")
    data object Git : Screen("git")
    data object Theme : Screen("theme")
    data object Server : Screen("server")
    data object About : Screen("about")
    data object Privacy : Screen("privacy")
    data object ClaudeTerminal : Screen("claude_terminal")
    data object ClaudeTerminalImmersive : Screen("claude_terminal_immersive?contextType={contextType}&param1={param1}&param2={param2}&param3={param3}") {
        fun createRoute(
            contextType: String = "",
            param1: String = "",
            param2: String = "",
            param3: String = ""
        ): String {
            return "claude_terminal_immersive?contextType=$contextType&param1=$param1&param2=$param2&param3=$param3"
        }
    }
}

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    val encryptionManager: EncryptionManager
) : ViewModel() {
    private val _hasConfig = MutableStateFlow<Boolean?>(null) // null = loading
    val hasConfig = _hasConfig.asStateFlow()

    init {
        viewModelScope.launch {
            val config = serverRepository.getServerConfig()
            _hasConfig.value = config != null
        }
    }
}

@Composable
fun ServerDashNavHost(widgetDeepLink: String? = null) {
    val startupViewModel: StartupViewModel = hiltViewModel()
    val hasConfig by startupViewModel.hasConfig.collectAsState()

    if (hasConfig == null) {
        // Still checking — show nothing or a brief loader
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()
    val startDestination = if (hasConfig == true) Screen.Dashboard.route else Screen.Setup.route

    // Handle widget deep link navigation after the nav graph is ready
    LaunchedEffect(widgetDeepLink) {
        if (widgetDeepLink != null && hasConfig == true) {
            val route = when (widgetDeepLink) {
                "terminal" -> Screen.Terminal.route
                "claude_code" -> Screen.ClaudeCode.route
                else -> null
            }
            if (route != null) {
                navController.navigate(route) {
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                },
                encryptionManager = startupViewModel.encryptionManager
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToDetail = { name, type ->
                    navController.navigate(Screen.ServiceDetail.createRoute(name, type))
                },
                onNavigateToTerminal = { navController.navigate(Screen.Terminal.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToClaudeCode = { navController.navigate(Screen.ClaudeCode.route) },
                onNavigateToFleet = { navController.navigate(Screen.Fleet.route) },
                onNavigateToGuardian = { navController.navigate(Screen.Guardian.route) },
                onNavigateToGit = { navController.navigate(Screen.Git.route) },
                onNavigateToServer = { navController.navigate(Screen.Server.route) },
                onNavigateToSecurity = { navController.navigate(Screen.Security.route) },
                onNavigateToAbout = { navController.navigate(Screen.About.route) },
                onDebugWithClaude = { serviceName, serviceType ->
                    navController.navigate(
                        Screen.ClaudeTerminalImmersive.createRoute(
                            contextType = "service_debug",
                            param1 = serviceName,
                            param2 = serviceType
                        )
                    )
                },
                encryptionManager = startupViewModel.encryptionManager
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
                onNavigateBack = { navController.popBackStack() },
                onDebugWithClaude = { serviceName, serviceType ->
                    navController.navigate(
                        Screen.ClaudeTerminalImmersive.createRoute(
                            contextType = "service_debug",
                            param1 = serviceName,
                            param2 = serviceType
                        )
                    )
                }
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
                },
                onNavigateToSecurity = { navController.navigate(Screen.Security.route) },
                onNavigateToTheme = { navController.navigate(Screen.Theme.route) },
                onNavigateToPrivacy = { navController.navigate(Screen.Privacy.route) }
            )
        }

        composable(Screen.ClaudeCode.route) {
            ClaudeCodeScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToClaudeTerminal = { navController.navigate(Screen.ClaudeTerminal.route) }
            )
        }

        composable(Screen.ClaudeTerminal.route) {
            ClaudeTerminalScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Fleet.route) {
            FleetScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Guardian.route) {
            GuardianScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Security.route) {
            SecurityScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Git.route) {
            GitScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Server.route) {
            ServerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Theme.route) {
            ThemeScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.About.route) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Privacy.route) {
            PrivacyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ClaudeTerminalImmersive.route,
            arguments = listOf(
                navArgument("contextType") { type = NavType.StringType; defaultValue = "" },
                navArgument("param1") { type = NavType.StringType; defaultValue = "" },
                navArgument("param2") { type = NavType.StringType; defaultValue = "" },
                navArgument("param3") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val contextType = backStackEntry.arguments?.getString("contextType") ?: ""
            val param1 = backStackEntry.arguments?.getString("param1") ?: ""
            val param2 = backStackEntry.arguments?.getString("param2") ?: ""
            val param3 = backStackEntry.arguments?.getString("param3") ?: ""

            // Build initial prompt from context args
            val initialPrompt = when (contextType) {
                "service_debug" -> if (param1.isNotBlank()) {
                    "Debug the $param2 service '$param1'. Check its status and recent logs, then diagnose the issue."
                } else null
                "metric_alert" -> if (param1.isNotBlank()) {
                    "Analyze $param1 usage at $param2 (threshold: $param3). Find what's causing high usage."
                } else null
                "custom_error" -> if (param1.isNotBlank()) {
                    "Help debug this error from $param2: $param1"
                } else null
                else -> null
            }

            ImmersiveTerminalContainer(
                initialPrompt = initialPrompt,
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                },
                onNavigateToTerminal = { navController.navigate(Screen.Terminal.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToFleet = { navController.navigate(Screen.Fleet.route) },
                onNavigateToGuardian = { navController.navigate(Screen.Guardian.route) },
                onNavigateToGit = { navController.navigate(Screen.Git.route) },
                onNavigateToSecurity = { navController.navigate(Screen.Security.route) },
                onNavigateToClaudeCode = { navController.navigate(Screen.ClaudeCode.route) },
                onNavigateToAbout = { navController.navigate(Screen.About.route) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
