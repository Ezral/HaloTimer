package com.ezral.halo.overlay

import android.content.Context
import android.graphics.*
import android.view.View
import com.ezral.halo.R
import com.ezral.halo.core.DockSide
import kotlin.math.hypot

/** A bounded visual menu. The original dock keeps ownership of the entire pointer gesture. */
class DockActionMenu(context: Context, private val side: DockSide, private val color: Int) : View(context) {
    enum class Action(val icon: String, val label: String) {
        PLAY("▶", "Play"), PAUSE("Ⅱ", "Pause"), RESTART("↺", "Restart"), DISMISS("×", "Dismiss")
    }
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val font = resources.getFont(R.font.poppins_medium)
    var selected: Action? = null
        private set
    init { setLayerType(LAYER_TYPE_SOFTWARE, null); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    private fun center(action: Action): PointF {
        val x = if (action == Action.PLAY || action == Action.DISMISS) 60f else 126f
        return PointF((if (side == DockSide.LEFT) x else 192 - x) * d, (40 + action.ordinal * 64) * d)
    }
    fun select(x: Float, y: Float) {
        val next = Action.entries.firstOrNull { val p = center(it); hypot(x - p.x, y - p.y) <= 31 * d }
        if (selected != next) { selected = next; invalidate() }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Short soft bridges make the four targets read as one connected blob menu.
        paint.color = Color.argb(180, 240, 243, 250); paint.strokeWidth = 16 * d
        Action.entries.zipWithNext().forEach { (a, b) ->
            val from = center(a); val to = center(b); canvas.drawLine(from.x, from.y, to.x, to.y, paint)
        }
        Action.entries.forEach { action ->
            val p = center(action); val active = selected == action
            paint.color = if (active) color else Color.rgb(244, 247, 253)
            paint.setShadowLayer(7 * d, 0f, 2 * d, 0x35000000)
            canvas.drawCircle(p.x, p.y, (if (active) 31 else 28) * d, paint)
            paint.clearShadowLayer(); paint.color = 0xFF303644.toInt(); paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT; paint.textSize = 23 * d
            canvas.drawText(action.icon, p.x, p.y + d, paint)
            paint.typeface = font; paint.textSize = 10 * d
            canvas.drawText(action.label, p.x, p.y + 17 * d, paint)
        }
    }
}
