package com.ezral.halo.overlay

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.RectF
import android.graphics.Shader

/** One tint recipe for the bar, dock and their shared morphing surface. */
internal object HaloGlass {
    fun colors(line: Int): IntArray {
        // Use one RGB tint: clipping a gradient to half a circle otherwise changes its apparent hue.
        val r = (Color.red(line) * .62f + 255 * .38f).toInt()
        val g = (Color.green(line) * .62f + 255 * .38f).toInt()
        val b = (Color.blue(line) * .62f + 255 * .38f).toInt()
        return intArrayOf(Color.argb(238, r, g, b), Color.argb(220, r, g, b))
    }
    fun shader(bounds: RectF, line: Int) = LinearGradient(bounds.left, bounds.top,
        bounds.right, bounds.bottom, colors(line), null, Shader.TileMode.CLAMP)
}
