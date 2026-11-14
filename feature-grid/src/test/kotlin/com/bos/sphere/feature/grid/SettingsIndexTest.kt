package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsIndexTest {
    private val g = Glyphs.Search
    private val cats = listOf(
        SettingCategory("Radar", g, listOf(
            SettingItem.Toggle("Keep moving", g, true, "Filmstrip and Orbit drift slowly") {},
            SettingItem.Toggle("Search the web", g, false) {},
        )),
        SettingCategory("Theme", g, listOf(
            SettingItem.Slider("Icon size", g, 0.5f, 0f..1f, "50%", hint = "How big the icons are") {},
            SettingItem.Action("Reset everything", g) {},
            SettingItem.Stepper("Columns", g, 4, 2..8) {},
        )),
    )
    private val index = SettingsIndex.fromCategories(cats)

    @Test
    fun CFG_05_everySettingIsAnEntry() {
        assertEquals(cats.sumOf { it.items.size }, index.entries.size)
        assertEquals(cats.flatMap { c -> c.items.map { it.title } }, index.entries.map { it.title })
        assertEquals("Theme", index.byId("1/0")!!.category)
    }

    @Test
    fun CFG_14_filtersByNameAndDescription() {
        assertEquals(listOf("Icon size"), index.search("icon").map { it.title })
        assertEquals(listOf("Keep moving"), index.search("drift").map { it.title }) // description only
        assertEquals(listOf("Search the web"), index.search("web").map { it.title })
        assertEquals(listOf("Icon size"), index.search("big icons").map { it.title }) // every word, anywhere
        assertTrue(index.search("zzz").isEmpty())
        assertTrue(index.search("  ").isEmpty())
        // Name hits beat description hits.
        val ranked = SettingsIndex.fromCategories(listOf(SettingCategory("X", g, listOf(
            SettingItem.Toggle("Quiet mode", g, false, "Also hides the clock") {},
            SettingItem.Toggle("Clock", g, false) {},
        ))))
        assertEquals(listOf("Clock", "Quiet mode"), ranked.search("clock").map { it.title })
        assertEquals(1, index.search("o", limit = 1).size)
    }

    @Test
    fun CFG_05_radarCanQueryTheSameIndex() {
        var opened: SettingEntry? = null
        val source: SettingsResultSource = CategorySettingsSource({ cats }, { opened = it })
        val hit = source.results("columns").single()
        source.open(hit)
        assertEquals("1/2", opened!!.id)
    }
}
