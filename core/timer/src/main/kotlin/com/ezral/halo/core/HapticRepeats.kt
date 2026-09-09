package com.ezral.halo.core

/** One cycle at a time, with a quiet gap. Eligible tracks take turns on one vibrator. */
class HapticRepeats {
    data class Cycle(val event: AlertEvent, val timings: LongArray, val remaining: Int, val stopAt: Long)
    private data class Pending(val cycle: Cycle, val readyAt: Long)
    private val pending = ArrayDeque<Pending>()
    private var playing: Cycle? = null
    private val seen = ArrayDeque<String>()
    fun contains(id: String) = id in seen
    val isEmpty get() = pending.isEmpty() && playing == null
    val hasRealAlert get() = playing?.event?.track?.let { it >= 0 } == true || pending.any { it.cycle.event.track >= 0 }
    fun enqueue(event: AlertEvent, now: Long) {
        if ((!event.vibrationEnabled && !event.soundEnabled) || (event.haptic == HapticStyle.OFF && !event.soundEnabled) || event.id in seen) return
        if (event.haptic == HapticStyle.MORSE && Morse.validate(event.text) != null) return
        val wave = if (event.haptic == HapticStyle.MORSE) Morse.nativeTimings(Morse.encode(event.text)) else longArrayOf(0, 90, 110, 90)
        val repeat = if (!event.final && event.repeat == HapticRepeat.UNTIL_DISMISS) HapticRepeat.ONCE else event.repeat
        val count = when (repeat) {
            HapticRepeat.ONCE -> 1; HapticRepeat.THREE -> 3; HapticRepeat.FIVE -> 5
            HapticRepeat.CUSTOM -> event.repeatCount.coerceIn(1, 99)
            HapticRepeat.TIMED, HapticRepeat.UNTIL_DISMISS -> Int.MAX_VALUE
        }
        val stopAt = if (repeat == HapticRepeat.TIMED) now + event.repeatDurationMs.coerceIn(1_000, 3_600_000) else Long.MAX_VALUE
        pending.removeAll { it.cycle.event.track == event.track }
        pending.addLast(Pending(Cycle(event, wave, count, stopAt), now))
        seen.addLast(event.id); if (seen.size > 32) seen.removeFirst()
    }
    fun next(now: Long): Cycle? {
        if (playing != null) return null
        pending.removeAll { it.cycle.stopAt <= now }
        val next = pending.firstOrNull { it.readyAt <= now } ?: return null
        pending.remove(next)
        val cycle = next.cycle.copy(timings = trim(next.cycle.timings, next.cycle.stopAt - now))
        playing = cycle
        return cycle
    }
    fun finish(cycle: Cycle, now: Long) {
        if (playing !== cycle) return
        playing = null
        if (cycle.remaining > 1 && now + 700 < cycle.stopAt) {
            pending.addLast(Pending(cycle.copy(remaining = cycle.remaining - 1), now + 700))
        }
    }
    /** True means the caller must cancel the currently active hardware vibration. */
    fun cancel(track: Int): Boolean {
        pending.removeAll { it.cycle.event.track == track }
        if (playing?.event?.track != track) return false
        playing = null; return true
    }
    fun clear() { pending.clear(); playing = null }
    private fun trim(wave: LongArray, budget: Long): LongArray {
        var left = budget
        return wave.asIterable().mapNotNull { ms ->
            if (left <= 0) null else ms.coerceAtMost(left).also { left -= it }
        }.toLongArray()
    }
}

fun Track.overlayLabel(): String {
    val step = session?.let { it.steps.getOrNull(it.index)?.name }
    return if (definition.sequence && !step.isNullOrBlank()) "$step · ${definition.name}" else definition.name
}
