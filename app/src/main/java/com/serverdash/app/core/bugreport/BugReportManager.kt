package com.serverdash.app.core.bugreport

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BugReportManager @Inject constructor() {

    private val _showBugReport = MutableStateFlow(false)
    val showBugReport: StateFlow<Boolean> = _showBugReport.asStateFlow()

    fun show() {
        _showBugReport.value = true
    }

    fun dismiss() {
        _showBugReport.value = false
    }

    fun collectLogs(context: Context): String {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        // App info
        val packageInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) { null }

        sb.appendLine("=== ServerDash Bug Report ===")
        sb.appendLine("Generated: ${dateFormat.format(Date())}")
        sb.appendLine("App Version: ${packageInfo?.versionName ?: "unknown"}")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Session ID: ${UUID.randomUUID()}")
        sb.appendLine()

        // Collect logcat (app's own logs only)
        try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "500", "--pid=${android.os.Process.myPid()}"))
            val logcat = process.inputStream.bufferedReader().readText()
            val redacted = redactPii(logcat)
            sb.appendLine("=== Recent Logs ===")
            sb.appendLine(redacted)
        } catch (e: Exception) {
            sb.appendLine("=== Logs unavailable: ${e.message} ===")
        }

        return sb.toString()
    }

    fun sendReport(context: Context, logs: String, userNotes: String) {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val timestamp = dateFormat.format(Date())
        val fileName = "serverdash_bugreport_$timestamp.txt"

        // Write to cache dir for sharing
        val cacheDir = File(context.cacheDir, "bugreports")
        cacheDir.mkdirs()
        val file = File(cacheDir, fileName)
        FileWriter(file).use { writer ->
            if (userNotes.isNotBlank()) {
                writer.write("=== User Notes ===\n")
                writer.write(userNotes)
                writer.write("\n\n")
            }
            writer.write(logs)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("matt@matthesketh.pro"))
            putExtra(Intent.EXTRA_SUBJECT, "ServerDash Bug Report ($timestamp)")
            putExtra(Intent.EXTRA_TEXT, "Bug report attached.\n\nUser notes:\n$userNotes")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(Intent.createChooser(intent, "Send Bug Report").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

        dismiss()
    }

    private fun redactPii(text: String): String {
        var result = text
        // IP addresses
        result = result.replace(Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b"), "[REDACTED_IP]")
        // Home directory paths — redact username portion
        result = result.replace(Regex("/home/[a-zA-Z0-9._-]+"), "/home/[USER]")
        result = result.replace(Regex("/Users/[a-zA-Z0-9._-]+"), "/Users/[USER]")
        // SSH keys (BEGIN...END blocks)
        result = result.replace(Regex("-----BEGIN[^-]*-----[\\s\\S]*?-----END[^-]*-----"), "[REDACTED_KEY]")
        // Password-like patterns in key=value
        result = result.replace(Regex("(?i)(password|passwd|secret|token|key|credential)\\s*[=:]\\s*\\S+"), "$1=[REDACTED]")
        // Email addresses (except the bug report email)
        result = result.replace(Regex("(?!matt@matthesketh\\.pro)[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"), "[REDACTED_EMAIL]")
        return result
    }
}
