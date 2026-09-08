package com.ezral.halo.overlay

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import com.ezral.halo.R
import com.ezral.halo.core.DockSide
import com.ezral.halo.core.Status
import kotlin.math.*

/** Three equally spaced bubbles; release retracts them along the same paths into the dock. */
class DockActionMenu(context: Context, private val side: DockSide, private val color: Int,
    private val reducedMotion: Boolean = false, private val status: Status = Status.RUNNING) : View(context) {
    enum class Action { PRIMARY, RESET, HIDE }
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val font = resources.getFont(R.font.poppins_medium)
    private val born = SystemClock.elapsedRealtime()
    private var closingAt = 0L
    private var closeDone: (() -> Unit)? = null
    var selected: Action? = null
        private set
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    private fun center(action: Action): PointF {
        val angle = Math.toRadians(-55.0 + action.ordinal*55)
        val x = (cos(angle)*108).toFloat()
        return PointF((if(side==DockSide.LEFT) x else 168-x)*d,(144+sin(angle).toFloat()*108)*d)
    }
    fun close(done: () -> Unit) {
        if (closingAt!=0L) return
        if(reducedMotion) { done(); return }
        closingAt=SystemClock.elapsedRealtime(); closeDone=done; postInvalidateOnAnimation()
    }
    fun select(x: Float,y: Float) {
        if(closingAt!=0L) return
        val next=Action.entries.firstOrNull { val p=center(it); hypot(x-p.x,y-p.y)<=31*d }
        if(selected!=next) { selected=next; invalidate() }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now=SystemClock.elapsedRealtime(); val elapsed=(if(closingAt==0L) now else closingAt)-born
        val exit=if(closingAt==0L) 0f else ((now-closingAt)/190f).coerceIn(0f,1f)
        val retract=1-exit*exit*(3-2*exit)
        Action.entries.forEach { action ->
            val t=if(reducedMotion) 1f else ((elapsed-action.ordinal*25)/250f).coerceIn(0f,1f)
            val spring=(if(t==1f) 1f else 1-exp(-8*t)*(cos(10*t)+.8f*sin(10*t)))*retract
            val dest=center(action); val ox=(if(side==DockSide.LEFT) 24 else 144)*d
            canvas.save(); canvas.translate(ox+(dest.x-ox)*spring,144*d+(dest.y-144*d)*spring)
            canvas.scale(spring.coerceAtLeast(.001f),spring.coerceAtLeast(.001f))
            paint.color=if(selected==action) HaloGlass.color(color) else Color.rgb(244,247,253)
            paint.alpha=(255*(t*4).coerceIn(0f,1f)*retract).toInt()
            canvas.drawCircle(0f,0f,28*d,paint)
            paint.color=0xFF303644.toInt(); paint.alpha=(255*retract).toInt()
            paint.textAlign=Paint.Align.CENTER; paint.typeface=Typeface.DEFAULT; paint.textSize=23*d
            val primary=when(status) { Status.RUNNING -> "Ⅱ" to "Pause"; Status.COMPLETED,Status.INTERRUPTED -> "■" to "Stop"; else -> "▶" to "Start" }
            val (icon,label)=when(action) { Action.PRIMARY -> primary; Action.RESET -> "↺" to "Reset"; Action.HIDE -> "⌄" to "Hide" }
            canvas.drawText(icon,0f,d,paint); paint.typeface=font; paint.textSize=10*d
            canvas.drawText(label,0f,17*d,paint); canvas.restore()
        }
        if(exit>=1f) { closeDone?.let { closeDone=null; post { it() } } }
        else if(isAttachedToWindow && !reducedMotion && (closingAt!=0L || elapsed<330)) postInvalidateOnAnimation()
    }
}
