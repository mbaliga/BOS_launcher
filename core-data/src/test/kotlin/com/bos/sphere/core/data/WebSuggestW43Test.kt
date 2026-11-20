package com.bos.sphere.core.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 43 (radarWeb, PAL-06, PAL-15, NFR-16): the pure parts of Radar's live web suggestions. No real network call anywhere: the fetcher is a fake. */
class WebSuggestW43Test {
    private class FakeFetcher(val answers: (String) -> String? = { null }) : WebFetcher {
        val urls = ArrayList<String>()
        override suspend fun get(url: String): String? { urls += url; return answers(url) }
    }

    // ---- engines and URL builders
    @Test fun PAL_06_everyEngineBuildsAnEscapedHttpsSearchUrlAndGoogleIsTheDefault() {
        assertEquals(WebEngine.Google, LauncherPrefs().webEngine)
        assertEquals(listOf("Google", "DuckDuckGo", "Bing", "Brave", "Ecosia", "Startpage"), WebEngine.entries.map { it.label })
        for (e in WebEngine.entries) assertTrue(e.name, e.url("a b&c=d/é").startsWith("https://"))
        assertEquals("https://www.google.com/search?q=spider+man%26x%3D1", WebEngine.Google.url(" spider man&x=1 "))
        assertEquals("https://duckduckgo.com/?q=caf%C3%A9", WebEngine.DuckDuckGo.url("café"))
        assertEquals("https://www.ecosia.org/search?q=tree", WebEngine.Ecosia.url("tree"))
    }

    @Test fun PAL_06_suggestionEndpointsAreTheDocumentedPublicOnesAndStartpageAndEcosiaHaveNone() {
        assertEquals("https://suggestqueries.google.com/complete/search?client=firefox&q=spi%26der", WebEngine.Google.suggestUrl("spi&der"))
        assertEquals("https://duckduckgo.com/ac/?q=spider&type=list", WebEngine.DuckDuckGo.suggestUrl("spider"))
        assertEquals("https://api.bing.com/osjson.aspx?query=spider", WebEngine.Bing.suggestUrl("spider"))
        assertEquals("https://search.brave.com/api/suggest?q=spider", WebEngine.Brave.suggestUrl("spider"))
        assertNull(WebEngine.Startpage.suggestUrl("spider")); assertNull(WebEngine.Ecosia.suggestUrl("spider"))
        assertEquals("https://api.duckduckgo.com/?q=a+b&format=json&no_html=1&skip_disambig=1", WebSuggestUrls.duckAnswer("a b"))
        assertEquals("https://market.android.com/suggest/SuggRequest?json=1&query=maps&hl=en&gl=us&c=3", WebSuggestUrls.playSuggest("maps"))
        WebEngine.entries.mapNotNull { it.suggestUrl("x y") }.plus(WebSuggestUrls.playSuggest("x y")).forEach { assertTrue(it.startsWith("https://")) }
    }

    @Test fun PAL_15_theMarketAndStoreUrlsEscapeTheQuery() {
        assertEquals("market://search?q=a+%26+b&c=apps", WebSearch.playStoreMarketUri("a & b"))
        assertEquals("https://play.google.com/store/search?q=a+%26+b&c=apps", WebSearch.playStoreUrl("a & b"))
    }

    // ---- parsers
    @Test fun PAL_06_parsesTheOpenSearchShapeOfGoogleBingAndBrave() {
        assertEquals(listOf("spider man", "spiderman", "spider"), WebSuggestParse.suggestions("""["spi",["spider man","spiderman","spider"]]"""))
        assertEquals(listOf("a"), WebSuggestParse.suggestions("""["q",["a"],[],{"google:suggestsubtypes":[[512]]}]"""))
    }

    @Test fun PAL_06_parsesDuckDuckGoListAndObjectShapesAndThePlayStoreShape() {
        assertEquals(listOf("spider", "spider-man"), WebSuggestParse.suggestions("""[{"phrase":"spider"},{"phrase":"spider-man"}]"""))
        assertEquals(listOf("maps", "maps go"), WebSuggestParse.suggestions("""[{"s":"maps","t":"q"},{"s":"maps go","t":"q"}]"""))
        assertEquals(listOf("x", "y"), WebSuggestParse.suggestions("""["x","y"]"""))
    }

    @Test fun PAL_06_malformedEmptyAndWrongShapedBodiesGiveNothingAndNeverThrow() {
        for (b in listOf(null, "", "   ", "not json", "{", "[", """{"a":1}""", "null", "[[[[", "[1,2,3]", "<html>blocked</html>"))
            assertEquals(b, emptyList<String>(), WebSuggestParse.suggestions(b))
        assertNull(WebSuggestParse.duckAnswer("garbage")); assertNull(WebSuggestParse.duckAnswer("[]")); assertNull(WebSuggestParse.duckAnswer(null))
    }

