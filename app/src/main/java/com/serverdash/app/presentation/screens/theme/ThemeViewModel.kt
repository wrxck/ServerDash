package com.serverdash.app.presentation.screens.theme

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serverdash.app.core.theme.AppTheme
import com.serverdash.app.core.theme.BuiltInThemes
import com.serverdash.app.core.theme.ThemeColors
import com.serverdash.app.data.preferences.PreferencesManager
import com.serverdash.app.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

data class ThemeUiState(
    val allThemes: List<AppTheme> = BuiltInThemes.all,
    val customThemes: List<AppTheme> = emptyList(),
    val selectedThemeId: String = "default_dark",
    val activeScreen: ThemeActiveScreen = ThemeActiveScreen.Selector,
    // Editor state
    val editingTheme: AppTheme? = null,
    val editorColors: Map<String, Color> = emptyMap(),
    val editorName: String = "",
    val editorCategory: String = "Custom",
    val editorIsDark: Boolean = true,
    val editorActiveSlot: String = "primary",
    // Undo
    val undoAction: UndoAction? = null,
    val undoDurationSeconds: Int = 5,
    // General
    val searchQuery: String = "",
    val showDeleteConfirm: Boolean = false,
    val deleteTarget: AppTheme? = null,
    val successMessage: String? = null,
    // Fonts
    val headerFont: String = "JetBrains Mono",
    val bodyFont: String = "JetBrains Mono",
    val codeFont: String = "JetBrains Mono"
)

sealed interface ThemeActiveScreen {
    data object Selector : ThemeActiveScreen
    data object Editor : ThemeActiveScreen
}

data class UndoAction(
    val label: String,
    val action: suspend () -> Unit,
    val timestampMs: Long = System.currentTimeMillis()
)

