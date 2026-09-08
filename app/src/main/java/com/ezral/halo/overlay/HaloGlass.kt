package com.ezral.halo.overlay

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.RectF
import android.graphics.Shader

/** One tint recipe for the bar, dock and their shared morphing surface. */
internal object HaloGlass {
    fun colors(line: Int): IntArray = intArrayOf(
        Color.argb(238, Color.red(line), Color.green(line), Color.blue(line)),
        Color.argb(220, (Color.red(line) * .38f + 255 * .62f).toInt(),
            (Color.green(line) * .38f + 255 * .62f).toInt(),
            (Color.blue(line) * .38f + 255 * .62f).toInt()),
    )
    fun shader(bounds: RectF, line: Int) = LinearGradient(bounds.left, bounds.top,
        bounds.right, bounds.bottom, colors(line), null, Shader.TileMode.CLAMP)
}
