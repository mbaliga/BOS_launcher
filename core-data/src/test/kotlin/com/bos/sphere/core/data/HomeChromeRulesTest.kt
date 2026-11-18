package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-22, HOM-23, STR-01, STR-09, STR-14, STR-06 (wave 17): the explicit chrome table, every landing x element. Replaces the old showsChrome flag. */
class HomeChromeRulesTest {
    private val defaults = ChromeSettings()

    @Test fun HOM_23_everyLandingDrawsTheLeftHandleTheWaitingIndicatorAndTheRightHandleWithTheDefaults() {
        for (l in HomeLanding.entries) {
            assertTrue("$l left handle", HomeChromeRules.shows(l, ChromeElement.LeftHandle, defaults))
            assertTrue("$l waiting indicator", HomeChromeRules.shows(l, ChromeElement.WaitingIndicator, defaults))
            assertTrue("$l right handle", HomeChromeRules.shows(l, ChromeElement.RightHandle, defaults))
        }
    }

    @Test fun HOM_23_noLandingDrawsTheRailUnlessTheRailSettingIsOnAndThenInsteadOfTheWaitingPill() {
        val rail = defaults.copy(launchpadRail = true)
        for (l in HomeLanding.entries) {
            assertFalse("$l rail off by default", HomeChromeRules.shows(l, ChromeElement.Rail, defaults))
            assertTrue("$l rail on with the setting", HomeChromeRules.shows(l, ChromeElement.Rail, rail))
            assertFalse("$l the pill steps aside for the rail", HomeChromeRules.shows(l, ChromeElement.WaitingIndicator, rail))
            assertTrue("$l the handle stays", HomeChromeRules.shows(l, ChromeElement.LeftHandle, rail))
        }
    }

    @Test fun STR_01_withLaunchpadOffNoLeftAffordanceIsDrawnOnAnyLanding() {
        for (page in listOf(LeftPage.Off)) for (l in HomeLanding.entries) for (rail in listOf(false, true)) {
            val s = ChromeSettings(leftPage = page, launchpadRail = rail)
            for (e in listOf(ChromeElement.LeftHandle, ChromeElement.WaitingIndicator, ChromeElement.Rail)) assertFalse("$l $e rail=$rail", HomeChromeRules.shows(l, e, s))
        }
    }

    @Test fun STR_01_aWidgetLeftPageHasAHandleButNoWaitingIndicatorOrRail() {
        val s = ChromeSettings(leftPage = LeftPage.Widget, launchpadRail = true)
        for (l in HomeLanding.entries) {
            assertTrue(HomeChromeRules.shows(l, ChromeElement.LeftHandle, s))
            assertFalse(HomeChromeRules.shows(l, ChromeElement.WaitingIndicator, s)); assertFalse(HomeChromeRules.shows(l, ChromeElement.Rail, s))
        }
    }

    @Test fun STR_14_theRightHandleFollowsTheRightSideSettingOnEveryLanding() {
        for (l in HomeLanding.entries) for (side in RightSide.entries)
            assertEquals("$l $side", side == RightSide.RecentsPanel, HomeChromeRules.shows(l, ChromeElement.RightHandle, defaults.copy(rightSide = side)))
    }

    @Test fun STR_06_noChromeElementIsAMediaStripTheControlsLiveOnlyInTheGlanceNotch() {
        assertTrue("no media chrome element left", ChromeElement.entries.none { it.name.contains("Media", ignoreCase = true) })
    }

    @Test fun HOM_23_theDockIsHiddenOnGlanceUnlessTheRightSideIsTheDockRail() {
        for (l in HomeLanding.entries) for (side in RightSide.entries)
            assertEquals("$l $side", l != HomeLanding.Glance || side == RightSide.DockRail, HomeChromeRules.shows(l, ChromeElement.Dock, defaults.copy(rightSide = side)))
    }

    @Test fun HOM_22_theWorkPausedBannerIsNotDrawnOnGlance() {
        for (l in HomeLanding.entries) assertEquals(l != HomeLanding.Glance, HomeChromeRules.shows(l, ChromeElement.WorkPausedBanner, defaults))
    }

    @Test fun HOM_23_everyLandingTimesEveryElementIsDecided() {
        var n = 0
        for (l in HomeLanding.entries) for (e in ChromeElement.entries) for (page in LeftPage.entries) for (side in RightSide.entries) for (rail in listOf(false, true)) {
            HomeChromeRules.shows(l, e, ChromeSettings(page, rail, side)); n++
        }
        assertEquals(HomeLanding.entries.size * ChromeElement.entries.size * LeftPage.entries.size * RightSide.entries.size * 2, n)
    }

    @Test fun HOM_23_onlyTheGlanceHandlesAndWaitingIndicatorAreQuiet() {
        for (l in HomeLanding.entries) for (e in ChromeElement.entries) {
            val quietElement = e == ChromeElement.LeftHandle || e == ChromeElement.RightHandle || e == ChromeElement.WaitingIndicator
            assertEquals("$l $e", l == HomeLanding.Glance && quietElement, HomeChromeRules.quiet(l, e))
        }
    }

    @Test fun STR_01_theEdgeZoneCoversTheHandleWidthOnGlanceAndIsTheDefaultElsewhere() {
        assertEquals(48f, HomeChromeRules.edgeZoneDp(HomeLanding.Glance), 0f)
        for (l in HomeLanding.entries.filter { it != HomeLanding.Glance }) assertEquals(28f, HomeChromeRules.edgeZoneDp(l), 0f)
        assertEquals(HomeChromeRules.HANDLE_WIDTH_DP, HomeChromeRules.GLANCE_EDGE_DP, 0f)
        assertEquals(48f, HomeChromeRules.HANDLE_WIDTH_DP, 0f); assertEquals(160f, HomeChromeRules.HANDLE_HEIGHT_DP, 0f)
    }

    @Test fun HOM_23_theGlanceLandingIsTheMinimalEnumValue() { assertEquals(HomeLanding.Minimal, HomeLanding.Glance) }
}
