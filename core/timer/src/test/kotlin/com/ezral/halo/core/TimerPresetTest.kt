package com.ezral.halo.core

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class TimerPresetTest {
    private var ids = 0
    private val engine = TimerEngine { "preset-${ids++}" }
    private fun apply(s: Snapshot, c: Command): Snapshot {
        val result = engine.apply(s, c, 100)
        assertNull(result.error)
        return result.snapshot
    }

    private fun sequenceState(): Snapshot = Snapshot().let { source ->
        source.copy(tracks = source.tracks.map { it.copy(definition = it.definition.copy(sequence = true)) })
    }

    @Test fun oldCheckpointsLoadWithAnEmptyLibrary() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val legacy = json.encodeToString(Snapshot.serializer(), Snapshot()).replace(",\"presets\":[]", "")
        val decoded = json.decodeFromString<Snapshot>(legacy)
        assertTrue(decoded.presets.isEmpty())
        assertEquals(Snapshot().tracks, decoded.tracks)
    }

    @Test fun sequenceConfigurationSurvivesStorageAndLoadsIntoAnotherSlot() {
        val configured = Definition(0, name = "Coffee", sequence = true, hoursEnabled = true, repetitions = 3,
            steps = listOf(Step("Bloom", 40_000), Step("Pour", 7_200_000)), color = 0xFF0088FF,
            customColor = 0xFF0088FF, linePalette = LinePalette.AURORA, haptic = HapticStyle.MORSE,
            morse = "C", soundEnabled = true, hapticRepeat = HapticRepeat.TIMED, repeatDurationMs = 12_000,
            alert = AlertStyle.DOUBLE_PONG, glow = .8f, showBarName = false, rotateBarText = true)
        var s = apply(Snapshot(), Command.Edit(configured))
        s = apply(s, Command.SavePreset(0, " Morning coffee "))
        val json = Json { encodeDefaults = true }
        s = json.decodeFromString<Snapshot>(json.encodeToString(Snapshot.serializer(), s))
        val target = s.tracks[2].definition.copy(sequence = true, x = .12f, y = .9f, dock = DockSide.LEFT, hidden = true)
        s = apply(s, Command.Edit(target))
        s = apply(s, Command.LoadPreset(s.presets.single().id, 2))
        assertEquals("Morning coffee", s.presets.single().name)
        assertEquals(target.copy(steps = configured.steps, repetitions = 3, hoursEnabled = true), s.tracks[2].definition)
        assertNull(s.tracks[2].session)
        assertEquals(configured, s.tracks[0].definition)
        assertEquals(Snapshot().tracks[1], s.tracks[1])
    }

    @Test fun savingAnAdjustedRunningTimerKeepsConfiguredDurationAndDoesNotPauseIt() {
        var s = apply(sequenceState(), Command.Start(setOf(0)))
        s = apply(s, Command.Adjust(AdjustmentTarget(0, s.tracks[0].session!!.id, 0), 30_000))
        val session = s.tracks[0].session
        s = apply(s, Command.SavePreset(0, "Focus"))
        assertEquals(30_000L, s.presets.single().definition.steps.first().durationMs)
        assertEquals(session, s.tracks[0].session)
        assertTrue(s.presets.single().definition.active)
    }

    @Test fun loadingRejectsAllExistingSessionsEvenWhenTheDefinitionMatches() {
        var s = apply(sequenceState(), Command.SavePreset(0, "Focus"))
        s = apply(s, Command.Start(setOf(0)))
        Status.entries.filter { it != Status.READY }.forEach { status ->
            val source = s.copy(tracks = s.tracks.map { if (it.definition.id == 0) it.copy(session = it.session!!.copy(status = status)) else it })
            val result = engine.apply(source, Command.LoadPreset(s.presets.single().id, 0), 100)
            assertEquals("Reset this timer before loading a preset", result.error)
            assertEquals(source.tracks, result.snapshot.tracks)
        }
    }

    @Test fun namesRejectBlankTooLongAndCaseInsensitiveDuplicates() {
        val s = apply(sequenceState(), Command.SavePreset(0, "Coffee"))
        listOf(" ", "a".repeat(41), " coffee ").forEach { name ->
            val result = engine.apply(s, Command.SavePreset(1, name), 100)
            assertNotNull(result.error)
            assertEquals(s.presets, result.snapshot.presets)
        }
        assertNull(presetNameError("☕".repeat(40), s.presets))
    }

    @Test fun updateAndRenameKeepIdentityAndDeleteDoesNotChangeLoadedTimers() {
        var s = apply(sequenceState(), Command.SavePreset(0, "Focus"))
        val id = s.presets.single().id
        s = apply(s, Command.Edit(s.tracks[0].definition.copy(steps = listOf(Step("Focus", 1_500_000)), repetitions = 0)))
        s = apply(s, Command.SavePreset(0, "Focus", id))
        assertEquals(1, s.presets.size)
        assertEquals(0, s.presets.single().definition.repetitions)
        s = apply(s, Command.RenamePreset(id, " Deep focus "))
        assertEquals("Deep focus", s.presets.single().name)
        s = apply(s, Command.LoadPreset(id, 1))
        val loaded = s.tracks
        s = apply(s, Command.DeletePreset(id))
        assertTrue(s.presets.isEmpty())
        assertEquals(loaded, s.tracks)
        assertEquals(1_500_000L, s.tracks[1].definition.steps.first().durationMs)
    }

    @Test fun savingAndLoadingAreCopiesAndStopAllRetainsPresets() {
        var s = apply(sequenceState(), Command.SavePreset(0, "Focus"))
        val saved = s.presets
        s = apply(s, Command.Edit(s.tracks[0].definition.copy(name = "Changed", steps = listOf(Step("Changed", 1_000)))))
        s = apply(s, Command.Start(setOf(0)))
        s = apply(s, Command.StopAll)
        assertEquals(saved, s.presets)
        assertEquals("Step 1", s.presets.single().definition.steps.first().name)
        assertEquals(30_000L, s.presets.single().definition.steps.first().durationMs)
    }
    @Test fun singleModeCannotSaveOrLoadSequencePresets() {
        val rejectedSave = engine.apply(Snapshot(), Command.SavePreset(0, "Single"), 100)
        assertNotNull(rejectedSave.error)
        assertTrue(rejectedSave.snapshot.presets.isEmpty())
        val saved = apply(sequenceState(), Command.SavePreset(0, "Focus"))
        val single = saved.copy(tracks = Snapshot().tracks)
        val rejectedLoad = engine.apply(single, Command.LoadPreset(saved.presets.single().id, 0), 100)
        assertNotNull(rejectedLoad.error)
        assertEquals(single, rejectedLoad.snapshot)
    }

    @Test fun legacyPresetsKeepDataAndOnlySequenceSettingsAreApplied() {
        val legacySingle = TimerPreset("old-single", "Coffee", Definition(0))
        val legacySequence = TimerPreset("old-sequence", "Routine", Definition(0, sequence = true,
            steps = listOf(Step("Long step", 7_200_000)), hoursEnabled = true, repetitions = 4,
            name = "Old name", color = 0xFFFF0000, soundEnabled = true, haptic = HapticStyle.MORSE, morse = "C"))
        val json = Json { encodeDefaults = true }
        val saved = sequenceState().copy(presets = listOf(legacySingle, legacySequence))
        var s = json.decodeFromString<Snapshot>(json.encodeToString(Snapshot.serializer(), saved))
        assertEquals(listOf(legacySequence), s.sequencePresets())
        assertNotNull(engine.apply(s, Command.LoadPreset(legacySingle.id, 0), 100).error)
        val original = s.tracks[0].definition.copy(durationMs = 10_800_000, hoursEnabled = true)
        s = apply(s, Command.Edit(original))
        s = apply(s, Command.LoadPreset(legacySequence.id, 0))
        assertEquals(original.copy(steps = legacySequence.definition.steps, repetitions = 4), s.tracks[0].definition)
        assertEquals(saved.presets, s.presets)
        // Hidden legacy single presets do not block names in the sequence library.
        s = apply(s, Command.SavePreset(0, "Coffee"))
        assertEquals(3, s.presets.size)
        assertEquals(2, s.sequencePresets().size)
    }

}
