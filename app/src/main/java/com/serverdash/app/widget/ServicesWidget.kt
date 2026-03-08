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
import androidx.glance.text.TextStyle
import com.serverdash.app.MainActivity

/**
 * Medium 2x2 widget showing service counts (running/failed/stopped)
 * and listing top failed services with red highlight.
 */
class ServicesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataStore.getWidgetData(context)

        provideContent {
            GlanceTheme {
                ServicesContent(data)
            }
        }
    }
}

@Composable
private fun ServicesContent(data: WidgetData) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(WidgetColors.surfaceColor)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize()
        ) {
            // Header
            Text(
                text = "Services",
                style = TextStyle(
                    color = WidgetColors.onSurfaceColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Status counts row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                StatusBadge(
                    count = data.runningCount,
                    label = "running",
                    color = WidgetColors.statusGreen
                )

                Spacer(modifier = GlanceModifier.width(8.dp))

                StatusBadge(
                    count = data.failedCount,
                    label = "failed",
                    color = WidgetColors.statusRed
                )

                Spacer(modifier = GlanceModifier.width(8.dp))

                StatusBadge(
                    count = data.stoppedCount,
                    label = "stopped",
                    color = WidgetColors.statusGrey
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Failed services list (top 4)
            if (data.failedServices.isNotEmpty()) {
                Text(
                    text = "Failed:",
                    style = TextStyle(
                        color = WidgetColors.statusRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                val displayServices = data.failedServices.take(4)
                for (service in displayServices) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .cornerRadius(6.dp)
                            .background(WidgetColors.failedBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = service.displayName,
                            style = TextStyle(
                                color = WidgetColors.statusRed,
                                fontSize = 12.sp
                            ),
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(2.dp))
                }

                if (data.failedServices.size > 4) {
                    Text(
                        text = "+${data.failedServices.size - 4} more",
                        style = TextStyle(
                            color = WidgetColors.onSurfaceSecondaryColor,
                            fontSize = 11.sp
                        )
                    )
                }
            } else if (data.services.isNotEmpty()) {
                Text(
                    text = "All services healthy",
                    style = TextStyle(
                        color = WidgetColors.statusGreen,
                        fontSize = 12.sp
                    )
                )
            } else {
                Text(
                    text = if (data.isConnected) "No services found" else "Not connected",
                    style = TextStyle(
                        color = WidgetColors.onSurfaceSecondaryColor,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(
    count: Int,
    label: String,
    color: androidx.glance.unit.ColorProvider
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = GlanceModifier
                .size(8.dp)
                .cornerRadius(4.dp)
                .background(color)
        ) {}

        Spacer(modifier = GlanceModifier.width(4.dp))

        Text(
            text = "$count $label",
            style = TextStyle(
                color = WidgetColors.onSurfaceSecondaryColor,
                fontSize = 12.sp
            )
        )
    }
}

class ServicesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ServicesWidget()
}
