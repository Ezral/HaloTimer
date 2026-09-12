package com.ezral.halo.overlay

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import com.ezral.halo.R
import com.ezral.halo.core.Track
import com.ezral.halo.core.CompletionMotion
import com.ezral.halo.data.Preferences
import kotlin.math.hypot

/** A finite, tap-dismissible reveal. Dismissing the page leaves the normal completion controls. */
internal class CompletionView(context:Context,private val track:Track,private val origin:PointF,
    private val preferences:Preferences,private val finished:()->Unit):View(context) {
    internal var clock:()->Long={SystemClock.elapsedRealtime()}
    internal var startedAt=SystemClock.elapsedRealtime()
    private val d=resources.displayMetrics.density
    private val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=HaloGlass.color(track.definition.color.toInt()) }
    private val textPaint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color=HaloGlass.foreground(track.definition.color.toInt())
        textSize=preferences.completionTextSp*resources.displayMetrics.scaledDensity
        typeface=resources.getFont(if(preferences.completionBold) R.font.poppins_bold else R.font.poppins_regular)
    }
    private var layout:StaticLayout?=null
    private var ended=false
    private val motion=CompletionMotion(preferences.completionSeconds*1000L, if(preferences.reducedMotion) 0 else 420)
    private val revealPath=Path()
    private val message=buildString {
        append(if(track.session?.status == com.ezral.halo.core.Status.RUNNING) "Round ${(track.session?.round ?: 2) - 1} completed\n" else "Timer is completed for\n");append(track.definition.name)
        if(track.definition.sequence) track.session?.steps?.lastOrNull()?.name?.takeIf { it.isNotBlank() }?.let { append(" · ");append(it) }
    }
    init {
        contentDescription="$message. Tap to close completion screen."
        isClickable=true;isFocusable=true;setOnClickListener { motion.close((clock()-startedAt).coerceAtLeast(0)); invalidate() }
    }
    private fun finish() { if(!ended) { ended=true;post { finished() } } }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        layout=StaticLayout.Builder.obtain(message,0,message.length,textPaint,(w-64*d).toInt().coerceAtLeast(1))
            .setAlignment(if(preferences.completionAlignment=="Left") Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false).setLineSpacing(8*d,1f).build()
    }
    override fun onDraw(canvas:Canvas) {
        val elapsed=(clock()-startedAt).coerceAtLeast(0)
        if(motion.finished(elapsed)) { finish();return }
        val p=motion.progress(elapsed)
        val radius=maxOf(hypot(origin.x,origin.y),hypot(width-origin.x,origin.y),hypot(origin.x,height-origin.y),hypot(width-origin.x,height-origin.y))
        revealPath.reset(); revealPath.addCircle(origin.x,origin.y,radius*p,Path.Direction.CW)
        canvas.save(); canvas.clipPath(revealPath)
        canvas.drawColor(fill.color)
        layout?.let { text ->
            canvas.save();canvas.translate(32*d,(height-text.height)/2f);text.draw(canvas);canvas.restore()
        }
        canvas.restore()
        if(isAttachedToWindow) postInvalidateOnAnimation()
    }
}
