package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.os.Process
import com.bos.sphere.core.data.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeMathTest {
    private fun app(i: Int, label: String = "A$i") =
        AppEntry("p$i", ComponentName("p$i", "M"), label, Process.myUserHandle()) { null }

    @Test fun recentFirstThenBySizeThenName() {
        val apps = (0 until 7).map { app(it) }
        val cat = mapOf(0 to "Zed", 1 to "Zed", 2 to "Alpha", 3 to "Alpha", 4 to "Big", 5 to "Big", 6 to "Big")
        val out = XmbMath.categories(apps, listOf(apps[6], apps[6], apps[0]), { cat[apps.indexOf(it)]!! })
        assertEquals(listOf("Recent", "Big", "Alpha", "Zed"), out.map { it.name })
        assertEquals(2, out[0].apps.size) // de-duplicated
        assertTrue(out[0].isRecent)
    }

    @Test fun emptyRecentIsDropped() {
        val apps = listOf(app(1), app(2))
        val out = XmbMath.categories(apps, emptyList(), { "X" })
        assertEquals(listOf("X"), out.map { it.name })
    }

    @Test fun overflowFoldsSmallestIntoOther() {
        val apps = (0 until 20).map { app(it) }
        // Category c0 has 1 app, c1 has 2, ... so the smallest are c0.. folded.
        val sizes = apps.indices.map { i -> "c" + (0 until 20).first { k -> (k + 1) * (k + 2) / 2 > i } }
        val out = XmbMath.categories(apps, listOf(apps[0]), { sizes[apps.indexOf(it)] }, max = 5)
        assertEquals(5, out.size)
        assertEquals("Recent", out[0].name)
        assertEquals("Other", out.last().name)
        // Nothing lost: every app appears in exactly one non-Recent category.
        assertEquals(20, out.drop(1).sumOf { it.apps.size })
    }

    @Test fun blankCategoryBecomesOther() {
        val out = XmbMath.categories(listOf(app(1)), emptyList(), { "  " })
        assertEquals("Other", out.single().name)
    }

    @Test fun glyphMapping() {
        assertSame(Glyphs.Music, XmbMath.glyphFor("Music & Audio"))
        assertSame(Glyphs.Clock, XmbMath.glyphFor("whatever", isRecent = true))
        assertSame(Glyphs.Apps, XmbMath.glyphFor("Zzz"))
    }

    @Test fun settleAndStretch() {
        assertEquals(2, XmbMath.settleTarget(1.2f, 4f, 5))
        assertEquals(0, XmbMath.settleTarget(0.4f, -9f, 5))
        assertEquals(5, XmbMath.settleTarget(4.9f, 30f, 5))
        assertEquals(-0.35f, XmbMath.clampStretch(-3f, 5), 1e-6f)
        assertEquals(5.35f, XmbMath.clampStretch(9f, 5), 1e-6f)
        assertEquals(1f, XmbMath.closeness(0f), 0f)
        assertEquals(0f, XmbMath.closeness(-2f), 0f)
    }

    @Test fun cellSpansCrossAxis() {
        assertEquals(100f, ContinuousMath.cell(300f, 3), 1e-4f)
        assertEquals(300f, ContinuousMath.cell(300f, 1), 1e-4f)
        assertEquals(300f, ContinuousMath.cell(300f, 0), 1e-4f) // guarded
        assertEquals(80f, ContinuousMath.iconSize(100f, 0.8f, 18f, capToCell = false), 1e-4f)
        // Landscape: icon + label must still fit the cell.
        assertEquals(80f, ContinuousMath.iconSize(100f, 0.8f, 18f, capToCell = true), 1e-4f)
        assertEquals(70f, ContinuousMath.iconSize(100f, 1f, 28f, capToCell = true), 1e-4f)
    }

    @Test fun spacingWidensExtent() {
        val tight = ContinuousMath.mainExtent(80f, 100f, 0f, 18f)
        val wide = ContinuousMath.mainExtent(80f, 100f, 1f, 18f)
        assertTrue(wide > tight)
        assertEquals(tight + 30f, wide, 1e-3f)
    }

    @Test fun scrollFractionAndThumb() {
        // 10 rows x 100 = 1000 content, 400 viewport -> range 600.
        assertEquals(0f, ContinuousMath.scrollFraction(0, 0f, 3, 100f, 30, 400f, 0f, 0f), 1e-6f)
        assertEquals(0.5f, ContinuousMath.scrollFraction(9, 0f, 3, 100f, 30, 400f, 0f, 0f), 1e-6f)
        assertEquals(1f, ContinuousMath.scrollFraction(27, 90f, 3, 100f, 30, 400f, 0f, 0f), 1e-6f)
        assertEquals(0f, ContinuousMath.scrollFraction(0, 0f, 3, 100f, 6, 400f, 0f, 0f), 0f) // fits: no range
        assertEquals(0.4f, ContinuousMath.thumbFraction(30, 3, 100f, 400f, 0f, 0f), 1e-6f)
        assertEquals(1f, ContinuousMath.thumbFraction(3, 3, 100f, 400f, 0f, 0f), 0f)
        assertEquals(0.08f, ContinuousMath.thumbFraction(3000, 3, 100f, 400f, 0f, 0f), 1e-6f)
    }
}
