package com.bos.sphere.feature.grid

import com.bos.sphere.core.catalog.HomeState
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.WidgetSpan
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.PageLayout
import com.bos.sphere.core.input.FocusDir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardEditOpsTest {
    private val cap: (PageLayout) -> Int = { 12 }
    private val widget = ItemKeys.widget(7)
    private fun state(vararg keys: String) = HomeState(pages = listOf(HomePage(keys.toList())), widgets = mapOf(widget to WidgetSpan(2, 2)))
    private fun plan(s: HomeState) = GridSurface.plan(SurfaceRequest(s, 400, 800, keys = s.pages.flatMap { it.keys }))

    @Test fun INP_09_arrow_moves_an_item_one_place_and_the_result_applies_like_a_drag() {
        val s = state("a", "b", "c")
        val act = KeyboardEditOps.resolve(s, plan(s), "a", FocusDir.Right, shift = false, columns = 4)
        assertEquals(ItemAction.Move("a", 0, 1), act)
        val next = SharedItemActions.apply(act!!, s, cap)
        assertEquals(listOf("b", "a", "c"), next.pages[0].keys)
    }

    @Test fun INP_09_shift_arrow_on_an_app_files_it_into_its_neighbour_by_keyboard_alone() {
        val s = state("a", "b", "c")
        val act = KeyboardEditOps.resolve(s, plan(s), "a", FocusDir.Right, shift = true, columns = 4)
        assertTrue(act is ItemAction.FileInto && act.targetKey == "b")
        val next = SharedItemActions.apply(act!!, s, cap)
        assertEquals(1, next.folders.size)
        assertEquals(setOf("a", "b"), next.folders.values.single().keys.toSet())
        assertTrue("a" !in next.pages[0].keys && "b" !in next.pages[0].keys)
    }

    @Test fun INP_09_shift_arrow_onto_a_folder_files_into_that_folder() {
        val s0 = state("a", "b", "c")
        val withFolder = SharedItemActions.apply(ItemAction.FileInto("a", "b", "f1", "Folder"), s0, cap)
        val fk = withFolder.folders.values.single().key
        val a = KeyboardEditOps.resolve(withFolder, plan(withFolder), "c", FocusDir.Left, shift = true, columns = 4)
        assertEquals(fk, (a as ItemAction.FileInto).targetKey)
        val after = SharedItemActions.apply(a, withFolder, cap)
        assertEquals(setOf("a", "b", "c"), after.folders.values.single().keys.toSet())
    }

    @Test fun INP_09_shift_arrow_resizes_a_widget_and_never_files_it() {
        val s = state(widget, "a")
        val grow = KeyboardEditOps.resolve(s, plan(s), widget, FocusDir.Right, shift = true, columns = 4)
        assertEquals(ItemAction.Resize(widget, 1, 0), grow)
        val next = SharedItemActions.apply(grow!!, s, cap)
        assertEquals(WidgetSpan(3, 2), next.spanOf(widget))
        val shrink = KeyboardEditOps.resolve(s, plan(s), widget, FocusDir.Up, shift = true, columns = 4) as ItemAction.Resize
        assertEquals(WidgetSpan(2, 1), SharedItemActions.apply(shrink, s, cap).spanOf(widget))
    }

    @Test fun INP_09_nothing_to_file_into_at_the_edge_and_a_widget_is_never_a_target() {
        val s = state("a")
        assertNull(KeyboardEditOps.resolve(s, plan(s), "a", FocusDir.Left, shift = true, columns = 4))
        val s2 = state("a", widget)
        val p = plan(s2)
        val act = KeyboardEditOps.resolve(s2, p, "a", FocusDir.Right, shift = true, columns = 4)
        assertTrue(act == null || (act as ItemAction.FileInto).targetKey != widget)
    }
}
