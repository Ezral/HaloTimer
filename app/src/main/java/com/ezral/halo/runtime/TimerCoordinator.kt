package com.ezral.halo.runtime

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.ezral.halo.core.*
import com.ezral.halo.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The only writer; persistence precedes alarms, UI state and physical effects. */
class TimerCoordinator(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val engine = TimerEngine()
    private val store = HaloStore(context)
    val preferences = PreferenceStore(context)
    val prefs = preferences.flow.stateIn(scope, SharingStarted.Eagerly, Preferences())
    private val mutableState = MutableStateFlow(Snapshot())
    val state = mutableState.asStateFlow()
    val ready = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val alarmScheduler = AlarmScheduler(context)
    val haptics = HapticQueue(context, scope)
    val notifications = HaloNotifications(context)
    private var loaded = false
    private var storageFailed = false

    private suspend fun ensureLoaded() {
        if (loaded) return
        try {
            val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
            val stored = store.load() ?: Snapshot(boot = boot)
            val stopped = if (Build.VERSION.SDK_INT >= 30) {
                val exit = context.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(null, 0, 1).firstOrNull()
                exit?.reason == ApplicationExitInfo.REASON_USER_REQUESTED
            } else false
            val recovered = engine.recover(stored, boot, SystemClock.elapsedRealtime(), stopped || boot == -1)
            store.save(recovered)
            mutableState.value = recovered
            loaded = true
            ready.value = true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            storageFailed = true
            error.value = "Timer storage could not be opened. Restart Halo; your saved data has been kept."
        }
    }
    fun submit(command: Command) { scope.launch { execute(command) } }
    suspend fun execute(command: Command) = mutex.withLock {
        ensureLoaded()
        if (!loaded || storageFailed) return@withLock
        try {
            val now = SystemClock.elapsedRealtime()
            val old = mutableState.value
            val result = engine.apply(old, command, now)
            if (result.error != null) error.value = result.error
            var next = result.snapshot
            if (next != old) store.save(next)
            mutableState.value = next
            // Reconcile on every semantic command; Tick only needs a reschedule on changes.
            if (next != old || command != Command.Tick) alarmScheduler.reconcile(next)
            old.tracks.zip(next.tracks).forEach { (before, after) ->
                if (before.session?.id != after.session?.id || before.session?.index != after.session?.index ||
                    !after.definition.active || after.definition.haptic == HapticStyle.OFF || after.session?.status == Status.PAUSED) {
                    haptics.cancel(after.definition.id)
                }
            }
            when (command) {
                is Command.Rewind -> { haptics.cancel(command.id); notifications.cancelCompletion(command.id) }
                is Command.Reset -> { haptics.cancel(command.id); notifications.cancelCompletion(command.id) }
                Command.StopAll -> { haptics.cancelAll(); notifications.cancelAllCompletions() }
                else -> Unit
            }
            if (next.outbox.isNotEmpty()) {
                val events = next.outbox
                // At-most-once attempt: persist consumption before hardware output. See ADR-007.
                next = next.copy(outbox = emptyList())
                store.save(next)
                mutableState.value = next
                events.forEach { event ->
                    if (event.final) notifications.completed(next.tracks[event.track])
                    haptics.enqueue(event)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            storageFailed = true
            haptics.cancelAll()
            alarmScheduler.cancelAll()
            error.value = "Timer update could not be saved. Timing is interrupted; reopen Halo to recover."
            mutableState.value = mutableState.value.copy(tracks = mutableState.value.tracks.map { t ->
                t.copy(session = t.session?.copy(status = Status.INTERRUPTED, visualUntilMs = 0))
            })
        }
    }
    suspend fun initialize() { execute(Command.Tick) }
    fun target(id: Int, selectedStep: Int = 0): AdjustmentTarget {
        val s = state.value.tracks[id].session
        return AdjustmentTarget(id, s?.id, s?.index ?: selectedStep)
    }
}
