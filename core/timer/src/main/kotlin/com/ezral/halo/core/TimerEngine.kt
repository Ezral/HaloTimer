package com.ezral.halo.core

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.ceil

const val MIN_MS = 1_000L
const val MAX_MS = 5_999_000L

@Serializable enum class Status { READY, RUNNING, PAUSED, COMPLETED, INTERRUPTED }
@Serializable enum class AlertStyle { BREATHE, ORBIT, PING_PONG, DOUBLE_PONG }
@Serializable enum class DockSide { NONE, LEFT, RIGHT }
@Serializable enum class HapticStyle { OFF, DOUBLE_TAP, MORSE }
@Serializable enum class HapticRepeat { ONCE, THREE, FIVE, UNTIL_DISMISS, CUSTOM, TIMED }
@Serializable data class Step(val name: String = "Timer", val durationMs: Long = 300_000L)
@Serializable data class Definition(
    val id: Int,
    val name: String = "Timer ${'A' + id}",
    val active: Boolean = id == 0,
    val sequence: Boolean = false,
    val durationMs: Long = 300_000L,
    val steps: List<Step> = listOf(Step("Step 1", 30_000L)),
    val color: Long = listOf(0xFFAA9CFF, 0xFF57DDB4, 0xFFFFBA77)[id],
    val alert: AlertStyle = AlertStyle.ORBIT,
    val haptic: HapticStyle = HapticStyle.DOUBLE_TAP,
    val morse: String = "TIME",
    val hapticRepeat: HapticRepeat = HapticRepeat.ONCE,
    val customRepeatCount: Int = 2,
    val repeatDurationMs: Long = 30_000,
    val glow: Float = 0.5f,
    val hidden: Boolean = false,
    val showBarName: Boolean = true,
    val rotateBarText: Boolean = false,
    val dock: DockSide = DockSide.NONE,
    val x: Float = 0.82f,
    val y: Float = 0.20f + id * 0.16f,
) {
    fun runSteps() = if (sequence) steps else listOf(Step(name, durationMs))
    fun error(): String? = when {
        name.isBlank() || name.codePointCount(0, name.length) > 24 -> "Use a name of 1–24 characters"
        durationMs !in MIN_MS..MAX_MS -> "Duration must be 00:01–99:59"
        steps.size !in 1..50 -> "Use 1–50 steps"
        steps.any { it.name.isBlank() || it.name.codePointCount(0, it.name.length) > 60 || it.durationMs !in MIN_MS..MAX_MS } -> "Check step names and durations"
        hapticRepeat == HapticRepeat.CUSTOM && customRepeatCount !in 1..99 -> "Use 1–99 vibration repeats"
        hapticRepeat == HapticRepeat.TIMED && repeatDurationMs !in 1_000..3_600_000 -> "Use a vibration duration of 1–3600 seconds"
        haptic == HapticStyle.MORSE -> Morse.validate(morse)
        else -> null
    }
}
@Serializable data class Session(
    val id: String,
    val steps: List<Step>,
    val status: Status = Status.RUNNING,
    val index: Int = 0,
    val deadlineMs: Long,
    val remainingMs: Long = steps.first().durationMs,
    val stepDurationMs: Long = steps.first().durationMs,
    val revision: Long = 0,
    val visualUntilMs: Long = 0,
    val alertStartedAtMs: Long = 0,
) {
    fun remaining(now: Long): Long = when (status) {
        Status.RUNNING -> (deadlineMs - now).coerceAtLeast(0)
        Status.COMPLETED -> 0
        else -> remainingMs
    }
    fun progress(now: Long) = (remaining(now).toFloat() / stepDurationMs).coerceIn(0f, 1f)
}
@Serializable data class Track(val definition: Definition, val session: Session? = null)
@Serializable data class AlertEvent(
    val id: String,
    val track: Int,
    val atMs: Long,
    val final: Boolean,
    val haptic: HapticStyle,
    val text: String,
    val repeat: HapticRepeat = HapticRepeat.ONCE,
    val repeatCount: Int = 1,
    val repeatDurationMs: Long = 30_000,
)
@Serializable data class Snapshot(
    val version: Int = 1,
    val boot: Int = -1,
    val tracks: List<Track> = (0..2).map { Track(Definition(it)) },
    val outbox: List<AlertEvent> = emptyList(),
)
data class AdjustmentTarget(val track: Int, val session: String?, val index: Int)
sealed interface Command {
    data class Start(val ids: Set<Int>) : Command
    data class Pause(val id: Int) : Command
    data class Reset(val id: Int) : Command
    data class Rewind(val id: Int) : Command
    data class Activate(val id: Int, val active: Boolean) : Command
    data class Edit(val definition: Definition) : Command
    data class BarAppearance(val id: Int, val showName: Boolean? = null, val rotateText: Boolean? = null) : Command
    data class Adjust(val target: AdjustmentTarget, val deltaMs: Long) : Command
    data class Hide(val id: Int, val hidden: Boolean) : Command
    data class Move(val id: Int, val x: Float, val y: Float, val dock: DockSide? = null) : Command
    data object ShowAll : Command
    data object StopAll : Command
    data object Tick : Command
}
data class Transition(val snapshot: Snapshot, val error: String? = null)

