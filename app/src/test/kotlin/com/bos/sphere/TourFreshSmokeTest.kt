package com.bos.sphere

import android.view.KeyEvent
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.bos.sphere.feature.grid.LauncherInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.MediaNow
import com.bos.sphere.core.data.LeftPage
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.CornerSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * TOUR, part 1 (wave 22): a fresh state (Glance default, 4 apps) walked surface by surface, each one opened every way the product
 * promises, closed every way, and rotated while open. Wave 22 tourSmoke; the INP / STR / HOM / PAL ids named in the test names.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class TourFreshSmokeTest : TourBase() {
    private val stores get() = LauncherStores.get(AppSmokeHarness.app)
    private val track = MediaInfo("Someday Out of The Blue", "Artist", "pkg.music", playing = true, canPrev = true, canNext = true)

    private fun startFresh(edit: (com.bos.sphere.core.data.LauncherPrefs) -> com.bos.sphere.core.data.LauncherPrefs = { it }) {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        AppSmokeHarness.configureFresh(edit)
        launch()
    }

    @Test fun HOM_23_tourStartOnGlanceThenTheControlsPopInAndOutAndTheQuickSheetOpens() {
        startFresh(); MediaNow.publishForTest(track); settle()
        assertTrue(stores.prefs.prefs.value.glanceEnabled); assertTrue(glanceShown())
        check("start", Surface.Home); assertFalse(controlsShown())
        tapAt(0.5f, 0.72f); assertTrue("a tap pops the controls in", controlsShown()); check("controls in", Surface.Home)
        rotateAndCheck("controls in, rotated")
        // out again: a tap on the controls' empty area or inactivity pops them out
        waitFor("the controls pop out after inactivity", 12_000) { !controlsShown() }
        check("controls out", Surface.Home)
        longPressAt(0.5f, 0.3f); assertTrue("a long press opens the quick sheet", quickSheetShown()); check("quick sheet")
        rotateAndCheck("quick sheet, rotated")
        closeSheet(); settle(); assertFalse("Back closes the quick sheet", quickSheetShown()); check("quick sheet closed", Surface.Home)
    }

    @Test fun HOM_22_tourGlanceOffAndOnAndTheHomeButtonReturnsToWhatHomeStartsOn() {
        startFresh()
        for (on in listOf(false, true, false, true)) {
            stores.prefs.update { it.copy(glanceEnabled = on) }; pressHome()
            check("glance $on", Surface.Home)
            assertEquals("Glance $on: home shows " + (if (on) "Glance" else "the first page"), on, glanceShown())
            rotateAndCheck("glance $on")
            key(KeyEvent.KEYCODE_L, ctrl = true, shift = true); check("the removed cycle shortcut does nothing, glance $on")
            assertEquals(on, stores.prefs.prefs.value.glanceEnabled)
        }
    }

    @Test fun STR_01_tourLaunchpadOpensFiveWaysAndClosesBySliverBackAndEscape() {
        startFresh()
        // edge swipe, closed by the sliver
        swipeFromLeft(); check("edge swipe", Surface.Launchpad); rotateAndCheck("launchpad")
        onNodeTapSliver(); assertFalse("the sliver closes it", launchpadShown()); check("sliver closed", Surface.Home)
        // the handle, closed by Back
        rule.onNodeWithTag("glance.handle.left", useUnmergedTree = true).performClick(); settle(); check("handle", Surface.Launchpad)
        back(); assertFalse("Back closes it", launchpadShown()); check("back closed", Surface.Home)
        // the indicator glyph, closed by Esc
        rule.onNodeWithTag("glance.indicator.launchpad", useUnmergedTree = true).performClick(); settle(); check("indicator", Surface.Launchpad)
        key(ESC); settle(); assertFalse("Esc closes it", launchpadShown()); check("esc closed", Surface.Home)
        // the shortcut
        key(KeyEvent.KEYCODE_N, ctrl = true); check("shortcut", Surface.Launchpad)
        key(KeyEvent.KEYCODE_N, ctrl = true); settle(); check("shortcut again")
        back(); settle(); check("back", Surface.Home)
        // a corner action
        stores.shortcuts.setCorner(Corner.TopLeft, CornerSlot.Single(CornerItem.Action(CornerActionId.OpenLaunchpad))); settle()
        rule.onNodeWithTagOrDesc("Top left hotspot").performClick(); settle(); check("corner", Surface.Launchpad)
        back(); check("back from corner", Surface.Home)
    }

    @Test fun STR_14_tourRecentsOpensBySwipeHandleAndShortcutClearAllAndCloses() {
        startFresh()
        stores.history.record(AppSmokeHarness.appKey("Bravo")); stores.history.record(AppSmokeHarness.appKey("Delta")); settle()
        swipeFromRight(); check("swipe", Surface.Recents); rotateAndCheck("recents")
        key(ESC); check("esc", Surface.Home)
        rule.onNodeWithTag("recents.handle", useUnmergedTree = true).performClick(); settle(); check("handle", Surface.Recents)
        assertTrue("Clear all is offered with a history", tagShown("recents.clearAll"))
        rule.onNodeWithTag("recents.clearAll", useUnmergedTree = true).performClick(); settle(); check("clear all", Surface.Recents)
        waitFor("clear all left no rows (the list is rebuilt off the main thread)") { !tagShownPrefix("recents.row.") }
        back(); check("back", Surface.Home)
        key(KeyEvent.KEYCODE_R, ctrl = true); check("Ctrl+R", Surface.Recents)
        onNodeTapSliver(); check("sliver", Surface.Home)
    }

    @Test fun PAL_18_tourRadarOpensByLetterCornerAndShortcutKeepsTheOrbitWhileTypingAndClears() {
        startFresh()
        key(KeyEvent.KEYCODE_B); check("letter", Surface.Radar); rotateAndCheck("radar")
        key(KeyEvent.KEYCODE_R); key(KeyEvent.KEYCODE_A); check("typing", Surface.Radar)
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_UP); key(KeyEvent.KEYCODE_DPAD_LEFT); check("arrows", Surface.Radar)
        back(); check("back", Surface.Home)
        key(KeyEvent.KEYCODE_SLASH, ctrl = true); check("shortcut", Surface.Radar)
        key(ESC); check("esc", Surface.Home)
        stores.shortcuts.setCorner(Corner.TopRight, CornerSlot.Single(CornerItem.Action(CornerActionId.Search))); settle()
        rule.onNodeWithTagOrDesc("Top right hotspot").performClick(); settle(); check("corner", Surface.Radar)
        key(KeyEvent.KEYCODE_D); key(KeyEvent.KEYCODE_E); check("typing 2", Surface.Radar)
        val before = stores.history.events.value.size
        key(KeyEvent.KEYCODE_ENTER); check("Enter on a result")
        assertFalse("Enter on a result closes Radar", radarShown())
        assertTrue("Enter on a result launched it (recorded in the history)", stores.history.events.value.size > before)
        check("closed", Surface.Home)
    }

    @Test fun HOM_01_tourDrawerOpensFiltersAndLaunchesAnApp() {
        startFresh()
        key(KeyEvent.KEYCODE_D, ctrl = true); check("drawer", Surface.Drawer); rotateAndCheck("drawer")
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("cam"); settle(); check("filter", Surface.Drawer)
        assertTrue("the filter keeps the match", LauncherInput.focus.nodes().any { it.id.startsWith("drawer.app.camera") })
        assertFalse("and drops the others", LauncherInput.focus.nodes().any { it.id.startsWith("drawer.app.alpha") })
        rule.onNodeWithText("Camera", useUnmergedTree = true).performClick(); settle(); check("launch")
        assertTrue("the launch is recorded in the history", stores.history.events.value.any { it.key == AppSmokeHarness.appKey("Camera") })
        if (drawerShown()) back()
        check("closed", Surface.Home)
    }

    @Test fun CFG_09_tourGroundControlOpensRotatesAndCloses() {
        startFresh()
        key(KeyEvent.KEYCODE_COMMA, ctrl = true); check("ground control", Surface.Settings); rotateAndCheck("ground control")
        // every category by the Right key (the category bar owns focus)
        repeat(30) { key(KeyEvent.KEYCODE_DPAD_RIGHT) }
        check("walked right", Surface.Settings)
        repeat(30) { key(KeyEvent.KEYCODE_DPAD_LEFT) }
        check("walked left", Surface.Settings)
        back(); check("closed", Surface.Home)
    }

    @Test fun HOM_12_tourEditModeOnSelectedCardCarouselAddDeleteUndoDone() {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        AppSmokeHarness.configure(); launch()
        val camera = AppSmokeHarness.appKey("Camera"); val alpha = AppSmokeHarness.appKey("Alpha")
        // an emptied home: the hint opens the edit card with the page carousel and the add carousel
        stores.layout.replace(com.bos.sphere.core.data.HomeLayout(pages = listOf(com.bos.sphere.core.data.HomePage(keys = emptyList(), id = 7L)), seeded = true)); settle()
        rule.onNodeWithText("Long press to add apps").performClick(); settle(); check("edit", Surface.Edit); rotateAndCheck("edit mode")
        rule.onNodeWithContentDescription("Add to home: Camera").performClick(); settle()
        assertEquals(listOf(camera), stores.layout.layout.value.pages.flatMap { it.keys }); check("added", Surface.Edit)
        stores.layout.replace(com.bos.sphere.core.data.HomeLayout(pages = listOf(com.bos.sphere.core.data.HomePage(keys = listOf(camera), id = 7L), com.bos.sphere.core.data.HomePage(keys = listOf(alpha), id = 8L)), seeded = true)); settle()
        rule.onNodeWithContentDescription("Delete page 2").performScrollTo().performClick(); settle(); check("deleted", Surface.Edit)
        assertEquals(1, stores.layout.layout.value.pages.size)
        rule.onNodeWithText("Undo", useUnmergedTree = true).performClick(); settle(); check("undone", Surface.Edit)
        assertEquals("undo brought the page back", 2, stores.layout.layout.value.pages.size)
        rotateAndCheck("edit mode after undo")
        back(); check("done", Surface.Home); assertFalse(editShown())
    }

    @Test fun HOM_23_tourGroundControlChangesLaunchpadOffAndBackTheBackgroundPairAndTheHotspotsReachTheHome() {
        startFresh()
        key(KeyEvent.KEYCODE_COMMA, ctrl = true); check("ground control", Surface.Settings)
        stores.prefs.update { it.copy(leftPage = LeftPage.Off) }; settle(); check("launchpad off while Ground Control is open", Surface.Settings)
        back(); check("closed", Surface.Home)
        assertFalse("Comms Off: no handle", tagShown("glance.handle.left")); swipeFromLeft(); assertFalse("and no swipe", launchpadShown()); check("off", Surface.Home)
        stores.prefs.update { it.copy(leftPage = LeftPage.Launchpad) }; settle()
        assertTrue("Comms back On: the handle is back", tagShown("glance.handle.left")); swipeFromLeft(); check("on again", Surface.Launchpad); back()
        // the background pair and the hotspot count change under the open home without a crash
        for (pair in listOf(com.bos.sphere.core.data.BgItem.None to com.bos.sphere.core.data.BgItem.None, com.bos.sphere.core.data.BackgroundChoice.DEFAULT.let { it.slot1 to it.slot2 })) {
            stores.prefs.update { it.copy(backgroundSlot1 = pair.first, backgroundSlot2 = pair.second) }; settle(); check("background $pair", Surface.Home)
        }
        stores.prefs.update { it.copy(glanceHotspots = emptySet()) }; settle(); check("no hotspots", Surface.Home)
        stores.prefs.update { it.copy(glanceHotspots = Corner.entries.toSet()) }; settle(); check("four hotspots", Surface.Home)
        rotateAndCheck("after the settings changes")
    }

    /** Found by the tour: on Glance the left Launchpad handle stayed drawn over the home card's sliver while Recents was open, so a tap on the sliver opened Launchpad instead of returning home. */
    @Test fun STR_14_onGlanceTheRecentsSliverReturnsHomeAndTheLaunchpadHandleStepsAsideWhileRecentsIsOpen() {
        startFresh()
        key(KeyEvent.KEYCODE_R, ctrl = true); check("recents", Surface.Recents)
        assertFalse("the left handle is gone while the card is pushed aside", tagShown("glance.handle.left"))
        onNodeTapSliver(); check("sliver", Surface.Home); assertFalse(launchpadShown())
        swipeFromRight(); check("swipe", Surface.Recents); assertFalse(tagShown("glance.handle.left"))
        onNodeTapSliver(); check("sliver again", Surface.Home)
    }
    @Test @Config(qualifiers = "ar-ldrtl-w360dp-h720dp-xhdpi")
    fun STR_14_rightToLeftTheSameSliverReturnsHomeOnGlance() {
        startFresh()
        key(KeyEvent.KEYCODE_R, ctrl = true); check("recents", Surface.Recents)
        onNodeTapSliver(); check("sliver", Surface.Home); assertFalse(launchpadShown())
    }

    /** Found by review: a surface drawn over the home must not leave an edge handle reachable. A tap on the physical edge middle while Radar, the drawer or Ground Control is open must not open Launchpad or Recents under or over it. */
    @Test fun STR_01_tourEdgeTapsWhileAnotherSurfaceIsOpenDoNotOpenLaunchpadOrRecents() {
        startFresh()
        for ((name, opener) in listOf<Pair<String, () -> Unit>>(
            "radar" to { key(KeyEvent.KEYCODE_SLASH, ctrl = true) }, "drawer" to { key(KeyEvent.KEYCODE_D, ctrl = true) }, "ground control" to { key(KeyEvent.KEYCODE_COMMA, ctrl = true) })) {
            opener(); val shown = open(); assertFalse("$name opened", shown.contains(Surface.Home))
            tapAt(0.02f, 0.5f); check("$name, left edge tapped"); assertFalse("$name: Comms opened by an edge tap", launchpadShown())
            // Wave 25 (dead touch): Radar takes a tap on empty space itself and closes; that one tap must not also open Launchpad, and Home behind it is then live,
            // so a second edge tap is a real edge tap on Home and is not part of this check.
            if (name == "radar") { assertFalse("radar: the edge tap closes Radar without opening Recents", recentsShown()); assertEquals("radar closed by the empty-space tap", setOf(Surface.Home), open().toSet()); continue }
            tapAt(0.98f, 0.5f); check("$name, right edge tapped"); assertFalse("$name: Recents opened by an edge tap", recentsShown())
            assertEquals("$name stays open", shown, open())
            back(); key(ESC); check("$name closed", Surface.Home)
        }
    }

    // small helpers
    private fun onNodeTapSliver() { rule.onNodeWithTag("lp.card", useUnmergedTree = true).performClick(); settle() }
    private fun tagShownPrefix(p: String) = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("tag $p") { n ->
        androidx.compose.ui.semantics.SemanticsProperties.TestTag.let { t -> t in n.config && n.config[t].startsWith(p) } }, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onNodeWithTagOrDesc(d: String) = onNodeWithContentDescription(d, substring = true)
}
