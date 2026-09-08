package com.ezral.halo.core

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class TimerEngineTest {
    private var serial = 0
    private val engine = TimerEngine { "run-${serial++}" }
    private fun all(duration: Long = 60_000) = Snapshot(boot = 7, tracks = (0..2).map { Track(Definition(it, active = true, durationMs = duration)) })
    private fun run(s: Snapshot = all(), now: Long = 1_000) = engine.apply(s, Command.Start(setOf(0, 1, 2)), now).snapshot
    private fun sequence(steps: List<Step> = pourOver) = all().let { s -> s.copy(tracks = s.tracks.map { it.copy(definition = it.definition.copy(sequence = true, steps = steps)) }) }
    @Test fun launchAllUsesOneTimestamp() {
        val s = run(); assertEquals(listOf(61_000L, 61_000L, 61_000L), s.tracks.map { it.session!!.deadlineMs })
        assertEquals(3, s.tracks.map { it.session!!.id }.distinct().size)
    }
    @Test fun runningStartDoesNotRestart() { val s = run(); assertEquals(s, run(s, 2_000)) }
    @Test fun pauseOnlyA() {
        val s = run(); val p = engine.apply(s, Command.Pause(0), 11_000).snapshot
        assertEquals(50_000L, p.tracks[0].session!!.remaining(1_000_000))
        assertEquals(s.tracks.drop(1), p.tracks.drop(1))
    }
    @Test fun resumeRetainsRemainder() {
        val p = engine.apply(run(), Command.Pause(0), 11_000).snapshot
        val s = engine.apply(p, Command.Start(setOf(0)), 31_000).snapshot
        assertEquals(81_000L, s.tracks[0].session!!.deadlineMs)
    }
    @Test fun lateSequenceDoesNotAccumulateDrift() {
        val s = run(sequence(), 0)
        val later = engine.reconcile(s, 45_000)
        assertEquals(1, later.tracks[0].session!!.index)
        assertEquals(150_000L, later.tracks[0].session!!.deadlineMs)
        assertTrue(later.outbox.isEmpty())
    }
    @Test fun crossesManyStepsAndEmitsFinalOnce() {
        val s = engine.reconcile(run(sequence(steak), 0), 570_000)
        assertEquals(Status.COMPLETED, s.tracks[0].session!!.status)
        assertEquals(3, s.outbox.size)
        assertEquals(s, engine.reconcile(s, 570_000))
        assertTrue(engine.reconcile(s.copy(outbox = emptyList()), 570_001).outbox.isEmpty())
    }
    @Test fun boundaryPrecedesPause() {
        val s = engine.apply(run(sequence(), 0), Command.Pause(0), 30_000).snapshot
        assertEquals(1, s.tracks[0].session!!.index)
        assertEquals(120_000L, s.tracks[0].session!!.remainingMs)
    }
    @Test fun staleHoldCannotAdjustNextStep() {
        val s = run(sequence(), 0); val t = Target(0, s.tracks[0].session!!.id, 0)
        val next = engine.apply(s, Command.Adjust(t, 30_000), 30_000).snapshot
        assertEquals(150_000L, next.tracks[0].session!!.deadlineMs)
    }
    @Test fun resetInvalidatesStaleSession() {
        val old = run(); val target = Target(0, old.tracks[0].session!!.id, 0)
        val reset = engine.apply(old, Command.Reset(0), 2_000).snapshot
        val restarted = engine.apply(reset, Command.Start(setOf(0)), 2_000).snapshot
        assertEquals(restarted, engine.apply(restarted, Command.Adjust(target, 30_000), 2_000).snapshot)
    }
    @Test fun adjustPreservesFractionAndOtherTracks() {
        val s = run(); val target = Target(0, s.tracks[0].session!!.id, 0)
        val adjusted = engine.apply(s, Command.Adjust(target, 30_000), 1_501).snapshot
        assertEquals(89_499L, adjusted.tracks[0].session!!.remaining(1_501))
        assertEquals(90_000L, adjusted.tracks[0].session!!.stepDurationMs)
        assertEquals(s.tracks.drop(1), adjusted.tracks.drop(1))
    }
    @Test fun clampNeverSkipsStep() {
        val s = run(); val result = engine.apply(s, Command.Adjust(Target(0, s.tracks[0].session!!.id, 0), -90_000), 2_000).snapshot
        assertEquals(1_000L, result.tracks[0].session!!.remaining(2_000)); assertEquals(0, result.tracks[0].session!!.index)
    }
    @Test fun deactivatePausesAndActivationDoesNotResume() {
        val off = engine.apply(run(), Command.Activate(0, false), 2_000).snapshot
        val on = engine.apply(off, Command.Activate(0, true), 3_000).snapshot
        assertEquals(Status.PAUSED, on.tracks[0].session!!.status); assertEquals(59_000L, on.tracks[0].session!!.remainingMs)
    }
    @Test fun hideRenameAndMovePreserveDeadlines() {
        var s = run(); val sessions = s.tracks.map { it.session }
        s = engine.apply(s, Command.Hide(0, true), 2_000).snapshot
        s = engine.apply(s, Command.Move(0, 2f, -1f), 2_000).snapshot
        s = engine.apply(s, Command.Edit(s.tracks[0].definition.copy(name = "Coffee")), 2_000).snapshot
        assertEquals(sessions, s.tracks.map { it.session }); assertEquals(1f, s.tracks[0].definition.x)
    }
    @Test fun structuralEditBlockedDuringRun() {
        val s = run(); val result = engine.apply(s, Command.Edit(s.tracks[0].definition.copy(durationMs = 5_000)), 1_000)
        assertNotNull(result.error); assertEquals(s, result.snapshot)
    }
    @Test fun badMorseBlocksOnlyAffectedTrack() {
        val base = all().let { it.copy(tracks = it.tracks.map { t -> if (t.definition.id == 1) t.copy(definition = t.definition.copy(haptic = HapticStyle.MORSE, morse = "?")) else t }) }
        val s = run(base); assertNull(s.tracks[1].session); assertNotNull(s.tracks[0].session); assertNotNull(s.tracks[2].session)
    }
    @Test fun recoverySameBootAndNewBoot() {
        val s = run()
        assertEquals(40_000L, engine.recover(s, 7, 21_000).tracks[0].session!!.remaining(21_000))
        assertEquals(Status.INTERRUPTED, engine.recover(s, 8, 100).tracks[0].session!!.status)
        assertEquals(Status.INTERRUPTED, engine.recover(s, 7, 100, true).tracks[0].session!!.status)
    }
    @Test fun snapshotRoundTrip() {
        val s = engine.reconcile(run(sequence(), 0), 30_000)
        assertEquals(s, Json.decodeFromString<Snapshot>(Json.encodeToString(Snapshot.serializer(), s)))
    }
    @Test fun stopAllClearsSessionsAndPendingEvents() {
        val s = engine.apply(run(), Command.StopAll, 61_000).snapshot
        assertTrue(s.outbox.isEmpty()); assertTrue(s.tracks.all { it.session == null })
    }
    @Test fun secondsCarryBorrowAndClamps() {
        var s = all(59_000)
        s = engine.apply(s, Command.Adjust(Target(0, null, 0), 1_000), 0).snapshot
        assertEquals("01:00", formatTime(s.tracks[0].definition.durationMs))
        s = engine.apply(s, Command.Adjust(Target(0, null, 0), -1_000), 0).snapshot
        assertEquals("00:59", formatTime(s.tracks[0].definition.durationMs))
        s = engine.apply(s, Command.Adjust(Target(0, null, 0), Long.MIN_VALUE / 2), 0).snapshot
        assertEquals(MIN_MS, s.tracks[0].definition.durationMs)
        s = engine.apply(s, Command.Adjust(Target(0, null, 0), MAX_MS * 2), 0).snapshot
        assertEquals(MAX_MS, s.tracks[0].definition.durationMs)
    }
    @Test fun selectedSequenceRowAdjustment() {
        val s = engine.apply(sequence(), Command.Adjust(Target(0, null, 1), 30_000), 0).snapshot
        assertEquals(30_000L, s.tracks[0].definition.steps[0].durationMs)
        assertEquals(150_000L, s.tracks[0].definition.steps[1].durationMs)
    }
    @Test fun presetsAndHoldThresholds() {
        assertEquals(150_000L, pourOver.sumOf { it.durationMs }); assertEquals(570_000L, steak.sumOf { it.durationMs })
        assertEquals(30_000L, holdStep(2_999)); assertEquals(60_000L, holdStep(3_000)); assertEquals(60_000L, holdStep(7_999)); assertEquals(300_000L, holdStep(8_000))
    }
    @Test fun randomIsolatedCommandTraces() {
        val random = Random(47)
        var s = run(all(MAX_MS), 0)
        repeat(2_000) { n ->
            val now = n.toLong() * 10
            val id = random.nextInt(3)
            val before = engine.reconcile(s, now)
            val command = when (random.nextInt(4)) {
                0 -> Command.Pause(id)
                1 -> Command.Start(setOf(id))
                2 -> Command.Hide(id, random.nextBoolean())
                else -> Command.Adjust(Target(id, s.tracks[id].session?.id, s.tracks[id].session?.index ?: 0), if (random.nextBoolean()) 30_000 else -30_000)
            }
            s = engine.apply(s, command, now).snapshot
            (0..2).filter { it != id }.forEach { assertEquals(before.tracks[it], s.tracks[it]) }
            s.tracks.forEach { t -> t.session?.let { assertTrue(it.remaining(now) >= 0); assertTrue(it.index in it.steps.indices) } }
        }
    }
}
