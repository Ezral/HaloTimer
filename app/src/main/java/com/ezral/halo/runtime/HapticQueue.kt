package com.ezral.halo.runtime

import android.content.Context
import android.media.AudioAttributes
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import com.ezral.halo.core.*
import kotlinx.coroutines.*

/** Serial, fair cycles: an until-dismiss alert never monopolizes the device vibrator. */
@Suppress("DEPRECATION")
class HapticQueue(context: Context, private val scope: CoroutineScope) {
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val sound = PatternSound(context)
    private val repeats = HapticRepeats()
    private var worker: Job? = null
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
    fun enqueue(event: AlertEvent) {
        if ((!event.soundEnabled && (!event.vibrationEnabled || event.haptic == HapticStyle.OFF || !vibrator.hasVibrator())) || repeats.contains(event.id)) return
        if (event.track >= 0) cancel(-1)
        cancel(event.track)
        repeats.enqueue(event, SystemClock.elapsedRealtime())
        start()
    }
    private fun start() {
        if (worker?.isActive == true || repeats.isEmpty) return
        worker = scope.launch {
            while (!repeats.isEmpty) {
                val cycle = repeats.next(SystemClock.elapsedRealtime())
                if (cycle == null) { delay(50); continue }
                try {
                    val samples = if (cycle.event.soundEnabled) withContext(Dispatchers.Default) { AlertTone.pcm(cycle.timings) } else null
                    // A failed sound output must not suppress the independent vibration output.
                    if (samples != null) runCatching { sound.play(samples) }
                    if (cycle.event.vibrationEnabled && cycle.event.haptic != HapticStyle.OFF && vibrator.hasVibrator()) {
                        runCatching { vibrator.vibrate(VibrationEffect.createWaveform(cycle.timings, -1), attributes) }
                    }
                    delay(cycle.timings.sum())
                    sound.stop()
                    repeats.finish(cycle, SystemClock.elapsedRealtime())
                } catch (e: CancellationException) { throw e }
                catch (_: RuntimeException) { repeats.cancel(cycle.event.track) }
                finally { sound.stop() }
            }
        }
    }
    fun preview(d: Definition) {
        if (repeats.hasRealAlert) return
        cancel(-1)
        // Preview is one complete pattern; real timer alerts use the configured repeat policy.
        enqueue(AlertEvent("preview:${SystemClock.elapsedRealtime()}", -1, SystemClock.elapsedRealtime(), false, d.alertPattern(), d.morse, vibrationEnabled = d.vibrates(), soundEnabled = d.soundEnabled))
    }
    fun cancel(id: Int) {
        if (repeats.cancel(id)) {
            worker?.cancel(); worker = null; vibrator.cancel(); sound.stop(); start()
        }
    }
    fun cancelAll() { repeats.clear(); worker?.cancel(); worker = null; vibrator.cancel(); sound.stop() }
}
