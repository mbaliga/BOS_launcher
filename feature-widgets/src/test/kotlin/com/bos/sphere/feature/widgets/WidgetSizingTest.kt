package com.bos.sphere.feature.widgets

import com.bos.sphere.feature.sphere.SphereParams
import com.bos.sphere.feature.sphere.SphereState
import com.bos.sphere.feature.sphere.TILE_SIZE_DP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSizingTest {

    @Test
    fun equatorReportsOnlyTheFocusLensSizeItIsLaidOutAt() {
        // One row, focus 1.85: the 132 dp tile magnifies to 244 dp at the focal point, and the
        // hosted view is always laid out at that size (off focus it is only scaled as a picture).
        assertEquals(listOf(SizeDp(244f, 244f)), WidgetSizing.equatorSizes(TILE_SIZE_DP, 1.85f))
        assertEquals(244f, WidgetSizing.equatorLayoutDp(TILE_SIZE_DP, 1.85f), 0f)
    }

    @Test
    fun twoRowLensIsSmallerThanTheTile() {
        val lens = SphereState(itemCount = 8, rows = 2).focusScale // 0.66 × (1 + 0.85 × 0.45)
        assertEquals(listOf(SizeDp(120f, 120f)), WidgetSizing.equatorSizes(TILE_SIZE_DP, lens))
        // The bind options derived from it describe that one square, not a shape that never occurs.
        assertEquals(SizeBounds(120, 120, 120, 120), WidgetSizing.bounds(WidgetSizing.equatorSizes(TILE_SIZE_DP, lens)))
    }

    @Test
    fun unmagnifiedLensReportsTheTileSize() {
        assertEquals(listOf(SizeDp(132f, 132f)), WidgetSizing.equatorSizes(TILE_SIZE_DP, 1f))
    }

    @Test
    fun absurdLensValuesAreClamped() {
        assertEquals(66f, WidgetSizing.equatorLayoutDp(TILE_SIZE_DP, 0.01f), 0f)
        assertEquals(396f, WidgetSizing.equatorLayoutDp(TILE_SIZE_DP, 40f), 0f)
    }

    @Test
    fun focusScaleMatchesTheTransformAtTheFocalPoint() {
        for (rows in 1..2) {
            for (focus in listOf(1f, 1.4f, 1.85f, 2f)) {
                for (minScale in listOf(0.4f, 1f)) {
                    val state = SphereState(itemCount = 12, rows = rows, params = SphereParams(focus = focus, minScale = minScale))
                    state.position = 3f
                    val focusedIndex = state.firstIndexOfColumn(3)
                    val t = state.transformFor(focusedIndex)
                    assertTrue(t.focused)
                    assertEquals("rows=$rows focus=$focus", t.scale, state.focusScale, 1e-5f)
                    // At rest the focused tile is unrotated, so a widget there is drawn 1:1.
                    assertEquals(0f, t.rotationY, 0f)
                    assertEquals(0f, t.translationX, 1e-4f)
                }
            }
        }
    }

    @Test
    fun focusedItemIndexIsTheTopRowOfTheNearestColumn() {
        val one = SphereState(itemCount = 10, rows = 1).apply { position = 4.3f }
        assertEquals(4, one.focusedItemIndex())
        val two = SphereState(itemCount = 10, rows = 2).apply { position = 2.6f }
        assertEquals(6, two.focusedItemIndex())
    }

    @Test
    fun hubWidgetsAreFullWidthAtTheirPresetHeight() {
        assertEquals(listOf(SizeDp(612f, 200f)), WidgetSizing.hubSizes(611.6f, HubSize.Medium))
        assertEquals(listOf(SizeDp(1f, 120f)), WidgetSizing.hubSizes(0f, HubSize.Small))
    }

    @Test
    fun boundsAreMinAndMaxOfTheSizes() {
        assertEquals(
            SizeBounds(minWidth = 132, minHeight = 120, maxWidth = 245, maxHeight = 244),
            WidgetSizing.bounds(listOf(SizeDp(132f, 244f), SizeDp(244.2f, 120f))),
        )
    }

    @Test
    fun defaultHubSizeFitsTheProvidersMinimumHeight() {
        assertEquals(HubSize.Small, WidgetSizing.defaultHubSize(40f))
        assertEquals(HubSize.Small, WidgetSizing.defaultHubSize(120f))
        assertEquals(HubSize.Medium, WidgetSizing.defaultHubSize(121f))
        assertEquals(HubSize.Large, WidgetSizing.defaultHubSize(250f))
        assertEquals(HubSize.Large, WidgetSizing.defaultHubSize(900f))
    }

    @Test
    fun hubStackLeavesRoomForNotificationsOnlyWhenTheyShareThePanel() {
        assertEquals(300f, HubStackLayout.viewportDp(300f, sharesPanel = false), 0f)
        // Shares: about one notification row is left to them...
        assertEquals(180f, HubStackLayout.viewportDp(300f, sharesPanel = true), 0f)
        // ...or half, when the panel is short.
        assertEquals(100f, HubStackLayout.viewportDp(200f, sharesPanel = true), 0f)
        assertEquals(0f, HubStackLayout.viewportDp(-5f, sharesPanel = true), 0f)
        assertEquals(Float.POSITIVE_INFINITY, HubStackLayout.viewportDp(Float.POSITIVE_INFINITY, true), 0f)
        // One Small widget (226 dp) fits a 470 dp portrait hub shared with notifications.
        val small = listOf(HubSize.Small)
        assertFalse(HubStackLayout.needsScroll(small, HubStackLayout.viewportDp(470f, sharesPanel = true)))
    }

    @Test
    fun hubWidgetsTakeTouchesOnlyOnceTheHubIsFullyOpen() {
        assertTrue(HubStackLayout.isLive(1f))
        assertTrue(HubStackLayout.isLive(0.995f))
        assertFalse(HubStackLayout.isLive(0.9f))
        assertFalse(HubStackLayout.isLive(0.1f))
        assertFalse(HubStackLayout.isLive(0f))
    }

    @Test
    fun hubStackScrollsOnlyWhenItOverflows() {
        val sizes = listOf(HubSize.Small, HubSize.Medium)
        // 48 header + (48 + 120 + 10) + (48 + 200 + 10) = 484
        assertEquals(484f, HubStackLayout.heightDp(sizes), 0f)
        assertFalse(HubStackLayout.needsScroll(sizes, 484f))
        assertTrue(HubStackLayout.needsScroll(sizes, 483f))
        assertFalse(HubStackLayout.needsScroll(sizes, Float.POSITIVE_INFINITY))
        assertFalse(HubStackLayout.needsScroll(emptyList(), 60f))
    }
}
