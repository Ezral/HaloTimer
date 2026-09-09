package com.ezral.halo.overlay

import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils

/** Individual glyphs retain their size and follow the path tangent, including at the crop. */
internal class ContourLabel(private val paint: TextPaint) {
    private val measure=PathMeasure()
    private val pos=FloatArray(2);private val tangent=FloatArray(2)
    private var source="";private var limit=0f;private var glyphs=emptyList<String>();private var advances=FloatArray(0)
    private var textWidth=0f
    fun draw(canvas:Canvas,path:Path,label:String,elapsed:Long,speed:Float,closed:Boolean,stationary:Boolean,maxWidth:Float) {
        if(source!=label || limit!=maxWidth) {
            source=label;limit=maxWidth
            val text=TextUtils.ellipsize(label,paint,maxWidth,TextUtils.TruncateAt.END).toString()
            // Keep surrogate pairs together; shape advances with Android's font metrics.
            glyphs=text.codePoints().toArray().map { String(Character.toChars(it)) }
            advances=FloatArray(glyphs.size) { paint.measureText(glyphs[it]) };textWidth=advances.sum()
        }
        measure.setPath(path,closed);val length=measure.length
        if(length<=0) return
        var cursor=if(stationary) (length-textWidth)/2 else if(closed) ((elapsed*speed/1000)%length) else ((elapsed*speed/1000)%(length+textWidth))-textWidth
        for(i in glyphs.indices) {
            val middle=cursor+advances[i]/2
            val distance=if(closed) ((middle%length)+length)%length else middle
            if(distance in 0f..length && measure.getPosTan(distance,pos,tangent)) {
                canvas.save();canvas.translate(pos[0],pos[1]);canvas.rotate(Math.toDegrees(kotlin.math.atan2(tangent[1],tangent[0]).toDouble()).toFloat())
                canvas.drawText(glyphs[i],-advances[i]/2,0f,paint);canvas.restore()
            }
            cursor+=advances[i]
        }
    }
}
