package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Wave 30 (pagesExplicit): the home holds only what the user put there. Pages exist because the user added them, apps and widgets are on a page
 * because the user placed them, a deleted page takes its placements with it, and an install-time auto-filled layout is reset once.
 */
class PagesExplicitW30Test {
    @get:Rule val tmp = TemporaryFolder()

    private val cap: (PageLayout) -> Int = { 6 }
    private val apps = (1..84).map { "app%02d".format(it) }
    private fun page(id: Long, vararg keys: String) = HomePage(keys.toList(), PageLayout(), id)
    private fun filled(pages: Int, per: Int = 6, from: List<String> = apps) =
        (0 until pages).map { i -> HomePage(from.drop(i * per).take(per), PageLayout(), i + 1L) }

    // ---- the model

    @Test fun PGX_addPageCreatesExactlyOneEmptyPageAfterTheCurrentOne() {
        val r = HomePageOps.add(listOf(page(1, "a"), page(2, "b")), 0)
        assertEquals(3, r.pages.size); assertEquals(1, r.focus)
        assertTrue(r.pages[1].keys.isEmpty()); assertEquals(listOf("a"), r.pages[0].keys); assertEquals(listOf("b"), r.pages[2].keys)
        val twice = HomeDeck.addPage(HomeDeck.addPage(listOf(page(1)), null, pagesAlreadyOn = true).pages, 1, pagesAlreadyOn = true)
        assertEquals(3, twice.pages.size)
        assertEquals(1, HomeDeck.addPage(listOf(page(1)), null).pages.size) // turning Pages on shows the untouched first page instead of stacking another
        val two = HomeDeck.addPage(HomeDeck.addPage(listOf(page(1)), null).pages, null, pagesAlreadyOn = true)
        assertEquals(2, two.pages.size); assertTrue(two.pages.all { it.keys.isEmpty() })
    }

    @Test fun PGX_aPlacedItemStaysOnItsPageAndANewInstallIsNotPlaced() {
        val placed = AddCarouselModel.place(listOf(page(1), page(2)), "app01", 1, cap)!!
        assertEquals(listOf(emptyList(), listOf("app01")), placed.map { it.keys })
        val layout = HomeLayout(pages = placed, seeded = true)
        // a second app is installed: it shows on no page, no page appears
        val shown = HomeLayoutMath.visualPages(layout, apps.take(2), cap)
        assertEquals(listOf(emptyList(), listOf("app01")), shown.map { it.keys })
        // not even when every page is empty or no page has ever been stored
        assertEquals(listOf(emptyList<String>()), HomeLayoutMath.visualPages(HomeLayout(), apps, cap).map { it.keys })
    }

    @Test fun PGX_deletingAPageRemovesItsPlacementsAndNothingRefillsIt() {
        val pages = listOf(page(1, "app01"), page(2, "app02", "app03"), page(3))
        val r = HomePageOps.delete(pages, 1)
        assertEquals(listOf(listOf("app01"), emptyList<String>()), r.pages.map { it.keys })
        assertEquals(setOf("app02", "app03"), r.taken)
        // the apps are still installed: the next resolve does not bring them (or the page) back
        val after = HomeLayoutMath.visualPages(HomeLayout(pages = r.pages, removed = r.taken, seeded = true), apps.take(5), cap)
        assertEquals(listOf(listOf("app01"), emptyList<String>()), after.map { it.keys })
        assertEquals(2, HomeDeck.stops(glanceOn = true, mode = HomePages.Paginated, pageCount = 1) + 0) // house + one dot for one page
    }

    @Test fun PGX_aFullPageRefusesTheItemInsteadOfOpeningAPage() {
        val full = listOf(HomePage(apps.take(6), PageLayout(), 1))
        assertNull(AddCarouselModel.place(full, "app07", 0, cap))
        assertEquals(full, HomeLayoutMath.add(full, "app07", 0, cap))
        assertFalse(HomeLayoutMath.hasRoom(full, cap))
    }

    @Test fun PGX_aPageEmptiedByRemovingItsLastItemStays() {
        val out = HomeLayoutMath.remove(listOf(page(1, "a"), page(2, "b")), "b")
        assertEquals(2, out.size)
    }

    @Test fun PGX_indicatorShowsTheRealPageCount() {
        assertEquals(1 + 2, HomeDeck.stops(true, HomePages.Paginated, 2)) // house + 2 dots
        assertEquals(1 + 1, HomeDeck.stops(true, HomePages.Paginated, 1))
    }

    // ---- the migration

    private fun store() = HomeLayoutStore(MapSharedPreferences())
    private fun backup() = LegacyHomeBackup(tmp.newFolder())
    private fun orders() = listOf(apps, apps.sortedDescending().reversed())
    private fun run(s: HomeLayoutStore, b: LegacyHomeBackup, everything: Boolean = true, now: Long = 1_000L) =
        PagesExplicitMigration.run(s, b, orders(), cap, everything, now)

