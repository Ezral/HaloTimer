package com.ezral.halo

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ezral.halo.core.*
import com.ezral.halo.data.HaloStore
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HaloPresetTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val c get() = (rule.activity.application as HaloApplication).coordinator
    @Before fun prepare() {
        rule.waitUntil(10000) { c.ready.value }
        reset()
    }
    @After fun reset() { runBlocking { withContext(Dispatchers.Main) {
        c.execute(Command.StopAll)
        c.state.value.presets.forEach { c.execute(Command.DeletePreset(it.id)) }
        (0..2).forEach { c.execute(Command.Activate(it, it == 0)); c.execute(Command.Edit(Definition(it))) }
        c.preferences.fullScreenMode(false); c.preferences.completionEnabled(true)
        c.preferences.theme("System"); c.preferences.dismissAllOnMenu(false)
    } } }
    private fun execute(command: Command) = runBlocking { withContext(Dispatchers.Main) { c.execute(command) } }.also { assertTrue("Command failed: $command", it) }
    private fun browse(count: Int = 1) {
        rule.onNodeWithText("Browse presets ($count)").performScrollTo().performClick()
        rule.onNodeWithText("Saved presets").assertIsDisplayed()
    }
    private fun screenshot(name: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("mkdir -p /sdcard/Download/halo-qa", "screencap -p /sdcard/Download/halo-qa/$name.png").forEach {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(it)).use { stream -> stream.readBytes() }
        }
    }
    @Test fun saveRenameUpdateReloadAndDeleteSurviveRecreationAndStorage() {
        // Saving from a focused time field must include the uncommitted input.
        rule.onNode(hasContentDescription("Minutes") and hasSetTextAction()).performScrollTo().performClick().performTextInput("12")
        rule.onNodeWithContentDescription("Save current timer as preset").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Preset name input").performTextReplacement("Focus")
        rule.onNodeWithText("Save").performClick()
        rule.waitUntil(5000) { c.state.value.presets.size == 1 }
        assertEquals(720_000L, c.state.value.presets.single().definition.durationMs)
        val id = c.state.value.presets.single().id
        val stored = runBlocking { HaloStore(rule.activity).load()!! }
        assertEquals(c.state.value.presets, stored.presets)
        rule.activityRule.scenario.recreate()
        browse()
        screenshot("20-saved-preset-library")
        rule.onNodeWithContentDescription("Manage preset Focus").performClick()
        rule.onNodeWithContentDescription("Rename preset Focus").performClick()
        rule.onNodeWithContentDescription("Preset name input").performTextReplacement("Deep focus")
        rule.onNodeWithText("Save").performClick()
        rule.waitUntil(5000) { c.state.value.presets.single().name == "Deep focus" }
        rule.onNodeWithText("Done").performClick()
        execute(Command.Edit(c.state.value.tracks[0].definition.copy(durationMs = 1_500_000, repetitions = 3)))
        browse()
        rule.onNodeWithContentDescription("Manage preset Deep focus").performClick()
        rule.onNodeWithContentDescription("Update preset Deep focus").performClick()
        rule.onNodeWithContentDescription("Confirm Update preset").performClick()
        rule.waitUntil(5000) { c.state.value.presets.single().definition.durationMs == 1_500_000L }
        assertEquals(id, c.state.value.presets.single().id)
        rule.onNodeWithText("Done").performClick()
        execute(Command.Edit(c.state.value.tracks[0].definition.copy(durationMs = 30_000, repetitions = 1)))
        browse()
        rule.onNodeWithContentDescription("Load preset Deep focus").performClick()
        rule.onNodeWithContentDescription("Confirm Load preset").performClick()
        rule.waitUntil(5000) { c.state.value.tracks[0].definition.durationMs == 1_500_000L }
        assertNull(c.state.value.tracks[0].session)
        assertEquals(3, c.state.value.tracks[0].definition.repetitions)
        rule.onNodeWithContentDescription("Timer round count").performScrollTo().assert(hasText("3"))
        execute(Command.Start(setOf(0)))
        browse()
        rule.onNodeWithContentDescription("Load preset Deep focus").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Manage preset Deep focus").performClick()
        rule.onNodeWithContentDescription("Delete preset Deep focus").performClick()
        rule.onNodeWithContentDescription("Confirm Delete preset").performClick()
        rule.waitUntil(5000) { c.state.value.presets.isEmpty() }
        assertEquals(Status.RUNNING, c.state.value.tracks[0].session?.status)
        assertTrue(runBlocking { HaloStore(rule.activity).load()!!.presets.isEmpty() })
    }

    @Test fun timerTabsKeepTwoRowsAndPresetCardsLoadWholeTimers() {
        fun heights(): List<Float> {
            rule.onNodeWithTag("timer-tab-0").performScrollTo()
            return (0..2).map { rule.onNodeWithTag("timer-tab-$it").fetchSemanticsNode().boundsInRoot.height }
        }
        val original = heights()
        original.forEach { assertEquals(original.first(), it, 1f) }
        execute(Command.Edit(Definition(0, name = "A longer timer name here", durationMs = 1_500_000)))
        execute(Command.SavePreset(0, "Deep focus"))
        execute(Command.Edit(Definition(1, name = "Tea", durationMs = 180_000, color = 0xFF57DDB4)))
        execute(Command.SavePreset(1, "Afternoon tea"))
        heights().forEach { assertEquals(original.first(), it, 1f) }
        execute(Command.Start(setOf(0)))
        heights().forEach { assertEquals(original.first(), it, 1f) }
        rule.onNodeWithContentDescription("Use preset Afternoon tea").assertIsNotEnabled()
        execute(Command.Reset(0))
        heights().forEach { assertEquals(original.first(), it, 1f) }
        listOf("Light", "Dark").forEach { theme ->
            runBlocking { c.preferences.theme(theme) }
            rule.waitUntil(5000) { c.prefs.value.theme == theme }
            rule.onNodeWithTag("timer-tab-0").performScrollTo().assertIsDisplayed()
            rule.onNodeWithContentDescription("Use preset Afternoon tea").assertIsDisplayed()
            screenshot("22-preset-cards-${theme.lowercase()}")
        }
        rule.onNodeWithContentDescription("Use preset Afternoon tea").performClick()
        rule.onNodeWithContentDescription("Confirm Load preset").performClick()
        rule.waitUntil(5000) { c.state.value.tracks[0].definition.name == "Tea" }
        assertEquals(180_000L, c.state.value.tracks[0].definition.durationMs)
        assertEquals(0xFF57DDB4, c.state.value.tracks[0].definition.color)
        assertNull(c.state.value.tracks[0].session)
        heights().forEach { assertEquals(original.first(), it, 1f) }
    }

    @Test fun sequencePresetsAndPreviewHubRemainUsableInBothThemes() {
        execute(Command.Edit(Definition(0, name = "Coffee", sequence = true, repetitions = 0,
            steps = listOf(Step("Bloom", 30_000), Step("Pour", 90_000)), linePalette = LinePalette.AURORA,
            haptic = HapticStyle.MORSE, morse = "C", soundEnabled = true)))
        rule.onNodeWithContentDescription("Save current timer as preset").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Preset name input").performTextReplacement("Morning coffee")
        rule.onNodeWithText("Save").performClick()
        rule.waitUntil(5000) { c.state.value.presets.size == 1 }
        rule.onNodeWithContentDescription("Save current timer as preset").performClick()
        rule.onNodeWithContentDescription("Preset name input").performTextReplacement(" morning COFFEE ")
        rule.onNodeWithText("Save").assertIsNotEnabled()
        rule.onNodeWithText("Cancel").performClick()
        execute(Command.Edit(Definition(0)))
        browse()
        rule.onNodeWithContentDescription("Load preset Morning coffee").performClick()
        rule.onNodeWithContentDescription("Confirm Load preset").performClick()
        rule.waitUntil(5000) { c.state.value.tracks[0].definition.sequence }
        assertEquals(2, c.state.value.tracks[0].definition.steps.size)
        assertEquals(LinePalette.AURORA, c.state.value.tracks[0].definition.linePalette)
        assertEquals("C", c.state.value.tracks[0].definition.morse)
        rule.onNodeWithText("Show completion preview").performScrollTo().performClick()
        listOf("Light", "Dark").forEach { theme ->
            rule.onNodeWithText(theme).performScrollTo().performClick()
            rule.waitUntil(5000) { c.prefs.value.theme == theme }
            rule.onNodeWithText("Preview edge alert").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("Open full-screen timer").assertIsDisplayed()
            rule.onNodeWithText("Test vibration once").assertIsDisplayed()
            rule.onNodeWithText("Test sound once").assertIsDisplayed()
            screenshot("21-preview-hub-${theme.lowercase()}")
            rule.onNodeWithContentDescription("Completion text preview").performScrollTo().assertIsDisplayed()
        }
    }
}
