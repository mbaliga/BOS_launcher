package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Wave 24, updated in wave 26: placing a home item while Glance is showing moves to the pages, which draw it. */
class HomePlacementLandingTest {
    @Test fun HOM_02_placingAnItemFromGlanceSwitchesToThePages() {
        assertEquals(HomeShown.Pages, HomePlacementLanding.after(HomeShown.Glance))
    }

    @Test fun HOM_02_placingAnItemOnThePagesStaysThere() {
        assertNull(HomePlacementLanding.after(HomeShown.Pages))
    }

    @Test fun HOM_02_theTargetIsTheSameSurfaceEditHomePagesUses() {
        assertEquals(HomeShownRules.editHomePagesSurface, HomePlacementLanding.after(HomeShown.Glance))
        assertEquals(true, HomeShownRules.hasPages(HomePlacementLanding.after(HomeShown.Glance)!!))
    }
}
