package com.ezral.halo.ui

import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.os.Build
import android.view.RoundedCorner
import android.view.ViewTreeObserver
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import com.ezral.halo.core.*

internal data class PhoneShape(val corners: List<Float>, val waterfall: List<Float>)

/** Read real per-corner radii and curved-glass insets again on layout/rotation changes. */
@Composable internal fun rememberPhoneShape(): PhoneShape {
    val view=LocalView.current;val density=LocalDensity.current.density
    var shape by remember(view,density) { mutableStateOf(PhoneShape(List(4){28f},List(4){0f})) }
    DisposableEffect(view,density) {
        fun update() {
            val wi=view.rootWindowInsets ?: return
            val corners=if(Build.VERSION.SDK_INT>=31) listOf(RoundedCorner.POSITION_TOP_LEFT,RoundedCorner.POSITION_TOP_RIGHT,
                RoundedCorner.POSITION_BOTTOM_RIGHT,RoundedCorner.POSITION_BOTTOM_LEFT).map { (wi.getRoundedCorner(it)?.radius ?: 0)/density }
                else List(4){28f}
            val waterfall=if(Build.VERSION.SDK_INT>=30) wi.displayCutout?.waterfallInsets?.let { listOf(it.left,it.top,it.right,it.bottom).map { n -> n/density } } else null
            shape=PhoneShape(corners,waterfall ?: List(4){0f})
        }
        val listener=ViewTreeObserver.OnGlobalLayoutListener { update() }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener);update()
        onDispose { if(view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return shape
}

internal data class SectionPaths(val fill: Path, val line: Path)

/** Intersect the inset perimeter with the curved viewport: clipping a square stroke would lose its corners. */
internal fun sectionPaths(panel:TimerPanel,width:Float,height:Float,lineWidth:Float,gap:Float,radii:List<Float>):SectionPaths {
    fun polygon(points:List<PanelPoint>)=AndroidPath().apply {
        points.forEachIndexed { i,p -> if(i==0) moveTo(p.x,p.y) else lineTo(p.x,p.y) };close()
    }
    fun frame(inset:Float)=AndroidPath().apply {
        val limit=(minOf(width,height)/2-inset).coerceAtLeast(0f)
        val corners=radii.flatMap { r -> val value=(r-inset).coerceIn(0f,limit);listOf(value,value) }.toFloatArray()
        addRoundRect(RectF(inset,inset,width-inset,height-inset),corners,AndroidPath.Direction.CW)
    }
    val fill=polygon(panel.outline.map { PanelPoint(it.x*width,it.y*height) }).apply { op(frame(0f),AndroidPath.Op.INTERSECT) }
    val half=lineWidth/2
    val line=polygon(insetPanel(panel,width,height,half,half+gap/2)).apply { op(frame(half),AndroidPath.Op.INTERSECT) }
    return SectionPaths(fill.asComposePath(),line.asComposePath())
}
