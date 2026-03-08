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
import androidx.biometric.BiometricManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.serverdash.app.data.encryption.EncryptionManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    encryptionManager: EncryptionManager? = null,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onSetupComplete()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup - Step ${state.currentStep + 1} of 4") }
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
                progress = { (state.currentStep + 1) / 4f },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            when (state.currentStep) {
                0 -> ConnectionStep(state, viewModel::onEvent)
                1 -> DiscoveryStep(state, viewModel::onEvent)
                2 -> PinServicesStep(state, viewModel::onEvent)
                3 -> EncryptionStep(
                    encryptionManager = encryptionManager,
                    onBack = { viewModel.onEvent(SetupEvent.PrevStep) },
                    onComplete = { viewModel.onEvent(SetupEvent.CompleteSetup) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConnectionStep(state: SetupUiState, onEvent: (SetupEvent) -> Unit) {
    var passwordVisible by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // Detect SSH private key on clipboard
    var clipboardKey by remember { mutableStateOf<String?>(null) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val clipText = clipboardManager.getText()?.text ?: ""
            if (clipText.contains("-----BEGIN") && clipText.contains("PRIVATE KEY-----")) {
                clipboardKey = clipText
            }
        } catch (_: Exception) { }
    }

    fun Modifier.scrollOnFocus(bringIntoViewRequester: BringIntoViewRequester): Modifier {
        return this
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                }
            }
    }

    var sudoPasswordVisible by remember { mutableStateOf(false) }

    val hostBiv = remember { BringIntoViewRequester() }
    val portBiv = remember { BringIntoViewRequester() }
    val userBiv = remember { BringIntoViewRequester() }
    val authBiv = remember { BringIntoViewRequester() }
    val sudoBiv = remember { BringIntoViewRequester() }
    val buttonBiv = remember { BringIntoViewRequester() }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Server Connection", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
        }
        // SSH key detected on clipboard banner
        if (clipboardKey != null && !dismissed && state.privateKey.isBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "SSH private key detected on clipboard",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Tap to autofill and switch to key auth",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        TextButton(onClick = {
                            onEvent(SetupEvent.UpdateAuthType("key"))
                            onEvent(SetupEvent.UpdatePrivateKey(clipboardKey!!))
                            dismissed = true
                        }) {
                            Text("Use it")
                        }
                        IconButton(onClick = { dismissed = true }) {
                            Icon(Icons.Default.Close, "Dismiss", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
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
            OutlinedTextField(
                value = state.sudoPassword,
                onValueChange = { onEvent(SetupEvent.UpdateSudoPassword(it)) },
                label = { Text("Sudo Password") },
                leadingIcon = { Icon(Icons.Default.AdminPanelSettings, null) },
                trailingIcon = {
                    IconButton(onClick = { sudoPasswordVisible = !sudoPasswordVisible }) {
                        Icon(
                            if (sudoPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null
                        )
                    }
                },
                visualTransformation = if (sudoPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                supportingText = { Text("Leave blank for passwordless sudo") },
                modifier = Modifier.fillMaxWidth().scrollOnFocus(sudoBiv),
                singleLine = true
            )
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
                onClick = { onEvent(SetupEvent.NextStep) },
                modifier = Modifier.weight(1f),
                enabled = state.selectedServices.isNotEmpty()
            ) { Text("Next: Security") }
        }
    }
}

@Composable
private fun EncryptionStep(
    encryptionManager: EncryptionManager?,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }
    // null = not attempted, true = success, false = failure
    var encryptionResult by remember { mutableStateOf<Boolean?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var usedBiometric by remember { mutableStateOf(false) }

    fun attemptEncryption(withBiometric: Boolean) {
        encryptionManager?.enableEncryption(withBiometric)?.fold(
            onSuccess = {
                encryptionResult = true
                usedBiometric = withBiometric
                errorMessage = null
            },
            onFailure = { e ->
                encryptionResult = false
                errorMessage = e.message ?: "Unknown error"
            }
        ) ?: run {
            encryptionResult = false
            errorMessage = "Encryption manager not available"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            when (encryptionResult) {
                true -> Icons.Default.CheckCircle
                false -> Icons.Default.Error
                null -> Icons.Default.Shield
            },
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = when (encryptionResult) {
                true -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.primary
            }
        )
        Text(
            when (encryptionResult) {
                true -> "Encryption Enabled"
                false -> "Encryption Failed"
                null -> "Protect Your Data"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        when (encryptionResult) {
            true -> {
                Text(
                    if (usedBiometric) "Your data is now encrypted and protected with device biometrics."
                    else "Your data is now encrypted with AES-256.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF4CAF50).copy(alpha = 0.1f)
                    )
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lock, null,
                            tint = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Encryption enabled",
                                fontWeight = FontWeight.Bold,
                                color = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                            )
                            Text(
                                "Database will be encrypted on next app restart",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            false -> {
                Text(
                    "Something went wrong while setting up encryption. You can try again or skip for now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                errorMessage?.let { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { encryptionResult = null; errorMessage = null },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Try Again") }
            }
            null -> {
                Text(
                    "ServerDash stores SSH credentials, passwords, and private keys on this device. We strongly recommend enabling encryption to keep them secure.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("AES-256 database encryption (SQLCipher)", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Keys stored in Android hardware KeyStore", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Encrypted preferences for sensitive settings", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (biometricAvailable) {
                    Button(
                        onClick = { attemptEncryption(true) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Fingerprint, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Enable with Biometrics")
                    }
                }

                OutlinedButton(
                    onClick = { attemptEncryption(false) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (biometricAvailable) "Enable without Biometrics" else "Enable Encryption")
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) { Text("Back") }
            Button(
                onClick = {
                    if (encryptionResult != true) {
                        encryptionManager?.dismissEncryptionPrompt()
                    }
                    onComplete()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (encryptionResult == true) "Finish Setup" else "Remind Me Later")
            }
        }
    }
}
