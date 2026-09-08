package com.ezral.halo.overlay

import android.graphics.Color

/** All overlay bodies are opaque timer colors; only pixels outside the silhouette are transparent. */
internal object HaloGlass {
    fun color(line: Int): Int = line or 0xFF000000.toInt()
    fun foreground(line: Int): Int {
        fun linear(c: Int): Double { val s=c/255.0; return if(s<=.04045) s/12.92 else Math.pow((s+.055)/1.055,2.4) }
        val luminance=.2126*linear(Color.red(line))+.7152*linear(Color.green(line))+.0722*linear(Color.blue(line))
        return if(luminance>.3) 0xFF303644.toInt() else Color.WHITE
    }
}
