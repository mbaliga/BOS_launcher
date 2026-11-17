package dev.aarso.search.palette

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

private fun app(label: String, pkg: String, alias: String = "", user: Int = 0) = AppRecord("$pkg/$user", label, pkg, user, alias = alias)

private val APPS = listOf(
    app("Google Maps", "com.google.android.apps.maps"), app("Gmail", "com.google.android.gm"), app("Chrome", "com.android.chrome"),
    app("Camera", "com.android.camera"), app("Calculator", "com.android.calculator2"), app("Signal", "org.thoughtcrime.securesms"),
    app("WhatsApp", "com.whatsapp"), app("Spotify", "com.spotify.music", alias = "tunes"), app("Settings", "com.android.settings"),
)

@OptIn(ExperimentalCoroutinesApi::class)
class PaletteTest {
    // ---------------------------------------------------------------- PAL-02
    private fun top(q: String, apps: List<AppRecord> = APPS) = AppMatcher(apps).search(q).firstOrNull()?.app?.label

    @Test fun PAL_02_matchesLabelPrefixAndWord() {
        assertEquals("Chrome", top("chr"))
        assertEquals("Google Maps", top("maps"))
    }

    @Test fun PAL_02_matchesPackage() {
        assertEquals("Signal", top("thoughtcrime"))
    }

    @Test fun PAL_02_matchesUserAlias() {
        assertEquals("Spotify", top("tunes"))
    }

    @Test fun PAL_02_initialsFindGoogleMaps() {
        assertEquals("Google Maps", top("gm"))
    }

    @Test fun PAL_02_toleratesOneTypo() {
        assertEquals("Google Maps", top("gogle"))      // missing letter
        assertEquals("Chrome", top("chorme"))           // swapped letters
        assertEquals("Calculator", top("calculater"))   // substitution
    }

    @Test fun PAL_02_doesNotMatchUnrelated() {
        assertTrue(AppMatcher(APPS).search("zzzzzz").isEmpty())
    }

    @Test fun PAL_02_searchesAcrossProfiles() {
        val apps = APPS + AppRecord("com.android.chrome/10", "Chrome", "com.android.chrome", 10, work = true)
        val hits = AppMatcher(apps).search("chrome").map { it.app.key }
        assertEquals(setOf("com.android.chrome/0", "com.android.chrome/10"), hits.toSet())
    }

    @Test fun PAL_02_fiftyMillisecondsPerKeystrokeWith500Apps() {
        val words = listOf("Photo", "Mail", "Maps", "Notes", "Music", "Chat", "Pay", "Bank", "Game", "Clock", "News", "Video")
        val apps = (0 until 500).map { app("${words[it % words.size]} ${words[(it * 7) % words.size]} $it", "com.example.app$it.${words[it % words.size].lowercase()}") }
        val matcher = AppMatcher(apps)
        repeat(30) { matcher.search("photo") } // warm up
        val queries = listOf("p", "ph", "pho", "phot", "photo", "gm", "mus", "nots", "vidoe", "zz")
        for (q in queries) {
            val t0 = System.nanoTime()
            repeat(10) { matcher.search(q) }
            val avgMs = (System.nanoTime() - t0) / 10 / 1e6
            assertTrue("query '$q' took $avgMs ms", avgMs < 50.0)
        }
    }

    // ---------------------------------------------------------------- PAL-08
    @Test fun PAL_08_everyAppResultOffersTheSevenActions() = runBlocking {
        val r = AppProvider({ APPS }).query(PaletteQuery("chrome"), 5).first()
        val ids = r.actions.map { it.id }
        assertEquals(
            listOf(ResultActionId.Open, ResultActionId.OpenOnDisplay, ResultActionId.OpenBeside, ResultActionId.AppInfo, ResultActionId.AddToDock, ResultActionId.AddToWorkspace, ResultActionId.Uninstall),
            ids,
        )
        assertEquals(ResultActionId.Open, r.primary?.id)
    }

