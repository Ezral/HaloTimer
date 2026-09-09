package com.ezral.halo.overlay

import android.graphics.Path
import android.graphics.RectF
import com.ezral.halo.core.DockSide

/** Matching cubic topology for a rounded rectangle, circle and the single shallow dock. */
internal object SurfaceContour {
    fun points(bounds: RectF, side: DockSide = DockSide.NONE): FloatArray {
        val x=bounds.centerX(); val y=bounds.centerY()
        val l=bounds.left; val r=bounds.right; val t=bounds.top; val b=bounds.bottom
        if(side!=DockSide.NONE) {
            val edge=if(side==DockSide.LEFT) l else r
            val tip=if(side==DockSide.LEFT) r else l
            val h=bounds.height()/2
            val base=if(side==DockSide.RIGHT) floatArrayOf(edge,t, edge,t, edge,y, edge,y,
                edge,y, edge,b, edge,b, edge,b-h*.52f, tip,y+h*.52f, tip,y,
                tip,y-h*.52f, edge,t+h*.52f, edge,t)
            else floatArrayOf(edge,t, edge,t+h*.52f, tip,y-h*.52f, tip,y,
                tip,y+h*.52f, edge,b-h*.52f, edge,b,
                edge,b, edge,y, edge,y, edge,y, edge,t, edge,t)
            // Exact subdivision preserves the approved dock shape while matching the bar's topology.
            val out=mutableListOf(base[0],base[1])
            for(i in 2 until base.size step 6) {
                val sx=base[i-2]; val sy=base[i-1]
                fun at(u:Float, axis:Int):Float {
                    val v=1-u; val start=if(axis==0) sx else sy
                    return v*v*v*start+3*v*v*u*base[i+axis]+3*v*u*u*base[i+2+axis]+u*u*u*base[i+4+axis]
                }
                fun slope(u:Float,axis:Int):Float {
                    val v=1-u;val start=if(axis==0) sx else sy
                    return 3*v*v*(base[i+axis]-start)+6*v*u*(base[i+2+axis]-base[i+axis])+3*u*u*(base[i+4+axis]-base[i+2+axis])
                }
                for(j in 0..2) {
                    val a=j/3f;val z=(j+1)/3f
                    out+=at(a,0)+slope(a,0)/9;out+=at(a,1)+slope(a,1)/9
                    out+=at(z,0)-slope(z,0)/9;out+=at(z,1)-slope(z,1)/9
                    out+=at(z,0);out+=at(z,1)
                }
            }
            return out.toFloatArray()
        }
        val radius=minOf(bounds.width(),bounds.height())*(if(kotlin.math.abs(bounds.width()-bounds.height())<1f) .5f else .32f)
        val k=.5522848f;val out=mutableListOf(x,t)
        var px=x;var py=t
        fun cubic(a:Float,b:Float,c:Float,d:Float,e:Float,f:Float) { out.addAll(listOf(a,b,c,d,e,f));px=e;py=f }
        fun line(ex:Float,ey:Float) = cubic(px+(ex-px)/3,py+(ey-py)/3,px+2*(ex-px)/3,py+2*(ey-py)/3,ex,ey)
        line(r-radius,t);cubic(r-radius+k*radius,t,r,t+radius-k*radius,r,t+radius);line(r,y)
        line(r,b-radius);cubic(r,b-radius+k*radius,r-radius+k*radius,b,r-radius,b);line(x,b)
        line(l+radius,b);cubic(l+radius-k*radius,b,l,b-radius+k*radius,l,b-radius);line(l,y)
        line(l,t+radius);cubic(l,t+radius-k*radius,l+radius-k*radius,t,l+radius,t);line(x,t)
        return out.toFloatArray()
    }
    fun path(out: Path, from: FloatArray, to: FloatArray = from, progress: Float = 0f) {
        fun v(i: Int)=from[i]+(to[i]-from[i])*progress
        out.reset(); out.moveTo(v(0),v(1))
        for(i in 2 until from.size step 6) out.cubicTo(v(i),v(i+1),v(i+2),v(i+3),v(i+4),v(i+5))
        out.close()
    }
}
