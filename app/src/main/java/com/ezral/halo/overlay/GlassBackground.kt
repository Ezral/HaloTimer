package com.ezral.halo.overlay

import android.graphics.*
import android.graphics.drawable.Drawable
import com.ezral.halo.core.DockSide

/** One opaque pill; its near edge extends by four pixels as the pointer approaches the edge. */
internal class GlassBackground(color: Int, private val radius: Float) : Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=HaloGlass.color(color) }
    private val rect=RectF()
    private val path=Path()
    var approach=0f
    var side=DockSide.NONE
    override fun draw(canvas: Canvas) {
        val inset=radius/7
        rect.set(bounds.left+inset*(if(side==DockSide.LEFT) 1-approach else 1f),bounds.top.toFloat(),
            bounds.right-inset*(if(side==DockSide.RIGHT) 1-approach else 1f),bounds.bottom.toFloat())
        SurfaceContour.path(path,SurfaceContour.points(rect)); canvas.drawPath(path,paint)
    }
    override fun getPadding(padding: Rect): Boolean { padding.setEmpty(); return false }
    override fun setAlpha(alpha: Int) { paint.alpha=alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter=colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
