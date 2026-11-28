package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.DefaultCorners
import com.bos.sphere.core.data.GlanceHotspots
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.core.quality.MotionPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * HOM-23, STR-06, NFR-13, NFR-14 (wave 22): the controls notch of the Glance home. It hangs from y = 0, is flared, holds only the
 * three outline controls (48 dp targets), the title stays in the line below. Screenshots go to build/screenshots/glance_notch_*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlanceNotchUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @After fun reset() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
        ReducedMotionTesting.force(null)
        L10n.setLanguage("")
        runCatching { LauncherInput.focus.nodes().map { it.id }.forEach(LauncherInput.focus::unregister) }
    }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(300)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { Canvas(bmp).also { it.drawColor(android.graphics.Color.BLACK); view.draw(it) } }
        File(outDir, "glance_notch_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val playing = MediaInfo("Someday Out of The Blue", "", "pkg.music", playing = true, canPrev = true, canNext = true)
    private fun hotspots() = DefaultCorners.filterKeys { it in GlanceHotspots.ALL }.map { (c, slot) -> Hotspot(c, GlanceHotspots.itemOf(slot)!!) }

    private fun glance(media: MediaInfo = playing, rtl: Boolean = false, clicks: IntArray = IntArray(4)) = rule.setContent {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            GlanceHomeForTest(
                prefs = LauncherPrefs(), media = media, hotspots = hotspots(), appsByKey = emptyMap(),
                hasLaunchpad = true,
                onRun = { _, _ -> }, onPrevious = { clicks[1]++ }, onPlayPause = { clicks[2]++ }, onNext = { clicks[3]++ },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    private fun popIn() {
        rule.onNodeWithContentDescription("Now playing", substring = true).performClick()
        rule.mainClock.advanceTimeBy(700)
    }

    private fun notch() = rule.onNodeWithTag("glance.notch", useUnmergedTree = true)
    private fun density() = rule.activity.resources.displayMetrics.density

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_theNotchStartsAtTheTopEdgeAndIsCentredAndTheTitleLineIsBelowIt() {
        ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f)) // no marquee loop for a title that overflows
        glance(playing.copy(artist = "Vince DiCola, Diego Navarro and the Hot Fire Orchestra"))
        popIn()
        val b = notch().fetchSemanticsNode().boundsInRoot
        val d = density()
        assertEquals("flush with the physical top", 0f, b.top, 0.5f)
        assertEquals("centred", rule.onRoot().fetchSemanticsNode().boundsInRoot.center.x, b.center.x, 1f)
        assertEquals("about 58 percent of the width", 411f * 0.58f, b.width / d, 1f)
        val line = rule.onNodeWithContentDescription("Now playing", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the title line stays visible under the notch", line.top >= (GlanceNotch.BODY_HEIGHT_DP) * d)
        shoot("portrait_dark_long_title")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_theTitleIsNotInsideTheNotchAndThereAreThreeLabelledTargetsOf48dp() {
        glance()
        popIn()
        val inNotch = hasAnyAncestor(hasTestTag("glance.notch"))
        assertEquals("no title text inside the notch", 0, rule.onAllNodes(inNotch and hasContentDescription("Now playing", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size)
        val d = density()
        for ((tag, label) in listOf("glance.prev" to "Previous", "glance.playpause" to "Pause", "glance.next" to "Next")) {
            val n = rule.onNode(hasTestTag(tag) and inNotch, useUnmergedTree = true).fetchSemanticsNode()
            assertEquals("$label width", 48f, n.boundsInRoot.width / d, 0.5f)
            assertEquals("$label height", 48f, n.boundsInRoot.height / d, 0.5f)
            rule.onNodeWithContentDescription(label).assertExists()
        }
        assertEquals(setOf("glance.prev", "glance.playpause", "glance.next"), LauncherInput.focus.nodes().map { it.id }.filter { it.startsWith("glance.p") || it == "glance.next" }.toSet())
        shoot("portrait_dark_short_title")
    }

    @Test @Config(sdk = [35]) fun HOM_23_theGlyphsAreNamedOutlinesWithNoFill() {
        assertEquals(listOf("OutlineRewind", "OutlinePlay", "OutlinePause", "OutlineForward"),
            listOf(OutlineMediaGlyphs.Rewind, OutlineMediaGlyphs.Play, OutlineMediaGlyphs.Pause, OutlineMediaGlyphs.Forward).map { it.name })
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_playPauseFollowsThePlayingStateAndCallsTheSameFunctions() {
        val clicks = IntArray(4)
        glance(playing.copy(playing = false), clicks = clicks)
        popIn()
        rule.onNodeWithContentDescription("Play").performClick()
        rule.onNodeWithContentDescription("Previous").performClick()
        rule.onNodeWithContentDescription("Next").performClick()
        assertEquals(listOf(1, 1, 1), listOf(clicks[1], clicks[2], clicks[3]))
        // the tap on Play flipped the glyph to Pause at once (the player confirms it later)
        assertEquals(1, rule.onAllNodes(hasContentDescription("Pause")).fetchSemanticsNodes().size)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_aDisplayCutoutPushesTheIconsDownButTheShapeStillStartsAtTheTop() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                ControlsNotch(true, false, playing, 411f, 40.dp, Modifier.align(androidx.compose.ui.Alignment.TopCenter), {}, {}, {}, {}, {})
            }
        }
        rule.mainClock.advanceTimeBy(700)
        val d = density()
        assertEquals(0f, notch().fetchSemanticsNode().boundsInRoot.top, 0.5f)
        val prev = rule.onNodeWithContentDescription("Previous").fetchSemanticsNode().boundsInRoot
        assertTrue("the targets start below the cutout", prev.top >= 40f * d - 1f)
        shoot("cutout")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_aRealCutoutInsetMovesTheTitleLineAndTheIconsNotTheNotchTop() {
        glance()
        rule.runOnUiThread {
            val content = rule.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            val view = content.getChildAt(0)
            val ins = androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout(), androidx.core.graphics.Insets.of(0, (40 * density()).toInt(), 0, 0))
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars(), androidx.core.graphics.Insets.of(0, (40 * density()).toInt(), 0, 0))
                .build()
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(view, ins)
        }
        rule.waitForIdle()
        popIn()
        val d = density()
        assertEquals(0f, notch().fetchSemanticsNode().boundsInRoot.top, 0.5f)
        assertTrue(rule.onNodeWithContentDescription("Previous").fetchSemanticsNode().boundsInRoot.top >= 40f * d - 1f)
        val line = rule.onNodeWithContentDescription("Now playing", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the line is under the notch", line.top >= (40f + GlanceNotch.BODY_HEIGHT_DP) * d - 1f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_rtlKeepsTheNotchCentredWithTheSameOrderOfControls() {
        glance(rtl = true)
        popIn()
        val b = notch().fetchSemanticsNode().boundsInRoot
        assertEquals(rule.onRoot().fetchSemanticsNode().boundsInRoot.center.x, b.center.x, 1f)
        val prev = rule.onNodeWithContentDescription("Previous").fetchSemanticsNode().boundsInRoot
        val next = rule.onNodeWithContentDescription("Next").fetchSemanticsNode().boundsInRoot
        assertTrue("transport order is not mirrored", prev.left < next.left)
        shoot("portrait_rtl")
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_23_landscapeNotchIsCappedAndFlush() {
        glance()
        popIn()
        val b = notch().fetchSemanticsNode().boundsInRoot
        assertEquals(0f, b.top, 0.5f)
        assertEquals(GlanceNotch.MAX_WIDTH_DP, b.width / density(), 1f)
        shoot("landscape")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_theTouchAreaIsExactlyTheDrawnNotchAndEveryTargetLiesInsideIt() {
        glance()
        popIn()
        val d = density()
        val b = notch().fetchSemanticsNode().boundsInRoot
        assertEquals("the box ends where the black ends", GlanceNotch.heightDp(0f), b.height / d, 0.5f)
        for (tag in listOf("glance.prev", "glance.playpause", "glance.next")) {
            val t = rule.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag("glance.notch")), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag is inside the drawn notch", t.top >= b.top - 0.5f && t.bottom <= b.bottom + 0.5f && t.left >= b.left && t.right <= b.right)
            assertEquals("$tag is 48 dp tall", 48f, t.height / d, 0.5f)
        }
        shoot("hit_area")
    }
}
