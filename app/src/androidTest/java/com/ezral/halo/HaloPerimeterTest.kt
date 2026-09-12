package com.ezral.halo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.asAndroidPath
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ezral.halo.core.timerPanels
import com.ezral.halo.ui.sectionPaths
import com.ezral.halo.ui.perimeterSegment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HaloPerimeterTest {
    @Test fun threeAnimatedPerimetersStaySeparateAndFollowPhysicalCorners() {
        val width=600;val height=1000;val stroke=8f
        val paths=timerPanels(3,false).map { sectionPaths(it,width.toFloat(),height.toFloat(),stroke,10f,listOf(64f,64f,100f,100f)) }
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;style=Paint.Style.STROKE;strokeWidth=stroke;strokeJoin=Paint.Join.ROUND }
        fun render(path:Path)=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also { Canvas(it).drawPath(path.asAndroidPath(),paint) }
        val images=paths.map { render(it.line) }
        try {
            for(y in 0 until height) for(x in 0 until width) {
                assertTrue("Timer lines overlap at $x,$y",images.count { Color.alpha(it.getPixel(x,y))>20 } <=1)
            }
            assertTrue("Lower corner follows an arc instead of disappearing under a square clip",
                images.any { image -> (30..34).any { x -> (966..970).any { y -> Color.alpha(image.getPixel(x,y))>100 } } })
            listOf(0 to 0,599 to 999,3 to 990).forEach { (x,y) -> assertTrue(images.all { Color.alpha(it.getPixel(x,y))==0 }) }
            paths.forEach { p ->
                val native=android.graphics.PathMeasure(p.line.asAndroidPath(),true)
                assertTrue(native.length>0);assertFalse("One continuous perimeter per section",native.nextContour())
                val measure=PathMeasure().apply { setPath(p.line,true) }
                val segment=Path()
                perimeterSegment(measure,segment,.9999f,.20f);val before=render(segment)
                perimeterSegment(measure,segment,.0001f,.20f);val after=render(segment)
                try {
                    var changed=0
                    for(y in 0 until height) for(x in 0 until width) if(before.getPixel(x,y)!=after.getPixel(x,y)) changed++
                    assertTrue("Orbit crosses its seam without a jump",changed<1000)
                } finally { before.recycle();after.recycle() }
            }
        } finally { images.forEach { it.recycle() } }
    }
}
