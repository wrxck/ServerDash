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
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.cornerRadius
import com.serverdash.app.MainActivity

/**
 * Small 2x1 widget showing server connection status, hostname,
 * and CPU/memory usage percentages.
 */
class ServerStatusWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataStore.getWidgetData(context)

        provideContent {
            GlanceTheme {
                ServerStatusContent(data)
            }
        }
    }
}

@Composable
private fun ServerStatusContent(data: WidgetData) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(WidgetColors.surfaceColor)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Top row: status dot + hostname
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier.fillMaxWidth()
            ) {
                // Status dot
                Box(
                    modifier = GlanceModifier
                        .size(10.dp)
                        .cornerRadius(5.dp)
                        .background(
                            if (data.isConnected) WidgetColors.statusGreen
                            else WidgetColors.statusRed
                        )
                ) {}

                Spacer(modifier = GlanceModifier.width(8.dp))

                Text(
                    text = data.hostname,
                    style = TextStyle(
                        color = WidgetColors.onSurfaceColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Bottom row: CPU and Memory
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                // CPU
                Text(
                    text = "CPU ${data.cpuUsage.toInt()}%",
                    style = TextStyle(
                        color = metricColor(data.cpuUsage),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )

                Spacer(modifier = GlanceModifier.width(16.dp))

                // Memory
                Text(
                    text = "MEM ${data.memoryUsage.toInt()}%",
                    style = TextStyle(
                        color = metricColor(data.memoryUsage),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

private fun metricColor(value: Float): ColorProvider {
    return when {
        value >= 90f -> WidgetColors.statusRed
        value >= 75f -> WidgetColors.statusYellow
        else -> WidgetColors.onSurfaceSecondaryColor
    }
}

class ServerStatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ServerStatusWidget()
}
