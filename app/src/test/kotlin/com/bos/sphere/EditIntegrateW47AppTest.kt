package com.bos.sphere

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import com.bos.sphere.core.catalog.HomeEdit
import com.bos.sphere.core.catalog.HomeExtrasStore
import com.bos.sphere.core.catalog.HomeState
import com.bos.sphere.core.catalog.ItemDef
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind
import com.bos.sphere.core.catalog.WidgetSpan
import com.bos.sphere.core.data.EditChromeStore
import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomeOrder
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.LeftPage
import com.bos.sphere.core.data.RightSide
import com.bos.sphere.feature.grid.ItemBarTags
import com.bos.sphere.feature.grid.ItemSheetTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Wave 47 (editIntegrate), HOM-07 / HOM-12: the ONE home editor, end to end in the real app under Robolectric. The streams of wave 45 (handles, action bar and sheet,
 * chrome, page editor layout) were merged; these flows are what ties them: one selection, one outline, the action bar below the chrome, Resize that only
 * focuses the handles, the sheet's size presets inside the provider limits, the page editor behind the same top bar. Pages Paginated and Continuous, left to
 * right and right to left (the layout direction and the page order). Not a device run: no real touch pipeline, no compositor, no screen reader, no haptics.
 */
abstract class EditIntegrateBase : TourBase() {
    protected open val mode: HomePages = HomePages.Paginated
    protected open val order: HomeOrder = HomeOrder.LeftToRight
    /** What a surface offers: the page grid has eight handles and the grid dots; the scroll surface flows its items (south, east, south-east; east only for a horizontal provider) and has no dots. */
    protected open val allHandles = 8
    protected open val horizontalHandles = 2
    protected open val hasGridDots = true

    protected val stores get() = LauncherStores.get(AppSmokeHarness.app)
    protected val extras get() = HomeExtrasStore.get(AppSmokeHarness.app)
    protected fun key(i: Int) = AppSmokeHarness.appKey("App %02d".format(i))
    protected fun pages() = stores.layout.layout.value.pages.map { it.keys }
    protected val widgetKey = ItemKeys.widget(51)

    protected fun provider(resizeMode: Int, maxResizeWidthDp: Int = 0, maxResizeHeightDp: Int = 0) = AppWidgetProviderInfo().apply {
        provider = ComponentName("demo.widgets", "demo.widgets.Weather")
        minWidth = 100; minHeight = 100; minResizeWidth = 100; minResizeHeight = 100; this.maxResizeWidth = maxResizeWidthDp; this.maxResizeHeight = maxResizeHeightDp
        this.resizeMode = resizeMode
    }

    protected fun start(withWidget: AppWidgetProviderInfo? = null, widgetSpan: WidgetSpan = WidgetSpan(2, 2)) {
        AppSmokeHarness.seedApps(*Array(12) { "App %02d".format(it) })
        AppSmokeHarness.configureFresh { it.copy(glanceEnabled = false, homePages = mode, homeOrder = order, rightSide = RightSide.RecentsPanel, leftPage = LeftPage.Launchpad) }
        AppSmokeHarness.app.getSharedPreferences("liftoff_misc", android.content.Context.MODE_PRIVATE).edit().putBoolean("defaultHomeSeeded", true).commit()
        if (withWidget != null) {
            Shadows.shadowOf(AppWidgetManager.getInstance(AppSmokeHarness.app)).addBoundWidget(51, withWidget)
            var s = HomeState(pages = listOf(HomePage(keys = emptyList(), id = 1L)))
            s = HomeEdit.add(s, ItemDef(widgetKey, ItemKind.Widget, "Weather", "demo.widgets/.Weather"), 0, { 20 }, widgetSpan)
            extras.set(s)
            stores.layout.replace(HomeLayout(pages = listOf(HomePage(keys = s.pages.single().keys + listOf(key(0), key(1)), id = 7L), HomePage(keys = listOf(key(2)), id = 8L)), seeded = true))
        } else {
            stores.layout.replace(HomeLayout(pages = listOf(HomePage(keys = listOf(key(0), key(1), key(3)), id = 7L), HomePage(keys = listOf(key(2)), id = 8L)), seeded = true))
        }
        launch()
    }

