package com.bos.sphere.feature.grid

import com.bos.sphere.core.catalog.HomeEdit
import com.bos.sphere.core.catalog.HomeState
import com.bos.sphere.core.catalog.ItemDef
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind
import com.bos.sphere.core.catalog.WidgetSpan
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.input.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSurfaceContractTest {
    private val cap: (com.bos.sphere.core.data.PageLayout) -> Int = { 100 }

    private fun sample(): HomeState {
        var s = HomeState(pages = listOf(HomePage((1..14).map { "app$it" }, id = 1L)))
        s = HomeEdit.add(s, ItemDef(ItemKeys.widget(1), ItemKind.Widget, "W"), 0, cap, WidgetSpan(2, 2))
        s = HomeEdit.createFolder(s, listOf("app1", "app2"), "f", "Folder", cap)
        return s
    }

    private fun req(s: HomeState, w: Int = 800, h: Int = 400, input: InputMode = InputMode.Touch) = SurfaceRequest(s, w, h, input)

    @Test
    fun HOM_01_everySurfaceAccountsForEveryItemAndSwitchingKeepsData() {
        val s = sample()
        val expected = s.topLevelKeys().toSet()
        for (style in HomeStyle.entries) {
            val plan = HomeSurfaceContract.forStyle(style).plan(req(s))
            assertEquals("style $style", expected, plan.allKeys())
            assertEquals(style, plan.style)
        }
        // Planning never edits the shared state.
        val before = s
        HomeStyle.entries.forEach { HomeSurfaceContract.forStyle(it).plan(req(s)) }
        assertEquals(before, s)
    }

    @Test
    fun HOM_01_widgetsShareGridLayoutButLiveInTheBandElsewhere() {
        val s = sample()
        val w = ItemKeys.widget(1)
        assertTrue(GridSurface.plan(req(s, 1200, 800)).cells.any { it.key == w && it.colSpan == 2 })
        for (style in listOf(HomeStyle.List, HomeStyle.Psp, HomeStyle.Dsi)) {
            val p = HomeSurfaceContract.forStyle(style).plan(req(s))
            assertTrue(w in p.bandWidgets)
            assertFalse(p.cells.any { it.key == w })
        }
    }

    @Test
    fun HOM_01_everyItemIsReachableByFocusFromTheFirst() {
        val s = sample()
        for (style in HomeStyle.entries) {
            val p = HomeSurfaceContract.forStyle(style).plan(req(s, 900, 500, InputMode.Keyboard))
            assertTrue(p.focusVisible)
            val graph = p.focus.associateBy { it.key }
            val seen = HashSet<String>()
            val queue = ArrayDeque(listOfNotNull(p.cells.firstOrNull()?.key))
            while (queue.isNotEmpty()) {
                val k = queue.removeFirst()
                if (seen.add(k)) graph[k]!!.to.values.forEach { queue.add(it) }
            }
            assertEquals("style $style", p.cells.map { it.key }.toSet(), seen)
        }
        assertFalse(GridSurface.plan(req(s)).focusVisible)
    }

    @Test
    fun HOM_01_itemActionsGiveTheSameResultOnEverySurface() {
        val s = sample()
        val actions = listOf(
            ItemAction.Move("app5", 0, 0), ItemAction.Remove("app6"),
            ItemAction.FileInto("app7", "app8", "g", "G"), ItemAction.Resize(ItemKeys.widget(1), 1, 0, WidgetSpan(4, 4)),
        )
        val results = HomeStyle.entries.map { st -> actions.fold(s) { acc, a -> HomeSurfaceContract.forStyle(st).accept(a, acc, cap) } }
        assertTrue(results.all { it == results[0] })
        assertEquals(WidgetSpan(3, 2), results[0].widgets[ItemKeys.widget(1)])
        assertEquals(listOf("app8", "app7"), results[0].folders["g"]!!.keys)
        assertEquals(s, GridSurface.accept(ItemAction.Open("app3"), s))
    }

    @Test
    fun HOM_08_bandNeverOverlapsTheTileAreaAtAnyWindowSize() {
        for ((w, h) in listOf(360 to 640, 640 to 360, 800 to 1280, 1280 to 800, 1920 to 1080)) {
            val l = widgetBandLayout(w, h, 3)
            assertFalse(l.tiles.intersects(l.band!!))
            l.widgets.forEach { assertFalse(l.tiles.intersects(it)) }
        }
    }

    @Test
    fun HOM_11_gridGrowsWithTheWindow() {
        val s = HomeState(pages = listOf(HomePage((1..60).map { "a$it" }, id = 1L)))
        val small = GridSurface.plan(req(s, 360, 640))
        val big = GridSurface.plan(req(s, 1280, 800))
        assertTrue(big.cells.size > small.cells.size)
        assertEquals(s.topLevelKeys().toSet(), (big.cells.map { it.key } + big.overflow).toSet())
    }
}
