package com.ezral.halo.runtime

import android.content.Context
import android.media.AudioAttributes
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import com.ezral.halo.core.*
import kotlinx.coroutines.*

/** One vibrator, a bounded queue, and per-track cancellation. Main-thread confined. */
@Suppress("DEPRECATION")
class HapticQueue(context: Context, private val scope: CoroutineScope) {
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val queue = ArrayDeque<Pair<AlertEvent, LongArray>>()
    private var worker: Job? = null
    private var playing: Pair<AlertEvent, LongArray>? = null
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
    fun enqueue(event: AlertEvent) {
        if (!vibrator.hasVibrator() || event.haptic == HapticStyle.OFF) return
        val wave = when (event.haptic) {
            HapticStyle.OFF -> return
            HapticStyle.DOUBLE_TAP -> longArrayOf(0, 90, 110, 90)
            HapticStyle.MORSE -> {
                if (Morse.validate(event.text) != null) return
                Morse.nativeTimings(Morse.encode(event.text))
            }
        }
        if (event.track >= 0) cancel(-1) // Real alerts preempt previews.
        if (queue.any { it.first.id == event.id } || playing?.first?.id == event.id) return
        if (event.final) queue.removeAll { !it.first.final && SystemClock.elapsedRealtime() - it.first.atMs > 5_000 }
        val total = queue.sumOf { it.second.sum() } + (playing?.second?.sum() ?: 0L)
        if (total + wave.sum() <= 30_000) queue.addLast(event to wave)
        else if (total + 150 <= 30_000 && queue.none { it.first.id == "overflow" }) {
            queue.addLast(event.copy(id = "overflow", haptic = HapticStyle.DOUBLE_TAP) to longArrayOf(0, 150))
        }
        start()
    }
    private fun start() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (queue.isNotEmpty()) {
                val next = queue.removeFirst()
                if (!next.first.final && SystemClock.elapsedRealtime() - next.first.atMs > 5_000) continue
                playing = next
                vibrator.vibrate(VibrationEffect.createWaveform(next.second, -1), attributes)
                delay(next.second.sum() + 80)
                playing = null
            }
        }
    }
    fun preview(d: Definition) {
        if (playing?.first?.track?.let { it >= 0 } == true || queue.any { it.first.track >= 0 }) return
        cancel(-1)
        enqueue(AlertEvent("preview", -1, SystemClock.elapsedRealtime(), false, d.haptic, d.morse))
    }
    fun cancel(id: Int) {
        queue.removeAll { it.first.track == id }
        if (playing?.first?.track == id) {
            worker?.cancel(); worker = null; vibrator.cancel(); playing = null; start()
        }
    }
    fun cancelAll() { queue.clear(); worker?.cancel(); worker = null; playing = null; vibrator.cancel() }
}
