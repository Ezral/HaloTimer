package com.ezral.halo

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ezral.halo.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HaloUpgradeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val c get() = (rule.activity.application as HaloApplication).coordinator
    @Before fun prepare() {
        rule.waitUntil(10_000) { c.ready.value }
        runBlocking { withContext(Dispatchers.Main) {
            c.execute(Command.StopAll)
            c.execute(Command.Edit(Definition(0)))
            c.preferences.completionEnabled(true); c.preferences.completionTextSp(18)
            c.preferences.dockTextMotion(true)
        } }
    }
    @After fun cleanUp() { runBlocking { withContext(Dispatchers.Main) {
        c.execute(Command.StopAll); c.execute(Command.Edit(Definition(0)))
        c.haptics.cancelAll(); c.preferences.completionEnabled(false); c.preferences.completionTextSp(28)
        c.preferences.dockTextMotion(true)
    } } }
    @Test fun hoursNamePaletteAndIndependentSoundSettings() {
        rule.onNodeWithContentDescription("Edit timer name").performClick()
        rule.onNodeWithContentDescription("Timer name input").performTextReplacement("Recovery")
        rule.onNodeWithText("Save").performClick()
        rule.waitUntil(5000) { c.state.value.tracks[0].definition.name == "Recovery" }
        rule.onNodeWithContentDescription("Hours", useUnmergedTree = true).performClick()
        rule.waitUntil(5000) { c.state.value.tracks[0].definition.hoursEnabled }
        rule.onNodeWithContentDescription("Increase hours").performClick()
        rule.waitUntil(5000) { c.state.value.tracks[0].definition.durationMs == 3_900_000L }
        rule.onNodeWithContentDescription("Decrease seconds").performClick()
        rule.waitUntil(5000) { c.state.value.tracks[0].definition.durationMs == 3_899_000L }
        rule.onNodeWithText("Custom line color").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Color hue").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(180f) }
        rule.onNodeWithContentDescription("Color saturation").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1f) }
        rule.onNodeWithContentDescription("Color brightness").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1f) }
        rule.onNodeWithText("Save").performClick()
        rule.waitUntil(5000) { c.state.value.tracks[0].definition.color == 0xFF00FFFFL }
        rule.onNodeWithContentDescription("Sound", useUnmergedTree = true).performScrollTo().performClick()
        rule.waitUntil(5000) { c.state.value.tracks[0].definition.soundEnabled }
        rule.onNodeWithContentDescription("Vibration", useUnmergedTree = true).performScrollTo().performClick()
        rule.waitUntil(5000) { !c.state.value.tracks[0].definition.vibrates() }
        assertTrue(c.state.value.tracks[0].definition.soundEnabled)
        rule.onNodeWithContentDescription("Text motion on timer dock", useUnmergedTree = true).performScrollTo().performClick()
        rule.waitUntil(5000) { !c.prefs.value.dockTextMotion }
        assertFalse("Dock text toggle must not reduce other animations", c.prefs.value.reducedMotion)
    }
    @Test fun completionPreviewGrowsWithLargeLongText() {
        runBlocking { withContext(Dispatchers.Main) {
            c.execute(Command.Edit(Definition(0, name="Long recovery timer", sequence=true,
                steps=listOf(Step("Rest and recover before the next training round", 60000)))))
        } }
        rule.onNodeWithContentDescription("Completion text preview").performScrollTo()
        val small = rule.onNodeWithContentDescription("Completion text preview").fetchSemanticsNode().size.height
        runBlocking { c.preferences.completionTextSp(48) }
        rule.waitUntil(5000) { c.prefs.value.completionTextSp == 48 }
        rule.waitForIdle()
        val large = rule.onNodeWithContentDescription("Completion text preview").fetchSemanticsNode().size.height
        assertTrue("Preview wraps and grows instead of cropping", large > small)
    }
}
