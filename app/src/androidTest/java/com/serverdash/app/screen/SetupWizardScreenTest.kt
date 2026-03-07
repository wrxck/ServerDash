package com.serverdash.app.screen

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.serverdash.app.core.theme.ServerDashTheme
import com.serverdash.app.presentation.screens.setup.*
import org.junit.Rule
import org.junit.Test

class SetupWizardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun connectionStep_showsAllFields() {
        composeTestRule.setContent {
            ServerDashTheme {
                ConnectionStep(state = SetupUiState(), onEvent = {})
            }
        }
        composeTestRule.onNodeWithText("Hostname / IP").assertIsDisplayed()
        composeTestRule.onNodeWithText("Port").assertIsDisplayed()
        composeTestRule.onNodeWithText("Username").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
    }

    @Test
    fun connectionStep_connectButtonDisabledWhenEmpty() {
        composeTestRule.setContent {
            ServerDashTheme {
                ConnectionStep(state = SetupUiState(), onEvent = {})
            }
        }
        composeTestRule.onNodeWithText("Connect").assertIsNotEnabled()
    }

    @Test
    fun connectionStep_connectButtonEnabledWithInput() {
        composeTestRule.setContent {
            ServerDashTheme {
                ConnectionStep(
                    state = SetupUiState(host = "192.168.1.1", username = "admin"),
                    onEvent = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Connect").assertIsEnabled()
    }

    @Test
    fun connectionStep_showsError() {
        composeTestRule.setContent {
            ServerDashTheme {
                ConnectionStep(
                    state = SetupUiState(connectionError = "Connection refused"),
                    onEvent = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Connection refused").assertIsDisplayed()
    }

    @Test
    fun connectionStep_showsLoadingOnConnecting() {
        composeTestRule.setContent {
            ServerDashTheme {
                ConnectionStep(
                    state = SetupUiState(isConnecting = true, host = "h", username = "u"),
                    onEvent = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Connecting...").assertIsDisplayed()
    }
}
