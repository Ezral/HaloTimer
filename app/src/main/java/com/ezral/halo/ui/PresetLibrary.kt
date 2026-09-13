package com.ezral.halo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    HaloCard {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
            TextButton(onClick = { focus.clearFocus(); failure = null; browsing = true }, enabled = ready) {
                Text("Browse presets (${snapshot.presets.size})")
            }
            TextButton(onClick = {
                focus.clearFocus(); name = track.definition.name; failure = null; saving = true
            }, enabled = ready, modifier = Modifier.semantics { contentDescription = "Save current timer as preset" }) { Text("Save current") }
        }
        notice?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
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
                if (snapshot.presets.isEmpty()) Text("No saved presets yet. Set up a timer, then choose Save current.")
                snapshot.presets.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }.forEach { preset ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(preset.name, fontWeight = FontWeight.SemiBold)
                        val d = preset.definition
                        Text((if (d.sequence) "Sequence · ${d.steps.size} steps" else "Single timer") +
                            " · ${formatTime(d.runSteps().sumOf { it.durationMs }, d.hoursEnabled)}" +
                            " · ${if (d.repetitions == 0) "∞" else "${d.repetitions}×"}", fontSize = 12.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { target = preset; action = "Load"; failure = null },
                                enabled = track.session == null && !busy,
                                modifier = Modifier.semantics { contentDescription = "Load preset ${preset.name}" }) { Text("Load") }
                            TextButton(onClick = { target = preset; name = preset.name; action = "Rename"; failure = null }, enabled = !busy,
                                modifier = Modifier.semantics { contentDescription = "Rename preset ${preset.name}" }) { Text("Rename") }
                            TextButton(onClick = { target = preset; action = "Update"; failure = null }, enabled = !busy,
                                modifier = Modifier.semantics { contentDescription = "Update preset ${preset.name}" }) { Text("Update") }
                            TextButton(onClick = { target = preset; action = "Delete"; failure = null }, enabled = !busy,
                                modifier = Modifier.semantics { contentDescription = "Delete preset ${preset.name}" }) { Text("Delete") }
                        }
                        HorizontalDivider()
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
