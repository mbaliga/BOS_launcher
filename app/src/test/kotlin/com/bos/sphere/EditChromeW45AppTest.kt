package com.bos.sphere

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithContentDescription
import com.bos.sphere.core.data.EditChromeStore
import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.LeftPage
import com.bos.sphere.core.data.RightSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOM-07, HOM-12 (wave 45, editChrome) in the real app under Robolectric: Edit home opens the single-page edit mode (top bar with the one Done, the hint for the
 * first three entries only, the tray, the left tray and the edge handles hidden, the page dots as the switcher), Cancel restores the entry snapshot and asks only
 * when something changed, Back is Cancel, Done keeps. Not a device run: no compositor, no real touch pipeline, no screen reader.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class EditChromeW45AppTest : TourBase() {
    private val stores get() = LauncherStores.get(AppSmokeHarness.app)
    private fun key(i: Int) = AppSmokeHarness.appKey("App %02d".format(i))
    private fun pages() = stores.layout.layout.value.pages.map { it.keys }

    private fun start(edit: (LauncherPrefs) -> LauncherPrefs = { it }) {
        AppSmokeHarness.seedApps(*Array(12) { "App %02d".format(it) })
        AppSmokeHarness.configureFresh { edit(it.copy(glanceEnabled = false, homePages = HomePages.Paginated, rightSide = RightSide.RecentsPanel, leftPage = LeftPage.Launchpad)) }
        AppSmokeHarness.app.getSharedPreferences("liftoff_misc", android.content.Context.MODE_PRIVATE).edit().putBoolean("defaultHomeSeeded", true).commit()
        stores.layout.replace(HomeLayout(pages = listOf(HomePage(keys = listOf(key(0), key(1)), id = 7L), HomePage(keys = listOf(key(2)), id = 8L)), seeded = true))
        launch()
    }

    /** W47: the remove badge is gone; an icon is selected (long press) and the action bar's Remove takes it away. */
    private fun removeViaBar(label: String) {
        rule.onAllNodesWithContentDescription(label, useUnmergedTree = true).onFirst().performTouchInput { longClick(center) }; settle(400)
        rule.onNodeWithTag("item-bar.remove", useUnmergedTree = true).performClick(); settle()
    }

    private fun enterEdit() {
        longPressAt(0.5f, 0.8f); assertTrue("a long press on empty space opens the quick sheet", quickSheetShown())
        quickGoTo("Edit home"); assertTrue("edit mode is on", tagShown("edit.bar"))
    }

    @Test fun HOM_12_editHomeShowsTheTopBarWithTheOneDoneAndTheTray() {
        start(); enterEdit()
        rule.onNodeWithText("Editing page 1 of 2").assertIsDisplayed()
        assertEquals("one Done", 1, rule.onAllNodesWithText("Done", useUnmergedTree = true).fetchSemanticsNodes().size)
        listOf("edit.cancel", "edit.undo", "edit.redo", "edit.done", "edit.tray", "edit.tray.apps", "edit.tray.widgets", "edit.tray.folders", "edit.tray.page").forEach { assertTrue("$it is shown", tagShown(it)) }
        assertFalse("no toast-like hint line from the old bar", textShown("Editing home: drag items to rearrange, long-press for more"))
        assertFalse("the old bottom pill Add page is gone", textShown("Add page"))
    }

    @Test fun HOM_12_theLeftTrayAndTheEdgeHandlesAreHiddenWhileEditingAndBackAfterwards() {
        start()
        assertTrue("the Recents handle is there before", tagShown("recents.handle"))
        enterEdit()
        assertFalse("hidden while editing", tagShown("recents.handle"))
        assertFalse(descShown("Open recents"))
        rule.onNodeWithTag("edit.done", useUnmergedTree = true).performClick(); settle()
        assertTrue("and back after Done", tagShown("recents.handle"))
    }

    @Test fun HOM_12_theHintShowsOnTheFirstThreeEntriesOnly() {
        start()
        val hint = "Drag to move. Tap an item to resize or change it."
        for (n in 1..3) {
            enterEdit()
            assertTrue("entry $n shows the hint", textShown(hint))
            rule.onNodeWithTag("edit.done", useUnmergedTree = true).performClick(); settle()
        }
        enterEdit()
        assertFalse("the fourth entry does not", textShown(hint))
        val sp = com.bos.sphere.core.data.TolerantPrefs.of(AppSmokeHarness.app, EditChromeStore.FILE)
        assertEquals(4, sp.getInt("entries", 0))
    }

    @Test fun HOM_12_cancelWithNoChangeLeavesAtOnce() {
        start(); enterEdit()
        rule.onNodeWithTag("edit.cancel", useUnmergedTree = true).performClick(); settle()
        assertFalse(tagShown("edit.bar")); assertFalse(textShown("Discard changes?"))
    }

    @Test fun HOM_12_cancelAfterARemovalAsksThenRestoresTheSnapshotAndDoneKeepsIt() {
        start()
        val before = pages()
        enterEdit()
        removeViaBar("App 00")
        assertTrue("the removal happened", key(0) !in pages().flatten())
        rule.onNodeWithTag("edit.cancel", useUnmergedTree = true).performClick(); settle()
        assertTrue("Cancel asks when something changed", textShown("Discard changes?")); assertTrue(tagShown("edit.bar"))
        rule.onNodeWithText("Keep editing").performClick(); settle()
        assertTrue(tagShown("edit.bar")); assertFalse(textShown("Discard changes?"))
        rule.onNodeWithTag("edit.cancel", useUnmergedTree = true).performClick(); settle()
        rule.onNodeWithText("Discard").performClick(); settle()
        assertFalse("edit mode is over", tagShown("edit.bar"))
        assertEquals("the snapshot taken on entry is back", before, pages())
        // and Done keeps
        enterEdit()
        removeViaBar("App 00")
        rule.onNodeWithTag("edit.done", useUnmergedTree = true).performClick(); settle()
        assertFalse(tagShown("edit.bar")); assertTrue("Done commits", key(0) !in pages().flatten())
    }

    @Test fun HOM_12_systemBackIsCancelAndAsksOnlyWhenSomethingChanged() {
        start(); enterEdit()
        back(); assertFalse("nothing changed: Back leaves at once", tagShown("edit.bar"))
        enterEdit()
        removeViaBar("App 00")
        back(); assertTrue("changed: Back asks", textShown("Discard changes?")); assertTrue(tagShown("edit.bar"))
        rule.onNodeWithText("Discard").performClick(); settle()
        assertFalse(tagShown("edit.bar")); assertTrue("restored", key(0) in pages().flatten())
    }

    @Test fun HOM_12_theTitleCountsThePagesAndAPageDotSwitchesPageInEditMode() {
        start(); enterEdit()
        rule.onNodeWithText("Editing page 1 of 2").assertIsDisplayed()
        rule.onNodeWithTag("pages.dot.1", useUnmergedTree = true).performClick(); settle()
        assertTrue("still editing", tagShown("edit.bar"))
        rule.onNodeWithText("Editing page 2 of 2").assertIsDisplayed()
    }

    @Test fun HOM_12_thePlusPageChipAddsOneEmptyPageAndTheCountFollows() {
        start(); enterEdit()
        rule.onNodeWithTag("edit.tray.page", useUnmergedTree = true).performClick(); settle(900)
        assertEquals(3, pages().size); assertTrue(pages()[2].isEmpty())
        rule.onNodeWithText("Editing page 3 of 3").assertIsDisplayed()
        rule.onNodeWithTag("edit.undo", useUnmergedTree = true).performClick(); settle()
        assertEquals("undo takes the page away again", 2, pages().size)
    }

    @Test fun HOM_12_theAppsChipOpensThePickerAndAPickPlacesTheAppOnTheCurrentPage() {
        start(); enterEdit()
        rule.onNodeWithTag("edit.tray.apps", useUnmergedTree = true).performClick(); settle()
        assertTrue("the app picker", textShown("Add an app"))
        rule.onNodeWithText("App 07").performClick(); settle()
        assertTrue("placed", key(7) in pages()[0])
    }

    @Test fun HOM_12_theTitleOpensThePageEditorAndTheOldBarComesBack() {
        start(); enterEdit()
        openPageEditorFromEdit()
        assertTrue(descShown("Page carousel"))
        assertTrue("the page editor has the same bar with its own title", tagShown("edit.bar") && textShown("Editing pages"))
        assertFalse("the single-page title is gone", textShown("Editing page 1 of 2"))
        assertFalse("and so is the tray", tagShown("edit.tray"))
        assertEquals("one Done", 1, rule.onAllNodesWithText("Done", useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
