package com.serverdash.app.core.util

import android.content.Context
import android.content.res.Configuration
import android.view.Display
import android.view.WindowManager
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

enum class DisplayType { LCD, OLED, UNKNOWN }

fun detectDisplayType(context: Context): DisplayType {
    // Samsung Tab A9+ uses LCD. Real detection would use Build.MODEL or display properties.
    val model = android.os.Build.MODEL.lowercase()
    return when {
        model.contains("tab a9") -> DisplayType.LCD
        model.contains("galaxy s2") || model.contains("galaxy s3") ||
        model.contains("galaxy s4") -> DisplayType.OLED
        else -> DisplayType.UNKNOWN
    }
}

@Composable
fun Modifier.pixelShift(enabled: Boolean, maxOffset: Int = 4): Modifier {
    if (!enabled) return this

    var offsetX by remember { mutableIntStateOf(0) }
    var offsetY by remember { mutableIntStateOf(0) }

    LaunchedEffect(enabled) {
        var step = 0
        while (true) {
            delay(60_000) // shift every 60 seconds
            step = (step + 1) % 4
            offsetX = when (step) { 0 -> 0; 1 -> maxOffset; 2 -> 0; 3 -> -maxOffset; else -> 0 }
            offsetY = when (step) { 0 -> 0; 1 -> maxOffset; 2 -> -maxOffset; 3 -> 0; else -> 0 }
        }
    }

    return this.offset { IntOffset(offsetX, offsetY) }
}

@Composable
fun isLandscape(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}

fun formatUptime(seconds: Long): String {
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
