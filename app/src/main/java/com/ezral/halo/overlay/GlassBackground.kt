package com.ezral.halo.overlay

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

/** Insets the glass shape without adding content padding to a floating Window. */
internal class GlassBackground(color: Int, radius: Float, private val inset: Int = 0) : Drawable() {
    private val shape = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    override fun onBoundsChange(bounds: Rect) {
        shape.setBounds(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset)
    }
    override fun draw(canvas: Canvas) = shape.draw(canvas)
    override fun getOutline(outline: Outline) = shape.getOutline(outline)
    override fun getPadding(padding: Rect): Boolean { padding.setEmpty(); return false }
    override fun setAlpha(alpha: Int) { shape.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { shape.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
