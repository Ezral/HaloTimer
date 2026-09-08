package com.ezral.halo

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HaloSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun screenshot(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(rule.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun nativeEditorThemesAndRuntime() {
        if (android.os.Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission("com.ezral.halo.debug", android.Manifest.permission.POST_NOTIFICATIONS)
        rule.waitUntil(10_000) { (rule.activity.application as HaloApplication).coordinator.ready.value }
        // A second-field adjustment must operate on the entire duration, including borrow.
        rule.onNodeWithContentDescription("Decrease seconds").performClick()
        rule.waitUntil(5_000) { (rule.activity.application as HaloApplication).coordinator.state.value.tracks[0].definition.durationMs == 299_000L }
        rule.onNodeWithContentDescription("Increase seconds").performClick()
        rule.waitUntil(5_000) { (rule.activity.application as HaloApplication).coordinator.state.value.tracks[0].definition.durationMs == 300_000L }
        screenshot("01-system-editor")
        rule.onNodeWithText("System", substring = true).performClick()
        rule.waitUntil(5_000) { (rule.activity.application as HaloApplication).coordinator.prefs.value.theme == "Light" }
        screenshot("02-light-editor")
        rule.onNodeWithText("Light", substring = true, ignoreCase = false).performClick()
        rule.waitUntil(5_000) { (rule.activity.application as HaloApplication).coordinator.prefs.value.theme == "Dark" }
        screenshot("03-dark-editor")
        rule.onNodeWithText("Start timer").performScrollTo().performClick()
        rule.waitUntil(5_000) { (rule.activity.application as HaloApplication).coordinator.state.value.tracks[0].session != null }
        rule.onNodeWithText("Pause").assertExists()
        screenshot("04-running")
        rule.onNodeWithText("Pause").performClick()
        rule.waitUntil(5_000) { (rule.activity.application as HaloApplication).coordinator.state.value.tracks[0].session?.status == com.ezral.halo.core.Status.PAUSED }
        rule.onNodeWithText("Reset").performClick()
        rule.waitUntil(5_000) { (rule.activity.application as HaloApplication).coordinator.state.value.tracks[0].session == null }
    }
}
