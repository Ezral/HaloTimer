package com.ezral.halo.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class LabelOrbitTest {
    @Test fun fullCircleCrossesSeamAtConstantSpeed() {
        val before=LabelOrbit.fullRadians(5999); val after=LabelOrbit.fullRadians(6001)
        assertEquals(2*Math.PI/3000,after+2*Math.PI-before,1e-9)
        assertEquals(LabelOrbit.fullRadians(1234),LabelOrbit.fullRadians(7234),1e-9)
    }
    @Test fun dockOnlyResetsOnceTheWholeStringHasExited() {
        val start=-Math.PI/2; val visible=Math.PI; val text=Math.PI/3
        assertEquals(start-text,LabelOrbit.dockedRadians(0,start,visible,text),1e-9)
        // Four seconds: 180 degrees of visible arc plus 60 degrees of text.
        assertTrue(LabelOrbit.dockedRadians(3999,start,visible,text)>start+visible-.002)
        assertEquals(start-text+Math.PI/3000,LabelOrbit.dockedRadians(4001,start,visible,text),1e-9)
        assertEquals(Math.PI/3000,LabelOrbit.dockedRadians(1001,start,visible,text)-LabelOrbit.dockedRadians(1000,start,visible,text),1e-9)
    }
    @Test fun bothEdgesAndDifferentLabelLengthsHaveTheSameAngularSpeed() {
        for(start in listOf(-1.2,1.94)) for(text in listOf(.4,1.5,2.2)) {
            val delta=LabelOrbit.dockedRadians(501,start,2.4,text)-LabelOrbit.dockedRadians(500,start,2.4,text)
            assertEquals(Math.PI/3000,delta,1e-9)
        }
    }
}
