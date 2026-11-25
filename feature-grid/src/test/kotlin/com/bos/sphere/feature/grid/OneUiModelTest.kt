package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CFG-21 (wave 40): the pure model of the One UI style settings: grouping, accents, summaries, cards, the collapsing title, navigation. */
class OneUiModelTest {
    private fun cat(title: String, vararg items: SettingItem) = SettingCategory(title, Glyphs.Apps, items.toList())
    private fun toggle(t: String) = SettingItem.Toggle(t, Glyphs.Apps, true) {}
    private fun action(t: String) = SettingItem.Action(t, Glyphs.Apps, "") {}
    private val real = listOf("Pages", "Home screen", "Profiles", "Look", "Corners", "Gestures", "App drawer", "Hidden apps", "Radar", "Side panel", "System & sound", "Backup", "Desktop & dock", "Capabilities", "Lab", "About")
        .map { cat(it, toggle("x")) }

    @Test fun CFG_21_everyCategoryIsInExactlyOneCardOfTheRootList() {
        val g = OneUiModel.groups(real)
        assertEquals(real.indices.toList(), g.flatten().sorted())
    }

    @Test fun CFG_21_theCardsFollowOneUiOrderLookAndHomeThenInputThenCommsThenSystem() {
        val g = OneUiModel.groups(real).map { card -> card.map { real[it].title } }
        // wave 52: Pages, Home screen and the top-level Workspaces row first; the panel is "Side panel"
        assertEquals(listOf("Pages", "Home screen", "Profiles"), g[0])
        assertEquals(listOf("Look", "App drawer", "Desktop & dock"), g[1])
        assertEquals(listOf("Corners", "Gestures", "Radar"), g[2])
        assertEquals(listOf("Side panel"), g[3])
        assertEquals(listOf("System & sound", "Hidden apps", "Backup", "Capabilities"), g[4])
        assertEquals(listOf("Lab", "About"), g[5])
    }

    @Test fun CFG_21_aCategoryThatIsNotListedGetsACardBeforeTheLastOne() {
        val cats = listOf(cat("Look", toggle("x")), cat("Brand new", toggle("x")), cat("About", toggle("x")))
        val g = OneUiModel.groups(cats)
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), g)
    }

    @Test fun CFG_21_emptyGroupsAreSkippedSoNoCardIsEmpty() {
        val g = OneUiModel.groups(listOf(cat("Side panel", toggle("x"))))
        assertEquals(listOf(listOf(0)), g)
    }

    @Test fun CFG_21_everyKnownCategoryHasItsOwnMutedAccent() {
        val titles = real.map { it.title }.filter { it != "Pages" }
        val colours = titles.map { OneUiModel.accent(it) }
        assertEquals("one accent per category", titles.size, colours.toSet().size)
        assertTrue("opaque", colours.all { (it shr 24) and 0xFF == 0xFFL })
        assertEquals(OneUiModel.accent("Home screen"), OneUiModel.accent("Pages"))
    }

    @Test fun CFG_21_theHiddenAppsTabTitleWithItsCountIsPlainHiddenAppsOnTheList() {
        assertEquals("Hidden apps", OneUiModel.displayTitle("Hidden apps · 3"))
        assertEquals("Hidden apps", OneUiModel.key("Hidden apps · 3"))
        assertEquals("only a known category loses its suffix", "Brand new · 3", OneUiModel.displayTitle("Brand new · 3"))
        assertEquals(listOf(listOf(0)), OneUiModel.groups(listOf(cat("Hidden apps · 3", toggle("x")))).let { listOf(it.flatten()) })
    }

    @Test fun CFG_21_thePagesCategoryIsRecognisedByItsNameAndTheOldPageNumberTitleIsGone() {
        assertEquals(OneUiModel.PAGE, OneUiModel.key("Pages"))
        assertNull("wave 52: no row is called Page 1 any more", OneUiModel.key("Page 7"))
        assertEquals(OneUiModel.SIDE_PANEL, OneUiModel.key("Side panel"))
        assertNull("Comms is a section inside the panel, not a settings category", OneUiModel.key("Comms"))
        assertNull(OneUiModel.key("Nothing like it"))
    }

    @Test fun CFG_21_theSummaryFallsBackToTheFirstValuesOrTheCountOfSettings() {
        val c = cat("Look", SettingItem.Choice("Theme", Glyphs.Apps, listOf("Dark", "Light"), "Dark", { it }) {}, SettingItem.Toggle("Plate", Glyphs.Apps, false) {}, action("Go"))
        assertEquals("Dark · Off", OneUiModel.fallbackSummary(c))
        assertEquals("1 settings", OneUiModel.fallbackSummary(cat("X", action("a"))))
        assertEquals("given wins", "Dark · Rounded icons", OneUiModel.summary(c, mapOf("Look" to "Dark · Rounded icons")))
        assertEquals("a blank given one is ignored", "Dark · Off", OneUiModel.summary(c, mapOf("Look" to " ")))
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

    @Test fun CFG_21_theSectionTableNamesRowsAndHeadersInTheOrderTheyAppear() {
        val items = listOf(toggle("Light / dark"), toggle("Icon shape"), toggle("Settings backdrop"))
        assertEquals(mapOf(0 to "Theme", 1 to "Icons", 2 to "Settings screen"), OneUiSections.headers("Look", items))
        assertEquals("an unknown category has none", emptyMap<Int, String>(), OneUiSections.headers("Brand new", items))
        assertEquals("a row that is not there starts none", emptyMap<Int, String>(), OneUiSections.headers("Look", listOf(toggle("zzz"))))
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

    @Test fun CFG_21_backStepsFromThePageOfARowToTheCategoryToTheRootThenCloses() {
        var n: OneUiModel.Nav? = OneUiModel.Nav().open(3).openSub(5)
        assertEquals(2, n!!.level)
        n = n.back(false); assertEquals(OneUiModel.Nav(3, -1), n)
        n = n!!.back(false); assertEquals(OneUiModel.Nav(-1, -1), n)
        assertNull(n!!.back(false))
    }

    @Test fun CFG_21_inTwoPanesBackLeavesTheCategoryOnlyByClosing() {
        assertEquals(OneUiModel.Nav(2, -1), OneUiModel.Nav(2, 4).back(true))
        assertNull(OneUiModel.Nav(2, -1).back(true))
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
