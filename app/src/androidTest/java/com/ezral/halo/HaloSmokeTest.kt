package com.ezral.halo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import com.ezral.halo.core.*
import com.ezral.halo.overlay.EdgeView
import com.ezral.halo.overlay.EdgeWindowLayout
import org.junit.Assert.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HaloSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    }

    private fun checkFullDisplayAndSpacing() {
        val context = rule.activity.applicationContext
        val manager = context.getSystemService(WindowManager::class.java)
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        manager.defaultDisplay.getRealMetrics(metrics)
        lateinit var view: EdgeView
        rule.runOnUiThread {
            view = EdgeView(context).apply {
                reducedMotion = true
                tracks = (0..2).map { id -> Track(Definition(id, active = true, glow = 0f),
                    Session("geometry-$id", listOf(Step()), deadlineMs = SystemClock.elapsedRealtime() + 300_000)) }
            }
            manager.addView(view, EdgeWindowLayout.create(context))
        }
        try {
            rule.waitUntil(5_000) { view.width > 0 && view.height > 0 }
            screenshot("04-full-display-three-lanes")
            rule.runOnUiThread {
                val location = IntArray(2); view.getLocationOnScreen(location)
                assertEquals("Edge starts at display left", 0, location[0])
                assertEquals("Edge includes status bar", 0, location[1])
                assertEquals("Edge covers display width", metrics.widthPixels, view.width)
                assertEquals("Edge includes bottom system navigation", metrics.heightPixels, view.height)
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val density = context.resources.displayMetrics.density
                listOf(2, 8, 14).forEach { x -> assertTrue("Lane core at ${x}dp", Color.alpha(bitmap.getPixel((x * density).toInt(), view.height / 2)) > 0) }
                listOf(5, 11).forEach { x -> assertEquals("Transparent gap at ${x}dp", 0, Color.alpha(bitmap.getPixel((x * density).toInt(), view.height / 2))) }
                bitmap.recycle()
            }
            rule.runOnUiThread {
                var frameTime = 1_000L
                view.clock = { frameTime }; view.reducedMotion = false
                val frames = mutableListOf<Bitmap>()
                try {
                    AlertStyle.entries.forEach { style ->
                        view.tracks = listOf(Track(Definition(0, alert = style, glow = 0f),
                            Session("alert", listOf(Step()), status = Status.COMPLETED, deadlineMs = 1_000,
                                visualUntilMs = Long.MAX_VALUE, alertStartedAtMs = 1_000)))
                        fun frame(time: Long): Bitmap {
                            frameTime = time
                            return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)); frames += it }
                        }
                        assertFalse("$style must animate", frame(1_000).sameAs(frame(1_700)))
                        if (style == AlertStyle.ORBIT) {
                            val before = frame(3_799); val after = frame(3_801)
                            var changed = 0
                            for (y in 0 until view.height step 2) for (x in 0 until view.width step 2) {
                                if (before.getPixel(x, y) != after.getPixel(x, y)) changed++
                            }
                            assertTrue("Orbit crosses the seam without restarting its length", changed < 500)
                        }
                    }
                } finally { frames.forEach { it.recycle() } }
            }
            // The decorative window must not consume a tap on the app beneath it.
            rule.onNodeWithContentDescription("Decrease seconds").performClick()
            rule.waitUntil(5_000) { (rule.activity.application as HaloApplication).coordinator.state.value.tracks[0].definition.durationMs == 299_000L }
            rule.onNodeWithContentDescription("Increase seconds").performClick()
        } finally { rule.runOnUiThread { manager.removeViewImmediate(view) } }
    }

    private fun overlayNode(description: String): android.view.accessibility.AccessibilityNodeInfo? {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val config = automation.serviceInfo
        config.flags = config.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = config
        fun find(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.contentDescription?.toString()?.contains(description) == true) return node
            for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
            return null
        }
        return automation.windows.firstNotNullOfOrNull { find(it.root) }
    }

    private fun checkGlassDockAndPlayback() {
        val c = (rule.activity.application as HaloApplication).coordinator
        rule.waitUntil(5_000) { overlayNode("Drag to an edge to dock") != null }
        val bounds = android.graphics.Rect()
        overlayNode("Drag to an edge to dock")!!.getBoundsInScreen(bounds)
        shell("input swipe ${bounds.centerX()} ${bounds.centerY()} 0 ${bounds.centerY()} 600")
        rule.waitUntil(5_000) { c.state.value.tracks[0].definition.dock == DockSide.LEFT }
        rule.waitUntil(5_000) { overlayNode("Tap or drag inward to expand") != null }
        screenshot("07-left-docked-glass")
        val dockBounds = android.graphics.Rect()
        overlayNode("Tap or drag inward to expand")!!.getBoundsInScreen(dockBounds)
        val density = rule.activity.resources.displayMetrics.density
        // Pull the half-circle back into the screen, without touching system back-gesture territory.
        shell("input swipe ${(24 * density).toInt()} ${dockBounds.centerY()} ${(200 * density).toInt()} ${dockBounds.centerY()} 600")
        rule.waitUntil(5_000) { c.state.value.tracks[0].definition.dock == DockSide.NONE && overlayNode("Pause Timer A") != null }
        overlayNode("Pause Timer A")!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        rule.waitUntil(5_000) { c.state.value.tracks[0].session?.status == Status.PAUSED && overlayNode("Play Timer A") != null }
        screenshot("08-paused-glass")
        overlayNode("Reset Timer A")!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        rule.waitUntil(5_000) { c.state.value.tracks[0].session?.remainingMs == 300_000L }
        assertEquals(Status.PAUSED, c.state.value.tracks[0].session?.status)
        overlayNode("Play Timer A")!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        rule.waitUntil(5_000) { c.state.value.tracks[0].session?.status == Status.RUNNING }
    }

    private fun checkSettingsEditors() {
        val c = (rule.activity.application as HaloApplication).coordinator
        rule.onNodeWithText("Morse", substring = false).performScrollTo().performClick()
        rule.waitUntil(5_000) { c.state.value.tracks[0].definition.haptic == HapticStyle.MORSE }
        rule.onNodeWithText("Edit", substring = false).performScrollTo().performClick()
        rule.onNodeWithContentDescription("Morse input").performTextReplacement("!")
        rule.onNodeWithText("Save").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Morse input").performTextReplacement("sos")
        rule.onNodeWithText("Save").performClick()
        rule.waitUntil(5_000) { c.state.value.tracks[0].definition.morse == "SOS" }
        rule.onNodeWithText(Morse.display("SOS")).assertExists()
        screenshot("09-morse-saved")
        rule.onNodeWithText("Custom", substring = false).performScrollTo().performClick()
        rule.waitUntil(5_000) { c.state.value.tracks[0].definition.hapticRepeat == HapticRepeat.CUSTOM }
        rule.onNodeWithText("Duration", substring = false).performScrollTo().performClick()
        rule.waitUntil(5_000) { c.state.value.tracks[0].definition.hapticRepeat == HapticRepeat.TIMED }
        rule.onNodeWithContentDescription("Vibration duration").performScrollTo().performTextReplacement("2")
        rule.waitUntil(5_000) { c.state.value.tracks[0].definition.repeatDurationMs == 2_000L }
        rule.onNodeWithText("Custom line color").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Custom color input").performTextReplacement("-FFFFF")
        rule.onNodeWithText("Save").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Custom color input").performTextReplacement("#FF2D2D")
        rule.onNodeWithText("Save").performClick()
        rule.waitUntil(5_000) { c.state.value.tracks[0].definition.color == 0xFFFF2D2DL }
        screenshot("10-custom-color")
        rule.onNodeWithText("Off", substring = false).performScrollTo().performClick()
        rule.waitUntil(5_000) { c.state.value.tracks[0].definition.haptic == HapticStyle.OFF }
    }

    private fun checkRealCompletion() {
        val c = (rule.activity.application as HaloApplication).coordinator
        // CI disables system transition animations; Halo canvas alerts must still animate.
        c.submit(Command.Edit(c.state.value.tracks[0].definition.copy(durationMs = 1_000, alert = AlertStyle.ORBIT, haptic = HapticStyle.OFF)))
        rule.waitUntil(5_000) { c.state.value.tracks[0].definition.durationMs == 1_000L }
        rule.onNodeWithContentDescription("Start timer").performClick()
        rule.waitUntil(8_000) { c.state.value.tracks[0].session?.status == Status.COMPLETED }
        assertEquals(Long.MAX_VALUE, c.state.value.tracks[0].session?.visualUntilMs)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val before = automation.takeScreenshot()
        SystemClock.sleep(450)
        val after = automation.takeScreenshot()
        try {
            val border = (rule.activity.resources.displayMetrics.density * 4).toInt()
            var changed = 0
            for (y in 0 until before.height) for (x in 0 until border) if (before.getPixel(x, y) != after.getPixel(x, y)) changed++
            for (x in 0 until before.width) for (y in 0 until border) if (before.getPixel(x, y) != after.getPixel(x, y)) changed++
            for (y in 0 until before.height) for (x in before.width - border until before.width) if (before.getPixel(x, y) != after.getPixel(x, y)) changed++
            for (x in 0 until before.width) for (y in before.height - border until before.height) if (before.getPixel(x, y) != after.getPixel(x, y)) changed++
            assertTrue("Actual completion overlay must move", changed > 0)
        } finally { before.recycle(); after.recycle() }
        screenshot("11-actual-completion")
        rule.waitUntil(5_000) { overlayNode("Hide or dismiss Timer A") != null }
        overlayNode("Hide or dismiss Timer A")!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        rule.waitUntil(5_000) { c.state.value.tracks[0].session == null }
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        // Capture as the shell directly into shared emulator output, outside app uninstall cleanup.
        shell("mkdir -p /sdcard/Download/halo-qa")
        shell("screencap -p /sdcard/Download/halo-qa/$name.png")
    }

    @Test fun nativeEditorThemesAndRuntime() {
        if (android.os.Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission("com.ezral.halo.debug", android.Manifest.permission.POST_NOTIFICATIONS)
        rule.waitUntil(10_000) { (rule.activity.application as HaloApplication).coordinator.ready.value }
        val launcherError = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            ?.findAccessibilityNodeInfosByText("Quickstep isn't responding")?.isNotEmpty() == true
        if (launcherError) shell("am force-stop com.android.launcher3")
        rule.waitUntil(10_000) { rule.activity.hasWindowFocus() }
        shell("appops set com.ezral.halo.debug SYSTEM_ALERT_WINDOW allow")
        rule.waitUntil(5_000) { Settings.canDrawOverlays(rule.activity) }
        // A second-field adjustment must operate on the entire duration, including borrow.
        rule.onNodeWithContentDescription("Decrease seconds").performClick()
        rule.waitUntil(5_000) { (rule.activity.application as HaloApplication).coordinator.state.value.tracks[0].definition.durationMs == 299_000L }
        rule.onNodeWithContentDescription("Increase seconds").performClick()
        rule.waitUntil(5_000) { (rule.activity.application as HaloApplication).coordinator.state.value.tracks[0].definition.durationMs == 300_000L }
        screenshot("01-system-editor")
        rule.onNodeWithText("Light").performScrollTo().performClick()
        rule.waitUntil(5_000) { (rule.activity.application as HaloApplication).coordinator.prefs.value.theme == "Light" }
        screenshot("02-light-editor")
        rule.onNodeWithText("Dark").performScrollTo().performClick()
        rule.waitUntil(5_000) { (rule.activity.application as HaloApplication).coordinator.prefs.value.theme == "Dark" }
        screenshot("03-dark-editor")
        rule.onNodeWithContentDescription("Decrease seconds").performScrollTo()
        checkFullDisplayAndSpacing()
        checkSettingsEditors()
        rule.onNodeWithContentDescription("Decrease seconds").performScrollTo()
        rule.onNodeWithContentDescription("Start timer").performClick()
        rule.waitUntil(5_000) { (rule.activity.application as HaloApplication).coordinator.state.value.tracks[0].session != null }
        rule.waitUntil(5_000) { !rule.activity.hasWindowFocus() }
        screenshot("05-running-over-home")
        checkGlassDockAndPlayback()
        shell("am start -W -n com.ezral.halo.debug/com.ezral.halo.MainActivity -f 0x00020000")
        rule.waitUntil(5_000) { rule.activity.hasWindowFocus() }
        rule.onNodeWithContentDescription("Pause timer").assertExists()
        rule.waitUntil(5_000) { overlayNode("Drag to an edge to dock") == null && overlayNode("Tap or drag inward to expand") == null }
        screenshot("06-running-settings")
        rule.onNodeWithContentDescription("Pause timer").performClick()
        rule.waitUntil(5_000) { (rule.activity.application as HaloApplication).coordinator.state.value.tracks[0].session?.status == com.ezral.halo.core.Status.PAUSED }
        rule.onNodeWithText("Reset").performScrollTo().performClick()
        rule.waitUntil(5_000) { (rule.activity.application as HaloApplication).coordinator.state.value.tracks[0].session == null }
        checkRealCompletion()
    }
}