    @Test fun PGX_anAutoFilledFourteenPageHomeIsResetToOneEmptyPageAndTheOldOneIsBackedUp() {
        val s = store(); val b = backup()
        s.replace(HomeLayout(pages = filled(14), seeded = true)) // 14 full pages: every installed app, the owner's phone
        assertEquals(PagesMigrationResult.Reset, run(s, b))
        assertEquals(1, s.layout.value.pages.size); assertTrue(s.layout.value.pages[0].keys.isEmpty())
        assertTrue(s.noticePending())
        assertEquals(14, b.load()!!.layout.pages.size)
        assertEquals(PagesMigrationResult.AlreadyDone, run(s, b)) // once
    }

    @Test fun PGX_aFlowedAppsOnlyLayoutInAnAutomaticOrderIsLegacyEvenWithFewPages() {
        val s = store(); val b = backup()
        s.replace(HomeLayout(pages = filled(3, per = 6) .let { it.dropLast(1) + HomePage(apps.drop(12).take(4), PageLayout(), 3L) }, seeded = true))
        assertEquals(PagesMigrationResult.Reset, run(s, b, everything = false))
    }

    @Test fun PGX_aHandBuiltTwoPageHomeIsKeptUntouched() {
        val s = store(); val b = backup()
        val mine = HomeLayout(pages = listOf(page(1, "app07", "app03", "tile:clock"), page(2, "app40")), seeded = true)
        s.replace(mine)
        assertEquals(PagesMigrationResult.Kept, run(s, b))
        assertEquals(mine, s.layout.value); assertNull(b.load()); assertFalse(s.noticePending())
        assertEquals(PagesMigrationResult.AlreadyDone, run(s, b))
    }

    @Test fun PGX_aLayoutWithARecordedEditIsNeverResetEvenWhenItIsHuge() {
        val s = store(); val b = backup()
        s.replace(HomeLayout(pages = filled(14), seeded = true))
        s.markExplicit()
        assertEquals(PagesMigrationResult.Kept, run(s, b))
        assertEquals(14, s.layout.value.pages.size)
    }

    @Test fun PGX_everyEditPathRecordsTheExplicitMarker() {
        val s = store()
        assertFalse(s.explicitEdit())
        s.setPages(listOf(page(1, "a")))
        assertTrue(s.explicitEdit())
        val t = store(); t.removeFromHome("a", listOf(page(1, "a"))); assertTrue(t.explicitEdit())
        val u = store(); u.addToHome("a", listOf(page(1)), 0, cap); assertTrue(u.explicitEdit())
    }

    @Test fun PGX_aFreshInstallAndAnEmptyCatalogDecideNothing() {
        val s = store(); val b = backup()
        s.replace(FreshHome.emptyLayout())
        assertEquals(PagesMigrationResult.Kept, run(s, b)); assertEquals(1, s.layout.value.pages.size)
        val t = store()
        assertEquals(PagesExplicit.Verdict.Keep, PagesExplicit.verdict(filled(10), listOf(emptyList()), cap, false, true))
        assertEquals(0, t.pagesExplicitVersion())
    }

    @Test fun PGX_aLongRunOfFullPagesCoveringMostAppsIsLegacyButAShortOrSparseRunIsNot() {
        fun rev(n: Int) = filled(n).map { p -> p.copy(keys = p.keys.reversed()) } + HomePage(listOf("tile:clock"), PageLayout(), 99L) // not in an automatic order, with a tile
        assertEquals(PagesExplicit.Verdict.Keep, PagesExplicit.verdict(rev(4), orders(), cap, false, false)) // 4 full pages: a hand-built home can look like this
        assertEquals(PagesExplicit.Verdict.Keep, PagesExplicit.verdict(rev(5), orders(), cap, false, false)) // 5 pages but only 30 of 84 apps (36 percent)
        assertEquals(PagesExplicit.Verdict.Reset, PagesExplicit.verdict(rev(9), orders(), cap, false, false)) // 9 full pages, 54 of 84 (64 percent)
    }

    @Test fun PGX_theOldAllAppsModeWithNoRecordedEditAndSeveralPagesIsLegacy() {
        val some = listOf(page(1, "app05", "app01", "app09"), page(2, "app30", "app31"), page(3, "app50", "app51")) +
            (4..20).map { page(it.toLong(), *apps.drop(it * 3).take(3).toTypedArray()) } // 3 pages' worth is not enough alone, 20 pages covering over half is
        assertEquals(PagesExplicit.Verdict.Reset, PagesExplicit.verdict(some, orders(), cap, false, legacyEverything = true))
        assertEquals(PagesExplicit.Verdict.Keep, PagesExplicit.verdict(some, orders(), cap, false, legacyEverything = false))
        assertEquals(PagesExplicit.Verdict.Keep, PagesExplicit.verdict(some.take(3), orders(), cap, false, legacyEverything = true))
    }

