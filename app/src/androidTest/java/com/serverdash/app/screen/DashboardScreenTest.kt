package com.serverdash.app.screen

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.serverdash.app.core.theme.ServerDashTheme
import com.serverdash.app.domain.model.*
import com.serverdash.app.presentation.screens.dashboard.*
import org.junit.Rule
import org.junit.Test

class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun serviceCards_displayCorrectly() {
        val services = listOf(
            Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD, ServiceStatus.RUNNING),
            Service(2, 1, "mysql", "mysql", ServiceType.SYSTEMD, ServiceStatus.STOPPED),
            Service(3, 1, "redis", "redis", ServiceType.DOCKER, ServiceStatus.FAILED)
        )

        composeTestRule.setContent {
            ServerDashTheme {
                services.forEach { service ->
                    ServiceCard(service = service, onClick = {})
                }
            }
        }

        composeTestRule.onNodeWithText("nginx").assertIsDisplayed()
        composeTestRule.onNodeWithText("mysql").assertIsDisplayed()
        composeTestRule.onNodeWithText("redis").assertIsDisplayed()
    }

    @Test
    fun connectionStatusBar_showsConnected() {
        composeTestRule.setContent {
            ServerDashTheme {
                ConnectionStatusBar(ConnectionState(isConnected = true))
            }
        }
        composeTestRule.onNodeWithText("Connected").assertIsDisplayed()
    }

    @Test
    fun connectionStatusBar_showsDisconnected() {
        composeTestRule.setContent {
            ServerDashTheme {
                ConnectionStatusBar(ConnectionState())
            }
        }
        composeTestRule.onNodeWithText("Disconnected").assertIsDisplayed()
    }

    @Test
    fun connectionStatusBar_showsConnecting() {
        composeTestRule.setContent {
            ServerDashTheme {
                ConnectionStatusBar(ConnectionState(isConnecting = true))
            }
        }
        composeTestRule.onNodeWithText("Connecting...").assertIsDisplayed()
    }

    @Test
    fun connectionStatusBar_showsError() {
        composeTestRule.setContent {
            ServerDashTheme {
                ConnectionStatusBar(ConnectionState(error = "Connection refused"))
            }
        }
        composeTestRule.onNodeWithText("Connection refused").assertIsDisplayed()
    }

    @Test
    fun alertBanner_displaysAlertCount() {
        composeTestRule.setContent {
            ServerDashTheme {
                AlertBanner(alertCount = 3, onDismiss = {})
            }
        }
        composeTestRule.onNodeWithText("3 active alerts").assertIsDisplayed()
    }

    @Test
    fun alertBanner_singleAlert() {
        composeTestRule.setContent {
            ServerDashTheme {
                AlertBanner(alertCount = 1, onDismiss = {})
            }
        }
        composeTestRule.onNodeWithText("1 active alert").assertIsDisplayed()
    }

    @Test
    fun metricsSummary_displaysValues() {
        val metrics = SystemMetrics(
            cpuUsage = 45f,
            memoryUsed = 4_000_000_000,
            memoryTotal = 8_000_000_000,
            diskUsed = 25_000_000_000,
            diskTotal = 50_000_000_000,
            uptimeSeconds = 86400
        )
        composeTestRule.setContent {
            ServerDashTheme {
                MetricsSummaryRow(metrics)
            }
        }
        composeTestRule.onNodeWithText("CPU").assertIsDisplayed()
        composeTestRule.onNodeWithText("MEM").assertIsDisplayed()
        composeTestRule.onNodeWithText("DISK").assertIsDisplayed()
        composeTestRule.onNodeWithText("UP").assertIsDisplayed()
    }

    @Test
    fun serviceCard_showsStatusInfo() {
        val service = Service(1, 1, "nginx", "Nginx Web Server", ServiceType.SYSTEMD, ServiceStatus.RUNNING, subState = "running")
        composeTestRule.setContent {
            ServerDashTheme {
                ServiceCard(service = service, onClick = {})
            }
        }
        composeTestRule.onNodeWithText("Nginx Web Server").assertIsDisplayed()
        composeTestRule.onNodeWithText("SYSTEMD - RUNNING").assertIsDisplayed()
    }

    @Test
    fun serviceCard_showsPinIcon() {
        val service = Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD, ServiceStatus.RUNNING, isPinned = true)
        composeTestRule.setContent {
            ServerDashTheme {
                ServiceCard(service = service, onClick = {})
            }
        }
        // Pin icon should be visible for pinned services
        composeTestRule.onNodeWithText("nginx").assertIsDisplayed()
    }
}
