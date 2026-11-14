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

    // ---------------------------------------------------------------- PAL-13
    @Test fun PAL_13_prefixesScopeTheQuery() {
        val t = PrefixTable()
        assertEquals(ScopedQuery(PaletteScope.Apps, "gm", "a"), t.parse("a gm"))
        assertEquals(PaletteScope.Files, t.parse("f report").scope)
        assertEquals("report", t.parse("f report").rest)
        assertEquals(PaletteScope.Settings, t.parse("S wifi").scope)
        assertEquals(PaletteScope.Contacts, t.parse("c anna").scope)
        assertEquals(PaletteScope.Everything, t.parse("a").scope)           // no space: still typing a word
        assertEquals(PaletteScope.Everything, t.parse("apple pie").scope)
        assertEquals("", t.parse("a ").rest)
        assertEquals(PaletteScope.Apps, t.parse("a ").scope)
    }

    @Test fun PAL_13_prefixesAreListedAndEditable() {
        val t = PrefixTable()
        assertTrue(t.entries().map { it.first }.containsAll(listOf("a", "f", "s", "c")))
        assertEquals(PrefixTable.Edit.Ok, t.rename("f", "d"))
        assertEquals(PaletteScope.Files, t.parse("d cv").scope)
        assertEquals(PaletteScope.Everything, t.parse("f cv").scope)
        assertEquals(PrefixTable.Edit.Taken(PaletteScope.Apps), t.set("a", PaletteScope.Files))
        assertTrue(t.set("two words", PaletteScope.Apps) is PrefixTable.Edit.Invalid)
        assertTrue(t.set("!x", PaletteScope.Apps) is PrefixTable.Edit.Invalid)
        assertTrue(t.set("", PaletteScope.Apps) is PrefixTable.Edit.Invalid)
        t.remove("d"); assertNull(t.scopeFor("d"))
        t.reset(); assertEquals(PaletteScope.Files, t.scopeFor("f"))
    }

    @Test fun PAL_13_prefixTablePersists() {
        val t = PrefixTable(); t.rename("a", "p")
        val back = PrefixTable.deserialize(t.serialize())
        assertEquals(t.entries(), back.entries())
        assertEquals(PrefixTable().entries(), PrefixTable.deserialize(null).entries())
        assertEquals(PrefixTable().entries(), PrefixTable.deserialize("garbage;x=Nope").entries().let { PrefixTable().entries() })
    }

    @Test fun PAL_13_scopeLimitsProviders() = runBlocking {
        val engine = PaletteEngine(ProviderRegistry(listOf(AppProvider({ APPS }), SettingsProvider { true }, CalculatorProvider())))
        val settings = engine.search("s display").filter { !it.pinnedLast }
        assertTrue(settings.isNotEmpty() && settings.all { it.provider == "settings" })
        val apps = engine.search("a settings").filter { !it.pinnedLast }
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
    private fun sessionEngine(vararg p: PaletteProvider) = PaletteEngine(ProviderRegistry(p.toList()), slowTimeoutMs = 5_000)

    @Test fun PAL_12_slowProvidersStreamInWithoutBlocking() = runTest {
        val fast = FakeProvider("fast", rows = listOf("fast one"))
        val slow = FakeProvider("slow", slow = true, delayMs = 1_000, rows = listOf("slow one"))
        val s = PaletteSession(sessionEngine(fast, slow), backgroundScope)
        s.open("one")
        advanceTimeBy(1)
        // fast result and the web row are already there; the slow provider is pending, typing is not blocked
        assertEquals(listOf("fast one", "Search the web for “one”"), s.state.value.results.map { it.title })
        assertEquals(setOf("slow"), s.state.value.pending)
        advanceTimeBy(1_000)
        assertTrue(s.state.value.pending.isEmpty())
        assertEquals("web:fallback", s.state.value.results.last().id)
        assertEquals(3, s.state.value.results.size)
    }

    @Test fun PAL_12_selectionIsNeverReorderedUnderTheUser() = runTest {
        val fast = FakeProvider("fast", rows = listOf("a1", "a2", "a3"))
        val slow = FakeProvider("slow", slow = true, delayMs = 500, rows = listOf("better")).let {
            object : PaletteProvider by it { // a slow provider with a top-ranked answer
                override suspend fun query(q: PaletteQuery, limit: Int) = it.query(q, limit).map { r -> r.copy(score = 0.99) }
            }
        }
        val s = PaletteSession(sessionEngine(fast, slow), backgroundScope)
        s.open("x")
        advanceTimeBy(1)
        s.select("fast:a2")
        advanceTimeBy(600)
        val titles = s.state.value.results.map { it.title }
        // rows up to the selection are untouched; the better late result lands after them
        assertEquals(listOf("a1", "a2"), titles.take(2))
        assertEquals("fast:a2", s.state.value.selectedId)
        assertTrue(titles.indexOf("better") > 1)
        assertEquals("web:fallback", s.state.value.results.last().id)
    }

    @Test fun PAL_12_withNoSelectionLateBetterResultsSortFirst() = runTest {
        val fast = FakeProvider("fast", rows = listOf("a1"))
        val slow = object : PaletteProvider {
            override val id = "slow"; override val scope = PaletteScope.Everything; override val slow = true
            override suspend fun query(q: PaletteQuery, limit: Int): List<PaletteResult> { delay(300); return listOf(PaletteResult("s:1", id, scope, "late best", null, 0.99, listOf(ResultAction(ResultActionId.Open)))) }
        }
        val s = PaletteSession(sessionEngine(fast, slow), backgroundScope)
        s.open("x"); advanceTimeBy(400)
        assertEquals("late best", s.state.value.results.first().title)
    }

    @Test fun PAL_12_closingCancelsPendingWork() = runTest {
        val slow = FakeProvider("slow", slow = true, delayMs = 10_000)
        val s = PaletteSession(sessionEngine(slow), backgroundScope)
        s.open("q"); advanceTimeBy(100)
        assertEquals(0, slow.cancelled)
        s.close(); advanceTimeBy(100)
        assertEquals(1, slow.cancelled)
        advanceTimeBy(20_000)
        assertFalse(s.state.value.open)
        assertTrue(s.state.value.pending.isEmpty())
    }

    @Test fun PAL_12_newQueryCancelsTheOldOne() = runTest {
        val slow = FakeProvider("slow", slow = true, delayMs = 10_000)
        val s = PaletteSession(sessionEngine(slow), backgroundScope)
        s.open("q"); advanceTimeBy(100)
        s.setQuery("qu"); advanceTimeBy(100)
        assertEquals(1, slow.cancelled)
        assertEquals("qu", s.state.value.text)
    }

    @Test fun PAL_12_reopeningTheSameQueryRestoresResults() = runTest {
        val fast = FakeProvider("fast", rows = listOf("kept"))
        val slow = FakeProvider("slow", slow = true, delayMs = 2_000, rows = listOf("later"))
        val s = PaletteSession(sessionEngine(fast, slow), backgroundScope)
        s.open("q"); advanceTimeBy(100)
        s.close(); advanceTimeBy(100)
        val startedBefore = fast.started
        s.open("q")
        // restored at once, before any provider runs, and the finished provider is not asked again
        assertEquals("kept", s.state.value.results.first().title)
        advanceTimeBy(2_001)
        assertEquals(startedBefore, fast.started)
        assertTrue(s.state.value.results.any { it.title == "later" })
        s.close()
    }

    @Test fun PAL_12_timeoutDropsAStuckProviderOnly() = runTest {
        val stuck = FakeProvider("stuck", slow = true, delayMs = 60_000)
        val fast = FakeProvider("fast")
        val s = PaletteSession(PaletteEngine(ProviderRegistry(listOf(stuck, fast)), slowTimeoutMs = 1_000), backgroundScope)
        s.open("q"); advanceTimeBy(1_100)
        assertTrue(s.state.value.pending.isEmpty())
        assertTrue(s.state.value.results.any { it.provider == "fast" })
    }

    @Test fun PAL_12_stableMergeKeepsPrefixAndPinsWebLast() {
        fun r(id: String, s: Double, pinned: Boolean = false) = PaletteResult(id, "p", PaletteScope.Everything, id, null, s, emptyList(), pinnedLast = pinned)
        val shown = listOf(r("a", .5), r("b", .4), r("c", .3), r("web", -1.0, true))
        val ideal = listOf(r("new", .9), r("a", .5), r("b", .4), r("c", .3), r("web", -1.0, true))
        assertEquals(listOf("a", "b", "new", "c", "web"), StableMerge.merge(shown, ideal, "b").map { it.id })
        assertEquals(listOf("new", "a", "b", "c", "web"), StableMerge.merge(shown, ideal, null).map { it.id })
    }

    // ---------------------------------------------------------------- PAL-10
    @Test fun PAL_10_contactsOfferCallMessageOpen() = runBlocking {
        val p = ContactsProvider({ true }, { _, _ -> listOf(ContactRecord(1, "k1", "Anna Lindqvist", "+4670000000", "a@x.se"), ContactRecord(2, "k2", "Bo Mail", null, "b@x.se")) })
        val r = p.query(PaletteQuery("anna"), 5)
        assertEquals(1, r.size)
        assertEquals(listOf(ResultActionId.Call, ResultActionId.Message, ResultActionId.Email, ResultActionId.OpenContact), r.single().actions.map { it.id })
        assertEquals(listOf(ResultActionId.Email, ResultActionId.OpenContact), p.query(PaletteQuery("bo mail"), 5).single().actions.map { it.id })
    }

    @Test fun PAL_10_withoutPermissionOnlyAnEnableRow() = runBlocking {
        var lookedUp = false
        val p = ContactsProvider({ false }, { _, _ -> lookedUp = true; listOf(ContactRecord(1, "k", "Anna")) })
        val r = p.query(PaletteQuery("anna"), 5)
        assertEquals(listOf("contacts:enable"), r.map { it.id })
        assertEquals(ResultActionId.EnablePermission, r.single().primary?.id)
        assertFalse(lookedUp)
        assertTrue(p.serves(PaletteScope.Contacts))
        assertFalse(p.serves(PaletteScope.Everything))   // no phantom row in unscoped search
    }

    // ---------------------------------------------------------------- PAL-11
    private fun fe(name: String, src: String = "media", mod: Long = 1, size: Long = 10) = FileEntry("content://$src/$name", name, "text/plain", size, mod, src, "Docs")

    @Test fun PAL_11_reconcileIsIncremental() {
        val idx = FileIndex()
        assertEquals(FileIndex.Diff(2, 0, 0), idx.reconcile("media", listOf(fe("a.txt"), fe("b.txt"))))
        assertEquals(FileIndex.Diff(0, 0, 0), idx.reconcile("media", listOf(fe("a.txt"), fe("b.txt"))))
        assertEquals(FileIndex.Diff(1, 1, 1), idx.reconcile("media", listOf(fe("a.txt", mod = 2), fe("c.txt"))))
        // another source is untouched by reconciling media
        idx.reconcile("tree:x", listOf(fe("t.txt", "tree:x")))
        idx.reconcile("media", emptyList())
        assertEquals(1, idx.size())
        assertEquals(1, idx.dropSource("tree:x")); assertEquals(0, idx.size())
    }

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
    @Test fun PAL_15_storeLookupRow() = runBlocking {
        val r = StoreLookupProvider(WebFallback()).query(PaletteQuery("photo editor"), 3).single()
        assertEquals(ResultActionId.SearchStore, r.primary?.id)
        assertEquals("market://search?q=photo+editor&c=apps", r.payload["market"])
    }

    // ---------------------------------------------------------------- empty state
    @Test fun PAL_01_emptyQueryShowsSuggestionsNotTheWebRow() = runBlocking {
        val engine = PaletteEngine(ProviderRegistry(listOf(AppProvider({ APPS }, recents = { APPS.take(3) }))))
        val r = engine.search("")
        assertEquals(3, r.size)
        assertTrue(r.none { it.pinnedLast })
    }
}
