package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.DefaultCorners
import com.bos.sphere.core.data.GlanceHotspots
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.RightDestination
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
 * HOM-23, STR-06, NFR-13, NFR-14 (wave 25, owner item 3): the song title under the cutout is pushed down by the notch while the controls are in,
 * never touches the notch, is a marquee when too long (no scrolling and an ellipsis with reduced motion) and the media controls are only in the notch.
 * Screenshots go to build/screenshots/glance_w25_*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlanceTitleW25UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @After fun reset() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
        ReducedMotionTesting.force(null)
        L10n.setLanguage("")
        runCatching { LauncherInput.focus.nodes().map { it.id }.forEach(LauncherInput.focus::unregister) }
    }

    private val longTitle = "Lose Yourself to Dance (Drumless Edition) (feat. Pharrell Williams)"
    private fun media(title: String) = MediaInfo(title, "", "pkg.music", playing = true, canPrev = true, canNext = true)
    private fun hotspots() = DefaultCorners.filterKeys { it in GlanceHotspots.ALL }.map { (c, slot) -> Hotspot(c, GlanceHotspots.itemOf(slot)!!) }

    private fun glance(title: String, fontScale: Float = 1f) = rule.setContent {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
            GlanceSurface(
                prefs = LauncherPrefs(), weather = null, media = media(title), hotspots = hotspots(), appsByKey = emptyMap(),
                rightDestinations = listOf(RightDestination.Recents), hasLaunchpad = true,
                onRun = { _, _ -> }, onClockTap = {}, onPrevious = {}, onPlayPause = {}, onNext = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(300)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { Canvas(bmp).also { it.drawColor(android.graphics.Color.BLACK); view.draw(it) } }
        File(outDir, "glance_w25_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tapIn() {
        rule.mainClock.advanceTimeBy(300)
        rule.onRoot().performTouchInput { down(Offset(width * 0.5f, height * 0.75f)); up() }
        rule.mainClock.advanceTimeBy(900)
    }
    private fun title(): Rect = rule.onNodeWithContentDescription("Now playing", substring = true).fetchSemanticsNode().boundsInRoot
    private fun notch(): Rect = rule.onNodeWithTag("glance.notch", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun d() = rule.activity.resources.displayMetrics.density
    private fun hasTag(t: String) = rule.onAllNodes(androidx.compose.ui.test.hasTestTag(t), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun pushedAndClear(title: String, scale: Float) {
        rule.mainClock.autoAdvance = title.length <= 30 // a scrolling title keeps the clock busy: step it by hand
        glance(title, scale)
        rule.mainClock.advanceTimeBy(1000)
        val hidden = title()
        assertTrue("hidden state: the title keeps its place under the status area", hidden.top >= GlanceNotch.TITLE_TOP_DP * d() - 1f)
        assertTrue("hidden state: no notch is composed", !hasTag("glance.notch"))
        tapIn()
        val n = notch(); val shown = title()
        assertTrue("shown: the title is below the notch (${shown.top} >= ${n.bottom})", shown.top >= n.bottom)
        assertTrue("shown: a visible gap, not touching", shown.top - n.bottom >= 4f)
        assertEquals("pushed down by exactly the notch body", GlanceNotch.TITLE_PUSH_DP * d(), shown.top - hidden.top, 2f)
        assertTrue("same horizontal place", kotlin.math.abs(shown.center.x - hidden.center.x) < 2f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_portraitTitleIsPushedBelowTheNotchAndNeverTouchesIt() { pushedAndClear("Someday Out of The Blue", 1f); shoot("portrait_short") }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_portraitLongTitleIsPushedBelowTheNotchAndNeverTouchesIt() { pushedAndClear(longTitle, 1f); shoot("portrait_long") }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_23_landscapeTitleIsPushedBelowTheNotchAndNeverTouchesIt() { pushedAndClear(longTitle, 1f); shoot("landscape") }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun NFR_14_fontScaleTwoTitleIsPushedBelowTheNotchAndNeverTouchesIt() { pushedAndClear(longTitle, 2f); shoot("font2") }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_theTitleMovesBackWhenTheControlsLeave() {
        glance("Someday Out of The Blue")
        rule.mainClock.advanceTimeBy(400)
        val hidden = title()
        tapIn()
        assertTrue(title().top > hidden.top + 40f)
        tapIn() // a second tap on the surface hides the controls
        rule.mainClock.advanceTimeBy(900)
        assertEquals("back at its place", hidden.top, title().top, 1.5f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_aLongTitleIsAMarqueeAndAShortOneIsStaticAndCentred() {
        rule.mainClock.autoAdvance = false
        glance(longTitle)
        rule.mainClock.advanceTimeBy(400)
        assertTrue("long title scrolls", hasTag("glance.title.marquee"))
        assertTrue(!hasTag("glance.title.static"))
        shoot("marquee_long")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_aShortTitleHasNoMarqueeAndIsCentred() {
        rule.mainClock.autoAdvance = false
        glance("Short")
        rule.mainClock.advanceTimeBy(400)
        assertTrue(hasTag("glance.title.static")); assertTrue(!hasTag("glance.title.marquee"))
        assertEquals("centred", rule.onRoot().fetchSemanticsNode().boundsInRoot.center.x, title().center.x, 1.5f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun NFR_13_withReducedMotionALongTitleDoesNotScrollAndShowsItsBeginningWithAnEllipsis() {
        ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f))
        rule.mainClock.autoAdvance = false
        glance(longTitle)
        rule.mainClock.advanceTimeBy(400)
        assertTrue("no marquee", !hasTag("glance.title.marquee"))
        assertTrue("static text", hasTag("glance.title.static"))
        rule.onNodeWithContentDescription("Now playing", substring = true).assertExists()
        shoot("reduced_motion_long")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun STR_06_theMediaControlsAreOnlyInTheNotchAndAppearOnlyAfterATap() {
        glance("Someday Out of The Blue")
        rule.mainClock.advanceTimeBy(400)
        assertTrue(!hasTag("glance.playpause"))
        tapIn()
        assertTrue(hasTag("glance.playpause"))
        val n = notch()
        for (t in listOf("glance.prev", "glance.playpause", "glance.next")) {
            val b = rule.onNodeWithTag(t, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$t sits inside the notch", b.top >= n.top && b.bottom <= n.bottom + 0.5f)
        }
    }
}
