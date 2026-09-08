package com.ezral.halo.overlay

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import com.ezral.halo.core.*

/** A full circle deliberately positioned halfway outside the display. */
class DockedTimerView(context: Context) : View(context) {
    var track: Track? = null
    var reducedMotion = false
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPath = Path()
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = track ?: return
        val s = t.session ?: return
        val now = SystemClock.elapsedRealtime()
        val cx = width / 2f; val cy = height / 2f; val radius = 48 * d
        paint.color = t.definition.color.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2 * d
        val progress = (s.remaining(now).toFloat() / s.stepDurationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
        canvas.drawArc(cx - radius + d, cy - radius + d, cx + radius - d, cy + radius - d, -90f, 360 * progress, false, paint)
        paint.style = Paint.Style.FILL; paint.color = Color.rgb(30, 33, 43)
        paint.typeface = Typeface.create("monospace", Typeface.NORMAL); paint.textSize = 18 * d; paint.textAlign = Paint.Align.CENTER
        val textX = cx + if (t.definition.dock == DockSide.LEFT) 23 * d else -23 * d
        val parts = formatTime(s.remaining(now)).split(":")
        canvas.drawText(parts[0], textX, cy - 3 * d, paint)
        canvas.drawText(parts[1], textX, cy + 19 * d, paint)
        paint.textSize = 10 * d; paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); paint.textAlign = Paint.Align.LEFT
        // A pale halo under the moving letters keeps the label legible over varied apps.
        paint.setShadowLayer(2 * d, 0f, 0f, Color.WHITE)
        labelPath.reset(); labelPath.addCircle(cx, cy, 59 * d, Path.Direction.CW)
        canvas.save()
        if (!reducedMotion) canvas.rotate((now % 16_000) / 16_000f * 360, cx, cy)
        val label = "${t.definition.name}  ·  "
        val length = (2 * Math.PI * 59 * d).toFloat()
        val count = (length / paint.measureText(label).coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        canvas.drawTextOnPath(label.repeat(count), labelPath, 0f, 0f, paint)
        canvas.restore(); paint.clearShadowLayer()
        if (isAttachedToWindow && !reducedMotion) postInvalidateDelayed(33)
    }
}
