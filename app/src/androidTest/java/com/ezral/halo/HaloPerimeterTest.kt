package com.ezral.halo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Region
import androidx.compose.ui.graphics.asAndroidPath
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ezral.halo.core.*
import com.ezral.halo.graphics.perimeterSegment
import com.ezral.halo.overlay.EdgeView
import com.ezral.halo.ui.sectionPaths
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class HaloPerimeterTest {
    private fun paint(stroke: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = stroke
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private fun render(path: Path, width: Int, height: Int, stroke: Float) =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { Canvas(it).drawPath(path, paint(stroke)) }

    private fun nearBorder(mask: IntArray, width: Int, height: Int, index: Int): Boolean {
        val x = index % width; val y = index / width
        for (dy in -1..1) for (dx in -1..1) {
            val xx = x + dx; val yy = y + dy
            if (xx in 0 until width && yy in 0 until height && Color.alpha(mask[yy * width + xx]) > 0) return true
        }
        return false
    }

    /** Reproduce newer Skia's destination-replacement behavior on every tested OS. */
    private class ReplacingMeasure(path: Path) : PathMeasure(path, true) {
        override fun getSegment(startD: Float, stopD: Float, dst: Path, startWithMoveTo: Boolean): Boolean {
            val fresh = Path()
            val result = super.getSegment(startD, stopD, fresh, startWithMoveTo)
            if (result) dst.set(fresh)
            return result
        }
    }
    private fun totalLength(path: Path): Float {
        val m = PathMeasure(path, false)
        var length = m.length
        while (m.nextContour()) length += m.length
        return length
    }

    @Test fun replacingExtractorReproducesOldDiagonalAndExplicitAssemblyFixesIt() {
        val outline = sectionPaths(timerPanels(3, false)[1], 600f, 1000f, 8f, 10f, List(4) { 64f }).line.asAndroidPath()
        val m = ReplacingMeasure(outline)
        val broken = Path()
        m.getSegment(m.length * .9f, m.length, broken, true)
        m.getSegment(0f, m.length * .1f, broken, false)
        val bounds = RectF(); broken.computeBounds(bounds, true)
        assertEquals("Old wrap creates a line from the canvas origin", 0f, bounds.left, .001f)
        assertEquals(0f, bounds.top, .001f)
        val repaired = Path()
        perimeterSegment(m, repaired, Path(), .9f, .2f)
        repaired.computeBounds(bounds, true)
        assertTrue("Right-hand timer never starts at the canvas origin", bounds.left > 200f)
        assertEquals("Both tail and head are retained", m.length * .2f, totalLength(repaired), 1f)
        val reference = render(outline, 600, 1000, 8f)
        val mask = IntArray(600000); reference.getPixels(mask, 0, 600, 0, 0, 600, 1000); reference.recycle()
        fun offBorderPixels(path: Path): Int {
            val image = render(path, 600, 1000, 8f)
            val pixels = IntArray(600000); image.getPixels(pixels, 0, 600, 0, 0, 600, 1000); image.recycle()
            return pixels.indices.count { Color.alpha(pixels[it]) > 64 && !nearBorder(mask, 600, 1000, it) }
        }
        assertTrue("The raster guard rejects the original diagonal even with edge tolerance", offBorderPixels(broken) > 100)
        assertEquals("The repaired stroke stays on the perimeter", 0, offBorderPixels(repaired))
        val dir = java.io.File("/sdcard/Download/halo-qa").apply { mkdirs() }
        listOf("before" to broken, "after" to repaired).forEach { (name, path) ->
            val image = render(path, 600, 1000, 8f)
            java.io.File(dir, "perimeter-$name-replacement.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
    }

    @Test fun allLayoutsPhasesAndCornersStayOnTheirOwnPerimeter() {
        data class Shape(val stroke: Float, val gap: Float, val inset: Int, val corners: List<Float>)
        val shapes = listOf(
            Shape(2f, 0f, 0, List(4) { 0f }),
            Shape(8f, 6f, 4, List(4) { 24f }),
            Shape(10f, 16f, 12, listOf(8f, 24f, 32f, 16f)),
            Shape(4f, 0f, 24, List(4) { 144f })
        )
        val phases = (0..128).map { it / 128f } + listOf(-.2f, .799999f, .8f, .800001f, .80138886f, .9f, .9999f, .9999999f, 1.5f)
        val segment = Path(); val wrapped = Path(); val point = FloatArray(2)
        var checked = 0
        for (landscape in listOf(false, true)) for (count in 1..3) {
            for (pizza in if (count == 3) listOf(true, false) else listOf(true)) for (shape in shapes) {
                val width = (if (landscape) 400 else 240) - 2 * shape.inset
                val height = (if (landscape) 240 else 400) - 2 * shape.inset
                val radii = shape.corners.map { (it - shape.inset).coerceAtLeast(0f) }
                timerPanels(count, landscape, pizza).forEachIndexed { index, panel ->
                    val outline = sectionPaths(panel, width.toFloat(), height.toFloat(), shape.stroke, shape.gap, radii).line.asAndroidPath()
                    val band = Path(); paint(shape.stroke + 3f).getFillPath(outline, band)
                    val allowed = Region().apply { setPath(band, Region(-4, -4, width + 4, height + 4)) }
                    val mask = render(outline, width, height, shape.stroke + 3f)
                    val maskPixels = IntArray(width * height); mask.getPixels(maskPixels, 0, width, 0, 0, width, height)
                    try {
                        for (replacing in listOf(false, true)) {
                            val m = if (replacing) ReplacingMeasure(outline) else PathMeasure(outline, true)
                            assertTrue(m.length > 0f)
                            assertFalse("One source contour per timer", PathMeasure(outline, true).nextContour())
                            for (phase in phases) {
                                val label = "count=$count landscape=$landscape pizza=$pizza panel=$index shape=$shape replacement=$replacing phase=$phase"
                                perimeterSegment(m, segment, wrapped, phase, .2f)
                                assertEquals("$label: retain full moving length", m.length * .2f, totalLength(segment), 1f)
                                val part = PathMeasure(segment, false)
                                do {
                                    for (sample in 0..32) {
                                        assertTrue(part.getPosTan(part.length * sample / 32f, point, null))
                                        assertTrue("$label: diagonal at ${point.toList()}", allowed.contains(point[0].roundToInt(), point[1].roundToInt()))
                                    }
                                } while (part.nextContour())
                                checked++
                            }
                            // Raster checks cover both sides of the wrap and the reported failing phase.
                            for (phase in listOf(.5f, .800001f, .80138886f, .9f, .9999f)) {
                                perimeterSegment(m, segment, wrapped, phase, .2f)
                                val image = render(segment, width, height, shape.stroke)
                                val pixels = IntArray(width * height)
                                image.getPixels(pixels, 0, width, 0, 0, width, height); image.recycle()
                                for (i in pixels.indices) if (Color.alpha(pixels[i]) > 32) {
                                    assertTrue("No off-border pixel: panel=$index phase=$phase replacement=$replacing", Color.alpha(maskPixels[i]) > 0)
                                }
                            }
                            for (fraction in listOf(0f, .0001f, .01f, .5f, .9999f, 1f)) {
                                perimeterSegment(m, segment, wrapped, 0f, fraction)
                                assertEquals("Countdown/full outline length", m.length * fraction, totalLength(segment), 1f)
                            }
                        }
                    } finally { mask.recycle() }
                }
            }
        }
        println("Verified $checked moving perimeter cases across every layout, position, shape and extraction behavior")
    }

    @Test fun wrappedStrokeIsContinuousAndZeroLengthNeverReusesOldPixels() {
        val outline = sectionPaths(timerPanels(3, false)[1], 600f, 1000f, 8f, 10f, List(4) { 64f }).line.asAndroidPath()
        val segment = Path(); val wrapped = Path()
        for (m in listOf(PathMeasure(outline, true), ReplacingMeasure(outline))) {
            perimeterSegment(m, segment, wrapped, .9999f, .2f); val before = render(segment, 600, 1000, 8f)
            perimeterSegment(m, segment, wrapped, .0001f, .2f); val after = render(segment, 600, 1000, 8f)
            val a = IntArray(600000); val b = IntArray(600000)
            before.getPixels(a, 0, 600, 0, 0, 600, 1000); after.getPixels(b, 0, 600, 0, 0, 600, 1000)
            before.recycle(); after.recycle()
            assertTrue("No visual restart at the loop boundary", a.indices.count { a[it] != b[it] } < 1000)
            perimeterSegment(m, segment, wrapped, .9f, 0f)
            assertTrue(segment.isEmpty); assertTrue(wrapped.isEmpty)
            perimeterSegment(m, segment, wrapped, Float.NaN, .2f)
            assertTrue(segment.isEmpty)
        }
    }

    @Test fun threePerimetersStaySeparateAndFollowPhysicalCorners() {
        val paths = timerPanels(3, false).map { sectionPaths(it, 600f, 1000f, 8f, 10f, listOf(64f, 64f, 100f, 100f)) }
        val images = paths.map { render(it.line.asAndroidPath(), 600, 1000, 8f) }
        val pixels = images.map { image -> IntArray(600000).also { image.getPixels(it, 0, 600, 0, 0, 600, 1000) } }
        try {
            for (i in 0 until 600000) assertTrue("Timer lines overlap at pixel $i", pixels.count { Color.alpha(it[i]) > 20 } <= 1)
            assertTrue("Lower corner follows an arc", pixels.any { p -> (30..34).any { x -> (966..970).any { y -> Color.alpha(p[y * 600 + x]) > 100 } } })
            listOf(0 to 0, 599 to 999, 3 to 990).forEach { (x, y) -> assertTrue(pixels.all { Color.alpha(it[y * 600 + x]) == 0 }) }
        } finally { images.forEach { it.recycle() } }
    }

    @Test fun actualOverlayRendererKeepsEveryStyleAndLaneOutOfTheInterior() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = EdgeView(instrumentation.targetContext)
            var now = 1000L; view.clock = { now }
            var checkedFrames = 0
            for (landscape in listOf(false, true)) for (count in 1..3) {
                val width = if (landscape) 400 else 240; val height = if (landscape) 240 else 400
                view.layout(0, 0, width, height)
                fun tracks(style: AlertStyle) = (0 until count).map { id -> Track(Definition(id, active = true, alert = style, glow = 0f),
                    Session("edge-$id", listOf(Step()), status = Status.COMPLETED, deadlineMs = 1000,
                        visualUntilMs = Long.MAX_VALUE, alertStartedAtMs = 1000)) }
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val pixels = IntArray(width * height); val mask = IntArray(width * height)
                try {
                    view.tracks = tracks(AlertStyle.ORBIT); view.reducedMotion = true
                    view.draw(Canvas(bitmap)); bitmap.getPixels(mask, 0, width, 0, 0, width, height)
                    view.reducedMotion = false
                    for (style in AlertStyle.entries) {
                        view.tracks = tracks(style)
                        for (time in listOf(0L, 700, 2241, 2380, 2520, 2799, 2801, 3599, 3600, 7199)) {
                            now = 1000 + time; bitmap.eraseColor(Color.TRANSPARENT); view.draw(Canvas(bitmap))
                            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                            // Subdividing a curved stroke can shift antialiased coverage by
                            // one pixel. The reproduced failure at (55,13) was one such pixel;
                            // no long/interior stroke is permitted by this neighborhood check.
                            for (i in pixels.indices) if (Color.alpha(pixels[i]) > 64 && Color.alpha(mask[i]) == 0) {
                                assertTrue("Overlay diagonal: style=$style lanes=$count landscape=$landscape time=$time pixel=$i", nearBorder(mask, width, height, i))
                            }
                            checkedFrames++
                        }
                    }
                } finally { bitmap.recycle() }
            }
            println("Verified $checkedFrames actual overlay frames across all styles, lane counts and orientations")
        }
    }
}
