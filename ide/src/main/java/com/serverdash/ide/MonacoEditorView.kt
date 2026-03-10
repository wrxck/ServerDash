package com.serverdash.ide

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.serverdash.ide.model.EditorConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Cached WebView holder — Monaco loads once and is reused across navigations.
 */
object MonacoWebViewPool {
    private var cachedWebView: WebView? = null
    private var currentBridge: MonacoBridge? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreate(context: Context, bridge: MonacoBridge, onReady: (WebView) -> Unit): WebView {
        val existing = cachedWebView
        if (existing != null) {
            // Detach from previous parent if needed
            (existing.parent as? ViewGroup)?.removeView(existing)
            // Re-bind the bridge
            if (currentBridge !== bridge) {
                existing.removeJavascriptInterface("AndroidBridge")
                existing.addJavascriptInterface(bridge, "AndroidBridge")
                currentBridge = bridge
            }
            onReady(existing)
            return existing
        }

        val webView = WebView(context.applicationContext).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            addJavascriptInterface(bridge, "AndroidBridge")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    onReady(this@apply)
                }
            }
            loadUrl("file:///android_asset/editor.html")
        }
        currentBridge = bridge
        cachedWebView = webView
        return webView
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MonacoEditorView(
    bridge: MonacoBridge,
    modifier: Modifier = Modifier,
    config: EditorConfig = EditorConfig(),
    onWebViewReady: (WebView) -> Unit = {},
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MonacoWebViewPool.getOrCreate(context, bridge) { webView ->
                val optionsJson = Json.encodeToString(
                    mapOf(
                        "fontSize" to config.fontSize.toString(),
                        "wordWrap" to if (config.wordWrap) "on" else "off",
                        "lineNumbers" to if (config.lineNumbers) "on" else "off",
                        "tabSize" to config.tabSize.toString(),
                        "insertSpaces" to config.insertSpaces.toString(),
                        "renderWhitespace" to config.renderWhitespace,
                    ),
                )
                webView.evaluateJavascript("setOptions('$optionsJson')", null)
                onWebViewReady(webView)
            }
        },
    )

    // Detach WebView from parent when leaving composition (don't destroy it)
    DisposableEffect(Unit) {
        onDispose { }
    }
}

class MonacoCommands(private val webView: WebView) {
    fun setContent(content: String, language: String) {
        val escaped = content
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        webView.evaluateJavascript("setContent('$escaped', '$language')", null)
    }

    fun setTheme(themeJson: String) {
        val escaped = themeJson.replace("'", "\\'")
        webView.evaluateJavascript("setThemeColors('$escaped')", null)
    }

    fun setOptions(optionsJson: String) {
        val escaped = optionsJson.replace("'", "\\'")
        webView.evaluateJavascript("setOptions('$escaped')", null)
    }

    fun undo() = webView.evaluateJavascript("editorUndo()", null)
    fun redo() = webView.evaluateJavascript("editorRedo()", null)
    fun find() = webView.evaluateJavascript("findText()", null)

    fun setCursorPosition(line: Int, column: Int) =
        webView.evaluateJavascript("setCursorPosition($line, $column)", null)

    fun setReadOnly(readOnly: Boolean) =
        webView.evaluateJavascript("setReadOnly($readOnly)", null)
}