/** Pure monotonic engine. All commands reconcile boundaries before applying intent. */
class TimerEngine(private val newId: () -> String = { UUID.randomUUID().toString() }) {
    fun reconcile(source: Snapshot, now: Long): Snapshot {
        val events = mutableListOf<AlertEvent>()
        val tracks = source.tracks.map { track ->
            var s = track.session ?: return@map track
            if (s.status != Status.RUNNING) return@map track
            while (now >= s.deadlineMs) {
                val final = s.index == s.steps.lastIndex
                events += AlertEvent("${s.id}:${s.index}", track.definition.id, s.deadlineMs, final,
                    track.definition.haptic, track.definition.morse, track.definition.hapticRepeat,
                    track.definition.customRepeatCount, track.definition.repeatDurationMs)
                if (final) {
                    // A final alert remains animated until the user dismisses/resets it.
                    s = s.copy(status = Status.COMPLETED, remainingMs = 0, revision = s.revision + 1, visualUntilMs = Long.MAX_VALUE, alertStartedAtMs = s.deadlineMs)
                    break
                }
                val next = s.steps[s.index + 1].durationMs
                s = s.copy(index = s.index + 1, deadlineMs = s.deadlineMs + next, stepDurationMs = next,
                    remainingMs = next, revision = s.revision + 1, visualUntilMs = s.deadlineMs + 2_000, alertStartedAtMs = s.deadlineMs)
            }
            track.copy(session = s)
        }
        // Old intermediate events never produce a backlog of historical Morse.
        val outbox = (source.outbox + events).distinctBy { it.id }
            .filter { now - it.atMs <= if (it.final) 30_000 else 5_000 }
            .sortedWith(compareBy<AlertEvent> { it.atMs }.thenBy { it.track }).takeLast(12)
        return source.copy(tracks = tracks, outbox = outbox)
    }

