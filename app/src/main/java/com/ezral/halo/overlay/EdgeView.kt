package com.ezral.halo.overlay

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import com.ezral.halo.core.*
import kotlin.math.*

/** One decorative, non-interactive surface for every lane. Geometry is cached. */
class EdgeView(context: Context) : View(context) {
    var tracks: List<Track> = emptyList()
    var reducedMotion = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val measures = mutableListOf<PathMeasure>()
    private val segment = Path()
    private val path = Path()
    private val rect = RectF()
    private val widthDp = resources.displayMetrics.density * 4f
    private var geometryCount = -1
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; setLayerType(LAYER_TYPE_SOFTWARE, null) }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { geometryCount = -1 }
    private fun geometry() {
        if (geometryCount == tracks.size) return
        measures.clear()
        repeat(tracks.size) { i ->
            val inset = widthDp / 2 + i * widthDp
            val radius = (28 * resources.displayMetrics.density - inset).coerceAtLeast(0f)
            val l = inset; val t = inset; val r = width - inset; val b = height - inset
            path.reset(); path.moveTo(width / 2f, t); path.lineTo(r - radius, t)
            rect.set(r - 2 * radius, t, r, t + 2 * radius); path.arcTo(rect, -90f, 90f)
            path.lineTo(r, b - radius); rect.set(r - 2 * radius, b - 2 * radius, r, b); path.arcTo(rect, 0f, 90f)
            path.lineTo(l + radius, b); rect.set(l, b - 2 * radius, l + 2 * radius, b); path.arcTo(rect, 90f, 90f)
            path.lineTo(l, t + radius); rect.set(l, t, l + 2 * radius, t + 2 * radius); path.arcTo(rect, 180f, 90f)
            path.close(); measures += PathMeasure(path, true)
        }
        geometryCount = tracks.size
    }
    private fun drawPart(canvas: Canvas, measure: PathMeasure, start: Float, fraction: Float) {
        val from = ((start % 1f) + 1f) % 1f
        val to = from + fraction.coerceIn(0f, 1f)
        segment.reset()
        measure.getSegment(from * measure.length, min(to, 1f) * measure.length, segment, true)
        if (to > 1) measure.getSegment(0f, (to - 1) * measure.length, segment, true)
        canvas.drawPath(segment, paint)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); geometry()
        val now = SystemClock.elapsedRealtime()
        tracks.forEachIndexed { i, track ->
            val s = track.session ?: return@forEachIndexed
            val m = measures[i]
            paint.strokeWidth = widthDp
            paint.color = track.definition.color.toInt()
            paint.clearShadowLayer(); paint.alpha = 36
            drawPart(canvas, m, 0f, 1f)
            paint.alpha = 255
            val glow = track.definition.glow
            if (glow > 0) paint.setShadowLayer(widthDp * (0.5f + glow), 0f, 0f, paint.color)
            val alert = s.visualUntilMs > now
            if (!alert) drawPart(canvas, m, 0f, if (s.status == Status.COMPLETED) 1f else s.progress(now))
            else if (reducedMotion) drawPart(canvas, m, 0f, 1f)
            else when (track.definition.alert) {
                AlertStyle.BREATHE -> {
                    paint.alpha = (100 + 155 * (0.5 + 0.5 * sin(now / 3200.0 * 2 * PI))).toInt()
                    drawPart(canvas, m, 0f, 1f)
                }
                AlertStyle.ORBIT -> drawPart(canvas, m, (now % 2800) / 2800f, 0.20f)
                AlertStyle.PING_PONG, AlertStyle.DOUBLE_PONG -> {
                    val half = if (track.definition.alert == AlertStyle.DOUBLE_PONG) 2800 else 3600
                    val phase = (now % (half * 2)) / half.toFloat()
                    val pos = if (phase <= 1) phase else 2 - phase
                    drawPart(canvas, m, pos * 0.8f, 0.2f)
                    if (track.definition.alert == AlertStyle.DOUBLE_PONG) drawPart(canvas, m, pos * 0.8f + 0.5f, 0.2f)
                }
            }
        }
        if (isAttachedToWindow && tracks.isNotEmpty()) postInvalidateDelayed(if (reducedMotion) 250 else 33)
    }
}
