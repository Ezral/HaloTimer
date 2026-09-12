package com.ezral.halo

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ezral.halo.core.*
import com.ezral.halo.overlay.OverlayVisibility
import com.ezral.halo.runtime.HaloRuntimeService
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HaloFullScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as HaloApplication
    private val c get() = app.coordinator
    private val automation get() = instrumentation.uiAutomation
    private fun shell(command:String) { ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText() } }
    private fun await(message:String, condition:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+10000
        while(SystemClock.elapsedRealtime()<end) { dismissEducation(); if(condition()) return; SystemClock.sleep(100) }
        assertTrue(message,condition())
    }
    private fun dismissEducation() {
        fun find(n:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
            if(n==null) return null
            if(n.packageName?.toString()=="android" && n.text?.toString()=="Got it") return n
            for(i in 0 until n.childCount) find(n.getChild(i))?.let { return it }
            return null
        }
        automation.windows.firstNotNullOfOrNull { find(it.root) }?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
    private fun node(description:String):AccessibilityNodeInfo? {
        // UiAutomation can retain the earlier virtual children when panels change.
        // https://developer.android.com/reference/android/app/UiAutomation#clearCache()
        if(android.os.Build.VERSION.SDK_INT>=34) automation.clearCache()
        fun find(n:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
            if(n==null) return null
            if(n.contentDescription?.toString()==description) return n
            for(i in 0 until n.childCount) find(n.getChild(i))?.let { return it }
            return null
        }
        return automation.windows.firstNotNullOfOrNull { find(it.root) }
    }
    private fun tap(description:String) {
        await("Visible control: $description") { node(description)!=null }
        var target=node(description)!!
        while(!target.isClickable && target.parent!=null) target=target.parent
        assertTrue("Click $description",target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun capture(name:String) {
        SystemClock.sleep(400);dismissEducation();SystemClock.sleep(150)
        val image=automation.takeScreenshot()!!
        val dir=File("/sdcard/Download/halo-qa");dir.mkdirs()
        File(dir,"fullscreen-$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle()
    }
    @Test fun layoutsIndependentControlsCompletionAndOverlayReturn() {
        automation.serviceInfo=automation.serviceInfo.apply { flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        shell("appops set ${app.packageName} SYSTEM_ALERT_WINDOW allow")
        shell("pm grant ${app.packageName} android.permission.POST_NOTIFICATIONS")
        // The clean emulator otherwise opens Android's full-screen education window
        // after the first capture and obscures the app's accessibility tree.
        shell("settings put secure immersive_mode_confirmations confirmed")
        runBlocking { withContext(Dispatchers.Main) {
            c.initialize();c.execute(Command.StopAll)
            listOf("Gym rest","Pour over","Stretch").forEachIndexed { id,name -> c.execute(Command.Activate(id,true)); c.execute(Command.Edit(Definition(id,name=name,active=true,durationMs=600000,color=listOf(0xFF39EBC7L,0xFFFF7452L,0xFF5260FFL)[id],haptic=HapticStyle.OFF))) }
            c.preferences.dismissAllOnMenu(false);c.preferences.theme("Dark");c.preferences.fullScreenMask(1);c.preferences.completionEnabled(true);c.preferences.completionSeconds(4)
        } }
        val scenario=ActivityScenario.launch(FullScreenActivity::class.java)
        try {
            await("Full-screen shown") { OverlayVisibility.fullScreenVisible && node("Gym rest countdown")!=null }
            capture("one")
            runBlocking { c.preferences.fullScreenMask(3) }
            await("Two timers shown") { node("Pour over countdown")!=null };capture("two")
            runBlocking { c.preferences.fullScreenMask(7) }
            await("Three timers shown") { node("Stretch countdown")!=null };capture("three")
            runBlocking { c.preferences.theme("Light") }
            await("Light theme") { c.prefs.value.theme=="Light" };capture("three-light")
            runBlocking { c.preferences.theme("Dark") }
            await("Dark theme") { c.prefs.value.theme=="Dark" }
            tap("Start Gym rest full screen")
            await("Starts only selected timer") { c.state.value.tracks[0].session?.status==Status.RUNNING }
            assertNull(c.state.value.tracks[1].session)
            tap("Pause Gym rest full screen")
            await("Pause works") { c.state.value.tracks[0].session?.status==Status.PAUSED }
            tap("Stop Gym rest full screen")
            await("Stop clears session") { c.state.value.tracks[0].session==null }
            scenario.onActivity { it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            await("Landscape") { app.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            capture("three-landscape")
            scenario.onActivity { it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            await("Portrait") { app.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT }
            runBlocking { withContext(Dispatchers.Main) { c.execute(Command.Edit(c.state.value.tracks[0].definition.copy(durationMs=1000))) } }
            tap("Start displayed timers")
            await("First timer completes") { c.state.value.tracks[0].session?.status==Status.COMPLETED }
            await("Completion effect visible") { node("Gym rest completion animation. Tap to close.")!=null }
            capture("completion")
            assertEquals(Status.RUNNING,c.state.value.tracks[1].session?.status)
            assertEquals(Status.RUNNING,c.state.value.tracks[2].session?.status)
            tap("Return to overlay")
            await("Full-screen yields to overlays") { !OverlayVisibility.fullScreenVisible }
            capture("overlay-return")
        } catch (failure: Throwable) {
            capture("failure")
            val dir=File("/sdcard/Download/halo-qa")
            File(dir,"fullscreen-state.txt").writeText(c.state.value.toString()+"\n"+c.prefs.value.toString())
            val nodes=StringBuilder()
            fun dump(n:AccessibilityNodeInfo?) {
                if(n==null) return
                nodes.append(n.className).append(" | ").append(n.contentDescription).append(" | ").append(n.text).append('\n')
                for(i in 0 until n.childCount) dump(n.getChild(i))
            }
            automation.windows.forEach { dump(it.root) }
            File(dir,"fullscreen-accessibility.txt").writeText(nodes.toString())
            throw failure
        } finally {
            runBlocking { withContext(Dispatchers.Main) {
                c.execute(Command.StopAll); app.stopService(Intent(app,HaloRuntimeService::class.java)); c.haptics.cancelAll()
                (0..2).forEach { c.execute(Command.Activate(it, it == 0)); c.execute(Command.Edit(Definition(it))) }
                c.preferences.dismissAllOnMenu(false);c.preferences.fullScreenMode(false);c.preferences.fullScreenMask(7);c.preferences.completionEnabled(false);c.preferences.completionSeconds(4);c.preferences.theme("System")
            } };scenario.close()
        }
    }
}