    @Test fun PGX_restoreBringsBackThePreviousPagesWithinThirtyDaysAndNotAfter() {
        val s = store(); val b = backup()
        val old = HomeLayout(pages = filled(14), seeded = true)
        s.replace(old)
        run(s, b, now = 1_000L)
        assertNotNull(b.available(1_000L + LegacyHomeBackup.WINDOW_MS - 1))
        assertTrue(PagesExplicitMigration.restore(s, b, 2_000L))
        assertEquals(old.pages, s.layout.value.pages)
        assertFalse(s.noticePending()); assertTrue(s.explicitEdit())
        assertNull(b.load()) // used up
        // expired: the backup is deleted and nothing is restored
        val s2 = store(); val b2 = backup()
        s2.replace(old); run(s2, b2, now = 0L)
        assertFalse(PagesExplicitMigration.restore(s2, b2, LegacyHomeBackup.WINDOW_MS + 1))
        assertNull(b2.load())
        assertEquals(1, s2.layout.value.pages.size)
    }

    @Test fun PGX_theLegacyBackupFileIsClassifiedAsExcludedFromBackup() {
        assertTrue(LegacyHomeBackup.FILE in StateSchema.BACKUP_EXCLUDED_FILES)
        assertEquals("home_layout_legacy.json", LegacyHomeBackup.FILE)
    }

    // ---- adversarial review findings

    @Test fun PGX_review_aDensityChangeSinceTheAutoFillDoesNotHideTheLegacyLayout() {
        // the auto-fill wrote 5 per page; today's grid holds 6 (or the phone is in landscape): the pages are the fullest they ever were, not "short"
        val five = filled(14, per = 5)
        assertEquals(PagesExplicit.Verdict.Reset, PagesExplicit.verdict(five, orders(), cap, false, false))
        val s = store(); s.replace(HomeLayout(pages = five, seeded = true))
        assertEquals(PagesMigrationResult.Reset, run(s, backup()))
    }

    @Test fun PGX_review_aTinyHandBuiltHomeInCatalogOrderIsNotMistakenForTheAutoFill() {
        val tiny = listOf(page(1, "app01", "app02"), page(2, "app03"))
        assertEquals(PagesExplicit.Verdict.Keep, PagesExplicit.verdict(tiny, orders(), cap, false, false))
        val sevenInOrder = listOf(HomePage(apps.take(6), PageLayout(), 1L), HomePage(apps.drop(6).take(1), PageLayout(), 2L))
        assertEquals(PagesExplicit.Verdict.Keep, PagesExplicit.verdict(sevenInOrder, orders(), cap, false, false))
    }

    @Test fun PGX_review_aBackupThatCannotBeWrittenNeverWipesTheLayout() {
        val s = store(); val old = HomeLayout(pages = filled(14), seeded = true); s.replace(old)
        val blocked = tmp.newFile() // a file where the backup directory should be: mkdirs and writeText fail
        val b = LegacyHomeBackup(File(blocked, "files"))
        assertEquals(PagesMigrationResult.Kept, run(s, b))
        assertEquals(old, s.layout.value); assertFalse(s.noticePending())
        assertEquals(0, s.pagesExplicitVersion()) // still pending: tried again on the next start
    }

    @Test fun PGX_review_theResetNoticeIsDroppedWhenItsBackupIsGone() {
        val s = store(); val b = backup()
        s.replace(HomeLayout(pages = filled(14), seeded = true)); run(s, b, now = 1_000L)
        assertTrue(PagesExplicitMigration.noticeStillValid(s, b, 2_000L))
        // a cloud backup or device transfer carries the pending flag but not the excluded backup file; the 30 days can also run out
        b.discard()
        assertFalse(PagesExplicitMigration.noticeStillValid(s, b, 2_000L))
        assertFalse(s.noticePending())
        val s2 = store(); val b2 = backup()
        s2.replace(HomeLayout(pages = filled(14), seeded = true)); run(s2, b2, now = 0L)
        assertFalse(PagesExplicitMigration.noticeStillValid(s2, b2, LegacyHomeBackup.WINDOW_MS + 1)); assertFalse(s2.noticePending())
    }

    @Test fun PGX_review_restoreAsksBeforeReplacingPagesTheUserHasBuiltSinceTheReset() {
        val s = store(); val b = backup()
        s.replace(HomeLayout(pages = filled(14), seeded = true)); run(s, b)
        assertFalse(PagesExplicitMigration.restoreReplacesEdits(s)) // still the one empty page
        s.setPages(listOf(page(1, "app50")))
        assertTrue(PagesExplicitMigration.restoreReplacesEdits(s))
        val t = store(); val bt = backup(); t.replace(HomeLayout(pages = filled(14), seeded = true)); run(t, bt)
        t.setPages(listOf(page(1), page(2))) // two empty pages the user added also count
        assertTrue(PagesExplicitMigration.restoreReplacesEdits(t))
    }

    @Test fun PGX_review_aFullHomeRefusesAnAppDroppedOnItAndWritesNothing() {
        val s = store(); val full = listOf(HomePage(apps.take(6), PageLayout(), 1L))
        s.replace(HomeLayout(pages = full, removed = setOf("app99"), seeded = true))
        assertFalse(s.addToHome("app99", full, 0, cap))
        assertEquals(setOf("app99"), s.layout.value.removed) // not put back on the home, nothing marked
        assertFalse(s.explicitEdit())
        assertTrue(s.addToHome("app98", listOf(HomePage(apps.take(3), PageLayout(), 1L)), 0, cap))
    }
}
