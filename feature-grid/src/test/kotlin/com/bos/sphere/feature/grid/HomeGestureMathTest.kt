package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.data.SurfaceScroll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGestureMathTest {

    @Test
    fun `a vertically scrolling home frees horizontal swipes and never the scroll axis`() {
        val s = SurfaceScroll.Vertical
        assertTrue(HomeGestureMath.isCrossSwipe(30f, 5f, s))
        assertFalse(HomeGestureMath.isCrossSwipe(5f, 30f, s))
        // Diagonals belong to the content.
        assertFalse(HomeGestureMath.isCrossSwipe(20f, 18f, s))
        assertEquals(HomeGesture.SwipeLeft, HomeGestureMath.swipeFor(-80f, 10f, s, 56f))
        assertEquals(HomeGesture.SwipeRight, HomeGestureMath.swipeFor(80f, -10f, s, 56f))
        assertNull(HomeGestureMath.swipeFor(40f, 0f, s, 56f))
        assertNull(HomeGestureMath.swipeFor(0f, -200f, s, 56f))
    }

    @Test
    fun `a horizontally scrolling home frees vertical swipes`() {
        val s = SurfaceScroll.Horizontal
        assertTrue(HomeGestureMath.isCrossSwipe(5f, 30f, s))
        assertFalse(HomeGestureMath.isCrossSwipe(30f, 5f, s))
        assertEquals(HomeGesture.SwipeUp, HomeGestureMath.swipeFor(0f, -90f, s, 56f))
        assertEquals(HomeGesture.SwipeDown, HomeGestureMath.swipeFor(0f, 90f, s, 56f))
        assertNull(HomeGestureMath.swipeFor(200f, 0f, s, 56f))
    }

    @Test
    fun `a home that scrolls both ways gets no swipes at all`() {
        assertNull(HomeGestureMath.freeAxisVertical(SurfaceScroll.Both))
        assertFalse(HomeGestureMath.isCrossSwipe(0f, 300f, SurfaceScroll.Both))
        assertNull(HomeGestureMath.swipeFor(0f, 300f, SurfaceScroll.Both, 56f))
    }

    @Test
    fun `pinch needs a clear span change`() {
        assertEquals(HomeGesture.PinchIn, HomeGestureMath.pinchFor(300f, 150f))
        assertEquals(HomeGesture.PinchOut, HomeGestureMath.pinchFor(200f, 320f))
        assertNull(HomeGestureMath.pinchFor(200f, 220f))
        assertNull(HomeGestureMath.pinchFor(-1f, 100f))
        assertNull(HomeGestureMath.pinchFor(100f, 0f))
    }
}
