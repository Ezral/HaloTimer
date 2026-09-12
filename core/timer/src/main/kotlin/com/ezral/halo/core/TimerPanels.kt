package com.ezral.halo.core

data class PanelPoint(val x: Float, val y: Float)
data class TimerPanel(val outline: List<PanelPoint>, val x: Float, val y: Float, val width: Float, val height: Float)

/** Shared normalized geometry: three equal angular slices, rotated for landscape. */
fun timerPanels(count: Int, landscape: Boolean): List<TimerPanel> {
    require(count in 1..3)
    fun points(vararg xy: Float) = xy.toList().chunked(2).map { PanelPoint(it[0], it[1]) }
    if (count == 1) return listOf(TimerPanel(points(0f,0f,1f,0f,1f,1f,0f,1f), .5f,.5f,.9f,.86f))
    if (count == 2) return if (landscape) listOf(
        TimerPanel(points(0f,0f,.5f,0f,.5f,1f,0f,1f),.25f,.5f,.45f,.86f),
        TimerPanel(points(.5f,0f,1f,0f,1f,1f,.5f,1f),.75f,.5f,.45f,.86f))
    else listOf(TimerPanel(points(0f,0f,1f,0f,1f,.5f,0f,.5f),.5f,.25f,.9f,.43f),
        TimerPanel(points(0f,.5f,1f,.5f,1f,1f,0f,1f),.5f,.75f,.9f,.43f))
    val slices = listOf(
        TimerPanel(points(0f,0f,1f,0f,1f,.211325f,.5f,.5f,0f,.211325f),.5f,.18f,.88f,.34f),
        TimerPanel(points(.5f,.5f,1f,.211325f,1f,1f,.5f,1f),.76f,.72f,.46f,.50f),
        TimerPanel(points(0f,.211325f,.5f,.5f,.5f,1f,0f,1f),.24f,.72f,.46f,.50f))
    return if (!landscape) slices else slices.mapIndexed { index, p ->
        p.copy(outline = p.outline.map { PanelPoint(1f-it.y,it.x) }, x=1f-p.y,y=p.x,
            width=if(index==0) .34f else .54f, height=if(index==0) .86f else .44f)
    }
}
