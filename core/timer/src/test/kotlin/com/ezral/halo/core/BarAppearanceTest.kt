package com.ezral.halo.core

import org.junit.Assert.*
import org.junit.Test

class BarAppearanceTest {
    @Test fun independentAppearanceCommandsPreserveTheOtherChoiceAndRunningSession() {
        val engine=TimerEngine { "bar" }
        var s=engine.apply(Snapshot(),Command.Start(setOf(0)),0).snapshot
        val session=s.tracks[0].session
        s=engine.apply(s,Command.BarAppearance(0,showName=false),1).snapshot
        s=engine.apply(s,Command.BarAppearance(0,rotateText=true),2).snapshot
        assertFalse(s.tracks[0].definition.showBarName)
        assertTrue(s.tracks[0].definition.rotateBarText)
        assertEquals(session,s.tracks[0].session)
        assertFalse(s.tracks[1].definition.rotateBarText)
        s=engine.apply(s,Command.BarAppearance(0,showName=true),3).snapshot
        assertTrue(s.tracks[0].definition.rotateBarText)
        assertTrue(s.tracks[0].definition.showBarName)
    }
}