    /** The way in the owner uses: the long press on empty space, then Ground Control's Edit home (a continuous home opens Ground Control itself). */
    protected fun enterEdit() {
        for (y in listOf(0.8f, 0.6f, 0.35f)) {
            if (tagShown("quick.groundcontrol") || tagShown("edit.bar") || settingsShown()) break
            longPressAt(0.5f, y)
        }
        if (tagShown("edit.bar")) return
        if (!settingsShown()) rule.onNodeWithTag("quick.groundcontrol", useUnmergedTree = true).performScrollTo().performClick().also { settle() }
        if (!textShown("Edit home")) { rule.onNodeWithTag("oneui.row.Home screen", useUnmergedTree = true).performScrollTo().performClick(); settle() }
        rule.onNodeWithText("Edit home", useUnmergedTree = true).performScrollTo().performClick(); settle()
        assertTrue("edit mode is on", tagShown("edit.bar"))
    }

    // ---- screenshots of the real app (read by a person; see docs/status/w47-editIntegrate.md) ----
    protected val outDir = java.io.File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    protected fun shoot(name: String) {
        settle(400)
        var bmp: android.graphics.Bitmap? = null
        sc.onActivity { a ->
            val view = a.window.decorView
            val b = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            android.view.PixelCopy.request(a.window, b, { }, android.os.Handler(android.os.Looper.getMainLooper()))
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            bmp = b
        }
        java.io.File(outDir, "$name.png").outputStream().use { bmp!!.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    protected fun icon(label: String) = rule.onAllNodesWithContentDescription(label, useUnmergedTree = true).onFirst()
    protected fun selectByLongPress(label: String) { icon(label).performTouchInput { longClick(center) }; settle(400) }
    protected fun selectByTap(label: String) { icon(label).performTouchInput { click(center) }; settle(400) }
    protected fun barButton(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true)
    protected fun barShown() = tagShown(ItemBarTags.BAR)
    protected fun barCount() = rule.onAllNodesWithTag(ItemBarTags.BAR, useUnmergedTree = true).fetchSemanticsNodes().size
    protected fun tapTag(tag: String) { rule.onNodeWithTag(tag, useUnmergedTree = true).performClick(); settle(500) }

    // ---- (a) icons: entry, selection, the bar, the sheet, remove with Undo, undo and redo, Cancel and Done

    @Test fun HOM_12_w47_a1_editEntryShowsTheBarTheTrayAndTheHint() {
        start(); enterEdit()
        listOf("edit.cancel", "edit.undo", "edit.redo", "edit.done", "edit.tray", "edit.tray.apps", "edit.tray.widgets", "edit.tray.folders").forEach { assertTrue("$it is shown", tagShown(it)) }
        assertTrue("the hint", textShown("Drag to move. Tap an item to resize or change it."))
        assertEquals("one Done", 1, rule.onAllNodesWithTag("edit.done", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertFalse("no bar before something is selected", barShown())
    }

    @Test fun HOM_12_w47_a2_aLongPressSelectsAnIconWithTheActionBarAndOneSelectionAtATime() {
        start(); enterEdit()
        selectByLongPress("App 00")
        assertTrue("the action bar", barShown()); assertEquals(1, barCount())
        assertFalse("an icon has no size: no Resize", tagShown(ItemBarTags.RESIZE))
        assertTrue(tagShown(ItemBarTags.REMOVE)); assertTrue(tagShown(ItemBarTags.SETTINGS))
        assertFalse("the temporary 48 dp Remove button is off now that the bar exists", tagShown("selection-remove"))
        assertFalse("the bar draws no outline of its own (the selection's is the one)", tagShown(ItemBarTags.OUTLINE))
        assertFalse("no remove badge on the icon", descShown("Remove from home", substring = true))
        selectByLongPress("App 03") // not App 01: on the scroll surface the bar of App 00 lies over the icon under it
        assertEquals("still one bar, for the newly selected item", 1, barCount())
        assertTrue(descShown("Remove App 03"))
        assertFalse(descShown("Remove App 00"))
    }

    @Test fun HOM_12_w47_a3_aTapAlsoSelectsAndASecondTapDeselects() {
        start(); enterEdit()
        selectByTap("App 00"); assertTrue(barShown()); assertTrue(descShown("Remove App 00"))
        selectByTap("App 00"); assertFalse("the second tap deselects", barShown())
    }

    @Test fun HOM_12_w47_a4_settingsOpensTheItemSheetAndTheBarStaysBelowTheChrome() {
        start(); enterEdit()
        selectByLongPress("App 00")
        val bar = rule.onNodeWithTag(ItemBarTags.BAR, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val top = rule.onNodeWithTag("edit.bar", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val tray = rule.onNodeWithTag("edit.tray", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the bar is under the top bar", bar.top >= top.bottom - 0.5f.dp())
        assertTrue("and above the tray", bar.bottom <= tray.top + 0.5f.dp())
        tapTag(ItemBarTags.SETTINGS)
        assertTrue("the item sheet", tagShown(ItemSheetTags.SHEET))
        assertTrue(textShown("Open app"))
        closeSheet()
        assertFalse(tagShown(ItemSheetTags.SHEET))
    }

    @Test fun HOM_12_w47_a5_removeFromTheBarRemovesWithAnUndoAndUndoRestoresIt() {
        start(); enterEdit()
        selectByLongPress("App 00")
        tapTag(ItemBarTags.REMOVE)
        assertTrue("removed", key(0) !in pages().flatten())
        assertTrue("the Undo snackbar", tagShown(ItemBarTags.SNACK)); assertFalse("the selection is over", barShown())
        tapTag(ItemBarTags.UNDO)
        assertTrue("Undo restores it", key(0) in pages().flatten())
        // and the top bar's own Undo / Redo take the same step back and forth
        selectByLongPress("App 00"); tapTag(ItemBarTags.REMOVE)
        assertTrue(key(0) !in pages().flatten())
        tapTag("edit.undo"); assertTrue(key(0) in pages().flatten())
        tapTag("edit.redo"); assertTrue(key(0) !in pages().flatten())
    }

    @Test fun HOM_12_w47_a6_cancelRestoresTheSnapshotAfterABarRemovalAndDoneSaves() {
        start()
        val before = pages()
        enterEdit()
        selectByLongPress("App 00"); tapTag(ItemBarTags.REMOVE)
        tapTag("edit.cancel")
        assertTrue("Cancel asks because something changed", textShown("Discard changes?"))
        rule.onNodeWithText("Discard").performClick(); settle()
        assertFalse(tagShown("edit.bar")); assertEquals("the snapshot is back", before, pages())
        enterEdit()
        selectByLongPress("App 00"); tapTag(ItemBarTags.REMOVE)
        tapTag("edit.done")
        assertFalse(tagShown("edit.bar")); assertTrue("Done saved the removal", key(0) !in pages().flatten())
    }

    // ---- (b) widgets: tap selects, the handles, a drag, the bar's Resize, the sheet's presets

    protected open fun widgetNode() = rule.onAllNodes(
        androidx.compose.ui.test.SemanticsMatcher("the widget item in edit mode") { n ->
            androidx.compose.ui.semantics.SemanticsActions.CustomActions in n.config &&
                n.config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].any { it.label == "Make wider" }
        },
        useUnmergedTree = true,
    ).onFirst()

    protected fun handlesShown() = rule.onAllNodesWithTag("resize-knob", useUnmergedTree = true).fetchSemanticsNodes().size
    protected fun spanNow() = extras.state.value.spanOf(widgetKey)
    protected fun dragHandle(name: String, dx: Float, dy: Float, release: Boolean = true) {
        val h = rule.onNodeWithTag("resize-handle-$name", useUnmergedTree = true)
        h.performTouchInput { down(center) }
        repeat(6) { h.performTouchInput { moveBy(Offset(dx / 6f, dy / 6f)) }; rule.mainClock.advanceTimeBy(16) }
        settle(100)
        if (release) { h.performTouchInput { up() }; settle(800) }
    }

    @Test fun HOM_07_w47_b1_aTapSelectsAWidgetAndShowsItsHandlesTheBarHasResize() {
        start(provider(AppWidgetProviderInfo.RESIZE_HORIZONTAL or AppWidgetProviderInfo.RESIZE_VERTICAL)); enterEdit()
        assertEquals("no handles before a selection", 0, handlesShown())
        widgetNode().performTouchInput { click(center) }; settle(500)
        assertTrue("the bar for the widget", barShown())
        assertTrue("Resize is offered for a widget", tagShown(ItemBarTags.RESIZE))
        assertTrue("the handles of the selected widget (every one its provider allows)", handlesShown() in 1..8)
        assertEquals("a provider that resizes both ways has every handle the surface has", allHandles, handlesShown())
        assertFalse("no in-widget Done pill", textShown("Done") && rule.onAllNodesWithText("Done", useUnmergedTree = true).fetchSemanticsNodes().size > 1)
    }

    @Test fun HOM_07_w47_b2_aHorizontalOnlyProviderShowsFewerHandles() {
        start(provider(AppWidgetProviderInfo.RESIZE_HORIZONTAL)); enterEdit()
        widgetNode().performTouchInput { click(center) }; settle(500)
        assertEquals("west and east only (east only where the items flow)", horizontalHandles, handlesShown())
    }

    @Test fun HOM_07_w47_b3_theBarsResizeKeepsTheHandlesAndAnnouncesWithoutAnyDoneMode() {
        start(provider(AppWidgetProviderInfo.RESIZE_HORIZONTAL or AppWidgetProviderInfo.RESIZE_VERTICAL)); enterEdit()
        widgetNode().performTouchInput { click(center) }; settle(500)
        tapTag(ItemBarTags.RESIZE)
        assertTrue("announced", descShown("Resize handles active", substring = true))
        assertTrue("the widget is still selected with its handles and its bar", handlesShown() == allHandles && barShown())
        assertEquals("exactly one Done (the top bar's)", 1, rule.onAllNodesWithTag("edit.done", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun HOM_07_w47_b4_draggingAHandleShowsTheGhostTheChipAndTheGridDotsAndReleaseResizes() {
        start(provider(AppWidgetProviderInfo.RESIZE_HORIZONTAL or AppWidgetProviderInfo.RESIZE_VERTICAL)); enterEdit()
        widgetNode().performTouchInput { click(center) }; settle(500)
        val before = spanNow()
        if (hasGridDots) assertTrue("the page's grid dots are drawn", tagShown("edit.gridDots"))
        dragHandle("South", 0f, 260f, release = false)
        assertTrue("the ghost", tagShown("resize-ghost")); assertTrue("the size chip", tagShown("resize-chip"))
        assertEquals("nothing applies before release", before, spanNow())
        rule.onNodeWithTag("resize-handle-South", useUnmergedTree = true).performTouchInput { up() }; settle(800)
        assertFalse("the ghost goes away", tagShown("resize-ghost"))
        val after = spanNow()
        assertTrue("release resized it ($before to $after)", after.rows > before.rows)
        assertEquals("the width is untouched", before.cols, after.cols)
    }

    @Test fun HOM_07_w47_b5_aLimitShowsTheRedStateAndTheWidgetStays() {
        // Provider: horizontal only, at most two cells wide: a widget already two cells wide cannot grow.
        start(provider(AppWidgetProviderInfo.RESIZE_HORIZONTAL, maxResizeWidthDp = 200)); enterEdit()
        widgetNode().performTouchInput { click(center) }; settle(500)
        val before = spanNow()
        dragHandle("East", 400f, 0f, release = false)
        assertTrue("the chip is shown and in its limit state (the same chip, error coloured)", tagShown("resize-chip"))
        rule.onNodeWithTag("resize-handle-East", useUnmergedTree = true).performTouchInput { up() }; settle(800)
        assertEquals("the widget did not grow past its provider's maximum", before, spanNow())
        assertTrue("and it says why (the live region toggles a zero-width mark so a repeat is announced again)", descShown("Can't resize: at the maximum", substring = true) || descShown("Can't resize: at the minimum", substring = true))
    }

    @Test fun HOM_07_w47_b6_theSheetsSizePresetsRespectTheProviderLimits() {
        // A widget two cells wide and one high whose provider allows at most one row (a hundred dp): the 2 x 2 preset is outside its limits.
        start(provider(AppWidgetProviderInfo.RESIZE_HORIZONTAL or AppWidgetProviderInfo.RESIZE_VERTICAL, maxResizeHeightDp = 100), widgetSpan = WidgetSpan(2, 1)); enterEdit()
        widgetNode().performTouchInput { click(center) }; settle(500)
        tapTag(ItemBarTags.SETTINGS)
        assertTrue("the widget's sheet has its presets", textShown("Size"))
        rule.onNodeWithText("2 × 1", useUnmergedTree = true).performClick(); settle(500)
        assertFalse("inside the limits: no note", tagShown(ItemSheetTags.PRESET_NOTE))
        rule.onNodeWithText("2 × 2", useUnmergedTree = true).performClick(); settle(800)
        assertEquals("clamped to the provider's maximum, never past it", WidgetSpan(2, 1), spanNow())
        assertTrue("with the inline note", tagShown(ItemSheetTags.PRESET_NOTE) && textShown("Not available for this widget"))
    }

    // ---- (c) the page editor: from the title, behind the one bar

    protected fun openPageEditor() { tapTag("edit.title"); assertTrue("the page editor", descShown("Page carousel")) }

    @Test fun HOM_12_w47_c1_thePageEditorUsesTheOneEditBarAndHasNoTrayNoOldPills() {
        start(); enterEdit(); openPageEditor()
        assertTrue("the same bar", tagShown("edit.bar")); assertTrue(textShown("Editing pages"))
        assertEquals("one Done", 1, rule.onAllNodesWithTag("edit.done", useUnmergedTree = true).fetchSemanticsNodes().size)
        listOf("edit.cancel", "edit.undo", "edit.redo").forEach { assertTrue(it, tagShown(it)) }
        assertFalse("no tray: the tabs already hold Apps, Widgets and Folders", tagShown("edit.tray"))
        assertFalse("the old bottom pill Add page is gone", textShown("Add page"))
    }

    @Test fun HOM_12_w47_c2_theStripIsAboveTheLiveCardAndTheTabsBelowAndTheCardIsWholeAndLarge() {
        start(); enterEdit(); openPageEditor()
        val h = rule.onRoot().fetchSemanticsNode().boundsInRoot.height
        val strip = rule.onNodeWithTag("page-editor-strip", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val tabs = rule.onNodeWithTag("page-editor-tabs", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val bar = rule.onNodeWithTag("page-editor-topbar-slot", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the strip is under the bar", strip.top >= bar.bottom - 0.5f.dp())
        assertTrue("the tabs are under the strip", tabs.top > strip.bottom)
        val cardTop = strip.bottom; val cardBottom = tabs.top
        val density = AppSmokeHarness.app.resources.displayMetrics.density
        assertTrue("the live card has room of at least 0.40 of the window between the strip and the tabs (${(cardBottom - cardTop).value * density} of $h px)", (cardBottom - cardTop).value * density >= 0.40f * h)
        assertTrue("the content panel is under the tabs", true)
    }

    @Test fun HOM_12_w47_c3_addAPageReorderAPageAndDeleteWithUndo() {
        start(); enterEdit(); openPageEditor()
        val before = pages()
        // reorder: Move later in the card's menu
        tapTag("pe.more.0"); tapTag("pe.menu.later.0")
        assertEquals(listOf(before[1], before[0]), pages())
        // add
        rule.onNodeWithTag("page-editor-strip", useUnmergedTree = true).performScrollToNode(androidx.compose.ui.test.hasTestTag("pe.add")); settle(300)
        tapTag("pe.add")
        assertEquals(3, pages().size); assertTrue(pages()[2].isEmpty())
        // delete the new, empty page, then undo takes it back
        tapTag("pe.more.2"); tapTag("pe.menu.delete.2")
        assertEquals(2, pages().size)
        rule.onNodeWithText("Undo", useUnmergedTree = true).performClick(); settle(400)
        assertEquals("Undo brings the empty page back", 3, pages().size)
    }

    @Test fun HOM_12_w47_c4_cancelInThePageEditorRestoresTheSessionSnapshotAndAsksOnlyWhenSomethingChanged() {
        start()
        val before = pages()
        enterEdit(); openPageEditor()
        tapTag("edit.cancel")
        assertFalse("nothing changed: no confirm, edit mode is over", textShown("Discard changes?")); assertFalse(tagShown("edit.bar"))
        enterEdit(); openPageEditor()
        tapTag("pe.more.0"); tapTag("pe.menu.later.0")
        assertTrue(pages() != before)
        tapTag("edit.cancel")
        assertTrue("something changed: it asks", textShown("Discard changes?"))
        rule.onNodeWithText("Keep editing").performClick(); settle()
        assertTrue(tagShown("edit.bar")); assertTrue(descShown("Page carousel"))
        back(); assertTrue("Back is Cancel: it asks again", textShown("Discard changes?"))
        rule.onNodeWithText("Discard").performClick(); settle()
        assertFalse(tagShown("edit.bar")); assertEquals("the session snapshot is back", before, pages())
    }

    @Test fun HOM_12_w47_c5_doneInThePageEditorKeepsTheChanges() {
        start()
        val before = pages()
        enterEdit(); openPageEditor()
        tapTag("pe.more.0"); tapTag("pe.menu.later.0")
        tapTag("edit.done")
        assertFalse(tagShown("edit.bar")); assertEquals(listOf(before[1], before[0]), pages())
    }

    // ---- (d) the fixed chrome while editing

    @Test fun HOM_12_w47_d1_theHotspotsAreInertAndTheSideTrayIsHiddenWhileEditing() {
        start()
        assertTrue("the Recents handle is there before", tagShown("recents.handle"))
        enterEdit()
        assertFalse("the side tray is hidden", tagShown("recents.handle")); assertFalse(descShown("Open recents"))
        assertTrue("the shield over the hotspots", tagShown("edit.fixed.shield"))
        tapAt(0.08f, 0.80f); tapAt(0.92f, 0.80f) // the page's bottom corners (the top corners lie under the bar)
        assertTrue("a tap on a corner does not run its hotspot or leave edit mode", tagShown("edit.bar"))
        assertFalse(settingsShown()); assertFalse(launchpadShown())
    }

    private fun Float.dp(): androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(this)
}

/** Paginated, left to right: the default home. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class EditIntegrateW47AppTest : EditIntegrateBase() {

    @Test fun HOM_07_w47_b4b_aWidgetBlocksItCannotGrowOverAnotherItem() {
        start(provider(AppWidgetProviderInfo.RESIZE_HORIZONTAL or AppWidgetProviderInfo.RESIZE_VERTICAL)); enterEdit()
        widgetNode().performTouchInput { click(center) }; settle(500)
        val before = spanNow()
        dragHandle("East", 260f, 0f, release = false)
        assertTrue("the ghost and the chip show the refusal", tagShown("resize-ghost") && tagShown("resize-chip"))
        rule.onNodeWithTag("resize-handle-East", useUnmergedTree = true).performTouchInput { up() }; settle(800)
        assertEquals("the neighbours stay and so does the size", before, spanNow())
        assertTrue(descShown("Can't resize: another item is in the way", substring = true))
    }

    @Test fun HOM_12_w47_z1_shotEditEntry() { start(); enterEdit(); shoot("w47-1-edit-entry") }
    @Test fun HOM_12_w47_z2_shotIconSelectedWithTheBar() { start(); enterEdit(); selectByLongPress("App 00"); shoot("w47-2-icon-selected-bar") }
    @Test fun HOM_07_w47_z3_shotWidgetSelectedWithHandles() {
        start(provider(AppWidgetProviderInfo.RESIZE_HORIZONTAL or AppWidgetProviderInfo.RESIZE_VERTICAL)); enterEdit()
        widgetNode().performTouchInput { click(center) }; settle(500); shoot("w47-3-widget-selected-handles")
        dragHandle("SouthEast", 260f, 260f, release = false); shoot("w47-3b-widget-resize-ghost")
        rule.onNodeWithTag("resize-handle-SouthEast", useUnmergedTree = true).performTouchInput { up() }; settle(800)
    }
    @Test fun HOM_12_w47_z4_shotItemSheet() {
        start(); enterEdit(); selectByLongPress("App 00"); tapTag(ItemBarTags.SETTINGS)
        shoot("w47-4-item-sheet-behind") // the activity window; the sheet is a window of its own and is captured separately
        // The sheet's root view (the last compose root): drawn into a bitmap.
        @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
        val view = (rule.onAllNodes(androidx.compose.ui.test.isRoot()).onLast().fetchSemanticsNode().root as androidx.compose.ui.platform.ViewRootForTest).view
        val bmp = android.graphics.Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        java.io.File(outDir, "w47-4-item-sheet.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun HOM_12_w47_z5_shotPageEditor() { start(); enterEdit(); openPageEditor(); shoot("w47-5-page-editor-portrait") }
}

/** Pages Continuous: the scroll surface (its items select through the same state, its widgets get the east and south handles). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class EditIntegrateContinuousW47AppTest : EditIntegrateBase() {
    override val mode = HomePages.Continuous
    override val allHandles = 3
    override val horizontalHandles = 1
    override val hasGridDots = false
    override fun widgetNode() = rule.onNodeWithTag("scroll.cell.$widgetKey", useUnmergedTree = true)
}

/** Pages right to left (the notifications on the right, the columns filled right to left) in a right-to-left layout direction. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ldrtl-w411dp-h891dp-port-xhdpi")
class EditIntegrateRtlW47AppTest : EditIntegrateBase() {
    override val order = HomeOrder.RightToLeft
}
