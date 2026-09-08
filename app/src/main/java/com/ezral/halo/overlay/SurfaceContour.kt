package com.ezral.halo.overlay

import android.graphics.Path
import android.graphics.RectF
import com.ezral.halo.core.DockSide

/** Four cubic quadrants, shared by the static body, live pull and release morph.
 * Interpolating control points keeps one closed, seam-free silhouette throughout. */
internal object SurfaceContour {
    fun points(bounds: RectF, side: DockSide = DockSide.NONE): FloatArray {
        val x=bounds.centerX(); val y=bounds.centerY()
        val l=bounds.left; val r=bounds.right; val t=bounds.top; val b=bounds.bottom
        if(side!=DockSide.NONE) {
            val edge=if(side==DockSide.LEFT) l else r
            val tip=if(side==DockSide.LEFT) r else l
            val h=bounds.height()/2
            // Vertical tangents at the edge, horizontal tangent at the broad, shallow tip.
            if(side==DockSide.RIGHT) return floatArrayOf(edge,t, edge,t, edge,y, edge,y,
                edge,y, edge,b, edge,b, edge,b-h*.52f, tip,y+h*.52f, tip,y,
                tip,y-h*.52f, edge,t+h*.52f, edge,t)
            return floatArrayOf(edge,t, edge,t+h*.52f, tip,y-h*.52f, tip,y,
                tip,y+h*.52f, edge,b-h*.52f, edge,b,
                edge,b, edge,y, edge,y, edge,y, edge,t, edge,t)
        }
        val radius=minOf(bounds.width(),bounds.height())/2
        val k=.5522848f
        return floatArrayOf(x,t, r-radius+k*radius,t, r,y-k*radius, r,y,
            r,y+k*radius, r-radius+k*radius,b, x,b,
            l+radius-k*radius,b, l,y+k*radius, l,y,
            l,y-k*radius, l+radius-k*radius,t, x,t)
    }
    fun path(out: Path, from: FloatArray, to: FloatArray = from, progress: Float = 0f) {
        fun v(i: Int)=from[i]+(to[i]-from[i])*progress
        out.reset(); out.moveTo(v(0),v(1))
        for(i in 2 until from.size step 6) out.cubicTo(v(i),v(i+1),v(i+2),v(i+3),v(i+4),v(i+5))
        out.close()
    }
}
