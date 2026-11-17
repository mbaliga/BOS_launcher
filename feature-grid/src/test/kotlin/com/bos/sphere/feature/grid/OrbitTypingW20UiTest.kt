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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
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
 * Wave 20: the Orbit stays on screen while typing, the best match on the innermost ring (PAL-18, PAL-07, PAL-12, PAL-09, PAL-20).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OrbitTypingW20UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun english() { com.bos.sphere.core.design.L10n.setLanguage("en") }

    private val apps = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India").map { n ->
        AppEntry("demo.$n", ComponentName("demo.$n", "demo.$n.Main"), n, Process.myUserHandle()) { null }
    }

    private fun open(layout: SearchLayout, query: String) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SearchScreen(
                apps = apps, recent = apps.take(9), prefs = LauncherPrefs(searchMotion = false, searchLayout = layout, searchLayoutPinned = true), contacts = null,
                onLaunch = {}, onLongPressApp = {}, onOpenIntent = {}, onRequestContactsPermission = {}, onDismiss = {},
                hub = null, onOpenHit = null, onRunAction = {}, onRequestFilePermission = {}, sounds = null, initialQuery = query,
                launchHistory = RadarTestHabits.history(apps), learnedOverride = emptyMap(), nowMillis = { RadarTestHabits.NOW },
            )
        }
        rule.mainClock.advanceTimeBy(1_000)
    }

    private fun orbitTiles() = rule.onAllNodes(SemanticsMatcher("has traversal index") { it.config.contains(SemanticsProperties.TraversalIndex) }).fetchSemanticsNodes()
        .sortedBy { it.config[SemanticsProperties.TraversalIndex] }
        .map { (it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull().orEmpty()) to (it.config.getOrNull(SemanticsProperties.StateDescription).orEmpty()) }

    @Test @Config(sdk = [35]) fun PAL_18_flatListRuleOnlyOrbitStaysWhileTyping() {
        for (typing in listOf(false, true)) {
            assertEquals(true, RadarLayoutPolicy.showsFlatList(SearchLayout.Flat, typing))
            assertEquals(false, RadarLayoutPolicy.showsFlatList(SearchLayout.Orbit, typing))
        }
        assertEquals(false, RadarLayoutPolicy.showsFlatList(SearchLayout.Filmstrip, false))
        assertEquals(true, RadarLayoutPolicy.showsFlatList(SearchLayout.Filmstrip, true))
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_18_orbitStaysOnScreenWhileTypingWithTheBestMatchInTheInnermostRing() {
        open(SearchLayout.Orbit, "bra")
        val tiles = orbitTiles()
        assertTrue("typed results are drawn on the rings", tiles.isNotEmpty())
        assertEquals("Bravo", tiles.first().first)
        assertEquals("Most used", tiles.first().second)
        // The Flat list's section header is not drawn: the Orbit is what stays.
        assertEquals(0, rule.onAllNodesWithText("Applications", substring = false).fetchSemanticsNodes().size)
        val headings = rule.onAllNodes(SemanticsMatcher("ring heading") { it.config.contains(SemanticsProperties.Heading) }).fetchSemanticsNodes()
            .map { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }
        assertEquals("Most used", headings.first())
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_07_typedOrbitRingOrderIsTheTileOrderTheKeysWalk() {
        open(SearchLayout.Orbit, "a") // Alpha, Bravo, Charlie, Delta, India ... all contain an a
        val names = orbitTiles().map { it.first }
        val order = listOf("Most used", "Often used", "Sometimes", "Rarely")
        val tiers = orbitTiles().map { order.indexOf(it.second) }
        assertTrue(names.isNotEmpty())
        assertEquals("never back to an inner ring", tiers.sorted(), tiers)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_12_orbitKeepsTheQueryLineAtTheTopAndAClearButtonWhileTyping() {
        open(SearchLayout.Orbit, "bra")
        rule.onNode(hasSetTextAction()).assertExists()
        rule.onNodeWithContentDescription("Clear").assertExists()
        assertEquals(0, rule.onAllNodesWithContentDescription("Close").fetchSemanticsNodes().size)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_20_orbitTileLongPressStillOpensThePreviewWhileTyping() {
        open(SearchLayout.Orbit, "bra")
        rule.onNodeWithContentDescription("Bravo").performTouchInput { longClick() }
        rule.mainClock.advanceTimeBy(600)
        assertTrue(rule.onAllNodesWithText("Bravo").fetchSemanticsNodes().isNotEmpty())
    }
}
