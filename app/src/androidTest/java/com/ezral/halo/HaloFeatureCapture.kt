package com.ezral.halo

import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ezral.halo.core.*
import com.ezral.halo.runtime.HaloRuntimeService
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real overlay input and screenshots, including a held gesture from one edge to the other. */
@RunWith(AndroidJUnit4::class)
class HaloFeatureCapture {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val app get()=inst.targetContext.applicationContext as HaloApplication
    private val c get()=app.coordinator
    private lateinit var activity:MainActivity
    private fun shell(s:String)=ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(s)).bufferedReader().use { it.readText() }
    private fun waitFor(test:()->Boolean) { val end=SystemClock.elapsedRealtime()+8000;while(!test()) { check(SystemClock.elapsedRealtime()<end) { "Feature check timed out" };SystemClock.sleep(60) } }
    private fun command(cmd:Command)=runBlocking { withContext(Dispatchers.Main) { c.execute(cmd) } }
    private fun shot(name:String) { shell("screencap -p /sdcard/Download/halo-qa/$name.png") }
    private fun window(name:String)=shell("dumpsys window windows").lineSequence().any { "Window #" in it && name in it }
    private fun node(label:String):android.view.accessibility.AccessibilityNodeInfo? {
        val a=inst.uiAutomation;a.serviceInfo=a.serviceInfo.apply { flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        fun find(n:android.view.accessibility.AccessibilityNodeInfo?):android.view.accessibility.AccessibilityNodeInfo? {
            if(n==null) return null
            if(n.contentDescription?.toString()?.contains(label)==true || n.text?.toString()==label) return n
            for(i in 0 until n.childCount) find(n.getChild(i))?.let { return it };return null
        }
        return a.windows.firstNotNullOfOrNull { find(it.root) }
    }
    private fun bounds(label:String):Rect { waitFor { node(label)!=null };return Rect().also { node(label)!!.getBoundsInScreen(it) } }
    @After fun cleanup() {
        if(!::activity.isInitialized) return
        command(Command.StopAll)
        inst.runOnMainSync { app.stopService(android.content.Intent(app,HaloRuntimeService::class.java)) }
        (0..2).forEach { command(Command.Activate(it,it==0));command(Command.Edit(Definition(it))) }
        runBlocking { c.preferences.completionEnabled(false);c.preferences.completionSeconds(4);c.preferences.theme("System") }
    }
    @Test(timeout=120_000) fun contourDragAndCompletionCapture() {
        activity=rule.activity;waitFor { c.ready.value }
        inst.uiAutomation.grantRuntimePermission("com.ezral.halo.debug",android.Manifest.permission.POST_NOTIFICATIONS)
        shell("appops set com.ezral.halo.debug SYSTEM_ALERT_WINDOW allow");shell("mkdir -p /sdcard/Download/halo-qa")
        command(Command.StopAll)
        runBlocking { c.preferences.dismissAllOnMenu(false);c.preferences.motion(false);c.preferences.theme("Light");c.preferences.completionEnabled(true);c.preferences.completionSeconds(3) }
        command(Command.Edit(Definition(0,name="Pour-over",color=0xFF8A2BE2L,showBarName=true,rotateBarText=false,haptic=HapticStyle.OFF,x=.4f,y=.35f)))
        rule.onNodeWithContentDescription("Show timer name").performScrollTo().performClick()
        waitFor { !c.state.value.tracks[0].definition.showBarName }
        rule.onNodeWithContentDescription("Show timer name").performClick()
        rule.onNodeWithContentDescription("Rotate text around bar").performClick()
        waitFor { c.state.value.tracks[0].definition.showBarName && c.state.value.tracks[0].definition.rotateBarText }
        rule.onNodeWithText("Completion screen",useUnmergedTree=true).performScrollTo()
        SystemClock.sleep(250);shot("20-completion-settings")
        // Use the compact default for the first scene, then capture optional perimeter text separately.
        command(Command.Edit(c.state.value.tracks[0].definition.copy(rotateBarText=false)))
        command(Command.Start(setOf(0)))
        inst.runOnMainSync { app.startForegroundService(android.content.Intent(app,HaloRuntimeService::class.java)) }
        shell("am start -W -n com.ezral.halo.debug.test/com.ezral.halo.MarketingBackdropActivity")
        waitFor { !activity.hasWindowFocus() };val b=bounds("Drag to an edge to dock")
        SystemClock.sleep(450);shot("21-rounded-bar-with-name")
        val d=activity.resources.displayMetrics.density;val w=activity.resources.displayMetrics.widthPixels
        var down=0L
        fun pointer(action:Int,x:Float,y:Float) {
            if(action==MotionEvent.ACTION_DOWN) down=SystemClock.uptimeMillis()
            val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);e.source=InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(inst.uiAutomation.injectInputEvent(e,true)) } finally { e.recycle() }
        }
        val recording=inst.uiAutomation.executeShellCommand("screenrecord --bit-rate 10000000 --time-limit 10 /sdcard/Download/halo-qa/22-live-docking.mp4")
        pointer(MotionEvent.ACTION_DOWN,b.centerX().toFloat(),b.centerY().toFloat())
        pointer(MotionEvent.ACTION_MOVE,0f,b.centerY().toFloat());SystemClock.sleep(320)
        assertTrue("Contact preview appears before release",window("Halo drag surface"));shot("22-bar-merges-while-held")
        pointer(MotionEvent.ACTION_UP,0f,b.centerY().toFloat())
        waitFor { c.state.value.tracks[0].definition.dock==DockSide.LEFT && !window("Halo surface transition") && !window("Halo drag surface") }
        val dock=bounds("Tap or drag inward to expand");SystemClock.sleep(400);shot("23-contour-label-left")
        pointer(MotionEvent.ACTION_DOWN,24*d,dock.centerY().toFloat())
        pointer(MotionEvent.ACTION_MOVE,w/2f,dock.centerY().toFloat());SystemClock.sleep(350);shot("24-held-circle")
        // Same pointer: touch the opposite edge without releasing in between.
        pointer(MotionEvent.ACTION_MOVE,w-1f,dock.centerY().toFloat());SystemClock.sleep(320)
        assertTrue(window("Halo drag surface"));shot("25-circle-merges-right-while-held")
        pointer(MotionEvent.ACTION_UP,w-1f,dock.centerY().toFloat())
        waitFor { c.state.value.tracks[0].definition.dock==DockSide.RIGHT && !window("Halo drag surface") && !window("Halo surface transition") }
        SystemClock.sleep(300);shot("26-contour-label-right")
        ParcelFileDescriptor.AutoCloseInputStream(recording).use { it.readBytes() }
        command(Command.Move(0,.4f,.35f,DockSide.NONE));waitFor { node("Drag to an edge to dock")!=null };SystemClock.sleep(350)
        command(Command.Edit(c.state.value.tracks[0].definition.copy(rotateBarText=true)))
        SystemClock.sleep(700);shot("27-bar-perimeter-text")
        // Final sequence step triggers exactly one reveal, which expires without stopping the timer alert.
        command(Command.Reset(0))
        shell("am start -W -n com.ezral.halo.debug/com.ezral.halo.MainActivity -f 0x00020000")
        waitFor { activity.hasWindowFocus() }
        command(Command.Edit(c.state.value.tracks[0].definition.copy(sequence=true,steps=listOf(Step("Bloom",1000),Step("Slow pour",1000)),dock=DockSide.RIGHT)))
        command(Command.Start(setOf(0)))
        inst.runOnMainSync { app.startForegroundService(android.content.Intent(app,HaloRuntimeService::class.java)) }
        shell("am start -W -n com.ezral.halo.debug.test/com.ezral.halo.MarketingBackdropActivity")
        val completionRecording=inst.uiAutomation.executeShellCommand("screenrecord --bit-rate 10000000 --time-limit 7 /sdcard/Download/halo-qa/28-completion-reveal.mp4")
        waitFor { window("Halo completion") }
        SystemClock.sleep(550);shot("29-completion-full-page")
        assertNotNull("Completion includes timer and final sequence step",node("Pour-over · Slow pour"))
        waitFor { !window("Halo completion") }
        assertEquals(Status.COMPLETED,c.state.value.tracks[0].session?.status)
        SystemClock.sleep(500);assertFalse("Completed session must not trigger again",window("Halo completion"))
        ParcelFileDescriptor.AutoCloseInputStream(completionRecording).use { it.readBytes() }
    }
}
