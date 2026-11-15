package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.ClockPosition
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.feature.dock.DockEdge
import com.bos.sphere.feature.dock.DockFootprint
import com.bos.sphere.feature.dock.HomeBottomStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HOM-19: the bottom clock, the page dots and the dock rail together, at small phone, tablet and foldable sizes. Rectangles
 * are in dp from the top-left of the window; the app area is the frame band inset by what the rail reserves.
 */
class BottomStackTest {
    private data class R(val l: Float, val t: Float, val r: Float, val b: Float) {
        fun hits(o: R) = l < o.r - 0.01f && o.l < r - 0.01f && t < o.b - 0.01f && o.t < b - 0.01f
    }

    /** small phone, phone, large phone, tablet portrait and landscape, foldable cover, foldable inner square-ish and its tall form. */
    private val sizes = listOf(320 to 568, 360 to 640, 411 to 891, 800 to 1280, 1280 to 800, 280 to 653, 673 to 841, 884 to 1104, 640 to 360)

    private fun prefs(clock: ClockPosition, weather: Boolean) = LauncherPrefs().copy(clockPosition = clock, clockShowWeather = weather)

    private fun check(w: Int, h: Int, edge: DockEdge, collapsed: Boolean, clock: ClockPosition, weather: Boolean) {
        val p = prefs(clock, weather)
        val band = HomeFrame.band(w.toFloat(), h.toFloat())
        val lower = bottomStackDp(p, w.toFloat(), h.toFloat())
        val plan = HomeBottomStack.plan(true, edge, collapsed, band, lower)
        val label = "$w x $h edge=$edge collapsed=$collapsed clock=$clock weather=$weather"

        val lowerRect = R(w * 0.2f, h - lower, w * 0.8f, h.toFloat())
        val rail = when (edge) {
            DockEdge.Bottom -> R(76f, h - plan.railTopDp, w - 76f, h - plan.railBottomDp)
            DockEdge.Left -> R(DockFootprint.SIDE_MARGIN_DP, 76f, DockFootprint.thicknessDp(edge, collapsed), h - 76f)
            DockEdge.Right -> R(w - DockFootprint.thicknessDp(edge, collapsed), 76f, w - DockFootprint.SIDE_MARGIN_DP, h - 76f)
        }
        val app = R(band + plan.reserve.start, band, w - band - plan.reserve.end, h - band - plan.reserve.bottom)

        // The bottom rail never sits on the clock and dots; a side rail is beside them.
        if (lower > 0f && edge == DockEdge.Bottom) assertFalse("rail over the clock/dots: $label", rail.hits(lowerRect))
        // The rail never sits on the app area (when the area still has any room: a very short window can have none left).
        if (app.r > app.l && app.b > app.t) assertFalse("rail over the app area: $label", rail.hits(app))
        // What rides on the rail starts above it.
        if (edge == DockEdge.Bottom) {
            assertTrue("media strip below the rail top: $label", plan.mediaBottomDp >= plan.railTopDp)
            assertTrue("shelf below the rail top: $label", plan.shelfBottomDp >= plan.railTopDp)
        }
        // With the group shelf on, the media strip sits above the shelf instead of on it.
        val withShelf = HomeBottomStack.plan(true, edge, collapsed, band, lower, shelfShown = true)
        assertTrue("media strip over the group shelf: $label", withShelf.mediaBottomDp >= withShelf.shelfBottomDp + HomeBottomStack.SHELF_HEIGHT_DP)
        assertEquals(plan.shelfBottomDp, withShelf.shelfBottomDp, 0f)
        // The rail stays inside the window.
        assertTrue("rail off the window: $label", rail.t >= -0.01f && rail.b <= h + 0.01f && rail.l >= -0.01f && rail.r <= w + 0.01f)
    }

    @Test
    fun HOM_19_railClockAndDotsNeverOverlapAtSmallPhoneTabletAndFoldableSizes() {
        for ((w, h) in sizes) for (edge in DockEdge.entries) for (collapsed in listOf(false, true))
            for (clock in listOf(ClockPosition.Bottom, ClockPosition.Top, ClockPosition.Hidden)) for (weather in listOf(false, true))
                check(w, h, edge, collapsed, clock, weather)
    }

    @Test
    fun HOM_19_withoutALowerStackTheRailKeepsItsOwnBottomMargin() {
        val plan = HomeBottomStack.plan(true, DockEdge.Bottom, false, 53f, 0f)
        assertEquals(DockFootprint.BOTTOM_MARGIN_DP, plan.railBottomDp, 0f)
        // This is the old reserve: thickness minus the band.
        assertEquals(DockFootprint.reserve(true, DockEdge.Bottom, false, 53f).bottom, plan.reserve.bottom, 0.001f)
    }

    @Test
    fun HOM_19_aBottomClockLiftsTheRailAndTheAppAreaGivesUpTheDifference() {
        val none = HomeBottomStack.plan(true, DockEdge.Bottom, false, 53f, 0f)
        val clock = HomeBottomStack.plan(true, DockEdge.Bottom, false, 53f, 50f)
        assertTrue(clock.railBottomDp > none.railBottomDp)
        assertEquals(50f + HomeBottomStack.GAP_DP, clock.railBottomDp, 0.001f)
        assertTrue(clock.reserve.bottom > none.reserve.bottom)
    }

    @Test
    fun HOM_19_withNoRailNothingIsReservedAndTheMediaStripKeepsItsPlace() {
        val off = HomeBottomStack.plan(false, DockEdge.Bottom, false, 53f, 50f)
        assertFalse(off.reserve.any)
        assertEquals(HomeBottomStack.MEDIA_DEFAULT_DP, off.mediaBottomDp, 0f)
        assertEquals(0f, off.railTopDp, 0f)
    }

    @Test
    fun HOM_19_theBottomStackCountsTheClockOnlyWhenItIsAtTheBottom() {
        val top = bottomStackDp(prefs(ClockPosition.Top, true), 360f, 640f)
        val bottom = bottomStackDp(prefs(ClockPosition.Bottom, true), 360f, 640f)
        // Dots only vs clock and dots.
        assertEquals(ClockBandFit.clockBottomDp(true), top, 0.001f)
        assertTrue(bottom > top)
        assertTrue(bottom <= HomeFrame.band(360f, 640f) + 0.5f)
    }
}
