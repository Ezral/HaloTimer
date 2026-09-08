package com.ezral.halo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezral.halo.core.*
import com.ezral.halo.runtime.TimerCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val palette = listOf(
    0xFFAA9CFF, 0xFF57DDB4, 0xFFFFBA77, 0xFF7BBEFF, 0xFFFF90B4, 0xFFF2D46F,
    0xFFFF2D2D, // bright red
    0xFF1565FF, // strong blue
    0xFF8A2BE2, // purple
)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HaloScreen(
    c: TimerCoordinator, initialSelected: Int,
    onSelect: (Int, Int) -> Unit, onLaunch: (Int) -> Unit, onPreview: (Int) -> Unit,
    onOverlayPermission: () -> Unit, onAlarmPermission: () -> Unit, onNotificationPermission: () -> Unit,
) {
    val state by c.state.collectAsStateWithLifecycle()
    val prefs by c.prefs.collectAsStateWithLifecycle()
    val ready by c.ready.collectAsStateWithLifecycle()
    val error by c.error.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableIntStateOf(initialSelected.coerceIn(0, 2)) }
    var selectedStep by rememberSaveable(selected) { mutableIntStateOf(0) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) permissionRevision++ }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val overlay = remember(permissionRevision) { Settings.canDrawOverlays(context) }
    val exact = remember(permissionRevision) { c.alarmScheduler.exactAvailable() }
    val notifications = remember(permissionRevision) { Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(lifecycle) {
        while (true) { if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { c.execute(Command.Tick); now = SystemClock.elapsedRealtime() }; delay(200) }
    }
    LaunchedEffect(selected, selectedStep) { onSelect(selected, selectedStep) }
    val dark = prefs.theme == "Dark" || (prefs.theme == "System" && isSystemInDarkTheme())
    LaunchedEffect(dark) {
        (context as? android.app.Activity)?.window?.let { window ->
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        }
    }
    val scheme = if (dark) darkColorScheme(primary = Color(0xFFB4A8FF), background = Color(0xFF101116), surface = Color(0xFF1B1C24), surfaceVariant = Color(0xFF272833))
    else lightColorScheme(primary = Color(0xFF6250C4), background = Color(0xFFF3F3F8), surface = Color.White, surfaceVariant = Color(0xFFEAE8F2))
    MaterialTheme(colorScheme = scheme, typography = HaloTypography, shapes = Shapes(medium = RoundedCornerShape(24.dp), large = RoundedCornerShape(28.dp))) {
        Surface(Modifier.fillMaxSize(), color = scheme.background) {
            val track = state.tracks[selected]
            val d = track.definition
            val s = track.session
            val editable = s == null
            val accent = Color(d.color)
            val scroll = rememberScrollState()
            var pendingPreset by remember { mutableStateOf<List<Step>?>(null) }
            var editingMorse by remember { mutableStateOf(false) }
            var morseDraft by remember(d.morse) { mutableStateOf(d.morse) }
            var customColor by remember { mutableStateOf(false) }
            var colorDraft by remember(d.color) { mutableStateOf("#%06X".format(d.color and 0xFFFFFF)) }
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Column(Modifier.fillMaxWidth().zIndex(1f).shadow(12.dp, clip = false,
                    ambientColor = Color.Black.copy(alpha = .10f), spotColor = Color.Black.copy(alpha = .18f))
                    .background(scheme.background).padding(horizontal = 22.dp, vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("halo", fontSize = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-2).sp)
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                focus.clearFocus()
                                if (s?.status == Status.RUNNING) c.submit(Command.Pause(selected)) else onLaunch(selected)
                            }, enabled = ready && d.active && s?.status !in listOf(Status.COMPLETED, Status.INTERRUPTED),
                                modifier = Modifier.semantics { contentDescription = when (s?.status) { Status.RUNNING -> "Pause timer"; Status.PAUSED -> "Resume timer"; else -> "Start timer" } }) {
                                Text(if (s?.status == Status.RUNNING) "Ⅱ" else "▶", fontSize = 22.sp)
                            }
                            IconButton(onClick = { focus.clearFocus(); onLaunch(-1) }, enabled = ready && state.tracks.any { it.definition.active },
                                modifier = Modifier.semantics { contentDescription = "Start all timers" }) {
                                Text("▶▶", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { c.submit(Command.StopAll) }, enabled = ready && state.tracks.any { it.session != null },
                                modifier = Modifier.semantics { contentDescription = "Stop all timers" }) {
                                Text("■", fontSize = 21.sp)
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 22.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.tracks.forEach { t ->
                        val isSelected = selected == t.definition.id
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                            .background(if (isSelected) scheme.surface else scheme.surfaceVariant.copy(alpha = 0.4f))
                            .selectableTab(isSelected, "${t.definition.name}, ${if (t.definition.active) "active" else "inactive"}, ${t.session?.status ?: Status.READY}") {
                                focus.clearFocus(); selected = t.definition.id
                            }.padding(horizontal = 12.dp, vertical = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(6.dp).background(Color(t.definition.color), RoundedCornerShape(3.dp)))
                                Text(t.definition.name, maxLines = 2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text(if (t.definition.active) "Active" else "Inactive", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                            if (t.session != null) Text(formatTime(t.session!!.remaining(now)), fontFamily = CountdownMono, fontSize = 12.sp)
                        }
                    }
                }
                if (!ready) Text("Opening timers…", color = scheme.onSurfaceVariant)
                if (error != null) Card(colors = CardDefaults.cardColors(containerColor = scheme.errorContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(error!!, Modifier.weight(1f), color = scheme.onErrorContainer)
                        TextButton(onClick = { c.error.value = null }) { Text("Close") }
                    }
                }
                HaloCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CommitText(d.name, "Timer name", Modifier.weight(1f), enabled = ready, maxLength = 48) { c.submit(Command.Edit(d.copy(name = it.trim()))) }
                        Switch(checked = d.active, onCheckedChange = { c.submit(Command.Activate(selected, it)) }, enabled = ready, modifier = Modifier.semantics { contentDescription = "${d.name} active" })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!d.sequence, { c.submit(Command.Edit(d.copy(sequence = false))) }, { Text("Single") }, enabled = editable && ready)
                        FilterChip(d.sequence, { c.submit(Command.Edit(d.copy(sequence = true))) }, { Text("Sequence") }, enabled = editable && ready)
                    }
                    if (s != null) {
                        Text(when (s.status) { Status.RUNNING -> "IN PROGRESS"; Status.PAUSED -> "PAUSED"; Status.COMPLETED -> "COMPLETED"; Status.INTERRUPTED -> "INTERRUPTED · RESET TO CONTINUE"; else -> "READY" }, color = scheme.primary, fontSize = 11.sp, letterSpacing = 1.sp)
                        Text(formatTime(s.remaining(now)), fontFamily = CountdownMono, fontSize = 60.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        if (s.steps.size > 1) Text("${s.index + 1} / ${s.steps.size}  ·  ${s.steps[s.index].name}", color = scheme.onSurfaceVariant)
                        LinearProgressIndicator(progress = { s.progress(now) }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)), color = accent)
                        if (s.status == Status.RUNNING || s.status == Status.PAUSED) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            TextButton(onClick = { c.submit(Command.Adjust(c.target(selected), -30_000)) }) { Text("− 30s") }
                            TextButton(onClick = { c.submit(Command.Adjust(c.target(selected), 30_000)) }) { Text("+ 30s") }
                        }
                    } else if (!d.sequence) {
                        DurationEditor(d.durationMs, enabled = ready) { c.submit(Command.Edit(d.copy(durationMs = it))) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf(1, 5, 15, 25).forEach { minutes -> AssistChip(onClick = { c.submit(Command.Edit(d.copy(durationMs = minutes * 60_000L))) }, label = { Text("${minutes}m") }) }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { pendingPreset = pourOver }) { Text("Pour-over") }
                            TextButton(onClick = { pendingPreset = steak }) { Text("Steak") }
                        }
                        d.steps.forEachIndexed { index, step ->
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (selectedStep == index) scheme.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { selectedStep = index }) { Text("${index + 1}") }
                                    CommitText(step.name, "Step ${index + 1} name", Modifier.weight(1f), maxLength = 120) { name -> c.submit(Command.Edit(d.copy(steps = d.steps.toMutableList().apply { set(index, step.copy(name = name.trim())) }))) }
                                    TextButton(onClick = { selectedStep = 0; c.submit(Command.Edit(d.copy(steps = d.steps.filterIndexed { i, _ -> i != index }))) }, enabled = d.steps.size > 1) { Text("×", Modifier.semantics { contentDescription = "Remove step ${index + 1}" }) }
                                }
                                DurationEditor(step.durationMs, compact = true, onInteract = { selectedStep = index }) { ms -> c.submit(Command.Edit(d.copy(steps = d.steps.toMutableList().apply { set(index, step.copy(durationMs = ms)) }))) }
                            }
                        }
                        TextButton(onClick = { c.submit(Command.Edit(d.copy(steps = d.steps + Step("Step ${d.steps.size + 1}", 60_000)))) }, enabled = d.steps.size < 50) { Text("+ Add timer") }
                        Text("Total ${formatTime(d.steps.sumOf { it.durationMs })}", fontFamily = CountdownMono, color = scheme.onSurfaceVariant)
                    }
                    if (s != null) OutlinedButton(onClick = { c.submit(Command.Reset(selected)) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (s.status == Status.COMPLETED) "Dismiss" else "Reset")
                    }
                }
                HaloCard {
                    Text("Edge light", fontWeight = FontWeight.SemiBold)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), maxItemsInEachRow = 6) {
                        palette.forEach { color ->
                            val taken = state.tracks.any { it.definition.id != selected && it.definition.active && it.definition.color == color }
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable(enabled = !taken) { c.submit(Command.Edit(d.copy(color = color))) }.semantics { contentDescription = "${palette.indexOf(color) + 1}: ${if (taken) "Used by another timer" else "Choose color"}" }, contentAlignment = Alignment.Center) {
                                Box(Modifier.size(28.dp).background(Color(color).copy(alpha = if (taken) .2f else 1f), RoundedCornerShape(14.dp)))
                                if (d.color == color) Text("✓", color = Color(0xFF20202A), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    OutlinedButton(onClick = { colorDraft = "#%06X".format(d.color and 0xFFFFFF); customColor = true }) { Text("Custom line color") }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AlertStyle.entries.forEach { style -> FilterChip(d.alert == style, { c.submit(Command.Edit(d.copy(alert = style))) }, { Text(when (style) { AlertStyle.BREATHE -> "Breathe"; AlertStyle.ORBIT -> "Orbit"; AlertStyle.PING_PONG -> "Ping-pong"; AlertStyle.DOUBLE_PONG -> "Double pong" }) }) }
                    }
                    Text("Glow", color = scheme.onSurfaceVariant)
                    var glow by remember(d.glow) { mutableFloatStateOf(d.glow) }
                    Slider(glow, { glow = it }, onValueChangeFinished = { c.submit(Command.Edit(d.copy(glow = glow))) }, modifier = Modifier.semantics { contentDescription = "Edge glow strength" })
                    SettingToggle("Floating control", !d.hidden) { c.submit(Command.Hide(selected, !it)) }
                    TextButton(onClick = { if (overlay) onPreview(selected) else onOverlayPermission() }) { Text("Preview edge alert") }
                }
                HaloCard {
                    Text("Vibration", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HapticStyle.entries.forEach { style -> FilterChip(d.haptic == style, { c.submit(Command.Edit(d.copy(haptic = style))) }, { Text(when (style) { HapticStyle.OFF -> "Off"; HapticStyle.DOUBLE_TAP -> "Double tap"; HapticStyle.MORSE -> "Morse" }) }) }
                    }
                    if (d.haptic == HapticStyle.MORSE) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(d.morse, fontWeight = FontWeight.SemiBold)
                                Text(Morse.display(d.morse), fontFamily = Poppins, color = accent)
                            }
                            TextButton(onClick = { morseDraft = d.morse; editingMorse = true }) { Text("Edit") }
                        }
                        Text("${Morse.encode(d.morse).sumOf { it.ms } / 1000.0}s", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    if (d.haptic != HapticStyle.OFF) {
                        Text("Repeat", color = scheme.onSurfaceVariant)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HapticRepeat.entries.filterNot { it == HapticRepeat.TIMED }.forEach { repeat ->
                                FilterChip(d.hapticRepeat == repeat || (repeat == HapticRepeat.CUSTOM && d.hapticRepeat == HapticRepeat.TIMED), { c.submit(Command.Edit(d.copy(hapticRepeat = repeat))) }, {
                                    Text(when (repeat) { HapticRepeat.ONCE -> "Once"; HapticRepeat.THREE -> "3×"; HapticRepeat.FIVE -> "5×"; HapticRepeat.UNTIL_DISMISS -> "Until dismiss"; HapticRepeat.CUSTOM, HapticRepeat.TIMED -> "Custom" })
                                })
                            }
                        }
                        if (d.hapticRepeat in listOf(HapticRepeat.CUSTOM, HapticRepeat.TIMED)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(d.hapticRepeat == HapticRepeat.CUSTOM, { c.submit(Command.Edit(d.copy(hapticRepeat = HapticRepeat.CUSTOM))) }, { Text("Repetitions") })
                                FilterChip(d.hapticRepeat == HapticRepeat.TIMED, { c.submit(Command.Edit(d.copy(hapticRepeat = HapticRepeat.TIMED))) }, { Text("Duration") })
                            }
                        }
                        if (d.hapticRepeat == HapticRepeat.TIMED) {
                            var seconds by remember(d.repeatDurationMs) { mutableStateOf((d.repeatDurationMs / 1000).toString()) }
                            OutlinedTextField(seconds, { value ->
                                if (value.length <= 4 && value.all(Char::isDigit)) {
                                    seconds = value
                                    value.toLongOrNull()?.takeIf { it in 1..3600 }?.let { c.submit(Command.Edit(d.copy(repeatDurationMs = it * 1000))) }
                                }
                            }, label = { Text("Duration in seconds (1–3600)") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Vibration duration" })
                        }
                        if (d.hapticRepeat == HapticRepeat.CUSTOM) {
                            var repeats by remember(d.customRepeatCount) { mutableStateOf(d.customRepeatCount.toString()) }
                            OutlinedTextField(repeats, { value ->
                                if (value.length <= 2 && value.all(Char::isDigit)) {
                                    repeats = value
                                    value.toIntOrNull()?.takeIf { it in 1..99 }?.let { c.submit(Command.Edit(d.copy(customRepeatCount = it))) }
                                }
                            }, label = { Text("Number of repeats (1–99)") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (d.haptic != HapticStyle.OFF) TextButton(onClick = { c.haptics.preview(d) }) { Text("Test vibration once") }
                }
                HaloCard {
                    Text("Controls", fontWeight = FontWeight.SemiBold)
                    SettingToggle("Volume buttons in Halo", prefs.volume) { c.scope.launch { c.preferences.volume(it) } }
                    if (prefs.volume) Text("${d.name} · 30s → 1m → 5m while held", color = scheme.onSurfaceVariant, fontSize = 13.sp)
                    SettingToggle("Dismiss all timers on menu entry", prefs.dismissAllOnMenu) { c.scope.launch { c.preferences.dismissAllOnMenu(it) } }
                    SettingToggle("Reduce motion", prefs.reducedMotion) { c.scope.launch { c.preferences.motion(it) } }
                    TextButton(onClick = { c.submit(Command.ShowAll); if (state.tracks.any { it.session?.status == Status.RUNNING }) onLaunch(-2) }) { Text("Show all floating controls") }
                }
                if (!overlay || !exact || !notifications) HaloCard {
                    Text("Permissions", fontWeight = FontWeight.SemiBold)
                    if (!overlay) TextButton(onClick = onOverlayPermission) { Text("Enable display over other apps") }
                    if (!exact) TextButton(onClick = onAlarmPermission) { Text("Enable precise alarm access") }
                    if (!notifications) TextButton(onClick = onNotificationPermission) { Text("Enable timer notifications") }
                    Text(if (!exact) "Screen-off alerts may be delayed." else "Short sequence alerts during deep sleep still depend on Android.", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                }
                HaloCard {
                    Text("Appearance", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("System", "Light", "Dark").forEach { theme ->
                            FilterChip(prefs.theme == theme, { c.scope.launch { c.preferences.theme(theme) } }, { Text(if (theme == "System") "Follow system" else theme) })
                        }
                    }
                }
                Text("HALO  /  0.1 ALPHA 06", Modifier.align(Alignment.CenterHorizontally), fontSize = 10.sp, letterSpacing = 2.sp, color = scheme.onSurfaceVariant)
            }
            }
            pendingPreset?.let { preset -> AlertDialog(onDismissRequest = { pendingPreset = null }, title = { Text("Replace this sequence?") }, text = { Text("Your current steps will be replaced by the editable example.") }, confirmButton = { TextButton(onClick = { selectedStep = 0; c.submit(Command.Edit(d.copy(steps = preset))); pendingPreset = null }) { Text("Replace") } }, dismissButton = { TextButton(onClick = { pendingPreset = null }) { Text("Cancel") } }) }
            if (editingMorse) AlertDialog(onDismissRequest = { editingMorse = false }, title = { Text("Morse vibration text") }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(morseDraft, { if (it.length <= 24) morseDraft = it }, singleLine = true, label = { Text("Text") }, modifier = Modifier.semantics { contentDescription = "Morse input" })
                    Text(Morse.display(morseDraft), fontFamily = Poppins, color = accent)
                    Morse.validate(morseDraft)?.let { Text(it, color = scheme.error, fontSize = 12.sp) }
                }
            }, confirmButton = { TextButton(onClick = { c.submit(Command.Edit(d.copy(morse = Morse.normalize(morseDraft)))); editingMorse = false }, enabled = Morse.validate(morseDraft) == null) { Text("Save") } }, dismissButton = { TextButton(onClick = { editingMorse = false }) { Text("Cancel") } })
            if (customColor) AlertDialog(onDismissRequest = { customColor = false }, title = { Text("Custom line color") }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(colorDraft, { if (it.length <= 7) colorDraft = it.uppercase() }, singleLine = true, label = { Text("Hex color") }, modifier = Modifier.semantics { contentDescription = "Custom color input" })
                    parseLineColor(colorDraft)?.let { color -> Box(Modifier.fillMaxWidth().height(32.dp).background(Color(color), RoundedCornerShape(12.dp))) }
                    Text("Example: #FF2D2D", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }, confirmButton = { TextButton(onClick = {
                parseLineColor(colorDraft)?.let { parsed ->
                    c.submit(Command.Edit(d.copy(color = parsed))); customColor = false
                }
            }, enabled = parseLineColor(colorDraft) != null) { Text("Save") } }, dismissButton = { TextButton(onClick = { customColor = false }) { Text("Cancel") } })
        }
    }
}

