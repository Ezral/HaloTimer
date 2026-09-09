package com.ezral.halo

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ezral.halo.core.*
import com.ezral.halo.runtime.HaloRuntimeService
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Opt-in real emulator capture; no marketing backdrop or synthesized overlay pixels. */
@RunWith(AndroidJUnit4::class)
class HaloFoldCapture {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val app get()=inst.targetContext.applicationContext as HaloApplication
    private val c get()=app.coordinator
    private lateinit var activity:MainActivity
    private lateinit var launchIntent:Intent
    private val directory=File("/sdcard/Download/halo-fold")
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun waitFor(test:()->Boolean) { val end=SystemClock.elapsedRealtime()+12_000;while(!test()) { check(SystemClock.elapsedRealtime()<end) { "Fold capture timed out" };SystemClock.sleep(80) } }
    private fun command(cmd:Command)=runBlocking { withContext(Dispatchers.Main) { c.execute(cmd) } }
    private fun shot(name:String)=shell("screencap -p /sdcard/Download/halo-fold/$name.png")
    private fun node(label:String):android.view.accessibility.AccessibilityNodeInfo? {
        val a=inst.uiAutomation;a.serviceInfo=a.serviceInfo.apply { flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        fun find(n:android.view.accessibility.AccessibilityNodeInfo?):android.view.accessibility.AccessibilityNodeInfo? {
            if(n==null) return null
            if(n.contentDescription?.toString()?.contains(label)==true) return n
            for(i in 0 until n.childCount) find(n.getChild(i))?.let { return it };return null
        }
        return a.windows.firstNotNullOfOrNull { find(it.root) }
    }
    @After fun cleanup() {
        if(!::activity.isInitialized) return
        command(Command.StopAll)
        inst.runOnMainSync { app.stopService(Intent(app,HaloRuntimeService::class.java));activity.intent=launchIntent;activity.finish() }
    }
    @Test(timeout=90_000) fun openedDisplayPillDockAndBottomContinuity() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("haloFold")=="true")
        activity=rule.activity;launchIntent=Intent(activity.intent);waitFor { c.ready.value }
        inst.uiAutomation.grantRuntimePermission("com.ezral.halo.debug",android.Manifest.permission.POST_NOTIFICATIONS)
        shell("appops set com.ezral.halo.debug SYSTEM_ALERT_WINDOW allow");shell("mkdir -p /sdcard/Download/halo-fold")
        command(Command.StopAll)
        runBlocking { c.preferences.theme("Dark");c.preferences.dismissAllOnMenu(false);c.preferences.motion(false);c.preferences.completionEnabled(false) }
        command(Command.Edit(Definition(0,name="Gym Set Rest",durationMs=90_000,color=0xFF57DDB4L,haptic=HapticStyle.OFF,dock=DockSide.NONE,x=.35f,y=.42f)))
        SystemClock.sleep(700);shot("01-opened-halo-menu")
        command(Command.Start(setOf(0)))
        inst.runOnMainSync { app.startForegroundService(Intent(app,HaloRuntimeService::class.java));activity.moveTaskToBack(true) }
        waitFor { !activity.hasWindowFocus() && node("Drag to an edge to dock")!=null }
        SystemClock.sleep(700);shot("02-opened-floating-pill")
        val recording=inst.uiAutomation.executeShellCommand("screenrecord --size 984x1092 --bit-rate 6000000 --time-limit 10 /sdcard/Download/halo-fold/04-live-docking.mp4")
        val b=Rect();node("Drag to an edge to dock")!!.getBoundsInScreen(b)
        shell("input swipe ${b.centerX()} ${b.centerY()} 0 ${b.centerY()} 650")
        waitFor { c.state.value.tracks[0].definition.dock==DockSide.LEFT && node("Tap or drag inward to expand")!=null }
        SystemClock.sleep(1500);shot("03-opened-side-dock")
        // Inspect actual composited display pixels: the whole bottom-center band must have
        // an uninterrupted mint core. This would fail if a hinge-shaped gap were rendered.
        val bitmap=checkNotNull(inst.uiAutomation.takeScreenshot())
        try {
            assertEquals("Unfolded target width",1968,bitmap.width)
            assertEquals("Unfolded target height",2184,bitmap.height)
            val d=app.resources.displayMetrics.density
            val half=(32*d).toInt();val band=(12*d).toInt();val missing=mutableListOf<Int>()
            for(x in bitmap.width/2-half..bitmap.width/2+half) {
                val lit=(bitmap.height-band until bitmap.height).any { y ->
                    val p=bitmap.getPixel(x,y)
                    Color.green(p)>110 && Color.green(p)>Color.red(p)+45 && Color.blue(p)>Color.red(p)+25
                }
                if(!lit) missing+=x
            }
            // Save the exact inspection frame, including failures, without retouching it.
            val cache=File(app.cacheDir,"fold-bottom-check.png")
            cache.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            shell("run-as com.ezral.halo.debug cat cache/fold-bottom-check.png > /sdcard/Download/halo-fold/05-bottom-check.png")
            assertTrue("No mint pixels at bottom-center columns: $missing",missing.isEmpty())
        } finally { bitmap.recycle() }
        ParcelFileDescriptor.AutoCloseInputStream(recording).use { it.readBytes() }
        assertEquals(Status.RUNNING,c.state.value.tracks[0].session?.status)
    }
}
