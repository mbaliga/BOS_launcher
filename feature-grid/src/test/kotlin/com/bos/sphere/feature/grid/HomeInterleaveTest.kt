package com.bos.sphere.feature.grid

import com.bos.sphere.core.catalog.ItemKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeInterleaveTest {
    private val folder = ItemKeys.folder("f1")
    private val link = ItemKeys.link("https://x.org")

    private fun keys(r: List<HomeRow<String>>) = r.map { it.key }

    @Test
    fun HOM_01_extrasSitInFrontOfTheAppThatFollowsThemInThePageOrder() {
        val rows = HomeInterleave.merge(listOf("a", "b", "c", "d"), { it }, listOf(folder, link), topLevel = listOf("a", folder, "b", "c", link, "d"))
        assertEquals(listOf("a", folder, "b", "c", link, "d"), keys(rows))
    }

    @Test
    fun HOM_01_extrasWithNoAppAfterThemGoToTheEndInOrder() {
        val rows = HomeInterleave.merge(listOf("a", "b"), { it }, listOf(folder, link), topLevel = listOf("a", "b", folder, link))
        assertEquals(listOf("a", "b", folder, link), keys(rows))
        // Extras missing from the page order still show.
        assertEquals(listOf("a", "b", folder), keys(HomeInterleave.merge(listOf("a", "b"), { it }, listOf(folder), emptyList())))
    }

    @Test
    fun HOM_01_everyAppAndEveryExtraAppearsExactlyOnce() {
        val apps = (1..9).map { "app$it" }
        val top = listOf(link, "app3", folder, "app1", "app9", "app2")
        val rows = keys(HomeInterleave.merge(apps, { it }, listOf(link, folder), top))
        assertEquals((apps + link + folder).sorted(), rows.sorted())
        assertEquals(rows.size, rows.toSet().size)
    }

    @Test
    fun HOM_01_listSortsExtrasIntoTheirLetterAmongTheApps() {
        val labels = mapOf(folder to "Games", link to "Zoo site")
        val rows = HomeInterleave.alphabetical(listOf("Camera", "Gmail", "Maps"), { it }, { it }, listOf(folder, link)) { labels.getValue(it) }
        assertEquals(listOf("Camera", folder, "Gmail", "Maps", link), keys(rows))
    }

    @Test
    fun HOM_01_widgetsAreNotInterleaved() {
        val w = ItemKeys.widget(4)
        assertEquals(listOf(folder), HomeInterleave.extraKeys(listOf("a", w, folder, folder)) { it == w || it == folder })
    }
}
