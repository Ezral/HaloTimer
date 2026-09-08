package com.ezral.halo.runtime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.ezral.halo.HaloApplication
import com.ezral.halo.core.*
import kotlinx.coroutines.launch

class AlarmScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)
    fun exactAvailable() = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
    private fun intent(id: Int): PendingIntent = PendingIntent.getBroadcast(context, id,
        Intent(context, AlarmReceiver::class.java).setData(Uri.parse("halo://boundary/$id")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun reconcile(state: Snapshot) {
        state.tracks.forEach { t ->
            val pending = intent(t.definition.id)
            manager.cancel(pending)
            val s = t.session ?: return@forEach
            if (s.status != Status.RUNNING) return@forEach
            try {
                if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, s.deadlineMs, pending)
                else manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, s.deadlineMs, pending)
            } catch (_: SecurityException) {
                manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, s.deadlineMs, pending)
            }
        }
    }
    fun cancelAll() { (0..2).forEach { manager.cancel(intent(it)) } }
}

/** A stale callback only reconciles current durable deadlines; it cannot target an old run. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val coordinator = (context.applicationContext as HaloApplication).coordinator
        coordinator.scope.launch {
            try { coordinator.execute(Command.Tick) } finally { pending.finish() }
        }
    }
}
class RecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val c = (context.applicationContext as HaloApplication).coordinator
        c.scope.launch {
            try { c.initialize(); c.alarmScheduler.reconcile(c.state.value) } finally { pending.finish() }
        }
    }
}
