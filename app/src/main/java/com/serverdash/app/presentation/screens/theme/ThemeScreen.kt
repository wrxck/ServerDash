package com.serverdash.app.presentation.screens.theme

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.serverdash.app.core.theme.AppTheme
import com.serverdash.app.core.theme.BuiltInThemes
import com.serverdash.app.core.theme.FontCategory
import com.serverdash.app.core.theme.availableFonts
import com.serverdash.app.core.theme.loadGoogleFont
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    onNavigateBack: () -> Unit,
    viewModel: ThemeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Undo snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.undoAction) {
        state.undoAction?.let { undo ->
            val result = snackbarHostState.showSnackbar(
                message = undo.label,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onEvent(ThemeEvent.PerformUndo)
            } else {
                viewModel.onEvent(ThemeEvent.DismissUndo)
            }
        }
    }

    // Success message
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.onEvent(ThemeEvent.DismissSuccess)
        }
    }

    // Delete confirmation
    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(ThemeEvent.DismissDelete) },
            title = { Text("Delete Theme") },
            text = { Text("Delete \"${state.deleteTarget?.name}\"? This can be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(ThemeEvent.ConfirmDelete) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(ThemeEvent.DismissDelete) }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (state.activeScreen) {
                            ThemeActiveScreen.Selector -> "Themes"
                            ThemeActiveScreen.Editor -> if (state.editingTheme != null) "Edit Theme" else "New Theme"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (state.activeScreen) {
                            ThemeActiveScreen.Selector -> onNavigateBack()
                            ThemeActiveScreen.Editor -> viewModel.onEvent(ThemeEvent.BackToSelector)
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (state.activeScreen == ThemeActiveScreen.Selector) {
                        IconButton(onClick = { viewModel.onEvent(ThemeEvent.OpenEditor) }) {
                            Icon(Icons.Default.Add, "New Theme")
                        }
                    }
                    if (state.activeScreen == ThemeActiveScreen.Editor) {
                        IconButton(onClick = { viewModel.onEvent(ThemeEvent.SaveTheme) }) {
                            Icon(Icons.Default.Check, "Save")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        AnimatedContent(
            targetState = state.activeScreen,
            modifier = Modifier.padding(padding),
            label = "themeScreen"
        ) { screen ->
            when (screen) {
                ThemeActiveScreen.Selector -> ThemeSelectorContent(state, viewModel)
                ThemeActiveScreen.Editor -> ThemeEditorContent(state, viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSelectorContent(state: ThemeUiState, viewModel: ThemeViewModel) {
    val filteredThemes = remember(state.allThemes, state.searchQuery) {
        if (state.searchQuery.isBlank()) state.allThemes
        else state.allThemes.filter {
            it.name.contains(state.searchQuery, ignoreCase = true) ||
                it.category.contains(state.searchQuery, ignoreCase = true)
        }
    }

    val grouped = remember(filteredThemes) {
        filteredThemes.groupBy { it.category }
    }

    val categoryOrder = listOf("Default", "Popular", "Minimal", "Vibrant", "Retro", "Nature", "Custom")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Search bar
        item {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onEvent(ThemeEvent.SearchThemes(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search themes...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.onEvent(ThemeEvent.SearchThemes("")) }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                },
                singleLine = true
            )
        }

        // Categories
        categoryOrder.forEach { category ->
            val themes = grouped[category] ?: return@forEach
            item {
                Text(
                    category,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(themes, key = { it.id }) { theme ->
                        ThemeCard(
                            theme = theme,
                            isSelected = theme.id == state.selectedThemeId,
                            onSelect = { viewModel.onEvent(ThemeEvent.SelectTheme(theme.id)) },
                            onEdit = { viewModel.onEvent(ThemeEvent.EditTheme(theme)) },
                            onDuplicate = { viewModel.onEvent(ThemeEvent.DuplicateTheme(theme)) },
                            onDelete = if (!theme.isBuiltIn) {{ viewModel.onEvent(ThemeEvent.RequestDelete(theme)) }} else null
                        )
                    }
                }
            }
        }

        // ── Fonts section ──────────────────────────────────────────
        item {
            Text(
                "Fonts",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        item {
            FontSettingsSection(state = state, viewModel = viewModel)
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontSettingsSection(state: ThemeUiState, viewModel: ThemeViewModel) {
    var showHeaderPicker by remember { mutableStateOf(false) }
    var showBodyPicker by remember { mutableStateOf(false) }
    var showCodePicker by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Font
            FontSettingRow(
                label = "Header Font",
                description = "Display, headline, and title styles",
                currentFont = state.headerFont,
                onClick = { showHeaderPicker = true }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Body Font
            FontSettingRow(
                label = "Body Font",
                description = "Body text and labels",
                currentFont = state.bodyFont,
                onClick = { showBodyPicker = true }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Code Font
            FontSettingRow(
                label = "Code Font",
                description = "Terminal and log output",
                currentFont = state.codeFont,
                onClick = { showCodePicker = true }
            )
        }
    }

    if (showHeaderPicker) {
        FontPickerDialog(
            title = "Header Font",
            currentFont = state.headerFont,
            onFontSelected = { viewModel.onEvent(ThemeEvent.UpdateHeaderFont(it)); showHeaderPicker = false },
            onDismiss = { showHeaderPicker = false }
        )
    }
    if (showBodyPicker) {
        FontPickerDialog(
            title = "Body Font",
            currentFont = state.bodyFont,
            onFontSelected = { viewModel.onEvent(ThemeEvent.UpdateBodyFont(it)); showBodyPicker = false },
            onDismiss = { showBodyPicker = false }
        )
    }
    if (showCodePicker) {
        FontPickerDialog(
            title = "Code Font",
            currentFont = state.codeFont,
            onFontSelected = { viewModel.onEvent(ThemeEvent.UpdateCodeFont(it)); showCodePicker = false },
            onDismiss = { showCodePicker = false }
        )
    }
}

@Composable
private fun FontSettingRow(
    label: String,
    description: String,
    currentFont: String,
    onClick: () -> Unit
) {
    val previewFontFamily = remember(currentFont) { loadGoogleFont(currentFont) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                currentFont,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = previewFontFamily),
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontPickerDialog(
    title: String,
    currentFont: String,
    onFontSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<FontCategory?>(null) }

    val filteredFonts = remember(searchQuery, selectedCategory) {
        availableFonts.filter { font ->
            (searchQuery.isBlank() || font.name.contains(searchQuery, ignoreCase = true)) &&
                (selectedCategory == null || font.category == selectedCategory)
        }
    }

    val grouped = remember(filteredFonts) {
        filteredFonts.groupBy { it.category }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp)
            ) {
                // Search
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search fonts...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, "Clear")
                            }
                        }
                    },
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                // Category filter chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("All", fontSize = 12.sp) }
                        )
                    }
                    items(FontCategory.entries.toList(), key = { it.name }) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = if (selectedCategory == category) null else category },
                            label = { Text(category.displayName, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Font list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    grouped.forEach { (category, fonts) ->
                        item {
                            Text(
                                category.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(fonts, key = { it.name }) { font ->
                            val fontFamily = remember(font.family) { loadGoogleFont(font.family) }
                            val isSelected = font.name == currentFont

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onFontSelected(font.name) },
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            font.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = fontFamily
                                            ),
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            "The quick brown fox jumps",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = fontFamily
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    if (font.isBuiltIn) {
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "Built-in",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ThemeCard(
    theme: AppTheme,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: (() -> Unit)?
) {
    var showMenu by remember { mutableStateOf(false) }
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable { onSelect() },
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(8.dp)) {
            // Color preview
            ThemePreviewMini(theme)

            Spacer(Modifier.height(8.dp))

            // Name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    theme.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, null, Modifier.size(14.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = { showMenu = false; onEdit() },
                            leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = { showMenu = false; onDuplicate() },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)) }
                        )
                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; onDelete() },
                                leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }
            }

            // Dark/Light badge
            Text(
                if (theme.isDark) "Dark" else "Light",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    "Selected",
                    Modifier.size(16.dp).align(Alignment.End),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ThemePreviewMini(theme: AppTheme) {
    val c = theme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(c.background))
    ) {
        Column(Modifier.padding(4.dp)) {
            // Title bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(c.surface)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.padding(start = 2.dp).size(6.dp).clip(CircleShape).background(Color(c.primary)))
                Spacer(Modifier.width(2.dp))
                Box(Modifier.width(30.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(c.onSurface).copy(alpha = 0.5f)))
            }
            Spacer(Modifier.height(3.dp))
            // Content bars
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(2.dp)).background(Color(c.primaryContainer)))
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(2.dp)).background(Color(c.secondaryContainer)))
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(2.dp)).background(Color(c.tertiaryContainer)))
            }
            Spacer(Modifier.height(3.dp))
            // Color swatches
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(c.primary, c.secondary, c.tertiary, c.error).forEach { color ->
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(color)))
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.width(20.dp).height(10.dp).clip(RoundedCornerShape(2.dp)).background(Color(c.surfaceVariant)))
            }
        }
    }
}

