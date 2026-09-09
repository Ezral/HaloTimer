package com.ezral.halo.core

/** 60 degrees per second in either mode, independent of refresh rate and label length. */
object LabelOrbit {
    private val speed=Math.PI/3 / 1000
    fun fullRadians(elapsedMs: Long): Double = (elapsedMs.coerceAtLeast(0)*speed) % (Math.PI*2)
    fun dockedRadians(elapsedMs: Long, start: Double, visibleArc: Double, labelArc: Double): Double {
        // Begin with the trailing end at the entrance; reset only after every glyph exits.
        // The hidden part of the full circumference consumes no animation time.
        val travel=visibleArc+labelArc
        return start-labelArc+(elapsedMs.coerceAtLeast(0)*speed)%travel
    }
}
