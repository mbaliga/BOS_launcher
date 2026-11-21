package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 13 audit of the edit card's drag geometry (HOM-12, HOM-04). */
class EditCardAuditW13Test {
    @Test fun HOM_12_theCardScalesAndShiftsTheHomesPointerCoordinatesSoTheEdgeZoneGrowsWhileEditing() {
        // 1080 x 2400 px, density 3: the card is at 0.68, its start edge 4% off screen. The dwell strip at the page edge is measured in the
        // home's own (unscaled) px, so on glass it is zone * scale minus the part that is off screen.
        val t = EditCardGeometry.target(1080f, 2400f, rtl = false, topReservePx = 150f, bottomReservePx = 200f, addBandPx = 396f)
        fun glass(zoneDp: Float): Float = zoneDp * 3f * t.scale + t.card.left
        val normal = glass(EditCardGeometry.pageEdgeZoneDp(false))
        val editing = glass(EditCardGeometry.pageEdgeZoneDp(true))
        assertTrue("the strip is only $normal px of glass at the old width", normal < 30f)
        assertTrue("and about ${editing} px with the wider zone", editing > 2 * normal)
        assertEquals(28f, EditCardGeometry.pageEdgeZoneDp(false), 0f)
    }

    @Test fun HOM_04_theDropTargetIsComparedInWindowPixelsWhenTheHostIsNotAtTheWindowOrigin() {
        val card = EdgeRect(-20f, 100f, 700f, 1500f)
        val moved = EditCardGeometry.inWindow(card, 40f, 60f)
        assertEquals(EdgeRect(20f, 160f, 740f, 1560f), moved)
        // a finger at window x = 720 is on the card when the host sits 40 px in, but outside it in the host's own px
        assertTrue(720f in moved.left..moved.right)
        assertTrue(720f !in card.left..card.right)
    }
}
