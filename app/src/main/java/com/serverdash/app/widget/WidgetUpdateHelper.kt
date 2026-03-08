package com.serverdash.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.serverdash.app.domain.model.Service
import com.serverdash.app.domain.model.SystemMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper to push data from the app into widget SharedPreferences
 * and trigger widget updates.
 */
object WidgetUpdateHelper {

    suspend fun updateAllWidgets(
        context: Context,
        isConnected: Boolean,
        hostname: String,
        metrics: SystemMetrics?,
        services: List<Service>
    ) {
        withContext(Dispatchers.IO) {
            WidgetDataStore.updateConnectionStatus(context, isConnected, hostname)

            if (metrics != null) {
                WidgetDataStore.updateMetrics(
                    context,
                    cpuUsage = metrics.cpuUsage,
                    memoryUsage = metrics.memoryUsagePercent,
                    diskUsage = metrics.diskUsagePercent
                )
            }

            val widgetServices = services.map { service ->
                WidgetServiceInfo(
                    name = service.name,
                    displayName = service.displayName,
                    status = service.status.name
                )
            }
            WidgetDataStore.updateServices(context, widgetServices)
        }

        // Trigger Glance widget updates
        try {
            ServerStatusWidget().updateAll(context)
            ServicesWidget().updateAll(context)
            QuickActionsWidget().updateAll(context)
        } catch (_: Exception) {
            // Widgets may not be placed yet
        }
    }
}
