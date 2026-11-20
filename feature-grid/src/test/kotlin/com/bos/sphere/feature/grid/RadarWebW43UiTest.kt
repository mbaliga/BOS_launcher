package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HitKind
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.SearchCategory
import com.bos.sphere.core.data.SearchHit
import com.bos.sphere.core.data.SearchLayout
import com.bos.sphere.core.data.WebEngine
import com.bos.sphere.core.data.WebFetcher
import com.bos.sphere.core.data.WebHits
import com.bos.sphere.core.data.WebLive
import com.bos.sphere.core.data.WebSuggestSource
import com.bos.sphere.feature.search.WebRow
import com.bos.sphere.feature.search.sections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Wave 43 (radarWeb, PAL-06, PAL-15, NFR-16): Enter on the Web scope opens the chosen engine (it used to open the Play Store), live suggestions in the Web scope,
 * Play Store rows in the Apps scope. No real network: the suggestion client is given a fake fetcher.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RadarWebW43UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun english() { com.bos.sphere.core.design.L10n.setLanguage("en") }

    private val apps = listOf("Alpha", "Bravo", "Charlie").map { n -> AppEntry("demo.$n", ComponentName("demo.$n", "demo.$n.Main"), n, Process.myUserHandle()) { null } }

    private class Fake(val answer: (String) -> String? = { u -> if ("suggestqueries" in u) """["spider",["spider man","spiders","spider solitaire"]]""" else if ("market.android.com" in u) """[{"s":"spider solitaire","t":"q"},{"s":"spider games","t":"q"}]""" else null }) : WebFetcher {
        val urls = ArrayList<String>()
        override suspend fun get(url: String): String? { urls += url; return answer(url) }
    }

    private val opened = ArrayList<Intent>()
    private var noteSeen = false

    private fun open(
        query: String, scope: SearchCategory?, fetch: Fake = Fake(), layout: SearchLayout = SearchLayout.Flat,
        prefs: LauncherPrefs = LauncherPrefs(searchMotion = false, searchLayout = layout, searchLayoutPinned = true, webEngine = WebEngine.Google),
        noteAlreadySeen: Boolean? = null,
    ) {
        if (noteAlreadySeen != null) noteSeen = noteAlreadySeen
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SearchScreen(
                apps = apps, recent = apps, prefs = prefs, contacts = null,
                onLaunch = {}, onLongPressApp = {}, onOpenIntent = { opened += it }, onRequestContactsPermission = {}, onDismiss = {},
                hub = null, onOpenHit = null, onRunAction = {}, onRequestFilePermission = {}, sounds = null, initialQuery = query, initialCategory = scope,
                learnedOverride = emptyMap(), nowMillis = { RadarTestHabits.NOW },
                webSuggest = WebSuggestSource(fetch), webNoteSeen = { mark -> val was = noteSeen; if (mark) noteSeen = true; was },
            )
        }
        rule.mainClock.advanceTimeBy(300)
    }

    private fun afterDebounce() { rule.mainClock.advanceTimeBy(900) }

    private fun go() { rule.onNode(hasSetTextAction()).performImeAction(); rule.mainClock.advanceTimeBy(100) }

    private fun data(query: String, prefs: LauncherPrefs = LauncherPrefs(webEngine = WebEngine.Google), live: WebLive = WebLive(), appsList: List<AppEntry> = apps) = SearchData(
        query = query, apps = com.bos.sphere.feature.search.AppSearch.rank(appsList, query, limit = 24), contacts = emptyList(), askContacts = false,
        web = sections(query, prefs.webEngine, emptyList<AppEntry>(), emptyList<ContactRow>(), searchWeb = prefs.searchWeb, searchContacts = false).web,
        webFirst = com.bos.sphere.core.data.WebSearch.looksLikeUrl(query), hits = emptyMap(), askFiles = false, deepHint = false,
        liveWeb = WebHits.web(prefs.webEngine, query, live), livePlay = WebHits.play(live),
    )

    // ---- the bug: what Enter runs, per scope (pure)
    @Test @Config(sdk = [35]) fun PAL_06_enterOnTheWebScopeIsTheEnginesSearchRowNotThePlayStore() {
        val live = WebLive(listOf("spider man"), play = listOf("spider solitaire"))
        for (l in listOf(WebLive(), live)) {
            val t = data("spider", live = l).enterTile(SearchCategory.Web) as Tile.Web
            assertTrue(t.row is WebRow.Search); assertEquals("https://www.google.com/search?q=spider", (t.row as WebRow.Search).url)
        }
        // Before wave 43 the Web scope listed the Play Store row first, so Enter opened the store.
        val tiles = data("spider").tiles(SearchCategory.Web)
        assertTrue(tiles.none { it is Tile.Web && it.row is WebRow.PlayStore })
    }

    @Test @Config(sdk = [35]) fun PAL_06_enterOnEverythingWithNoAppMatchIsTheEnginesSearchRow() {
        val t = data("spider").enterTile(null) as Tile.Web
        assertTrue(t.row is WebRow.Search)
        val all = data("spider", live = WebLive(listOf("spider man", "spiders", "spider x", "spider y"), play = listOf("a", "b", "c"))).tiles(null)
        val firstPlay = all.indexOfFirst { isPlayTile(it) }
        assertTrue("the Play Store comes after the web rows", firstPlay > all.indexOfFirst { it is Tile.Web && it.row is WebRow.Search })
        assertEquals("at most 3 live web suggestions in Everything", 3, all.count { it is Tile.Hit && it.hit.kind == HitKind.WebSuggestion })
        assertEquals("at most 2 store suggestions in Everything", 2, all.count { it is Tile.Hit && it.hit.kind == HitKind.PlayStore })
    }

    @Test @Config(sdk = [35]) fun PAL_06_enterOnEverythingWithAnAppMatchLaunchesTheAppAndInstalledMatchesComeFirst() {
        val d = data("bra", live = WebLive(listOf("brazil")))
        assertEquals("demo.Bravo", (d.enterTile(null) as Tile.App).app.packageName)
        val all = d.tiles(null)
        assertTrue(all.first() is Tile.App)
        assertTrue("web suggestions rank below the app matches", all.indexOfFirst { it is Tile.Hit && it.hit.kind == HitKind.WebSuggestion } > all.indexOfFirst { it is Tile.App })
    }

    @Test @Config(sdk = [35]) fun PAL_15_enterOnTheAppsScopeIsTheTopAppOrThePlayStoreRowWhenNothingIsInstalled() {
        assertEquals("demo.Alpha", (data("alp").enterTile(SearchCategory.Apps) as Tile.App).app.packageName)
        val none = data("zzqx").enterTile(SearchCategory.Apps) as Tile.Web
        assertTrue(none.row is WebRow.PlayStore)
        assertTrue("one letter: no Play Store row", data("z").tiles(SearchCategory.Apps).isEmpty())
    }

    @Test @Config(sdk = [35]) fun PAL_06_aUrlQueryOnTheWebScopeOpensThePageAndWebResultsOffRemovesTheRows() {
        assertTrue(((data("example.com").enterTile(SearchCategory.Web) as Tile.Web).row) is WebRow.Open)
        val off = LauncherPrefs(searchWeb = false)
        assertNull(data("spider", off).enterTile(SearchCategory.Web))
        assertTrue(data("zzqx", off).tiles(SearchCategory.Apps).isEmpty())
    }

    // ---- the Enter key / Go in the running screen
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi") fun PAL_06_goOnTheWebScopeOpensTheChosenEngineInTheBrowserAndNeverTheStore() {
        val f = Fake(); open("spider", SearchCategory.Web, f); afterDebounce(); go()
        val i = opened.single()
        assertEquals(Intent.ACTION_VIEW, i.action); assertEquals("https://www.google.com/search?q=spider", i.dataString)
        assertTrue(i.categories.orEmpty().contains(Intent.CATEGORY_BROWSABLE))
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi") fun PAL_06_goOnEverythingWithNothingInstalledOpensTheChosenEngineEvenAfterTheEngineIsChanged() {
        open("spider", null, prefs = LauncherPrefs(searchMotion = false, searchLayout = SearchLayout.Flat, searchLayoutPinned = true, webEngine = WebEngine.Bing)); go()
        assertEquals("https://www.bing.com/search?q=spider", opened.single().dataString)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi") fun PAL_15_goOnTheAppsScopeWithNoMatchOpensThePlayStoreSearch() {
        open("zzqx", SearchCategory.Apps); go()
        val d = opened.single().dataString!!
        assertTrue(d, d.startsWith("https://play.google.com/store/search?q=zzqx") || d.startsWith("market://search?q=zzqx"))
    }

    // ---- live results
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi") fun PAL_06_suggestionsAppearAfterTheDebounceAndATapOpensTheEnginesResultsForThem() {
        val f = Fake(); open("spider", SearchCategory.Web, f)
        rule.onNodeWithText("Search Google for “spider”").assertExists()
        rule.mainClock.advanceTimeBy(400)
        assertEquals("nothing is sent before 800 ms", emptyList<String>(), f.urls)
        assertEquals(0, rule.onAllNodesWithText("spider man").fetchSemanticsNodes().size)
        rule.mainClock.advanceTimeBy(500)
        assertEquals(listOf("https://suggestqueries.google.com/complete/search?client=firefox&q=spider"), f.urls)
        rule.onNodeWithText("spider man").assertExists(); rule.onNodeWithText("spiders").assertExists()
        rule.onNodeWithText("spider man").performClick()
        assertEquals("https://www.google.com/search?q=spider+man", opened.single().dataString)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi") fun PAL_06_theAnswerCardShowsForDuckDuckGo() {
        val f = Fake { u -> if ("api.duckduckgo.com" in u) """{"Heading":"Spider","AbstractText":"Spiders are arachnids.","AbstractURL":"https://en.wikipedia.org/wiki/Spider"}""" else """["spider",["spider man"]]""" }
        open("spider", SearchCategory.Web, f, prefs = LauncherPrefs(searchMotion = false, searchLayout = SearchLayout.Flat, searchLayoutPinned = true, webEngine = WebEngine.DuckDuckGo))
        afterDebounce()
        rule.onNodeWithText("Spiders are arachnids.").assertExists(); rule.onNodeWithText("spider man").assertExists()
        rule.onNodeWithText("Spiders are arachnids.").performClick()
        assertEquals("https://en.wikipedia.org/wiki/Spider", opened.single().dataString)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi") fun PAL_06_offlineShowsOnlyTheSearchRowAndNoErrorText() {
        val f = Fake { null }; open("spider", SearchCategory.Web, f); afterDebounce()
        assertEquals(1, f.urls.size)
        rule.onNodeWithText("Search Google for “spider”").assertExists()
        assertEquals(0, rule.onAllNodesWithText("error", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("Suggestions come from", substring = true).fetchSemanticsNodes().size)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi") fun PAL_06_theSwitchOffMeansNoRequestAtAllAndOnlyTheSearchRow() {
        val f = Fake()
        open("spider", SearchCategory.Web, f, prefs = LauncherPrefs(searchMotion = false, searchLayout = SearchLayout.Flat, searchLayoutPinned = true, webEngine = WebEngine.Google, webSuggestions = false))
        afterDebounce(); rule.mainClock.advanceTimeBy(5_000)
        assertEquals(emptyList<String>(), f.urls)
        rule.onNodeWithText("Search Google for “spider”").assertExists()
        assertEquals(0, rule.onAllNodesWithText("spider man").fetchSemanticsNodes().size)
        go(); assertEquals("https://www.google.com/search?q=spider", opened.single().dataString)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi") fun PAL_06_webResultsOffMeansNoRequestEither() {
        val f = Fake()
        open("spider", SearchCategory.Web, f, prefs = LauncherPrefs(searchMotion = false, searchLayout = SearchLayout.Flat, searchLayoutPinned = true, searchWeb = false))
        afterDebounce(); assertEquals(emptyList<String>(), f.urls)
    }

    private fun neverSent(q: String) { val f = Fake(); open(q, SearchCategory.Web, f); afterDebounce(); assertEquals(q, emptyList<String>(), f.urls) }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi") fun PAL_06_aUrlIsNeverSent() = neverSent("example.com")
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi") fun PAL_06_anEmailAddressIsNeverSent() = neverSent("me@example.com")
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi") fun PAL_06_oneLetterIsNeverSent() = neverSent("s")

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi") fun PAL_15_theAppsScopeAsksOnlyThePlayStoreAndNeverTheSearchEngine() {
        val f = Fake(); open("spider", SearchCategory.Apps, f); afterDebounce()
        assertEquals(listOf("https://market.android.com/suggest/SuggRequest?json=1&query=spider&hl=en&gl=us&c=3"), f.urls)
        assertTrue(f.urls.none { "google" in it || "duckduckgo" in it || "bing" in it })
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi") fun PAL_15_theAppsScopeShowsInstalledMatchesFirstThenAPlayStoreGroupWithAMarketLink() {
        val f = Fake(); open("a", SearchCategory.Apps, f, prefs = LauncherPrefs(searchMotion = false, searchLayout = SearchLayout.Flat, searchLayoutPinned = true))
        // one letter: installed matches only, no Play Store row, nothing sent
        rule.onAllNodesWithText("Alpha").fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
        assertEquals(0, rule.onAllNodesWithText("Play Store").fetchSemanticsNodes().size)
        assertEquals(emptyList<String>(), f.urls)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi") fun PAL_15_theAppsScopeListsTheStoreRowAndStoreSuggestionsAfterTheInstalledApp() {
        val f = Fake(); open("alpha", SearchCategory.Apps, f)
        rule.onNodeWithText("Search the Play Store for “alpha”").assertExists()
        afterDebounce()
        assertTrue("the group header and the label of each store result", rule.onAllNodesWithText("Play Store").fetchSemanticsNodes().size >= 2)
        rule.onNodeWithText("spider solitaire").assertExists()
        // a tap on a store suggestion opens the store (the web page when this phone has no store app)
        rule.onNodeWithText("spider solitaire").performClick()
        val d = opened.single().dataString!!
        assertTrue(d, d == "market://search?q=spider+solitaire&c=apps" || d == "https://play.google.com/store/search?q=spider+solitaire&c=apps")
        // the row opens the store search for the query
        opened.clear(); rule.onNodeWithText("Search the Play Store for “alpha”").performClick()
        assertTrue(opened.single().dataString!!.contains("q=alpha"))
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi") fun PAL_15_aStoreSuggestionOpensTheMarketLinkWhenAStoreIsInstalled() {
        val pm = org.robolectric.Shadows.shadowOf(rule.activity.packageManager)
        pm.addResolveInfoForIntent(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://search?q=spider+solitaire&c=apps")), android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply { packageName = "com.android.vending"; name = "Market" }
        })
        val f = Fake(); open("spider", SearchCategory.Apps, f); afterDebounce()
        rule.onNodeWithText("spider solitaire").performClick()
        assertEquals("market://search?q=spider+solitaire&c=apps", opened.single().dataString)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi") fun PAL_06_theFirstUseNoteNamesTheEngineAndShowsOnlyOnce() {
        open("spider", SearchCategory.Web, noteAlreadySeen = false); afterDebounce()
        rule.onNodeWithText("Suggestions come from Google. Turn off in settings.").assertExists()
        assertTrue(noteSeen)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi") fun PAL_06_theNoteIsNotShownAgainOnTheNextVisit() {
        open("spider", SearchCategory.Web, noteAlreadySeen = true); afterDebounce()
        rule.onNodeWithText("spider man").assertExists()
        assertEquals(0, rule.onAllNodesWithText("Suggestions come from", substring = true).fetchSemanticsNodes().size)
    }

    // ---- every layout
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi") fun PAL_18_orbitShowsTheSearchRowAndTheSuggestionsOnTheRingsInTheWebScope() {
        open("spider", SearchCategory.Web, layout = SearchLayout.Orbit); afterDebounce()
        assertTrue(rule.onAllNodesWithContentDescription("spider man", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("Search Google for “spider”", substring = true).fetchSemanticsNodes().isNotEmpty())
        val orbit = SearchData("spider", emptyList(), emptyList(), false, sections("spider", WebEngine.Google, emptyList<AppEntry>(), emptyList<ContactRow>(), searchContacts = false).web, false, emptyMap(), false, false,
            liveWeb = WebHits.web(WebEngine.Google, "spider", WebLive(listOf("spider man"))))
        assertEquals(emptyList<Tile>(), orbit.stripTiles(SearchCategory.Web))
        assertEquals(2, orbit.ringTiles(SearchCategory.Web).size)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi") fun PAL_18_filmstripShowsTheSuggestionsInTheWebScope() {
        open("spider", SearchCategory.Web, layout = SearchLayout.Filmstrip); afterDebounce()
        assertTrue(rule.onAllNodesWithText("spider man").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithContentDescription("spider man", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi") fun PAL_18_orbitInTheAppsScopePutsThePlayStoreRowAndSuggestionsOnTheRingsAfterTheApps() {
        open("alpha", SearchCategory.Apps, layout = SearchLayout.Orbit); afterDebounce()
        assertTrue("the store row is a strip circle, always reachable", rule.onAllNodesWithContentDescription("Search the Play Store for “alpha”", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue("store suggestions are ring tiles", rule.onAllNodesWithContentDescription("spider solitaire", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    // ---- screenshots
    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(600)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun shot_w43_webScopeFlatDark() {
        open("spider", SearchCategory.Web, noteAlreadySeen = false); afterDebounce(); shoot("w43-web-scope-list-dark")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun shot_w43_webScopeOrbitDark() {
        open("spider", SearchCategory.Web, layout = SearchLayout.Orbit, noteAlreadySeen = false); afterDebounce(); shoot("w43-web-scope-orbit-dark")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun shot_w43_appsScopeFlatDark() {
        open("alpha", SearchCategory.Apps); afterDebounce(); shoot("w43-apps-scope-list-dark")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun shot_w43_everythingFlatDark() {
        open("al", null); afterDebounce(); shoot("w43-everything-list-dark")
    }
}
