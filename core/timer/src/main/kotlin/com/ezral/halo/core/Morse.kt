package com.ezral.halo.core

import java.util.Locale

data class Pulse(val on: Boolean, val ms: Long)
object Morse {
    private val codes = listOf(".-", "-...", "-.-.", "-..", ".", "..-.", "--.", "....", "..", ".---", "-.-", ".-..", "--", "-.", "---", ".--.", "--.-", ".-.", "...", "-", "..-", "...-", ".--", "-..-", "-.--", "--..", "-----", ".----", "..---", "...--", "....-", ".....", "-....", "--...", "---..", "----.")
    val alphabet = (('A'..'Z') + ('0'..'9')).zip(codes).toMap()
    fun normalize(text: String) = text.uppercase(Locale.ROOT).trim().replace(Regex(" +"), " ")
    private fun inputError(text: String): String? {
        // Validate the original input too: Unicode uppercasing must not silently transliterate.
        if (text.any { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' && it != ' ' }) return "Use A–Z, 0–9 and spaces"
        val clean = normalize(text)
        return when { clean.isEmpty() -> "Enter Morse text"; clean.length > 24 -> "Use up to 24 characters"; else -> null }
    }
    fun validate(text: String): String? = inputError(text) ?: if (encode(text).sumOf { it.ms } > 20_000) "Morse pattern exceeds 20 seconds" else null
    fun display(text: String): String = if (inputError(text) != null) "" else normalize(text).split(' ').joinToString(" / ") { word -> word.map { alphabet.getValue(it) }.joinToString(" ") }
    fun encode(text: String): List<Pulse> {
        require(inputError(text) == null) { inputError(text) ?: "Invalid Morse" }
        val pulses = mutableListOf<Pulse>()
        normalize(text).split(' ').forEachIndexed { wi, word ->
            if (wi > 0) pulses += Pulse(false, 700)
            word.forEachIndexed { ci, c ->
                if (ci > 0) pulses += Pulse(false, 300)
                alphabet.getValue(c).forEachIndexed { ei, e ->
                    if (ei > 0) pulses += Pulse(false, 100)
                    pulses += Pulse(true, if (e == '.') 100 else 300)
                }
            }
        }
        return pulses
    }
    fun nativeTimings(pulses: List<Pulse>): LongArray {
        require(pulses.isNotEmpty() && pulses.first().on)
        return longArrayOf(0) + pulses.map { it.ms }.toLongArray()
    }
}
