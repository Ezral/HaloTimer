package com.ezral.halo.overlay

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.os.Build
import android.view.WindowInsets
import android.view.RoundedCorner
import android.view.View
import com.ezral.halo.core.*
import kotlin.math.*

/** One decorative, non-interactive surface for every lane. Geometry is cached. */
class EdgeView(context: Context) : View(context) {
    var tracks: List<Track> = emptyList()
    var reducedMotion = false
    internal var clock: () -> Long = { SystemClock.elapsedRealtime() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val measures = mutableListOf<PathMeasure>()
    private val segment = Path()
    private val path = Path()
    private val rect = RectF()
    private val widthDp = resources.displayMetrics.density * 4f
    private val gapPx = resources.displayMetrics.density * 2f
    private var cornerRadii = FloatArray(4) { 28 * resources.displayMetrics.density }
    private var geometryCount = -1
    private val shaders = mutableMapOf<LinePalette, Shader>()
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; setLayerType(LAYER_TYPE_SOFTWARE, null) }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { geometryCount = -1; shaders.clear() }
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= 31) {
            val positions = intArrayOf(RoundedCorner.POSITION_TOP_LEFT, RoundedCorner.POSITION_TOP_RIGHT,
                RoundedCorner.POSITION_BOTTOM_RIGHT, RoundedCorner.POSITION_BOTTOM_LEFT)
            val radii = FloatArray(4) { index -> insets.getRoundedCorner(positions[index])?.radius?.toFloat() ?: 0f }
            if (!radii.contentEquals(cornerRadii)) { cornerRadii = radii; geometryCount = -1; invalidate() }
        }
        return super.onApplyWindowInsets(insets)
    }
    private fun geometry() {
        if (geometryCount == tracks.size) return
        measures.clear()
        repeat(tracks.size) { i ->
            val inset = widthDp / 2 + i * (widthDp + gapPx)
            val l = inset; val t = inset; val r = width - inset; val b = height - inset
            val limit = min(r - l, b - t) / 2
            val tl = (cornerRadii[0] - inset).coerceIn(0f, limit)
            val tr = (cornerRadii[1] - inset).coerceIn(0f, limit)
            val br = (cornerRadii[2] - inset).coerceIn(0f, limit)
            val bl = (cornerRadii[3] - inset).coerceIn(0f, limit)
            path.reset(); path.moveTo(width / 2f, t); path.lineTo(r - tr, t)
            if (tr > 0) { rect.set(r - 2 * tr, t, r, t + 2 * tr); path.arcTo(rect, -90f, 90f) }
            path.lineTo(r, b - br)
            if (br > 0) { rect.set(r - 2 * br, b - 2 * br, r, b); path.arcTo(rect, 0f, 90f) }
            path.lineTo(l + bl, b)
            if (bl > 0) { rect.set(l, b - 2 * bl, l + 2 * bl, b); path.arcTo(rect, 90f, 90f) }
            path.lineTo(l, t + tl)
            if (tl > 0) { rect.set(l, t, l + 2 * tl, t + 2 * tl); path.arcTo(rect, 180f, 90f) }
            path.close(); measures += PathMeasure(path, true)
        }
        geometryCount = tracks.size
    }
    private fun drawPart(canvas: Canvas, measure: PathMeasure, start: Float, fraction: Float) {
        val from = ((start % 1f) + 1f) % 1f
        val to = from + fraction.coerceIn(0f, 1f)
        segment.reset()
        measure.getSegment(from * measure.length, min(to, 1f) * measure.length, segment, true)
        if (to > 1) measure.getSegment(0f, (to - 1) * measure.length, segment, false)
        canvas.drawPath(segment, paint)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); geometry()
        val now = clock()
        tracks.forEachIndexed { i, track ->
            val s = track.session ?: return@forEachIndexed
            val m = measures[i]
            paint.strokeWidth = widthDp
            paint.color = track.definition.color.toInt()
            val palette = track.definition.linePalette
            paint.shader = if (palette == LinePalette.SOLID) null else shaders.getOrPut(palette) {
                SweepGradient(width / 2f, height / 2f, (palette.colors + palette.colors.first()).map { it.toInt() }.toIntArray(), null)
            }
            paint.clearShadowLayer(); paint.alpha = 36
            drawPart(canvas, m, 0f, 1f)
            paint.alpha = 255
            val glow = track.definition.glow
            if (glow > 0) paint.setShadowLayer(widthDp * (0.5f + glow), 0f, 0f, paint.color)
            val alert = s.visualUntilMs > now
            val elapsed = (now - s.alertStartedAtMs).coerceAtLeast(0)
            if (!alert) drawPart(canvas, m, 0f, if (s.status == Status.COMPLETED) 1f else s.progress(now))
            else if (reducedMotion) drawPart(canvas, m, 0f, 1f)
            else when (track.definition.alert) {
                AlertStyle.BREATHE -> {
                    val level = AlertMotion.breathLevel(elapsed)
                    val alpha = AlertMotion.breathe(elapsed)
                    paint.clearShadowLayer()
                    if (glow > 0) paint.setShadowLayer(widthDp * (.35f + glow * level), 0f, 0f,
                        Color.argb(alpha, Color.red(paint.color), Color.green(paint.color), Color.blue(paint.color)))
                    paint.alpha = alpha
                    drawPart(canvas, m, 0f, 1f)
                }
                AlertStyle.ORBIT -> drawPart(canvas, m, AlertMotion.orbit(elapsed), 0.20f)
                AlertStyle.PING_PONG, AlertStyle.DOUBLE_PONG -> {
                    val pos = AlertMotion.pong(elapsed, track.definition.alert == AlertStyle.DOUBLE_PONG)
                    drawPart(canvas, m, pos, 0.2f)
                    if (track.definition.alert == AlertStyle.DOUBLE_PONG) drawPart(canvas, m, pos + 0.5f, 0.2f)
                }
            }
        }
        if (isAttachedToWindow && tracks.isNotEmpty()) postInvalidateDelayed(if (reducedMotion) 250 else 33)
    }
}
