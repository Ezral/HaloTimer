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
import java.io.File

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
        overlayNode("Tap or drag inward to expand")!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        rule.waitUntil(5_000) { c.state.value.tracks[0].definition.dock == DockSide.NONE && overlayNode("Pause Timer A") != null }
        overlayNode("Pause Timer A")!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        rule.waitUntil(5_000) { c.state.value.tracks[0].session?.status == Status.PAUSED && overlayNode("Play Timer A") != null }
        screenshot("08-paused-glass")
        overlayNode("Play Timer A")!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        rule.waitUntil(5_000) { c.state.value.tracks[0].session?.status == Status.RUNNING }
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(rule.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        // The test runner may uninstall the app before Gradle returns. Preserve QA output first.
        shell("mkdir -p /sdcard/Download/halo-qa")
        shell("cp '${File(directory, "$name.png").absolutePath}' '/sdcard/Download/halo-qa/$name.png'")
    }

    @Test fun nativeEditorThemesAndRuntime() {
        if (android.os.Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission("com.ezral.halo.debug", android.Manifest.permission.POST_NOTIFICATIONS)
        rule.waitUntil(10_000) { (rule.activity.application as HaloApplication).coordinator.ready.value }
        shell("appops set com.ezral.halo.debug SYSTEM_ALERT_WINDOW allow")
        rule.waitUntil(5_000) { Settings.canDrawOverlays(rule.activity) }
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
        checkFullDisplayAndSpacing()
        rule.onNodeWithText("Start timer").performClick()
        rule.waitUntil(5_000) { (rule.activity.application as HaloApplication).coordinator.state.value.tracks[0].session != null }
        rule.waitUntil(5_000) { !rule.activity.hasWindowFocus() }
        screenshot("05-running-over-home")
        checkGlassDockAndPlayback()
        shell("am start -W -n com.ezral.halo.debug/com.ezral.halo.MainActivity -f 0x00020000")
        rule.waitUntil(5_000) { rule.activity.hasWindowFocus() }
        rule.onNodeWithText("Pause").assertExists()
        screenshot("06-running-settings")
        rule.onNodeWithText("Pause").performClick()
        rule.waitUntil(5_000) { (rule.activity.application as HaloApplication).coordinator.state.value.tracks[0].session?.status == com.ezral.halo.core.Status.PAUSED }
        rule.onNodeWithText("Reset").performClick()
        rule.waitUntil(5_000) { (rule.activity.application as HaloApplication).coordinator.state.value.tracks[0].session == null }
    }
}
