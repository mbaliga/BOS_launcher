package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 28 (commsLayout): the Comms size detents, clamping, rounding and parsing of stored garbage. */
class CommsScaleTest {
    @Test fun CMS_01_elevenDetentsFrom75To125In5Steps() {
        assertEquals(listOf(75, 80, 85, 90, 95, 100, 105, 110, 115, 120, 125), CommsScale.steps)
        assertEquals(11, CommsScale.steps.size)
        assertEquals(100, CommsScale.DEFAULT)
    }

    @Test fun CMS_02_stepMovesOneDetentAndStopsAtTheEnds() {
        assertEquals(105, CommsScale.step(100, +1))
        assertEquals(95, CommsScale.step(100, -1))
        assertEquals(125, CommsScale.step(125, +1))
        assertEquals(75, CommsScale.step(75, -1))
        assertEquals(75, CommsScale.step(60, -1))
        assertEquals(125, CommsScale.step(500, +1))
    }

    @Test fun CMS_03_snapRoundsToTheNearestDetentHalvesUpAndClamps() {
        assertEquals(100, CommsScale.snap(102))
        assertEquals(105, CommsScale.snap(103))
        assertEquals(75, CommsScale.snap(-40))
        assertEquals(125, CommsScale.snap(999))
        for (p in 0..200) assertTrue("snap($p) is a detent", CommsScale.snap(p) in CommsScale.steps)
    }

    @Test fun CMS_04_parseOfGarbageIsTheDefaultAndNumbersSnap() {
        assertEquals(100, CommsScale.parse(null))
        assertEquals(100, CommsScale.parse(""))
        assertEquals(100, CommsScale.parse("big"))
        assertEquals(100, CommsScale.parse("NaN"))
        assertEquals(100, CommsScale.parse("Infinity"))
        assertEquals(110, CommsScale.parse("110"))
        assertEquals(110, CommsScale.parse(" 110% "))
        assertEquals(75, CommsScale.parse("3"))
        assertEquals(125, CommsScale.parse("4000"))
        assertEquals(85, CommsScale.parse("86.4"))
    }

    @Test fun CMS_05_factorIsPercentOver100() {
        assertEquals(0.75f, CommsScale.factor(75), 0f)
        assertEquals(1f, CommsScale.factor(100), 0f)
        assertEquals(1.25f, CommsScale.factor(125), 0f)
        assertEquals(1f, CommsScale.factor(101), 0f)
    }
}
