package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HomePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** W48 (editGaps), HOM-07: a size preset follows the "widgets block" rule of the handles. Pure JVM. */
class PresetFitW48Test {
    private val w = ItemKeys.widget(1)
    private val spans = mutableMapOf<String, WidgetSpan>()
    private fun span(k: String) = spans[k] ?: WidgetSpan(1, 1)
    private fun fit(keys: List<String>, c: Int, r: Int, cols: Int = 4, rows: Int = 6) = PresetFit.fit(keys, ::span, cols, rows, w, c, r)

    @Test fun HOM_07_w48_aPresetThatOnlyTakesFreeCellsFits() {
        spans[w] = WidgetSpan(2, 1)
        val r = fit(listOf(w), 2, 2) as PresetFit.Result.Fits
        assertEquals(CellRect(0, 0, 2, 2), r.rect); assertFalse(r.moveOrigin)
    }

    @Test fun HOM_07_w48_aPresetThatWouldCoverANeighbourIsBlockedAndNothingIsPushed() {
        spans[w] = WidgetSpan(2, 1)
        // the widget at (0,0) 2x1, an icon at (0,1) directly under it: 2 x 2 would cover the icon
        val keys = listOf(w, "app:a", "app:b", "app:c")
        // packer: w (0,0)-(2,1); a at (2,0); b at (3,0); c at (0,1)
        val r = fit(keys, 2, 2)
        assertTrue("blocked, not pushed: $r", r is PresetFit.Result.Blocked)
        assertEquals("the neighbour's key is named", "app:c", (r as PresetFit.Result.Blocked).by)
    }

    @Test fun HOM_07_w48_aBlockedPresetWouldHaveBeenAppliedByTheOldReflowPath() {
        // the old path (HomeEdit.resizeWidget) only clamps to the grid: it would have taken the 2 x 2 and pushed the icon aside
        spans[w] = WidgetSpan(2, 1)
        val st = HomeState(pages = listOf(HomePage(keys = listOf(w, "app:a", "app:b", "app:c"), id = 1L)), widgets = mapOf(w to WidgetSpan(2, 1)))
        assertEquals(WidgetSpan(2, 2), HomeEdit.resizeWidget(st, w, 0, 1, max = WidgetSpan(4, 6), gridCols = 4, gridRows = 6).widgets[w])
        assertTrue(fit(st.pages.single().keys, 2, 2) is PresetFit.Result.Blocked)
    }

    @Test fun HOM_07_w48_shrinkingIsAlwaysFree() {
        spans[w] = WidgetSpan(4, 2)
        val keys = listOf(w, "app:a")
        assertTrue(fit(keys, 2, 1) is PresetFit.Result.Fits)
    }

    @Test fun HOM_07_w48_aPresetAtTheRightEdgeShiftsTheOriginBackInsideTheGrid() {
        spans[w] = WidgetSpan(1, 1)
        // three icons then the widget lands at (3,0); a 2 x 1 would stick out at the right: it shifts to column 2, which an icon holds -> blocked
        val keys = listOf("app:a", "app:b", "app:c", w)
        assertTrue(fit(keys, 2, 1) is PresetFit.Result.Blocked)
        // with the row before it empty the shift is free: the widget at (3,0) of a page whose cell (2,0) is free cannot exist in a packed page, so use rows
        spans[w] = WidgetSpan(1, 2)
        val r = PresetFit.fit(listOf(w), ::span, 4, 6, w, 4, 2) as PresetFit.Result.Fits
        assertEquals(CellRect(0, 0, 4, 2), r.rect)
    }

    @Test fun HOM_07_w48_aKeyThatIsNotOnThePageIsUnknown() {
        assertEquals(PresetFit.Result.Unknown, fit(listOf("app:a"), 2, 2))
    }

    @Test fun HOM_07_w48_aPresetBiggerThanTheGridIsKeptInsideIt() {
        spans[w] = WidgetSpan(1, 1)
        val r = fit(listOf(w), 9, 9, cols = 4, rows = 6) as PresetFit.Result.Fits
        assertEquals(CellRect(0, 0, 4, 6), r.rect)
    }
}

/** W48 (editGaps), HOM-12: Cancel puts the extras back (folder contents, definitions, widget sizes, stacks); widgets added meanwhile are freed. Pure JVM. */
class EditExtrasRestoreW48Test {
    private val folder = Folder("f1", "Games", listOf("app:a", "app:b"))
    private val wA = ItemKeys.widget(7)
    private val entry = HomeState(
        pages = listOf(HomePage(keys = listOf(folder.key, wA), id = 1L)),
        folders = mapOf("f1" to folder), items = mapOf(wA to ItemDef(wA, ItemKind.Widget, "Weather", "x/.W")), widgets = mapOf(wA to WidgetSpan(2, 2)),
    )

    @Test fun HOM_12_w48_folderContentsComeBack() {
        val snap = EditExtrasEntry(entry)
        val now = entry.copy(folders = mapOf("f1" to folder.copy(keys = listOf("app:a"), name = "Renamed")))
        assertTrue(snap.changed(now))
        assertEquals(folder, snap.restore(now).state.folders["f1"])
    }

    @Test fun HOM_12_w48_widgetSizeAndDefinitionsComeBackAndAnAddedWidgetIsFreed() {
        val snap = EditExtrasEntry(entry)
        val added = ItemKeys.widget(9)
        val now = entry.copy(
            widgets = mapOf(wA to WidgetSpan(4, 1), added to WidgetSpan(2, 2)),
            items = entry.items + (added to ItemDef(added, ItemKind.Widget, "New", "x/.N")),
        )
        val r = snap.restore(now)
        assertEquals(WidgetSpan(2, 2), r.state.widgets[wA]); assertFalse(added in r.state.widgets); assertFalse(added in r.state.items)
        assertEquals(listOf(added), r.widgetKeysToFree)
    }

    @Test fun HOM_12_w48_pagesAndDockOfTheCurrentStateAreKept() {
        val snap = EditExtrasEntry(entry)
        val now = entry.copy(pages = listOf(HomePage(keys = listOf("app:z"), id = 5L)), dock = listOf("app:q"))
        val s = snap.restore(now).state
        assertEquals(now.pages, s.pages); assertEquals(now.dock, s.dock)
        assertFalse("nothing differs in the extras", snap.changed(now))
    }

    @Test fun HOM_12_w48_aStackAddedMeanwhileIsNotFreedAsAWidgetId() {
        val snap = EditExtrasEntry(entry)
        val stack = WidgetStacks.stackKey("s1")
        val now = entry.copy(items = entry.items + (stack to ItemDef(stack, ItemKind.Widget, "Stack", ";")))
        assertTrue(snap.restore(now).widgetKeysToFree.isEmpty())
    }
}
