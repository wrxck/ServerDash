package com.serverdash.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.serverdash.app.MainActivity

/**
 * Small 2x1 widget with quick-action buttons for Terminal and Claude Code.
 * Each button launches MainActivity with a deep-link URI that the activity
 * routes to the appropriate screen.
 */
class QuickActionsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                QuickActionsContent()
            }
        }
    }
}

@Composable
private fun QuickActionsContent() {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(WidgetColors.surfaceColor)
            .padding(12.dp)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Header
            Text(
                text = "ServerDash",
                style = TextStyle(
                    color = WidgetColors.onSurfaceSecondaryColor,
                    fontSize = 11.sp
                )
            )

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Buttons row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Terminal button
                ActionButton(
                    label = "\u25B6 Terminal",
                    deepLink = "serverdash://terminal",
                    modifier = GlanceModifier.defaultWeight()
                )

                Spacer(modifier = GlanceModifier.width(8.dp))

                // Claude Code button
                ActionButton(
                    label = "\u2728 Claude Code",
                    deepLink = "serverdash://claude_code",
                    modifier = GlanceModifier.defaultWeight()
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    deepLink: String,
    modifier: GlanceModifier = GlanceModifier
) {
    Box(
        modifier = modifier
            .cornerRadius(12.dp)
            .background(WidgetColors.actionBg)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = WidgetColors.primaryColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
    }
}

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}
