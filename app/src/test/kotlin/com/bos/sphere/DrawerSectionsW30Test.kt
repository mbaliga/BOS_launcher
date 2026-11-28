package com.bos.sphere

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomeOrder
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.PuckActions
import com.bos.sphere.core.data.PuckActionsStore
import com.bos.sphere.core.data.PuckSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * CFG-15, HOM-01 (wave 30, puckSections): the home's all-apps disc shows the user's four drawer shortcuts as wedge sections, its centre is Back,
 * an app section launches the app and an empty one opens the editor. Not a device run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class DrawerSectionsW30Test : TourBase() {
    private val stores get() = LauncherStores.get(AppSmokeHarness.app)
    private fun wheelShown() = descShown("Back") && !settingsShown()

    private fun start() {
        AppSmokeHarness.seedApps(*Array(12) { "App %02d".format(it) })
        AppSmokeHarness.configureFresh { it.copy(glanceEnabled = true, homeOrder = HomeOrder.LeftToRight, homePages = HomePages.Paginated) }
        stores.layout.replace(HomeLayout(pages = List(2) { HomePage(keys = listOf(AppSmokeHarness.appKey("App %02d".format(it))), id = it + 1L) }, seeded = true))
        launch()
    }

    private fun openDisc() {
        rule.onNodeWithContentDescription("All apps", substring = true).performClick(); settle()
        assertTrue("the disc is open", wheelShown())
    }

    private fun onDisc(label: String): SemanticsNodeInteraction = rule.onAllNodesWithContentDescription(label).onLast()

    @Test fun CFG_15_w30_aFreshInstallShowsFlashlightAlarmBellAndDoNotDisturbOnTheRing() {
        start(); openDisc()
        // wave 38: eight segments, the first four (and slot 7, which the corner shows) are in view at rest; the rest turn into view
        for (id in PuckActions.DEFAULT_IDS.take(4)) assertTrue("${id.label} is a section", descShown(id.label))
        assertFalse("no search glyph on the hub", descShown("Search apps"))
    }

    @Test fun CFG_15_w30_theCentreBackButtonClosesTheDiscLikeTheSystemBack() {
        start(); openDisc()
        onDisc("Back").performClick(); settle()
        assertFalse("Back closed the disc", wheelShown())
        assertTrue("and left the home", glanceShown())
    }

    @Test fun CFG_15_w30_theSectionsFollowTheChosenSlots() {
        start()
        PuckActionsStore.get(AppSmokeHarness.app).setAll(listOf(PuckSlot.Action(CornerActionId.Search), PuckSlot.Action(CornerActionId.Phone), PuckSlot.Empty, PuckSlot.Empty)); settle()
        openDisc()
        assertTrue(descShown("Radar")); assertTrue(descShown("Phone"))
        assertTrue("an empty slot is there to long-press", descShown("Drawer shortcut 3, empty") && descShown("Drawer shortcut 4, empty"))
        assertFalse("the replaced defaults are gone", descShown("Flashlight"))
    }

    @Test fun CFG_15_w30_aRadarSectionOpensRadarAndClosesTheDisc() {
        start()
        PuckActionsStore.get(AppSmokeHarness.app).set(0, PuckSlot.Action(CornerActionId.Search)); settle()
        openDisc()
        onDisc("Radar").performSemanticsAction(SemanticsActions.OnClick); settle()
        assertFalse(wheelShown())
        assertTrue("search is reachable from a section", radarShown())
    }

    @Test fun CFG_15_w30_anAppSectionShowsTheAppAndLaunchesItClosingTheDisc() {
        start()
        PuckActionsStore.get(AppSmokeHarness.app).set(1, PuckSlot.App(AppSmokeHarness.appKey("App 03"))); settle()
        openDisc()
        assertTrue("the app's name is the section's label", descShown("App 03"))
        onDisc("App 03").performSemanticsAction(SemanticsActions.OnClick); settle()
        assertFalse("launching closes the disc", wheelShown())
    }

    @Test fun CFG_15_w30_anEmptySectionOpensTheDrawerShortcutsEditorOnLongPress() {
        start()
        PuckActionsStore.get(AppSmokeHarness.app).set(2, PuckSlot.Empty); settle()
        openDisc()
        onDisc("Drawer shortcut 3, empty").performSemanticsAction(SemanticsActions.OnLongClick); settle()
        assertFalse(wheelShown())
        assertTrue("Ground Control opened", settingsShown())
        assertTrue("on the App drawer category (it holds the Drawer shortcuts and labels rows)", textShown("Show labels under glyphs") || textShown("Drawer shortcuts"))
    }

    @Test fun CFG_15_w30_theWave29RingSelectionIsMigratedOnTheFirstStart() {
        start()
        val sp = com.bos.sphere.core.data.TolerantPrefs.of(AppSmokeHarness.app, "puck_actions")
        sp.edit().clear().putString("actions", "Phone,Messages,Maps,Camera,Email").commit()
        AppSmokeHarness.resetProcess()
        val migrated = PuckActionsStore.get(AppSmokeHarness.app).slots.value
        assertEquals(listOf(CornerActionId.Phone, CornerActionId.Messages, CornerActionId.Maps, CornerActionId.Camera).map { PuckSlot.Action(it) }, migrated.take(4))
        assertEquals("wave 38: the other four slots are filled", 8, migrated.size)
        assertFalse(sp.contains("actions"))
    }
}
