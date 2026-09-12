package com.ezral.halo.ui

import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ezral.halo.core.*
import com.ezral.halo.data.Preferences
import com.ezral.halo.overlay.HaloGlass
import com.ezral.halo.runtime.TimerCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.hypot

@Composable
fun FullScreenTimers(c: TimerCoordinator, onSettings: () -> Unit, onOverlay: () -> Unit, onStart: (Set<Int>) -> Unit) {
    val state by c.state.collectAsStateWithLifecycle()
    val prefs by c.prefs.collectAsStateWithLifecycle()
    val ready by c.ready.collectAsStateWithLifecycle()
    val error by c.error.collectAsStateWithLifecycle()
    val tracks = state.tracks.filter { it.definition.active && prefs.fullScreenMask and (1 shl it.definition.id) != 0 }
    val dark = prefs.theme == "Dark" || prefs.theme == "System" && isSystemInDarkTheme()
    val background = if (dark) Color(0xFF0B0D13) else Color(0xFFF6F7FC)
    val foreground = if (dark) Color(0xFFF4F5FC) else Color(0xFF171B28)
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (isActive) { withFrameNanos { now = SystemClock.elapsedRealtime() }; delay(16) }
    } }
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (isActive) { c.execute(Command.Tick); delay(200) }
    } }
    val window = LocalActivity.current?.window
    val awake = prefs.keepScreenOn && tracks.any { it.session?.status == Status.RUNNING }
    DisposableEffect(window, awake) {
        if (awake) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme(), typography = HaloTypography) {
        Column(Modifier.fillMaxSize().background(background).safeDrawingPadding().semantics { contentDescription = "Full-screen timer display" }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSettings, modifier = Modifier.semantics { contentDescription = "Open timer settings" }) { Text("☰", color = foreground, fontSize = 22.sp) }
                Text("halo", Modifier.weight(1f), color = foreground, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                IconButton(onClick = { onStart(tracks.map { it.definition.id }.toSet()) }, enabled = ready && tracks.isNotEmpty(), modifier = Modifier.semantics { contentDescription = "Start displayed timers" }) { Text("▶▶", color = foreground, fontSize = 18.sp) }
                IconButton(onClick = { c.submit(Command.StopAll) }, enabled = state.tracks.any { it.session != null }, modifier = Modifier.semantics { contentDescription = "Stop all timers" }) { Text("■", color = foreground, fontSize = 20.sp) }
                IconButton(onClick = onOverlay, modifier = Modifier.semantics { contentDescription = "Return to overlay" }) { Text("↗", color = foreground, fontSize = 25.sp) }
            }
            error?.let { message -> Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().clickable { c.error.value = null }.padding(12.dp)) }
            if (tracks.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TextButton(onClick = onSettings) { Text("Choose active timers in settings") }
            } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val landscape = maxWidth > maxHeight
                val panels = remember(tracks.size, landscape) { timerPanels(tracks.size, landscape) }
                tracks.forEachIndexed { index, track -> key(track.definition.id) {
                    TimerSection(track, panels[index], tracks.size, landscape, now, prefs, dark, foreground, maxWidth, maxHeight,
                        onPrimary = { if (track.session?.status == Status.RUNNING) c.submit(Command.Pause(track.definition.id)) else onStart(setOf(track.definition.id)) },
                        onReset = { c.submit(Command.Rewind(track.definition.id)) }, onStop = { c.submit(Command.Reset(track.definition.id)) })
                } }
            }
        }
    }
}

