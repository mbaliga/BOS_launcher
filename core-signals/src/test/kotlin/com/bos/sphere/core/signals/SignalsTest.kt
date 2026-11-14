package com.bos.sphere.core.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalsTest {
    @Test fun DCK_02_recentsAreNewestFirstDistinctAndExcludeSelf() {
        val ev = listOf(
            UsageEv("a", 10, true), UsageEv("b", 30, true), UsageEv("a", 50, true),
            UsageEv("me", 60, true), UsageEv("c", 40, false), UsageEv("d", 20, true),
        )
        assertEquals(listOf("a", "b", "d"), RecentsLogic.recents(ev, setOf("me"), 10))
        assertEquals(listOf("a", "b"), RecentsLogic.recents(ev, setOf("me"), 2))
    }

    @Test fun WSP_08_scopeFiltersDisplayOnly() {
        val all = listOf("x" to 1, "y" to 2, "z" to 3)
        assertEquals(all, NotificationScope(null).apply(all) { it.first })
        assertEquals(listOf("y" to 2), NotificationScope(setOf("y")).apply(all) { it.first })
        assertTrue(NotificationScope(setOf("y")).allows("y"))
    }

    @Test fun STR_10_batteryAndLinkText() {
        assertEquals("50% charging", SystemStateLogic.describe(SystemStateLogic.battery(50, 100, 2)))
        assertEquals("Battery unknown", SystemStateLogic.describe(BatteryState(-1, false)))
        assertEquals("Offline", SystemStateLogic.describe(ConnectivityState(LinkKind.None, false)))
        assertEquals("Wi-Fi, no internet", SystemStateLogic.describe(ConnectivityState(LinkKind.Wifi, false)))
    }
}
