package com.ezral.halo.overlay

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import com.ezral.halo.core.*
import kotlin.math.*

/** One shallow edge contour which becomes a full circle when pulled into the display. */
class DockedTimerView(context: Context) : View(context) {
    var track: Track? = null
    var reducedMotion=false
    var pull=0f
    var fullCircle=false
    internal var clock: () -> Long = { SystemClock.elapsedRealtime() }
    private val d=resources.displayMetrics.density
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPath=Path(); private val labelPath=Path()
    private val ink=Rect(); private val location=IntArray(2)
    private val digits=resources.getFont(com.ezral.halo.R.font.jetbrains_mono_regular)
    private val labelFont=resources.getFont(com.ezral.halo.R.font.poppins_bold)
    private val textPaint=android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface=labelFont; textSize=15*d }
    private var sourceLabel=""; private var cachedLabel=""; private var labelWidth=0f
    private val born=SystemClock.elapsedRealtime()
    internal fun currentShape(): MorphShape {
        val side=track?.definition?.dock ?: DockSide.LEFT
        val cx=width/2f; val cy=height/2f
        if(fullCircle || pull>=1f) return MorphShape(RectF(cx-48*d,cy-48*d,cx+48*d,cy+48*d),PointF(cx,cy))
        getLocationOnScreen(location)
        val edge=if(side==DockSide.LEFT) -location[0].toFloat() else resources.displayMetrics.widthPixels-location[0].toFloat()
        val bounds=if(side==DockSide.LEFT) RectF(edge,cy-82*d,edge+44*d,cy+82*d) else RectF(edge-44*d,cy-82*d,edge,cy+82*d)
        val a=SurfaceContour.points(bounds,side)
        val circle=RectF(cx-48*d,cy-48*d,cx+48*d,cy+48*d)
        val b=SurfaceContour.points(circle)
        val blend=pull.coerceIn(0f,1f).let { it*it*(3-2*it) }
        val points=FloatArray(a.size) { a[it]+(b[it]-a[it])*blend }
        val textX=edge+(if(side==DockSide.LEFT) 22 else -22)*d
        return MorphShape(RectF(bounds).apply { union(circle) },PointF(textX+(cx-textX)*blend,cy),side,points)

    }
    override fun onDraw(canvas: Canvas) {
        val t=track ?: return; val s=t.session ?: return
        val now=clock(); val cx=width/2f; val cy=height/2f
        getLocationOnScreen(location)
        val left=t.definition.dock==DockSide.LEFT
        val edge=if(left) -location[0].toFloat() else resources.displayMetrics.widthPixels-location[0].toFloat()
        val blend=if(fullCircle) 1f else pull.coerceIn(0f,1f).let { it*it*(3-2*it) }
        val dockBounds=if(left) RectF(edge,cy-82*d,edge+44*d,cy+82*d) else RectF(edge-44*d,cy-82*d,edge,cy+82*d)
        SurfaceContour.path(bodyPath,SurfaceContour.points(dockBounds,t.definition.dock),
            SurfaceContour.points(RectF(cx-48*d,cy-48*d,cx+48*d,cy+48*d)),blend)
        paint.color=HaloGlass.color(t.definition.color.toInt()); paint.style=Paint.Style.FILL
        canvas.drawPath(bodyPath,paint)
        paint.color=HaloGlass.foreground(t.definition.color.toInt()); paint.typeface=digits
        paint.textSize=(16+2*blend)*d; paint.textAlign=Paint.Align.CENTER
        val dockTextX=edge+(if(left) 22 else -22)*d
        val textX=dockTextX+(cx-dockTextX)*blend
        val parts=formatTime(s.remaining(now)).split(":")
        paint.getTextBounds("0123456789",0,10,ink)
        val spacing=ink.height()+6*d; val baseline=cy-(ink.top+ink.bottom)/2f
        canvas.drawText(parts[0],textX,baseline-spacing/2,paint)
        canvas.drawText(parts[1],textX,baseline+spacing/2,paint)
        // The implied circle sits 22dp behind the edge, with its label outside the solid body.
        val dockCx=edge+(if(left) -22 else 22)*d
        val orbitCx=dockCx+(cx-dockCx)*blend; val radius=(76-17*blend)*d
        val source=t.overlayLabel()
        if(source!=sourceLabel) {
            sourceLabel=source
            // Stable glyph size and string across dock/full-circle transitions.
            cachedLabel=android.text.TextUtils.ellipsize(source,textPaint,170*d,android.text.TextUtils.TruncateAt.END).toString()
            labelWidth=textPaint.measureText(cachedLabel)
        }
        val halfSpan=acos(((if(left) edge-orbitCx else orbitCx-edge)/(radius-textPaint.ascent())).coerceIn(-1f,1f)).toDouble()
        val center=if(left) 0.0 else Math.PI
        val labelAngle=labelWidth/radius.toDouble()
        val radians=if(reducedMotion) center-labelAngle/2 else if(fullCircle || blend>=1f)
            LabelOrbit.fullRadians(now-born) else LabelOrbit.dockedRadians(now-born,center-halfSpan,halfSpan*2,labelAngle)
        paint.typeface=labelFont; paint.textSize=15*d; paint.textAlign=Paint.Align.LEFT
        paint.color=HaloGlass.color(t.definition.color.toInt())
        labelPath.reset(); labelPath.addCircle(orbitCx,cy,radius,Path.Direction.CW)
        // Clip in display coordinates before rotation. The glyphs keep their full opacity/scale.
        canvas.save()
        canvas.clipRect(-location[0].toFloat(),-location[1].toFloat(),
            (resources.displayMetrics.widthPixels-location[0]).toFloat(),(resources.displayMetrics.heightPixels-location[1]).toFloat())
        canvas.rotate(Math.toDegrees(radians).toFloat(),orbitCx,cy)
        canvas.drawTextOnPath(cachedLabel,labelPath,0f,0f,paint)
        canvas.restore()
        if(isAttachedToWindow && !reducedMotion) postInvalidateOnAnimation()
    }
}
