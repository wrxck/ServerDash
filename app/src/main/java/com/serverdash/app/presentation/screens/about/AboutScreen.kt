package com.serverdash.app.presentation.screens.about

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val GITHUB_URL = "https://github.com/wrxck"
private const val DONATE_URL = "https://donate.stripe.com/6oU4grbmIc8PdXH2Y3bjW02"
private const val APP_VERSION = "1.0.0"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var logoTapCount by remember { mutableIntStateOf(0) }
    var easterEggActive by remember { mutableStateOf(false) }
    var devModeUnlocked by remember { mutableStateOf(false) }
    var matrixMode by remember { mutableStateOf(false) }
    var konamiIndex by remember { mutableIntStateOf(0) }

    // Easter egg: tap logo 7 times
    LaunchedEffect(logoTapCount) {
        if (logoTapCount >= 7 && !devModeUnlocked) {
            devModeUnlocked = true
            easterEggActive = true
        }
    }

    // Animated gradient for easter egg mode
    val infiniteTransition = rememberInfiniteTransition(label = "aboutAnim")
    val hueRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "hue"
    )
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "heart"
    )
    val logoRotation by animateFloatAsState(
        targetValue = if (easterEggActive) 360f else 0f,
        animationSpec = tween(800), label = "logoRot"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App logo / icon
            item {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(
                            if (easterEggActive) Brush.sweepGradient(
                                listOf(Color(0xFF5CCFE6), Color(0xFFF0B866), Color(0xFFCBB2F0), Color(0xFF66BB6A), Color(0xFF5CCFE6))
                            ) else Brush.linearGradient(
                                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                            )
                        )
                        .rotate(logoRotation)
                        .clickable { logoTapCount++ },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Dashboard,
                        null,
                        Modifier.size(48.dp),
                        tint = Color.White
                    )
                }
            }

            // App name and version
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "ServerDash",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "v$APP_VERSION",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Made with love
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Made with ", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "<3",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color(0xFFEF5350),
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.scale(heartScale)
                    )
                    Text(" by ", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Matt Hesketh",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Description
            item {
                Text(
                    "A powerful server management dashboard for Android. Monitor services, metrics, and infrastructure from anywhere.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Links
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        ListItem(
                            headlineContent = { Text("GitHub") },
                            supportingContent = { Text("@wrxck") },
                            leadingContent = { Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp)) },
                            modifier = Modifier.clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                            }
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text("Buy Me a Coffee") },
                            supportingContent = { Text("Support development") },
                            leadingContent = { Icon(Icons.Default.Favorite, null, tint = Color(0xFFEF5350)) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp)) },
                            modifier = Modifier.clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DONATE_URL)))
                            }
                        )
                    }
                }
            }

            // Tech stack
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Built With", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        val techs = listOf(
                            "Kotlin" to "Language",
                            "Jetpack Compose" to "UI Framework",
                            "Material 3" to "Design System",
                            "Hilt" to "Dependency Injection",
                            "SSHJ" to "SSH Client",
                            "JetBrains Mono" to "Typography"
                        )
                        techs.forEach { (name, desc) ->
                            Row(Modifier.padding(vertical = 2.dp)) {
                                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.width(140.dp))
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Features
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Features", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        val features = listOf(
                            "SSH-based server monitoring",
                            "Systemd & Docker service management",
                            "Real-time CPU/Memory/Disk metrics",
                            "Claude Code integration",
                            "Git & GitHub management",
                            "Fleet application discovery",
                            "Guardian security monitoring",
                            "Interactive terminal",
                            "Alert rules & notifications",
                            "Encrypted credential storage"
                        )
                        features.forEach { feature ->
                            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = Color(0xFF66BB6A))
                                Spacer(Modifier.width(8.dp))
                                Text(feature, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Developer mode / easter eggs
            if (devModeUnlocked) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Developer Mode Unlocked", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "You found the secret! Here's what we know about you:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            val facts = listOf(
                                "You tapped a logo 7 times. Dedication.",
                                "Your server probably has 42 nginx configs.",
                                "You check uptime before breakfast.",
                                "\"It works on my machine\" is your mantra.",
                                "sudo rm -rf / is never the answer (usually)."
                            )
                            facts.forEach { fact ->
                                Text(
                                    "- $fact",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { matrixMode = !matrixMode }) {
                                    Text(if (matrixMode) "Exit Matrix" else "Enter the Matrix")
                                }
                            }
                        }
                    }
                }
            }

            // Matrix mode: falling characters
            if (matrixMode) {
                item {
                    Card(
                        Modifier.fillMaxWidth().height(200.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black)
                    ) {
                        Box(Modifier.fillMaxSize().padding(8.dp)) {
                            val chars = remember {
                                (1..20).map { col ->
                                    (1..15).map { "01".random().toString() }
                                }
                            }
                            Column {
                                chars.take(10).forEachIndexed { row, line ->
                                    val alpha by infiniteTransition.animateFloat(
                                        initialValue = 0.2f, targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            tween((300..800).random(), delayMillis = row * 100),
                                            RepeatMode.Reverse
                                        ),
                                        label = "matrix$row"
                                    )
                                    Text(
                                        line.joinToString(" "),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = Color(0xFF00FF00).copy(alpha = alpha)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tap counter hint
            if (!devModeUnlocked && logoTapCount in 3..6) {
                item {
                    Text(
                        "${7 - logoTapCount} more taps...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // Footer
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "ServerDash (c) 2024-2026 Matt Hesketh",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
