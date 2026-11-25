package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CFG-21 (wave 40): the pure model of the One UI style settings: cards, the collapsing title, navigation (wave 57: the root grouping, accents and summaries went with the root list). */
class OneUiModelTest {
    private fun cat(title: String, vararg items: SettingItem) = SettingCategory(title, Glyphs.Apps, items.toList())
    private fun toggle(t: String) = SettingItem.Toggle(t, Glyphs.Apps, true) {}
    private fun action(t: String) = SettingItem.Action(t, Glyphs.Apps, "") {}

    @Test fun CFG_40_theSixTabsAndTheFinishSetupPageAreTheKnownCategories() {
        for (k in SettingsTabs.KEYS) assertEquals(k, OneUiModel.key(k))
        assertEquals(OneUiModel.FINISH_SETUP, OneUiModel.key("Finish setup"))
        assertEquals(OneUiModel.SIDE_PANEL, OneUiModel.key("Side panel"))
        assertNull("Comms is a section inside the panel, not a tab", OneUiModel.key("Comms"))
        assertNull("wave 57: the old category pages are gone", OneUiModel.key("Home screen"))
        assertNull(OneUiModel.key("Nothing like it"))
    }

    @Test fun CFG_21_controlsAndLinksSplitIntoSeparateCards() {
        val items = listOf(toggle("a"), toggle("b"), action("c"), action("d"), toggle("e"))
        val b = OneUiModel.blocks(items)
        assertEquals(listOf<OneUiModel.Block>(OneUiModel.Block.Group(listOf(0, 1)), OneUiModel.Block.Group(listOf(2, 3)), OneUiModel.Block.Group(listOf(4))), b)
    }

    @Test fun CFG_21_aSectionHeaderStartsANewCardUnderIt() {
        val items = listOf(toggle("a"), toggle("b"), toggle("c"))
        val b = OneUiModel.blocks(items, mapOf(0 to "First", 2 to "Second"))
        assertEquals(listOf(OneUiModel.Block.Header("First"), OneUiModel.Block.Group(listOf(0, 1)), OneUiModel.Block.Header("Second"), OneUiModel.Block.Group(listOf(2))), b)
        assertEquals(1, OneUiModel.blockOf(b, 1)); assertEquals(3, OneUiModel.blockOf(b, 2)); assertEquals(-1, OneUiModel.blockOf(b, 9))
    }

    @Test fun CFG_21_aRowOfPreviewCardsGetsACardOfItsOwn() {
        val rail = SettingItem.Choice("Style", Glyphs.Apps, listOf(1, 2), 1, { it.toString() }, preview = {}) {}
        val b = OneUiModel.blocks(listOf(toggle("a"), rail, toggle("b")))
        assertEquals(3, b.size)
        assertTrue(OneUiModel.isRail(rail)); assertFalse(OneUiModel.isRail(toggle("a")))
    }

    @Test fun CFG_40_theSectionHeadersAreTheOnesTheCategoryCarries() {
        val items = listOf(toggle("Light / dark"), toggle("Icon shape"), toggle("Settings backdrop"))
        assertEquals(mapOf(0 to "Theme", 1 to "Icons"), OneUiSections.headers(SettingCategory("Look", Glyphs.Apps, items, headers = mapOf(0 to "Theme", 1 to "Icons", 9 to "Gone"))))
        assertEquals("a category without headers has none", emptyMap<Int, String>(), OneUiSections.headers(cat("Brand new", *items.toTypedArray())))
    }

    @Test fun CFG_21_theTitleCollapsesWithTheScrollAndStaysCollapsedPastTheFirstItem() {
        assertEquals(0f, OneUiModel.collapse(0, 0, 400f, 100f), 0f)
        assertEquals(0.5f, OneUiModel.collapse(0, 150, 400f, 100f), 0.001f)
        assertEquals(1f, OneUiModel.collapse(0, 999, 400f, 100f), 0f)
        assertEquals(1f, OneUiModel.collapse(1, 0, 400f, 100f), 0f)
        assertEquals("small title hidden while the large one is whole", 0f, OneUiModel.smallTitleAlpha(0f), 0f)
        assertEquals("small title whole once collapsed", 1f, OneUiModel.smallTitleAlpha(1f), 0f)
        assertEquals(1f, OneUiModel.largeTitleAlpha(0f), 0f)
        assertEquals(0f, OneUiModel.largeTitleAlpha(1f), 0f)
        assertTrue("the two titles cross-fade, never both gone in the middle", OneUiModel.largeTitleAlpha(0.5f) + OneUiModel.smallTitleAlpha(0.5f) > 0.3f)
    }

    @Test fun CFG_21_theLargeTitleBandIsTheUpperThirdWithinBounds() {
        assertEquals(260f, OneUiModel.headerDp(2000f), 0f)
        assertEquals(172f, OneUiModel.headerDp(300f), 0f)
        assertEquals(297f.coerceAtMost(260f), OneUiModel.headerDp(891f), 0f)
    }

    @Test fun CFG_21_twoPaneNeedsAWideLandscapeWindow() {
        assertTrue(OneUiModel.twoPane(891f, 411f))
        assertFalse("portrait", OneUiModel.twoPane(411f, 891f))
        assertFalse("narrow landscape", OneUiModel.twoPane(560f, 320f))
        assertFalse("a wide but tall window", OneUiModel.twoPane(700f, 900f))
    }

    @Test fun CFG_41_backStepsFromThePageOfARowToTheTabThenCloses() {
        var n: SettingsTabs.Nav? = SettingsTabs.Nav(3).openSub(2, 5)
        assertTrue(n!!.subOpen)
        n = n.back(); assertEquals(SettingsTabs.Nav(3), n)
        assertNull("from a tab, Back closes Settings", n!!.back())
    }

    @Test fun CFG_21_searchRanksATitleStartAboveADescriptionHitAcrossCategories() {
        val cats = listOf(
            cat("Look", SettingItem.Toggle("Dark theme", Glyphs.Apps, true, "makes the icons dark") {}),
            cat("Radar", SettingItem.Toggle("Icons in results", Glyphs.Apps, true) {}),
            cat("Home screen", SettingItem.Toggle("Icon plate", Glyphs.Apps, true) {}),
        )
        val r = SettingsIndex.fromCategories(cats).search("icon")
                assertEquals("rows of three different categories", setOf("Look", "Radar", "Home screen"), r.map { it.category }.toSet())
        assertEquals("title starts come first", "Dark theme", r.last().title)
    }
}
