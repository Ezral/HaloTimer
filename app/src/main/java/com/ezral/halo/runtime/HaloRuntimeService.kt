package com.ezral.halo.runtime

import android.app.Service
import android.content.*
import android.os.*
import androidx.core.content.ContextCompat
import com.ezral.halo.HaloApplication
import com.ezral.halo.core.*
import com.ezral.halo.overlay.OverlayController
import kotlinx.coroutines.*

class HaloRuntimeService : Service() {
    private val c get() = (application as HaloApplication).coordinator
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var overlay: OverlayController
    private var loop: Job? = null
    private var screenOn = true
    private val displayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn = intent.action != Intent.ACTION_SCREEN_OFF
            if (!screenOn) overlay.removeAll()
        }
    }
    override fun onCreate() {
        super.onCreate()
        overlay = OverlayController(this, c)
        screenOn = getSystemService(PowerManager::class.java).isInteractive
        ContextCompat.registerReceiver(this, displayReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, c.notifications.ongoing(c.state.value))
        scope.launch {
            c.initialize()
            when (intent?.action) {
                "stop" -> { c.execute(Command.StopAll); overlay.removeAll(); stopSelf(); return@launch }
                "pause" -> c.state.value.tracks.forEach { c.execute(Command.Pause(it.definition.id)) }
                "resume" -> c.execute(Command.Start((0..2).toSet()))
                "show" -> c.execute(Command.ShowAll)
                "launch" -> {
                    val id = intent.getIntExtra("track", -1)
                    c.execute(Command.Start(if (id == -1) (0..2).toSet() else setOf(id)))
                }
                "preview" -> overlay.preview(intent.getIntExtra("track", 0))
            }
            if (loop?.isActive != true) loop = scope.launch {
                var lastNotice = 0L
                while (isActive) {
                    c.execute(Command.Tick)
                    val state = c.state.value
                    val now = SystemClock.elapsedRealtime()
                    if (screenOn) overlay.render(state, c.prefs.value)
                    if (now - lastNotice >= 1_000) {
                        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this@HaloRuntimeService, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            getSystemService(android.app.NotificationManager::class.java).notify(1, c.notifications.ongoing(state))
                        }
                        lastNotice = now
                    }
                    val work = state.tracks.any { it.session?.let { s -> s.status == Status.RUNNING || s.status == Status.PAUSED || s.visualUntilMs > now } == true }
                    if (!work && !overlay.previewing()) { stopSelf(); break }
                    delay(if (screenOn && state.tracks.any { it.session?.status == Status.RUNNING }) 200 else 1_000)
                }
            }
        }
        return START_NOT_STICKY // No blind overlay resurrection after a user/system stop.
    }
    override fun onDestroy() {
        scope.cancel(); overlay.removeAll(); unregisterReceiver(displayReceiver)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
