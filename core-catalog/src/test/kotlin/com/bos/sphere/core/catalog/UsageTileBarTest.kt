package com.bos.sphere.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-17: the split bar inside the Usage tile. */
class UsageTileBarTest {
    private fun slices(vararg ms: Long): List<UsageSlice> {
        val total = ms.sum().toFloat()
        return ms.mapIndexed { i, m -> UsageSlice("p$i", m, m / total) }
    }

    @Test
    fun HOM_17_noUsageMeansNoBar() {
        assertTrue(UsageTileBar.segments(emptyList()).isEmpty())
        assertTrue(UsageTileBar.segments(listOf(UsageSlice("a", 0, 0f))).isEmpty())
    }

    @Test
    fun HOM_17_theSegmentWidthsAddUpToTheWholeBarAndKeepTheProportions() {
        val seg = UsageTileBar.segments(slices(60, 30, 10))
        assertEquals(1f, seg.sumOf { it.share.toDouble() }.toFloat(), 0.0001f)
        assertEquals(listOf(0, 1, 2), seg.map { it.sliceIndex })
        assertTrue(seg[0].share > seg[1].share && seg[1].share > seg[2].share)
        assertEquals(0.6f, seg[0].share, 0.02f)
    }

    @Test
    fun HOM_17_aThinSliceStaysVisible() {
        val seg = UsageTileBar.segments(slices(10_000, 1))
        assertTrue(seg[1].share >= UsageTileBar.MIN_SHARE * 0.9f)
    }

    @Test
    fun HOM_17_atMostFiveSegmentsAreDrawnKeepingTheBiggest() {
        val seg = UsageTileBar.segments(slices(5, 50, 4, 40, 3, 30, 20, 10))
        assertEquals(UsageTileBar.MAX_SEGMENTS, seg.size)
        // The slices the panel colours in order keep their own colour index.
        assertTrue(seg.map { it.sliceIndex } == seg.map { it.sliceIndex }.sorted())
        assertTrue(1 in seg.map { it.sliceIndex } && 3 in seg.map { it.sliceIndex })
    }

    @Test
    fun HOM_17_theTileIsDescribedByItsTotalAndTopApp() {
        val s = listOf(UsageSlice("a", 90 * 60_000L, 0.9f), UsageSlice("b", 10 * 60_000L, 0.1f))
        assertEquals("1 h 40 min, Alpha", UsageTileBar.description(s) { if (it == "a") "Alpha" else null })
        assertEquals("", UsageTileBar.description(emptyList()) { null })
    }
}
