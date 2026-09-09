package com.ezral.halo.overlay

import android.graphics.Path
import android.graphics.PathMeasure
import com.ezral.halo.core.DockSide

/** Parallel path sampled from the dock's exact two cubic curves, rather than a circular proxy. */
internal object DockLabelPath {
    fun build(out:Path,edge:Float,cy:Float,d:Float,side:DockSide) {
        val left=side==DockSide.LEFT;val sign=if(left) 1f else -1f
        val h=82*d;val tip=edge+sign*44*d
        val startY=cy-sign*h;val endY=cy+sign*h
        val curve=Path().apply {
            moveTo(edge,startY)
            cubicTo(edge,startY+sign*h*.52f,tip,cy-sign*h*.52f,tip,cy)
            cubicTo(tip,cy+sign*h*.52f,edge,endY-sign*h*.52f,edge,endY)
        }
        val measure=PathMeasure(curve,false);val pos=FloatArray(2);val tangent=FloatArray(2)
        val gap=8*d;val x=edge+sign*gap
        // The short entrance/exit travels behind the physical edge. The visible run is a
        // constant-distance offset of the body, including its shoulder and shallow tip.
        out.reset();out.moveTo(edge-sign*24*d,startY-sign*16*d)
        out.cubicTo(x,startY-sign*16*d,x,startY-sign*8*d,x,startY)
        for(i in 0..160) {
            measure.getPosTan(measure.length*i/160f,pos,tangent)
            out.lineTo(pos[0]+tangent[1]*gap,pos[1]-tangent[0]*gap)
        }
        out.cubicTo(x,endY+sign*8*d,x,endY+sign*16*d,edge-sign*24*d,endY+sign*16*d)
    }
}
