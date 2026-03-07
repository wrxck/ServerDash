package com.serverdash.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.serverdash.app.MainActivity
import com.serverdash.app.R
import com.serverdash.app.ServerDashApp
import com.serverdash.app.domain.model.SystemMetrics
import com.serverdash.app.domain.repository.*
import com.serverdash.app.domain.usecase.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class MonitoringService : Service() {

    @Inject lateinit var preferencesRepository: PreferencesRepository
    @Inject lateinit var refreshServiceStatus: RefreshServiceStatusUseCase
    @Inject lateinit var fetchMetrics: FetchSystemMetricsUseCase
    @Inject lateinit var evaluateAlertRules: EvaluateAlertRulesUseCase
    @Inject lateinit var serviceRepository: ServiceRepository
    @Inject lateinit var sshRepository: SshRepository
    @Inject lateinit var alertNotificationManager: AlertNotificationManager
    @Inject lateinit var webhookDispatcher: WebhookDispatcher

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Monitoring active"))
        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            val prefs = preferencesRepository.getPreferences()
            while (isActive) {
                if (sshRepository.isConnected()) {
                    try {
                        val servicesResult = refreshServiceStatus(1L)
                        val metricsResult = fetchMetrics()

                        val services = servicesResult.getOrNull() ?: emptyList()
                        val metrics = metricsResult.getOrNull() ?: SystemMetrics()

                        val alerts = evaluateAlertRules(services, metrics, 1L)
                        alerts.forEach { alert ->
                            alertNotificationManager.showAlertNotification(alert)
                            if (alert.rule.webhookUrl.isNotBlank()) {
                                webhookDispatcher.dispatch(alert)
                            }
                        }

                        updateNotification("Last check: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                    } catch (e: Exception) {
                        updateNotification("Error: ${e.message}")
                    }
                }
                delay(prefs.pollingIntervalSeconds * 1000L)
            }
        }
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, ServerDashApp.CHANNEL_MONITORING)
            .setContentTitle("ServerDash")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}