private fun Modifier.selectableTab(selected: Boolean, description: String, action: () -> Unit): Modifier = this
    .semantics { this.selected = selected; role = Role.Tab; contentDescription = description }
    .clickable(onClick = action)

@Composable private fun HaloCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
@Composable private fun SettingToggle(label: String, value: Boolean, changed: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(value, changed, modifier = Modifier.semantics { contentDescription = label })
    }
}
@Composable private fun CommitText(value: String, label: String, modifier: Modifier = Modifier, enabled: Boolean = true, maxLength: Int = 60, commit: (String) -> Unit) {
    var draft by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    BasicTextField(draft, { if (it.length <= maxLength) draft = it }, modifier.onFocusChanged {
        if (focused && !it.isFocused && draft != value) { commit(draft); draft = value }
        focused = it.isFocused
    }.semantics { contentDescription = label }.padding(vertical = 12.dp), enabled = enabled, singleLine = true,
        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Medium),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
}

@Composable
private fun DurationEditor(value: Long, compact: Boolean = false, enabled: Boolean = true, onInteract: () -> Unit = {}, changed: (Long) -> Unit) {
    val latestValue by rememberUpdatedState(value)
    val latestChanged by rememberUpdatedState(changed)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        listOf(true, false).forEach { minute ->
            if (!minute) Text(":", fontFamily = CountdownMono, fontSize = if (compact) 32.sp else 52.sp, modifier = Modifier.padding(bottom = 20.dp))
            val number = if (minute) value / 60_000 else value / 1000 % 60
            var draft by remember(number) { mutableStateOf("%02d".format(number)) }
            var focused by remember { mutableStateOf(false) }
            val focus = LocalFocusManager.current
            val unit = if (minute) 60_000L else 1_000L
            fun change(delta: Long) { onInteract(); latestChanged((latestValue + delta).coerceIn(MIN_MS, MAX_MS)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                BasicTextField(draft, { if (it.length <= 2 && it.all(Char::isDigit)) draft = it },
                    Modifier.fillMaxWidth().heightIn(min = 64.dp)
                        .onFocusChanged {
                            if (it.isFocused) onInteract()
                            if (focused && !it.isFocused) {
                                val n = draft.toLongOrNull()
                                if (n != null) latestChanged((if (minute) n * 60_000 + latestValue % 60_000 else latestValue / 60_000 * 60_000 + n * 1_000).coerceIn(MIN_MS, MAX_MS))
                                else draft = "%02d".format(number)
                            }; focused = it.isFocused
                        }
                        .pointerInput(minute, enabled) {
                            var dragRemainder = 0f
                            if (enabled) detectVerticalDragGestures(onDragStart = { dragRemainder = 0f; focus.clearFocus(); onInteract() }) { changeEvent, amount ->
                                changeEvent.consume()
                                // One bounded increment per 18dp of deliberate travel.
                                dragRemainder += amount
                                val threshold = 18.dp.toPx()
                                if (kotlin.math.abs(dragRemainder) >= threshold) {
                                    change(if (dragRemainder < 0) unit else -unit); dragRemainder = 0f
                                }
                            }
                        }
                        .pointerInput(minute, enabled) { if (enabled) awaitPointerEventScope { while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val dy = event.changes.first().scrollDelta.y
                                if (dy != 0f) { change(if (dy < 0) unit else -unit); event.changes.forEach { it.consume() } }
                            }
                        } } }
                        .semantics {
                            contentDescription = if (minute) "Minutes" else "Seconds"
                            customActions = listOf(CustomAccessibilityAction("Increase") { change(unit); true }, CustomAccessibilityAction("Decrease") { change(-unit); true })
                        }, enabled = enabled, singleLine = true,
                    textStyle = TextStyle(fontFamily = CountdownMono, fontSize = if (compact) 36.sp else 58.sp, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
                Text(if (minute) "MIN" else "SEC", fontSize = 10.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row { TextButton(onClick = { change(-unit) }, enabled = enabled) { Text("−", Modifier.semantics { contentDescription = "Decrease ${if (minute) "minutes" else "seconds"}" }) }; TextButton(onClick = { change(unit) }, enabled = enabled) { Text("+", Modifier.semantics { contentDescription = "Increase ${if (minute) "minutes" else "seconds"}" }) } }
            }
        }
    }
}

private fun parseLineColor(text: String): Long? = text.removePrefix("#").takeIf { it.length == 6 && it.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' } }?.toLongOrNull(16)?.let { it or 0xFF000000L }
