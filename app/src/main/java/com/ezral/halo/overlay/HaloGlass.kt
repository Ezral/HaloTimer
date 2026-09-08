package com.ezral.halo.overlay

import android.graphics.Color

/** Uniform translucent tint shared by every floating surface. No color or alpha gradient. */
internal object HaloGlass {
    fun color(line: Int): Int = Color.argb(230,
        (Color.red(line) * .62f + 255 * .38f).toInt(),
        (Color.green(line) * .62f + 255 * .38f).toInt(),
        (Color.blue(line) * .62f + 255 * .38f).toInt())
}