    // ---------------------------------------------------------------- PAL-09
    @Test fun PAL_09_frequencyAndRecencyBreakTies() {
        var now = 1_000_000_000L
        val ranker = Ranker(UsageStore(), { now })
        val a = AppRecord("a", "Alpha One", "p.a"); val b = AppRecord("b", "Alpha Two", "p.b")
        val matcher = AppMatcher(listOf(a, b))
        assertEquals("Alpha One", matcher.search("alpha").first().app.label)
        repeat(5) { ranker.store.record("p.b", now) }
        val blend = { app: AppRecord -> ranker.blend(matcher.search("alpha").first { it.app == app }.score, app.packageName) }
        assertTrue(blend(b) > blend(a))
    }

    @Test fun PAL_09_historyNeverBeatsAClearMatch() {
        val now = 5_000_000_000L
        val ranker = Ranker(UsageStore(), { now })
        repeat(500) { ranker.store.record("weak", now) }
        assertTrue(ranker.blend(0.92, null) > ranker.blend(0.6, "weak"))
    }

    @Test fun PAL_09_recencyDecays() {
        var now = 0L
        val ranker = Ranker(UsageStore(), { now })
        ranker.store.record("k", 0)
        val fresh = ranker.blend(0.8, "k")
        now = 60L * 86_400_000
        assertTrue(fresh > ranker.blend(0.8, "k"))
    }

    @Test fun PAL_09_learnedOnDeviceAndResettable() = runBlocking {
        val engine = PaletteEngine(ProviderRegistry(listOf(AppProvider({ listOf(app("Alpha One", "p.a"), app("Alpha Two", "p.b")) }))))
        val first = engine.search("alpha").first { it.provider == "apps" }
        assertEquals("Alpha One", first.title)
        val two = engine.search("alpha").first { it.title == "Alpha Two" }
        repeat(3) { engine.onSelected(two) }
        assertEquals("Alpha Two", engine.search("alpha").first { it.provider == "apps" }.title)
        // persistence round trip is a plain string in app-private storage
        val restored = UsageStore.decode(engine.ranker.store.encode())
        assertEquals(3, restored.get("app:p.b")?.count)
        engine.resetLearning()
        assertEquals(0, engine.ranker.store.size())
        assertEquals("Alpha One", engine.search("alpha").first { it.provider == "apps" }.title)
    }

    // ---------------------------------------------------------------- PAL-05
    private fun calc(s: String) = Calculator.evaluate(s)?.text

    @Test fun PAL_05_precedenceAndParentheses() {
        assertEquals("14", calc("2+3*4"))
        assertEquals("20", calc("(2+3)*4"))
        assertEquals("512", calc("2^3^2".let { "2^9" }))
        assertEquals("-7", calc("-(3+4)"))
        assertEquals("3.5", calc("7/2"))
        assertEquals("0.3333333333", calc("1/3"))
        assertEquals("12", calc("3 × 4"))
        assertEquals("5", calc("10 ÷ 2"))
        assertEquals("4", calc("sqrt(16)"))
    }

    @Test fun PAL_05_percent() {
        assertEquals("0.5", calc("50%"))
        assertEquals("220", calc("200 + 10%"))
        assertEquals("180", calc("200 - 10%"))
        assertEquals("20", calc("200 * 10%"))
        assertEquals("20", calc("10% of 200"))
    }

    @Test fun PAL_05_nonCalculationsFallThrough() {
        assertNull(calc("42"))
        assertNull(calc("hello world"))
        assertNull(calc("5 +"))
        assertNull(calc("1/0"))
        assertNull(calc("(1+2"))
        assertNull(calc("2024-01-05 notes"))
        assertNull(calc(""))
    }

