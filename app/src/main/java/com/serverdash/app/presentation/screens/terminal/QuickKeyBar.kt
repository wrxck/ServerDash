package com.serverdash.app.presentation.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.connectbot.terminal.TerminalEmulator

private object VTermKey {
    const val ESCAPE = 4
    const val TAB = 2
    const val ENTER = 1
    const val BACKSPACE = 3
    const val UP = 5
    const val DOWN = 6
    const val LEFT = 7
    const val RIGHT = 8
    const val DEL = 10
    const val HOME = 11
    const val END = 12
    const val PAGEUP = 13
    const val PAGEDOWN = 14
}

private const val VTERM_MOD_SHIFT = 1
private const val VTERM_MOD_ALT = 2
private const val VTERM_MOD_CTRL = 4

private val BarBackground = Color(0xFF16161E)
private val KeyBackground = Color(0xFF292E42)
private val KeyForeground = Color(0xFF7AA2F7)
private val ActiveToggleBackground = Color(0xFF7AA2F7)
private val ActiveToggleForeground = Color(0xFF16161E)

@Composable
fun QuickKeyBar(
    emulator: TerminalEmulator?,
    modifier: Modifier = Modifier,
) {
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }

    fun buildModifiers(): Int {
        var mods = 0
        if (ctrlActive) mods = mods or VTERM_MOD_CTRL
        if (altActive) mods = mods or VTERM_MOD_ALT
        return mods
    }

    fun clearToggles() {
        ctrlActive = false
        altActive = false
    }

    fun dispatchSpecialKey(key: Int) {
        val mods = buildModifiers()
        emulator?.dispatchKey(mods, key)
        clearToggles()
    }

    fun dispatchChar(character: Char, forceCtrl: Boolean = false) {
        var mods = buildModifiers()
        if (forceCtrl) mods = mods or VTERM_MOD_CTRL
        emulator?.dispatchCharacter(mods, character)
        clearToggles()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BarBackground)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickKey(label = "Esc") { dispatchSpecialKey(VTermKey.ESCAPE) }
        QuickKey(label = "Tab") { dispatchSpecialKey(VTermKey.TAB) }

        QuickKey(
            label = "Ctrl",
            isActive = ctrlActive,
        ) { ctrlActive = !ctrlActive }

        QuickKey(
            label = "Alt",
            isActive = altActive,
        ) { altActive = !altActive }

        QuickKey(label = "^C") { dispatchChar('c', forceCtrl = true) }
        QuickKey(label = "^D") { dispatchChar('d', forceCtrl = true) }
        QuickKey(label = "^Z") { dispatchChar('z', forceCtrl = true) }
        QuickKey(label = "^L") { dispatchChar('l', forceCtrl = true) }

        QuickKey(label = "\u2191") { dispatchSpecialKey(VTermKey.UP) }
        QuickKey(label = "\u2193") { dispatchSpecialKey(VTermKey.DOWN) }
        QuickKey(label = "\u2190") { dispatchSpecialKey(VTermKey.LEFT) }
        QuickKey(label = "\u2192") { dispatchSpecialKey(VTermKey.RIGHT) }
    }
}

@Composable
private fun QuickKey(
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit,
) {
    val backgroundColor = if (isActive) ActiveToggleBackground else KeyBackground
    val contentColor = if (isActive) ActiveToggleForeground else KeyForeground

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor,
        contentColor = contentColor,
        modifier = Modifier.height(28.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp),
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
