package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.getOrNull
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.SearchLayout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Wave 22 audit of the Radar chrome: a hardware keyboard or d-pad must reach the Clear X, Close and the mic (they used to be
 * unreachable because Radar swallowed Tab and the arrows), Clear keeps focus in the field, and hostile pasted text is harmless.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class RadarAuditGlassW22UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun english() { com.bos.sphere.core.design.L10n.setLanguage("en") }

    private val names = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India")
    private val apps = names.map { n -> AppEntry("demo.$n", ComponentName("demo.$n", "demo.$n.Main"), n, Process.myUserHandle()) { null } }
    private var dismissed = 0

    private fun open(layout: SearchLayout, query: String = "", web: Boolean = true) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SearchScreen(
                apps = apps, recent = apps, prefs = LauncherPrefs(searchMotion = false, searchLayout = layout, searchLayoutPinned = true, searchWeb = web), contacts = null,
                onLaunch = {}, onLongPressApp = {}, onOpenIntent = {}, onRequestContactsPermission = {}, onDismiss = { dismissed++ },
                hub = null, onOpenHit = null, onRunAction = {}, onRequestFilePermission = {}, sounds = null, initialQuery = query,
                launchHistory = RadarTestHabits.history(apps), learnedOverride = emptyMap(), nowMillis = { RadarTestHabits.NOW }, voiceAvailable = true,
            )
        }
        rule.mainClock.advanceTimeBy(1_000)
    }

    private fun key(k: Key, shift: Boolean = false) {
        rule.onRoot().performKeyInput { if (shift) keyDown(Key.ShiftLeft); pressKey(k); if (shift) keyUp(Key.ShiftLeft) }
        rule.mainClock.advanceTimeBy(300)
    }
    private fun count(desc: String) = rule.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().size

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun INP_13_shiftTabFromTheFieldReachesClearAndTabReachesTheMic() {
        open(SearchLayout.Flat, "al")
        rule.onNode(hasSetTextAction()).assertIsFocused()
        key(Key.Tab, shift = true)
        rule.onNodeWithContentDescription("Clear").assertIsFocused()
        key(Key.Tab)
        rule.onNodeWithContentDescription("Speak to search").assertIsFocused()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun PAL_18_enterOnTheFocusedClearEmptiesTheFieldAndFocusReturnsToIt() {
        open(SearchLayout.Flat, "al")
        key(Key.Tab, shift = true)
        rule.onNodeWithContentDescription("Clear").assertIsFocused()
        key(Key.Enter)
        assertEquals(0, count("Clear"))
        assertEquals("Flat has no Close (wave 22: the owner asked only for the X inside the field)", 0, count("Close"))
        rule.onNode(hasSetTextAction()).assertIsFocused()
        // and the keys are Radar's again (Escape closes it)
        key(Key.Escape)
        assertEquals(1, dismissed)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun PAL_18_aTapOnClearKeepsTheFieldFocusedAndEmpties() {
        open(SearchLayout.Orbit, "bra")
        rule.onNodeWithContentDescription("Clear").performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.onNode(hasSetTextAction()).assertIsFocused()
        assertEquals(0, count("Clear")); assertEquals(1, count("Close"))
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun PAL_07_upFromTheFieldReachesCloseWhenNothingIsSelectedAndEnterCloses() {
        open(SearchLayout.Orbit, "")
        key(Key.DirectionUp)
        rule.onNodeWithContentDescription("Close").assertIsFocused()
        key(Key.Enter)
        assertEquals(1, dismissed)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun INP_13_downFromTheFieldWithNothingToWalkReachesTheMic() {
        open(SearchLayout.Flat, "qzxqzx", web = false)
        key(Key.DirectionDown)
        rule.onNodeWithContentDescription("Speak to search").assertIsFocused()
        key(Key.Tab, shift = true)
        rule.onNodeWithContentDescription("Clear").assertIsFocused()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun PAL_18_aQueryOfOnlySpacesKeepsClearAndNoCloseAndEnterDoesNothing() {
        open(SearchLayout.Orbit, "")
        rule.onNode(hasSetTextAction()).performTextReplacement("     ")
        rule.mainClock.advanceTimeBy(500)
        assertEquals("there is text to clear", 1, count("Clear")); assertEquals(0, count("Close"))
        key(Key.Enter)
        assertEquals(0, dismissed)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun PAL_06_pastingTenThousandCharactersIsHandledAndTheFieldStaysUsable() {
        open(SearchLayout.Flat, "")
        rule.onNode(hasSetTextAction()).performTextReplacement("a".repeat(10_000))
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(1, count("Clear")); assertEquals(0, count("Close"))
        key(Key.Enter) // the web row or nothing: never a crash
        rule.onNodeWithContentDescription("Clear").performClick()
        rule.mainClock.advanceTimeBy(500)
        assertEquals(0, count("Close"))
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun NFR_13_theTypedOrbitStripPillsAre48dpButtonsAndTheRingsAndTheResultsAreReadingGroupsSoTheMicIsReadLast() {
        open(SearchLayout.Orbit, "bra")
        val sem = androidx.compose.ui.semantics.SemanticsProperties
        val rootBounds = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val pills = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("a strip pill") { n ->
            n.config.getOrNull(sem.Role) == androidx.compose.ui.semantics.Role.Button && n.config.getOrNull(sem.ContentDescription)?.joinToString().orEmpty().contains("bra", ignoreCase = true) && // wave 25: a strip circle names itself by its description
                n.boundsInRoot.top > rootBounds.height * 0.6f
        }).fetchSemanticsNodes()
        org.junit.Assert.assertTrue("a typed query has web hand-off pills", pills.isNotEmpty())
        pills.forEach { n -> org.junit.Assert.assertTrue("pill ${n.boundsInRoot} is at least 48 dp high", n.boundsInRoot.height >= 96f - 1) }
        val groups = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("a traversal group") { it.config.getOrNull(sem.IsTraversalGroup) == true }).fetchSemanticsNodes()
        org.junit.Assert.assertTrue("the results area and the rings are reading groups (the mic, index 0, is otherwise read before ring tile 1)", groups.size >= 2)
        val mic = rule.onNodeWithContentDescription("Speak to search").fetchSemanticsNode()
        // Wave 29: the Orbit's layer spans the display under the mic, so "inside" is about the tree, not the geometry.
        org.junit.Assert.assertTrue("the mic is not inside a results group", generateSequence(mic.parent) { it.parent }.none { g -> g.config.getOrNull(sem.IsTraversalGroup) == true && g.boundsInRoot.height > mic.boundsInRoot.height * 3 })
        val ringTiles = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("ring tile") { it.config.contains(sem.TraversalIndex) }).fetchSemanticsNodes()
        org.junit.Assert.assertTrue(ringTiles.isNotEmpty())
        org.junit.Assert.assertTrue("every ring tile sits inside a reading group", ringTiles.all { t -> groups.any { g -> g.boundsInRoot.let { b -> b.left <= t.boundsInRoot.left + 1 && b.right >= t.boundsInRoot.right - 1 && b.top <= t.boundsInRoot.top + 1 && b.bottom >= t.boundsInRoot.bottom - 1 } } })
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun PAL_18_closeLeavingWhileItHasFocusHandsTheKeysBackToRadar() {
        open(SearchLayout.Orbit, "")
        key(Key.DirectionUp)
        rule.onNodeWithContentDescription("Close").assertIsFocused()
        // text arrives (as a voice result would) while Close has keyboard focus: Close is replaced by Clear
        rule.onNode(hasSetTextAction()).performTextReplacement("bra")
        rule.mainClock.advanceTimeBy(500)
        assertEquals(0, count("Close")); assertEquals(1, count("Clear"))
        key(Key.Tab, shift = true)
        rule.onNodeWithContentDescription("Clear").assertIsFocused()
    }
}
