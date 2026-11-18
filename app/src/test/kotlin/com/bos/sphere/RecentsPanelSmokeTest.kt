package com.bos.sphere

import android.view.KeyEvent
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import com.bos.sphere.core.data.AppsOnHome
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.RightSide
import com.bos.sphere.feature.dock.DockItemRef
import com.bos.sphere.feature.dock.WorkspaceRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * STR-14 (whole app): the real [MainActivity] under Robolectric. The right-hand handle, a swipe in from the right edge, the "Open recents"
 * shortcut and the Right side setting open and close the Recents panel; the dock stays optional. Not a device run: Robolectric has no
 * compositor, no real gesture navigation and no real usage log.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class RecentsPanelSmokeTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun freshProcess() { AppSmokeHarness.resetProcess() }
    @After fun closeApp() {
        if (scenario != null) { rule.waitForIdle(); assertFalse("a test left a global Compose snapshot write pending", Snapshot.current.hasPendingChanges()) }
        scenario?.close(); scenario = null
        AppSmokeHarness.resetProcess()
    }

    private fun start(dock: Boolean = false): ActivityScenario<MainActivity> {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        AppSmokeHarness.configure { it.copy(appsOnHome = AppsOnHome.Everything) }
        if (dock) {
            WorkspaceRepository.get(AppSmokeHarness.app).setDock { it.copy(enabled = true) }
            WorkspaceRepository.get(AppSmokeHarness.app).setPins(listOf(DockItemRef.app(AppSmokeHarness.appKey("Alpha"))))
        }
        return AppSmokeHarness.launch().also { scenario = it; rule.waitForIdle() }
    }

    private fun settle(ms: Long = 1500) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); Snapshot.sendApplyNotifications(); rule.mainClock.advanceTimeBy(ms); org.robolectric.shadows.ShadowLooper.idleMainLooper(); rule.mainClock.advanceTimeBy(ms) }
    private fun panelOpen() = rule.onAllNodesWithTag("recents.panel").fetchSemanticsNodes().isNotEmpty() && rule.onAllNodesWithTag("lp.card").fetchSemanticsNodes().isNotEmpty()
    private fun hasHandle() = rule.onAllNodesWithTag("recents.handle").fetchSemanticsNodes().isNotEmpty()
    private fun setSide(s: RightSide) { LauncherStores.get(AppSmokeHarness.app).prefs.update { it.copy(rightSide = s) }; settle() }
    private fun edgeSwipeLeft() { rule.onRoot().performTouchInput { down(Offset(width - 6f, height / 2f)); moveBy(Offset(-60f, 0f)); moveBy(Offset(-200f, 0f)); moveBy(Offset(-300f, 0f)); up() }; settle() }

    @Test fun STR_14_theRightSideDefaultsToTheRecentsPanelAndTheHandleOpensItAndTheSliverReturns() {
        start()
        assertEquals(RightSide.RecentsPanel, LauncherStores.get(AppSmokeHarness.app).prefs.prefs.value.rightSide)
        assertTrue("a handle on the right edge", hasHandle())
        assertFalse(panelOpen())
        LauncherStores.get(AppSmokeHarness.app).history.record(AppSmokeHarness.appKey("Bravo"))
        rule.onNodeWithTag("recents.handle").performClick(); settle()
        assertTrue("the panel is open with the home card as a sliver on the left", panelOpen())
        rule.onNodeWithTag("recents.honesty").assertIsDisplayed()
        rule.onNodeWithTag("lp.card").performClick(); settle()
        assertFalse("tapping the sliver returns home", panelOpen())
    }

    @Test fun STR_14_aSwipeInFromTheRightEdgeOpensThePanelAndEscapeReturnsHome() {
        val sc = start()
        edgeSwipeLeft()
        assertTrue("an edge swipe from the right opens the Recents panel", panelOpen())
        AppSmokeHarness.press(sc, KeyEvent.KEYCODE_ESCAPE); settle()
        assertFalse("Esc returns home", panelOpen())
    }

    @Test fun STR_14_theOpenRecentsShortcutOpensThePanelAndBackClosesIt() {
        val sc = start()
        AppSmokeHarness.press(sc, KeyEvent.KEYCODE_R, ctrl = true); settle()
        assertTrue("Ctrl+R opens the panel", panelOpen())
        AppSmokeHarness.back(sc); rule.mainClock.advanceTimeBy(1500); settle()
        assertFalse("Back closes it", panelOpen())
    }

    @Test fun STR_14_whenRightSideIsOffThereIsNoHandleAndNoSwipe() {
        start()
        setSide(RightSide.Off)
        assertFalse(hasHandle())
        edgeSwipeLeft()
        assertFalse(panelOpen())
    }

    @Test fun STR_14_whenRightSideIsDockRailTheEdgeSwipeDoesNotOpenThePanelAndTheDockStaysOptional() {
        start(dock = false)
        setSide(RightSide.DockRail)
        assertFalse("no recents handle", hasHandle())
        edgeSwipeLeft()
        assertFalse("no recents panel", panelOpen())
        assertFalse("the dock was not turned on by choosing the rail", WorkspaceRepository.get(AppSmokeHarness.app).state.value.dock.enabled == true)
    }

    @Test fun STR_14_launchpadAndTheRecentsPanelAreNeverOpenTogether() {
        val sc = start()
        rule.onNodeWithTag("recents.handle").performClick(); settle()
        assertTrue(panelOpen())
        AppSmokeHarness.press(sc, KeyEvent.KEYCODE_N, ctrl = true); settle()
        assertFalse("opening Comms closes the recents panel", rule.onAllNodesWithTag("recents.panel").fetchSemanticsNodes().isNotEmpty())
    }
}
