package com.ezral.halo.overlay

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import com.ezral.halo.R
import com.ezral.halo.core.*
import kotlin.math.*

/** The original input window retains the gesture while this single surface follows either edge. */
internal class DragSurfaceView(context:Context,private val track:Track,private val circle:Boolean,private val reduced:Boolean):View(context) {
    private val d=resources.displayMetrics.density
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path();private val ink=Rect()
    private val labelPath=Path()
    private val labelPaint=android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface=resources.getFont(R.font.poppins_bold);textSize=15*d }
    private val label=ContourLabel(labelPaint)
    private val born=SystemClock.elapsedRealtime()
    private val digits=resources.getFont(R.font.jetbrains_mono_regular)
    private var free=RectF();private var side=track.definition.dock.takeIf { it!=DockSide.NONE } ?: DockSide.LEFT
    private var mix=if(circle) 1f else 0f;private var target=0f;private var last=0L
    private var points=FloatArray(0);private var text=PointF()
    var contact=DockSide.NONE;private set
    var windowY=0
    fun position(bounds:RectF,screenWidth:Int) {
        free=RectF(bounds).apply { offset(0f,-windowY.toFloat()) }
        val left=free.left;val right=screenWidth-free.right
        val next=if(left<=right) DockSide.LEFT else DockSide.RIGHT
        if(next!=side) { mix=0f;side=next }
        val distance=min(left,right)
        contact=if(distance<=1f) side else DockSide.NONE
        target=if(contact!=DockSide.NONE) 1f else (1-distance/(32*d)).coerceIn(0f,1f)*.16f
        if(reduced) mix=target
        invalidate()
    }
    fun currentShape():MorphShape {
        updateShape()
        val bounds=RectF();path.computeBounds(bounds,true);bounds.offset(0f,windowY.toFloat())
        return MorphShape(bounds,PointF(text.x,text.y+windowY),if(mix>.5f) side else DockSide.NONE,
            points.mapIndexed { i,v -> v+if(i%2==1) windowY else 0 }.toFloatArray())
    }
    private fun updateShape() {
        val edge=if(side==DockSide.LEFT) 0f else resources.displayMetrics.widthPixels.toFloat()
        val cy=free.centerY()
        val dock=if(side==DockSide.LEFT) RectF(edge,cy-82*d,edge+44*d,cy+82*d) else RectF(edge-44*d,cy-82*d,edge,cy+82*d)
        val a=SurfaceContour.points(free);val b=SurfaceContour.points(dock,side)
        points=FloatArray(a.size) { a[it]+(b[it]-a[it])*mix }
        SurfaceContour.path(path,points)
        val tx=edge+(if(side==DockSide.LEFT) 22 else -22)*d
        val startX=if(circle) free.centerX() else free.left+42*d
        text=PointF(startX+(tx-startX)*mix,cy)
    }
    override fun onDraw(canvas:Canvas) {
        val now=SystemClock.elapsedRealtime()
        val dt=if(last==0L) 16f else (now-last).coerceAtMost(40).toFloat();last=now
        // Critically damped-looking, monotone approach; no spring overshoot.
        mix=if(reduced) target else mix+(target-mix)*(1-exp(-dt/55f))
        if(abs(mix-target)<.001f) mix=target
        updateShape()
        paint.color=HaloGlass.color(track.definition.color.toInt());canvas.drawPath(path,paint)
        paint.color=HaloGlass.foreground(track.definition.color.toInt());paint.typeface=digits
        paint.textSize=(if(circle || mix>.5f) 17 else 20)*d;paint.textAlign=Paint.Align.CENTER
        val time=formatTime(track.session?.remaining(now) ?: 0)
        paint.getTextBounds("0123456789",0,10,ink)
        val baseline=text.y-(ink.top+ink.bottom)/2f
        if(circle || mix>.5f) {
            val parts=time.split(":");val gap=ink.height()+6*d
            canvas.drawText(parts[0],text.x,baseline-gap/2,paint);canvas.drawText(parts[1],text.x,baseline+gap/2,paint)
        } else canvas.drawText(time,text.x,baseline,paint)
        if(!circle && mix<.7f) {
            val scale=(1-mix/.7f).coerceIn(0f,1f)
            paint.typeface=android.graphics.Typeface.DEFAULT;paint.textSize=22*d
            listOf(if(track.session?.status==Status.RUNNING) "Ⅱ" else "▶","↺","■").forEachIndexed { i,icon ->
                val x=free.right-(126-48*i)*d
                canvas.save();canvas.translate(x+(text.x-x)*mix,text.y);canvas.scale(scale,scale)
                canvas.drawText(icon,0f,-(paint.ascent()+paint.descent())/2,paint);canvas.restore()
            }
        }
        if(circle && mix<.02f) {
            labelPath.reset();labelPath.addCircle(free.centerX(),free.centerY(),59*d,Path.Direction.CW)
            labelPaint.color=HaloGlass.color(track.definition.color.toInt())
            label.draw(canvas,labelPath,track.overlayLabel(),(now-born)%6000,(2*Math.PI*59*d/6).toFloat(),true,reduced,170*d)
        }
        if(isAttachedToWindow && (mix!=target || circle && !reduced || track.session?.status==Status.RUNNING)) postInvalidateOnAnimation()
    }
}
