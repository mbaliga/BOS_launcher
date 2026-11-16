package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-12 page carousel logic, the empty default home (HOM-01) and the add carousel rules (HOM-04, HOM-05). */
class EditCardPagesTest {
    private val cap: (PageLayout) -> Int = { 4 }
    private fun page(id: Long, vararg keys: String) = HomePage(keys.toList(), PageLayout(), id)

    // ---- HOM-12 page delete / undo-able result / reorder
    @Test fun HOM_12_deleteMovesItemsToThePreviousPage() {
        val r = HomePageOps.delete(listOf(page(1, "a"), page(2, "b", "c"), page(3, "d")), 1, cap)
        assertEquals(listOf(listOf("a", "b", "c"), listOf("d")), r.pages.map { it.keys })
        assertTrue(r.taken.isEmpty()); assertEquals(0, r.focus)
    }

    @Test fun HOM_12_deletingTheLastPageEmptiesIt() {
        val r = HomePageOps.delete(listOf(page(5, "a", "b")), 0, cap)
        assertEquals(1, r.pages.size); assertTrue(r.pages[0].keys.isEmpty()); assertEquals(5L, r.pages[0].id)
        assertEquals(setOf("a", "b"), r.taken)
    }

    @Test fun HOM_12_deletingTheOnlyEmptyPageStillLeavesOnePage() {
        val r = HomePageOps.delete(listOf(page(0)), 0, cap)
        assertEquals(1, r.pages.size); assertEquals(1L, r.pages[0].id)
    }

    @Test fun HOM_12_moveUpAndDownKeepsAllPages() {
        val ps = listOf(page(1, "a"), page(2, "b"), page(3, "c"))
        val down = HomePageOps.move(ps, 0, 1)!!
        assertEquals(listOf("b", "a", "c"), down.pages.flatMap { it.keys }); assertEquals(1, down.focus)
        val up = HomePageOps.move(ps, 2, -1)!!
        assertEquals(listOf("a", "c", "b"), up.pages.flatMap { it.keys }); assertEquals(1, up.focus)
        assertNull(HomePageOps.move(ps, 0, -1)); assertNull(HomePageOps.move(ps, 2, 1))
    }

    @Test fun HOM_12_moveCarriesAnOverflowedPageAsOneGroup() {
        val ps = listOf(page(1, "a"), page(2, "b", "c"), page(2, "d"), page(3, "e"))
        assertEquals(1..2, HomePageOps.groupOf(ps, 2))
        val r = HomePageOps.move(ps, 1, 1)!!
        assertEquals(listOf(1L, 3L, 2L, 2L), r.pages.map { it.id }); assertEquals(2, r.focus)
        assertEquals(setOf("a", "b", "c", "d", "e"), r.pages.flatMap { it.keys }.toSet())
    }

    @Test fun HOM_12_dragToPositionReorders() {
        val ps = listOf(page(1, "a"), page(2, "b"), page(3, "c"), page(4, "d"))
        val r = HomePageOps.moveTo(ps, 0, 3)!!
        assertEquals(listOf("b", "c", "d", "a"), r.pages.flatMap { it.keys }); assertEquals(3, r.focus)
        assertNull(HomePageOps.moveTo(ps, 1, 1))
    }

    @Test fun HOM_12_addPageGoesAfterTheCurrentOne() {
        val r = HomePageOps.add(listOf(page(1, "a"), page(2, "b")), 0)
        assertEquals(3, r.pages.size); assertEquals(1, r.focus); assertTrue(r.pages[1].keys.isEmpty())
    }

    // ---- HOM-01 empty default
    @Test fun HOM_01_freshInstallSeedsOneEmptyPageAndNoApps() {
        assertTrue(FreshHome.isFresh(HomeLayout(), onboardingDone = false))
        val seed = FreshHome.emptyLayout()
        assertEquals(1, seed.pages.size); assertTrue(seed.pages[0].keys.isEmpty()); assertTrue(seed.seeded)
        assertEquals(AppsOnHome.PinnedOnly, FreshHome.prefs(LauncherPrefs()).appsOnHome)
    }

