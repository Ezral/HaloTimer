package com.ezral.halo.overlay

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import com.ezral.halo.R
import com.ezral.halo.core.*

internal data class MorphShape(val bounds: RectF, val text: PointF, val side: DockSide = DockSide.NONE, val contour: FloatArray? = null)

/** A single opaque path changes curvature; no unions, second layers, shadows or overshoot. */
internal class DockMorphView(context: Context, private val from: MorphShape, private val to: MorphShape,
    private val track: Track, private val finished: () -> Unit) : View(context) {
    private val born=SystemClock.elapsedRealtime()
    private val d=resources.displayMetrics.density
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    private val a=from.contour ?: SurfaceContour.points(from.bounds,from.side)
    private val b=to.contour ?: SurfaceContour.points(to.bounds,to.side)
    private val font=resources.getFont(R.font.jetbrains_mono_regular)
    private val ink=Rect()
    private var completed=false
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        val now=SystemClock.elapsedRealtime()
        val t=((now-born)/220f).coerceIn(0f,1f)
        val p=t*t*(3-2*t)
        SurfaceContour.path(path,a,b,p)
        paint.color=HaloGlass.color(track.definition.color.toInt()); canvas.drawPath(path,paint)
        paint.color=HaloGlass.foreground(track.definition.color.toInt()); paint.typeface=font
        paint.textAlign=Paint.Align.CENTER
        val dockMix=if(to.side!=DockSide.NONE) p else if(from.side!=DockSide.NONE) 1-p else 0f
        paint.textSize=(20-4*dockMix)*d
        val x=from.text.x+(to.text.x-from.text.x)*p; val y=from.text.y+(to.text.y-from.text.y)*p
        val time=formatTime(track.session?.remaining(now) ?: 0)
        paint.getTextBounds("0123456789",0,10,ink)
        val baseline=y-(ink.top+ink.bottom)/2f
        canvas.save(); canvas.clipPath(path)
        if(dockMix<.5f) canvas.drawText(time,x,baseline,paint)
        else {
            val parts=time.split(":"); val spacing=ink.height()+6*d
            canvas.drawText(parts[0],x,baseline-spacing/2,paint)
            canvas.drawText(parts[1],x,baseline+spacing/2,paint)
        }
        canvas.restore()
        if(t<1 && isAttachedToWindow) postInvalidateOnAnimation()
        else if(!completed) { completed=true; post { finished() } }
    }
}
