package com.ezral.halo.overlay

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.input.InputManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.*
import android.widget.*
import com.ezral.halo.MainActivity
import com.ezral.halo.core.*
import com.ezral.halo.data.Preferences
import com.ezral.halo.runtime.TimerCoordinator
import kotlin.math.abs

class OverlayController(private val context: Context, private val c: TimerCoordinator) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private var edge: EdgeView? = null
    private val controls = mutableMapOf<Int, LinearLayout>()
    private var previewId = 0
    private var previewUntil = 0L
    private var lastSize = ""
    private fun dp(v: Int) = (v * density).toInt()
    fun preview(id: Int) { previewId = id; previewUntil = SystemClock.elapsedRealtime() + 5_000 }
    fun previewing() = previewUntil > SystemClock.elapsedRealtime()
    private fun params(w: Int, h: Int, decorative: Boolean) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            (if (decorative) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0), PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        if (decorative) {
            val maximum = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch else 0.8f
            alpha = minOf(0.75f, maximum) // Only one full-screen SAW window; never change system policy.
        }
    }
    fun render(state: Snapshot, preferences: Preferences) {
        if (!Settings.canDrawOverlays(context)) { removeAll(); return }
        val now = SystemClock.elapsedRealtime()
        val size = "${context.resources.displayMetrics.widthPixels}:${context.resources.displayMetrics.heightPixels}:${context.resources.configuration.orientation}"
        if (size != lastSize) { removeAll(); lastSize = size }
        val tracks = state.tracks.filter { it.definition.active && it.session?.let { s -> s.status == Status.RUNNING || s.status == Status.PAUSED || s.visualUntilMs > now } == true }.toMutableList()
        if (previewing()) {
            val t = state.tracks[previewId]
            tracks.removeAll { it.definition.id == previewId }
            tracks += t.copy(session = Session("preview", listOf(Step()), deadlineMs = now, status = Status.COMPLETED, visualUntilMs = previewUntil))
            tracks.sortBy { it.definition.id }
        }
        if (tracks.isEmpty()) { removeAll(); return }
        try {
            if (edge == null) {
                edge = EdgeView(context)
                wm.addView(edge, params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, true))
            }
            edge?.apply { this.tracks = tracks; reducedMotion = preferences.reducedMotion || !android.animation.ValueAnimator.areAnimatorsEnabled(); invalidate() }
            val visible = tracks.filterNot { it.definition.hidden }.map { it.definition.id }.toSet()
            controls.keys.toList().filterNot { it in visible }.forEach { id -> controls.remove(id)?.let { wm.removeView(it) } }
            tracks.filter { it.definition.id in visible }.forEach { t ->
                val view = controls.getOrPut(t.definition.id) { createControl(t) }
                val dark = preferences.theme == "Dark" || (preferences.theme == "System" && context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
                view.background = GradientDrawable().apply { setColor(if (dark) 0xDC20212C.toInt() else 0xE8F5F4FC.toInt()); cornerRadius = dp(28).toFloat() }
                val info = view.getChildAt(0) as TextView
                val s = t.session!!
                info.text = "${t.definition.name}\n${if (s.status == Status.COMPLETED) "Done" else formatTime(s.remaining(now))}${if (s.steps.size > 1) " · ${s.index + 1}/${s.steps.size}" else ""}"
                info.contentDescription = "${t.definition.name}, ${s.steps[s.index].name}, ${s.status.name.lowercase()}, ${formatTime(s.remaining(now))}. Tap to pause or resume. Drag to move."
                info.setTextColor(t.definition.color.toInt().let { if (dark) it else Color.rgb(Color.red(it) / 2, Color.green(it) / 2, Color.blue(it) / 2) })
                for (i in 1 until view.childCount) (view.getChildAt(i) as TextView).setTextColor(if (dark) Color.WHITE else Color.BLACK)
            }
        } catch (_: SecurityException) { removeAll() }
        catch (_: WindowManager.BadTokenException) { removeAll() }
    }
    private fun createControl(track: Track): LinearLayout {
        val id = track.definition.id
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(4), dp(4)); elevation = dp(8).toFloat()
        }
        val info = TextView(context).apply {
            textSize = 13f; typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(48); maxWidth = dp(132); setPadding(dp(4), 0, dp(8), 0)
            isClickable = true
        }
        root.addView(info, LinearLayout.LayoutParams(dp(132), dp(60)))
        fun button(label: String, description: String, action: () -> Unit) {
            root.addView(TextView(context).apply {
                text = label; textSize = 22f; gravity = Gravity.CENTER; contentDescription = description
                isFocusable = true; setOnClickListener { action() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        button("−", "Subtract thirty seconds from ${track.definition.name}") { c.submit(Command.Adjust(c.target(id), -30_000)) }
        button("+", "Add thirty seconds to ${track.definition.name}") { c.submit(Command.Adjust(c.target(id), 30_000)) }
        button("⋮", "Open Halo settings") { context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("track", id)) }
        button("×", "Hide ${track.definition.name} control") { c.submit(Command.Hide(id, true)) }
        // Wrap-content window contains only actual controls; never a full-display touch pane.
        val p = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, false)
        root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val screen = context.resources.displayMetrics
        val maxX = (screen.widthPixels - root.measuredWidth).coerceAtLeast(0)
        val maxY = (screen.heightPixels - root.measuredHeight - dp(56)).coerceAtLeast(0)
        p.x = (maxX * track.definition.x).toInt(); p.y = (maxY * track.definition.y).toInt()
        var downX = 0f; var downY = 0f; var initialX = 0; var initialY = 0; var dragged = false
        info.setOnClickListener {
            val status = c.state.value.tracks[id].session?.status
            c.submit(when (status) { Status.RUNNING -> Command.Pause(id); Status.COMPLETED -> Command.Reset(id); else -> Command.Start(setOf(id)) })
        }
        info.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; initialX = p.x; initialY = p.y; dragged = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (abs(dx) + abs(dy) > dp(8)) dragged = true
                    if (dragged) {
                        p.x = (initialX + dx.toInt()).coerceIn(0, maxX); p.y = (initialY + dy.toInt()).coerceIn(0, maxY)
                        runCatching { wm.updateViewLayout(root, p) }
                    }; true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragged) c.submit(Command.Move(id, p.x.toFloat() / maxX.coerceAtLeast(1), p.y.toFloat() / maxY.coerceAtLeast(1))) else v.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        wm.addView(root, p)
        return root
    }
    fun removeAll() {
        edge?.let { runCatching { wm.removeView(it) } }; edge = null
        controls.values.forEach { runCatching { wm.removeView(it) } }; controls.clear()
    }
}
