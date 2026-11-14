package com.bos.sphere.core.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchResolverTest {
    private val target = LaunchTarget("com.example.app")
    private val caps = LaunchCaps(displayIds = setOf(0, 2), hostingDisplayIds = setOf(0, 2), boundsHonoured = true, adjacentSupported = true)

    @Test fun plain_launch_applies_nothing_and_drops_nothing() {
        val r = LaunchResolver.resolve(LaunchRequest(target), caps)
        assertNull(r.displayId); assertNull(r.bounds); assertFalse(r.adjacent); assertTrue(r.dropped.isEmpty())
    }
    @Test fun display_is_applied_when_present() =
        assertEquals(2, LaunchResolver.resolve(LaunchRequest(target, displayId = 2), caps).displayId)
    @Test fun missing_display_falls_back_with_reason() {
        val r = LaunchResolver.resolve(LaunchRequest(target, displayId = 9), caps)
        assertNull(r.displayId); assertEquals(listOf(DropReason.DisplayMissing), r.dropped)
    }
    @Test fun display_that_cannot_host_falls_back() {
        val r = LaunchResolver.resolve(LaunchRequest(target, displayId = 2), caps.copy(hostingDisplayIds = setOf(0)))
        assertNull(r.displayId); assertEquals(listOf(DropReason.DisplayCannotHost), r.dropped)
    }
    @Test fun bounds_dropped_where_not_honoured() {
        val req = LaunchRequest(target, bounds = BoundsPreset.LeftHalf.on(1000, 800))
        assertEquals(listOf(DropReason.BoundsIgnored), LaunchResolver.resolve(req, caps.copy(boundsHonoured = false)).dropped)
        assertEquals(Bounds(0, 0, 500, 800), LaunchResolver.resolve(req, caps).bounds)
    }
    @Test fun adjacent_needs_a_proven_spike() {
        val req = LaunchRequest(target, placement = Placement.Adjacent)
        assertTrue(LaunchResolver.resolve(req, caps).adjacent)
        assertEquals(listOf(DropReason.AdjacentUnsupported), LaunchResolver.resolve(req, caps.copy(adjacentSupported = null)).dropped)
        assertFalse(LaunchResolver.offersAdjacent(caps.copy(adjacentSupported = false)))
    }
    @Test fun offered_displays_exclude_the_current_one() =
        assertEquals(listOf(2), LaunchResolver.offeredDisplays(caps, current = 0))
    @Test fun notices_show_once_per_reason() {
        val n = DropNotices()
        assertEquals(listOf(DropReason.BoundsIgnored), n.fresh(listOf(DropReason.BoundsIgnored)))
        assertTrue(n.fresh(listOf(DropReason.BoundsIgnored)).isEmpty())
    }
    @Test fun presets_cover_the_display() {
        assertEquals(Bounds(500, 0, 1000, 800), BoundsPreset.RightHalf.on(1000, 800))
        assertEquals(Bounds(0, 0, 1000, 800), BoundsPreset.Full.on(1000, 800))
    }
    @Test(expected = IllegalArgumentException::class) fun a_group_needs_two_apps() { AppGroup("solo", listOf(LaunchRequest(target))) }
}