sealed interface ThemeEvent {
    data class SelectTheme(val themeId: String) : ThemeEvent
    data class SearchThemes(val query: String) : ThemeEvent
    // Navigation
    data object OpenEditor : ThemeEvent
    data class EditTheme(val theme: AppTheme) : ThemeEvent
    data class DuplicateTheme(val theme: AppTheme) : ThemeEvent
    data object BackToSelector : ThemeEvent
    // Editor
    data class UpdateEditorName(val name: String) : ThemeEvent
    data class UpdateEditorCategory(val category: String) : ThemeEvent
    data class UpdateEditorIsDark(val isDark: Boolean) : ThemeEvent
    data class UpdateEditorColor(val slot: String, val color: Color) : ThemeEvent
    data class SelectEditorSlot(val slot: String) : ThemeEvent
    data object SaveTheme : ThemeEvent
    // Delete
    data class RequestDelete(val theme: AppTheme) : ThemeEvent
    data object ConfirmDelete : ThemeEvent
    data object DismissDelete : ThemeEvent
    // Undo
    data object PerformUndo : ThemeEvent
    data object DismissUndo : ThemeEvent
    data object DismissSuccess : ThemeEvent
    // Fonts
    data class UpdateHeaderFont(val font: String) : ThemeEvent
    data class UpdateBodyFont(val font: String) : ThemeEvent
    data class UpdateCodeFont(val font: String) : ThemeEvent
}

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _state = MutableStateFlow(ThemeUiState())
    val state: StateFlow<ThemeUiState> = _state.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    init {
        viewModelScope.launch {
            preferencesRepository.observePreferences().collect { prefs ->
                _state.update {
                    it.copy(
                        selectedThemeId = prefs.selectedThemeId,
                        undoDurationSeconds = prefs.undoDurationSeconds,
                        headerFont = prefs.headerFont,
                        bodyFont = prefs.bodyFont,
                        codeFont = prefs.codeFont
                    )
                }
            }
        }
        viewModelScope.launch {
            preferencesManager.customThemesJson.collect { jsonStr ->
                val customs = try { json.decodeFromString<List<AppTheme>>(jsonStr) } catch (_: Exception) { emptyList() }
                _state.update { it.copy(customThemes = customs, allThemes = BuiltInThemes.all + customs) }
            }
        }
    }

    fun onEvent(event: ThemeEvent) {
        when (event) {
            is ThemeEvent.SelectTheme -> selectTheme(event.themeId)
            is ThemeEvent.SearchThemes -> _state.update { it.copy(searchQuery = event.query) }
            is ThemeEvent.OpenEditor -> openNewEditor()
            is ThemeEvent.EditTheme -> openEditorFor(event.theme)
            is ThemeEvent.DuplicateTheme -> duplicateTheme(event.theme)
            is ThemeEvent.BackToSelector -> _state.update { it.copy(activeScreen = ThemeActiveScreen.Selector, editingTheme = null) }
            // Editor
            is ThemeEvent.UpdateEditorName -> _state.update { it.copy(editorName = event.name) }
            is ThemeEvent.UpdateEditorCategory -> _state.update { it.copy(editorCategory = event.category) }
            is ThemeEvent.UpdateEditorIsDark -> _state.update { it.copy(editorIsDark = event.isDark) }
            is ThemeEvent.UpdateEditorColor -> {
                _state.update { it.copy(editorColors = it.editorColors + (event.slot to event.color)) }
            }
            is ThemeEvent.SelectEditorSlot -> _state.update { it.copy(editorActiveSlot = event.slot) }
            is ThemeEvent.SaveTheme -> saveTheme()
            // Delete
            is ThemeEvent.RequestDelete -> _state.update { it.copy(showDeleteConfirm = true, deleteTarget = event.theme) }
            is ThemeEvent.ConfirmDelete -> confirmDelete()
            is ThemeEvent.DismissDelete -> _state.update { it.copy(showDeleteConfirm = false, deleteTarget = null) }
            // Undo
            is ThemeEvent.PerformUndo -> performUndo()
            is ThemeEvent.DismissUndo -> _state.update { it.copy(undoAction = null) }
            is ThemeEvent.DismissSuccess -> _state.update { it.copy(successMessage = null) }
            // Fonts
            is ThemeEvent.UpdateHeaderFont -> updateFont { it.copy(headerFont = event.font) }
            is ThemeEvent.UpdateBodyFont -> updateFont { it.copy(bodyFont = event.font) }
            is ThemeEvent.UpdateCodeFont -> updateFont { it.copy(codeFont = event.font) }
        }
    }

    private fun updateFont(transform: (com.serverdash.app.domain.model.AppPreferences) -> com.serverdash.app.domain.model.AppPreferences) {
        viewModelScope.launch {
            preferencesRepository.updatePreferences(transform)
        }
    }

    private fun selectTheme(themeId: String) {
        val previousId = _state.value.selectedThemeId
        viewModelScope.launch {
            preferencesRepository.updatePreferences { it.copy(selectedThemeId = themeId) }
            // Also update themeMode to match
            val theme = BuiltInThemes.findById(themeId) ?: _state.value.customThemes.find { it.id == themeId }
            if (theme != null) {
                val mode = when (themeId) {
                    "default_dark" -> com.serverdash.app.domain.model.ThemeMode.DARK
                    "default_light" -> com.serverdash.app.domain.model.ThemeMode.LIGHT
                    "true_black" -> com.serverdash.app.domain.model.ThemeMode.TRUE_BLACK
                    else -> if (theme.isDark) com.serverdash.app.domain.model.ThemeMode.DARK else com.serverdash.app.domain.model.ThemeMode.LIGHT
                }
                preferencesRepository.updatePreferences { it.copy(themeMode = mode) }
            }
        }
        _state.update {
            it.copy(
                undoAction = UndoAction(label = "Theme changed", action = {
                    preferencesRepository.updatePreferences { p -> p.copy(selectedThemeId = previousId) }
                })
            )
        }
    }

    private fun openNewEditor() {
        val defaultColors = colorMapFromThemeColors(BuiltInThemes.default.colors)
        _state.update {
            it.copy(
                activeScreen = ThemeActiveScreen.Editor,
                editingTheme = null,
                editorName = "My Theme",
                editorCategory = "Custom",
                editorIsDark = true,
                editorColors = defaultColors,
                editorActiveSlot = "primary"
            )
        }
    }

    private fun openEditorFor(theme: AppTheme) {
        _state.update {
            it.copy(
                activeScreen = ThemeActiveScreen.Editor,
                editingTheme = theme,
                editorName = theme.name,
                editorCategory = theme.category,
                editorIsDark = theme.isDark,
                editorColors = colorMapFromThemeColors(theme.colors),
                editorActiveSlot = "primary"
            )
        }
    }

    private fun duplicateTheme(theme: AppTheme) {
        val newTheme = theme.copy(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = "${theme.name} Copy",
            category = "Custom",
            isBuiltIn = false
        )
        openEditorFor(newTheme)
        _state.update { it.copy(editingTheme = null) } // treat as new
    }

    private fun saveTheme() {
        val s = _state.value
        val themeColors = themeColorsFromMap(s.editorColors)
        val id = s.editingTheme?.id ?: "custom_${UUID.randomUUID().toString().take(8)}"
        val theme = AppTheme(
            id = id,
            name = s.editorName.ifBlank { "Untitled" },
            category = s.editorCategory.ifBlank { "Custom" },
            isDark = s.editorIsDark,
            colors = themeColors,
            isBuiltIn = false
        )

        val updated = s.customThemes.toMutableList()
        val existingIndex = updated.indexOfFirst { it.id == id }
        if (existingIndex >= 0) updated[existingIndex] = theme else updated.add(theme)

        viewModelScope.launch {
            preferencesManager.updateCustomThemesJson(json.encodeToString(updated))
            preferencesRepository.updatePreferences { it.copy(selectedThemeId = theme.id) }
        }
        _state.update {
            it.copy(
                activeScreen = ThemeActiveScreen.Selector,
                editingTheme = null,
                successMessage = "Theme \"${theme.name}\" saved"
            )
        }
    }

    private fun confirmDelete() {
        val target = _state.value.deleteTarget ?: return
        if (target.isBuiltIn) return

        val previousCustom = _state.value.customThemes.toList()
        val updated = _state.value.customThemes.filter { it.id != target.id }

        viewModelScope.launch {
            preferencesManager.updateCustomThemesJson(json.encodeToString(updated))
            if (_state.value.selectedThemeId == target.id) {
                preferencesRepository.updatePreferences { it.copy(selectedThemeId = "default_dark") }
            }
        }
        _state.update {
            it.copy(
                showDeleteConfirm = false,
                deleteTarget = null,
                undoAction = UndoAction(label = "Theme \"${target.name}\" deleted", action = {
                    preferencesManager.updateCustomThemesJson(json.encodeToString(previousCustom))
                })
            )
        }
    }

    private fun performUndo() {
        val action = _state.value.undoAction ?: return
        viewModelScope.launch { action.action() }
        _state.update { it.copy(undoAction = null, successMessage = "Undone") }
    }

    companion object {
        val COLOR_SLOTS = listOf(
            "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
            "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
            "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
            "background", "surface", "surfaceVariant",
            "onBackground", "onSurface", "onSurfaceVariant",
            "outline", "outlineVariant",
            "error", "onError", "errorContainer", "onErrorContainer",
            "inverseSurface", "inverseOnSurface", "inversePrimary",
            "editorBackground", "editorForeground", "editorLineHighlight", "editorSelection",
            "editorLineNumber", "editorGutter", "editorCursor", "editorWhitespace",
            "editorIndentGuide", "editorBracketMatch",
            "syntaxKeyword", "syntaxString", "syntaxComment", "syntaxFunction",
            "syntaxNumber", "syntaxType", "syntaxOperator", "syntaxVariable",
            "syntaxConstant", "syntaxTag", "syntaxAttribute", "syntaxProperty",
            "syntaxRegex", "syntaxPunctuation"
        )

        fun slotDisplayName(slot: String): String = slot
            .replace(Regex("([A-Z])")) { " ${it.value}" }
            .replaceFirstChar { it.uppercase() }
            .trim()

        fun colorMapFromThemeColors(tc: ThemeColors): Map<String, Color> = mapOf(
            "primary" to Color(tc.primary), "onPrimary" to Color(tc.onPrimary),
            "primaryContainer" to Color(tc.primaryContainer), "onPrimaryContainer" to Color(tc.onPrimaryContainer),
            "secondary" to Color(tc.secondary), "onSecondary" to Color(tc.onSecondary),
            "secondaryContainer" to Color(tc.secondaryContainer), "onSecondaryContainer" to Color(tc.onSecondaryContainer),
            "tertiary" to Color(tc.tertiary), "onTertiary" to Color(tc.onTertiary),
            "tertiaryContainer" to Color(tc.tertiaryContainer), "onTertiaryContainer" to Color(tc.onTertiaryContainer),
            "background" to Color(tc.background), "surface" to Color(tc.surface), "surfaceVariant" to Color(tc.surfaceVariant),
            "onBackground" to Color(tc.onBackground), "onSurface" to Color(tc.onSurface), "onSurfaceVariant" to Color(tc.onSurfaceVariant),
            "outline" to Color(tc.outline), "outlineVariant" to Color(tc.outlineVariant),
            "error" to Color(tc.error), "onError" to Color(tc.onError),
            "errorContainer" to Color(tc.errorContainer), "onErrorContainer" to Color(tc.onErrorContainer),
            "inverseSurface" to Color(tc.inverseSurface), "inverseOnSurface" to Color(tc.inverseOnSurface),
            "inversePrimary" to Color(tc.inversePrimary),
            "editorBackground" to Color(tc.editorBackground),
            "editorForeground" to Color(tc.editorForeground),
            "editorLineHighlight" to Color(tc.editorLineHighlight),
            "editorSelection" to Color(tc.editorSelection),
            "editorLineNumber" to Color(tc.editorLineNumber),
            "editorGutter" to Color(tc.editorGutter),
            "editorCursor" to Color(tc.editorCursor),
            "editorWhitespace" to Color(tc.editorWhitespace),
            "editorIndentGuide" to Color(tc.editorIndentGuide),
            "editorBracketMatch" to Color(tc.editorBracketMatch),
            "syntaxKeyword" to Color(tc.syntaxKeyword),
            "syntaxString" to Color(tc.syntaxString),
            "syntaxComment" to Color(tc.syntaxComment),
            "syntaxFunction" to Color(tc.syntaxFunction),
            "syntaxNumber" to Color(tc.syntaxNumber),
            "syntaxType" to Color(tc.syntaxType),
            "syntaxOperator" to Color(tc.syntaxOperator),
            "syntaxVariable" to Color(tc.syntaxVariable),
            "syntaxConstant" to Color(tc.syntaxConstant),
            "syntaxTag" to Color(tc.syntaxTag),
            "syntaxAttribute" to Color(tc.syntaxAttribute),
            "syntaxProperty" to Color(tc.syntaxProperty),
            "syntaxRegex" to Color(tc.syntaxRegex),
            "syntaxPunctuation" to Color(tc.syntaxPunctuation)
        )

        fun themeColorsFromMap(map: Map<String, Color>): ThemeColors {
            fun get(key: String) = map[key]?.value?.toLong() ?: 0xFF000000
            return ThemeColors(
                primary = get("primary"), onPrimary = get("onPrimary"),
                primaryContainer = get("primaryContainer"), onPrimaryContainer = get("onPrimaryContainer"),
                secondary = get("secondary"), onSecondary = get("onSecondary"),
                secondaryContainer = get("secondaryContainer"), onSecondaryContainer = get("onSecondaryContainer"),
                tertiary = get("tertiary"), onTertiary = get("onTertiary"),
                tertiaryContainer = get("tertiaryContainer"), onTertiaryContainer = get("onTertiaryContainer"),
                background = get("background"), surface = get("surface"), surfaceVariant = get("surfaceVariant"),
                onBackground = get("onBackground"), onSurface = get("onSurface"), onSurfaceVariant = get("onSurfaceVariant"),
                outline = get("outline"), outlineVariant = get("outlineVariant"),
                error = get("error"), onError = get("onError"),
                errorContainer = get("errorContainer"), onErrorContainer = get("onErrorContainer"),
                inverseSurface = get("inverseSurface"), inverseOnSurface = get("inverseOnSurface"),
                inversePrimary = get("inversePrimary"),
                editorBackground = get("editorBackground"),
                editorForeground = get("editorForeground"),
                editorLineHighlight = get("editorLineHighlight"),
                editorSelection = get("editorSelection"),
                editorLineNumber = get("editorLineNumber"),
                editorGutter = get("editorGutter"),
                editorCursor = get("editorCursor"),
                editorWhitespace = get("editorWhitespace"),
                editorIndentGuide = get("editorIndentGuide"),
                editorBracketMatch = get("editorBracketMatch"),
                syntaxKeyword = get("syntaxKeyword"),
                syntaxString = get("syntaxString"),
                syntaxComment = get("syntaxComment"),
                syntaxFunction = get("syntaxFunction"),
                syntaxNumber = get("syntaxNumber"),
                syntaxType = get("syntaxType"),
                syntaxOperator = get("syntaxOperator"),
                syntaxVariable = get("syntaxVariable"),
                syntaxConstant = get("syntaxConstant"),
                syntaxTag = get("syntaxTag"),
                syntaxAttribute = get("syntaxAttribute"),
                syntaxProperty = get("syntaxProperty"),
                syntaxRegex = get("syntaxRegex"),
                syntaxPunctuation = get("syntaxPunctuation")
            )
        }
    }
}
