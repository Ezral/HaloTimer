package com.ezral.halo.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class AlertsTest {
    private fun event(id: Int, repeat: HapticRepeat = HapticRepeat.ONCE, morse: Boolean = false) =
        AlertEvent("event-$id", id, 0, true, if (morse) HapticStyle.MORSE else HapticStyle.DOUBLE_TAP, "SOS", repeat)
    @Test fun countedRepeatsPreserveEveryMorseCycleAndGap() {
        for ((mode, count) in listOf(HapticRepeat.ONCE to 1, HapticRepeat.THREE to 3, HapticRepeat.FIVE to 5, HapticRepeat.CUSTOM to 7)) {
            val q = HapticRepeats(); q.enqueue(event(0, mode, true).copy(repeatCount = 7), 0)
            var now = 0L
            repeat(count) { index ->
                val cycle = q.next(now)!!
                assertArrayEquals(Morse.nativeTimings(Morse.encode("SOS")), cycle.timings)
                now += cycle.timings.sum(); q.finish(cycle, now)
                if (index < count - 1) assertNull(q.next(now + 699))
                now += 700
            }
            assertTrue(q.isEmpty)
        }
    }
    @Test fun untilDismissYieldsToOtherTracksAndCancellationIsIndependent() {
        val q = HapticRepeats(); q.enqueue(event(0, HapticRepeat.UNTIL_DISMISS), 0); q.enqueue(event(1, HapticRepeat.THREE), 0)
        val a = q.next(0)!!; assertEquals(0, a.event.track); q.finish(a, 290)
        val b = q.next(290)!!; assertEquals(1, b.event.track); q.finish(b, 580)
        q.cancel(0)
        assertNull(q.next(980)); val nextB = q.next(1280)!!; assertEquals(1, nextB.event.track)
        q.cancel(1); q.finish(nextB, 1600); assertTrue(q.isEmpty)
    }
    @Test fun timedRepeatStopsAtBudgetEvenWithinMorsePattern() {
        val q = HapticRepeats(); q.enqueue(event(0, HapticRepeat.TIMED, true).copy(repeatDurationMs = 1000), 10)
        val cycle = q.next(10)!!; assertEquals(1000L, cycle.timings.sum()); q.finish(cycle, 1010)
        assertTrue(q.isEmpty)
    }
    @Test fun duplicateEventsAndIntermediateForeverDoNotCreateEndlessQueue() {
        val q = HapticRepeats(); val e = event(0, HapticRepeat.UNTIL_DISMISS).copy(final = false)
        q.enqueue(e, 0); q.enqueue(e, 0); val cycle = q.next(0)!!; q.finish(cycle, 290)
        assertTrue(q.isEmpty)
    }
    @Test fun orbitWrapAndPongReversalAreContinuous() {
        val before = AlertMotion.orbit(2799); val after = AlertMotion.orbit(2801)
        assertTrue((1 - before) + after < .001f)
        assertEquals(AlertMotion.orbit(0), AlertMotion.orbit(2800), .00001f)
        for (double in listOf(false, true)) {
            val turn = if (double) 2800L else 3600L
            assertTrue(abs(AlertMotion.pong(turn - 1, double) - AlertMotion.pong(turn + 1, double)) < .00001f)
            assertEquals(1f, AlertMotion.pong(turn, double), .00001f)
        }
    }
    @Test fun oneStepSequenceStillUsesItsCurrentStepLabel() {
        val t = Track(Definition(0, name = "Coffee", sequence = true), Session("run", listOf(Step("Bloom")), deadlineMs = 30_000))
        assertEquals("Bloom · Coffee", t.overlayLabel())
        assertEquals("Coffee", t.copy(definition = t.definition.copy(sequence = false)).overlayLabel())
    }
    @Test fun finalAlertPersistsAndResetDismissesIt() {
        val e = TimerEngine { "run" }; val started = e.apply(Snapshot(), Command.Start(setOf(0)), 0).snapshot
        val done = e.reconcile(started, 300_000)
        assertEquals(300_000L, done.tracks[0].session!!.alertStartedAtMs)
        assertTrue(e.reconcile(done, 3_000_000).tracks[0].session!!.visualUntilMs > 3_000_000)
        assertNull(e.apply(done, Command.Reset(0), 3_000_000).snapshot.tracks[0].session)
    }
}
