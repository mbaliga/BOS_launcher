package com.bos.sphere

import android.view.KeyEvent
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.test.click
import androidx.test.core.app.ActivityScenario
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.CornerSlot
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.LeftPage
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.MediaNow
import com.bos.sphere.core.data.RightSide
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
 * HOM-23, STR-01, STR-09, STR-14, INP-08, INP-16 (wave 17, owner bug report on build 48): every way into Launchpad and into the
 * Recents panel from the Glance home, with touch swipes injected on a fresh state (the default landing). Not a device run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class GlanceEdgesSmokeTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun freshProcess() { AppSmokeHarness.resetProcess() }
    @After fun closeApp() {
        if (scenario != null) { rule.waitForIdle(); assertFalse("a test left a global Compose snapshot write pending", Snapshot.current.hasPendingChanges()) }
        scenario?.close(); scenario = null
        MediaNow.publishForTest(null)
        AppSmokeHarness.resetProcess()
    }

    private fun startFresh(edit: (com.bos.sphere.core.data.LauncherPrefs) -> com.bos.sphere.core.data.LauncherPrefs = { it }): ActivityScenario<MainActivity> {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        AppSmokeHarness.configureFresh(edit)
        return AppSmokeHarness.launch().also { scenario = it; rule.waitForIdle(); settle() }
    }
    private fun settle(ms: Long = 1500) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); Snapshot.sendApplyNotifications(); rule.mainClock.advanceTimeBy(ms); org.robolectric.shadows.ShadowLooper.idleMainLooper(); rule.mainClock.advanceTimeBy(ms) }
    private fun launchpadShown() = rule.onAllNodesWithTag("lp.card").fetchSemanticsNodes().isNotEmpty() && rule.onAllNodesWithTag("lp.root").fetchSemanticsNodes().isNotEmpty()
    private fun recentsShown() = rule.onAllNodesWithTag("recents.panel").fetchSemanticsNodes().isNotEmpty()
    private fun swipeFromLeft(y: Float = 0.5f) { rule.onRoot().performTouchInput { down(Offset(6f, height * y)); moveBy(Offset(60f, 0f)); moveBy(Offset(200f, 0f)); moveBy(Offset(300f, 0f)); up() }; settle() }
    private fun swipeFromRight(y: Float = 0.5f) { rule.onRoot().performTouchInput { down(Offset(width - 6f, height * y)); moveBy(Offset(-60f, 0f)); moveBy(Offset(-200f, 0f)); moveBy(Offset(-300f, 0f)); up() }; settle() }
    private val track = MediaInfo("Someday Out of The Blue", "Artist", "pkg.music", playing = true, canPrev = true, canNext = true)

    @Test fun STR_01_aFreshStateIsOnGlanceWithTheLeftPageOnLaunchpad() {
        startFresh()
        assertTrue(LauncherStores.get(AppSmokeHarness.app).prefs.prefs.value.glanceEnabled); assertTrue(tagShown("glance.deckdots"))
        assertEquals(LeftPage.Launchpad, LauncherStores.get(AppSmokeHarness.app).prefs.prefs.value.leftPage)
    }
    @Test fun STR_01_aTouchSwipeFromTheLeftEdgeOpensLaunchpadOnGlance() { startFresh(); swipeFromLeft(); assertTrue(launchpadShown()) }
    @Test fun STR_01_theEdgeSwipeWorksWithTheLeftPageAtItsDefaultExplicitlySet() { startFresh { it.copy(leftPage = LeftPage.Launchpad) }; swipeFromLeft(); assertTrue(launchpadShown()) }
    @Test fun STR_01_theEdgeSwipeWorksWhileTheMusicControlsArePoppedIn() {
        startFresh(); MediaNow.publishForTest(track); settle()
        rule.onRoot().performTouchInput { click(Offset(width * 0.5f, height * 0.72f)) }; settle(300)
        assertTrue("controls are in", rule.onAllNodesWithContentDescriptionCount("Next") > 0)
        swipeFromLeft(); assertTrue(launchpadShown())
    }
    @Test fun STR_01_aSwipeStartingAtAHotspotCornerOnTheLeftEdgeOpensLaunchpad() {
        startFresh(); swipeFromLeft(0.03f); assertTrue("top left corner", launchpadShown())
    }
    @Test fun STR_01_aSwipeStartingAtTheBottomLeftHotspotCornerOpensLaunchpad() { startFresh(); swipeFromLeft(0.97f); assertTrue(launchpadShown()) }
    @Test fun STR_01_theKeyboardShortcutOpensLaunchpadOnGlance() {
        val sc = startFresh(); AppSmokeHarness.press(sc, KeyEvent.KEYCODE_N, ctrl = true); settle(); assertTrue(launchpadShown())
    }
    @Test fun INP_08_theOpenLaunchpadCornerActionOpensLaunchpadOnGlance() {
        startFresh()
        LauncherStores.get(AppSmokeHarness.app).shortcuts.setCorner(Corner.TopLeft, CornerSlot.Single(CornerItem.Action(CornerActionId.OpenLaunchpad))); settle()
        rule.onNodeWithContentDescription("Top left hotspot", substring = true).performClick(); settle()
        assertTrue(launchpadShown())
    }
    @Test fun STR_14_aSwipeFromTheRightEdgeOpensRecentsOnGlanceWhenTheRightSideIsTheRecentsPanel() {
        startFresh(); assertEquals(RightSide.RecentsPanel, LauncherStores.get(AppSmokeHarness.app).prefs.prefs.value.rightSide)
        swipeFromRight(); assertTrue(recentsShown())
    }
    @Test fun STR_14_theRightEdgeSwipeWorksWhileTheMusicControlsArePoppedIn() {
        startFresh(); MediaNow.publishForTest(track); settle()
        rule.onRoot().performTouchInput { click(Offset(width * 0.5f, height * 0.72f)) }; settle(300)
        swipeFromRight(); assertTrue(recentsShown())
    }
    @Test fun STR_14_aSwipeFromARightHotspotCornerOpensRecents() { startFresh(); swipeFromRight(0.03f); assertTrue(recentsShown()) }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithContentDescriptionCount(d: String) =
        onAllNodesWithContentDescription(d).fetchSemanticsNodes().size

    // ---- wave 17: the visible affordances

    private fun tagShown(t: String) = rule.onAllNodesWithTag(t, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    @Test fun HOM_23_glanceDrawsTheQuietLeftAndRightHandlesAndTheOldHandleIsNotThere() {
        startFresh()
        assertTrue(tagShown("glance.handle.left")); assertTrue(tagShown("recents.handle"))
    }
    @Test fun STR_01_aTapOnTheGlanceLeftHandleOpensLaunchpad() { startFresh(); rule.onNodeWithTag("glance.handle.left", useUnmergedTree = true).performClick(); settle(); assertTrue(launchpadShown()) }
    @Test fun STR_14_aTapOnTheGlanceRightHandleOpensRecents() { startFresh(); rule.onNodeWithTag("recents.handle", useUnmergedTree = true).performClick(); settle(); assertTrue(recentsShown()) }
    @Test fun HOM_23_theIndicatorLaunchpadGlyphOpensLaunchpad() { startFresh(); rule.onNodeWithTag("glance.indicator.launchpad", useUnmergedTree = true).performClick(); settle(); assertTrue(launchpadShown()) }
    @Test fun HOM_23_theIndicatorRightDotOpensTheRecentsPanel() { startFresh(); rule.onNodeWithTag("glance.indicator.right.Recents", useUnmergedTree = true).performClick(); settle(); assertTrue(recentsShown()) }
    @Test fun STR_01_theGlanceEdgeZoneIsWideEnoughToStartWhereTheHandleIs() {
        startFresh()
        rule.onRoot().performTouchInput { down(Offset(40f * 2f, height * 0.5f)); moveBy(Offset(60f, 0f)); moveBy(Offset(200f, 0f)); moveBy(Offset(300f, 0f)); up() }; settle()
        assertTrue("a swipe that starts 40 dp in (beyond the old 28 dp zone) opens Launchpad on Glance", launchpadShown())
    }
    @Test fun STR_01_aSwipeStartingBeyondTheEdgeZoneDoesNotOpenLaunchpad() {
        startFresh()
        rule.onRoot().performTouchInput { down(Offset(60f * 2f, height * 0.3f)); moveBy(Offset(60f, 0f)); moveBy(Offset(200f, 0f)); moveBy(Offset(300f, 0f)); up() }; settle()
        assertFalse(launchpadShown())
    }
    @Test fun STR_01_withLaunchpadOffGlanceDrawsNoLeftAffordanceAndNothingIsDead() {
        val sc = startFresh { it.copy(leftPage = LeftPage.Off) }
        assertFalse(tagShown("glance.handle.left")); assertFalse(tagShown("glance.indicator.launchpad"))
        swipeFromLeft(); assertFalse(launchpadShown())
        AppSmokeHarness.press(sc, KeyEvent.KEYCODE_N, ctrl = true); settle(); assertFalse(launchpadShown())
        assertTrue("the right side is untouched", tagShown("recents.handle"))
        swipeFromRight(); assertTrue(recentsShown())
    }
    @Test fun STR_14_withTheRightSideOffGlanceDrawsNoRecentsHandleOrDot() {
        startFresh { it.copy(rightSide = RightSide.Off) }
        assertFalse(tagShown("recents.handle")); assertFalse(tagShown("glance.indicator.right.Recents")); assertFalse(tagShown("glance.indicator.right.Dock"))
        assertTrue(tagShown("glance.handle.left"))
        swipeFromRight(); assertFalse(recentsShown())
    }
    @Test fun STR_14_withTheRightSideOnTheDockRailGlanceHasNoRecentsHandleButTheDotIsThere() {
        startFresh { it.copy(rightSide = RightSide.DockRail) }
        assertFalse(tagShown("recents.handle")); assertTrue(tagShown("glance.indicator.right.Dock"))
    }
    @Test fun HOM_23_theRailSettingKeepsTheQuietHandleOnGlance() {
        startFresh { it.copy(launchpadRail = true) }
        assertTrue(tagShown("glance.handle.left")); assertFalse("no pill when the rail setting is on", tagShown("glance.waiting"))
        rule.onNodeWithTag("glance.handle.left", useUnmergedTree = true).performClick(); settle(); assertTrue(launchpadShown())
    }
    @Test fun HOM_22_withGlanceOffThePagesKeepTheirOwnHandles() {
        AppSmokeHarness.seedApps("Alpha"); AppSmokeHarness.configure(); scenario = AppSmokeHarness.launch(); rule.waitForIdle(); settle()
        assertFalse(tagShown("glance.handle.left")); assertTrue(tagShown("recents.handle"))
        assertTrue(rule.onAllNodesWithContentDescription("Launchpad").fetchSemanticsNodes().isNotEmpty())
    }
    @Test fun HOM_23_theHandlesWorkAfterTheControlsPopOutToo() {
        startFresh(); MediaNow.publishForTest(track); settle()
        rule.onRoot().performTouchInput { click(Offset(width * 0.5f, height * 0.72f)) }; settle(300)
        rule.onNodeWithTag("glance.handle.left", useUnmergedTree = true).performClick(); settle(); assertTrue(launchpadShown())
    }

    @Test @Config(qualifiers = "w720dp-h360dp-land-xhdpi")
    fun STR_01_landscapeTheLeftEdgeSwipeAndTheHandlesWorkOnGlance() {
        startFresh(); assertTrue(tagShown("glance.handle.left")); assertTrue(tagShown("recents.handle"));
        swipeFromLeft(); assertTrue(launchpadShown())
    }
    @Test @Config(qualifiers = "w720dp-h360dp-land-xhdpi")
    fun STR_14_landscapeTheRightEdgeSwipeOpensRecentsOnGlance() { startFresh(); swipeFromRight(); assertTrue(recentsShown()) }

    @Test @Config(qualifiers = "ar-ldrtl-w360dp-h720dp-xhdpi")
    fun STR_01_rightToLeftOrderLaunchpadOpensFromThePhysicalRightEdgeAndRecentsFromTheLeft() {
        startFresh { it.copy(homeOrder = com.bos.sphere.core.data.HomeOrder.RightToLeft) }
        swipeFromRight(); assertTrue("RTL: Launchpad from the right edge", launchpadShown())
    }
    @Test @Config(qualifiers = "ar-ldrtl-w360dp-h720dp-xhdpi")
    fun STR_14_rightToLeftOrderRecentsOpensFromThePhysicalLeftEdge() { startFresh { it.copy(homeOrder = com.bos.sphere.core.data.HomeOrder.RightToLeft) }; swipeFromLeft(); assertTrue(recentsShown()) }
    @Test @Config(qualifiers = "ar-ldrtl-w360dp-h720dp-xhdpi")
    fun STR_01_rightToLeftOrderTheLaunchpadHandleSitsOnTheRightEdge() {
        startFresh { it.copy(homeOrder = com.bos.sphere.core.data.HomeOrder.RightToLeft) }
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(root.right, rule.onNodeWithTag("glance.handle.left", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.right, 2f)
        assertEquals(0f, rule.onNodeWithTag("recents.handle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left, 2f)
    }
}
