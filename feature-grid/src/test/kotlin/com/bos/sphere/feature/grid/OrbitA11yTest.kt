package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** NFR-13: the Orbit's tiles circle the screen, so TalkBack must read them in rank order with their names. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OrbitA11yTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun app(i: Int) = AppEntry("p$i", ComponentName("p$i", "M"), "App ${'A' + i}", Process.myUserHandle()) { null }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun NFR_13_orbit_tiles_read_in_rank_order_and_carry_their_names() {
        rule.mainClock.autoAdvance = false // the Orbit runs a frame loop that never idles
        val apps = (0 until 6).map(::app)
        val tiles = apps.map { Tile.App(it) }
        rule.setContent {
            Orbit(tiles, motionRequested = false, footnote = null, actions = TileActions({}, {}, {}), centreFromBottom = 400.dp)
        }
        rule.mainClock.advanceTimeBy(500)
        val nodes = rule.onAllNodes(SemanticsMatcher("has traversal index") { it.config.contains(SemanticsProperties.TraversalIndex) }).fetchSemanticsNodes()
        assertTrue("some tiles are on screen", nodes.isNotEmpty())
        val names = nodes.sortedBy { it.config[SemanticsProperties.TraversalIndex] }.map { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }
        // Whatever subset has a slot on screen, it is read best match first, and every tile has a non-empty name.
        assertTrue(names.all { !it.isNullOrBlank() })
        val ranks = names.map { n -> apps.indexOfFirst { it.label == n } }
        assertEquals(ranks.sorted(), ranks)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun NFR_13_filmstrip_tiles_each_carry_their_name_in_strip_order() {
        rule.mainClock.autoAdvance = false
        val apps = (0 until 3).map(::app)
        rule.setContent {
            Filmstrip(apps.map { Tile.App(it) }, motion = false, query = "", expanded = null, onOpenIntent = {}, footnote = null, actions = TileActions({}, {}, {}))
        }
        rule.mainClock.advanceTimeBy(500)
        val names = rule.onAllNodes(SemanticsMatcher("named tile") { it.config.contains(SemanticsProperties.ContentDescription) && it.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick) })
            .fetchSemanticsNodes().map { it.config[SemanticsProperties.ContentDescription].first() }
        assertEquals(apps.map { it.label }, names)
    }
}
