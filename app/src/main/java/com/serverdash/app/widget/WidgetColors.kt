package com.serverdash.app.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider

/**
 * Color constants for widget rendering. Uses single colors that work
 * reasonably in both light and dark modes.
 */
object WidgetColors {
    val surfaceColor = ColorProvider(Color(0xFF1C1B1F))
    val onSurfaceColor = ColorProvider(Color(0xFFE6E1E5))
    val onSurfaceSecondaryColor = ColorProvider(Color(0xFFCAC4D0))
    val primaryColor = ColorProvider(Color(0xFFD0BCFF))
    val statusGreen = ColorProvider(Color(0xFF66BB6A))
    val statusRed = ColorProvider(Color(0xFFEF5350))
    val statusYellow = ColorProvider(Color(0xFFFFCA28))
    val statusGrey = ColorProvider(Color(0xFF9E9E9E))
    val failedBg = ColorProvider(Color(0xFF3E1C1C))
    val actionBg = ColorProvider(Color(0xFF332D41))
}
