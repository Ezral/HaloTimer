package com.ezral.halo.runtime

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ezral.halo.MainActivity
import com.ezral.halo.R
import com.ezral.halo.core.*

class HaloNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    init {
        listOf("runtime" to "Running timers", "completed" to "Timer completions").forEach { (id, title) ->
            manager.createNotificationChannel(NotificationChannel(id, title, NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null); enableVibration(false); lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            })
        }
    }
    private fun open() = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun action(name: String) = PendingIntent.getService(context, name.hashCode(),
        Intent(context, HaloRuntimeService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun generic() = NotificationCompat.Builder(context, "runtime").setSmallIcon(R.drawable.ic_halo).setContentTitle("Halo timer").build()
    fun ongoing(state: Snapshot): Notification {
        val active = state.tracks.filter { it.session != null && it.definition.active }
        val paused = active.none { it.session?.status == Status.RUNNING }
        return NotificationCompat.Builder(context, "runtime").setSmallIcon(R.drawable.ic_halo)
            .setContentTitle("Halo · ${active.size} timer${if (active.size == 1) "" else "s"}")
            .setContentText(active.joinToString(" · ") { "${it.definition.name}: ${it.session?.status?.name?.lowercase()}" })
            .setContentIntent(open()).setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(generic())
            .addAction(0, if (paused) "Resume" else "Pause", action(if (paused) "resume" else "pause"))
            .addAction(0, "Show controls", action("show"))
            .addAction(0, "Stop all", action("stop"))
            .build()
    }
    fun completed(track: Track) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        manager.notify(100 + track.definition.id, NotificationCompat.Builder(context, "completed").setSmallIcon(R.drawable.ic_halo)
            .setContentTitle("${track.definition.name} finished").setContentText("Open Halo to reset or start again")
            .setContentIntent(open()).setAutoCancel(true).setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(generic()).build())
    }
    fun cancelCompletion(id: Int) = manager.cancel(100 + id)
    fun cancelAllCompletions() { (0..2).forEach(::cancelCompletion) }
}