    @Test fun PAL_06_oversizedBodiesAreRefusedAndLongOrDuplicateItemsAreDropped() {
        val big = """["q",["a"]]""" + " ".repeat(WebSuggestParse.MAX_BYTES)
        assertEquals(emptyList<String>(), WebSuggestParse.suggestions(big))
        val many = (1..40).joinToString(",") { "\"item $it\"" }
        assertEquals(WebSuggestParse.MAX_ITEMS, WebSuggestParse.suggestions("""["q",[$many]]""").size)
        assertEquals(listOf("A", "b"), WebSuggestParse.suggestions("""["q",["A","a"," b ","","${"x".repeat(500)}"]]"""))
    }

    @Test fun PAL_06_parsesTheDuckDuckGoInstantAnswerAndKeepsOnlyHttpsSources() {
        val a = WebSuggestParse.duckAnswer("""{"Heading":"Spider","AbstractText":"Spiders are arachnids.","AbstractURL":"https://en.wikipedia.org/wiki/Spider","Image":"/i/x.png"}""")
        assertEquals(WebAnswer("Spider", "Spiders are arachnids.", "https://en.wikipedia.org/wiki/Spider"), a)
        assertNull(WebSuggestParse.duckAnswer("""{"Heading":"x","AbstractText":"y","AbstractURL":"http://insecure.example"}""")!!.url)
        assertNull(WebSuggestParse.duckAnswer("""{"Heading":"x","AbstractText":""}"""))
        assertEquals("4", WebSuggestParse.duckAnswer("""{"Answer":"4","AbstractText":""}""")!!.text)
        assertTrue(WebSuggestParse.duckAnswer("""{"AbstractText":"${"w ".repeat(600)}"}""")!!.text.length <= 280)
    }

    // ---- what may be sent
    @Test fun PAL_06_noQueryShorterThanTwoOrShapedLikeAUrlEmailOrPhoneIsEverSent() {
        for (t in listOf("", " ", "a", " b ", "example.com", "https://example.com/x", "www.example.org/a?b=c", "me@example.com", "john.doe@mail.example.org", "+1 (555) 123-4567", "555-1234", "0049 30 123456", "a".repeat(101)))
            assertFalse("\"$t\"", WebSuggestRules.eligible(t))
        for (t in listOf("sp", "spider man", "how to tie a tie", "2 plus 2", "cats @ home", "tour de france 2024")) assertTrue("\"$t\"", WebSuggestRules.eligible(t))
    }

    @Test fun PAL_06_anIneligibleQueryNeverReachesTheFetcherAndNeverWaits() = runBlocking {
        val f = FakeFetcher { """["q",["x"]]""" }
        var waited = 0L
        val s = WebSuggestSource(f, wait = { waited += it })
        for (t in listOf("a", "me@x.com", "example.com", "5551234")) { assertTrue(s.web(t, WebEngine.Google).isEmpty); assertTrue(s.play(t).isEmpty()) }
        assertEquals(emptyList<String>(), f.urls); assertEquals(0L, waited)
    }

    @Test fun PAL_06_anEngineWithoutAnEndpointMakesNoRequest() = runBlocking {
        val f = FakeFetcher { """["q",["x"]]""" }
        val s = WebSuggestSource(f, wait = {})
        assertTrue(s.web("spider", WebEngine.Startpage).isEmpty); assertTrue(s.web("spider", WebEngine.Ecosia).isEmpty)
        assertEquals(emptyList<String>(), f.urls)
    }

    // ---- the source: debounce, cancel, cache, silence
    @Test fun PAL_06_theRequestWaitsEightHundredMillisecondsOfFakeTimeAndOnlyTheTypedTextIsSent() = runBlocking {
        val log = ArrayList<String>()
        val f = FakeFetcher { log += "fetch"; """["spider",["spider man","Spider","spiders"]]""" }
        val s = WebSuggestSource(f, wait = { log += "wait $it" })
        val r = s.web("spider", WebEngine.Google)
        assertEquals(listOf("wait 800", "fetch"), log)
        assertEquals(listOf("spider man", "spiders"), r.suggestions) // the typed text itself is dropped: the top row is that search
        assertEquals(listOf("https://suggestqueries.google.com/complete/search?client=firefox&q=spider"), f.urls)
    }

    @Test fun PAL_06_duckDuckGoAlsoAsksForTheInstantAnswer() = runBlocking {
        val f = FakeFetcher { u -> if ("api.duckduckgo.com" in u) """{"Heading":"Spider","AbstractText":"Arachnid."}""" else """["spider",["spider man"]]""" }
        val r = WebSuggestSource(f, wait = {}).web("spider", WebEngine.DuckDuckGo)
        assertEquals(listOf("spider man"), r.suggestions); assertEquals("Arachnid.", r.answer?.text)
        assertEquals(2, f.urls.size)
    }

