package com.ezral.halo.overlay

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import com.ezral.halo.R
import com.ezral.halo.core.*
import kotlin.math.*

internal data class MorphShape(val bounds: RectF, val text: PointF, val docked: Boolean)

/** A short, bounded glass surface carries the pill into/out of its anchored edge bubble. */
internal class DockMorphView(context: Context, private val from: MorphShape, private val to: MorphShape,
    private val track: Track, private val finished: () -> Unit) : View(context) {
    private val born = SystemClock.elapsedRealtime()
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val bubble = Path()
    private val rect = RectF()
    private val font = resources.getFont(R.font.jetbrains_mono_regular)
    private var completed = false
    init { setLayerType(LAYER_TYPE_SOFTWARE, null); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    private fun mix(a: Float, b: Float, p: Float) = a + (b - a) * p
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.elapsedRealtime()
        val t = ((now - born) / 420f).coerceIn(0f, 1f)
        val p = t * t * (3 - 2 * t)
        rect.set(mix(from.bounds.left, to.bounds.left, p), mix(from.bounds.top, to.bounds.top, p),
            mix(from.bounds.right, to.bounds.right, p), mix(from.bounds.bottom, to.bounds.bottom, p))
        val puff = sin(t * PI).toFloat() * 3 * d
        rect.inset(0f, -puff)
        val radius = min(rect.width(), rect.height()) / 2
        path.reset(); path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        val dock = if (to.docked) to else from
        val dockMix = if (to.docked) p else 1 - p
        val anchorRadius = dock.bounds.width() / 2 * dockMix
        if (anchorRadius > 0) {
            bubble.reset(); bubble.addCircle(dock.bounds.centerX(), dock.bounds.centerY(), anchorRadius, Path.Direction.CW)
            path.op(bubble, Path.Op.UNION)
        }
        paint.style = Paint.Style.FILL; paint.alpha = 255
        paint.pathEffect = CornerPathEffect(7 * d)
        paint.shader = HaloGlass.shader(rect, track.definition.color.toInt())
        paint.setShadowLayer(8 * d, 0f, 2 * d, 0x30000000)
        canvas.drawPath(path, paint)
        paint.shader = null; paint.pathEffect = null; paint.clearShadowLayer()
        paint.color = 0xFF303644.toInt(); paint.typeface = font; paint.textAlign = Paint.Align.CENTER
        paint.textSize = mix(20 * resources.displayMetrics.scaledDensity, 18 * d, dockMix)
        val x = mix(from.text.x, to.text.x, p); val y = mix(from.text.y, to.text.y, p)
        val time = formatTime(track.session?.remaining(now) ?: 0)
        val ink = Rect(); paint.getTextBounds("0123456789", 0, 10, ink)
        val baseline = y - (ink.top + ink.bottom) / 2f
        canvas.save(); canvas.clipPath(path)
        if (dockMix < .5f) {
            paint.alpha = (255 * (1 - dockMix * 2)).toInt()
            canvas.drawText(time, x, baseline, paint)
        } else {
            paint.alpha = (255 * (dockMix * 2 - 1)).toInt()
            val parts = time.split(":"); val spacing = ink.height() + 6 * d
            canvas.drawText(parts[0], x, baseline - spacing / 2, paint)
            canvas.drawText(parts[1], x, baseline + spacing / 2, paint)
        }
        canvas.restore()
        if (t < 1 && isAttachedToWindow) postInvalidateOnAnimation()
        else if (!completed) { completed = true; post { finished() } }
    }
}
