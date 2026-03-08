package com.serverdash.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.serverdash.app.domain.repository.MetricsRepository
import com.serverdash.app.domain.repository.ServerRepository
import com.serverdash.app.domain.repository.ServiceRepository
import com.serverdash.app.domain.repository.SshRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager worker that fetches current server data
 * and pushes it to widget SharedPreferences, then triggers widget updates.
 * Runs every 15 minutes (minimum WorkManager interval).
 */
@HiltWorker
class WidgetUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val serverRepository: ServerRepository,
    private val serviceRepository: ServiceRepository,
    private val metricsRepository: MetricsRepository,
    private val sshRepository: SshRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val config = serverRepository.getServerConfig()
            val hostname = config?.label ?: config?.host ?: "No server"
            val isConnected = sshRepository.isConnected()

            WidgetDataStore.updateConnectionStatus(context, isConnected, hostname)

            if (isConnected) {
                // Fetch latest metrics
                val metricsResult = metricsRepository.fetchMetrics()
                metricsResult.getOrNull()?.let { metrics ->
                    WidgetDataStore.updateMetrics(
                        context,
                        cpuUsage = metrics.cpuUsage,
                        memoryUsage = metrics.memoryUsagePercent,
                        diskUsage = metrics.diskUsagePercent
                    )
                }

                // Fetch latest services
                val serverId = config?.id ?: 1L
                val services = serviceRepository.getServices(serverId)
                val widgetServices = services.map { service ->
                    WidgetServiceInfo(
                        name = service.name,
                        displayName = service.displayName,
                        status = service.status.name
                    )
                }
                WidgetDataStore.updateServices(context, widgetServices)
            }

            // Trigger all widget updates
            try {
                ServerStatusWidget().updateAll(context)
                ServicesWidget().updateAll(context)
                QuickActionsWidget().updateAll(context)
            } catch (_: Exception) { }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "widget_update_work"

        fun enqueuePeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun cancelPeriodicWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