    @Test fun PAL_06_aNewKeystrokeCancelsTheWaitSoNothingIsSent() = runBlocking {
        val f = FakeFetcher { """["q",["x"]]""" }
        val s = WebSuggestSource(f, wait = { awaitCancellation() })
        val job = launch { s.web("spider", WebEngine.Google) }
        yield(); job.cancelAndJoin()
        assertTrue(job.isCancelled); assertEquals(emptyList<String>(), f.urls)
    }

    @Test fun PAL_06_closingRadarCancelsARequestInFlight() = runBlocking {
        val started = CompletableDeferred<Unit>(); var cancelled = false
        val s = WebSuggestSource({ _ -> started.complete(Unit); try { awaitCancellation() } finally { cancelled = true } }, wait = {})
        val job = launch { s.web("spider", WebEngine.Google) }
        started.await(); job.cancelAndJoin()
        assertTrue("the in-flight request was cancelled", cancelled)
    }

    @Test fun PAL_06_offlineOrBrokenAnswersAreSilentEmptyAndNotCached() = runBlocking {
        var online = false
        val f = FakeFetcher { if (online) """["q",["x1"]]""" else null }
        val s = WebSuggestSource(f, wait = {})
        assertTrue(s.web("spider", WebEngine.Google).isEmpty)
        assertEquals(0, s.cacheSize())
        online = true
        assertEquals(listOf("x1"), s.web("spider", WebEngine.Google).suggestions) // the failure was not remembered
        val thrower = WebSuggestSource({ _ -> throw IllegalStateException("boom") }, wait = {})
        assertTrue(thrower.web("spider", WebEngine.Google).isEmpty)
    }

    @Test fun PAL_06_theLastThirtyQueriesAreCachedInMemoryAndARepeatIsNotRefetched() = runBlocking {
        val f = FakeFetcher { """["q",["x"]]""" }
        var waited = 0
        val s = WebSuggestSource(f, wait = { waited++ })
        for (i in 1..35) s.web("query $i", WebEngine.Google)
        assertEquals(30, s.cacheSize())
        val before = f.urls.size
        s.web("Query 35", WebEngine.Google) // cached (case-insensitive): no request, no wait
        assertEquals(before, f.urls.size); assertEquals(35, waited)
        s.web("query 1", WebEngine.Google) // evicted: fetched again
        assertEquals(before + 1, f.urls.size)
        s.clear(); assertEquals(0, s.cacheSize())
    }

    @Test fun PAL_15_playSuggestionsGoToThePlayEndpointWithFixedLanguageAndCountry() = runBlocking {
        val f = FakeFetcher { """[{"s":"maps","t":"q"},{"s":"maps go","t":"q"}]""" }
        val s = WebSuggestSource(f, wait = {})
        assertEquals(listOf("maps go"), s.play("maps"))
        assertEquals(listOf("https://market.android.com/suggest/SuggRequest?json=1&query=maps&hl=en&gl=us&c=3"), f.urls)
    }

    // ---- hits
    @Test fun PAL_06_theLiveHitsOpenTheChosenEnginesPageAndCarryTheirOwnKinds() {
        val live = WebLive(listOf("spider man", "spiders"), WebAnswer("Spider", "Arachnid.", "https://en.wikipedia.org/wiki/Spider"), listOf("spider solitaire"))
        val web = WebHits.web(WebEngine.Brave, "spider", live)
        assertEquals(listOf(HitKind.WebAnswer, HitKind.WebSuggestion, HitKind.WebSuggestion), web.map { it.kind })
        assertEquals("https://search.brave.com/search?q=spider+man", web[1].uri)
        assertEquals("https://en.wikipedia.org/wiki/Spider", web[0].uri)
        assertTrue(web.all { it.category == SearchCategory.Web })
        val play = WebHits.play(live).single()
        assertEquals(HitKind.PlayStore, play.kind); assertEquals("market://search?q=spider+solitaire&c=apps", play.uri)
        assertNotNull(play.extras["https"])
    }

    @Test fun PAL_06_theWebSuggestionsSwitchIsOnByDefaultAndSurvivesARestart() {
        assertTrue(LauncherPrefs().webSuggestions)
        val sp = MapSharedPreferences()
        LauncherPrefsStore(sp).update { it.copy(webSuggestions = false, webEngine = WebEngine.Brave) }
        val back = LauncherPrefsStore(sp).prefs.value
        assertFalse(back.webSuggestions); assertEquals(WebEngine.Brave, back.webEngine)
        assertTrue(LauncherPrefsStore(MapSharedPreferences()).prefs.value.webSuggestions) // an older install has no key: the default applies
        // An older install that stored DuckDuckGo keeps it (only a fresh install defaults to Google).
        assertEquals(WebEngine.DuckDuckGo, LauncherPrefsStore(MapSharedPreferences(mapOf("webEngine" to "DuckDuckGo"))).prefs.value.webEngine)
    }
}
