package com.serverdash.app.presentation.screens.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onSetupComplete()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup - Step ${state.currentStep + 1} of 3") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(16.dp)
        ) {
            // Step indicator
            LinearProgressIndicator(
                progress = { (state.currentStep + 1) / 3f },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            when (state.currentStep) {
                0 -> ConnectionStep(state, viewModel::onEvent)
                1 -> DiscoveryStep(state, viewModel::onEvent)
                2 -> PinServicesStep(state, viewModel::onEvent)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConnectionStep(state: SetupUiState, onEvent: (SetupEvent) -> Unit) {
    var passwordVisible by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun Modifier.scrollOnFocus(bringIntoViewRequester: BringIntoViewRequester): Modifier {
        return this
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                }
            }
    }

    val hostBiv = remember { BringIntoViewRequester() }
    val portBiv = remember { BringIntoViewRequester() }
    val userBiv = remember { BringIntoViewRequester() }
    val authBiv = remember { BringIntoViewRequester() }
    val buttonBiv = remember { BringIntoViewRequester() }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Server Connection", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
        }
        item {
            OutlinedTextField(
                value = state.host,
                onValueChange = { onEvent(SetupEvent.UpdateHost(it)) },
                label = { Text("Hostname / IP") },
                leadingIcon = { Icon(Icons.Default.Dns, null) },
                modifier = Modifier.fillMaxWidth().scrollOnFocus(hostBiv),
                singleLine = true
            )
        }
        item {
            OutlinedTextField(
                value = state.port,
                onValueChange = { onEvent(SetupEvent.UpdatePort(it)) },
                label = { Text("Port") },
                leadingIcon = { Icon(Icons.Default.Tag, null) },
                modifier = Modifier.fillMaxWidth().scrollOnFocus(portBiv),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        item {
            OutlinedTextField(
                value = state.username,
                onValueChange = { onEvent(SetupEvent.UpdateUsername(it)) },
                label = { Text("Username") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                modifier = Modifier.fillMaxWidth().scrollOnFocus(userBiv),
                singleLine = true
            )
        }
        item {
            // Auth type selector
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Auth: ", style = MaterialTheme.typography.bodyLarge)
                FilterChip(
                    selected = state.authType == "password",
                    onClick = { onEvent(SetupEvent.UpdateAuthType("password")) },
                    label = { Text("Password") },
                    modifier = Modifier.padding(end = 8.dp)
                )
                FilterChip(
                    selected = state.authType == "key",
                    onClick = { onEvent(SetupEvent.UpdateAuthType("key")) },
                    label = { Text("SSH Key") }
                )
            }
        }
        item {
            if (state.authType == "password") {
                OutlinedTextField(
                    value = state.password,
                    onValueChange = { onEvent(SetupEvent.UpdatePassword(it)) },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                null
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().scrollOnFocus(authBiv),
                    singleLine = true
                )
            } else {
                OutlinedTextField(
                    value = state.privateKey,
                    onValueChange = { onEvent(SetupEvent.UpdatePrivateKey(it)) },
                    label = { Text("Private Key (paste or path)") },
                    leadingIcon = { Icon(Icons.Default.Key, null) },
                    modifier = Modifier.fillMaxWidth().scrollOnFocus(authBiv),
                    minLines = 3,
                    maxLines = 6
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.passphrase,
                    onValueChange = { onEvent(SetupEvent.UpdatePassphrase(it)) },
                    label = { Text("Passphrase (optional)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().scrollOnFocus(buttonBiv),
                    singleLine = true
                )
            }
        }
        item {
            state.connectionError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = { onEvent(SetupEvent.Connect) },
                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(buttonBiv),
                enabled = !state.isConnecting && state.host.isNotBlank() && state.username.isNotBlank()
            ) {
                if (state.isConnecting) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.isConnecting) "Connecting..." else "Connect")
            }
        }
    }
}

@Composable
private fun DiscoveryStep(state: SetupUiState, onEvent: (SetupEvent) -> Unit) {
    Column {
        Text("Discover Services", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Find running systemctl and Docker services on your server.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onEvent(SetupEvent.DiscoverServices) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isDiscovering
        ) {
            if (state.isDiscovering) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (state.isDiscovering) "Discovering..." else "Discover Services")
        }

        if (state.discoveredServices.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Found ${state.discoveredServices.size} services", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onEvent(SetupEvent.NextStep) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Next: Select Services")
            }
        }
    }
}

@Composable
private fun PinServicesStep(state: SetupUiState, onEvent: (SetupEvent) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Pin Services", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { onEvent(SetupEvent.SelectAll) }) {
                Text("Select All")
            }
        }
        Text(
            "Select services to monitor on your dashboard.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(state.discoveredServices) { service ->
                val isSelected = service.name in state.selectedServices
                ListItem(
                    headlineContent = { Text(service.displayName) },
                    supportingContent = { Text("${service.type.name} - ${service.status.name}") },
                    leadingContent = {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onEvent(SetupEvent.ToggleService(service.name)) }
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onEvent(SetupEvent.PrevStep) },
                modifier = Modifier.weight(1f)
            ) { Text("Back") }
            Button(
                onClick = { onEvent(SetupEvent.CompleteSetup) },
                modifier = Modifier.weight(1f),
                enabled = state.selectedServices.isNotEmpty()
            ) { Text("Finish Setup") }
        }
    }
}
