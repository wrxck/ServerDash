package com.serverdash.app.service

import com.serverdash.app.domain.model.Alert
import com.serverdash.app.domain.model.AlertCondition
import com.serverdash.app.domain.model.WebhookPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebhookDispatcher @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun dispatch(alert: Alert) = withContext(Dispatchers.IO) {
        try {
            val severity = when (alert.rule.condition) {
                is AlertCondition.ServiceDown -> "critical"
                is AlertCondition.CpuAbove -> "warning"
                is AlertCondition.MemoryAbove -> "warning"
                is AlertCondition.DiskAbove -> "warning"
            }

            val payload = WebhookPayload(
                server = "ServerDash",
                alert = alert.rule.name,
                message = alert.message,
                timestamp = alert.timestamp,
                severity = severity
            )

            val body = json.encodeToString(payload)
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(alert.rule.webhookUrl)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Log failure but don't throw
                    android.util.Log.w("WebhookDispatcher", "Webhook failed: ${response.code}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WebhookDispatcher", "Webhook dispatch error", e)
        }
    }
}
