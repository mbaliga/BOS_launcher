package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Wave 24: placing a home item from a landing without pages shows the landing that draws it. */
class HomePlacementLandingTest {
    @Test fun HOM_02_placingAnItemFromALandingWithoutPagesSwitchesToSelectedApps() {
        for (l in HomeLanding.entries.filter { !HomeLandingRules.hasPages(it) }) {
            assertEquals("from $l", HomeLanding.Selected, HomePlacementLanding.after(l))
        }
    }

    @Test fun HOM_02_placingAnItemOnSelectedAppsStaysThere() {
        assertNull(HomePlacementLanding.after(HomeLanding.Selected))
    }

    @Test fun HOM_02_theTargetIsTheSameLandingEditHomePagesUses() {
        assertEquals(HomeLandingRules.editHomePagesLanding, HomePlacementLanding.after(HomeLanding.Minimal))
        assertEquals(true, HomeLandingRules.hasPages(HomePlacementLanding.after(HomeLanding.Recents)!!))
    }
}
