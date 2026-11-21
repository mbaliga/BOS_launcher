package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.data.ClockFormat
import com.bos.sphere.core.data.HomeTiles
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ThemeVariant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Wave 45 (editSheet), HOM-07 / HOM-12: the item action bar and the item sheet on screen (Robolectric; nothing here is device-verified). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class ItemEditUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private val log = ArrayList<String>()

    @Before fun setUp() { L10n.setLanguage("en"); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
    @After fun tearDown() { L10n.setLanguage("en") }

    /** Draws the activity and every other window on top of it (the bottom sheet is a dialog window), in stacking order. */
    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        val content = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        rule.runOnUiThread {
            content.draw(canvas)
            runCatching {
                val wmg = Class.forName("android.view.WindowManagerGlobal")
                val g = wmg.getMethod("getInstance").invoke(null)
                @Suppress("UNCHECKED_CAST") val names = wmg.getMethod("getViewRootNames").invoke(g) as Array<String>
                for (n in names) {
                    val v = wmg.getMethod("getRootView", String::class.java).invoke(g, n) as android.view.View
                    if (v === content.rootView || v.width == 0 || v.height == 0) continue
                    val loc = IntArray(2); v.getLocationOnScreen(loc)
                    canvas.save(); canvas.translate(loc[0].toFloat(), loc[1].toFloat()); v.draw(canvas); canvas.restore()
                }
            }
        }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(outDir, "$name.png").length() > 2000)
    }

    private val px get() = rule.activity.resources.displayMetrics.density

    private val itemBounds = Rect(300f, 500f, 480f, 680f)

    /** A page with a few fake tiles, the action layer, and (optionally) the sheet rows as the sheet would hold them. */
    private fun host(key: String, bounds: Rect? = itemBounds, withNotice: Boolean = false, scroll: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit = {}): EditSelectionState {
        val sel = EditSelectionState()
        var notice by mutableStateOf<RemovedNotice?>(null)
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF0A0B12))) {
                for (i in 0 until 4) Box(Modifier.offset((40 + i * 90).dp, 120.dp).size(64.dp).background(Color(0x33FFFFFF), RoundedCornerShape(14.dp)))
                content()
                ItemActionLayer(
                    sel, notice, { notice = null }, onResize = { log += "resize:$it" },
                    onRemove = { log += "remove:$it"; sel.clear(); notice = RemovedNotice("Removed", { log += "undo" }) },
                    onSettings = { log += "settings:$it" }, label = { if (it == HomeTiles.CLOCK) "Clock" else it },
                    reserveBottom = 120.dp, scrollSurface = scroll,
                )
            }
        }
        rule.runOnUiThread { sel.select(key); bounds?.let { sel.report(key, it) } }
        rule.waitForIdle()
        return sel
    }

    @Test fun HOM_12_bar_10_appearsOnSelectionWithThreeButtonsOfAtLeast48DpForAWidget() {
        val key = ItemKeys.widget(3)
        host(key)
        rule.onNodeWithTag(ItemBarTags.BAR).assertIsDisplayed()
        for (t in listOf(ItemBarTags.RESIZE, ItemBarTags.REMOVE, ItemBarTags.SETTINGS)) {
            val b = rule.onNodeWithTag(t).getUnclippedBoundsInRoot()
            assertTrue("$t is 48 dp tall", b.bottom - b.top >= 48.dp)
            assertTrue("$t is 48 dp wide", b.right - b.left >= 48.dp)
        }
        rule.onNodeWithTag(ItemBarTags.SETTINGS).performClick(); rule.onNodeWithTag(ItemBarTags.RESIZE).performClick()
        assertEquals(listOf("settings:$key", "resize:$key"), log)
    }

    @Test fun HOM_12_bar_11_aTileHasNoResizeButtonRatherThanADeadOne() {
        host(HomeTiles.CLOCK)
        rule.onNodeWithTag(ItemBarTags.RESIZE).assertDoesNotExist()
        rule.onNodeWithTag(ItemBarTags.REMOVE).assertIsDisplayed()
        rule.onNodeWithTag(ItemBarTags.SETTINGS).assertIsDisplayed()
    }

    @Test fun HOM_12_bar_12_isBelowTheItemWhenThereIsRoom() {
        host(HomeTiles.CLOCK)
        val below = rule.onNodeWithTag(ItemBarTags.BAR).getUnclippedBoundsInRoot()
        assertTrue("below the item", below.top.value * px > itemBounds.bottom - 1f)
    }

    @Test fun HOM_12_bar_16_isAboveTheItemWhenTheItemIsAtTheDock() {
        val sel = host(HomeTiles.CLOCK, Rect(300f, 2000f, 480f, 2180f)) // the item sits on the reserved dock band
        val above = rule.onNodeWithTag(ItemBarTags.BAR).getUnclippedBoundsInRoot()
        assertTrue("above the item", above.bottom.value * px <= 2000f)
        assertNotNull(sel.selectedKey)
    }

    // ---- W48 (editGaps)
    private fun barPx() = rule.onNodeWithTag(ItemBarTags.BAR).getUnclippedBoundsInRoot().let { Rect(it.left.value * px, it.top.value * px, it.right.value * px, it.bottom.value * px) }
    private fun underCell(i: Rect) = Rect(i.left, i.bottom, i.right, i.bottom + 24f * px + 8f * px + ActionBarMath.UNDER_FACTOR * i.height)

    private fun scrollCase(item: Rect) {
        host(HomeTiles.CLOCK, item, scroll = true)
        val bar = barPx()
        assertFalse("item $item: bar $bar covers the item", bar.overlaps(item))
        assertFalse("item $item: bar $bar covers the cell under it", bar.overlaps(underCell(item)))
    }
    @Test fun HOM_12_w48_onTheScrollSurfaceTheBarNeverCoversTheSelectedItemOrTheIconUnderIt() = scrollCase(itemBounds)
    @Test fun HOM_12_w48_onTheScrollSurfaceTheFirstRowFlipsTheBarBelowTheCellUnderIt() { scrollCase(Rect(300f, 60f, 480f, 240f)); assertTrue(barPx().top >= underCell(Rect(300f, 60f, 480f, 240f)).bottom - 1f) }
    @Test fun HOM_12_w48_onTheScrollSurfaceTheLastRowPutsTheBarAboveTheItem() { scrollCase(Rect(300f, 1400f, 480f, 1580f)); assertTrue(barPx().bottom <= 1400f + 1f) }

    @Test fun HOM_12_w48_theOldPlacementSatBelowTheItemOverTheIconUnderIt() {
        host(HomeTiles.CLOCK, itemBounds, scroll = false)
        assertTrue("without the scroll rule the bar is right under the item, over the cell under it", barPx().overlaps(underCell(itemBounds)))
    }

    @Test fun HOM_07_w48_aPresetThatWouldCoverANeighbourIsRefusedWithTheReasonAndNothingChanges() {
        val applied = mutableListOf<Pair<Int, Int>>()
        sheet(SheetKind.Widget, actions = ItemSheetActions(widgetSpan = 2 to 1, gridCols = 4, gridRows = 6, onWidgetSize = { c, r -> applied += c to r }, widgetBlocked = { c, r -> c == 2 && r == 2 }))
        rule.onNodeWithText("2 × 2").performClick(); rule.waitForIdle()
        assertTrue("blocked: nothing applied", applied.isEmpty())
        rule.onNodeWithText("Can't resize: another item is in the way").assertExists()
        rule.onNodeWithText("4 × 2").performClick(); rule.waitForIdle()
        assertEquals("a free preset applies and clears the note", listOf(4 to 2), applied)
        rule.onNodeWithTag(ItemSheetTags.PRESET_NOTE).assertDoesNotExist()
    }

    @Test fun HOM_07_w48_theCurrentSizeIsNeverAskedToBeBlocked() {
        val asked = mutableListOf<Pair<Int, Int>>()
        sheet(SheetKind.Widget, actions = ItemSheetActions(widgetSpan = 2 to 1, gridCols = 4, gridRows = 6, widgetBlocked = { c, r -> asked += c to r; true }))
        rule.onNodeWithText("2 × 1").performClick(); rule.waitForIdle()
        assertTrue(asked.isEmpty())
    }

    @Test fun HOM_12_bar_13_removeRemovesAndAnUndoSnackbarTakesItBack() {
        host(HomeTiles.CLOCK)
        rule.onNodeWithTag(ItemBarTags.REMOVE).performClick(); rule.waitForIdle()
        assertEquals(listOf("remove:${HomeTiles.CLOCK}"), log)
        rule.onNodeWithTag(ItemBarTags.BAR).assertDoesNotExist()
        rule.onNodeWithTag(ItemBarTags.SNACK).assertIsDisplayed()
        assertTrue(rule.onNodeWithTag(ItemBarTags.UNDO).getUnclippedBoundsInRoot().let { it.bottom - it.top } >= 48.dp)
        rule.onNodeWithTag(ItemBarTags.UNDO).performClick(); rule.waitForIdle()
        assertEquals("undo", log.last())
        rule.onNodeWithTag(ItemBarTags.SNACK).assertDoesNotExist()
    }

    @Test fun HOM_12_bar_14_theButtonsAreLabelledForTheScreenReaderAndNothingShowsWithoutASelection() {
        val sel = host(HomeTiles.CLOCK)
        fun desc(t: String) = rule.onNodeWithTag(t).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        assertEquals("Remove Clock", desc(ItemBarTags.REMOVE)); assertEquals("Settings for Clock", desc(ItemBarTags.SETTINGS))
        rule.runOnUiThread { sel.clear() }; rule.waitForIdle()
        rule.onNodeWithTag(ItemBarTags.BAR).assertDoesNotExist()
    }

    @Test fun HOM_12_bar_15_everyItemNodeCarriesOpenItemSettingsInEditModeOnly() {
        var opened: String? = null
        val on = itemSettingsActions({ opened = it }, "k", editing = true)
        assertEquals(listOf("Open item settings"), on.map { it.label })
        on.single().action()
        assertEquals("k", opened)
        assertTrue(itemSettingsActions({ }, "k", editing = false).isEmpty())
        assertTrue(itemSettingsActions(null, "k", editing = true).isEmpty())
    }

    // ---- the sheet ----------------------------------------------------------------------------------------------------------

    private fun sheet(kind: SheetKind, clock: ClockTileOptions = ClockTileOptions(), caps: SheetCaps = SheetCaps(), actions: ItemSheetActions = ItemSheetActions(), onDismiss: () -> Unit = { log += "dismiss" }) {
        rule.setContent { ItemSheet(kind, "Title", clock, caps, actions, onDismiss) }
        rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
    }

    @Test fun HOM_07_w47_aSizePresetOutsideTheProviderLimitsIsClampedAndTheSheetSaysSo() {
        val applied = mutableListOf<Pair<Int, Int>>()
        val limits = com.bos.sphere.core.catalog.CellLimits(minCols = 1, maxCols = 2, minRows = 1, maxRows = 2, horizontal = true, vertical = true)
        sheet(SheetKind.Widget, caps = SheetCaps(), actions = ItemSheetActions(widgetSpan = 2 to 1, gridCols = 4, gridRows = 6, widgetLimits = limits, onWidgetSize = { c, r -> applied += c to r }))
        rule.onNodeWithTag(ItemSheetTags.PRESET_NOTE).assertDoesNotExist()
        rule.onNodeWithText("2 × 2").performClick(); rule.waitForIdle()
        assertEquals("inside the limits: applied as asked", listOf(2 to 2), applied)
        rule.onNodeWithTag(ItemSheetTags.PRESET_NOTE).assertDoesNotExist()
        rule.onNodeWithText("4 × 2").performClick(); rule.waitForIdle()
        assertEquals("outside: clamped to the provider's maximum, never past it", listOf(2 to 2, 2 to 2), applied)
        rule.onNodeWithTag(ItemSheetTags.PRESET_NOTE).assertExists()
        rule.onNodeWithText("Not available for this widget").assertExists()
    }

    @Test fun HOM_07_w47_aPresetThatClampsToTheCurrentSizeChangesNothing() {
        val applied = mutableListOf<Pair<Int, Int>>()
        val limits = com.bos.sphere.core.catalog.CellLimits(minCols = 2, maxCols = 2, minRows = 1, maxRows = 1, horizontal = false, vertical = false)
        sheet(SheetKind.Widget, actions = ItemSheetActions(widgetSpan = 2 to 1, gridCols = 4, gridRows = 6, widgetLimits = limits, onWidgetSize = { c, r -> applied += c to r }))
        rule.onNodeWithText("4 × 2").performClick(); rule.waitForIdle()
        assertTrue("a widget that cannot resize stays as it is", applied.isEmpty())
        rule.onNodeWithTag(ItemSheetTags.PRESET_NOTE).assertExists()
    }

    @Test fun HOM_12_sheet_10_theClockSheetChangesTheTile() {
        var clock by mutableStateOf(ClockTileOptions())
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF0A0B12))) { ClockTile(96.dp, Modifier.size(96.dp), clock) }
            ItemSheet(SheetKind.Clock, "Clock tile", clock, SheetCaps(), ItemSheetActions(onClock = { clock = it }), {})
        }
        rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        rule.onNodeWithTag("clock-tile-digital").assertExists()
        rule.onNodeWithTag(ItemSheetTags.row(SheetOption.ClockFormat)).assertExists()
        rule.onNodeWithText("Analog").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("clock-tile-analog").assertExists()
        assertTrue(clock.analog)
        rule.onNodeWithTag(ItemSheetTags.row(SheetOption.ClockFormat)).assertDoesNotExist() // hours are for the digital face
        rule.onNodeWithTag(ItemSheetTags.row(SheetOption.ClockDate)).performClick(); rule.waitForIdle()
        assertFalse(clock.date)
        rule.onNodeWithText("Digital").performClick(); rule.waitForIdle()
        rule.onNodeWithText("12-hour").performClick(); rule.waitForIdle()
        assertEquals(ClockFormat.Hour12, clock.format)
        assertEquals(false, clock.analog)
    }

    @Test fun HOM_12_sheet_11_anAppSheetListsItsRowsAndTheyRun() {
        sheet(SheetKind.App, actions = ItemSheetActions(onAppInfo = { log += "info" }, onUninstall = { log += "uninstall" }, onToggleHidden = { log += "hide" }))
        for (o in listOf(SheetOption.OpenApp, SheetOption.AppInfo, SheetOption.ChangeIcon, SheetOption.HideApp, SheetOption.Uninstall)) {
            val b = rule.onNodeWithTag(ItemSheetTags.row(o)).getUnclippedBoundsInRoot()
            assertTrue("$o is a 56 dp row", b.bottom - b.top >= 56.dp)
        }
        rule.onNodeWithTag(ItemSheetTags.row(SheetOption.AppInfo)).performClick(); rule.waitForIdle()
        assertEquals("info", log.first())
        assertEquals("the sheet closes after the action", "dismiss", log.last())
    }

    @Test fun HOM_12_sheet_12_aFolderAndAWidgetSheetHaveTheirOwnRows() {
        sheet(SheetKind.Folder, actions = ItemSheetActions(onDissolve = { log += "dissolve" }))
        rule.onNodeWithTag(ItemSheetTags.row(SheetOption.FolderRename)).assertExists()
        rule.onNodeWithTag(ItemSheetTags.row(SheetOption.FolderDissolve)).performClick(); rule.waitForIdle()
        assertEquals("dissolve", log.first())
    }

    @Test fun HOM_12_sheet_13_aWidgetSheetHasSizePresetsThatResize() {
        sheet(SheetKind.Widget, caps = SheetCaps(canReconfigure = true), actions = ItemSheetActions(widgetSpan = 2 to 1, gridCols = 4, gridRows = 6, onWidgetSize = { c, r -> log += "size:$c:$r" }, onReconfigure = { log += "reconf" }))
        rule.onNodeWithTag(ItemSheetTags.row(SheetOption.WidgetReconfigure)).assertExists()
        rule.onNodeWithText("4 × 2").performClick(); rule.waitForIdle()
        assertEquals(listOf("size:4:2"), log)
    }

    @Test fun HOM_12_sheet_14_hasAHeadingAGrabberAndAPartialHeightSoThePageStaysVisible() {
        sheet(SheetKind.App)
        assertTrue(rule.onNodeWithText("Title").fetchSemanticsNode().config.contains(SemanticsProperties.Heading))
        rule.onNodeWithTag(OneUiMenuTags.GRABBER, useUnmergedTree = true).assertExists()
        val top = rule.onNodeWithTag(OneUiMenuTags.SHEET).getUnclippedBoundsInRoot().top
        assertTrue("the sheet leaves the upper part of the screen free (top at $top)", top > 300.dp)
    }

    @Test fun HOM_12_sheet_15_escapeClosesTheSheet() {
        sheet(SheetKind.App)
        rule.onNodeWithTag(ItemSheetTags.SHEET).performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }
        rule.waitForIdle()
        assertEquals(listOf("dismiss"), log)
    }

    // ---- screenshots (read by a person) -------------------------------------------------------------------------------------

    @Test fun HOM_12_shot_01_clockTileSelectedWithTheBarAndTheSheetOpen() {
        var clock by mutableStateOf(ClockTileOptions())
        val sel = EditSelectionState()
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF0A0B12))) {
                for (i in 0 until 4) Box(Modifier.offset((24 + i * 92).dp, 90.dp).size(64.dp).background(Color(0x33FFFFFF), RoundedCornerShape(14.dp)))
                Box(Modifier.offset(116.dp, 200.dp)) { ClockTile(72.dp, Modifier.size(72.dp), clock) }
                ItemActionLayer(sel, null, {}, null, {}, {}, { "Clock" }, reserveBottom = 120.dp)
            }
            ItemSheet(SheetKind.Clock, "Clock tile", clock, SheetCaps(), ItemSheetActions(onClock = { clock = it }), {})
        }
        rule.runOnUiThread { sel.select(HomeTiles.CLOCK); sel.report(HomeTiles.CLOCK, Rect(116f * px, 200f * px, 188f * px, 272f * px)) }
        shoot("w45-clock-tile-bar-and-sheet-dark")
    }

    @Test fun HOM_12_shot_02_anAppIconSheet() {
        sheet(SheetKind.App)
        shoot("w45-app-sheet-dark")
    }

    @Test fun HOM_12_shot_03_aFolderSheet() {
        sheet(SheetKind.Folder)
        shoot("w45-folder-sheet-dark")
    }
}
