package com.bos.sphere.feature.grid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/** Wave 49 (actionBubbles, PAL-03): where the Orbit puts an app's shortcut bubbles. Pure geometry, dp units, deterministic. */
class RadarSatellitesTest {
    private val bounds = Rect(12f, 90f, 399f, 700f)
    private fun appBox(cx: Float, cy: Float) = Rect(cx - 40f, cy - 27f, cx + 40f, cy + 53f)
    private fun all(spots: List<ShortcutSatellites.Spot>) = spots.map { it.box }

    private fun assertClean(app: Rect, spots: List<ShortcutSatellites.Spot>, others: List<Rect>, b: Rect, where: String) {
        val boxes = all(spots)
        boxes.forEachIndexed { i, r ->
            assertTrue("inside the visible area ($where): $r", r.left >= b.left && r.right <= b.right && r.top >= b.top && r.bottom <= b.bottom)
            assertTrue("a touch target is at least 48 dp ($where)", r.width >= 48f && r.height >= 48f)
            assertFalse("not on the app ($where)", r.overlaps(app))
            others.forEach { assertFalse("not on another result ($where): $r vs $it", r.overlaps(it)) }
            for (j in i + 1 until boxes.size) assertFalse("two bubbles never overlap ($where)", r.overlaps(boxes[j]))
        }
    }

    @Test fun PAL_03_everyShortcutGetsABubbleInsideTheAreaClearOfEverythingElse() {
        val app = appBox(200f, 300f)
        val others = listOf(appBox(80f, 300f), appBox(320f, 300f), appBox(200f, 180f), appBox(200f, 440f))
        for (n in 1..ShortcutForm.PER_LINE * 2) {
            val s = ShortcutSatellites.place(app, n, 1f, bounds, others, -PI / 2)
            assertEquals(n, s.size)
            assertClean(app, s, others, bounds, "n=$n")
        }
    }

    @Test fun PAL_03_theSpotsAreDeterministicAndStayNextToTheAppWhenThereIsRoom() {
        val app = appBox(200f, 400f)
        val a = ShortcutSatellites.place(app, 6, 1f, bounds, emptyList(), PI / 2)
        val b = ShortcutSatellites.place(app, 6, 1f, bounds, emptyList(), PI / 2)
        assertEquals(all(a), all(b))
        assertTrue("all on the ring around the app", a.all { it.onRing })
        // adjacent: the middle of every bubble is within two bubble heights of the app
        a.forEach { assertTrue((it.box.center - app.center).getDistance() < 2f * ShortcutForm.bubbleHeight(1f)) }
    }

    @Test fun PAL_03_aCrowdedAppStillGetsAllItsBubblesOnTheNearestFreeSpots() {
        val app = appBox(200f, 300f)
        // a wall of results around the app: nothing is free in its neighbourhood
        val wall = ArrayList<Rect>()
        var y = bounds.top
        while (y < bounds.bottom) { var x = bounds.left; while (x < bounds.right) { val r = Rect(x, y, x + 80f, y + 80f); if (!r.overlaps(app) && (r.center - app.center).getDistance() < 260f) wall += r; x += 84f }; y += 84f }
        val s = ShortcutSatellites.place(app, 4, 1f, bounds, wall, 0.0)
        assertEquals(4, s.size)
        assertClean(app, s, wall, bounds, "crowded")
        assertFalse("they moved off the app's own ring to the nearest free area", s.all { it.onRing })
    }

    @Test fun PAL_03_anAppAtTheScreenEdgeFansInwards() {
        val app = appBox(60f, 300f)
        val s = ShortcutSatellites.place(app, 6, 1f, bounds, emptyList(), PI) // preferred direction points off the left edge
        assertClean(app, s, emptyList(), bounds, "left edge")
        assertTrue("the bubbles went to the visible side", s.all { it.box.center.x > 0f })
    }

    @Test fun PAL_03_landscapeAndLargeFontStillFit() {
        val land = Rect(12f, 60f, 880f, 380f)
        val app = appBox(440f, 220f)
        listOf(1f, 1.5f, 2f).forEach { fs ->
            val s = ShortcutSatellites.place(app, 6, fs, land, listOf(appBox(300f, 220f), appBox(580f, 220f)), -PI / 2)
            assertClean(app, s, listOf(appBox(300f, 220f), appBox(580f, 220f)), land, "landscape font=$fs")
            assertEquals(ShortcutForm.bubbleHeight(fs), s[0].box.height, 0.01f)
        }
    }

    @Test fun PAL_03_rtlMirrorsWhichSideTheFanStartsOn() {
        val app = appBox(200f, 400f)
        val ltr = ShortcutSatellites.place(app, 2, 1f, bounds, emptyList(), -PI / 2, rtl = false)
        val rtl = ShortcutSatellites.place(app, 2, 1f, bounds, emptyList(), -PI / 2, rtl = true)
        assertTrue(ltr[1].box.center.x != rtl[1].box.center.x)
        assertTrue("the first bubbles sit on opposite sides", (ltr[0].box.center.x - app.center.x) * (rtl[0].box.center.x - app.center.x) < 0f)
    }

    @Test fun PAL_03_placeAllServesAppsInOrderWithoutBubblesOfOneOverlappingAnother() {
        val boxes = listOf(appBox(140f, 300f), appBox(260f, 300f))
        val spots = ShortcutSatellites.placeAll(boxes, listOf(6, 6), listOf(-PI / 2, -PI / 2), 1f, bounds, emptyList())
        val flat = spots.flatten().map { it.box }
        assertEquals(12, flat.size)
        for (i in flat.indices) {
            boxes.forEach { assertFalse(flat[i].overlaps(it)) }
            for (j in i + 1 until flat.size) assertFalse("bubbles $i and $j overlap", flat[i].overlaps(flat[j]))
        }
    }

    @Test fun PAL_03_theCircleOfABubbleIsAtTheTopOfItsBox() {
        val s = ShortcutSatellites.place(appBox(200f, 300f), 1, 1f, bounds, emptyList(), 0.0).single()
        assertEquals(Offset(s.box.center.x, s.box.top + 24f), s.circleCentre)
    }

    @Test fun PAL_03_flowRowReservesTheHeightOfItsLines() {
        assertEquals(0f, ShortcutForm.flowHeight(0), 0f)
        assertEquals(72f, ShortcutForm.flowHeight(3), 0f)
        assertEquals(144f, ShortcutForm.flowHeight(6), 0f)
    }
}
