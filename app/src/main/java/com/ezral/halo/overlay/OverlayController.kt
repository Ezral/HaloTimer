package com.ezral.halo.overlay

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.PixelFormat
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
    private var actionMenu: DockActionMenu? = null
    private var actionMenuTrack: Int? = null
    private var actionMenuX = 0
    private var actionMenuY = 0
    private val exitingMenus = mutableSetOf<DockActionMenu>()
    private fun closeActionMenu(animated: Boolean = false, after: (() -> Unit)? = null) {
        val menu=actionMenu
        actionMenu=null; actionMenuTrack=null
        if(menu!=null) {
            if(animated) {
                exitingMenus+=menu
                menu.close { val valid=exitingMenus.remove(menu); runCatching { wm.removeView(menu) }; if(valid) after?.invoke() }
            } else { runCatching { wm.removeView(menu) }; after?.invoke() }
        } else after?.invoke()
    }
    private fun moveControl(command: Command.Move) {
        c.scope.launch { c.execute(command); render(c.state.value,c.prefs.value) }
    }
    private fun openActionMenu(id: Int, dock: DockSide, centerY: Int) {
        closeActionMenu()
        val screen = context.resources.displayMetrics
        val menu = DockActionMenu(context, dock, c.state.value.tracks[id].definition.color.toInt(), c.prefs.value.reducedMotion, c.state.value.tracks[id].session?.status ?: Status.READY)
        actionMenuX = if (dock == DockSide.LEFT) 0 else screen.widthPixels - dp(168)
        actionMenuY = (centerY - dp(144)).coerceIn(0, (screen.heightPixels - dp(288)).coerceAtLeast(0))
        val params = WindowManager.LayoutParams(dp(168), dp(288), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, android.graphics.PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT; x = actionMenuX; y = actionMenuY; alpha = 1f
            title = "Halo dock actions"
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        }
        try { wm.addView(menu, params); actionMenu = menu; actionMenuTrack = id }
        catch (_: WindowManager.BadTokenException) { closeActionMenu() }
        catch (_: SecurityException) { closeActionMenu() }
    }
    private fun selectAction(rawX: Float, rawY: Float) {
        actionMenu?.let { menu ->
            val location = IntArray(2); menu.getLocationOnScreen(location)
            menu.select(rawX - location[0], rawY - location[1])
        }
    }
    private fun chooseAction(id: Int, action: DockActionMenu.Action?) {
        closeActionMenu(animated=true) {
            when(action) {
                DockActionMenu.Action.PRIMARY -> toggle(id)
                DockActionMenu.Action.RESET -> c.submit(Command.Rewind(id))
                DockActionMenu.Action.STOP -> stop(id)
                null -> Unit
            }
        }
    }
    private data class Control(val dialog: Dialog, val view: View, val dock: DockSide, val info: TextView? = null, val play: TextView? = null, var color: Long? = null)
    private data class Morph(val view: DockMorphView, val target: Control)
    private val morphs = mutableMapOf<Int, Morph>()
    private val retiring = mutableSetOf<Control>()
    private fun finishMorph(id: Int) {
        morphs.remove(id)?.let { morph ->
            runCatching { wm.removeView(morph.view) }
            reveal(morph.target)
        }
    }
    private fun reveal(control: Control) {
        control.dialog.window?.let { window ->
            window.attributes = window.attributes.apply { alpha = 1f; flags = flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv() }
        }
    }
    private fun shape(control: Control): MorphShape {
        val location = IntArray(2); control.view.getLocationOnScreen(location)
        val bounds = RectF(location[0].toFloat(), location[1].toFloat(),
            (location[0] + control.view.width).toFloat(), (location[1] + control.view.height).toFloat())
        if (control.dock != DockSide.NONE) {
            val shape=(control.view as DockedTimerView).currentShape()
            return shape.copy(bounds=RectF(shape.bounds).apply { offset(location[0].toFloat(),location[1].toFloat()) },
                text=PointF(shape.text.x+location[0],shape.text.y+location[1]),
                contour=shape.contour?.mapIndexed { index, value -> value+location[index%2] }?.toFloatArray())
        }
        (control.dialog.window?.decorView?.background as? GlassBackground)?.let { bg ->
            bounds.left+=dp(4)*(if(bg.side==DockSide.LEFT) 1-bg.approach else 1f)
            bounds.right-=dp(4)*(if(bg.side==DockSide.RIGHT) 1-bg.approach else 1f)
        }
        val text = control.info!!; text.getLocationOnScreen(location)
        return MorphShape(bounds, PointF(location[0] + text.width / 2f, location[1] + text.height / 2f))
    }
    private fun replaceControl(id: Int, old: Control, track: Track, reduce: Boolean): Control {
        finishMorph(id)
        val next = createControl(track)
        if (reduce) { old.dialog.dismiss(); return next }
        retiring += old
        next.dialog.window!!.apply {
            attributes = attributes.apply { alpha = 0f; flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE }
        }
        next.view.post {
            if (controls[id] !== next || !next.view.isAttachedToWindow || OverlayVisibility.menuVisible) {
                old.dialog.dismiss(); retiring -= old; return@post
            }
            if (next.view.width == 0 || old.view.width == 0) {
                old.dialog.dismiss(); retiring -= old; reveal(next); return@post
            }
            val start = shape(old); val end = shape(next)
            val area = RectF(start.bounds).apply { union(end.bounds); inset(-dp(12).toFloat(), -dp(12).toFloat()) }
            val left = kotlin.math.floor(area.left).toInt(); val top = kotlin.math.floor(area.top).toInt()
            fun local(value: MorphShape) = MorphShape(RectF(value.bounds).apply { offset(-left.toFloat(), -top.toFloat()) },
                PointF(value.text.x - left, value.text.y - top), value.side,
                value.contour?.mapIndexed { index, v -> v-if(index%2==0) left else top }?.toFloatArray())
            val view = DockMorphView(context, local(start), local(end), track) { finishMorph(id) }
            val params = WindowManager.LayoutParams(kotlin.math.ceil(area.width()).toInt(), kotlin.math.ceil(area.height()).toInt(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.LEFT; x = left; y = top; alpha = 1f; title = "Halo surface transition"
                if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
            }
            try { wm.addView(view, params); morphs[id] = Morph(view, next) }
            catch (_: WindowManager.BadTokenException) { reveal(next) }
            catch (_: SecurityException) { reveal(next) }
            old.dialog.dismiss(); retiring -= old
        }
        return next
    }
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
            controls.keys.toList().filter { it !in visible }.forEach {
                if (actionMenuTrack == it) closeActionMenu()
                finishMorph(it); controls.remove(it)?.dialog?.dismiss()
            }
            visible.forEach { (id, t) ->
                val previous = controls[id]
                val control = when {
                    previous == null -> createControl(t)
                    previous.dock != t.definition.dock -> {
                        if (actionMenuTrack == id) closeActionMenu()
                        replaceControl(id, previous, t, reduce)
                    }
                    else -> previous
                }
                controls[id] = control
                if (control.color != t.definition.color) {
                    control.color=t.definition.color
                    control.dialog.window!!.apply {
                        setBackgroundDrawable(if(control.dock==DockSide.NONE) GlassBackground(HaloGlass.color(t.definition.color.toInt()),dp(28).toFloat())
                            else android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
                        decorView.setPadding(0,0,0,0); decorView.elevation=0f
                        if(Build.VERSION.SDK_INT>=31) setBackgroundBlurRadius(0)
                    }
                    (control.view as? LinearLayout)?.let { row ->
                        for(i in 0 until row.childCount) (row.getChildAt(i) as? TextView)?.setTextColor(HaloGlass.foreground(t.definition.color.toInt()))
                    }
                }
                val s = t.session!!
                control.info?.apply {
                    text = if (s.status == Status.COMPLETED) "00:00" else formatTime(s.remaining(now))
                    setTextColor(HaloGlass.foreground(t.definition.color.toInt()))
                    typeface = context.resources.getFont(R.font.jetbrains_mono_regular)
                    textSize = 20f
                    contentDescription = "${t.definition.name}, ${formatTime(s.remaining(now))}. Drag to an edge to dock."
                }
                control.play?.apply {
                    text = when(s.status) { Status.RUNNING -> "Ⅱ"; else -> "▶" }
                    contentDescription = "${when(s.status) { Status.RUNNING -> "Pause"; else -> "Play" }} ${t.definition.name}"
                }
                (control.view as? DockedTimerView)?.apply {
                    track = t; reducedMotion = reduce
                    contentDescription = "${t.definition.name}, ${formatTime(s.remaining(now))}. Tap or drag inward to expand. Long press and slide for playback actions."
                    invalidate()
                }
            }
        } catch (_: SecurityException) { removeAll() }
        catch (_: WindowManager.BadTokenException) { removeAll() }
    }
    private fun settings(id: Int) = context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("track", id))
    private fun toggle(id: Int) {
        val status = c.state.value.tracks[id].session?.status
        c.scope.launch {
            if(status==Status.COMPLETED || status==Status.INTERRUPTED) c.execute(Command.Rewind(id))
            c.execute(if(status==Status.RUNNING) Command.Pause(id) else Command.Start(setOf(id)))
        }
    }
    private fun stop(id: Int) {
        if(previewId==id) previewUntil=0
        // Reset is the existing cancellation command: clears session, alarm, alert and haptics.
        c.scope.launch { c.execute(Command.Reset(id)); render(c.state.value,c.prefs.value) }
    }
    private fun createControl(track: Track): Control {
        val id = track.definition.id; val dock = track.definition.dock
        val dialog = Dialog(context, R.style.Theme_Halo_Glass)
        val window = dialog.window!!
        window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0f); window.decorView.setPadding(0, 0, 0, 0)
        dialog.setCancelable(false); dialog.setCanceledOnTouchOutside(false)
        var info: TextView? = null; var play: TextView? = null
        val root: View
        if (dock != DockSide.NONE) {
            root = DockedTimerView(context).apply { this.track = track; isClickable = true; isFocusable = true }
            dialog.setContentView(root, ViewGroup.LayoutParams(dp(160), dp(192)))
        } else {
            root = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(2), dp(2), dp(2))
            }
            info = TextView(context).apply {
                textSize = 20f; typeface = context.resources.getFont(R.font.jetbrains_mono_regular); gravity = Gravity.CENTER
                text = formatTime(track.session?.remaining(SystemClock.elapsedRealtime()) ?: 0)
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; includeFontPadding = false
                minHeight = dp(48); setPadding(dp(6), 0, dp(2), 0); setTextColor(0xFF222632.toInt()); isClickable = true
            }
            info.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val infoWidth = info.measuredWidth.coerceIn(dp(72), dp(96))
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
            button("■", "Stop ${track.definition.name}") { stop(id) }
            info.setOnClickListener { toggle(id) }
            dialog.setContentView(root)
        }
        root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val width = if (dock == DockSide.NONE) root.measuredWidth else dp(160)
        val height = if (dock == DockSide.NONE) root.measuredHeight.coerceAtLeast(dp(56)) else dp(192)
        val screen = context.resources.displayMetrics
        val maxX = (screen.widthPixels - width).coerceAtLeast(0)
        val maxY = (screen.heightPixels - height - dp(56)).coerceAtLeast(0)
        val p = window.attributes.apply {
            gravity = Gravity.TOP or Gravity.LEFT
            if(Build.VERSION.SDK_INT>=30) setFitInsetsTypes(0)
            this.width = width; this.height = if (dock == DockSide.NONE) WindowManager.LayoutParams.WRAP_CONTENT else height
            x = when (dock) { DockSide.LEFT -> -width / 2; DockSide.RIGHT -> screen.widthPixels - width / 2; else -> (maxX * track.definition.x).toInt() }
            y = (maxY * track.definition.y).toInt(); title = "Halo timer ${track.definition.name}"
        }
        window.attributes = p
        fun expand(x: Float = if (dock == DockSide.LEFT) 0.05f else 0.95f) = moveControl(Command.Move(id, x.coerceIn(0f, 1f), p.y.toFloat() / maxY.coerceAtLeast(1), DockSide.NONE))
        if (dock != DockSide.NONE) {
            root.setOnClickListener { expand() }
            root.setOnLongClickListener {
                val location = IntArray(2); root.getLocationOnScreen(location)
                openActionMenu(id, dock, location[1] + root.height / 2); true
            }
        }
        val handle = info ?: root
        var downX = 0f; var downY = 0f; var initialX = 0; var initialY = 0; var dragged = false; var holding = false
        val longPress = Runnable {
            if (handle.isAttachedToWindow && !dragged && dock != DockSide.NONE) {
                holding = true; handle.performLongClick()
            }
        }
        handle.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; initialX = p.x; initialY = p.y; dragged = false; holding = false
                                handle.removeCallbacks(longPress)
                    if (dock != DockSide.NONE) handle.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                    true }
                MotionEvent.ACTION_MOVE -> {
                    if (holding) {
                        selectAction(event.rawX, event.rawY)
                        return@setOnTouchListener true
                    }
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (abs(dx) + abs(dy) > ViewConfiguration.get(context).scaledTouchSlop) { dragged = true; handle.removeCallbacks(longPress) }
                    if (dragged) {
                        p.x = (initialX + dx.toInt()).coerceIn(if (dock == DockSide.NONE) 0 else -width / 2, if (dock == DockSide.NONE) maxX else screen.widthPixels - width / 2)
                        p.y = (initialY + dy.toInt()).coerceIn(0, maxY)
                        window.attributes = p
                        if(root is DockedTimerView) {
                            val inward=if(dock==DockSide.LEFT) p.x+width/2 else screen.widthPixels-width/2-p.x
                            root.pull=(inward.toFloat()/dp(80)).coerceIn(0f,1f)
                            root.fullCircle=root.pull>=1f
                            root.invalidate()
                        } else {
                            val nearLeft=p.x<maxX/2
                            val distance=if(nearLeft) p.x else maxX-p.x
                            (window.decorView.background as? GlassBackground)?.apply {
                                side=if(nearLeft) DockSide.LEFT else DockSide.RIGHT
                                approach=if(c.prefs.value.reducedMotion) 0f else (1-distance.toFloat()/dp(48)).coerceIn(0f,1f)
                                invalidateSelf()
                            }
                        }
                    }; true
                }
                MotionEvent.ACTION_UP -> {
                    handle.removeCallbacks(longPress)
                    if (holding) {
                        selectAction(event.rawX, event.rawY)
                        chooseAction(id, actionMenu?.selected); holding = false
                        return@setOnTouchListener true
                    }
                    if (dragged) {
                        if (dock != DockSide.NONE) {
                            val inward = if (dock == DockSide.LEFT) event.rawX - downX else downX - event.rawX
                            if (inward > dp(32)) expand(event.rawX / screen.widthPixels)
                            else {
                                val current=c.state.value.tracks[id]
                                controls[id]?.let { old -> controls[id]=replaceControl(id,old,current,c.prefs.value.reducedMotion) }
                                moveControl(Command.Move(id,current.definition.x,p.y.toFloat()/maxY.coerceAtLeast(1),dock))
                            }
                        } else {
                            val side = when { p.x <= dp(16) -> DockSide.LEFT; p.x >= maxX - dp(16) -> DockSide.RIGHT; else -> DockSide.NONE }
                            moveControl(Command.Move(id, p.x.toFloat() / maxX.coerceAtLeast(1), p.y.toFloat() / maxY.coerceAtLeast(1), side))
                        }
                    } else v.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> { handle.removeCallbacks(longPress); holding = false; closeActionMenu(animated=true); (root as? DockedTimerView)?.apply { fullCircle=false; pull=0f; invalidate() }; p.x = initialX; p.y = initialY; window.attributes = p; true }
                else -> false
            }
        }
        dialog.show()
        // The window remains non-modal and only its compact bounds receive touches.
        return Control(dialog, root, dock, info, play)
    }
    fun hideControls() {
        closeActionMenu()
        exitingMenus.forEach { runCatching { wm.removeView(it) } }; exitingMenus.clear()
        morphs.keys.toList().forEach { finishMorph(it) }
        retiring.forEach { runCatching { it.dialog.dismiss() } }; retiring.clear()
        controls.values.forEach { runCatching { it.dialog.dismiss() } }; controls.clear()
    }
    fun removeAll() {
        edge?.let { runCatching { wm.removeView(it) } }; edge = null
        hideControls()
    }

}
