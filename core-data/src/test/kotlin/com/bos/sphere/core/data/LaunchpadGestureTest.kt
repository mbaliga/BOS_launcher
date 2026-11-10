package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchpadGestureTest {
    @Test fun defaultsRoundTrip() {
        assertEquals(LaunchpadGestures.defaults, LaunchpadGestures.decode(LaunchpadGestures.encode(LaunchpadGestures.defaults)))
    }

    @Test fun decodeKeepsDefaultsForJunkAndDisallowedPairs() {
        val m = LaunchpadGestures.decode("PinchIn=Dismiss;Nope=ShowAll;SwipeItemLeft=ShowUnread;garbage")
        assertEquals(LaunchpadGestures.defaults[LaunchpadGesture.PinchIn], m[LaunchpadGesture.PinchIn])
        assertEquals(LaunchpadGestures.defaults[LaunchpadGesture.SwipeItemLeft], m[LaunchpadGesture.SwipeItemLeft])
    }

    @Test fun itemActionsNeedAnItem() {
        assertTrue(LaunchpadAction.Dismiss !in LaunchpadGestures.allowed(LaunchpadGesture.PinchIn))
        assertTrue(LaunchpadAction.ShowUnread !in LaunchpadGestures.allowed(LaunchpadGesture.SwipeItemRight))
        assertTrue(LaunchpadAction.Dismiss in LaunchpadGestures.allowed(LaunchpadGesture.SwipeItemRight))
        assertFalse(LaunchpadGestures.allowed(LaunchpadGesture.LongPressItem).isEmpty())
    }
}
