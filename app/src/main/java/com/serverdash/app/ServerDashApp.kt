package com.serverdash.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import java.security.Security
import javax.inject.Inject

@HiltAndroidApp
class ServerDashApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Register Bouncy Castle as the top-priority security provider
        // Required for SSHJ's X25519/Ed25519 key exchange on Android
        try {
            Security.removeProvider("BC")
            val bcProvider = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
                .getDeclaredConstructor().newInstance() as java.security.Provider
            Security.insertProviderAt(bcProvider, 1)
        } catch (_: Exception) {
            // BC not on classpath - SSHJ will fail on modern key exchange
        }
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val critical = NotificationChannel(
            CHANNEL_CRITICAL,
            getString(R.string.notification_channel_critical),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            enableLights(true)
        }

        val warning = NotificationChannel(
            CHANNEL_WARNING,
            getString(R.string.notification_channel_warning),
            NotificationManager.IMPORTANCE_DEFAULT
        )

        val info = NotificationChannel(
            CHANNEL_INFO,
            getString(R.string.notification_channel_info),
            NotificationManager.IMPORTANCE_LOW
        )

        val monitoring = NotificationChannel(
            CHANNEL_MONITORING,
            getString(R.string.notification_channel_monitoring),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }

        manager.createNotificationChannels(listOf(critical, warning, info, monitoring))
    }

    companion object {
        const val CHANNEL_CRITICAL = "critical_alerts"
        const val CHANNEL_WARNING = "warning_alerts"
        const val CHANNEL_INFO = "info"
        const val CHANNEL_MONITORING = "monitoring_service"
    }
}
