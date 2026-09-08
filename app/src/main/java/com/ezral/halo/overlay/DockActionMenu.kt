package com.ezral.halo.overlay

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import com.ezral.halo.R
import com.ezral.halo.core.DockSide
import kotlin.math.*

/** Independent bubbles spring from the dock onto equally spaced points of the same arc. */
class DockActionMenu(context: Context, private val side: DockSide, private val color: Int,
    private val reducedMotion: Boolean = false) : View(context) {
    enum class Action(val icon: String, val label: String) {
        PLAY("▶", "Play"), PAUSE("Ⅱ", "Pause"), RESTART("↺", "Restart"), DISMISS("×", "Dismiss")
    }
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val font = resources.getFont(R.font.poppins_medium)
    private val born = SystemClock.elapsedRealtime()
    var selected: Action? = null
        private set
    init { setLayerType(LAYER_TYPE_SOFTWARE, null); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    private fun center(action: Action): PointF {
        val angle = Math.toRadians(-66.0 + action.ordinal * 44)
        val x = (cos(angle) * 108).toFloat()
        return PointF((if (side == DockSide.LEFT) x else 168 - x) * d, (144 + sin(angle).toFloat() * 108) * d)
    }
    fun select(x: Float, y: Float) {
        // Stable targets support a quick slide even while their entrance animation settles.
        val next = Action.entries.firstOrNull { val p = center(it); hypot(x - p.x, y - p.y) <= 31 * d }
        if (selected != next) { selected = next; invalidate() }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val elapsed = SystemClock.elapsedRealtime() - born
        Action.entries.forEach { action ->
            val t = if (reducedMotion) 1f else ((elapsed - action.ordinal * 35) / 380f).coerceIn(0f, 1f)
            val spring = if (t == 1f) 1f else (1 - exp(-7 * t) * (cos(10 * t) + .7f * sin(10 * t)))
            val destination = center(action)
            val originX = (if (side == DockSide.LEFT) 24 else 144) * d
            val x = originX + (destination.x - originX) * spring
            val y = 144 * d + (destination.y - 144 * d) * spring
            val active = selected == action
            canvas.save(); canvas.translate(x, y); canvas.scale(spring.coerceAtLeast(.01f), spring.coerceAtLeast(.01f))
            val alpha = (255 * (t * 4).coerceIn(0f, 1f)).toInt()
            paint.color = if (active) Color.rgb((Color.red(color) * .4f + 255 * .6f).toInt(),
                (Color.green(color) * .4f + 255 * .6f).toInt(), (Color.blue(color) * .4f + 255 * .6f).toInt())
                else Color.rgb(244, 247, 253)
            paint.alpha = alpha
            paint.setShadowLayer(7 * d, 0f, 2 * d, Color.argb((alpha * .2f).toInt(), 0, 0, 0))
            canvas.drawCircle(0f, 0f, (if (active) 31 else 28) * d, paint)
            paint.clearShadowLayer(); paint.color = 0xFF303644.toInt(); paint.alpha = alpha; paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT; paint.textSize = 23 * d
            canvas.drawText(action.icon, 0f, d, paint)
            paint.typeface = font; paint.textSize = 10 * d
            canvas.drawText(action.label, 0f, 17 * d, paint)
            canvas.restore()
        }
        if (!reducedMotion && elapsed < 500 && isAttachedToWindow) postInvalidateOnAnimation()
    }
}
