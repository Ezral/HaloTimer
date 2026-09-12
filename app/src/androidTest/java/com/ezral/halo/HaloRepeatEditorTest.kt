package com.ezral.halo

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ezral.halo.core.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HaloRepeatEditorTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val c get() = (rule.activity.application as HaloApplication).coordinator
    @Before fun prepare() {
        rule.waitUntil(10000) { c.ready.value }
        reset()
    }
    @After fun reset() { runBlocking { withContext(Dispatchers.Main) {
        c.execute(Command.StopAll)
        (0..2).forEach { c.execute(Command.Activate(it, it == 0)); c.execute(Command.Edit(Definition(it))) }
        c.preferences.fullScreenMode(false); c.preferences.fullScreenMask(7); c.preferences.keepScreenOn(false)
    } } }
    @Test fun typingReplacesTimeAndRoundPaletteChoicesPersist() {
        rule.onNode(hasContentDescription("Hours") and hasClickAction() and isToggleable(), useUnmergedTree=true).performClick()
        rule.waitUntil(5000) { c.state.value.tracks[0].definition.hoursEnabled }
        rule.onNode(hasContentDescription("Hours") and hasSetTextAction()).performClick().performTextInput("2").performImeAction()
        rule.waitUntil(5000) { c.state.value.tracks[0].definition.durationMs == 7500000L }
        rule.onNode(hasContentDescription("Minutes") and hasSetTextAction()).performClick().performTextInput("12").performImeAction()
        rule.waitUntil(5000) { c.state.value.tracks[0].definition.durationMs == 7920000L }
        rule.onNode(hasContentDescription("Seconds") and hasSetTextAction()).performClick().performTextInput("9").performImeAction()
        rule.waitUntil(5000) { c.state.value.tracks[0].definition.durationMs == 7929000L }
        rule.onNode(hasContentDescription("Minutes") and hasSetTextAction()).performClick().performTextInput("0").performImeAction()
        rule.waitUntil(5000) { c.state.value.tracks[0].definition.durationMs == 7209000L }
        rule.onNodeWithText("Set count").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Timer round count").performTextReplacement("3")
        rule.waitUntil(5000) { c.state.value.tracks[0].definition.repetitions == 3 }
        rule.onNodeWithContentDescription("Repeat timer forever").performClick()
        rule.waitUntil(5000) { c.state.value.tracks[0].definition.repetitions == 0 }
        rule.onNodeWithContentDescription("Aurora mixed color").performScrollTo().performClick()
        rule.waitUntil(5000) { c.state.value.tracks[0].definition.linePalette == LinePalette.AURORA }
        rule.onNodeWithText("Custom line color").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Color hue").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(180f) }
        rule.onNodeWithContentDescription("Color saturation").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1f) }
        rule.onNodeWithContentDescription("Color brightness").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1f) }
        rule.onNodeWithText("Save").performClick()
        rule.waitUntil(5000) { c.state.value.tracks[0].definition.customColor == 0xFF00FFFFL }
        rule.onNodeWithContentDescription("Sunset mixed color").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Saved custom color").performClick()
        rule.waitUntil(5000) { c.state.value.tracks[0].definition.let { it.color == 0xFF00FFFFL && it.linePalette == LinePalette.SOLID } }
    }
}
