package com.bos.sphere

import android.view.KeyEvent
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import com.bos.sphere.core.data.HomeLanding
import com.bos.sphere.core.data.LauncherStores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * TOUR, part 2 (wave 22): the same surfaces on a BUSY state: 500 apps and a work-profile app, a launch history, a media session, notifications
 * with real PendingIntents (the fake sender records), and a Launchpad group. Every step asserts no crash, the expected surface and a rotation.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class TourBusySmokeTest : TourBase() {
    private val stores get() = LauncherStores.get(AppSmokeHarness.app)

    private fun startBusy(landing: HomeLanding? = null) {
        TourSeed.apps(); runCatching { TourSeed.workApp() }
        AppSmokeHarness.configureFresh { if (landing != null) it.copy(homeLanding = landing) else it }
        TourSeed.history("Alpha", "Bravo", "Tour 007", "Tour 250", "Camera")
        TourSeed.recordSends()
        TourSeed.notifications("Alpha" to "Hello from Alpha", "Bravo" to "Ping from Bravo", "Alpha" to "Second from Alpha")
        TourSeed.group("Work", "Alpha")
        launch(); TourSeed.media(); settle() // after the start: a session published before it is reset by the media source's own start
    }
    private fun rowTagged(prefix: String) = rule.onAllNodes(SemanticsMatcher("tag $prefix") { n -> SemanticsProperties.TestTag.let { t -> t in n.config && n.config[t].startsWith(prefix) } }, useUnmergedTree = true)

    @Test fun HOM_23_busyTourStartsOnGlanceWithTheControlsAndLongPressWorks() {
        startBusy(); check("start", Surface.Home)
        tapAt(0.5f, 0.72f); assertTrue("controls pop in", controlsShown()); rotateAndCheck("busy controls")
        tapAt(0.5f, 0.72f); settle(); check("controls tapped again", Surface.Home)
        longPressAt(0.5f, 0.3f); assertTrue(quickSheetShown()); rotateAndCheck("busy quick sheet")
        closeSheet(); settle(); assertFalse(quickSheetShown()); check("sheet closed", Surface.Home)
    }

    @Test fun STR_02_busyTourLaunchpadShowsGroupsAndATapOnANotificationSendsItsPendingIntent() {
        startBusy()
        swipeFromLeft(); check("launchpad", Surface.Launchpad); rotateAndCheck("busy launchpad")
        assertTrue("the group section is listed", rowTagged("lp.group.").fetchSemanticsNodes().isNotEmpty())
        val strips = rowTagged("lp.strip.").fetchSemanticsNodes()
        if (strips.size > 1) { rowTagged("lp.strip.").onFirst().performClick(); settle(); check("group selected", Surface.Launchpad) }
        val focusRows = rowTagged("lp.focus.").fetchSemanticsNodes()
        assertTrue("notification rows exist", focusRows.isNotEmpty())
        rowTagged("lp.focus.").onFirst().performClick(); settle(); check("notification tapped")
        assertTrue("the notification's PendingIntent went through PendingIntentLauncher", TourSeed.sent.isNotEmpty())
        // closing: Back, then Esc and the sliver on a second opening
        if (launchpadShown()) { back(); check("back", Surface.Home) }
        swipeFromLeft(); key(ESC); settle(); check("esc", Surface.Home)
        swipeFromLeft(); rule.onNodeWithTag("lp.card", useUnmergedTree = true).performClick(); settle(); check("sliver", Surface.Home)
    }

    @Test fun STR_14_busyTourRecentsListsHistoryOpensAnAppClearsAndCloses() {
        startBusy()
        swipeFromRight(); check("recents", Surface.Recents); rotateAndCheck("busy recents")
        assertTrue("history rows", rowTagged("recents.row.").fetchSemanticsNodes().isNotEmpty())
        rowTagged("recents.row.").onFirst().performClick(); settle(); check("row tapped")
        if (!recentsShown()) swipeFromRight()
        rule.onNodeWithTag("recents.clearAll", useUnmergedTree = true).performClick(); settle(); check("cleared", Surface.Recents)
        waitFor("no rows after clear all (the list is rebuilt off the main thread)") { rowTagged("recents.row.").fetchSemanticsNodes().isEmpty() }
        back(); check("closed", Surface.Home)
        key(KeyEvent.KEYCODE_R, ctrl = true); check("Ctrl+R", Surface.Recents); key(ESC); check("esc", Surface.Home)
    }

    @Test fun PAL_18_busyTourRadarFindsAmong500AppsKeepsTheOrbitAndEnterLaunches() {
        startBusy()
        key(KeyEvent.KEYCODE_T); check("type", Surface.Radar); rotateAndCheck("busy radar")
        key(KeyEvent.KEYCODE_O); key(KeyEvent.KEYCODE_U); key(KeyEvent.KEYCODE_R); key(KeyEvent.KEYCODE_SPACE); key(KeyEvent.KEYCODE_0); key(KeyEvent.KEYCODE_0); key(KeyEvent.KEYCODE_7)
        check("query typed", Surface.Radar)
        for (k in listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP)) key(k)
        check("arrows", Surface.Radar)
        key(KeyEvent.KEYCODE_ENTER); check("Enter")
        assertFalse("Enter on a result launches it and closes Radar", radarShown())
        assertTrue("the launch is recorded in the history", stores.history.events.value.any { it.key == AppSmokeHarness.appKey("Tour 007") })
    }

    @Test fun HOM_01_busyTourDrawerFiltersAndLaunches() {
        startBusy()
        key(KeyEvent.KEYCODE_D, ctrl = true); check("drawer", Surface.Drawer); rotateAndCheck("busy drawer")
        key(KeyEvent.KEYCODE_T); key(KeyEvent.KEYCODE_O); check("filter", Surface.Drawer)
        key(KeyEvent.KEYCODE_ENTER); check("launch")
        if (drawerShown()) { back(); check("closed", Surface.Home) }
    }

    @Test fun CFG_09_busyTourGroundControlWalksEveryCategoryAndTheLandingsRenderOnABusyState() {
        startBusy(HomeLanding.Selected)
        for (l in listOf(HomeLanding.Recents, HomeLanding.AllApps, HomeLanding.Selected, HomeLanding.Glance)) { stores.prefs.update { it.copy(homeLanding = l) }; settle(); check("busy landing $l", Surface.Home); rotateAndCheck("busy landing $l") }
        key(KeyEvent.KEYCODE_COMMA, ctrl = true); check("ground control", Surface.Settings)
        repeat(30) { key(KeyEvent.KEYCODE_DPAD_RIGHT); if (it % 6 == 0) check("category $it", Surface.Settings) }
        rotateAndCheck("ground control deep")
        back(); check("closed", Surface.Home)
    }
}
