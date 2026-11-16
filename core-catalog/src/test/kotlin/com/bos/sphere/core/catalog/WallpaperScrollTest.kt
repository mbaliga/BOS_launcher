package com.bos.sphere.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperScrollTest {
    @Test
    fun HOM_16_pagedGridForwardsPositionInPagesAndSnapsWhenAnimationsAreOff() {
        val s = WallpaperScroll.send(1.4f, 4, scrollsAcross = true, reduced = false)
        assertEquals(1.4f, s.position, 0f); assertEquals(4, s.pages)
        assertEquals(1f / 3f * 1.4f, WallpaperMath.xOffset(s.position, s.pages), 1e-6f)
        val snapped = WallpaperScroll.send(1.4f, 4, true, reduced = true)
        assertEquals(1f, snapped.position, 0f)
        assertEquals(2f, WallpaperScroll.send(1.5f, 4, true, true).position, 0f)
        assertEquals(3f, WallpaperScroll.send(9f, 4, true, false).position, 0f) // clamped
    }

    @Test
    fun HOM_16_verticalOrSinglePageStylesSendTheCentredOffset() {
        val c = WallpaperScroll.send(0.7f, 5, scrollsAcross = false, reduced = false)
        assertEquals(WallpaperScroll.Centred, c)
        assertEquals(0.5f, WallpaperMath.xOffset(c.position, c.pages), 0f)
        assertEquals(0f, WallpaperMath.xStep(c.pages), 0f)
        assertEquals(WallpaperScroll.Centred, WallpaperScroll.send(0f, 1, true, false))
    }

    @Test
    fun HOM_16_xmbCategoryBarMapsOneSlotPerCategory() {
        // catPos 0..lastCat over cats.size pages
        assertEquals(0f, WallpaperMath.xOffset(0f, 6), 0f)
        assertEquals(0.5f, WallpaperMath.xOffset(2.5f, 6), 1e-6f)
        assertEquals(1f, WallpaperMath.xOffset(5f, 6), 0f)
    }

    @Test
    fun HOM_16_continuousGridProgressIsZeroToOneAlongTheContent() {
        // 20 items, 2 across => 10 lines of 100px; padding 10+10; viewport 300 => max scroll 720
        fun p(i: Int, off: Int) = WallpaperScroll.gridProgress(i, off, 100f, 20, 2, 10f, 10f, 300f)
        assertEquals(0f, p(0, 0), 0f)
        assertEquals((100f + 50f) / 720f, p(2, 50), 1e-6f)
        assertEquals(1f, p(18, 400), 0f) // past the end clamps
        assertEquals(0f, WallpaperScroll.gridProgress(0, 0, 100f, 4, 2, 0f, 0f, 500f), 0f) // fits: nothing to scroll
        assertEquals(0f, WallpaperScroll.gridProgress(0, 0, 100f, 0, 2, 0f, 0f, 500f), 0f)
        val send = WallpaperScroll.send(0.5f, WallpaperScroll.GRID_PAGES, true, false)
        assertEquals(0.5f, WallpaperMath.xOffset(send.position, send.pages), 1e-6f)
    }
}