    fun apply(source: Snapshot, command: Command, now: Long): Transition {
        var state = reconcile(source, now)
        var error: String? = null
        fun change(id: Int, action: (Track) -> Track) {
            state = state.copy(tracks = state.tracks.map { if (it.definition.id == id) action(it) else it })
        }
        fun clear(id: Int) { state = state.copy(outbox = state.outbox.filterNot { it.track == id }) }
        fun pause(t: Track): Track = t.session?.takeIf { it.status == Status.RUNNING }?.let {
            t.copy(session = it.copy(status = Status.PAUSED, remainingMs = it.remaining(now), revision = it.revision + 1, visualUntilMs = 0))
        } ?: t
        when (command) {
            is Command.Start -> command.ids.forEach { id -> change(id) { t ->
                val d = t.definition
                if (!d.active || t.session?.status == Status.RUNNING) t
                else if (d.error() != null) { error = "${d.name}: ${d.error()}"; t }
                else if (t.session?.status == Status.PAUSED) t.copy(session = t.session.copy(status = Status.RUNNING,
                    deadlineMs = now + t.session.remainingMs, revision = t.session.revision + 1))
                else if (t.session != null) t // Completed/interrupted require an explicit reset.
                else t.copy(session = Session(newId(), d.runSteps().map { it.copy() }, deadlineMs = now + d.runSteps().first().durationMs))
            } }
            is Command.Pause -> change(command.id, ::pause)
            is Command.Rewind -> {
                change(command.id) { t ->
                    val steps = t.session?.steps ?: t.definition.runSteps()
                    t.copy(session = Session(newId(), steps, status = Status.PAUSED,
                        deadlineMs = now + steps.first().durationMs, remainingMs = steps.first().durationMs,
                        stepDurationMs = steps.first().durationMs))
                }
                clear(command.id)
            }
            is Command.Reset -> { change(command.id) { it.copy(session = null) }; clear(command.id) }
            is Command.Activate -> change(command.id) {
                (if (command.active) it else pause(it)).let { t -> t.copy(definition = t.definition.copy(active = command.active)) }
            }
            is Command.Edit -> change(command.definition.id) { t ->
                val d = command.definition
                val structural = d.sequence != t.definition.sequence || d.durationMs != t.definition.durationMs || d.steps != t.definition.steps
                when {
                    d.error() != null -> { error = d.error(); t }
                    structural && t.session != null -> { error = "Reset this timer before editing its steps"; t }
                    d.active != t.definition.active -> { error = "Use the Active switch"; t }
                    else -> t.copy(definition = d)
                }
            }
            is Command.Adjust -> change(command.target.track) { t ->
                val s = t.session
                if (!t.definition.active || command.target.session != s?.id || (s != null && command.target.index != s.index)) t
                else if (s == null) {
                    val d = t.definition
                    if (!d.sequence) t.copy(definition = d.copy(durationMs = (d.durationMs + command.deltaMs).coerceIn(MIN_MS, MAX_MS)))
                    else t.copy(definition = d.copy(steps = d.steps.mapIndexed { i, step ->
                        if (i == command.target.index) step.copy(durationMs = (step.durationMs + command.deltaMs).coerceIn(MIN_MS, MAX_MS)) else step
                    }))
                } else if (s.status == Status.RUNNING || s.status == Status.PAUSED) {
                    val old = s.remaining(now)
                    val remaining = (old + command.deltaMs).coerceIn(MIN_MS, MAX_MS)
                    t.copy(session = s.copy(remainingMs = remaining, deadlineMs = now + remaining,
                        stepDurationMs = s.stepDurationMs - old + remaining, revision = s.revision + 1))
                } else t
            }
            is Command.Hide -> change(command.id) { it.copy(definition = it.definition.copy(hidden = command.hidden)) }
            is Command.BarAppearance -> change(command.id) { t -> t.copy(definition=t.definition.copy(
                showBarName=command.showName ?: t.definition.showBarName,
                rotateBarText=command.rotateText ?: t.definition.rotateBarText)) }
            is Command.Move -> change(command.id) { it.copy(definition = it.definition.copy(x = command.x.coerceIn(0f, 1f), y = command.y.coerceIn(0f, 1f), dock = command.dock ?: it.definition.dock)) }
            Command.ShowAll -> state = state.copy(tracks = state.tracks.map { it.copy(definition = it.definition.copy(hidden = false)) })
            Command.StopAll -> state = state.copy(tracks = state.tracks.map { it.copy(session = null) }, outbox = emptyList())
            Command.Tick -> Unit
        }
        return Transition(state, error)
    }

    fun recover(source: Snapshot, boot: Int, now: Long, userStopped: Boolean = false): Snapshot {
        if (source.boot == boot && !userStopped) return reconcile(source, now)
        return source.copy(boot = boot, outbox = emptyList(), tracks = source.tracks.map { t ->
            t.copy(session = t.session?.let { s ->
                if (s.status == Status.RUNNING) s.copy(status = Status.INTERRUPTED, visualUntilMs = 0) else s.copy(visualUntilMs = 0)
            })
        })
    }
}

fun formatTime(ms: Long): String {
    val seconds = ceil(ms.coerceAtLeast(0) / 1_000.0).toLong()
    return "%02d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
}
fun holdStep(heldMs: Long): Long = when { heldMs >= 8_000 -> 300_000; heldMs >= 3_000 -> 60_000; else -> 30_000 }
val pourOver = listOf(Step("Blooming", 30_000), Step("Slow pour over", 120_000))
val steak = listOf(Step("1st sear", 60_000), Step("Flip sear", 60_000), Step("2nd sear", 60_000), Step("Flip sear 2nd time", 60_000), Step("Sear fatty side", 30_000), Step("Rest", 300_000))
