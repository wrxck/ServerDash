package com.serverdash.app.presentation.screens.claudeterminal

import android.app.Activity
import android.os.Build
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.serverdash.app.presentation.screens.terminal.UnifiedTerminalScreen

@Composable
fun ImmersiveTerminalContainer(
    contextType: String = "",
    contextParams: String = "",
    onNavigateToDashboard: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToFleet: () -> Unit,
    onNavigateToGuardian: () -> Unit,
    onNavigateToGit: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToClaudeCode: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val view = LocalView.current
    var showOverlay by remember { mutableStateOf(false) }

    // Enter immersive mode on entry, restore on exit
    DisposableEffect(Unit) {
        val activity = view.context as? Activity
        val window = activity?.window

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insetsController = window?.insetsController
            insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            onDispose {
                insetsController?.show(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window?.decorView?.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            )
            onDispose {
                @Suppress("DEPRECATION")
                window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        UnifiedTerminalScreen(
            onNavigateBack = onNavigateBack,
            isImmersive = true,
            isClaudeMode = true,
            contextType = contextType,
            contextParams = contextParams,
        )

        // Floating pill/chevron at the right edge
        if (!showOverlay) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    .clickable { showOverlay = true }
            )
        }

        // Navigation overlay
        AnimatedVisibility(
            visible = showOverlay,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it })
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { showOverlay = false }
            ) {
                Card(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(280.dp)
                        .fillMaxHeight()
                        .clickable(enabled = false) {}, // Prevent clicks from passing through
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 24.dp)
                    ) {
                        // Header
                        Text(
                            "Return to Terminal",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .clickable {
                                    showOverlay = false
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Navigation items
                        OverlayNavItem(
                            icon = Icons.Default.Dashboard,
                            label = "Dashboard",
                            onClick = { showOverlay = false; onNavigateToDashboard() }
                        )
                        OverlayNavItem(
                            icon = Icons.Default.Terminal,
                            label = "Terminal",
                            onClick = { showOverlay = false; onNavigateToTerminal() }
                        )
                        OverlayNavItem(
                            icon = Icons.Default.Settings,
                            label = "Settings",
                            onClick = { showOverlay = false; onNavigateToSettings() }
                        )
                        OverlayNavItem(
                            icon = Icons.Default.Dns,
                            label = "Fleet",
                            onClick = { showOverlay = false; onNavigateToFleet() }
                        )
                        OverlayNavItem(
                            icon = Icons.Default.Shield,
                            label = "Guardian",
                            onClick = { showOverlay = false; onNavigateToGuardian() }
                        )
                        OverlayNavItem(
                            icon = Icons.Default.Code,
                            label = "Git",
                            onClick = { showOverlay = false; onNavigateToGit() }
                        )
                        OverlayNavItem(
                            icon = Icons.Default.Security,
                            label = "Security",
                            onClick = { showOverlay = false; onNavigateToSecurity() }
                        )
                        OverlayNavItem(
                            icon = Icons.Default.SmartToy,
                            label = "Claude Code",
                            onClick = { showOverlay = false; onNavigateToClaudeCode() }
                        )
                        OverlayNavItem(
                            icon = Icons.Default.Info,
                            label = "About",
                            onClick = { showOverlay = false; onNavigateToAbout() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayNavItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
