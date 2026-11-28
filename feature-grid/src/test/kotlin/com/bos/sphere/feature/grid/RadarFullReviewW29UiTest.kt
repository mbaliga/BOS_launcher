package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
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

/**
 * Wave 29 (radarFull), adversarial review: what the first pass left open. The wheel's own box was half the screen wide and took every touch in it, a tile's caption was
 * dead (the tap fell through to "close Radar"), the Flat list was still cut at the status bar and the navigation bar.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RadarFullReviewW29UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun english() { com.bos.sphere.core.design.L10n.setLanguage("en") }

    private val names = List(44) { "Clau%02d".format(it) }
    private val apps = names.map { n -> AppEntry("demo.$n", ComponentName("demo.$n", "demo.$n.Main"), n, Process.myUserHandle()) { null } }
    private var dismissed = 0
    private var launched = 0

    private fun content(query: String, motion: Boolean, layout: SearchLayout = SearchLayout.Orbit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SearchScreen(
                apps = apps, recent = apps, prefs = LauncherPrefs(searchMotion = motion, searchLayout = layout, searchLayoutPinned = true), contacts = null,
                onLaunch = { launched++ }, onLongPressApp = { launched++ }, onOpenIntent = { launched++ }, onRequestContactsPermission = {}, onDismiss = { dismissed++ },
                hub = null, onOpenHit = null, onRunAction = {}, onRequestFilePermission = {}, sounds = null, initialQuery = query,
                launchHistory = RadarTestHabits.history(apps), learnedOverride = emptyMap(), nowMillis = { RadarTestHabits.NOW }, voiceAvailable = true,
            )
        }
    }

    private fun open(query: String, motion: Boolean, layout: SearchLayout = SearchLayout.Orbit) { content(query, motion, layout); rule.mainClock.advanceTimeBy(1_000) }

    private fun tiles() = rule.onAllNodes(hasTestTag(RADAR_ORBIT_TILE_TAG)).fetchSemanticsNodes().map { n -> Rect(n.positionInRoot, androidx.compose.ui.geometry.Size(n.size.width.toFloat(), n.size.height.toFloat())) }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun PAL_20_w29_theWheelTakesTouchesOnlyWhereItsLabelsAre() {
        open("", motion = true)
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val wheel = rule.onNodeWithTag(RADAR_WHEEL_TAG).fetchSemanticsNode().boundsInRoot
        // It used to be the whole right half of the screen (a tile passing there could not be tapped); now it is as wide as its widest label.
        assertTrue("the wheel's touch box (${wheel.width}px) is narrower than 70 percent of the half screen (${root.width / 2}px)", wheel.width < root.width / 2 * 0.7f)
        assertTrue("the wheel still holds its labels", wheel.width > 40f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun PAL_20_w29_aTilesCaptionTakesTheTapLikeItsFace() {
        open("", motion = false)
        val d = rule.activity.resources.displayMetrics.density
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val overlays = listOf(RADAR_FIELD_TAG, RADAR_WHEEL_TAG, RADAR_MIC_TAG).map { rule.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot } +
            rule.onNodeWithContentDescription("Close").fetchSemanticsNode().boundsInRoot
        val all = tiles()
        var checked = 0
        val bad = ArrayList<String>()
        for (t in all) {
            val p = Offset(t.center.x, t.top + (54f + 3f + 7f) * d) // the middle of the caption line, 54 dp face + 3 dp gap + half the line
            if (p.x < 4f || p.x > root.right - 4f || p.y < 80f * d || p.y > root.bottom - 90f * d) continue
            if (overlays.any { it.contains(p) } || all.any { it != t && it.contains(p) }) continue
            val l0 = launched; val d0 = dismissed
            rule.onAllNodes(isRoot()).onFirst().performTouchInput { click(p) }
            rule.mainClock.advanceTimeBy(50)
            if (launched != l0 + 1 || dismissed != d0) bad += "caption of $t (tap $p) launched=${launched - l0} dismissed=${dismissed - d0}"
            checked++
        }
        assertTrue("some captions were tapped ($checked)", checked >= 3)
        assertTrue("a tile's caption is not a dead spot that closes Radar: ${bad.take(4)}", bad.isEmpty())
    }

    private fun fakeSystemBars(topPx: Int, bottomPx: Int) {
        rule.runOnUiThread {
            val w = androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars(), androidx.core.graphics.Insets.of(0, topPx, 0, 0))
                .setVisible(androidx.core.view.WindowInsetsCompat.Type.statusBars(), true)
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars(), androidx.core.graphics.Insets.of(0, 0, 0, bottomPx))
                .setVisible(androidx.core.view.WindowInsetsCompat.Type.navigationBars(), true)
                .build()
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(rule.activity.window.decorView, w)
        }
        rule.mainClock.advanceTimeBy(1_500)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun PAL_18_w29_theFlatListReachesTheTrueScreenEdgesUnderTheStatusBarAndTheNavigationBar() {
        rule.runOnUiThread { androidx.core.view.WindowCompat.setDecorFitsSystemWindows(rule.activity.window, false) } // edge to edge, as the launcher is: the bars are insets the content sees
        open("clau", false, SearchLayout.Flat)
        val d = rule.activity.resources.displayMetrics.density
        fakeSystemBars((24 * d).toInt(), (48 * d).toInt())
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val list = rule.onAllNodes(SemanticsMatcher("the lazy list") { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange) }).fetchSemanticsNodes().first().boundsInRoot
        assertEquals("the list starts at the top edge, not at the status bar's bottom line", root.top, list.top, 1f)
        assertEquals("the list ends at the bottom edge, not at the navigation bar's top line", root.bottom, list.bottom, 1f)
    }
}