@Composable
private fun TimerSection(track: Track, panel: TimerPanel, count: Int, landscape: Boolean, now: Long,
    prefs: Preferences, dark: Boolean, foreground: Color, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp,
    onPrimary: () -> Unit, onReset: () -> Unit, onStop: () -> Unit) {
    val d = track.definition; val s = track.session
    val accent = Color(d.color)
    // Pale custom colors still need legible large digits in the light theme.
    val digitColor = remember(d.color, dark, foreground) {
        val surface = ColorUtils.compositeColors(ColorUtils.setAlphaComponent(d.color.toInt(), if(dark) 23 else 14),
            if(dark) 0xFF0B0D13.toInt() else 0xFFF6F7FC.toInt())
        if(ColorUtils.calculateContrast(d.color.toInt(), surface) >= 3.0) accent else foreground
    }
    val colors = if (d.linePalette == LinePalette.SOLID) listOf(accent, accent) else d.linePalette.colors.map { Color(it) }
    val brush = remember(colors) { Brush.sweepGradient(colors + colors.first()) }
    var motion by remember(s?.id) { mutableStateOf<CompletionMotion?>(null) }
    var startedAt by remember(s?.id) { mutableLongStateOf(0) }
    var completionText by remember(s?.id) { mutableStateOf("") }
    val boundary = s?.cycleCompletedAtMs?.takeIf { it > 0 } ?: if (s?.status == Status.COMPLETED) s.alertStartedAtMs else 0
    LaunchedEffect(s?.id, boundary, prefs.completionEnabled) {
        if (!prefs.completionEnabled) motion = null
        else if (boundary > 0 && now - boundary in 0..10_000 && (s?.status == Status.COMPLETED || motion == null || motion!!.finished(now - startedAt))) {
            startedAt = SystemClock.elapsedRealtime(); motion = CompletionMotion(prefs.completionSeconds * 1000L)
            completionText = (if (s?.status == Status.COMPLETED) "Timer is completed for" else "Round ${(s?.round ?: 2) - 1} completed") +
                "\n${d.name}" + if (d.sequence) " · ${s?.steps?.lastOrNull()?.name.orEmpty()}" else ""
        }
    }
    val elapsed = (now - startedAt).coerceAtLeast(0)
    val completion = if (motion?.finished(elapsed) == false) motion!!.progress(elapsed) else 0f
    val density = LocalDensity.current
    val outline = remember(panel, width, height, density.density) { Path().apply {
        with(density) { panel.outline.forEachIndexed { i, p -> if(i==0) moveTo(p.x*width.toPx(),p.y*height.toPx()) else lineTo(p.x*width.toPx(),p.y*height.toPx()) } }; close()
    } }
    val measure = remember(outline) { PathMeasure().apply { setPath(outline, true) } }
    val progress = remember(outline) { Path() }
    Canvas(Modifier.fillMaxSize()) {
        val path = outline
        drawPath(path, accent.copy(alpha = if (dark) .09f else .055f))
        drawPath(path, foreground.copy(alpha=.12f), style=Stroke(1.dp.toPx()))
        fun line(start: Float, fraction: Float, alpha: Float = 1f) {
            val from = ((start%1f)+1f)%1f
            val to = from + fraction.coerceIn(0f,1f)
            progress.reset(); measure.getSegment(from*measure.length,minOf(to,1f)*measure.length,progress,true)
            if(to>1f) measure.getSegment(0f,(to-1f)*measure.length,progress,false)
            drawPath(progress, brush, alpha=alpha, style=Stroke(4.dp.toPx()))
        }
        if(s != null && s.visualUntilMs > now) {
            val age = (now-s.alertStartedAtMs).coerceAtLeast(0)
            when(d.alert) {
                AlertStyle.BREATHE -> line(0f,1f,AlertMotion.breathe(age)/255f)
                AlertStyle.ORBIT -> line(AlertMotion.orbit(age),.20f)
                AlertStyle.PING_PONG, AlertStyle.DOUBLE_PONG -> {
                    val p=AlertMotion.pong(age,d.alert==AlertStyle.DOUBLE_PONG); line(p,.20f)
                    if(d.alert==AlertStyle.DOUBLE_PONG) line(p+.5f,.20f)
                }
            }
        } else line(0f,s?.progress(now) ?: 1f)
        if (completion > 0) clipPath(path) {
            val center = Offset(panel.x*size.width,panel.y*size.height)
            drawCircle(accent, hypot(size.width, size.height)*completion, center)
        }
    }
    val compact = count == 3 && landscape
    val idealSize = if (count == 1) (if(d.hoursEnabled) 58 else 88) else if (count == 2) (if(d.hoursEnabled) 40 else 64) else (if(d.hoursEnabled) 24 else 36)
    val numberSize = minOf(idealSize.toFloat(), width.value*panel.width / ((if(d.hoursEnabled) 8 else 5)*.65f*density.fontScale))
    Box(Modifier.offset(width*(panel.x-panel.width/2), height*(panel.y-panel.height/2)).width(width*panel.width).height(height*panel.height).semantics { isTraversalGroup = true }.drawWithContent {
        if(completion>0) clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(center=Offset(size.width/2,size.height/2), radius=hypot(width.toPx(),height.toPx())*completion)) }) { this@drawWithContent.drawContent() }
        else drawContent()
    }, contentAlignment=Alignment.Center) {
        if (completion > 0) {
            Text(completionText, color=Color(HaloGlass.foreground(d.color.toInt())),
                fontSize=minOf(prefs.completionTextSp, if(count==3) 18 else if(count==2) 24 else 48).sp,
                lineHeight=minOf(prefs.completionTextSp, if(count==3) 18 else if(count==2) 24 else 48).times(1.3f).sp,
                fontWeight=if(prefs.completionBold) FontWeight.Bold else FontWeight.Normal,
                textAlign=if(prefs.completionAlignment=="Left") TextAlign.Start else TextAlign.Center,
                maxLines=if(compact) 4 else 7, overflow=TextOverflow.Ellipsis,
                modifier=Modifier.fillMaxWidth().clickable { motion?.close(elapsed) }.semantics { contentDescription="${d.name} completion animation. Tap to close." })
        } else Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(if(compact) 0.dp else 6.dp)) {
            Text(d.name + (if (d.sequence) " · ${s?.steps?.getOrNull(s.index)?.name ?: d.steps.first().name}" else ""),
                color=foreground, fontWeight=FontWeight.SemiBold, fontSize=(if(count==3) 13 else 19).sp,
                maxLines=if(compact) 1 else 2, overflow=TextOverflow.Ellipsis, textAlign=TextAlign.Center)
            Text(formatTime(s?.remaining(now) ?: d.runSteps().first().durationMs,d.hoursEnabled), fontFamily=CountdownMono,
                fontWeight=FontWeight.Bold, fontSize=numberSize.sp, color=digitColor, maxLines=1,
                modifier=Modifier.semantics { contentDescription="${d.name} countdown" })
            Text(if(s?.repetitions != null && s.repetitions != 1) s.roundLabel() else s?.status?.name ?: "READY", color=foreground.copy(alpha=.65f), fontSize=11.sp)
            Row(horizontalArrangement=Arrangement.Center) {
                IconButton(onClick=onPrimary, modifier=Modifier.size(48.dp).semantics { contentDescription="${if(s?.status==Status.RUNNING) "Pause" else "Start"} ${d.name} full screen" }) { Text(if(s?.status==Status.RUNNING) "Ⅱ" else "▶", color=foreground,fontSize=21.sp) }
                IconButton(onClick=onReset, enabled=s!=null, modifier=Modifier.size(48.dp).semantics { contentDescription="Reset ${d.name} full screen" }) { Text("↺", color=foreground,fontSize=25.sp) }
                IconButton(onClick=onStop, enabled=s!=null, modifier=Modifier.size(48.dp).semantics { contentDescription="Stop ${d.name} full screen" }) { Text("■", color=foreground,fontSize=20.sp) }
            }
        }
    }
}