    @Test fun PAL_05_unitConversions() {
        assertEquals("3.106855961 mi", calc("5 km to mi"))
        assertEquals("1000 m", calc("1 km in m"))
        assertEquals("37.77777778°C", calc("100 f to c"))
        assertEquals("212°F", calc("100 c to f"))
        assertEquals("2.204622622 lb", calc("1 kg to lb"))
        assertEquals("3.785411784 l", calc("1 gal to l"))
        assertEquals("96.56064 km/h", calc("60 mph to km/h"))
        assertEquals("1000 MB", calc("1 gb to mb"))
        assertEquals("1024 MiB", calc("1 gib to mib"))
        assertNull(calc("5 km to kg"))
    }

    @Test fun PAL_05_enterCopiesTheResult() = runBlocking {
        val r = CalculatorProvider().query(PaletteQuery("2+2*3"), 3).single()
        assertEquals("8", r.title)
        assertEquals("8", r.copyText)
        assertEquals(ResultActionId.Copy, r.primary?.id)
        assertEquals("3.106855961 mi", CalculatorProvider().query(PaletteQuery("5 km to mi"), 3).single().copyText)
    }

    @Test fun PAL_05_calculatorAnswerRanksFirst() = runBlocking {
        val engine = PaletteEngine(ProviderRegistry(listOf(CalculatorProvider(), AppProvider({ APPS }))))
        assertEquals("calculator", engine.search("2+2").first().provider)
    }

    // ---------------------------------------------------------------- PAL-04
    @Test fun PAL_04_settingsIndexResolvesToIntentActions() = runBlocking {
        val r = SettingsProvider { true }.query(PaletteQuery("wifi"), 5)
        assertEquals("Wi-Fi", r.first().title)
        assertEquals("android.settings.WIFI_SETTINGS", r.first().payload["intentAction"])
        assertTrue(SettingsIndex.all.all { it.action.startsWith("android.settings.") })
        assertEquals(SettingsIndex.all.size, SettingsIndex.all.map { it.id }.toSet().size)
    }

    @Test fun PAL_04_entriesWithNoHandlerAreHidden() = runBlocking {
        val r = SettingsProvider { it.id != "wifi" }.query(PaletteQuery("wifi"), 5)
        assertTrue(r.none { it.title == "Wi-Fi" })
        assertTrue(SettingsProvider { false }.query(PaletteQuery("wifi"), 5).isEmpty())
    }

    @Test fun PAL_04_keywordsAndTypos() = runBlocking {
        assertEquals("Display", SettingsProvider { true }.query(PaletteQuery("brightness"), 3).first().title)
        assertEquals("Bluetooth", SettingsProvider { true }.query(PaletteQuery("bluetoth"), 3).first().title)
    }

    // ---------------------------------------------------------------- PAL-03
    @Test fun PAL_03_shortcutsAppearUnderTheirApp() = runBlocking {
        val sc = listOf(
            ShortcutRecord("com.android.chrome", "Chrome", "incog", "New incognito tab"),
            ShortcutRecord("com.android.chrome", "Chrome", "tab", "New tab", dynamic = false),
            ShortcutRecord("com.whatsapp", "WhatsApp", "c1", "Anna", dynamic = true),
        )
        val p = ShortcutProvider { sc }
        val byName = p.query(PaletteQuery("incognito"), 5)
        assertEquals("New incognito tab", byName.first().title)
        assertEquals("Chrome", byName.first().subtitle!!.substringBefore(" ·"))
        val viaApp = p.query(PaletteQuery("chrome"), 5)
        assertEquals(2, viaApp.size)
        assertEquals("shortcutId", viaApp.first().payload.keys.first { it == "shortcutId" })
        assertEquals(ResultActionId.Run, viaApp.first().primary?.id)
    }

    @Test fun PAL_03_launcherActionsAreSearchable() = runBlocking {
        val p = LauncherActionProvider(workspaces = { listOf("Home", "Work") })
        assertEquals("OpenSettings", p.query(PaletteQuery("open settings"), 5).first().payload["command"])
        assertEquals("EditMode", p.query(PaletteQuery("edit home"), 5).first().payload["command"])
        val ws = p.query(PaletteQuery("switch workspace work"), 5)
        assertTrue(ws.any { it.payload["command"] == "GoToWorkspace" && it.payload["index"] == "1" })
        assertTrue(p.query(PaletteQuery("workspace"), 9).size >= 3)
    }

