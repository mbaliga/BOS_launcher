package com.bos.sphere.feature.dock

import com.bos.sphere.core.launch.BoundsPreset
import com.bos.sphere.core.launch.LaunchCaps
import com.bos.sphere.core.launch.LaunchTarget
import com.bos.sphere.core.launch.Placement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchMenusTest {
    private val none = LaunchCaps(setOf(0), setOf(0), boundsHonoured = false, adjacentSupported = null)
    private val rich = LaunchCaps(setOf(0, 2, 3), setOf(0, 2, 3), boundsHonoured = true, adjacentSupported = true)
    private val names = mapOf(2 to "Desk monitor", 3 to "TV")

    @Test fun WSP_02_openOnDisplayListsEachOtherHostingDisplayByName() {
        assertEquals(listOf("Open on Desk monitor", "Open on TV"), LaunchMenus.choices(rich, 0, names).filterIsInstance<LaunchChoice.OnDisplay>().map { it.label })
        assertTrue(LaunchMenus.choices(none, 0, names).isEmpty())
        assertEquals(2, LaunchMenus.context(rich, 0).otherDisplays)
    }

    @Test fun WSP_03_openBesideOnlyWhereTheSpikeProvedIt() {
        assertTrue(LaunchMenus.choices(rich, 0, names).contains(LaunchChoice.Beside))
        assertFalse(LaunchMenus.choices(none, 0, names).contains(LaunchChoice.Beside))
        assertFalse(LaunchMenus.context(rich.copy(adjacentSupported = false), 0).adjacentSupported)
        assertFalse(LaunchMenus.choices(rich.copy(adjacentSupported = null), 0, names).contains(LaunchChoice.Beside))
    }

    @Test fun WSP_04_boundsPresetsOnlyWhereHonouredAndMapToHalves() {
        assertEquals(4, LaunchMenus.choices(rich, 0, names).filterIsInstance<LaunchChoice.WithBounds>().size)
        assertTrue(LaunchMenus.choices(none, 0, names).none { it is LaunchChoice.WithBounds })
        val r = LaunchMenus.request(LaunchChoice.WithBounds(BoundsPreset.RightHalf), LaunchTarget("p"), 0, { 1000 to 800 })
        assertEquals(500, r.bounds!!.left)
    }

    @Test fun WSP_02_requestsCarryDisplayPlacementAndSourceRect() {
        val src = com.bos.sphere.core.launch.Bounds(1, 2, 3, 4)
        assertEquals(2, LaunchMenus.request(LaunchChoice.OnDisplay(2, "x"), LaunchTarget("p"), 0, { 1 to 1 }, src).displayId)
        assertEquals(Placement.Adjacent, LaunchMenus.request(LaunchChoice.Beside, LaunchTarget("p"), 0, { 1 to 1 }).placement)
        assertEquals(src, LaunchMenus.request(LaunchChoice.Plain, LaunchTarget("p"), 0, { 1 to 1 }, src).source)
    }
}
