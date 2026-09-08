package com.ezral.halo.core

import kotlin.math.PI
import kotlin.math.cos

/** Phase is continuous in time; the renderer wraps only the path coordinate. */
object AlertMotion {
    fun phase(elapsedMs: Long, periodMs: Long) = elapsedMs.coerceAtLeast(0).toDouble() / periodMs
    fun orbit(elapsedMs: Long) = phase(elapsedMs, 2800).rem(1.0).toFloat()
    fun pong(elapsedMs: Long, double: Boolean): Float {
        val phase = phase(elapsedMs, if (double) 5600 else 7200)
        return ((1 - cos(phase * 2 * PI)) / 2).toFloat()
    }
    fun breathe(elapsedMs: Long) = (100 + 155 * (1 + cos(phase(elapsedMs, 3200) * 2 * PI)) / 2).toInt()
}
