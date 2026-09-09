package com.ezral.halo.overlay

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.text.TextPaint
import android.widget.LinearLayout
import com.ezral.halo.R
import com.ezral.halo.core.*

internal class FloatingBarLayout(context:Context):LinearLayout(context) {
    var track:Track?=null
    var reducedMotion=false
    private val d=resources.displayMetrics.density
    private val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface=resources.getFont(R.font.poppins_bold);textSize=10*d }
    private val label=ContourLabel(paint)
    private val path=Path()
    private val born=SystemClock.elapsedRealtime()
    override fun dispatchDraw(canvas:Canvas) {
        super.dispatchDraw(canvas)
        val t=track ?: return
        if(!t.definition.rotateBarText) return
        paint.color=HaloGlass.foreground(t.definition.color.toInt())
        SurfaceContour.path(path,SurfaceContour.points(RectF(13*d,12*d,width-13*d,height-8*d)))
        label.draw(canvas,path,t.overlayLabel(),SystemClock.elapsedRealtime()-born,42*d,true,reducedMotion,150*d)
        if(isAttachedToWindow && !reducedMotion) postInvalidateOnAnimation()
    }
}
