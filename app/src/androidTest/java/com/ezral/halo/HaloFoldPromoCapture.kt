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
class HaloFoldPromoCapture {
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
    @Test(timeout=90_000) fun countdownSwipeAndCompletion() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("haloFoldPromo")=="true")
        activity=rule.activity;launchIntent=Intent(activity.intent);waitFor { c.ready.value }
        inst.uiAutomation.grantRuntimePermission("com.ezral.halo.debug",android.Manifest.permission.POST_NOTIFICATIONS)
        shell("appops set com.ezral.halo.debug SYSTEM_ALERT_WINDOW allow")
        shell("mkdir -p /sdcard/Download/halo-fold-promo")
        command(Command.StopAll)
        runBlocking {
            c.preferences.dismissAllOnMenu(false);c.preferences.motion(false)
            c.preferences.completionEnabled(true);c.preferences.completionSeconds(9)
            c.preferences.completionTextSp(36);c.preferences.completionBold(true)
        }
        command(Command.Edit(Definition(0,name="Gym Set Rest",durationMs=16_000,color=0xFF57DDB4L,haptic=HapticStyle.OFF,dock=DockSide.LEFT,x=0f,y=.45f,glow=.45f)))
        shell("am start -W -n com.ezral.halo.debug.test/com.ezral.halo.ShortsBackdropActivity")
        waitFor { !activity.hasWindowFocus() };SystemClock.sleep(3000)
        val recording=inst.uiAutomation.executeShellCommand("screenrecord --size 984x1092 --bit-rate 10000000 --time-limit 25 /sdcard/Download/halo-fold-promo/countdown-swipe-completion.mp4")
        SystemClock.sleep(700)
        command(Command.Start(setOf(0)))
        inst.runOnMainSync { app.startForegroundService(Intent(app,HaloRuntimeService::class.java)) }
        waitFor { node("Tap or drag inward to expand")!=null }
        SystemClock.sleep(2600)
        shell("screencap -p /sdcard/Download/halo-fold-promo/01-countdown.png")
        shell("input swipe 1100 1650 1100 550 420")
        waitFor { node("Video 2")!=null }
        assertEquals(Status.RUNNING,c.state.value.tracks[0].session?.status)
        SystemClock.sleep(2000)
        shell("screencap -p /sdcard/Download/halo-fold-promo/02-next-video.png")
        waitFor { c.state.value.tracks[0].session?.status==Status.COMPLETED }
        waitFor { node("Timer is completed for")!=null }
        SystemClock.sleep(1000)
        shell("screencap -p /sdcard/Download/halo-fold-promo/03-completion.png")
        ParcelFileDescriptor.AutoCloseInputStream(recording).use { it.readBytes() }
    }
}
