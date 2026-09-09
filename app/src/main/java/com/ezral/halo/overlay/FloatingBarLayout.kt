package com.ezral.halo.overlay

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.widget.LinearLayout
import com.ezral.halo.R
import com.ezral.halo.core.*

/** A capsule body with one separate, timer-colored label in the transparent margin. */
internal class FloatingBarLayout(context:Context):LinearLayout(context) {
    var track:Track?=null
    var reducedMotion=false
    private val d=resources.displayMetrics.density
    private val fill=Paint(Paint.ANTI_ALIAS_FLAG)
    private val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface=resources.getFont(R.font.poppins_bold);textSize=14*d }
    private val label=ContourLabel(paint)
    private val path=Path()
    private val born=SystemClock.elapsedRealtime()
    fun surfaceBounds():RectF = getChildAt(0)?.let { RectF(it.left.toFloat(),it.top.toFloat(),it.right.toFloat(),it.bottom.toFloat()) } ?: RectF()
    override fun dispatchDraw(canvas:Canvas) {
        val t=track ?: return
        val body=surfaceBounds()
        fill.color=HaloGlass.color(t.definition.color.toInt())
        SurfaceContour.path(path,SurfaceContour.points(body));canvas.drawPath(path,fill)
        super.dispatchDraw(canvas)
        if(!t.definition.showBarName) return
        paint.color=fill.color
        if(!t.definition.rotateBarText || reducedMotion) {
            val text=TextUtils.ellipsize(t.definition.name,paint,(body.width()-24*d).coerceAtLeast(1f),TextUtils.TruncateAt.END).toString()
            canvas.drawText(text,body.left+12*d,body.bottom+8*d-paint.ascent(),paint)
        } else {
            val orbit=RectF(body).apply { inset(-8*d,-8*d) }
            SurfaceContour.path(path,SurfaceContour.points(orbit))
            label.draw(canvas,path,t.overlayLabel(),SystemClock.elapsedRealtime()-born,48*d,true,false,170*d)
            if(isAttachedToWindow) postInvalidateOnAnimation()
        }
    }
}
