package com.serverdash.ide

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.serverdash.ide.model.EditorConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                addJavascriptInterface(bridge, "AndroidBridge")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
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
                        evaluateJavascript("setOptions('$optionsJson')", null)
                    }
                }
                loadUrl("file:///android_asset/editor.html")
                onWebViewReady(this)
            }
        },
    )
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
