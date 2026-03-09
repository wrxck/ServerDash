package com.serverdash.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.serverdash.app.MainActivity
import com.serverdash.app.R
import com.serverdash.app.ServerDashApp
import com.serverdash.app.domain.model.Alert
import com.serverdash.app.domain.model.AlertCondition
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertNotificationManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val notificationManager =
            context.getSystemService(NotificationManager::class.java)

        fun showAlertNotification(alert: Alert) {
            val channel =
                when (alert.rule.condition) {
                    is AlertCondition.ServiceDown -> ServerDashApp.CHANNEL_CRITICAL
                    is AlertCondition.CpuAbove -> ServerDashApp.CHANNEL_WARNING
                    is AlertCondition.MemoryAbove -> ServerDashApp.CHANNEL_WARNING
                    is AlertCondition.DiskAbove -> ServerDashApp.CHANNEL_WARNING
                }

            val priority =
                when (alert.rule.condition) {
                    is AlertCondition.ServiceDown -> NotificationCompat.PRIORITY_HIGH
                    else -> NotificationCompat.PRIORITY_DEFAULT
                }

            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val notification =
                NotificationCompat
                    .Builder(context, channel)
                    .setContentTitle("ServerDash Alert")
                    .setContentText(alert.message)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setPriority(priority)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

            notificationManager.notify(alert.id.toInt(), notification)
        }

        fun cancelAll() {
            notificationManager.cancelAll()
        }
    }
