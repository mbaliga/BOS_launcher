package com.bos.sphere

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.bos.sphere.core.catalog.HomeEdit
import com.bos.sphere.core.catalog.HomeExtrasStore
import com.bos.sphere.core.catalog.HomePanelLinks
import com.bos.sphere.core.catalog.HomeState
import com.bos.sphere.core.catalog.ItemDef
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind
import com.bos.sphere.core.catalog.PanelTab
import com.bos.sphere.core.catalog.WidgetSpan
import com.bos.sphere.core.catalog.WidgetStacks
import com.bos.sphere.core.data.HomeShown
import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherStores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Wave 24 (stream homeTests): home edit, widgets, stacks and panel tiles when the landing is Glance (the default since wave 16)
 * and when it is Selected apps, in the real app under Robolectric. Every way in starts from a surface that still exists: the
 * Glance long-press quick sheet. Not a device run: the hosted widget views are Robolectric's, no real provider draws.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class HomeLandingItemsW24Test : TourBase() {
    private val stores get() = LauncherStores.get(AppSmokeHarness.app)
    private val extras get() = HomeExtrasStore.get(AppSmokeHarness.app)
    private fun landing() = if (glanceShown()) HomeShown.Glance else HomeShown.Pages
    private fun pageKeys() = stores.layout.layout.value.pages.flatMap { it.keys }

    private val clockKey = ItemKeys.widget(41)
    private val weatherKey = ItemKeys.widget(42)
    private val notesKey = ItemKeys.widget(43)
    private val stackKey = WidgetStacks.stackKey("s1")

    private fun provider(name: String) = AppWidgetProviderInfo().apply {
        provider = ComponentName("demo.widgets", "demo.widgets.$name")
        minWidth = 100; minHeight = 100
    }

    /** Three bound widgets (Robolectric's widget service), Weather and Notes stacked, Clock on its own, all on page 1. */
    private fun seedWidgets() {
        val awm = Shadows.shadowOf(AppWidgetManager.getInstance(AppSmokeHarness.app))
        awm.addBoundWidget(41, provider("Clock")); awm.addBoundWidget(42, provider("Weather")); awm.addBoundWidget(43, provider("Notes"))
        val cap: (com.bos.sphere.core.data.PageLayout) -> Int = { 20 }
        var s = HomeState(pages = listOf(HomePage(keys = emptyList(), id = 1L)))
        s = HomeEdit.add(s, ItemDef(clockKey, ItemKind.Widget, "Clock", "demo.widgets/.Clock"), 0, cap, WidgetSpan(2, 2))
        s = HomeEdit.add(s, ItemDef(weatherKey, ItemKind.Widget, "Weather", "demo.widgets/.Weather"), 0, cap, WidgetSpan(2, 2))
        s = HomeEdit.add(s, ItemDef(notesKey, ItemKind.Widget, "Notes", "demo.widgets/.Notes"), 0, cap, WidgetSpan(2, 2))
        s = WidgetStacks.create(s, weatherKey, notesKey, "s1")
        assertEquals(listOf(clockKey, stackKey), s.pages.single().keys)
        stores.layout.replace(HomeLayout(pages = s.pages, seeded = true))
        stores.prefs.update { it.copy(homePages = com.bos.sphere.core.data.HomePages.Paginated) } // wave 28: the widgets are on pages, so Pages is on
        extras.set(s)
    }

    /** Wave 45 (editHandles): in edit mode a widget item node carries the accessibility actions (widgets alone have "Make wider"); these are the item nodes, in page order. */
    private fun widgetItemNodes() = rule.onAllNodes(
        androidx.compose.ui.test.SemanticsMatcher("a widget item in edit mode") { n ->
            androidx.compose.ui.semantics.SemanticsActions.CustomActions in n.config &&
                n.config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].any { it.label == "Make wider" }
        },
        useUnmergedTree = true,
    ).fetchSemanticsNodes()

    private fun removeWidgetItem(index: Int) {
        val node = widgetItemNodes()[index]
        rule.runOnUiThread { node.config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].first { it.label == "Remove" }.action() }
        settle()
    }

    private fun startOnGlance(seed: () -> Unit = {}) {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        AppSmokeHarness.configureFresh()
        seed()
        launch()
        assertEquals(HomeShown.Glance, landing())
    }

    private fun openQuickSheet() {
        longPressAt(0.5f, 0.3f)
        assertTrue("a long press on Glance opens the quick sheet", quickSheetShown())
    }

    @Test fun HOM_12_onGlanceEditHomePagesInTheQuickSheetOpensTheEditCardOnSelectedApps() {
        startOnGlance()
        openQuickSheet()
        quickGoTo("Edit home")
        assertEquals("Edit home pages moves to the landing that has pages", HomeShown.Pages, landing())
        assertTrue("the edit card with its page carousel is up", editShown())
        check("edit from Glance", TourBase.Surface.Edit)
        back(); assertFalse("Back leaves edit mode", editShown())
    }

    @Test fun HOM_15_onGlanceAFilesTilePlacedFromTheQuickSheetSwitchesToSelectedAppsWhereItIsShown() {
        startOnGlance()
        openQuickSheet()
        quickGoTo("Widgets, folders and icons")
        rule.onNodeWithText("Files panel").performClick(); settle()
        val key = ItemKeys.link(HomePanelLinks.uri(PanelTab.Files))
        assertTrue("the tile is placed on a page", key in pageKeys())
        // Before wave 24 the tile went onto the Selected apps pages while Glance stayed up: placed, but nowhere to be seen.
        assertEquals("placing an item from a landing without pages shows the landing that has them", HomeShown.Pages, landing())
        assertTrue("the Files tile is drawn", descShown("Files"))
        check("files tile placed", TourBase.Surface.Home)
    }

    @Test fun HOM_07_widgetsAndAStackSurviveAGlanceRoundTripAndEditModeRemovesAWidgetWithUndo() {
        startOnGlance(::seedWidgets)
        // on Glance nothing of the pages is drawn, and nothing is lost either
        assertFalse(descShown("Remove from home", substring = true))
        assertEquals(listOf(clockKey, stackKey), pageKeys())
        stores.prefs.update { it.copy(glanceEnabled = false) }; settle()
        stores.prefs.update { it.copy(glanceEnabled = true) }; pressHome()
        assertEquals("a landing round trip keeps the widget and the stack", listOf(clockKey, stackKey), pageKeys())
        assertEquals(setOf(clockKey, weatherKey, notesKey, stackKey), extras.state.value.items.keys)

        openQuickSheet()
        quickGoTo("Edit home")
        assertTrue(editShown())
        // HOM-12 / HOM-14 (wave 45): the widget and the stack are each ONE item node with the edit actions (no handle on their content); a stacked member has none of its own
        assertEquals(2, widgetItemNodes().size)

        removeWidgetItem(0); settle()
        assertEquals(listOf(stackKey), pageKeys())
        rule.onAllNodesWithText("Undo", substring = true, useUnmergedTree = true).onFirst().performClick(); settle()
        assertEquals("one undo brings the widget back to its slot", listOf(clockKey, stackKey), pageKeys())
        assertTrue("and its definition with it", clockKey in extras.state.value.items)

        removeWidgetItem(1); settle()
        assertEquals(listOf(clockKey), pageKeys())
        rule.onAllNodesWithText("Undo", substring = true, useUnmergedTree = true).onFirst().performClick(); settle()
        assertEquals("the whole stack comes back", listOf(clockKey, stackKey), pageKeys())
        assertEquals(listOf(weatherKey, notesKey), WidgetStacks.stackAt(extras.state.value, stackKey)?.keys)
        check("edit with widgets", TourBase.Surface.Edit)
        back(); assertFalse(editShown())
    }

    @Test fun HOM_12_aWorkspaceSwitchDuringEditModeEndsEditModeInsteadOfBecomingAnUndoableEdit() {
        startOnGlance(::seedWidgets)
        val wm = com.bos.sphere.feature.dock.WorkspaceRepository.get(AppSmokeHarness.app)
        val other = wm.create("Travel"); settle()
        openQuickSheet()
        quickGoTo("Edit home")
        assertTrue(editShown())
        removeWidgetItem(0); settle()
        assertEquals(listOf(stackKey), pageKeys())
        rule.runOnUiThread { wm.switchTo(other) }; settle()
        // The switch replaces the whole arrangement: it is not an edit, so the session ends (as leaving Selected apps ends it).
        assertFalse("a workspace switch ends edit mode", editShown())
        assertEquals(other, wm.current.activeId)
        check("after the switch", TourBase.Surface.Home)
        // Back on the first workspace its arrangement is the one it was left with (the removal made in edit mode stands).
        rule.runOnUiThread { wm.switchTo(wm.current.workspaces.first { it.id != other }.id) }; settle()
        assertEquals(listOf(stackKey), pageKeys())
    }

    @Test fun HOM_18_w34_onGlanceTheFilesAndTimelinePanelsAreInTheCommsHubNotTheQuickSheetAndTheTimelineListsOpens() {
        startOnGlance { TourSeed.history("Alpha", "Bravo") }
        openQuickSheet()
        assertFalse("wave 34: the quick sheet no longer has the Files row", textShown("Files, usage and timeline"))
        closeSheet(); swipeFromLeft(); assertTrue("Comms opens from the left edge", launchpadShown())
        // Wave 25 (hubData): the panels are views of the Launchpad hub now (no sheet of their own); its switcher scrolls on a narrow screen.
        assertTrue("the Comms hub has the Files and Timeline panel views (wave 28: Usage is part of Timeline)", textShown("FILES") && textShown("TIMELINE") && !textShown("USAGE"))
        rule.onNodeWithText("TIMELINE", useUnmergedTree = true).performScrollTo().performClick(); settle()
        waitFor("the timeline lists the seeded opens") { textShown("Opened Bravo") && textShown("Opened Alpha") }
        // HOM-17 (wave 28): the usage block above the timeline offers "Allow usage access" without access (and does not crash).
        waitFor("the usage block is in the timeline") { textShown("Allow usage access") || rule.onAllNodesWithTag("usage.block").fetchSemanticsNodes().isNotEmpty() }
        check("panels on Glance")
        back(); assertEquals("the landing is untouched by looking at the panels", HomeShown.Glance, landing())
    }

    @Test fun HOM_11_rotatingOnGlanceKeepsTheWidgetsAndTheStackOfTheSelectedAppsPages() {
        startOnGlance(::seedWidgets)
        rotateAndCheck("Glance with widgets on the pages")
        stores.prefs.update { it.copy(glanceEnabled = false) }; settle()
        assertEquals("the portrait arrangement is the one that was seeded", listOf(clockKey, stackKey), pageKeys())
        assertEquals(listOf(weatherKey, notesKey), WidgetStacks.stackAt(extras.state.value, stackKey)?.keys)
        check("Selected after the rotation", TourBase.Surface.Home)
    }

    @Test fun HOM_15_onGlanceAFilesTileThatIsAlreadyOnThePagesIsShownWhenItIsAddedAgain() {
        val key = ItemKeys.link(HomePanelLinks.uri(PanelTab.Files))
        startOnGlance {
            val s = HomeEdit.add(HomeState(pages = listOf(HomePage(keys = emptyList(), id = 1L))), ItemDef(key, ItemKind.Link, "Files", HomePanelLinks.uri(PanelTab.Files)), 0, { 20 })
            stores.layout.replace(HomeLayout(pages = s.pages, seeded = true)); extras.set(s)
        }
        openQuickSheet()
        quickGoTo("Widgets, folders and icons")
        rule.onNodeWithText("Files panel").performClick(); settle()
        assertEquals("still one tile", 1, pageKeys().count { it == key })
        // Before the second pass the second tap returned early: nothing placed, nothing switched, nothing on screen changed.
        assertEquals("the existing tile is shown", HomeShown.Pages, landing())
        assertTrue(descShown("Files"))
    }
}
