package com.bos.sphere.feature.sphere

import org.junit.Assert.assertEquals
import org.junit.Test

/** Mode 2's pull past the coverflow's ends counts only from a scrub that began at that end. */
class ScrubPastEndTest {

    private val maxIndex = 9f

    @Test
    fun insideTheListThereIsNothingPastTheEnds() {
        assertEquals(0f, ScrubPastEnd.columns(startScrub = 4f, raw = 6f, maxIndex = maxIndex), 0f)
        assertEquals(0f, ScrubPastEnd.columns(startScrub = 9f, raw = 9f, maxIndex = maxIndex), 0f)
    }

    @Test
    fun aPullThatStartsOnTheLastItemCounts() {
        assertEquals(1.6f, ScrubPastEnd.columns(startScrub = 9f, raw = 10.6f, maxIndex = maxIndex), 0.0001f)
        // Within half a column of the end (the coverflow settled a hair off it) still counts.
        assertEquals(1.5f, ScrubPastEnd.columns(startScrub = 8.6f, raw = 10.5f, maxIndex = maxIndex), 0.0001f)
    }

    @Test
    fun aFastStrokeFromTheMiddleThatOvershootsNeverCounts() {
        // From the second-to-last item, 2.6 columns of travel ends 1.6 columns past the end: an
        // ordinary scrub (the coverflow has no fling), so it must not reach the page-turn threshold.
        assertEquals(0f, ScrubPastEnd.columns(startScrub = 8f, raw = 10.6f, maxIndex = maxIndex), 0f)
        // A sparse coverflow (3 apps): one full-width swipe from the first item.
        assertEquals(0f, ScrubPastEnd.columns(startScrub = 0f, raw = 3.8f, maxIndex = 2f), 0f)
    }

    @Test
    fun theFirstEndWorksTheSameWay() {
        assertEquals(-1.5f, ScrubPastEnd.columns(startScrub = 0f, raw = -1.5f, maxIndex = maxIndex), 0.0001f)
        assertEquals(0f, ScrubPastEnd.columns(startScrub = 2f, raw = -1.5f, maxIndex = maxIndex), 0f)
    }
}
