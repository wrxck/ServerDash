package com.serverdash.app.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.serverdash.app.domain.model.SystemMetrics
import com.serverdash.app.domain.repository.*
import com.serverdash.app.domain.usecase.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class MonitoringWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sshRepository: SshRepository,
    private val refreshServiceStatus: RefreshServiceStatusUseCase,
    private val fetchMetrics: FetchSystemMetricsUseCase,
    private val evaluateAlertRules: EvaluateAlertRulesUseCase,
    private val alertNotificationManager: AlertNotificationManager,
    private val webhookDispatcher: WebhookDispatcher,
    private val serverRepository: ServerRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val config = serverRepository.getServerConfig() ?: return Result.failure()

            // Connect if not connected
            if (!sshRepository.isConnected()) {
                sshRepository.connect(config).getOrElse { return Result.retry() }
            }

            val services = refreshServiceStatus(1L).getOrNull() ?: emptyList()
            val metrics = fetchMetrics().getOrNull() ?: SystemMetrics()

            val alerts = evaluateAlertRules(services, metrics, config.id)
            alerts.forEach { alert ->
                alertNotificationManager.showAlertNotification(alert)
                if (alert.rule.webhookUrl.isNotBlank()) {
                    webhookDispatcher.dispatch(alert)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "monitoring_worker"

        fun schedule(context: Context, intervalMinutes: Long = 15) {
            val request = PeriodicWorkRequestBuilder<MonitoringWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