    @Test fun HOM_01_existingUsersAreNeverTouched() {
        val saved = HomeLayout(pages = listOf(page(1, "a")), seeded = true)
        assertFalse(FreshHome.isFresh(saved, onboardingDone = false))
        assertFalse(FreshHome.isFresh(HomeLayout(), onboardingDone = true)) // settings from before: all apps flow as before
        assertFalse(FreshHome.isFresh(HomeLayout(removed = setOf("x")), onboardingDone = false))
        assertFalse(FreshHome.isFresh(HomeLayout(), onboardingDone = false, legacySeeded = true))
        assertFalse(FreshHome.isFresh(FreshHome.emptyLayout(), onboardingDone = false)) // once seeded it is a saved layout
        assertTrue(FreshHome.isFresh(HomeLayout(seeded = true), onboardingDone = false)) // the size-class binding flags an empty layout seeded on the first frame
    }

    @Test fun HOM_01_hintShowsOnlyWhenEveryPageIsEmpty() {
        assertTrue(FreshHome.isEmpty(listOf(page(1), page(2))))
        assertFalse(FreshHome.isEmpty(listOf(page(1), page(2, "a"))))
    }

    @Test fun HOM_01_freshVisualPagesAreOneEmptyPageEvenWithAppsInstalled() {
        val v = HomeLayoutMath.visualPages(FreshHome.emptyLayout(), listOf("a", "b", "c"), cap, autoPlace = false)
        assertEquals(1, v.size); assertTrue(v[0].keys.isEmpty())
    }

    // ---- HOM-04 / HOM-05 add carousel
    private data class A(val label: String, val key: String = "p.$label")

    @Test fun HOM_05_addCarouselListsAlphabeticallyFiltersAndSkipsPlacedApps() {
        val all = listOf(A("Maps"), A("Camera"), A("Clock"), A("Mail"))
        fun q(on: Set<String>, query: String, work: Boolean? = null, w: (A) -> Boolean = { false }) =
            AddCarouselModel.apps(all, on, query, work, w, { it.key }, { it.label }).map { it.label }
        assertEquals(listOf("Camera", "Clock", "Mail", "Maps"), q(emptySet(), ""))
        assertEquals(listOf("Camera", "Clock"), q(emptySet(), "c"))
        assertEquals(listOf("Mail", "Maps"), q(setOf(all[1].key, all[2].key), ""))
        assertEquals(listOf("Mail"), q(emptySet(), "  MAI "))
        assertEquals(listOf("Maps"), q(emptySet(), "", work = true, w = { it.label == "Maps" }))
    }

    @Test fun HOM_04_tapPlacesInTheFirstFreeCellOfTheCurrentPage() {
        val ps = listOf(page(1, "a"), page(2, "b"))
        val r = AddCarouselModel.place(ps, "x", 1, cap)
        assertEquals(listOf("a"), r[0].keys); assertEquals(listOf("b", "x"), r[1].keys)
    }

    @Test fun HOM_04_fullPageSpillsToTheNextWithRoomThenGrowsAPage() {
        val full = listOf(page(1, "a", "b", "c", "d"), page(2, "e"))
        assertEquals(listOf("e", "x"), AddCarouselModel.place(full, "x", 0, cap)[1].keys)
        val allFull = listOf(page(1, "a", "b", "c", "d"))
        val grown = AddCarouselModel.place(allFull, "x", 0, cap)
        assertEquals(2, grown.size); assertEquals(listOf("x"), grown[1].keys); assertEquals(4, grown[0].keys.size)
    }

    @Test fun HOM_04_anEmptyHomeStillTakesTheFirstItemAndNeverDuplicates() {
        assertEquals(listOf("x"), AddCarouselModel.place(emptyList(), "x", 0, cap)[0].keys)
        val ps = listOf(page(1, "x"))
        assertEquals(ps, AddCarouselModel.place(ps, "x", 0, cap))
    }
}
