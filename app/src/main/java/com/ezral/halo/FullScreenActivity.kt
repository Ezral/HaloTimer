package com.ezral.halo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.ezral.halo.core.*
import com.ezral.halo.overlay.OverlayVisibility
import com.ezral.halo.runtime.HaloRuntimeService
import com.ezral.halo.ui.FullScreenTimers
import kotlinx.coroutines.launch

class FullScreenActivity : ComponentActivity() {
    private val c get() = (application as HaloApplication).coordinator
    private var pendingIds: Set<Int>? = null
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingIds?.let { ids -> pendingIds = null; startTimers(ids) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30)
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideSystemBars()
        setContent { FullScreenTimers(c, onSettings = { finish() }, onOverlay = {
            if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            else moveTaskToBack(true)
        }, onStart = ::requestStart) }
        lifecycleScope.launch { c.initialize() }
    }
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }
    override fun onStart() { super.onStart(); OverlayVisibility.fullScreenShown(this) }
    override fun onStop() { OverlayVisibility.fullScreenHidden(this); super.onStop() }
    private fun requestStart(ids: Set<Int>) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingIds = ids; notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startTimers(ids)
    }
    private fun startTimers(ids: Set<Int>) { lifecycleScope.launch {
        ids.forEach { id -> if (c.state.value.tracks[id].session?.status in listOf(Status.COMPLETED, Status.INTERRUPTED)) c.execute(Command.Reset(id)) }
        c.execute(Command.Start(ids))
        if (ids.none { c.state.value.tracks[it].session?.status == Status.RUNNING }) return@launch
        try { ContextCompat.startForegroundService(this@FullScreenActivity, Intent(this@FullScreenActivity, HaloRuntimeService::class.java).setAction("display")) }
        catch (_: RuntimeException) { c.error.value = "Android could not start Halo. Keep the app open and try again." }
    } }
}
