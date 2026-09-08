package com.ezral.halo.core

import org.junit.Assert.*
import org.junit.Test

class MorseTest {
    @Test fun completeAlphabetFixture() {
        val expected = ".- -... -.-. -.. . ..-. --. .... .. .--- -.- .-.. -- -. --- .--. --.- .-. ... - ..- ...- .-- -..- -.-- --.. ----- .---- ..--- ...-- ....- ..... -.... --... ---.. ----."
        assertEquals(expected, Morse.alphabet.values.joinToString(" "))
    }
    @Test fun cNativeWaveformStartsWithOffInterval() {
        assertEquals("-.-.", Morse.display("c"))
        assertArrayEquals(longArrayOf(0, 300, 100, 100, 100, 300, 100, 100), Morse.nativeTimings(Morse.encode("C")))
    }
    @Test fun wordAndCharacterGapsAreTotalGaps() {
        assertEquals(listOf(Pulse(true, 100), Pulse(false, 700), Pulse(true, 300)), Morse.encode(" E   t "))
        assertEquals(listOf(Pulse(true, 100), Pulse(false, 300), Pulse(true, 300)), Morse.encode("ET"))
    }
    @Test fun validationRejectsInvalidEmptyAndOverlong() {
        listOf("", "   ", "HI!", "ß", "é", "HI\n", "Z".repeat(24), "E".repeat(25)).forEach { assertNotNull(it, Morse.validate(it)) }
        assertNull(Morse.validate("FLIP")); assertEquals("... --- ...", Morse.display("sos"))
    }
    @Test fun allSegmentsAlternate() {
        Morse.alphabet.keys.forEach { c ->
            val p = Morse.encode(c.toString()); assertTrue(p.first().on); assertTrue(p.last().on)
            p.zipWithNext().forEach { (a, b) -> assertNotEquals(a.on, b.on) }
        }
    }
}
