package com.ezral.halo.core

data class PanelPoint(val x: Float, val y: Float)
data class TimerPanel(val outline: List<PanelPoint>, val x: Float, val y: Float, val width: Float, val height: Float)

/** Shared normalized geometry: three equal angular slices, rotated for landscape. */
fun timerPanels(count: Int, landscape: Boolean, pizza: Boolean = true): List<TimerPanel> {
    require(count in 1..3)
    fun points(vararg xy: Float) = xy.toList().chunked(2).map { PanelPoint(it[0], it[1]) }
    if (count == 1) return listOf(TimerPanel(points(0f,0f,1f,0f,1f,1f,0f,1f), .5f,.5f,.9f,.86f))
    if (count == 2) return if (landscape) listOf(
        TimerPanel(points(0f,0f,.5f,0f,.5f,1f,0f,1f),.25f,.5f,.45f,.86f),
        TimerPanel(points(.5f,0f,1f,0f,1f,1f,.5f,1f),.75f,.5f,.45f,.86f))
    else listOf(TimerPanel(points(0f,0f,1f,0f,1f,.5f,0f,.5f),.5f,.25f,.9f,.43f),
        TimerPanel(points(0f,.5f,1f,.5f,1f,1f,0f,1f),.5f,.75f,.9f,.43f))
    if (!pizza) return if (landscape) listOf(
        TimerPanel(points(0f,0f,1f/3f,0f,1f/3f,1f,0f,1f),1f/6f,.5f,.29f,.86f),
        TimerPanel(points(1f/3f,0f,2f/3f,0f,2f/3f,1f,1f/3f,1f),.5f,.5f,.29f,.86f),
        TimerPanel(points(2f/3f,0f,1f,0f,1f,1f,2f/3f,1f),5f/6f,.5f,.29f,.86f))
    else listOf(
        TimerPanel(points(0f,0f,1f,0f,1f,1f/3f,0f,1f/3f),.5f,1f/6f,.9f,.28f),
        TimerPanel(points(0f,1f/3f,1f,1f/3f,1f,2f/3f,0f,2f/3f),.5f,.5f,.9f,.28f),
        TimerPanel(points(0f,2f/3f,1f,2f/3f,1f,1f,0f,1f),.5f,5f/6f,.9f,.28f))
    val slices = listOf(
        TimerPanel(points(0f,0f,1f,0f,1f,.211325f,.5f,.5f,0f,.211325f),.5f,.18f,.88f,.34f),
        TimerPanel(points(.5f,.5f,1f,.211325f,1f,1f,.5f,1f),.76f,.72f,.46f,.50f),
        TimerPanel(points(0f,.211325f,.5f,.5f,.5f,1f,0f,1f),.24f,.72f,.46f,.50f))
    return if (!landscape) slices else slices.mapIndexed { index, p ->
        p.copy(outline = p.outline.map { PanelPoint(1f-it.y,it.x) }, x=1f-p.y,y=p.x,
            width=if(index==0) .34f else .54f, height=if(index==0) .86f else .44f)
    }
}

/** Inset each convex section in pixels. Shared dividers reserve half the requested gap. */
fun insetPanel(panel: TimerPanel, width: Float, height: Float, outer: Float, divider: Float): List<PanelPoint> {
    val points=panel.outline.map { PanelPoint(it.x*width,it.y*height) }
    data class Edge(val x:Float,val y:Float,val dx:Float,val dy:Float)
    val edges=points.indices.map { i ->
        val a=points[i];val b=points[(i+1)%points.size]
        val dx=b.x-a.x;val dy=b.y-a.y;val length=kotlin.math.hypot(dx,dy)
        val boundary=(a.x==b.x && (a.x==0f || a.x==width)) || (a.y==b.y && (a.y==0f || a.y==height))
        val distance=if(boundary) outer else divider
        Edge(a.x-dy/length*distance,a.y+dx/length*distance,dx,dy)
    }
    return edges.indices.map { i ->
        val a=edges[(i+edges.size-1)%edges.size];val b=edges[i]
        val cross=a.dx*b.dy-a.dy*b.dx
        if(kotlin.math.abs(cross)<.00001f) PanelPoint(b.x,b.y) else {
            val t=((b.x-a.x)*b.dy-(b.y-a.y)*b.dx)/cross
            PanelPoint(a.x+t*a.dx,a.y+t*a.dy)
        }
    }
}
