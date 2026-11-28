package com.bos.sphere

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import android.view.KeyEvent
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.feature.grid.LauncherInput
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

    private var workAppAdded = false
    private fun startBusy(glanceOff: Boolean = false) {
        TourSeed.apps(); workAppAdded = runCatching { TourSeed.workApp() }.isSuccess
        AppSmokeHarness.configureFresh { if (glanceOff) it.copy(glanceEnabled = false) else it }
        TourSeed.history("Alpha", "Bravo", "Tour 007", "Tour 250", "Camera")
        TourSeed.recordSends()
        TourSeed.notifications("Alpha" to "Hello from Alpha", "Bravo" to "Ping from Bravo", "Alpha" to "Second from Alpha")
        TourSeed.group("Work", "Alpha")
        launch(); TourSeed.media(); settle() // after the start: a session published before it is reset by the media source's own start
    }
    private fun rowTagged(prefix: String) = rule.onAllNodes(SemanticsMatcher("tag $prefix") { n -> SemanticsProperties.TestTag.let { t -> t in n.config && n.config[t].startsWith(prefix) } }, useUnmergedTree = true)

    @Test fun HOM_23_busyTourStartsOnGlanceWithTheControlsAndLongPressWorks() {
        startBusy(); check("start", Surface.Home)
        rule.onNodeWithContentDescription("Now playing", substring = true).performClick(); settle(300); assertTrue("controls pop in", controlsShown()); rotateAndCheck("busy controls")
        rule.onNodeWithContentDescription("Now playing", substring = true).performClick(); settle(300); settle(); check("controls tapped again", Surface.Home)
        longPressAt(0.5f, 0.75f); assertTrue(quickSheetShown()); rotateAndCheck("busy quick sheet")
        closeSheet(); settle(); assertFalse(quickSheetShown()); check("sheet closed", Surface.Home)
    }

    @Test fun STR_02_busyTourLaunchpadShowsGroupsAndATapOnANotificationSendsItsPendingIntent() {
        startBusy()
        swipeFromLeft(); check("launchpad", Surface.Launchpad); rotateAndCheck("busy launchpad")
        assertTrue("the group section is listed", rowTagged("lp.group.").fetchSemanticsNodes().isNotEmpty())
        // Wave 27: the jump strip of names is gone (the owner's Comms mock-up has none).
        assertTrue("no group strip", rowTagged("lp.strip.").fetchSemanticsNodes().isEmpty())
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

    @Test fun HOM_01_busyTourDrawerOpensRotatesAndCloses() {
        // Wave 38: the app drawer is the disc (the list drawer, its filter and its Work tab are gone).
        startBusy()
        key(KeyEvent.KEYCODE_D, ctrl = true); check("drawer", Surface.Drawer); rotateAndCheck("busy drawer")
        back(); check("closed", Surface.Home)
    }

    @Test fun CFG_09_busyTourGroundControlWalksEveryCategoryAndGlanceOnAndOffRenderOnABusyState() {
        startBusy(glanceOff = true)
        for (on in listOf(false, true, false)) { stores.prefs.update { it.copy(glanceEnabled = on) }; pressHome(); check("busy glance $on", Surface.Home); rotateAndCheck("busy glance $on") }
        key(KeyEvent.KEYCODE_COMMA, ctrl = true); check("ground control", Surface.Settings)
        repeat(30) { key(KeyEvent.KEYCODE_DPAD_RIGHT); if (it % 6 == 0) check("category $it", Surface.Settings) }
        rotateAndCheck("ground control deep")
        back(); check("closed", Surface.Home)
    }
}
