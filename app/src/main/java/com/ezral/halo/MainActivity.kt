package com.ezral.halo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.ezral.halo.core.*
import com.ezral.halo.runtime.HaloRuntimeService
import com.ezral.halo.overlay.OverlayVisibility
import com.ezral.halo.ui.HaloScreen
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private val c get() = (application as HaloApplication).coordinator
    private var selected by androidx.compose.runtime.mutableIntStateOf(0)
    private var selectedStep = 0
    private val handler = Handler(Looper.getMainLooper())
    private var heldKey: Int? = null
    private var target: AdjustmentTarget? = null
    private var downAt = 0L
    private var exhausted = false
    private var pendingLaunch: Pair<String, Int>? = null
    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingLaunch?.let { (action, id) -> pendingLaunch = null; startRuntime(action, id) }
    }
    private val repeat = object : Runnable {
        override fun run() {
            val held = SystemClock.elapsedRealtime() - downAt
            val frozen = target ?: return
            if (held >= 15_000 || !c.prefs.value.volume || c.target(frozen.track, selectedStep) != frozen || !c.state.value.tracks[frozen.track].definition.active) {
                exhausted = true; return
            }
            c.submit(Command.Adjust(frozen, holdStep(held) * if (heldKey == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1))
            handler.postDelayed(this, 600)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        selected = savedInstanceState?.getInt("selected") ?: intent.getIntExtra("track", 0)
        setContent {
            HaloScreen(c, selected,
                onSelect = { id, step -> cancelHold(); selected = id; selectedStep = step },
                onLaunch = { id -> launchRuntime("launch", id) },
                onPreview = { id -> launchRuntime("preview", id) },
                onOverlayPermission = { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) },
                onAlarmPermission = { if (Build.VERSION.SDK_INT >= 31) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) },
                onNotificationPermission = { if (Build.VERSION.SDK_INT >= 33) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) })
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent);setIntent(intent);cancelHold()
        selected=intent.getIntExtra("track",selected).coerceIn(0,2);selectedStep=0
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("selected", selected); super.onSaveInstanceState(outState) }
    private var menuEntry: Job? = null
    override fun onStart() {
        super.onStart()
        val entering = !OverlayVisibility.menuVisible
        OverlayVisibility.shown(this)
        if (entering && !isChangingConfigurations) menuEntry = lifecycleScope.launch {
            // Read the persisted preference, not the state-flow's initial default on cold start.
            if (c.preferences.flow.first().dismissAllOnMenu) c.execute(Command.StopAll)
        }
    }
    override fun onStop() { menuEntry?.cancel(); OverlayVisibility.hidden(this); super.onStop() }
    override fun onResume() { super.onResume(); c.scope.launch { c.initialize(); c.alarmScheduler.reconcile(c.state.value) } }
    override fun onPause() { cancelHold(); super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (!hasFocus) cancelHold() }
    private fun launchRuntime(action: String, id: Int) {
        cancelHold()
        if (action == "launch" && id >= -1 && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingLaunch = action to id
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startRuntime(action, id)
    }
    private fun startRuntime(action: String, id: Int) {
        c.scope.launch {
            val leaveSettings = action == "launch" && id >= -1
            if (leaveSettings) {
                val ids = if (id == -1) (0..2).toSet() else setOf(id)
                c.execute(Command.Start(ids))
                // Do not leave the editor when all requested starts failed validation.
                if (ids.none { c.state.value.tracks[it].session?.status == Status.RUNNING }) return@launch
            }
            try {
                ContextCompat.startForegroundService(this@MainActivity,
                    Intent(this@MainActivity, HaloRuntimeService::class.java).setAction(action).putExtra("track", id))
                // Reveal the previous task (or home) without guessing which app was last used.
                if (leaveSettings) moveTaskToBack(true)
            } catch (_: RuntimeException) {
                c.error.value = "Android could not start Halo. Keep the app open and try again."
            }
        }
    }
    private fun cancelHold() { handler.removeCallbacks(repeat); heldKey = null; target = null; exhausted = false }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = handleVolume(event) || super.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = handleVolume(event) || super.onKeyUp(keyCode, event)
    private fun handleVolume(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (event.action == KeyEvent.ACTION_UP && heldKey == event.keyCode) { cancelHold(); return true }
        if (!c.prefs.value.volume || !c.ready.value) { cancelHold(); return false }
        val track = c.state.value.tracks[selected]
        if (!track.definition.active || track.session?.status in listOf(Status.COMPLETED, Status.INTERRUPTED)) return false
        if (event.isCanceled) { cancelHold(); return false }
        if (heldKey != null && heldKey != event.keyCode) { cancelHold(); return false }
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0 && heldKey == null) {
                heldKey = event.keyCode; target = c.target(selected, selectedStep); downAt = SystemClock.elapsedRealtime(); exhausted = false
                c.submit(Command.Adjust(target!!, if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 30_000 else -30_000))
                handler.postDelayed(repeat, 600)
            }
            return heldKey == event.keyCode || exhausted
        }
        return false
    }
}
