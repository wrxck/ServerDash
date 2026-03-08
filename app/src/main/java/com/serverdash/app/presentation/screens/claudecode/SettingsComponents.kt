package com.serverdash.app.presentation.screens.claudecode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*

@Composable
internal fun SettingsSectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
    HorizontalDivider(Modifier.padding(top = 4.dp))
}

@Composable
internal fun StringSetting(label: String, key: String, obj: JsonObject, onUpdate: (String, JsonElement?) -> Unit, placeholder: String = "") {
    val parts = key.split(".")
    val currentValue = try {
        if (parts.size == 1) obj[parts[0]]?.jsonPrimitive?.content ?: ""
        else (obj[parts[0]] as? JsonObject)?.get(parts[1])?.jsonPrimitive?.content ?: ""
    } catch (e: Exception) { "" }
    var editValue by remember(currentValue) { mutableStateOf(currentValue) }
    OutlinedTextField(
        value = editValue, onValueChange = { editValue = it },
        label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text(placeholder) },
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        trailingIcon = {
            if (editValue != currentValue) {
                IconButton(onClick = { onUpdate(key, if (editValue.isBlank()) null else JsonPrimitive(editValue)) }) {
                    Icon(Icons.Default.Check, "Apply", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    )
}

@Composable
internal fun CcSwitchSetting(label: String, key: String, obj: JsonObject, onUpdate: (String, JsonElement?) -> Unit, default: Boolean = false) {
    val parts = key.split(".")
    val currentValue = try {
        if (parts.size == 1) obj[parts[0]]?.jsonPrimitive?.boolean ?: default
        else (obj[parts[0]] as? JsonObject)?.get(parts[1])?.jsonPrimitive?.boolean ?: default
    } catch (e: Exception) { default }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = currentValue, onCheckedChange = { onUpdate(key, JsonPrimitive(it)) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CcSegmentedSetting(label: String, key: String, obj: JsonObject, onUpdate: (String, JsonElement?) -> Unit, options: List<String>, default: String? = null) {
    val parts = key.split(".")
    val currentValue = try {
        if (parts.size == 1) obj[parts[0]]?.jsonPrimitive?.content
        else (obj[parts[0]] as? JsonObject)?.get(parts[1])?.jsonPrimitive?.content
    } catch (e: Exception) { null }
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = currentValue == option || (currentValue == null && option == default),
                    onClick = { onUpdate(key, JsonPrimitive(option)) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size)
                ) { Text(option, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            }
        }
    }
}

@Composable
internal fun IntSliderSetting(label: String, key: String, obj: JsonObject, onUpdate: (String, JsonElement?) -> Unit, range: IntRange, default: Int) {
    val currentValue = try { obj[key]?.jsonPrimitive?.int ?: default } catch (e: Exception) { default }
    var sliderValue by remember(currentValue) { mutableFloatStateOf(currentValue.toFloat()) }
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(if (sliderValue.toInt() == 0 && range.first == 0) "Disabled" else "${sliderValue.toInt()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = sliderValue, onValueChange = { sliderValue = it }, onValueChangeFinished = { onUpdate(key, JsonPrimitive(sliderValue.toInt())) }, valueRange = range.first.toFloat()..range.last.toFloat(), steps = range.last - range.first - 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StringListSetting(label: String, key: String, obj: JsonObject, onUpdate: (String, JsonElement?) -> Unit, placeholder: String = "") {
    val parts = key.split(".")
    val currentList = try {
        val parent = if (parts.size == 1) obj else obj[parts[0]]?.jsonObject ?: JsonObject(emptyMap())
        val listKey = if (parts.size == 1) parts[0] else parts[1]
        parent[listKey]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
    } catch (e: Exception) { emptyList() }
    var showAdd by remember { mutableStateOf(false) }
    var newItem by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Row {
                    Text("${currentList.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { showAdd = !showAdd; newItem = "" }, modifier = Modifier.size(24.dp)) {
                        Icon(if (showAdd) Icons.Default.Close else Icons.Default.Add, "Add", modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (showAdd) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newItem, onValueChange = { newItem = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text(placeholder) }, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    IconButton(onClick = {
                        if (newItem.isNotBlank()) {
                            val updated = currentList + newItem.trim()
                            onUpdate(key, JsonArray(updated.map { JsonPrimitive(it) }))
                            newItem = ""; showAdd = false
                        }
                    }, enabled = newItem.isNotBlank()) {
                        Icon(Icons.Default.Check, "Add", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (currentList.isEmpty()) {
                Text("None", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    currentList.forEach { item ->
                        InputChip(selected = false, onClick = {
                            val updated = currentList - item
                            onUpdate(key, if (updated.isEmpty()) null else JsonArray(updated.map { JsonPrimitive(it) }))
                        }, label = { Text(item, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 1) }, trailingIcon = { Icon(Icons.Default.Close, "Remove", Modifier.size(14.dp)) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun HooksViewer(obj: JsonObject) {
    val hooks = try { obj["hooks"]?.jsonObject } catch (e: Exception) { null }
    if (hooks == null || hooks.isEmpty()) {
        Text("No hooks configured", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            hooks.entries.forEach { (eventName, eventValue) ->
                var expanded by remember { mutableStateOf(false) }
                val hookArray = try { eventValue.jsonArray } catch (e: Exception) { null }
                val hookCount = hookArray?.sumOf { entry -> try { entry.jsonObject["hooks"]?.jsonArray?.size ?: 0 } catch (e: Exception) { 0 } } ?: 0
                Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(eventName, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("$hookCount hooks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (expanded && hookArray != null) {
                    hookArray.forEach { entry ->
                        val entryObj = try { entry.jsonObject } catch (e: Exception) { return@forEach }
                        val matcher = try { entryObj["matcher"]?.jsonPrimitive?.content } catch (e: Exception) { null }
                        val entryHooks = try { entryObj["hooks"]?.jsonArray } catch (e: Exception) { return@forEach }
                        if (!matcher.isNullOrBlank()) {
                            Text("  Matcher: $matcher", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(start = 24.dp))
                        }
                        entryHooks?.forEach { hook ->
                            val hookObj = try { hook.jsonObject } catch (e: Exception) { return@forEach }
                            val type = try { hookObj["type"]?.jsonPrimitive?.content ?: "?" } catch (e: Exception) { "?" }
                            val command = try { hookObj["command"]?.jsonPrimitive?.content ?: hookObj["prompt"]?.jsonPrimitive?.content ?: hookObj["url"]?.jsonPrimitive?.content ?: "?" } catch (e: Exception) { "?" }
                            Row(Modifier.padding(start = 24.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                SuggestionChip(onClick = {}, label = { Text(type, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(24.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(command.substringAfterLast("/"), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
internal fun PluginsMapSetting(obj: JsonObject, onUpdate: (String, JsonElement?) -> Unit) {
    val enabledPlugins = try { obj["enabledPlugins"]?.jsonObject } catch (e: Exception) { null }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Enabled Plugins", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            if (enabledPlugins == null || enabledPlugins.isEmpty()) {
                Text("None configured", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                enabledPlugins.entries.forEach { (pluginId, value) ->
                    val enabled = try { value.jsonPrimitive.boolean } catch (e: Exception) { true }
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(pluginId, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        Switch(checked = enabled, onCheckedChange = { newVal ->
                            val current = enabledPlugins.toMutableMap()
                            current[pluginId] = JsonPrimitive(newVal)
                            onUpdate("enabledPlugins", JsonObject(current))
                        })
                    }
                }
            }
        }
    }
}

@Composable
internal fun CcEnvVarsCard(obj: JsonObject, onUpdate: (String, JsonElement?) -> Unit) {
    val envVars = try { obj["env"]?.jsonObject?.entries?.associate { (k, v) -> k to v.jsonPrimitive.content } ?: emptyMap() } catch (e: Exception) { emptyMap() }
    var showAdd by remember { mutableStateOf(false) }
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Environment Variables", style = MaterialTheme.typography.titleSmall)
                }
                IconButton(onClick = { showAdd = !showAdd; newKey = ""; newValue = "" }) {
                    Icon(if (showAdd) Icons.Default.Close else Icons.Default.Add, "Add")
                }
            }
            if (showAdd) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newKey, onValueChange = { newKey = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("KEY") }, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(value = newValue, onValueChange = { newValue = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("value") }, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = {
                        if (newKey.isNotBlank()) {
                            val currentEnv = envVars.toMutableMap()
                            currentEnv[newKey.trim()] = newValue.trim()
                            onUpdate("env", JsonObject(currentEnv.mapValues { JsonPrimitive(it.value) }))
                            newKey = ""; newValue = ""; showAdd = false
                        }
                    }, enabled = newKey.isNotBlank()) {
                        Icon(Icons.Default.Check, "Add", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (envVars.isEmpty()) {
                Text("None", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
            } else {
                envVars.entries.forEachIndexed { index, (key, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(key, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                            Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            val updated = envVars.toMutableMap()
                            updated.remove(key)
                            onUpdate("env", if (updated.isEmpty()) null else JsonObject(updated.mapValues { JsonPrimitive(it.value) }))
                        }) { Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) }
                    }
                    if (index < envVars.size - 1) HorizontalDivider()
                }
            }
        }
    }
}

@Composable
internal fun DiffDialog(diffs: List<SettingsDiffEntry>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review Changes") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(diffs) { diff ->
                    Row(Modifier.fillMaxWidth()) {
                        Icon(
                            when (diff.type) {
                                DiffType.ADDED -> Icons.Default.Add
                                DiffType.REMOVED -> Icons.Default.Remove
                                DiffType.CHANGED -> Icons.Default.Edit
                            },
                            null,
                            tint = when (diff.type) {
                                DiffType.ADDED -> MaterialTheme.colorScheme.primary
                                DiffType.REMOVED -> MaterialTheme.colorScheme.error
                                DiffType.CHANGED -> MaterialTheme.colorScheme.tertiary
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(diff.path, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            diff.oldValue?.let {
                                Text("- $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontFamily = FontFamily.Monospace, maxLines = 2)
                            }
                            diff.newValue?.let {
                                Text("+ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, maxLines = 2)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Apply ${diffs.size} changes") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
