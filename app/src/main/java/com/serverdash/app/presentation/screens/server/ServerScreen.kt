package com.serverdash.app.presentation.screens.server

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(
    onNavigateBack: () -> Unit,
    viewModel: ServerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val tabs = ServerTab.entries

    // Snackbar messages
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(ServerEvent.DismissSnackbar)
        }
    }

    // Error dialog
    if (state.error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(ServerEvent.DismissError) },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(ServerEvent.DismissError) }) { Text("OK") }
            },
            title = { Text("Error") },
            text = { Text(state.error!!) },
        )
    }

    // Confirmation dialog
    state.pendingConfirmation?.let { action ->
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(ServerEvent.DismissConfirmation) },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(ServerEvent.ConfirmAction) }) {
                    Text(if (action.requiresBiometric) "Authenticate & Confirm" else "Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(ServerEvent.DismissConfirmation) }) {
                    Text("Cancel")
                }
            },
            title = { Text("Confirm Action") },
            text = { Text(action.label + if (action.requiresBiometric) "\n\nThis requires biometric authentication." else "") },
        )
    }

    // Biometric auth trigger
    val context = LocalContext.current
    val pendingBiometric = state.pendingBiometricAction
    LaunchedEffect(pendingBiometric) {
        if (pendingBiometric != null) {
            val activity = context as? FragmentActivity
            if (activity != null) {
                val executor = ContextCompat.getMainExecutor(activity)
                val callback = object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        viewModel.onEvent(ServerEvent.BiometricSucceeded)
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        viewModel.onEvent(ServerEvent.BiometricCancelled)
                    }
                    override fun onAuthenticationFailed() {}
                }
                val prompt = BiometricPrompt(activity, executor, callback)
                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Confirm: ${pendingBiometric.label}")
                    .setSubtitle("Authenticate to proceed with destructive action")
                    .setAllowedAuthenticators(
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                    )
                    .build()
                prompt.authenticate(info)
            } else {
                viewModel.onEvent(ServerEvent.BiometricSucceeded)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server Management") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = state.selectedTab,
                edgePadding = 8.dp,
                divider = {},
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { viewModel.onEvent(ServerEvent.SelectTab(index)) },
                        text = { Text(tab.label) },
                    )
                }
            }

            when (tabs[state.selectedTab]) {
                ServerTab.PACKAGES -> PackagesTab(state, viewModel::onEvent)
                ServerTab.FIREWALL -> FirewallTab(state, viewModel::onEvent)
                ServerTab.SYSTEM -> SystemTab(state, viewModel::onEvent)
                ServerTab.USERS -> UsersTab(state, viewModel::onEvent)
                ServerTab.CRON -> CronTab(state, viewModel::onEvent)
                ServerTab.SERVICES -> ServicesTab(state, viewModel::onEvent)
            }
        }
    }
}
