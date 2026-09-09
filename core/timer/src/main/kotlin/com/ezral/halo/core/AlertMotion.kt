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
    /** Gentle inhale, longer exhale; smooth velocity and acceleration at every turn. */
    fun breathLevel(elapsedMs: Long): Float {
        val cycle = phase(elapsedMs, 4800).rem(1.0)
        val t = if (cycle < .4) cycle / .4 else (1 - cycle) / .6
        return (t * t * t * (t * (t * 6 - 15) + 10)).toFloat()
    }
    fun breathe(elapsedMs: Long) = (28 + 227 * breathLevel(elapsedMs)).toInt()
}
