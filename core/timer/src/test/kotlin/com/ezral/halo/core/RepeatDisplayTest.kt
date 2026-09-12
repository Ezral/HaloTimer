package com.ezral.halo.core

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RepeatDisplayTest {
    private val engine = TimerEngine { "run" }
    private fun start(d: Definition, now: Long=0) = engine.apply(Snapshot(tracks=listOf(Track(d))),Command.Start(setOf(0)),now).snapshot
    @Test fun defaultsAndSerializationPreserveOldTimers() {
        val d = Json.decodeFromString<Definition>("""{"id":0,"color":4278255615}""")
        assertEquals(1,d.repetitions); assertEquals(LinePalette.SOLID,d.linePalette)
        val custom=d.copy(repetitions=0,customColor=0xFF00FFFF,linePalette=LinePalette.AURORA)
        assertEquals(custom,Json.decodeFromString<Definition>(Json.encodeToString(Definition.serializer(),custom)))
        assertNotNull(d.copy(repetitions=10000).error())
    }
    @Test fun singleRunsExactlyThreeTimesAndKeepsUniqueAlerts() {
        var state=start(Definition(0,durationMs=1000,repetitions=3))
        val ids=mutableSetOf<String>()
        for(round in 1..3) {
            state=engine.reconcile(state.copy(outbox=emptyList()),round*1000L)
            assertEquals(1,state.outbox.size);assertTrue(ids.add(state.outbox.single().id))
            val s=state.tracks.single().session!!
            assertEquals(if(round==3) Status.COMPLETED else Status.RUNNING,s.status)
            assertEquals(round==3,state.outbox.single().final)
        }
        assertEquals(3,state.tracks.single().session!!.round)
    }
    @Test fun entireSequenceRepeatsThenCompletesAtOriginalDeadline() {
        val d=Definition(0,sequence=true,steps=listOf(Step("Bloom",1000),Step("Pour",2000)),repetitions=2)
        var state=start(d)
        state=engine.reconcile(state,1000);assertEquals(1,state.tracks.single().session!!.index)
        state=engine.reconcile(state,3000);assertEquals(0,state.tracks.single().session!!.index);assertEquals(2,state.tracks.single().session!!.round)
        state=engine.reconcile(state,6000);assertEquals(Status.COMPLETED,state.tracks.single().session!!.status)
        assertEquals(6000,state.tracks.single().session!!.deadlineMs)
    }
    @Test(timeout=1000) fun infiniteRecoverySkipsYearsWithoutAReplayBacklog() {
        val state=start(Definition(0,durationMs=1000,repetitions=0))
        val years=365L*24*3600*1000*10
        val recovered=engine.reconcile(state,years+500)
        val s=recovered.tracks.single().session!!
        assertEquals(Status.RUNNING,s.status);assertEquals(years/1000+1,s.round);assertEquals(500,s.remaining(years+500))
        assertEquals(1,recovered.outbox.size);assertFalse(recovered.outbox.single().final)
        assertNull(engine.apply(recovered,Command.Reset(0),years+500).snapshot.tracks.single().session)
    }
    @Test fun pauseRewindAndConfigurationChangesRespectRoundState() {
        var state=engine.reconcile(start(Definition(0,durationMs=1000,repetitions=5)),2300)
        state=engine.apply(state,Command.Pause(0),2300).snapshot
        assertEquals(700,state.tracks.single().session!!.remainingMs)
        val blocked=engine.apply(state,Command.Edit(state.tracks.single().definition.copy(repetitions=2)),9000)
        assertNotNull(blocked.error)
        state=engine.apply(state,Command.Start(setOf(0)),10000).snapshot
        assertEquals(10700,state.tracks.single().session!!.deadlineMs)
        state=engine.apply(state,Command.Rewind(0),10100).snapshot
        assertEquals(1,state.tracks.single().session!!.round);assertEquals(5,state.tracks.single().session!!.repetitions)
        assertEquals(Status.PAUSED,state.tracks.single().session!!.status)
    }
    @Test fun adjustmentOnlyChangesTheCurrentRound() {
        var state=start(Definition(0,durationMs=1000,repetitions=3))
        state=engine.apply(state,Command.Adjust(AdjustmentTarget(0,"run",0),1000),0).snapshot
        state=engine.reconcile(state,2000)
        assertEquals(2,state.tracks.single().session!!.round);assertEquals(3000,state.tracks.single().session!!.deadlineMs)
        assertEquals(Status.COMPLETED,engine.reconcile(state,4000).tracks.single().session!!.status)
    }
    @Test fun firstKeystrokeReplacesInsteadOfAppending() {
        assertEquals("7",replacementDigits("05","057"))
        assertEquals("7",replacementDigits("05","705"))
        assertEquals("0",replacementDigits("05","0"))
        assertEquals("12",replacementDigits("05","12"))
        assertEquals("",replacementDigits("05",""))
    }
    @Test fun panelsCoverTheScreenAndContainTheirOwnAnchors() {
        for(n in 1..3) for(wide in listOf(false,true)) {
            val panels=timerPanels(n,wide)
            assertEquals(n,panels.size)
            fun contains(p:TimerPanel,x:Float,y:Float):Boolean {
                var inside=false;var j=p.outline.lastIndex
                p.outline.forEachIndexed { i,a -> val b=p.outline[j]
                    if((a.y>y)!=(b.y>y) && x<(b.x-a.x)*(y-a.y)/(b.y-a.y)+a.x) inside=!inside
                    j=i
                };return inside
            }
            panels.forEach { assertTrue(contains(it,it.x,it.y)) }
            for(x in 1..19) for(y in 1..19) assertEquals(1,panels.count { contains(it,(x+.17f)/20,(y+.13f)/20) })
        }
    }
}
