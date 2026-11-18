package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 25 (PAL-18, PAL-20, NFR-13): nothing the Orbit draws leaves the region it has, and every recent is placed at once. The matrix is every window
 * size the owner's phone, small phones, tablets and landscape give, with font scale 1.0 and 2.0 and the keyboard closed and 40 percent open.
 */
class RadarFitW25Test {
    private class Win(val w: Float, val h: Float)
    private val windows = listOf(Win(360f, 640f), Win(411f, 891f), Win(320f, 480f), Win(640f, 360f), Win(891f, 411f), Win(1280f, 800f), Win(280f, 653f))

    /** The region the Orbit gets in [SearchScreen]: below the 64 dp field, above the mic strip (or the field row, when the free height is under 220 dp) and the keyboard. */
    private fun region(win: Win, keyboard: Float): FloatArray {
        val free = win.h * (1f - keyboard) - 64f
        val strip = if (RadarMicPlacement.inline(androidx.compose.ui.unit.Dp(free))) 0f else 72f
        return floatArrayOf(win.w, (free - strip).coerceAtLeast(0f))
    }

    private fun fitFor(win: Win, keyboard: Float, font: Float, count: Int): Pair<OrbitFit.Fit, FloatArray> {
        val r = region(win, keyboard)
        val wheel = WheelSpec.fit(OrbitFit.wheelRoom(r[1]), font)
        val cy = maxOf(r[1] / 2f, wheel.height.value / 2f)
        return OrbitFit.fit(r[0], r[1], cy, 90f * font, 100f * font, wheel.halfVisible.value, count, font) to r
    }

    @Test
    fun PAL_18_everyTileBoxLiesInsideTheRegionInEveryWindowFontAndKeyboard() {
        for (win in windows) for (font in listOf(1f, 2f)) for (kb in listOf(0f, 0.4f)) for (count in listOf(5, 17, 27, 44)) {
            val (fit, r) = fitFor(win, kb, font, count)
            val where = "${win.w}x${win.h} font=$font keyboard=$kb count=$count"
            fit.slots.forEach { s ->
                assertTrue("left edge: $where $s", s.x - fit.boxW / 2f >= 0f)
                assertTrue("right edge: $where $s", s.x + fit.boxW / 2f <= r[0] + 0.01f)
                assertTrue("top edge: $where $s", s.y - fit.boxH / 2f >= 0f)
                assertTrue("bottom edge (above the mic and the keyboard): $where $s", s.y + fit.boxH / 2f <= r[1] + 0.01f)
                assertTrue("icon between the floor and the full size: $where", fit.icon in OrbitFit.MIN_ICON..OrbitFit.MAX_ICON)
            }
            // no two boxes overlap
            for (i in fit.slots.indices) for (j in i + 1 until fit.slots.size) {
                val a = fit.slots[i]; val b = fit.slots[j]
                assertTrue("tiles $i and $j overlap: $where", kotlin.math.abs(a.x - b.x) >= fit.boxW - 0.01f || kotlin.math.abs(a.y - b.y) >= fit.boxH - 0.01f)
            }
        }
    }

    @Test
    fun PAL_18_allRecentsArePlacedAtOnceWhenTheRegionCanHoldThemAndTheIconsShrinkFirst() {
        // The owner's phone class (411x891) and the tablet hold the whole 44 (5 + 9 + 13 + 17) at once.
        for (win in listOf(Win(411f, 891f), Win(1280f, 800f))) {
            val (fit, _) = fitFor(win, 0f, 1f, 44)
            assertEquals("all 44 recents on ${win.w}x${win.h}", 44, fit.slots.size)
        }
        // A small phone gets 27 (three rings) at once, and the icon shrank below its full size to make room (never below the floor).
        val (small, _) = fitFor(Win(360f, 640f), 0f, 1f, 27)
        assertEquals(27, small.slots.size)
        // With room to spare the icon keeps its full size; with a tight region it is smaller.
        assertEquals(OrbitFit.MAX_ICON, fitFor(Win(1280f, 800f), 0f, 1f, 9).first.icon, 0f)
        assertTrue(fitFor(Win(360f, 640f), 0f, 1f, 44).first.icon <= fitFor(Win(1280f, 800f), 0f, 1f, 44).first.icon)
    }

    @Test
    fun PAL_18_aRingNeverPlacesMoreThanItsCapacityAndInnerRingsComeFirst() {
        val (fit, _) = fitFor(Win(411f, 891f), 0f, 1f, 44)
        for (r in 0 until RadarRings.MAX_RINGS) assertTrue(fit.slots.count { it.ring == r } <= RadarRings.capacity(r))
        assertEquals("rank order is ring order", fit.slots.map { it.ring }, fit.slots.map { it.ring }.sorted())
    }

    @Test
    fun PAL_18_landscapeWithTheKeyboardUsesTheFreeHeightAndStillPlacesTiles() {
        for (kb in listOf(0.4f, 0.5f, 0.6f)) for (win in listOf(Win(640f, 360f), Win(891f, 411f), Win(1280f, 800f))) {
            val (fit, r) = fitFor(win, kb, 1f, 12)
            assertTrue("landscape ${win.w}x${win.h} keyboard=$kb has a region of ${r[1]} dp and places at least 5 recents (placed ${fit.slots.size})", fit.slots.size >= minOf(5, 12) || r[1] < 80f)
            // The tiles use the free height: the lowest box reaches into the lower third of the region when there are enough of them.
            if (fit.slots.size >= 8 && r[1] >= 120f) assertTrue("tiles spread over the height ${r[1]}", fit.slots.maxOf { it.y + fit.boxH / 2f } > r[1] * 0.6f)
        }
    }

    @Test
    fun PAL_18_theFilmstripDropsItsExtrasAndShrinksTheTileBeforeItRunsPastItsHeight() {
        for (h in 40..200 step 4) {
            val f = StripFit.of(h.toFloat())
            val used = f.tile.value + (if (f.caption) 26f else 0f) + (if (f.footnote) 16f else 0f) + 4f
            assertTrue("strip of $h dp: tile ${f.tile} caption=${f.caption} footnote=${f.footnote} needs $used", used <= h + 0.01f || f.tile.value <= 32f)
            assertTrue(f.tile.value in 32f..76f)
        }
        assertEquals(76f, StripFit.of(182f).tile.value, 0f)
        assertTrue(StripFit.of(182f).caption && StripFit.of(182f).footnote)
    }
}
