package com.serverdash.app.presentation.screens.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// tokyo night colour scheme
private val BarBackground = Color(0xFF1A1B26)
private val ActiveTabBackground = Color(0xFF292E42)
private val ActiveTabText = Color(0xFF7AA2F7)
private val InactiveTabBackground = Color(0xFF16161E)
private val InactiveTabText = Color(0xFF565F89)
private val CloseIconTint = Color(0xFF565F89)
private val AddButtonTint = Color(0xFF565F89)
private val TmuxIndicatorColor = Color(0xFF9ECE6A)

@Composable
fun TerminalTabBar(
    sessions: List<TerminalSessionInfo>,
    activeIndex: Int,
    onSelectSession: (Int) -> Unit,
    onCloseSession: (Int) -> Unit,
    onAddSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = BarBackground,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sessions.forEachIndexed { index, session ->
                val isActive = index == activeIndex
                TerminalTab(
                    session = session,
                    isActive = isActive,
                    onSelect = { onSelectSession(index) },
                    onClose = { onCloseSession(index) },
                )
            }

            // add session button
            Surface(
                onClick = onAddSession,
                shape = RoundedCornerShape(8.dp),
                color = InactiveTabBackground,
                modifier = Modifier.height(32.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add session",
                        tint = AddButtonTint,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalTab(
    session: TerminalSessionInfo,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val backgroundColor = if (isActive) ActiveTabBackground else InactiveTabBackground
    val textColor = if (isActive) ActiveTabText else InactiveTabText

    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        modifier = Modifier.height(32.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (session.isTmux) {
                Text(
                    text = "\u27F3",
                    color = TmuxIndicatorColor,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            Text(
                text = session.name,
                color = textColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(20.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = CloseIconTint,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close session",
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}
