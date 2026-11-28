package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-22 (wave 28, homeUnify, second review): a settings file from before the Pages setting must not hide the pages it brings with it. */
class HomePagesImportW28Test {
    private val twoPages = HomeLayout(pages = listOf(HomePage(keys = listOf("a", "b"), id = 1L), HomePage(keys = listOf("c"), id = 2L)), seeded = true)

    /** What an older build exported: the layout, and settings that carry the old Scroll / Paginated keys instead of homePages. */
    private fun oldFile(layout: HomeLayout, vararg old: Pair<String, Any>): StateDocument {
        val doc = StateCodec.export(StateParts(prefs = LauncherPrefs(), layout = layout), 1L)
        val prefs = doc.json.getJSONObject(StateSchema.K_SETTINGS).getJSONObject("prefs")
        prefs.remove("homePages"); old.forEach { (k, v) -> prefs.put(k, v) }
        return StateDocument.parse(doc.encode())
    }

    @Test fun HOM_22_w28_anOldFileWithPagesAndNoChoiceImportsAsPaginatedSoNoPageIsHidden() {
        val d = StateCodec.decode(oldFile(twoPages))
        assertEquals(HomePages.Paginated, d.parts.prefs.homePages)
        assertEquals(2, d.parts.layout.pages.size)
    }

    @Test fun HOM_22_w28_anOldFileWithAnEmptyHomeAndNoChoiceImportsAsOff() {
        val d = StateCodec.decode(oldFile(HomeLayout(pages = listOf(HomePage(keys = emptyList(), id = 1L)), seeded = true)))
        assertEquals(HomePages.Off, d.parts.prefs.homePages)
    }

    @Test fun HOM_22_w28_anOldFileThatChoseScrollOrPaginatedKeepsThatChoice() {
        assertEquals(HomePages.Continuous, StateCodec.decode(oldFile(twoPages, "homeScroll" to "Continuous", "pagesModeChosen" to true)).parts.prefs.homePages)
        assertEquals(HomePages.Paginated, StateCodec.decode(oldFile(twoPages, "homeScroll" to "Paged", "pagesModeChosen" to true)).parts.prefs.homePages)
    }

    @Test fun HOM_22_w28_aNewFileKeepsItsOwnPagesValueEvenWithNoPages() {
        val doc = StateCodec.export(StateParts(prefs = LauncherPrefs(homePages = HomePages.Off), layout = twoPages), 1L)
        assertEquals(HomePages.Off, StateCodec.decode(StateDocument.parse(doc.encode())).parts.prefs.homePages)
    }

    @Test fun HOM_22_w28_theRetiredScrollKeysAreNeitherKeptAsUnknownFieldsNorWrittenBackOnExport() {
        val d = StateCodec.decode(oldFile(twoPages, "homeScroll" to "Continuous", "pagesModeChosen" to true))
        val again = StateCodec.export(d.parts, 2L).json.getJSONObject(StateSchema.K_SETTINGS).getJSONObject("prefs")
        assertFalse(again.has("homeScroll")); assertFalse(again.has("pagesModeChosen")); assertTrue(again.has("homePages"))
    }
}