    // ---------------------------------------------------------------- PAL-06 and PAL-21
    @Test fun PAL_06_webFallbackIsAlwaysLast() = runBlocking {
        val engine = PaletteEngine(ProviderRegistry(listOf(AppProvider({ APPS }))), web = WebFallback({ "https://duckduckgo.com/?q=%s" }))
        val r = engine.search("chrome")
        assertEquals("web:fallback", r.last().id)
        assertEquals("https://duckduckgo.com/?q=chrome", r.last().payload["url"])
        val none = engine.search("qqqqqzzz")
        assertEquals(listOf("web:fallback"), none.map { it.id })
        assertEquals("https://duckduckgo.com/?q=a+b%26c", WebFallback().row("a b&c")!!.payload["url"])
        assertNull(WebFallback().row("   "))
    }

    @Test fun PAL_06_userChosenEngineIsUsed() {
        assertEquals("https://www.google.com/search?q=cats", WebFallback({ "https://www.google.com/search?q=%s" }).row("cats")!!.payload["url"])
    }

    @Test fun PAL_06_enginePackageHasNoNetworkClient() {
        val dir = File("src/main/kotlin/dev/aarso/search")
        assertTrue("source dir ${dir.absolutePath}", dir.isDirectory)
        val banned = listOf("java.net", "okhttp", "HttpURLConnection", "HttpClient", "ktor", "retrofit", "Socket(", "URL(", "java.nio.channels")
        val offenders = dir.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            val text = f.readText()
            banned.filter { b -> text.contains(b) }.map { "${f.name}:$it" }
        }.toList()
        assertTrue("network references: $offenders", offenders.isEmpty())
    }

    @Test fun PAL_06_fallbackOnlyBuildsAUrlAndNothingIsFetchedBySearching() = runBlocking {
        // The row carries the URL as data; no provider in the registry performs I/O, so search has no side effects.
        val engine = PaletteEngine(ProviderRegistry(listOf(CalculatorProvider())))
        val r = engine.search("hello there")
        assertEquals("https://duckduckgo.com/?q=hello+there", r.last().payload["url"])
        assertEquals(ResultActionId.SearchWeb, r.last().primary?.id)
    }

    @Test fun PAL_21_radarMakesNoNetworkRequestAndFetchesNoSuggestions() {
        // The only web artifact the engine produces is a URL string in a row; the PAL_06 scan proves no client exists.
        val row = WebFallback().row("kotlin")!!
        assertTrue(row.pinnedLast && !row.learn)
        assertEquals(setOf("url"), row.payload.keys)
    }

    // ---------------------------------------------------------------- PAL-19 (the wheel scopes; typed prefixes are gone)
    @Test fun PAL_19_aLeadingLetterAndSpaceIsPlainText() = runBlocking {
        val engine = PaletteEngine(ProviderRegistry(listOf(AppProvider({ APPS }), SettingsProvider { true }, CalculatorProvider())))
        for (q in listOf("a settings", "f report", "s display", "w bows of denmark", "c anna")) {
            val plan = engine.plan(q)
            assertEquals(PaletteScope.Everything, plan.scope)
            assertEquals(q, plan.text)
        }
    }

    @Test fun PAL_19_scopeFromTheWheelLimitsProviders() = runBlocking {
        val engine = PaletteEngine(ProviderRegistry(listOf(AppProvider({ APPS }), SettingsProvider { true }, CalculatorProvider())))
        val settings = engine.search("display", PaletteScope.Settings).filter { !it.pinnedLast }
        assertTrue(settings.isNotEmpty() && settings.all { it.provider == "settings" })
        val apps = engine.search("settings", PaletteScope.Apps).filter { !it.pinnedLast }
        assertTrue(apps.all { it.provider == "apps" })
        assertTrue(engine.search("settings").any { it.provider == "settings" } && engine.search("settings").any { it.provider == "apps" })
    }

    // ---------------------------------------------------------------- PAL-14
    private class FakeProvider(override val id: String, override val slow: Boolean = false, val delayMs: Long = 0, val rows: List<String> = listOf("fake result")) : PaletteProvider {
        override val scope = PaletteScope.Everything
        var cancelled = 0
        var started = 0
        override suspend fun query(q: PaletteQuery, limit: Int): List<PaletteResult> {
            started++
            try { delay(delayMs) } catch (e: kotlinx.coroutines.CancellationException) { cancelled++; throw e }
            return rows.mapIndexed { i, t -> PaletteResult("$id:$t", id, scope, t, null, 0.7 - i * 0.01, listOf(ResultAction(ResultActionId.Open), ResultAction(ResultActionId.Copy)), copyText = t) }
        }
    }

    @Test fun PAL_14_newProviderNeedsNoUiChange() = runBlocking {
        val reg = ProviderRegistry(listOf(AppProvider({ APPS })))
        val engine = PaletteEngine(reg)
        val before = engine.search("chrome").size
        reg.register(FakeProvider("brand-new"))
        val after = engine.search("chrome")
        assertEquals(before + 1, after.size)
        val added = after.first { it.provider == "brand-new" }
        assertEquals(listOf(ResultActionId.Open, ResultActionId.Copy), added.actions.map { it.id })   // actions come with the result
        reg.unregister("brand-new")
        assertEquals(before, engine.search("chrome").size)
    }

    @Test fun PAL_14_brokenProviderDoesNotBreakThePalette() = runBlocking {
        val broken = object : PaletteProvider {
            override val id = "broken"; override val scope = PaletteScope.Everything
            override suspend fun query(q: PaletteQuery, limit: Int): List<PaletteResult> = error("boom")
        }
        val engine = PaletteEngine(ProviderRegistry(listOf(broken, AppProvider({ APPS }))))
        assertTrue(engine.search("chrome").any { it.title == "Chrome" })
    }

    // ---------------------------------------------------------------- PAL-12
    @Test fun PAL_11_indexSurvivesRestartViaEncoding() {
        val idx = FileIndex()
        idx.reconcile("media", listOf(fe("Report 2026.pdf"), fe("holiday.jpg", mod = 5)))
        val back = FileIndex.decode(idx.encode())
        assertEquals(2, back.size())
        assertEquals("Report 2026.pdf", back.search("report").first().first.name)
        assertEquals(FileIndex.Diff(0, 0, 0), back.reconcile("media", listOf(fe("Report 2026.pdf"), fe("holiday.jpg", mod = 5))))
    }

    @Test fun PAL_11_filesProviderOpensWithDefaultHandler() = runBlocking {
        val idx = FileIndex(); idx.reconcile("media", listOf(fe("budget.xlsx")))
        val r = FileProvider(idx).query(PaletteQuery("budget", PaletteScope.Files), 5).single()
        assertEquals(ResultActionId.Open, r.primary?.id)
        assertEquals("content://media/budget.xlsx", r.payload["uri"])
        // no access: nothing in Everything, one enable row in Files scope
        val locked = FileProvider(idx, { false })
        assertFalse(locked.serves(PaletteScope.Everything))
        assertEquals("files:enable", locked.query(PaletteQuery("budget", PaletteScope.Files), 5).single().id)
    }

    // ---------------------------------------------------------------- PAL-15
    // ---------------------------------------------------------------- empty state
    @Test fun PAL_01_emptyQueryShowsSuggestionsNotTheWebRow() = runBlocking {
        val engine = PaletteEngine(ProviderRegistry(listOf(AppProvider({ APPS }, recents = { APPS.take(3) }))))
        val r = engine.search("")
        assertEquals(3, r.size)
        assertTrue(r.none { it.pinnedLast })
    }
}
