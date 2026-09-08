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
    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = track ?: return
        val s = t.session ?: return
        val now = SystemClock.elapsedRealtime()
        val cx = width / 2f; val cy = height / 2f; val radius = 48 * d
        // Keep the orbiting label outside the glass without a rectangular window-blur footprint.
        paint.style = Paint.Style.FILL
        val line = t.definition.color.toInt()
        val pale = Color.rgb(
            (Color.red(line) * .38f + 255 * .62f).toInt(),
            (Color.green(line) * .38f + 255 * .62f).toInt(),
            (Color.blue(line) * .38f + 255 * .62f).toInt())
        paint.shader = LinearGradient(cx - radius, cy - radius, cx + radius, cy + radius,
            Color.argb(238, Color.red(line), Color.green(line), Color.blue(line)),
            Color.argb(220, Color.red(pale), Color.green(pale), Color.blue(pale)), Shader.TileMode.CLAMP)
        paint.setShadowLayer(8 * d, 0f, 2 * d, 0x30000000)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null; paint.clearShadowLayer()
        paint.color = t.definition.color.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2 * d
        val progress = (s.remaining(now).toFloat() / s.stepDurationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
        canvas.drawArc(cx - radius + d, cy - radius + d, cx + radius - d, cy + radius - d, -90f, 360 * progress, false, paint)
        paint.style = Paint.Style.FILL; paint.color = Color.rgb(30, 33, 43)
        paint.typeface = Typeface.create("monospace", Typeface.NORMAL); paint.textSize = 18 * d; paint.textAlign = Paint.Align.CENTER
        val textX = cx + if (t.definition.dock == DockSide.LEFT) 23 * d else -23 * d
        val parts = formatTime(s.remaining(now)).split(":")
        canvas.drawText(parts[0], textX, cy - 3 * d, paint)
        canvas.drawText(parts[1], textX, cy + 19 * d, paint)
        paint.textSize = 15 * d; paint.typeface = Typeface.create("sans-serif", Typeface.BOLD); paint.textAlign = Paint.Align.LEFT
        paint.color = t.definition.color.toInt()
        labelPath.reset(); labelPath.addCircle(cx, cy, 59 * d, Path.Direction.CW)
        canvas.save()
        if (!reducedMotion) canvas.rotate((now % 16_000) / 16_000f * 360, cx, cy)
        val fullLabel = t.overlayLabel()
        // Keep one label on the circumference without overlapping its own beginning.
        val label = android.text.TextUtils.ellipsize(fullLabel, android.text.TextPaint(paint),
            (2 * Math.PI * 59 * d * .94).toFloat(), android.text.TextUtils.TruncateAt.END).toString()
        // One solid label orbits the outside; it is intentionally not repeated around the circle.
        canvas.drawTextOnPath(label, labelPath, 0f, 0f, paint)
        canvas.restore(); paint.clearShadowLayer()
        if (isAttachedToWindow && !reducedMotion) postInvalidateDelayed(33)
    }
}
