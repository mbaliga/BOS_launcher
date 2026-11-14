package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.Corner
import org.junit.Assert.assertEquals
import org.junit.Test

class PeekGeometryTest {
    private val container = PeekSize(1000f, 2000f)
    private val insets = PeekInsets(left = 10f, top = 50f, right = 20f, bottom = 80f)
    private val card = PeekSize(400f, 300f)
    private val visible = PeekGeometry.visibleExtent(card, PeekSize(1000f, 1000f))

    @Test fun visibleIsHalfPerAxisAndCapped() {
        assertEquals(PeekSize(200f, 150f), visible)
        assertEquals(PeekSize(120f, 150f), PeekGeometry.visibleExtent(card, PeekSize(120f, 999f)))
    }

    @Test fun peekLeavesInnerCornerVisiblePerCorner() {
        for (c in Corner.entries) {
            val o = PeekGeometry.peekOrigin(c, container, card, visible, insets)
            val onScreenX = if (PeekGeometry.isLeft(c)) o.x + card.width - insets.left
            else container.width - insets.right - o.x
            val onScreenY = if (PeekGeometry.isTop(c)) o.y + card.height - insets.top
            else container.height - insets.bottom - o.y
            assertEquals("x $c", visible.width, onScreenX, 1e-3f)
            assertEquals("y $c", visible.height, onScreenY, 1e-3f)
        }
        assertEquals(PeekPoint(-190f, -100f), PeekGeometry.peekOrigin(Corner.TopLeft, container, card, visible, insets))
        assertEquals(PeekPoint(780f, 1770f), PeekGeometry.peekOrigin(Corner.BottomRight, container, card, visible, insets))
    }

    @Test fun expandedSitsInsideSafeCornerWithMargin() {
        val m = 12f
        assertEquals(PeekPoint(22f, 62f), PeekGeometry.expandedOrigin(Corner.TopLeft, container, card, insets, m))
        assertEquals(PeekPoint(568f, 62f), PeekGeometry.expandedOrigin(Corner.TopRight, container, card, insets, m))
        assertEquals(PeekPoint(568f, 1608f), PeekGeometry.expandedOrigin(Corner.BottomRight, container, card, insets, m))
        assertEquals(PeekPoint(22f, 1608f), PeekGeometry.expandedOrigin(Corner.BottomLeft, container, card, insets, m))
    }

    @Test fun expandedSizeHonoursFloorAndCap() {
        val floor = PeekSize(160f, 120f)
        assertEquals(PeekSize(160f, 120f), PeekGeometry.expandedSize(40f, 40f, container, PeekInsets(), 0f, floor))
        // 85% of 1000 = 850 caps width; height 500 passes.
        assertEquals(PeekSize(850f, 500f), PeekGeometry.expandedSize(5000f, 500f, container, PeekInsets(), 0f, floor))
        // Safe area minus margins can be tighter than 85%.
        assertEquals(850f, PeekGeometry.expandedSize(5000f, 0f, PeekSize(1000f, 1000f), PeekInsets(), 30f, floor).width, 1e-3f)
        assertEquals(800f, PeekGeometry.expandedSize(5000f, 0f, PeekSize(1000f, 1000f), PeekInsets(left = 100f, right = 60f), 20f, floor).width, 1e-3f)
    }

    @Test fun pivotsAreOppositeCorners() {
        assertEquals(PeekPoint(0f, 0f), PeekGeometry.anchorPivot(Corner.TopLeft))
        assertEquals(PeekPoint(1f, 1f), PeekGeometry.innerPivot(Corner.TopLeft))
        assertEquals(PeekPoint(1f, 0f), PeekGeometry.anchorPivot(Corner.TopRight))
        assertEquals(PeekPoint(0f, 1f), PeekGeometry.innerPivot(Corner.TopRight))
    }

    @Test fun lerpInterpolates() {
        assertEquals(PeekPoint(5f, 10f), PeekGeometry.lerp(PeekPoint(0f, 0f), PeekPoint(10f, 20f), 0.5f))
    }
}
