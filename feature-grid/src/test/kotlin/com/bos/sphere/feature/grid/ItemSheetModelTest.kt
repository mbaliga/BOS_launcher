package com.bos.sphere.feature.grid

import androidx.compose.ui.geometry.Rect
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.data.ClockFormat
import com.bos.sphere.core.data.HomeTiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 45 (editSheet), HOM-07 / HOM-12: the pure part of the item action bar and the item sheet. */
class ItemSheetModelTest {
    private val area = Rect(0f, 100f, 1000f, 1800f)
    private val bar = 600f to 144f

    private fun place(item: Rect, avoid: List<Rect> = emptyList(), a: Rect = area) =
        ActionBarMath.place(item, bar.first, bar.second, a, avoid, gap = 24f, handleReach = 90f)

    @Test fun HOM_12_bar_01_isBelowTheItemWhenThereIsRoom() {
        val item = Rect(400f, 300f, 600f, 500f)
        val p = place(item)
        assertTrue(p.below)
        assertEquals(item.bottom + 90f + 24f, p.y, 0.01f) // past the handles that stick out of the item
        assertEquals(item.center.x - 300f, p.x, 0.01f)
    }

    @Test fun HOM_12_bar_02_isAboveTheItemWhenTheDockLeavesNoRoomBelow() {
        val item = Rect(400f, 1400f, 600f, 1600f)
        val dock = Rect(0f, 1650f, 1000f, 1800f)
        val p = place(item, listOf(dock))
        assertFalse(p.below)
        assertTrue(p.y + bar.second <= item.top - 90f)
    }

    @Test fun HOM_12_bar_03_isClampedInsideTheAreaAtBothEdges() {
        val left = place(Rect(0f, 300f, 100f, 400f))
        assertEquals(0f, left.x, 0.01f)
        val right = place(Rect(900f, 300f, 1000f, 400f))
        assertEquals(1000f - 600f, right.x, 0.01f)
    }

    @Test fun HOM_12_bar_04_neverOverlapsTheHandlesTheDockOrLeavesTheArea() {
        val dock = Rect(0f, 1650f, 1000f, 1800f)
        for (top in listOf(110f, 400f, 900f, 1300f, 1500f)) {
            val item = Rect(300f, top, 700f, top + 200f)
            val p = place(item, listOf(dock), Rect(0f, 100f, 1000f, 1650f))
            val r = Rect(p.x, p.y, p.x + bar.first, p.y + bar.second)
            val handles = Rect(item.left - 90f, item.top - 90f, item.right + 90f, item.bottom + 90f)
            assertFalse("top=$top: bar over the handles", r.overlaps(handles))
            assertTrue("top=$top: inside the area", r.top >= 100f && r.bottom <= 1650f && r.left >= 0f && r.right <= 1000f)
        }
    }

    @Test fun HOM_12_bar_05_aTallItemThatFillsThePageGetsABarInsideTheAreaNotOffScreen() {
        val p = place(Rect(0f, 100f, 1000f, 1800f))
        assertTrue(p.y >= 100f && p.y + bar.second <= 1800f)
    }

    @Test fun HOM_12_sheet_01_theClockTileHasStyleHoursAndDateAndHoursOnlyForTheDigitalFace() {
        assertEquals(listOf(SheetOption.ClockStyle, SheetOption.ClockFormat, SheetOption.ClockDate), ItemSheetModel.options(SheetKind.Clock, ClockTileOptions(analog = false)))
        assertEquals(listOf(SheetOption.ClockStyle, SheetOption.ClockDate), ItemSheetModel.options(SheetKind.Clock, ClockTileOptions(analog = true)))
    }

    @Test fun HOM_12_sheet_02_anAppHasOpenInfoIconHideAndUninstall() {
        assertEquals(listOf(SheetOption.OpenApp, SheetOption.AppInfo, SheetOption.ChangeIcon, SheetOption.HideApp, SheetOption.Uninstall), ItemSheetModel.options(SheetKind.App))
        assertFalse(SheetOption.Uninstall in ItemSheetModel.options(SheetKind.App, caps = SheetCaps(canUninstall = false)))
    }

    @Test fun HOM_12_sheet_03_aFolderHasOpenRenameAndRemoveKeepingApps() {
        assertEquals(listOf(SheetOption.FolderOpen, SheetOption.FolderRename, SheetOption.FolderDissolve), ItemSheetModel.options(SheetKind.Folder))
    }

    @Test fun HOM_12_sheet_04_aWidgetOffersReconfigureOnlyWhenTheProviderHasAConfigureActivity() {
        assertEquals(listOf(SheetOption.WidgetSize), ItemSheetModel.options(SheetKind.Widget))
        assertEquals(listOf(SheetOption.WidgetReconfigure, SheetOption.WidgetSize), ItemSheetModel.options(SheetKind.Widget, caps = SheetCaps(canReconfigure = true)))
    }

    @Test fun HOM_12_sheet_05_kindsComeFromTheKeysAndOnlyWidgetsResize() {
        assertEquals(SheetKind.Clock, ItemSheetModel.kindOf(HomeTiles.CLOCK))
        assertEquals(SheetKind.SettingsTile, ItemSheetModel.kindOf(HomeTiles.SETTINGS))
        assertEquals(SheetKind.Widget, ItemSheetModel.kindOf(ItemKeys.widget(7)))
        assertEquals(SheetKind.App, ItemSheetModel.kindOf("com.example/Main#0"))
        assertTrue(ItemSheetModel.canResize(SheetKind.Widget))
        SheetKind.entries.filter { it != SheetKind.Widget }.forEach { assertFalse(it.name, ItemSheetModel.canResize(it)) }
    }

    @Test fun HOM_12_sheet_06_widgetSizePresetsAreClippedToTheGrid() {
        assertEquals(listOf(2 to 1, 2 to 2, 4 to 2), WidgetSizePresets.forGrid(4, 6))
        assertEquals(listOf(2 to 1, 2 to 2), WidgetSizePresets.forGrid(3, 6))
        assertEquals(listOf(1 to 1), WidgetSizePresets.forGrid(1, 1))
        assertEquals("2 × 2", WidgetSizePresets.label(2 to 2))
    }

    @Test fun HOM_12_sheet_07_everyOptionOfEveryKindIsListedOnce() {
        for (k in SheetKind.entries) {
            val o = ItemSheetModel.options(k, caps = SheetCaps(canReconfigure = true))
            assertEquals(k.name, o.distinct(), o)
            assertTrue(k.name, o.isNotEmpty())
        }
        assertEquals(ClockFormat.System, ClockTileOptions().format)
        assertTrue("the tile keeps the day line it always had", ClockTileOptions().date)
    }
}
