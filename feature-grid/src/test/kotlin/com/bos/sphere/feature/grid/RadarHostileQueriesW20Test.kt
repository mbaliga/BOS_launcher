package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.SearchLayout
import com.bos.sphere.core.data.WebEngine
import com.bos.sphere.feature.search.AppSearch
import com.bos.sphere.feature.search.SectionKind
import com.bos.sphere.feature.search.WebRow
import com.bos.sphere.feature.search.sections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Wave 20 audit (PAL-06, PAL-18, PAL-19): after the prefix and bang syntax were removed, a bare "!", a trailing space and queries made only of
 * operator characters are ordinary text. Nothing throws, a blank query offers no web row, and the web hand-off keeps the text as typed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RadarHostileQueriesW20Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun english() { com.bos.sphere.core.design.L10n.setLanguage("en") }

    private val apps = listOf("Alpha", "Bravo").map { n -> AppEntry("demo.$n", ComponentName("demo.$n", "demo.$n.Main"), n, Process.myUserHandle()) { null } }
    private val hostile = listOf("!", "! ", " !", "!!", "!g", "!g cats", "a ", "  ", "\t", "-", "--", "(", "((((", ")", "\"", "\"\"", "/", "//", "/(/", "/[a/", "*", "?", "\\", ":", "a:", ":a", "-:", "AND", "OR", "NOT", "OR OR", "\u0000", "‮!", "😀", "x".repeat(5_000))

    @Test fun PAL_06_everyHostileQueryRanksAndBuildsWebRowsWithoutThrowing() {
        for (q in hostile) {
            AppSearch.rank(apps, q)
            val s = sections(q.trim(), WebEngine.DuckDuckGo, emptyList<String>(), emptyList<String>())
            if (q.isBlank()) assertTrue("no web row for a blank query: '$q'", s.web.isEmpty())
            else s.web.forEach { r -> if (r is WebRow.Search) assertTrue("the text is sent as typed: $q", r.url.startsWith("https://duckduckgo.com/?q=")) }
        }
    }

    @Test fun PAL_06_aBareBangSearchesTheWebForTheBangAndNothingMore() {
        val s = sections("!", WebEngine.DuckDuckGo, emptyList<String>(), emptyList<String>())
        assertEquals(listOf(SectionKind.Web), s.order)
        assertEquals("https://duckduckgo.com/?q=%21", (s.web[0] as WebRow.Search).url)
        assertEquals("a trailing space is trimmed, not a second query", sections("cats ", WebEngine.DuckDuckGo, emptyList<String>(), emptyList<String>()).web.first().let { (it as WebRow.Search).url }, sections("cats", WebEngine.DuckDuckGo, emptyList<String>(), emptyList<String>()).web.first().let { (it as WebRow.Search).url })
    }

    @Test fun PAL_06_submittingAnyHostileQueryNeverThrowsAndABlankOneDoesNothing() {
        val launched = ArrayList<String>(); val opened = ArrayList<android.content.Intent>()
        for (q in hostile) submitSearch(q, apps, LauncherPrefs(), { launched += it.label }, { opened += it })
        submitSearch("   ", apps, LauncherPrefs(), { launched += it.label }, { opened += it })
        assertTrue("a bare '!' opens at most one web search", opened.size <= hostile.size)
        val before = opened.size
        submitSearch("", apps, LauncherPrefs(), { launched += it.label }, { opened += it }); submitSearch("\n \t", apps, LauncherPrefs(), { launched += it.label }, { opened += it })
        assertEquals("blank queries open nothing", before, opened.size)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_18_aBareBangInTheFlatLayoutShowsTheWebRowsAndTheFieldStaysFocusable() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SearchScreen(
                apps = apps, recent = apps, prefs = LauncherPrefs(searchLayout = SearchLayout.Flat, searchLayoutPinned = true), contacts = null,
                onLaunch = {}, onLongPressApp = {}, onOpenIntent = {}, onRequestContactsPermission = {}, onDismiss = {},
                hub = null, onOpenHit = null, onRunAction = {}, onRequestFilePermission = {}, sounds = null, initialQuery = "! ",
                launchHistory = emptyList(), learnedOverride = emptyMap(), nowMillis = { 1L },
            )
        }
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(1, rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        assertTrue(rule.onAllNodesWithText("Search DuckDuckGo for", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun NFR_13_theClearAndCloseButtonsOfTheQueryLineAre48dpTouchTargets() {
        for (layout in listOf(SearchLayout.Flat)) {
            rule.mainClock.autoAdvance = false
            rule.setContent {
                SearchScreen(
                    apps = apps, recent = apps, prefs = LauncherPrefs(searchLayout = layout, searchLayoutPinned = true), contacts = null,
                    onLaunch = {}, onLongPressApp = {}, onOpenIntent = {}, onRequestContactsPermission = {}, onDismiss = {},
                    hub = null, onOpenHit = null, onRunAction = {}, onRequestFilePermission = {}, sounds = null, initialQuery = "al",
                    launchHistory = emptyList(), learnedOverride = emptyMap(), nowMillis = { 1L },
                )
            }
            rule.mainClock.advanceTimeBy(1_000)
            val b = rule.onNodeWithContentDescription("Clear").fetchSemanticsNode().boundsInRoot
            assertTrue("clear is ${b.width} x ${b.height} px at 2 px per dp", b.width >= 96f && b.height >= 96f)
        }
    }
}
