package com.ezral.halo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezral.halo.core.*
import com.ezral.halo.runtime.TimerCoordinator
import kotlinx.coroutines.launch

/** Actions operate on the coordinator's latest definition after committing focused editors. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetLibrary(c: TimerCoordinator, selected: Int, onLoaded: () -> Unit) {
    val snapshot by c.state.collectAsStateWithLifecycle()
    val ready by c.ready.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val track = snapshot.tracks[selected]
    var browsing by remember(selected) { mutableStateOf(false) }
    var saving by remember(selected) { mutableStateOf(false) }
    var action by remember(selected) { mutableStateOf<String?>(null) }
    var target by remember(selected) { mutableStateOf<TimerPreset?>(null) }
    var name by remember(selected) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var notice by remember(selected) { mutableStateOf<String?>(null) }

    fun perform(command: Command, done: () -> Unit) {
        busy = true; failure = null
        scope.launch {
            try {
                c.error.value = null
                if (c.execute(command)) done()
                else failure = c.error.value ?: "This change could not be saved. Reopen Halo and try again."
            } finally { busy = false }
        }
    }
    val presets = snapshot.presets.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
    val colors = MaterialTheme.colorScheme
    HaloCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Presets", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Your timers, ready to reuse", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
            FilledTonalButton(onClick = {
                focus.clearFocus(); name = track.definition.name; failure = null; saving = true
            }, enabled = ready && !busy, contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.semantics { contentDescription = "Save current timer as preset" }) { Text("+ Save") }
        }
        if (presets.isEmpty()) {
            Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceVariant.copy(alpha = 0.45f)) {
                Text("Save a timer you love. Its timing, look and alerts will be here for next time.",
                    Modifier.fillMaxWidth().padding(14.dp), fontSize = 13.sp, color = colors.onSurfaceVariant)
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(presets, key = { it.id }) { preset ->
                    Surface(onClick = { focus.clearFocus(); target = preset; action = "Load"; failure = null },
                        enabled = ready && track.session == null && !busy,
                        shape = RoundedCornerShape(16.dp), color = colors.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.width(174.dp).semantics { contentDescription = "Use preset ${preset.name}" }) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(7.dp).background(Color(preset.definition.color), RoundedCornerShape(4.dp)))
                                Text(if (preset.definition.sequence) "${preset.definition.steps.size} steps" else "Single timer",
                                    fontSize = 11.sp, color = colors.onSurfaceVariant)
                            }
                            Text(preset.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(presetTiming(preset), fontSize = 12.sp, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
            if (track.session != null) Text("Reset this timer to load a preset.", fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(notice ?: "Saved on this device", Modifier.weight(1f), fontSize = 11.sp,
                color = if (notice != null) colors.primary else colors.onSurfaceVariant)
            TextButton(onClick = { focus.clearFocus(); failure = null; browsing = true }, enabled = ready && !busy) {
                Text("Browse presets (${presets.size})", fontSize = 12.sp)
            }
        }
    }
    if (browsing) AlertDialog(
        onDismissRequest = { if (!busy) browsing = false },
        title = { Text("Saved presets") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Load into ${track.definition.name}. App-wide display settings stay as you set them.", fontSize = 12.sp)
                if (track.session != null) Text("Reset this timer to load a preset. You can still save or manage presets.",
                    color = MaterialTheme.colorScheme.primary)
                if (snapshot.presets.isEmpty()) Text("No saved presets yet. Set up a timer, then tap + Save.")
                presets.forEach { preset ->
                    var expanded by remember(preset.id) { mutableStateOf(false) }
                    Surface(shape = RoundedCornerShape(18.dp), color = colors.surface) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(4.dp).height(36.dp).background(Color(preset.definition.color), RoundedCornerShape(2.dp)))
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(preset.name, fontWeight = FontWeight.SemiBold)
                                    Text(if (preset.definition.sequence) "Sequence · ${preset.definition.steps.size} steps" else "Single timer",
                                        fontSize = 12.sp, color = colors.onSurfaceVariant)
                                }
                                Box {
                                    IconButton(onClick = { expanded = true }, enabled = !busy,
                                        modifier = Modifier.semantics { contentDescription = "Manage preset ${preset.name}" }) {
                                        Text("⋮", fontSize = 24.sp)
                                    }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        listOf("Rename", "Update", "Delete").forEach { operation ->
                                            DropdownMenuItem(text = { Text(if (operation == "Update") "Update from current timer" else operation,
                                                color = if (operation == "Delete") colors.error else colors.onSurface) },
                                                onClick = { expanded = false; target = preset; name = preset.name; action = operation; failure = null },
                                                modifier = Modifier.semantics { contentDescription = "$operation preset ${preset.name}" })
                                        }
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(presetTiming(preset), Modifier.weight(1f), fontSize = 13.sp, color = colors.onSurfaceVariant)
                                FilledTonalButton(onClick = { target = preset; action = "Load"; failure = null },
                                    enabled = ready && track.session == null && !busy,
                                    modifier = Modifier.semantics { contentDescription = "Load preset ${preset.name}" }) { Text("Load") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { browsing = false }, enabled = !busy) { Text("Done") } },
    )
    val preset = target
    if (saving || (preset != null && action != null)) {
        val editingName = saving || action == "Rename"
        val nameError = if (editingName) presetNameError(name, snapshot.presets, if (saving) null else preset?.id) else null
        AlertDialog(
            onDismissRequest = { if (!busy) { saving = false; action = null } },
            title = { Text(if (saving) "Save preset" else "$action preset?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (editingName) {
                        OutlinedTextField(name, { if (it.codePointCount(0, it.length) <= 40) { name = it; failure = null } },
                            label = { Text("Preset name") }, singleLine = true, enabled = !busy,
                            isError = nameError != null && name.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Preset name input" })
                        if (nameError != null && name.isNotEmpty()) Text(nameError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        if (saving) Text("Saves the configured durations, steps, repetitions, colors, floating pill and alerts. Running progress is not saved.", fontSize = 12.sp)
                    } else Text(when (action) {
                        "Load" -> "Replace ${track.definition.name} with “${preset?.name}”? It will be ready to start. Other timers stay as they are."
                        "Update" -> "Replace the settings in “${preset?.name}” with the current ${track.definition.name} configuration?"
                        else -> "Delete “${preset?.name}”? Timers already loaded from it will stay as they are."
                    })
                    failure?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(modifier = Modifier.semantics { contentDescription = "Confirm ${if (saving) "Save" else action} preset" },
                    enabled = ready && !busy && nameError == null && (action != "Load" || track.session == null), onClick = {
                    val command = if (saving) Command.SavePreset(selected, name) else when (action) {
                        "Rename" -> Command.RenamePreset(preset!!.id, name)
                        "Load" -> Command.LoadPreset(preset!!.id, selected)
                        "Update" -> Command.SavePreset(selected, preset!!.name, preset.id)
                        else -> Command.DeletePreset(preset!!.id)
                    }
                    perform(command) {
                        notice = when (command) {
                            is Command.LoadPreset -> "Preset loaded · ready to start"
                            is Command.DeletePreset -> "Preset deleted"
                            is Command.RenamePreset -> "Preset renamed"
                            else -> "Preset saved"
                        }
                        if (command is Command.LoadPreset) { browsing = false; onLoaded() }
                        saving = false; action = null
                    }
                }) { Text(if (busy) "Saving…" else if (saving || action == "Rename") "Save" else action ?: "Save") }
            },
            dismissButton = { TextButton(onClick = { saving = false; action = null }, enabled = !busy) { Text("Cancel") } },
        )
    }
}

private fun presetTiming(preset: TimerPreset): String {
    val d = preset.definition
    return formatTime(d.runSteps().sumOf { it.durationMs }, d.hoursEnabled) +
        " · ${if (d.repetitions == 0) "∞ rounds" else if (d.repetitions == 1) "1 round" else "${d.repetitions} rounds"}"
}
