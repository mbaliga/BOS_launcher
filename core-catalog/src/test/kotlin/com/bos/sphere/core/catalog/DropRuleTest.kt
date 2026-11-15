package com.bos.sphere.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-04: drop-onto against reorder never fights. */
class DropRuleTest {
    private val cell = 100f

    @Test
    fun HOM_04_passingOverACentreWithoutDwellingStaysAReorder() {
        val t = DropOntoTracker()
        t.update("a", 0f, 0f, cell, 0)
        t.update("a", 5f, 0f, cell, 100)
        assertNull(t.armedKey)
        t.update("b", 0f, 0f, cell, 120) // moved on to another item: starts over
        assertNull(t.armedKey)
    }

    @Test
    fun HOM_04_restingInTheCentreForTheDwellArmsDropOnto() {
        val t = DropOntoTracker()
        t.update("a", 2f, 2f, cell, 0)
        t.update("a", 3f, 1f, cell, 100)
        assertNull(t.armedKey)
        t.update("a", 3f, 1f, cell, DropOntoTracker.DWELL_MS)
        assertEquals("a", t.armedKey)
    }

    @Test
    fun HOM_04_aStillFingerArmsThroughTickWithoutNewSamples() {
        val t = DropOntoTracker()
        t.update("a", 0f, 0f, cell, 1000)
        t.tick(1000 + DropOntoTracker.DWELL_MS - 1)
        assertNull(t.armedKey)
        t.tick(1000 + DropOntoTracker.DWELL_MS)
        assertEquals("a", t.armedKey)
    }

    @Test
    fun HOM_04_onceArmedASmallTremorDoesNotFlipItBackToReorder() {
        val t = DropOntoTracker()
        t.update("a", 0f, 0f, cell, 0)
        t.update("a", 0f, 0f, cell, 300)
        assertEquals("a", t.armedKey)
        // Outside the enter zone (22) but inside the exit zone (36): still armed.
        t.update("a", 30f, 0f, cell, 400)
        assertEquals("a", t.armedKey)
        t.update("a", 40f, 0f, cell, 450)
        assertNull(t.armedKey)
    }

    @Test
    fun HOM_04_leavingTheEnterZoneBeforeTheDwellRestartsTheDwell() {
        val t = DropOntoTracker()
        t.update("a", 0f, 0f, cell, 0)
        t.update("a", 30f, 0f, cell, 150) // drifted out
        t.update("a", 0f, 0f, cell, 200) // back in: dwell restarts at 200
        t.update("a", 0f, 0f, cell, 300)
        assertNull(t.armedKey)
        t.update("a", 0f, 0f, cell, 200 + DropOntoTracker.DWELL_MS)
        assertEquals("a", t.armedKey)
    }

    @Test
    fun HOM_04_enteringATargetFromTheEdgeNeverArmsUntilTheCentreIsReached() {
        val t = DropOntoTracker()
        t.update("a", 45f, 0f, cell, 0)
        t.update("a", 45f, 0f, cell, 1000)
        assertNull(t.armedKey)
    }

    @Test
    fun HOM_04_overEmptySpaceOrNoTargetItResets() {
        val t = DropOntoTracker()
        t.update("a", 0f, 0f, cell, 0); t.update("a", 0f, 0f, cell, 300)
        assertEquals("a", t.armedKey)
        t.update(null, 0f, 0f, cell, 400)
        assertNull(t.armedKey)
    }

    @Test
    fun HOM_04_theDecisionFilesOnlyIntoAnItemThatCanReceiveTheDrop() {
        val state = HomeState(items = mapOf())
        val t = DropOntoTracker()
        t.update("pkg/B#0", 0f, 0f, cell, 0); t.update("pkg/B#0", 0f, 0f, cell, 300)
        assertEquals(DropDecision.Onto("pkg/B#0"), DropRule.decide(t, "pkg/A#0", cell, state, 300))
        // Dropping an item onto itself is a reorder, however long the finger rested.
        assertEquals(DropDecision.Reorder, DropRule.decide(t, "pkg/B#0", cell, state, 300))
        // A widget cannot receive a drop.
        val w = DropOntoTracker()
        w.update("widget:5", 0f, 0f, cell, 0); w.update("widget:5", 0f, 0f, cell, 300)
        assertEquals(DropDecision.Reorder, DropRule.decide(w, "pkg/A#0", cell, state, 300))
    }

    @Test
    fun HOM_04_withoutTheDwellTheDecisionIsReorder() {
        val t = DropOntoTracker()
        t.update("pkg/B#0", 0f, 0f, cell, 0)
        assertEquals(DropDecision.Reorder, DropRule.decide(t, "pkg/A#0", cell, HomeState(), 50))
        assertTrue(DropOntoTracker.ENTER_ZONE < DropOntoTracker.EXIT_ZONE)
    }
}
