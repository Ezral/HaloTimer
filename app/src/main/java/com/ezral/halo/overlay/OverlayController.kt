package com.ezral.halo.overlay

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.*
import android.widget.*
import com.ezral.halo.MainActivity
import com.ezral.halo.R
import com.ezral.halo.core.*
import com.ezral.halo.data.Preferences
import com.ezral.halo.runtime.TimerCoordinator
import kotlin.math.abs
import kotlinx.coroutines.launch

class OverlayController(private val context: Context, private val c: TimerCoordinator) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private var edge: EdgeView? = null
    private data class Control(val dialog: Dialog, val view: View, val dock: DockSide, val info: TextView? = null, val play: TextView? = null, var blur: Boolean? = null, var color: Long? = null)
    private val controls = mutableMapOf<Int, Control>()
    private var previewId = 0
    private var previewUntil = 0L
    private var lastSize = ""
    private fun dp(v: Int) = (v * density).toInt()
    fun preview(id: Int) { previewId = id; previewUntil = SystemClock.elapsedRealtime() + 5_000 }
    fun previewing() = previewUntil > SystemClock.elapsedRealtime()
    fun render(state: Snapshot, preferences: Preferences) {
        if (!Settings.canDrawOverlays(context)) { removeAll(); return }
        val now = SystemClock.elapsedRealtime()
        val size = "${context.resources.displayMetrics.widthPixels}:${context.resources.displayMetrics.heightPixels}:${context.resources.configuration.orientation}"
        if (size != lastSize) { removeAll(); lastSize = size }
        val tracks = state.tracks.filter { it.definition.active && it.session?.let { s -> s.status == Status.RUNNING || s.status == Status.PAUSED || s.visualUntilMs > now } == true }.toMutableList()
        if (previewing()) {
            val t = state.tracks[previewId]
            tracks.removeAll { it.definition.id == previewId }
            tracks += t.copy(session = Session("preview", listOf(Step()), deadlineMs = now, status = Status.COMPLETED, visualUntilMs = previewUntil, alertStartedAtMs = previewUntil - 5_000))
            tracks.sortBy { it.definition.id }
        }
        if (tracks.isEmpty()) { removeAll(); return }
        try {
            if (edge == null) { edge = EdgeView(context); wm.addView(edge, EdgeWindowLayout.create(context)) }
            // Canvas alerts follow Halo's explicit setting; system transition scales are independent.
            val reduce = preferences.reducedMotion
            edge?.apply { this.tracks = tracks; reducedMotion = reduce; invalidate() }
            val visible = if (OverlayVisibility.menuVisible) emptyMap() else tracks.filterNot { it.definition.hidden }.associateBy { it.definition.id }
            controls.keys.toList().filter { it !in visible || controls[it]?.dock != visible[it]?.definition?.dock }.forEach { controls.remove(it)?.dialog?.dismiss() }
            visible.forEach { (id, t) ->
                val control = controls.getOrPut(id) { createControl(t) }
                // Capability can change at runtime (for example battery saver); refresh on service ticks.
                val blur = control.dock == DockSide.NONE && Build.VERSION.SDK_INT >= 31 && wm.isCrossWindowBlurEnabled
                if (control.blur != blur || control.color != t.definition.color) {
                    control.blur = blur
                    control.color = t.definition.color
                    val color = visible[id]?.definition?.color?.toInt() ?: 0xFFFFFFFF.toInt()
                    val glass = GlassBackground(intArrayOf(
                        blend(color, Color.WHITE, if (blur) .70f else .78f),
                        blend(color, Color.WHITE, if (blur) .48f else .62f),
                    ), dp(if (control.dock == DockSide.NONE) 28 else 48).toFloat(), if (control.dock == DockSide.NONE) 0 else dp(24))
                    control.dialog.window!!.apply {
                        setBackgroundDrawable(if (control.dock == DockSide.NONE) glass else android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
                        decorView.setPadding(0, 0, 0, 0)
                        if (Build.VERSION.SDK_INT >= 31) setBackgroundBlurRadius(if (blur) dp(24).coerceAtMost(100) else 0)
                    }
                }
                val s = t.session!!
                control.info?.apply {
                    text = "${t.overlayLabel()}\n${if (s.status == Status.COMPLETED) "Done" else formatTime(s.remaining(now))}"
                    setTextColor(t.definition.color.toInt())
                    setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = 16f
                    contentDescription = "${t.definition.name}, ${formatTime(s.remaining(now))}. Drag to an edge to dock."
                }
                control.play?.apply {
                    text = if (s.status == Status.RUNNING) "Ⅱ" else "▶"
                    contentDescription = "${if (s.status == Status.RUNNING) "Pause" else "Play"} ${t.definition.name}"
                }
                (control.view as? DockedTimerView)?.apply {
                    track = t; reducedMotion = reduce
                    contentDescription = "${t.definition.name}, ${formatTime(s.remaining(now))}. Tap or drag inward to expand. Long press for settings."
                    invalidate()
                }
            }
        } catch (_: SecurityException) { removeAll() }
        catch (_: WindowManager.BadTokenException) { removeAll() }
    }
    private fun settings(id: Int) = context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("track", id))
    private fun toggle(id: Int) {
        val status = c.state.value.tracks[id].session?.status
        if (status == Status.COMPLETED || status == Status.INTERRUPTED) c.scope.launch {
            c.execute(Command.Rewind(id)); c.execute(Command.Start(setOf(id)))
        } else c.submit(if (status == Status.RUNNING) Command.Pause(id) else Command.Start(setOf(id)))
    }
    private fun createControl(track: Track): Control {
        val id = track.definition.id; val dock = track.definition.dock
        val dialog = Dialog(context, R.style.Theme_Halo_Glass)
        val window = dialog.window!!
        window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0f); window.decorView.setPadding(0, 0, 0, 0)
        dialog.setCancelable(false); dialog.setCanceledOnTouchOutside(false)
        var info: TextView? = null; var play: TextView? = null
        val root: View
        if (dock != DockSide.NONE) {
            root = DockedTimerView(context).apply { this.track = track; isClickable = true; isFocusable = true }
            dialog.setContentView(root, ViewGroup.LayoutParams(dp(144), dp(144)))
        } else {
            root = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(4), dp(4), dp(4))
            }
            info = TextView(context).apply {
                textSize = 13f; typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(48); setPadding(dp(4), 0, dp(8), 0); setTextColor(0xFF222632.toInt()); isClickable = true
            }
            val infoWidth = (context.resources.displayMetrics.widthPixels - dp(216)).coerceIn(dp(64), dp(132))
            root.addView(info, LinearLayout.LayoutParams(infoWidth, LinearLayout.LayoutParams.WRAP_CONTENT))
            fun button(label: String, description: String, action: () -> Unit): TextView {
                val button = TextView(context).apply {
                    text = label; textSize = 22f; gravity = Gravity.CENTER; contentDescription = description
                    setTextColor(0xFF303644.toInt()); isFocusable = true; setOnClickListener { action() }
                }
                root.addView(button, LinearLayout.LayoutParams(dp(48), dp(48))); return button
            }
            play = button("Ⅱ", "Pause ${track.definition.name}") { toggle(id) }
            button("↺", "Reset ${track.definition.name}") { c.submit(Command.Rewind(id)) }
            button("⋮", "Open Halo settings") { settings(id) }
            button("×", "Hide or dismiss ${track.definition.name}") {
                c.submit(if (c.state.value.tracks[id].session?.status == Status.COMPLETED) Command.Reset(id) else Command.Hide(id, true))
            }
            info.setOnClickListener { toggle(id) }
            dialog.setContentView(root)
        }
        root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val width = if (dock == DockSide.NONE) root.measuredWidth else dp(144)
        val height = if (dock == DockSide.NONE) root.measuredHeight.coerceAtLeast(dp(56)) else dp(144)
        val screen = context.resources.displayMetrics
        val maxX = (screen.widthPixels - width).coerceAtLeast(0)
        val maxY = (screen.heightPixels - height - dp(56)).coerceAtLeast(0)
        val p = window.attributes.apply {
            gravity = Gravity.TOP or Gravity.LEFT
            this.width = width; this.height = if (dock == DockSide.NONE) WindowManager.LayoutParams.WRAP_CONTENT else height
            x = when (dock) { DockSide.LEFT -> -width / 2; DockSide.RIGHT -> screen.widthPixels - width / 2; else -> (maxX * track.definition.x).toInt() }
            y = (maxY * track.definition.y).toInt(); title = "Halo timer ${track.definition.name}"
        }
        window.attributes = p
        fun expand(x: Float = if (dock == DockSide.LEFT) 0.05f else 0.95f) = c.submit(Command.Move(id, x.coerceIn(0f, 1f), p.y.toFloat() / maxY.coerceAtLeast(1), DockSide.NONE))
        if (dock != DockSide.NONE) {
            root.setOnClickListener { expand() }
            root.setOnLongClickListener { settings(id); true }
        }
        val handle = info ?: root
        var downX = 0f; var downY = 0f; var initialX = 0; var initialY = 0; var dragged = false; var downAt = 0L
        handle.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; initialX = p.x; initialY = p.y; dragged = false; downAt = event.eventTime; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (abs(dx) + abs(dy) > dp(8)) dragged = true
                    if (dragged) {
                        p.x = (initialX + dx.toInt()).coerceIn(if (dock == DockSide.NONE) 0 else -width / 2, if (dock == DockSide.NONE) maxX else screen.widthPixels - width / 2)
                        p.y = (initialY + dy.toInt()).coerceIn(0, maxY)
                        window.attributes = p
                    }; true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragged) {
                        if (dock != DockSide.NONE) {
                            val inward = if (dock == DockSide.LEFT) event.rawX - downX else downX - event.rawX
                            if (inward > dp(32)) expand(event.rawX / screen.widthPixels)
                            else { p.x = initialX; window.attributes = p; c.submit(Command.Move(id, track.definition.x, p.y.toFloat() / maxY.coerceAtLeast(1), dock)) }
                        } else {
                            val side = when { p.x <= dp(16) -> DockSide.LEFT; p.x >= maxX - dp(16) -> DockSide.RIGHT; else -> DockSide.NONE }
                            c.submit(Command.Move(id, p.x.toFloat() / maxX.coerceAtLeast(1), p.y.toFloat() / maxY.coerceAtLeast(1), side))
                        }
                    } else if (dock != DockSide.NONE && event.eventTime - downAt >= ViewConfiguration.getLongPressTimeout()) v.performLongClick()
                    else v.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> { p.x = initialX; p.y = initialY; window.attributes = p; true }
                else -> false
            }
        }
        dialog.show()
        // The window remains non-modal and only its compact bounds receive touches.
        return Control(dialog, root, dock, info, play)
    }
    fun hideControls() {
        controls.values.forEach { runCatching { it.dialog.dismiss() } }; controls.clear()
    }
    fun removeAll() {
        edge?.let { runCatching { wm.removeView(it) } }; edge = null
        hideControls()
    }

    private fun blend(from: Int, to: Int, amount: Float): Int {
        fun channel(a: Int, b: Int) = (a + (b - a) * amount).toInt().coerceIn(0, 255)
        return Color.argb(235, channel(Color.red(from), Color.red(to)),
            channel(Color.green(from), Color.green(to)), channel(Color.blue(from), Color.blue(to)))
    }
}
