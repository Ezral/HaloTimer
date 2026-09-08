package com.ezral.halo.overlay

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import com.ezral.halo.core.*

/** A circle that becomes a half dock when its window crosses the physical display edge. */
class DockedTimerView(context: Context) : View(context) {
    var track: Track? = null
    var reducedMotion = false
    var fullCircle = false
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPath = Path()
    private val ink = Rect()
    private val digits = resources.getFont(com.ezral.halo.R.font.jetbrains_mono_regular)
    private val labelFont = resources.getFont(com.ezral.halo.R.font.poppins_bold)
    private val textPaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = labelFont; textSize = 15*d }
    private var cachedLabel = ""
    private var sourceLabel = ""
    private var cachedFull = false
    private var labelWidth = 0f
    private val born = SystemClock.elapsedRealtime()
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = track ?: return
        val s = t.session ?: return
        val now = SystemClock.elapsedRealtime()
        val cx = width/2f; val cy = height/2f; val radius = 48*d
        paint.style = Paint.Style.FILL; paint.shader = null
        paint.color = HaloGlass.color(t.definition.color.toInt())
        canvas.drawCircle(cx, cy, radius, paint)
        paint.color = 0xFF303644.toInt(); paint.typeface = digits
        paint.textSize = 18*d; paint.textAlign = Paint.Align.CENTER
        val textX = if (fullCircle) cx else cx + if (t.definition.dock == DockSide.LEFT) radius/2 else -radius/2
        val parts = formatTime(s.remaining(now)).split(":")
        paint.getTextBounds("0123456789", 0, 10, ink)
        val spacing = ink.height()+6*d
        val baseline = cy-(ink.top+ink.bottom)/2f
        canvas.drawText(parts[0], textX, baseline-spacing/2, paint)
        canvas.drawText(parts[1], textX, baseline+spacing/2, paint)
        val source = t.overlayLabel()
        val orbitRadius = 59*d
        val arc = (Math.PI*orbitRadius*(if (fullCircle) 2 else 1)).toFloat()
        if (source != sourceLabel || cachedFull != fullCircle) {
            sourceLabel=source; cachedFull=fullCircle
            cachedLabel=android.text.TextUtils.ellipsize(source,textPaint,arc*.92f,android.text.TextUtils.TruncateAt.END).toString()
            labelWidth=textPaint.measureText(cachedLabel)
        }
        paint.typeface=labelFont; paint.textSize=15*d; paint.textAlign=Paint.Align.LEFT
        paint.color=t.definition.color.toInt()
        labelPath.reset()
        val start = if (fullCircle || t.definition.dock==DockSide.LEFT) -90f else 90f
        labelPath.addArc(cx-orbitRadius,cy-orbitRadius,cx+orbitRadius,cy+orbitRadius,start,360f)
        val distance = if (reducedMotion) 0f else ((now-born)/1000f * (2*Math.PI*orbitRadius/6).toFloat())
        // Restart the whole label as soon as its leading end would cross the cropped edge.
        // Rotate the canvas instead of using negative path offsets, which clamp glyphs together.
        val offset = if (fullCircle) distance%arc else if (reducedMotion) 0f else
            ((now-born)%3_000)/3_000f*(arc-labelWidth).coerceAtLeast(0f)
        canvas.save()
        if (!fullCircle) {
            if (t.definition.dock==DockSide.LEFT) canvas.clipRect(cx,0f,width.toFloat(),height.toFloat())
            else canvas.clipRect(0f,0f,cx,height.toFloat())
        }
        canvas.rotate((offset/orbitRadius*180/Math.PI).toFloat(),cx,cy)
        canvas.drawTextOnPath(cachedLabel,labelPath,0f,0f,paint)
        canvas.restore()
        if (isAttachedToWindow && !reducedMotion) postInvalidateOnAnimation()
    }
}
