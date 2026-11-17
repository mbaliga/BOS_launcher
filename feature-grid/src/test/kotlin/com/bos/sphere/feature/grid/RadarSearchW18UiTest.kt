package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.SearchLayout
import org.junit.Assert.assertEquals
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
 * Wave 18 (owner request on build 49): Radar's query line is at the top in every layout and both orientations, a plain
 * query simply searches (no "letter, space, query" prefix and no bang list), and the Orbit rings are tiers of use.
 * Screenshots go to `build/screenshots/w18-*.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RadarSearchW18UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun english() { com.bos.sphere.core.design.L10n.setLanguage("en") }

    private val apps = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India").map { n ->
        AppEntry("demo.$n", ComponentName("demo.$n", "demo.$n.Main"), n, Process.myUserHandle()) { null }
    }

    private fun open(layout: SearchLayout, query: String = "", withHistory: Boolean = true, motion: Boolean = false, pinned: Boolean = true) {
        rule.mainClock.autoAdvance = false // the Orbit runs a frame loop that never idles
        rule.setContent {
            SearchScreen(
                apps = apps, recent = apps.take(9), prefs = LauncherPrefs(searchMotion = motion, searchLayout = layout, searchLayoutPinned = pinned), contacts = null,
                onLaunch = {}, onLongPressApp = {}, onOpenIntent = {}, onRequestContactsPermission = {}, onDismiss = {},
                hub = null, onOpenHit = null, onRunAction = {}, onRequestFilePermission = {}, sounds = null, initialQuery = query,
                launchHistory = if (withHistory) RadarTestHabits.history(apps) else emptyList(), learnedOverride = emptyMap(), nowMillis = { RadarTestHabits.NOW },
            )
        }
        rule.mainClock.advanceTimeBy(1_000)
    }

    private fun fieldBounds() = rule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
    private fun rootHeight() = rule.onRoot().fetchSemanticsNode().boundsInRoot.height

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(600)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ---- (2) the query line is at the top, everywhere

    private fun assertFieldAtTop(layout: SearchLayout, query: String) {
        open(layout, query)
        val f = fieldBounds()
        val h = rootHeight()
        assertTrue("the field is in the top fifth: top=${f.top} of $h", f.top >= 0f && f.bottom < h * 0.2f)
        rule.onNode(hasSetTextAction()).assertIsFocused() // keyboard focus lands in the field on open
        // Results (when there are any) sit below the field, never above it.
        val below = rule.onAllNodes(SemanticsMatcher("a result tile") { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() == "Alpha" })
        below.fetchSemanticsNodes().forEach { assertTrue("result below the field", it.boundsInRoot.top >= f.bottom - 1f) }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun INP_12_orbitQueryLineIsAtTheTopInPortrait() = assertFieldAtTop(SearchLayout.Orbit, "")

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun INP_12_orbitQueryLineIsAtTheTopInLandscape() = assertFieldAtTop(SearchLayout.Orbit, "")

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun INP_12_filmstripQueryLineIsAtTheTopInPortrait() = assertFieldAtTop(SearchLayout.Filmstrip, "")

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun INP_12_filmstripQueryLineIsAtTheTopInLandscape() = assertFieldAtTop(SearchLayout.Filmstrip, "")

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun INP_12_flatQueryLineIsAtTheTopInPortrait() = assertFieldAtTop(SearchLayout.Flat, "al")

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun INP_12_flatQueryLineIsAtTheTopInLandscape() = assertFieldAtTop(SearchLayout.Flat, "al")

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun INP_12_typingInOrbitKeepsTheQueryLineAtTheTop() { open(SearchLayout.Orbit, "br"); assertTrue(fieldBounds().bottom < rootHeight() * 0.2f) }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun INP_12_typingInFilmstripKeepsTheQueryLineAtTheTop() { open(SearchLayout.Filmstrip, "br"); assertTrue(fieldBounds().bottom < rootHeight() * 0.2f) }

    // ---- (1) a plain query simply searches

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_06_aLeadingLetterAndSpaceIsPlainTextAndTheWebRowsStay() {
        open(SearchLayout.Flat, "w bows of denmark")
        rule.onNodeWithText("Search DuckDuckGo for “w bows of denmark”", substring = false).assertExists()
        rule.onNodeWithText("Search Play Store for “w bows of denmark”", substring = false).assertExists()
        for (gone in listOf("!g", "Google Images", "!yt", "YouTube", "Wikipedia", "Try")) {
            assertEquals("no '$gone' chip", 0, rule.onAllNodesWithText(gone, substring = true).fetchSemanticsNodes().size)
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_13_removed_theEmptyStateShowsNoPrefixChips() {
        open(SearchLayout.Flat, "")
        assertEquals(0, rule.onAllNodesWithText("Start with a letter", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("Apps", substring = false).fetchSemanticsNodes().filter { it.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick) }.size)
    }

    // ---- (3) the Orbit rings are tiers of use

    private fun orbitTiles() = rule.onAllNodes(SemanticsMatcher("has traversal index") { it.config.contains(SemanticsProperties.TraversalIndex) }).fetchSemanticsNodes()
        .sortedBy { it.config[SemanticsProperties.TraversalIndex] }
        .map { (it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull().orEmpty()) to (it.config.getOrNull(SemanticsProperties.StateDescription).orEmpty()) }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_18_orbitInnermostRingHoldsTheMostUsedApps() {
        open(SearchLayout.Orbit, motion = true)
        val tiles = orbitTiles()
        assertEquals("every app has a slot while the rings move", apps.map { it.label }, tiles.map { it.first })
        // Use order Alpha > Bravo > ... so the first five are on the "Most used" ring, the next four on "Often used".
        assertEquals(List(5) { "Most used" } + List(4) { "Often used" }, tiles.map { it.second })
        val headings = rule.onAllNodes(SemanticsMatcher("ring heading") { it.config.contains(SemanticsProperties.Heading) }).fetchSemanticsNodes()
            .map { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }
        assertEquals(listOf("Most used", "Often used"), headings)
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun PAL_18_orbitTiersAlsoHoldInLandscapeAndNeverGoBackToAnInnerRing() {
        open(SearchLayout.Orbit, motion = false)
        val tiles = orbitTiles()
        assertTrue("some tiles are drawn", tiles.isNotEmpty())
        val order = listOf("Most used", "Often used", "Sometimes", "Rarely")
        val idx = tiles.map { order.indexOf(it.second) }
        assertTrue(idx.all { it >= 0 })
        assertEquals(idx.sorted(), idx)
        // Rank order is use order: what is drawn is a prefix of Alpha, Bravo, ...
        assertEquals(apps.take(tiles.size).map { it.label }, tiles.map { it.first })
    }

    private fun assertHint(layout: SearchLayout) {
        open(layout, withHistory = false)
        rule.onNodeWithText("Rings fill up as you use Radar").assertExists()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_18_emptyHistoryShowsTheQuietHintInOrbit() = assertHint(SearchLayout.Orbit)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_18_emptyHistoryShowsTheQuietHintInFilmstrip() = assertHint(SearchLayout.Filmstrip)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_18_emptyHistoryShowsTheQuietHintInFlat() = assertHint(SearchLayout.Flat)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_18_flatAtRestListsTheMostUsedAppsFirst() {
        open(SearchLayout.Flat)
        val tops = apps.map { a -> a.label to rule.onAllNodesWithText(a.label).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top }
        assertTrue("all apps listed", tops.all { it.second != null })
        assertEquals(tops.sortedBy { it.second }.map { it.first }, tops.map { it.first })
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_18_filmstripAtRestStartsWithTheMostUsedApp() {
        open(SearchLayout.Filmstrip)
        val names = rule.onAllNodes(SemanticsMatcher("named tile") { it.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick) && it.config.contains(SemanticsProperties.ContentDescription) })
            .fetchSemanticsNodes().map { it.config[SemanticsProperties.ContentDescription].first() }
        assertEquals("Alpha", names.first())
    }

    // ---- screenshots: Orbit in portrait and landscape, dark and light; Flat and Filmstrip at rest

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun shot_orbitPortraitDark() { open(SearchLayout.Orbit, motion = false); shoot("w18-radar-orbit-portrait-dark") }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-notnight-xhdpi")
    fun shot_orbitPortraitLight() { open(SearchLayout.Orbit, motion = false); shoot("w18-radar-orbit-portrait-light") }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi")
    fun shot_orbitLandscapeDark() { open(SearchLayout.Orbit, motion = false); shoot("w18-radar-orbit-landscape-dark") }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-notnight-xhdpi")
    fun shot_orbitLandscapeLight() { open(SearchLayout.Orbit, motion = false); shoot("w18-radar-orbit-landscape-light") }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun shot_orbitPortraitMoving() { open(SearchLayout.Orbit, motion = true); shoot("w18-radar-orbit-portrait-moving") }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun shot_flatTypedWebQuery() { open(SearchLayout.Flat, "w bows of denmark"); shoot("w18-radar-flat-typed-portrait") }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun shot_flatRestingPortrait() { open(SearchLayout.Flat); shoot("w18-radar-flat-resting-portrait") }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun shot_filmstripPortrait() { open(SearchLayout.Filmstrip); shoot("w18-radar-filmstrip-portrait") }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun shot_orbitEmptyHistory() { open(SearchLayout.Orbit, withHistory = false); shoot("w18-radar-orbit-empty-portrait") }
}
