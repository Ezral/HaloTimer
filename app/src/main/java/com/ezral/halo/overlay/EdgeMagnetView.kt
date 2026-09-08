package com.ezral.halo.overlay

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/** Touch-transparent liquid neck between a dragged control and the physical screen edge. */
internal class EdgeMagnetView(context: Context) : View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    var right=false
    var color=Color.WHITE
    var strength=0f
    var bodyX=0f
    var bodyY=0f
    var halfHeight=0f
    override fun onDraw(canvas: Canvas) {
        if(strength<=0f) return
        canvas.save()
        if(right) { canvas.translate(width.toFloat(),0f); canvas.scale(-1f,1f) }
        val x=if(right) width-bodyX else bodyX
        val radius=halfHeight*(.45f+.7f*strength)
        val y=bodyY
        path.reset(); path.moveTo(0f,y-radius*1.3f)
        path.cubicTo(x*.35f,y-radius*1.3f,x*.55f,y-halfHeight,x,y-halfHeight)
        path.lineTo(x,y+halfHeight)
        path.cubicTo(x*.55f,y+halfHeight,x*.35f,y+radius*1.3f,0f,y+radius*1.3f)
        path.close()
        paint.color=HaloGlass.color(color)
        paint.alpha=(Color.alpha(paint.color)*strength).toInt()
        canvas.drawPath(path,paint)
        // The edge bud grows as the neck shortens; it becomes the half-circle on release.
        canvas.drawCircle(0f,y,radius*strength,paint)
        canvas.restore()
    }
}
