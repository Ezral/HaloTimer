package com.ezral.halo

import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.MotionEvent
import android.view.InputDevice
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ezral.halo.core.*
import com.ezral.halo.runtime.HaloRuntimeService
import kotlinx.coroutines.*
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HaloMarketingCapture {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as HaloApplication
    private val c get() = app.coordinator
    private lateinit var activity: MainActivity
    private fun shell(s: String) = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(s)).bufferedReader().use { it.readText() }
    private fun waitFor(condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 10_000
        while (!condition()) { check(SystemClock.elapsedRealtime() < end) { "Capture condition timed out" }; SystemClock.sleep(80) }
    }
    private fun command(cmd: Command) = runBlocking { withContext(Dispatchers.Main) { c.execute(cmd) } }
    private fun shot(name: String) { shell("screencap -p /sdcard/Download/halo-marketing/$name.png") }
    private fun record(name: String, seconds: Int, action: () -> Unit) {
        val stream = instrumentation.uiAutomation.executeShellCommand("screenrecord --bit-rate 12000000 --time-limit $seconds /sdcard/Download/halo-marketing/$name.mp4")
        SystemClock.sleep(600)
        action()
        ParcelFileDescriptor.AutoCloseInputStream(stream).bufferedReader().use { it.readText() }
    }
    private fun node(label: String): android.view.accessibility.AccessibilityNodeInfo? {
        val a = instrumentation.uiAutomation
        a.serviceInfo = a.serviceInfo.apply { flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        fun find(n: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
            if (n == null) return null
            if (n.contentDescription?.toString()?.contains(label) == true || n.text?.toString() == label) return n
            for (i in 0 until n.childCount) find(n.getChild(i))?.let { return it }
            return null
        }
        return a.windows.firstNotNullOfOrNull { find(it.root) }
    }
    private fun bounds(label: String): Rect {
        waitFor { node(label) != null }
        return Rect().also { node(label)!!.getBoundsInScreen(it) }
    }
    private fun openMenu() {
        shell("am start -W -n com.ezral.halo.debug/com.ezral.halo.MainActivity -f 0x00020000")
        waitFor { activity.hasWindowFocus() }; SystemClock.sleep(500)
    }
    private fun start(ids: Set<Int>) {
        command(Command.Start(ids))
        instrumentation.runOnMainSync { app.startForegroundService(android.content.Intent(app, HaloRuntimeService::class.java)) }
        shell("am start -W -n com.ezral.halo.debug.test/com.ezral.halo.MarketingBackdropActivity")
        waitFor { !activity.hasWindowFocus() }
        SystemClock.sleep(900)
    }
    @After fun cleanup() {
        if (::activity.isInitialized) {
            command(Command.StopAll)
            instrumentation.runOnMainSync { app.stopService(android.content.Intent(app, HaloRuntimeService::class.java)) }
        }
    }
    @Test(timeout = 240_000) fun captureMarketingMedia() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("haloMarketing") == "true")
        activity = rule.activity
        waitFor { c.ready.value }
        instrumentation.uiAutomation.grantRuntimePermission("com.ezral.halo.debug", android.Manifest.permission.POST_NOTIFICATIONS)
        shell("appops set com.ezral.halo.debug SYSTEM_ALERT_WINDOW allow")
        shell("mkdir -p /sdcard/Download/halo-marketing")
        shell("settings put global sysui_demo_allowed 1")
        shell("am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941")
        shell("am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false")
        shell("am broadcast -a com.android.systemui.demo -e command notifications -e visible false")
        command(Command.StopAll)
        runBlocking { c.preferences.theme("Dark"); c.preferences.dismissAllOnMenu(false); c.preferences.motion(false) }
        val names = listOf("Pour-over", "Tea", "Focus")
        val colors = listOf(0xFF8A2BE2L, 0xFF00B890L, 0xFFFF8C36L)
        (0..2).forEach { id -> command(Command.Edit(Definition(id, name = names[id], active = true,
            durationMs = listOf(150_000L, 180_000L, 300_000L)[id], color = colors[id], haptic = HapticStyle.OFF,
            x = .48f, y = .25f + id * .12f, glow = .6f))) }
        SystemClock.sleep(800); shot("01-dark-menu")
        runBlocking { c.preferences.theme("Light") }; SystemClock.sleep(600); shot("02-light-menu")
        command(Command.Edit(c.state.value.tracks[0].definition.copy(sequence = true, steps = listOf(Step("Bloom", 30_000), Step("Slow pour", 120_000), Step("Draw down", 45_000)))))
        SystemClock.sleep(600); shot("03-sequence-menu")
        command(Command.Edit(c.state.value.tracks[0].definition.copy(sequence = false)))
        start(setOf(0, 1, 2))
        bounds("Drag to an edge to dock")
        record("01-parallel-timers", 12) {
            SystemClock.sleep(1800); shot("04-parallel-overlay")
            // Switch to an actual system app, then return to the independent recipe app.
            shell("am start -W -a android.settings.SETTINGS")
            SystemClock.sleep(2800); shot("05-over-android-settings")
            shell("am start -W -n com.ezral.halo.debug.test/com.ezral.halo.MarketingBackdropActivity")
        }
        command(Command.Reset(1)); command(Command.Reset(2))
        record("02-fluid-dock", 16) {
            val b = bounds("Drag to an edge to dock")
            SystemClock.sleep(800)
            shell("input swipe ${b.centerX()} ${b.centerY()} 0 ${b.centerY()} 700")
            waitFor { c.state.value.tracks[0].definition.dock == DockSide.LEFT }
            SystemClock.sleep(1000); shot("06-half-circle-dock")
            val dock = bounds("Tap or drag inward to expand")
            val d = activity.resources.displayMetrics.density
            val a = instrumentation.uiAutomation
            val down = SystemClock.uptimeMillis()
            fun pointer(type: Int, x: Float, y: Float) {
                MotionEvent.obtain(down, SystemClock.uptimeMillis(), type, x, y, 0).also {
                    it.source = InputDevice.SOURCE_TOUCHSCREEN; a.injectInputEvent(it, true); it.recycle()
                }
            }
            pointer(MotionEvent.ACTION_DOWN, 24*d, dock.centerY().toFloat())
            SystemClock.sleep(1500); shot("07-dock-actions")
            val top = (dock.centerY() - 144*d).coerceAtLeast(0f)
            val x = (kotlin.math.cos(Math.toRadians(-22.0))*108*d).toFloat()
            val y = top + (144 + kotlin.math.sin(Math.toRadians(-22.0))*108).toFloat()*d
            pointer(MotionEvent.ACTION_MOVE, x, y); SystemClock.sleep(600); pointer(MotionEvent.ACTION_UP, x, y)
            waitFor { c.state.value.tracks[0].session?.status == Status.PAUSED }
            SystemClock.sleep(1000)
            shell("input swipe ${(24*d).toInt()} ${dock.centerY()} ${(200*d).toInt()} ${dock.centerY()} 700")
            waitFor { c.state.value.tracks[0].definition.dock == DockSide.NONE }
            SystemClock.sleep(1200); shot("08-compact-glass")
        }
        command(Command.StopAll)
        for ((index, style) in AlertStyle.entries.withIndex()) {
            openMenu()
            command(Command.Edit(c.state.value.tracks[0].definition.copy(durationMs = 1000, alert = style, dock = DockSide.NONE, x = .48f, y = .48f)))
            start(setOf(0))
            waitFor { c.state.value.tracks[0].session?.status == Status.COMPLETED }
            record("0${index+3}-alert-${style.name.lowercase()}", 7) {
                SystemClock.sleep(1200); shot("0${index+9}-alert-${style.name.lowercase()}")
            }
            command(Command.StopAll)
        }
    }
}
