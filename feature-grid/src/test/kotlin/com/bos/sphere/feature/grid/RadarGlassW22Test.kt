package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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

/** Wave 22 (glassFix audit): the wheel never meets the mic strip half drawn, Close in Flat, and the order TalkBack walks Radar in. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RadarGlassW22Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun english() { com.bos.sphere.core.design.L10n.setLanguage("en") }

    private val apps = listOf("Alpha", "Bravo", "Charlie", "Delta").map { n -> AppEntry("demo.$n", ComponentName("demo.$n", "demo.$n.Main"), n, Process.myUserHandle()) { null } }

    private fun open(layout: SearchLayout, query: String = "", onDismiss: () -> Unit = {}) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SearchScreen(
                apps = apps, recent = apps, prefs = LauncherPrefs(searchMotion = false, searchLayout = layout, searchLayoutPinned = true), contacts = null,
                onLaunch = {}, onLongPressApp = {}, onOpenIntent = {}, onRequestContactsPermission = {}, onDismiss = onDismiss,
                hub = null, onOpenHit = null, onRunAction = {}, onRequestFilePermission = {}, sounds = null, initialQuery = query,
                launchHistory = RadarTestHabits.history(apps), learnedOverride = emptyMap(), nowMillis = { RadarTestHabits.NOW }, voiceAvailable = true,
            )
        }
        rule.mainClock.advanceTimeBy(1_000)
    }

    private fun bounds(desc: String) = rule.onNodeWithContentDescription(desc).fetchSemanticsNode().boundsInRoot

    @Test
    fun PAL_18_wheelLabelsFadeToNothingBeforeTheEdgeOfTheResultsArea() {
        assertEquals(1f, wheelLabelAlpha(0f), 1e-6f)
        var prev = 1f
        var d = 0.25f
        while (d <= 6f) { val a = wheelLabelAlpha(d); assertTrue("alpha falls with distance at $d", a <= prev + 1e-6f); prev = a; d += 0.25f }
        assertTrue("the third neighbour is still faintly there", wheelLabelAlpha(3f) > 0f)
        assertEquals("nothing is drawn at the fade distance, so no glyph is cut at the strip boundary", 0f, wheelLabelAlpha(WheelFadeRows), 0f)
        assertEquals(0f, wheelLabelAlpha(5f), 0f)
        // The old falloff is unchanged for the labels that matter.
        assertEquals(1f / (1f + 0.9f * 4f), wheelLabelAlpha(2f), 1e-6f)
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi")
    fun INP_13_inLandscapeEveryVisibleWheelLabelEndsAboveTheMicStrip() {
        open(SearchLayout.Orbit)
        val mic = bounds("Speak to search")
        val row = 34f * 2f // one wheel row in px at xhdpi
        val labels = rule.onAllNodes(SemanticsMatcher("a wheel label") { n -> n.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text == "EVERYTHING" }).fetchSemanticsNodes()
        assertTrue("the wheel is on screen", labels.isNotEmpty())
        val sel = labels.first().boundsInRoot
        assertTrue("the selected label ${sel} ends above the mic ${mic}", sel.bottom <= mic.top)
        // The last label that is still drawn (3.5 rows below the selected one) also ends above the mic strip, so nothing visible is cut.
        assertTrue("the visible fade zone ends above the mic: ${sel.center.y + WheelFadeRows * row} vs ${mic.top}", sel.center.y + WheelFadeRows * row <= mic.top + row / 2)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun PAL_18_flatWithNoTextShowsCloseAndNoClear() {
        var closed = 0
        open(SearchLayout.Flat, "", onDismiss = { closed++ })
        assertEquals(0, rule.onAllNodesWithContentDescription("Clear").fetchSemanticsNodes().size)
        rule.onNodeWithContentDescription("Close").performClick()
        assertEquals(1, closed)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun NFR_13_theQueryLineTheResultsAndTheMicAreSeparateTraversalGroupsReadTopToBottom() {
        open(SearchLayout.Orbit)
        val groups = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.IsTraversalGroup), useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.config[SemanticsProperties.IsTraversalGroup] }.map { it.boundsInRoot }
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val mic = bounds("Speak to search")
        val field = rule.onNode(androidx.compose.ui.test.hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val bar = groups.firstOrNull { it.contains(field.center) && it.height < root.height / 4 }
        val micGroup = groups.firstOrNull { it.contains(mic.center) && it.height < root.height / 4 && it !== bar }
        val results = groups.firstOrNull { it.height > root.height / 2 }
        assertTrue("a group holds the query line: $groups", bar != null)
        assertTrue("a group holds the mic: $groups", micGroup != null)
        assertTrue("a group holds the results: $groups", results != null)
        assertTrue("query line group above the results above the mic group", bar!!.bottom <= results!!.top + 1f && results.bottom <= micGroup!!.top + 1f)
    }
}
