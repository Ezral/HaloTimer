package com.ezral.halo.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Rounded 660 Hz sine pulses: sample-accurate dots/dashes with 8 ms click-free ramps. */
object AlertTone {
    const val SAMPLE_RATE = 24_000
    fun pcm(timings: LongArray): ShortArray {
        require(timings.all { it >= 0 } && timings.sum() <= 20_000)
        val samples = ShortArray((timings.sum() * SAMPLE_RATE / 1000).toInt())
        var offset = 0
        timings.forEachIndexed { index, ms ->
            val length = (ms * SAMPLE_RATE / 1000).toInt()
            if (index % 2 == 1) {
                val ramp = min(SAMPLE_RATE * 8 / 1000, length / 2).coerceAtLeast(1)
                for (i in 0 until length) {
                    val edge = min(i, length - 1 - i).coerceAtMost(ramp).toDouble() / ramp
                    val envelope = (1 - cos(PI * edge)) / 2
                    samples[offset + i] = (sin(2 * PI * 660 * i / SAMPLE_RATE) * envelope * 0.22 * Short.MAX_VALUE).toInt().toShort()
                }
            }
            offset += length
        }
        return samples
    }
}
