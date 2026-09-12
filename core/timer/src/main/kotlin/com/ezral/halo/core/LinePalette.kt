package com.ezral.halo.core

import kotlinx.serialization.Serializable

@Serializable enum class LinePalette(val label: String, val colors: List<Long>) {
    SOLID("Solid", emptyList()),
    AURORA("Aurora", listOf(0xFF39EBC7, 0xFF249CFF, 0xFFAA55FF)),
    SUNSET("Sunset", listOf(0xFFFF7452, 0xFFFF3FA4, 0xFFFFCF50)),
    ELECTRIC("Electric", listOf(0xFF00D9FF, 0xFF5260FF, 0xFFFF36CC));
}

fun Session.roundLabel() = "Round $round / ${if (repetitions == 0) "∞" else repetitions}"

/** First keyboard input replaces a field even if the IME moved its selection. */
fun replacementDigits(old: String, edited: String): String {
    if (edited.length <= 2 && edited.length <= old.length) return edited
    var prefix = 0
    while (prefix < minOf(old.length, edited.length) && old[prefix] == edited[prefix]) prefix++
    var suffix = 0
    while (suffix < minOf(old.length - prefix, edited.length - prefix) && old[old.lastIndex - suffix] == edited[edited.lastIndex - suffix]) suffix++
    return edited.substring(prefix, edited.length - suffix)
}