// ── Theme Editor ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeEditorContent(state: ThemeUiState, viewModel: ThemeViewModel) {
    val colorScheme = remember(state.editorColors, state.editorIsDark) {
        ThemeViewModel.themeColorsFromMap(state.editorColors).toColorScheme(state.editorIsDark)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Theme name & settings
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.editorName,
                        onValueChange = { viewModel.onEvent(ThemeEvent.UpdateEditorName(it)) },
                        label = { Text("Theme Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.editorIsDark,
                            onClick = { viewModel.onEvent(ThemeEvent.UpdateEditorIsDark(true)) },
                            label = { Text("Dark") }
                        )
                        FilterChip(
                            selected = !state.editorIsDark,
                            onClick = { viewModel.onEvent(ThemeEvent.UpdateEditorIsDark(false)) },
                            label = { Text("Light") }
                        )
                    }
                    if (state.editingTheme?.isBuiltIn == true) {
                        Text(
                            "Editing a built-in theme will save a custom copy",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Live preview
        item {
            Text("Live Preview", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        item {
            ThemePreviewLive(colorScheme)
        }

        // Color slots
        item {
            Text("Color Slots", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }

        // Group color slots
        val groups = listOf(
            "Primary" to listOf("primary", "onPrimary", "primaryContainer", "onPrimaryContainer"),
            "Secondary" to listOf("secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer"),
            "Tertiary" to listOf("tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer"),
            "Surface" to listOf("background", "surface", "surfaceVariant", "onBackground", "onSurface", "onSurfaceVariant"),
            "Outline" to listOf("outline", "outlineVariant"),
            "Error" to listOf("error", "onError", "errorContainer", "onErrorContainer"),
            "Inverse" to listOf("inverseSurface", "inverseOnSurface", "inversePrimary")
        )

        groups.forEach { (groupName, slots) ->
            item {
                Text(groupName, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        slots.forEach { slot ->
                            ColorSlotRow(
                                slot = slot,
                                color = state.editorColors[slot] ?: Color.Black,
                                isActive = state.editorActiveSlot == slot,
                                onSelect = { viewModel.onEvent(ThemeEvent.SelectEditorSlot(slot)) },
                                onColorChange = { viewModel.onEvent(ThemeEvent.UpdateEditorColor(slot, it)) }
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun ColorSlotRow(
    slot: String,
    color: Color,
    isActive: Boolean,
    onSelect: () -> Unit,
    onColorChange: (Color) -> Unit
) {
    var hexInput by remember(color) {
        mutableStateOf(String.format("%06X", color.toArgb() and 0xFFFFFF))
    }
    var showPicker by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect() }
                .background(
                    if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    else Color.Transparent,
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { showPicker = !showPicker }
            )
            Text(
                ThemeViewModel.slotDisplayName(slot),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = "#$hexInput",
                onValueChange = { input ->
                    val cleaned = input.removePrefix("#").take(6).filter { it.isLetterOrDigit() }.uppercase()
                    hexInput = cleaned
                    if (cleaned.length == 6) {
                        try {
                            val argb = android.graphics.Color.parseColor("#$cleaned")
                            onColorChange(Color(argb).copy(alpha = 1f))
                        } catch (_: Exception) {}
                    }
                },
                modifier = Modifier.width(100.dp).height(40.dp),
                textStyle = MaterialTheme.typography.labelSmall,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )
        }

        // Simple color picker (hue slider + saturation/brightness)
        AnimatedVisibility(visible = showPicker && isActive) {
            ColorPickerSimple(color = color, onColorChange = onColorChange)
        }
    }
}

@Composable
private fun ColorPickerSimple(
    color: Color,
    onColorChange: (Color) -> Unit
) {
    val hsv = remember(color) {
        val arr = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt(),
            arr
        )
        arr
    }
    var hue by remember(color) { mutableFloatStateOf(hsv[0]) }
    var saturation by remember(color) { mutableFloatStateOf(hsv[1]) }
    var brightness by remember(color) { mutableFloatStateOf(hsv[2]) }

    fun emitColor() {
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
        onColorChange(Color(argb))
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Hue
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("H", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(16.dp))
            Slider(
                value = hue,
                onValueChange = { hue = it; emitColor() },
                valueRange = 0f..360f,
                modifier = Modifier.weight(1f)
            )
            Text("${hue.toInt()}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
        }
        // Saturation
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("S", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(16.dp))
            Slider(
                value = saturation,
                onValueChange = { saturation = it; emitColor() },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f)
            )
            Text("${(saturation * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
        }
        // Brightness
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("V", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(16.dp))
            Slider(
                value = brightness,
                onValueChange = { brightness = it; emitColor() },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f)
            )
            Text("${(brightness * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
        }

        // Quick presets row
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val presets = listOf(
                Color.White, Color.Black, Color(0xFF5CCFE6), Color(0xFFF0B866),
                Color(0xFFCBB2F0), Color(0xFF66BB6A), Color(0xFFEF5350), Color(0xFFFF7043)
            )
            presets.forEach { preset ->
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(preset)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { onColorChange(preset) }
                )
            }
        }
    }
}

// ── Live preview ────────────────────────────────────────────────────

@Composable
private fun ThemePreviewLive(colorScheme: ColorScheme) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.background)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // App bar simulation
            Surface(
                color = colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ServerDash", color = colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(20.dp).clip(CircleShape).background(colorScheme.primary))
                }
            }

            // Cards
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Primary" to colorScheme.primaryContainer to colorScheme.onPrimaryContainer,
                    "Secondary" to colorScheme.secondaryContainer to colorScheme.onSecondaryContainer,
                    "Tertiary" to colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer
                ).forEach { (pair, onColor) ->
                    val (label, bg) = pair
                    Surface(
                        color = bg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(16.dp).clip(CircleShape).background(onColor))
                            Spacer(Modifier.height(4.dp))
                            Text(label, color = onColor, fontSize = 9.sp, maxLines = 1)
                        }
                    }
                }
            }

            // Surface variant card
            Surface(
                color = colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Surface Variant", color = colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = colorScheme.primary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Button", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = colorScheme.onPrimary, fontSize = 11.sp)
                        }
                        Surface(
                            color = colorScheme.error,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Error", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = colorScheme.onError, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Outline + text colors
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Text", color = colorScheme.onBackground, fontSize = 11.sp)
                Text("Muted", color = colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Text("Primary", color = colorScheme.primary, fontSize = 11.sp)
                Text("Error", color = colorScheme.error, fontSize = 11.sp)
            }
        }
    }
}
