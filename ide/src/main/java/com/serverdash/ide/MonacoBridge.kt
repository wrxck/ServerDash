package com.serverdash.ide

import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MonacoBridge {
    private val _cursorPosition = MutableStateFlow(1 to 1)
    val cursorPosition: StateFlow<Pair<Int, Int>> = _cursorPosition.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _currentContent = MutableStateFlow("")
    val currentContent: StateFlow<String> = _currentContent.asStateFlow()

    var onSaveRequested: (() -> Unit)? = null

    @JavascriptInterface
    fun onContentChanged(content: String) {
        _currentContent.value = content
        _isDirty.value = true
    }

    @JavascriptInterface
    fun onCursorChange(line: Int, column: Int) {
        _cursorPosition.value = line to column
    }

    @JavascriptInterface
    fun onSave() {
        onSaveRequested?.invoke()
    }

    @JavascriptInterface
    fun onReady() {
        _isReady.value = true
    }

    fun resetDirty() {
        _isDirty.value = false
    }
}
