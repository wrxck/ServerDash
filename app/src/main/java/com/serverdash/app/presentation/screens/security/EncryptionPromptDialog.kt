package com.serverdash.app.presentation.screens.security

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.serverdash.app.data.encryption.EncryptionManager

@Composable
fun EncryptionPromptDialog(
    biometricAvailable: Boolean,
    encryptionManager: EncryptionManager,
    onDismissAfterSuccess: () -> Unit,
    onRemindLater: () -> Unit
) {
    var status by remember { mutableStateOf<EncryptionStatus>(EncryptionStatus.Idle) }

    AlertDialog(
        onDismissRequest = {
            if (status !is EncryptionStatus.Success) onRemindLater()
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
        icon = {
            val (icon, tint) = when (status) {
                is EncryptionStatus.Success -> Icons.Default.CheckCircle to Color(0xFF66BB6A)
                is EncryptionStatus.Error -> Icons.Default.Error to MaterialTheme.colorScheme.error
                else -> Icons.Default.Shield to MaterialTheme.colorScheme.primary
            }
            Icon(icon, null, modifier = Modifier.size(48.dp), tint = tint)
        },
        title = {
            Text(
                when (status) {
                    is EncryptionStatus.Success -> "Encryption enabled"
                    is EncryptionStatus.Error -> "Encryption failed"
                    else -> "Protect your data"
                },
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (val s = status) {
                    is EncryptionStatus.Success -> {
                        Text(
                            if (s.withBiometric) "Your data is now encrypted and protected with device biometrics."
                            else "Your data is now encrypted with AES-256.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "The app will need to restart to apply encryption to the database.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onDismissAfterSuccess,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Done") }
                    }
                    is EncryptionStatus.Error -> {
                        Text(
                            "Something went wrong while setting up encryption.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { status = EncryptionStatus.Idle },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Try Again") }
                        TextButton(onClick = onRemindLater) {
                            Text("Remind me later")
                        }
                    }
                    is EncryptionStatus.Idle -> {
                        Text(
                            "ServerDash stores SSH credentials, passwords, and private keys on this device. Enable encryption to keep them secure.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))

                        if (biometricAvailable) {
                            Button(
                                onClick = {
                                    encryptionManager.enableEncryption(true).fold(
                                        onSuccess = { status = EncryptionStatus.Success(withBiometric = true) },
                                        onFailure = { status = EncryptionStatus.Error(it.message ?: "Unknown error") }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Fingerprint, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Encrypt with Biometrics")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                encryptionManager.enableEncryption(false).fold(
                                    onSuccess = { status = EncryptionStatus.Success(withBiometric = false) },
                                    onFailure = { status = EncryptionStatus.Error(it.message ?: "Unknown error") }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Lock, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (biometricAvailable) "Encrypt without Biometrics" else "Enable Encryption")
                        }

                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = onRemindLater) {
                            Text("Remind me later")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

private sealed interface EncryptionStatus {
    data object Idle : EncryptionStatus
    data class Success(val withBiometric: Boolean) : EncryptionStatus
    data class Error(val message: String) : EncryptionStatus
}
