package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.HitKind
import com.bos.sphere.core.data.SearchCategory
import com.bos.sphere.core.data.SearchHit
import com.bos.sphere.core.input.MenuAction
import com.bos.sphere.feature.search.WebRow
import dev.aarso.search.palette.Ranker
import dev.aarso.search.palette.UsageStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarPaletteTest {
    private fun hit(id: String, cat: SearchCategory = SearchCategory.Actions, score: Float = 0.5f, copy: String? = null, usage: String? = null) =
        SearchHit(id = id, category = cat, title = id, kind = HitKind.Action, score = score, copyText = copy, extras = if (usage != null) mapOf("usage" to usage) else emptyMap())

    private val webRows = listOf(
        WebRow.Open("Open example.com", "https://example.com"),
        WebRow.Search("Search DuckDuckGo for “x”", "https://duckduckgo.com/?q=x"),
        WebRow.PlayStore("Search Play Store for “x”", "https://play.google.com/store/search?q=x", "market://search?q=x"),
    )

    private fun data(q: String = "x", web: List<WebRow> = webRows, webFirst: Boolean = false, hits: Map<SearchCategory, List<SearchHit>> = emptyMap(), askContacts: Boolean = false) =
        SearchData(query = q, apps = emptyList(), contacts = emptyList(), askContacts = askContacts, web = web, webFirst = webFirst, hits = hits, askFiles = false, deepHint = false)

    @Test fun PAL_05_anInlineAnswerIsTheFirstResultSoEnterCopiesIt() {
        val answer = hit("calc:2+2", copy = "4", score = 0.2f)
        val d = data("2+2", hits = mapOf(SearchCategory.Actions to listOf(hit("other", score = 0.9f), answer)))
        val top = d.enterTile(null) as Tile.Hit
        assertEquals("calc:2+2", top.hit.id)
        assertEquals("4", top.hit.copyText)
        assertEquals("calc:2+2", (flatTiles(d, null).first() as Tile.Hit).hit.id)
        assertEquals(SearchCategory.Actions, flatSections(d).first().first)
    }

    @Test fun PAL_06_theWebHandOffIsAlwaysTheLastResult() {
        for (webFirst in listOf(false, true)) {
            val d = data("x", webFirst = webFirst, hits = mapOf(SearchCategory.Files to listOf(hit("f1", SearchCategory.Files))))
            val all = d.tiles(null)
            val last = all.last()
            assertTrue("last is a web row", last is Tile.Web && (last.row is WebRow.Search || last.row is WebRow.Open))
            assertEquals(SearchCategory.Web, flatSections(d).last().first)
            assertTrue(flatTiles(d, null).last() is Tile.Web)
        }
    }

    @Test fun PAL_06_aBangOrUrlQueryStillRunsOnEnterThroughTheWebRow() {
        val d = data("example.com", webFirst = true)
        val e = d.enterTile(null) as Tile.Web
        assertTrue(e.row is WebRow.Open)
    }

    @Test fun PAL_15_theStoreLookupSitsJustBeforeTheBrowserHandOff() {
        val web = data().tiles(SearchCategory.Web).filterIsInstance<Tile.Web>()
        val i = web.indexOfFirst { it.row is WebRow.PlayStore }
        assertTrue(i >= 0 && i < web.lastIndex)
        assertTrue(web.last().row !is WebRow.PlayStore)
    }

    @Test fun PAL_10_withoutContactsPermissionTheCategoryIsOneEnableRowAndNothingElse() {
        val tiles = data("anna", web = emptyList(), askContacts = true).tiles(SearchCategory.Contacts)
        assertEquals(1, tiles.size)
        assertEquals(Tile.Allow(files = false), tiles.single())
        // It does not leak into the mixed list.
        assertTrue(data("anna", web = emptyList(), askContacts = true).tiles(null).none { it is Tile.Allow })
    }

    @Test fun PAL_08_everyAppResultOffersTheSameListOnEveryPath() {
        val labels = ResultMenus.forKind(ResultKind.App, otherDisplays = 1, adjacentSupported = true).mapNotNull { it.action }
        for (a in listOf(MenuAction.Open, MenuAction.OpenOnDisplay, MenuAction.OpenBeside, MenuAction.AppInfo, MenuAction.AddToDock, MenuAction.AddToWorkspace, MenuAction.Uninstall)) assertTrue("missing $a", a in labels)
        // The keyboard list and the host sheet entries (more) are both there; non-apps never offer app-only actions.
        val app = ResultMenus.forKind(ResultKind.App)
        assertTrue(app.any { it.alias } && app.any { it.more } && app.any { it.preview })
        assertFalse(ResultMenus.forKind(ResultKind.File).any { it.action == MenuAction.Uninstall || it.alias })
        // Without a second display, "open on another display" is not offered.
        assertFalse(ResultMenus.forKind(ResultKind.App, otherDisplays = 0).any { it.action == MenuAction.OpenOnDisplay })
    }

    @Test fun PAL_12_hubResultsStreamInWithoutMovingTheSelectedRow() {
        val shown = mapOf(SearchCategory.Actions to listOf(hit("a", score = 0.9f), hit("b", score = 0.8f)))
        val fresh = mapOf(SearchCategory.Actions to listOf(hit("n", score = 0.99f), hit("a", score = 0.9f), hit("b", score = 0.8f)))
        val out = HubOrdering.order(shown, fresh, "b", null)[SearchCategory.Actions]!!.map { it.id }
        assertEquals(listOf("a", "b", "n"), out)
        assertEquals(listOf("n", "a", "b"), HubOrdering.order(shown, fresh, null, null)[SearchCategory.Actions]!!.map { it.id })
    }

    @Test fun PAL_09_selectionsReorderHubHits() {
        val store = UsageStore()
        val ranker = Ranker(store, clock = { 5_000L })
        val fresh = mapOf(SearchCategory.Actions to listOf(hit("a", score = 0.6f), hit("b", score = 0.6f)))
        assertEquals("a", HubOrdering.order(emptyMap(), fresh, null, ranker)[SearchCategory.Actions]!!.first().id)
        repeat(4) { store.record("b", 5_000L) }
        assertEquals("b", HubOrdering.order(emptyMap(), fresh, null, ranker)[SearchCategory.Actions]!!.first().id)
        ranker.reset()
        assertEquals("a", HubOrdering.order(emptyMap(), fresh, null, ranker)[SearchCategory.Actions]!!.first().id)
    }

    @Test fun PAL_13_prefixLettersAreEditableAndOneSettingWithTheWheel() {
        val m = ScopePrefixes.DEFAULT
        val changed = PrefixEditing.change(m, SearchCategory.Files, "d") as PrefixEditing.Result.Saved
        assertEquals('d', changed.mapping.letterOf(SearchCategory.Files))
        // The typed prefix, the wheel and the stored form all follow the new letter.
        assertEquals(SearchCategory.Files to "budget", ScopePrefixes.parse("d budget", changed.mapping))
        assertEquals("d budget", ScopePrefixes.applyWheel("budget", SearchCategory.Files, changed.mapping))
        assertEquals(SearchCategory.Files, ScopePrefixes.Mapping.decode(changed.mapping.encode()).categoryOf('d'))
        // Clashes and non-letters are refused with a reason.
        assertTrue(PrefixEditing.change(m, SearchCategory.Files, "a") is PrefixEditing.Result.Refused)
        assertTrue(PrefixEditing.change(m, SearchCategory.Files, "7") is PrefixEditing.Result.Refused)
        assertTrue(PrefixEditing.change(m, SearchCategory.Files, "") is PrefixEditing.Result.Refused)
    }

    @Test fun PAL_03_launcherCommandsHaveATouchPathAndAnActionId() {
        assertNotNull(CornerActionId.Search)
    }
}
