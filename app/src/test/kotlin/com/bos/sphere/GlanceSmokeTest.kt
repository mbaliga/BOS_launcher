package com.bos.sphere

import android.view.KeyEvent
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import com.bos.sphere.core.data.GlanceHotspots
import com.bos.sphere.core.data.HotspotPreset
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.MediaNow
import com.bos.sphere.feature.grid.LauncherInput
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
 * HOM-23, HOM-22, STR-14, INP-16 (whole app): the real [MainActivity] on a fresh state lands on the Glance home; a tap pops the music
 * controls in and they pop out on inactivity; the hotspot presets show four, two, one or no hotspots; the right edge opens the
 * Recents panel from Glance. Not a device run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class GlanceSmokeTest {
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
        return AppSmokeHarness.launch().also { scenario = it; rule.waitForIdle() }
    }

    private fun settle(ms: Long = 1500) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); Snapshot.sendApplyNotifications(); rule.mainClock.advanceTimeBy(ms); org.robolectric.shadows.ShadowLooper.idleMainLooper(); rule.mainClock.advanceTimeBy(ms) }
    private fun longPressEmpty() { rule.onRoot().performTouchInput { longClick(Offset(width * 0.5f, height * 0.3f)) }; settle(300) }
    private fun graphIds() = LauncherInput.focus.nodes().map { it.id }
    private fun hotspotCount() = graphIds().count { it.startsWith("corner.") }
    private fun controlsShown() = rule.onAllNodesWithContentDescription("Next").fetchSemanticsNodes().isNotEmpty()
    private val track = MediaInfo("Someday Out of The Blue", "Artist", "pkg.music", playing = true, canPrev = true, canNext = true)

    @Test fun HOM_23_aFreshStateLandsOnTheGlanceHomeWithNoClockAndFourHotspotsAndNoHint() {
        startFresh(); settle()
        assertTrue(LauncherStores.get(AppSmokeHarness.app).prefs.prefs.value.glanceEnabled)
        assertEquals("wave 38: no clock or date on the Glance", 0, rule.onAllNodesWithTag("glance.time", useUnmergedTree = true).fetchSemanticsNodes().size + rule.onAllNodesWithTag("glance.date", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(4, hotspotCount())
        assertEquals("the empty-home hint belongs to Selected apps only", 0, rule.onAllNodesWithText("Long press to add apps").fetchSemanticsNodes().size)
        assertFalse("no dock on Glance", graphIds().any { it.startsWith("dock.") })
    }

    @Test fun HOM_23_aTapOnTheMusicLinePopsTheControlsInAndTheyPopOutOnInactivity() {
        startFresh(); settle()
        MediaNow.publishForTest(track); settle()
        rule.onNodeWithContentDescription("Now playing: Someday Out of The Blue · Artist").assertIsDisplayed()
        assertFalse(controlsShown())
        rule.onNodeWithContentDescription("Now playing: Someday Out of The Blue · Artist").performClick(); rule.mainClock.advanceTimeBy(700)
        assertTrue("the controls pop in", controlsShown())
        rule.mainClock.advanceTimeBy(5000); rule.mainClock.advanceTimeBy(700)
        assertFalse("and pop out", controlsShown())
    }

    @Test fun HOM_23_theHotspotPresetsShowFourTwoOneOrNone() {
        startFresh(); settle()
        val stores = LauncherStores.get(AppSmokeHarness.app)
        for ((p, n) in listOf(HotspotPreset.Two to 2, HotspotPreset.One to 1, HotspotPreset.None to 0, HotspotPreset.Four to 4)) {
            stores.prefs.update { it.copy(glanceHotspots = GlanceHotspots.preset(p)!!) }; settle()
            assertEquals("$p", n, hotspotCount())
        }
    }

    @Test fun HOM_23_withNoHotspotsTheEdgeSwipesAndShortcutsStillWork() {
        val sc = startFresh { it.copy(glanceHotspots = emptySet()) }; settle()
        assertEquals(0, hotspotCount())
        AppSmokeHarness.press(sc, KeyEvent.KEYCODE_R, ctrl = true); settle()
        assertTrue("Ctrl+R opens the recents panel", rule.onAllNodesWithTag("recents.panel").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun STR_14_aSwipeInFromTheRightEdgeOpensTheRecentsPanelFromGlance() {
        startFresh(); settle()
        rule.onRoot().performTouchInput { down(Offset(width - 6f, height / 2f)); moveBy(Offset(-60f, 0f)); moveBy(Offset(-200f, 0f)); moveBy(Offset(-300f, 0f)); up() }; settle()
        assertTrue(rule.onAllNodesWithTag("recents.panel").fetchSemanticsNodes().isNotEmpty() && rule.onAllNodesWithTag("lp.card").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun HOM_23_glanceIsOnForNewStateAndTheQuickSheetHasNoGlanceTileButAGroundControlRow() {
        startFresh(); settle()
        assertTrue(LauncherStores.get(AppSmokeHarness.app).prefs.prefs.value.glanceEnabled)
        longPressEmpty(); assertTrue("the long press opens the quick sheet", rule.onAllNodesWithTag("quick.sheet", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue("wave 34: the sheet has no Glance tile (the switch is Ground Control, Home screen; see HomeUnifyRowsTest)", rule.onAllNodesWithTag("quick.glance", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("quick.groundcontrol", useUnmergedTree = true).assertExists()
    }
}
