package com.ezral.halo.core

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class UpgradeTest {
    @Test fun hoursToggleUsesCommittedElevenMinuteDuration() {
        val engine = TimerEngine()
        val original = Definition(0, hoursEnabled = true, durationMs = 7_860_000)
        var state = Snapshot(tracks = listOf(Track(original)))
        state = engine.apply(state, Command.Edit(original.copy(durationMs = 660_000)), 0).snapshot
        val result = engine.apply(state, Command.SetHours(0, false), 0)
        assertNull(result.error)
        assertFalse(result.snapshot.tracks[0].definition.hoursEnabled)
        assertEquals(660_000L, result.snapshot.tracks[0].definition.durationMs)
    }
    @Test fun unusedModeDoesNotLockHoursButKeepsItsLongDuration() {
        val engine = TimerEngine()
        for (sequence in listOf(false, true)) {
            val definition = Definition(0, hoursEnabled = true, sequence = sequence,
                durationMs = if (sequence) 7_200_000 else 660_000,
                steps = listOf(Step("Saved step", if (sequence) 660_000 else 7_200_000)))
            val result = engine.apply(Snapshot(tracks = listOf(Track(definition))), Command.SetHours(0, false), 0)
            assertNull(result.error)
            val updated = result.snapshot.tracks[0].definition
            assertEquals(definition.copy(hoursEnabled = false), updated)
            // The long mode still requires Hours when it becomes the active editor.
            assertNotNull(engine.apply(result.snapshot, Command.Edit(updated.copy(sequence = !sequence)), 0).error)
            assertNull(updated.error())
            assertNotNull(updated.copy(durationMs = MAX_HOURS_MS + 1000).error())
            assertNotNull(updated.copy(steps = listOf(Step(durationMs = MAX_HOURS_MS + 1000))).error())
        }
    }
    @Test fun hoursToggleStillProtectsLongActiveTimersAndSteps() {
        val engine = TimerEngine()
        for (definition in listOf(
            Definition(0, hoursEnabled = true, durationMs = 7_200_000),
            Definition(0, hoursEnabled = true, sequence = true, steps = listOf(Step(durationMs = 7_200_000)))
        )) {
            val state = Snapshot(tracks = listOf(Track(definition)))
            val result = engine.apply(state, Command.SetHours(0, false), 0)
            assertNotNull(result.error)
            assertEquals(state, result.snapshot)
        }
    }
    @Test fun oldSavedDefinitionsKeepHoursAndSoundOff() {
        val d = Json.decodeFromString<Definition>("""{"id":0,"haptic":"OFF"}""")
        assertFalse(d.hoursEnabled); assertFalse(d.soundEnabled); assertFalse(d.vibrates())
        assertEquals(HapticStyle.DOUBLE_TAP, d.alertPattern())
        val enabled = d.copy(hoursEnabled = true, soundEnabled = true, vibrationEnabled = false, durationMs = 7_200_000)
        assertEquals(enabled, Json.decodeFromString<Definition>(Json.encodeToString(Definition.serializer(), enabled)))
    }
    @Test fun countdownFormatsCarryBeforeSplittingAndIncludeZeroHours() {
        assertEquals("01:00:00", formatTime(3_599_001, true))
        assertEquals("00:59:59", formatTime(3_599_000, true))
        assertEquals("00:00:00", formatTime(0, true))
        assertEquals("99:59:59", formatTime(MAX_HOURS_MS, true))
        assertEquals("99:59", formatTime(MAX_MS))
        assertEquals(3_600_000, replaceTimeField(3_599_000, 1000, 60, true))
        assertEquals(3_599_000, replaceTimeField(3_600_000, 1000, -1, true))
        assertEquals(7_200_000, replaceTimeField(3_600_000, 60_000, 60, true))
    }
    @Test fun longSequencesSurvivePauseResumeAndAdvanceOnTheirOriginalDeadline() {
        val engine = TimerEngine { "long" }
        val d = Definition(0, hoursEnabled = true, sequence = true, steps = listOf(Step("First", 7_200_000), Step("Second", 3_600_000)))
        var state = Snapshot(tracks = listOf(Track(d)))
        state = engine.apply(state, Command.Start(setOf(0)), 0).snapshot
        state = engine.apply(state, Command.Pause(0), 3_600_000).snapshot
        state = engine.apply(state, Command.Start(setOf(0)), 4_000_000).snapshot
        assertEquals(7_600_000, state.tracks[0].session!!.deadlineMs)
        state = engine.reconcile(state, 7_600_000)
        assertEquals(1, state.tracks[0].session!!.index)
        assertEquals(11_200_000, state.tracks[0].session!!.deadlineMs)
    }
    @Test fun disablingHoursNeverTruncatesConfiguredOrRunningTime() {
        val engine = TimerEngine { "long" }
        val d = Definition(0, hoursEnabled = true, durationMs = 7_200_000)
        val state = Snapshot(tracks = listOf(Track(d)))
        val result = engine.apply(state, Command.Edit(d.copy(hoursEnabled = false)), 0)
        assertNotNull(result.error); assertEquals(d, result.snapshot.tracks[0].definition)
        var running = engine.apply(Snapshot(tracks = listOf(Track(d.copy(durationMs = 60_000)))), Command.Start(setOf(0)), 0).snapshot
        running = engine.apply(running, Command.Adjust(AdjustmentTarget(0, "long", 0), 7_200_000), 0).snapshot
        val result2 = engine.apply(running, Command.Edit(running.tracks[0].definition.copy(hoursEnabled = false)), 0)
        assertNotNull(result2.error); assertTrue(result2.snapshot.tracks[0].definition.hoursEnabled)
    }
    @Test fun soundOnlyMorseSharesRepeatCountAndStopBudget() {
        val q = HapticRepeats()
        q.enqueue(AlertEvent("sound", 0, 0, true, HapticStyle.MORSE, "C", HapticRepeat.THREE,
            vibrationEnabled = false, soundEnabled = true), 0)
        var now = 0L
        repeat(3) {
            val c = q.next(now)!!
            assertFalse(c.event.vibrationEnabled); assertTrue(c.event.soundEnabled)
            assertArrayEquals(Morse.nativeTimings(Morse.encode("C")), c.timings)
            now += c.timings.sum(); q.finish(c, now); now += 700
        }
        assertTrue(q.isEmpty)
        q.enqueue(AlertEvent("silent", 1, 0, true, HapticStyle.MORSE, "C", vibrationEnabled = false), now)
        assertTrue(q.isEmpty)
    }
    @Test fun generatedToneContainsExactMorseSilencesAndSoftEndpoints() {
        val wave = Morse.nativeTimings(Morse.encode("C"))
        val pcm = AlertTone.pcm(wave)
        assertEquals(wave.sum() * 24, pcm.size.toLong())
        var offset = 0
        wave.forEachIndexed { i, ms ->
            val n = (ms * 24).toInt()
            val segment = pcm.sliceArray(offset until offset + n)
            if (i % 2 == 0) assertTrue(segment.all { it == 0.toShort() })
            else { assertEquals(0.toShort(), segment.first()); assertEquals(0.toShort(), segment.last()); assertTrue(segment.any { it != 0.toShort() }) }
            offset += n
        }
    }
    @Test fun completionReversesContinuouslyOnTimeoutAndEarlyTap() {
        val m = CompletionMotion(1000, 420)
        assertEquals(0f, m.progress(0), 0f); assertEquals(1f, m.progress(420), 0f)
        assertEquals(1f, m.progress(1420), 0f); assertEquals(.5f, m.progress(1630), .0001f)
        assertFalse(m.finished(1839)); assertTrue(m.finished(1840)); assertEquals(0f, m.progress(1840), 0f)
        val early = CompletionMotion(1000, 420)
        val before = early.progress(210); early.close(210)
        assertEquals(before, early.progress(210), 0f)
        assertEquals(before / 2, early.progress(420), .0001f)
        early.close(430); assertTrue(early.finished(630))
    }
}
