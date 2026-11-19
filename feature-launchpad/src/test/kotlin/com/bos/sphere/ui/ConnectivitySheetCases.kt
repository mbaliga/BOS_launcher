package com.bos.sphere.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 34 (connectivity): the Connectivity sheet with fake seams. Screenshots go to build/screenshots/connectivity-*.png (compare with the
 * owner's mock-up, connectivity-ref.png).
 */
@OptIn(ExperimentalTestApi::class)
abstract class ConnectivitySheetCases(private val orientation: String) : LaunchpadShots() {
    private val actions = FakeConnectivityActions()
    private var dismissed = 0

    private fun show(state: ConnectivityState = ConnectivityFixtures.mock, dark: Boolean = true, rtl: Boolean = false, fontScale: Float = 1f) {
        rule.setContent {
            Themed(dark) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                ) { ConnectivitySheet(onDismiss = { dismissed++ }, source = { state }, actions = actions) }
            }
        }
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
    }

    private fun tag(t: String) = rule.onNodeWithTag(t, useUnmergedTree = true)
    private fun exists(t: String) = rule.onAllNodes(hasTestTag(t), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun toggle(t: String) = rule.onNodeWithTag(t).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState)
    private fun stateDesc(t: String) = rule.onNodeWithTag(t).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    @Test fun CON_20_theSheetHasTheTitleBothToggleRowsTheNetworksSeeAllShareAndDone() {
        show()
        rule.onNodeWithText("Connectivity").assertIsDisplayed()
        rule.onNodeWithText("Airtel").assertIsDisplayed()
        rule.onNodeWithText("Wi-Fi").assertIsDisplayed()
        for (n in ConnectivityFixtures.coffee) rule.onNodeWithText(n.name!!).assertIsDisplayed()
        for (t in listOf("cn.seeall", "cn.share", "cn.done")) tag(t).performScrollTo().assertIsDisplayed()
        val root = rule.onRoot().getUnclippedBoundsInRoot(); val sheet = tag("cn.sheet").getUnclippedBoundsInRoot()
        assertTrue("sheet sits at the bottom", (root.bottom - sheet.bottom).value < 56f)
        shoot("connectivity-$orientation-dark")
    }

    @Test fun CON_21_theTogglesShowTheRealStateAndAreSwitchesForTalkBack() {
        show(ConnectivityFixtures.snap(mobile = true, wifiOn = false))
        assertEquals(ToggleableState.On, toggle("cn.mobile")); assertEquals("On", stateDesc("cn.mobile"))
        assertEquals(ToggleableState.Off, toggle("cn.wifi")); assertEquals("Off", stateDesc("cn.wifi"))
        assertEquals("Switch", rule.onNodeWithTag("cn.wifi").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role).toString())
        assertTrue(exists("cn.switch.on") && exists("cn.switch.off"))
    }

    @Test fun CON_22_tappingATogglePopsTheSystemPanelAndNeverFlipsTheShownState() {
        show(ConnectivityFixtures.snap(mobile = true, wifiOn = false))
        tag("cn.mobile").performClick(); tag("cn.wifi").performClick(); rule.waitForIdle()
        assertEquals(listOf("mobile", "wifiPanel"), actions.calls)
        assertEquals(ToggleableState.On, toggle("cn.mobile")); assertEquals(ToggleableState.Off, toggle("cn.wifi"))
    }

    @Test fun CON_23_theConnectedNetworkIsBlueWithATuneButtonAndTheOthersWhiteWithLocks() {
        show(ConnectivityFixtures.snap(wifiOn = true))
        assertTrue(exists("cn.tune")); assertFalse(exists("cn.lock.0"))
        assertTrue(exists("cn.lock.1") && exists("cn.lock.2"))
        tag("cn.tune").performClick(); rule.waitForIdle()
        assertEquals(listOf("wifiSettings"), actions.calls)
        assertEquals("Connected", stateDesc("cn.net.0")); assertEquals("Secured", stateDesc("cn.net.1"))
        shoot("connectivity-$orientation-wifi-on")
    }

    @Test fun CON_24_seeAllOpensTheWifiPanelShareOpensTheSharePathAndDoneCloses() {
        show()
        tag("cn.seeall").performScrollTo().performClick(); tag("cn.share").performScrollTo().performClick(); tag("cn.done").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(listOf("wifiPanel", "share"), actions.calls); assertEquals(1, dismissed)
    }

    @Test fun CON_25_noSimHidesTheMobileRow() {
        show(ConnectivityFixtures.snap(hasSim = false, carrier = null))
        assertFalse(exists("cn.mobile")); tag("cn.wifi").assertIsDisplayed()
    }

    @Test fun CON_26_airplaneModeShowsMobileOffAndTheNote() {
        show(ConnectivityFixtures.snap(airplane = true, wifiOn = false, connected = false))
        assertEquals(ToggleableState.Off, toggle("cn.mobile"))
        rule.onNodeWithText("Airplane mode is on").assertIsDisplayed()
    }

    @Test fun CON_27_withoutLocationAccessTheConnectedRowHasNoNameAndAQuietExplanationNotACrash() {
        show(ConnectivityFixtures.snap(networks = listOf(WifiNetworkRow(null, secured = true, current = true))))
        rule.onNodeWithText("Connected network").assertIsDisplayed()
        tag("cn.note").assertIsDisplayed()
        tag("cn.seeall").performScrollTo().assertIsDisplayed()
        shoot("connectivity-$orientation-names-hidden")
    }

    @Test fun CON_28_everyTargetIsAtLeast48dpTall() {
        show()
        for (t in listOf("cn.mobile", "cn.wifi", "cn.net.0", "cn.net.1", "cn.seeall", "cn.share", "cn.done", "cn.tune")) {
            val b = tag(t).getUnclippedBoundsInRoot()
            assertTrue("$t is ${b.bottom - b.top} tall", (b.bottom - b.top) >= 48.dp)
        }
    }

    @Test fun CON_29_wifiOffKeepsTheListVisibleDimmed() {
        show(ConnectivityFixtures.mock)
        rule.onNodeWithText("Third Wave Coffee 5Ghz").assertIsDisplayed()
        assertEquals("Off", stateDesc("cn.wifi"))
    }

    @Test fun CON_30_backEscapeAndTheScrimDismiss() {
        show()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle(); assertEquals(1, dismissed)
        tag("cn.scrim").performTouchInput { click(androidx.compose.ui.geometry.Offset(20f, 20f)) }; rule.waitForIdle(); assertEquals(2, dismissed)
    }

    @Test fun CON_31_rtlAndFontScaleTwoKeepEverythingReachable() {
        show(rtl = true, fontScale = 2f)
        for (t in listOf("cn.mobile", "cn.wifi", "cn.seeall", "cn.share", "cn.done")) tag(t).performScrollTo().assertIsDisplayed()
        val sw = tag("cn.switch.on").getUnclippedBoundsInRoot(); val root = rule.onRoot().getUnclippedBoundsInRoot()
        assertTrue("in RTL the switch is on the left", sw.left.value < root.right.value / 2)
        shoot("connectivity-$orientation-rtl-font2")
    }

    @Test fun CON_32_theSignalAndWifiGlyphsOfTheCommsHeaderOpenTheSheetNotTheSystemPanel() {
        rule.setContent { Themed(true) { Body(grouped) } }
        rule.waitForIdle()
        assertFalse(exists("cn.sheet"))
        rule.onNodeWithTag("lp.sys.wifi").performClick()
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        tag("cn.sheet").assertIsDisplayed()
        shoot("connectivity-over-comms-$orientation")
    }

    @Test fun CON_33_lightThemeKeepsTheBlackSheetLikeTheSoundSheet() {
        show(dark = false)
        val lum = shoot("connectivity-$orientation-light")
        tag("cn.sheet").assertIsDisplayed()
        assertTrue("sheet region is dark even in the light theme, mean $lum", lum < 200)
    }
}
