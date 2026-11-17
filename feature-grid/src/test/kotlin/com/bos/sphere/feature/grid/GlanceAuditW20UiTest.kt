package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import com.bos.sphere.core.data.ClockFormat
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.DefaultCorners
import com.bos.sphere.core.data.GlanceHotspots
import com.bos.sphere.core.data.GlanceItem
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.RightDestination
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.HyleTheme
import androidx.compose.ui.graphics.toArgb
import com.bos.sphere.core.data.BackgroundChoice
import com.bos.sphere.core.data.BgItem
import com.bos.sphere.core.data.RadarIntensity
import com.bos.sphere.core.quality.Contrast
import com.bos.sphere.core.quality.HomeLegibility
import org.junit.Assert.assertEquals
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.design.ThemeVariant
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Wave 20 audit of HOM-23 (Glance): the clock fits its window on small phones, in landscape on a small window and at 200 percent font,
 * and never lands on a hotspot or an edge handle. Robolectric, not a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlanceAuditW20UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @After fun reset() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
        ReducedMotionTesting.force(null)
        L10n.setLanguage("")
        runCatching { LauncherInput.focus.nodes().map { it.id }.forEach(LauncherInput.focus::unregister) }
    }

    private val media = MediaInfo("Title", "Artist", "pkg.music", playing = true, canPrev = true, canNext = true)
    private fun hotspots() = DefaultCorners.map { (c, slot) -> Hotspot(c, GlanceHotspots.itemOf(slot)!!) }

    private fun show(prefs: LauncherPrefs) = rule.setContent {
        GlanceSurface(
            prefs = prefs, weather = null, media = media, hotspots = hotspots(), appsByKey = emptyMap(), rightDestinations = listOf(RightDestination.Recents),
            hasLaunchpad = true, onRun = { _, _ -> }, onClockTap = {}, onPrevious = {}, onPlayPause = {}, onNext = {}, dark = true, modifier = Modifier.fillMaxSize(),
        )
    }

    private fun assertClockFits(label: String) {
        val node = rule.onNodeWithTag("glance.time", useUnmergedTree = true).fetchSemanticsNode()
        val results = ArrayList<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.firstOrNull()
        assertTrue("$label: the time has a text layout", layout != null)
        assertFalse("$label: the clock text is not clipped by its own box", layout!!.hasVisualOverflow)
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val t = node.boundsInRoot
        assertTrue("$label: inside the window", t.left >= 0f && t.right <= root.right && t.top >= 0f && t.bottom <= root.bottom)
        // the 48 dp edge handles (Launchpad on the left, Recents on the right) sit at the vertical centre: the clock stays clear of them
        val handle = 48f * rule.activity.resources.displayMetrics.density
        assertTrue("$label: clear of the left handle", t.left >= handle)
        assertTrue("$label: clear of the right handle", t.right <= root.right - handle)
        for (c in LauncherInput.focus.nodes().filter { it.id.startsWith("minimal.corner.") }.map { it.rect })
            assertFalse("$label: the clock does not overlap a hotspot", t.left < c.right && t.right > c.left && t.top < c.bottom && t.bottom > c.top)
    }

    @Test @Config(sdk = [35], qualifiers = "w320dp-h568dp-port-xhdpi", fontScale = 2f)
    fun HOM_23_theClockIsNotClippedOnASmallPhoneAt200PercentFont() {
        show(LauncherPrefs(clockFormat = ClockFormat.Hour24, glanceContent = setOf(GlanceItem.Time, GlanceItem.Date, GlanceItem.NowPlaying)))
        assertClockFits("320x568 font 200")
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-xhdpi")
    fun HOM_23_theClockIsNotClippedOnACommon360DpPhone() {
        show(LauncherPrefs(clockFormat = ClockFormat.Hour24))
        assertClockFits("360x640")
    }

    @Test @Config(sdk = [35], qualifiers = "w568dp-h320dp-land-xhdpi", fontScale = 2f)
    fun HOM_23_theClockInLandscapeOnASmallWindowAt200PercentStaysClearOfTheCorners() {
        show(LauncherPrefs(clockFormat = ClockFormat.Hour24))
        assertClockFits("568x320 land font 200")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_theClockOnATypicalPhoneKeepsItsSizeAndFits() {
        show(LauncherPrefs(clockFormat = ClockFormat.Hour12))
        assertClockFits("411x891")
    }

    @Test @Config(sdk = [35]) fun HOM_23_theMusicLineIsolatesEachPartSoARightToLeftTitleKeepsItsOrderAndNoArtistIsTheTitleAlone() {
        assertEquals("عنوان", GlanceText.nowPlayingDisplay("عنوان", ""))
        assertEquals("\u2068عنوان\u2069 · \u2068Artist\u2069", GlanceText.nowPlayingDisplay("عنوان", "Artist"))
        assertEquals("\u2068Title\u2069 · \u2068فنان\u2069", GlanceText.nowPlayingDisplay("Title", "فنان"))
        // the spoken line has no control characters
        assertEquals("عنوان · Artist", GlanceText.nowPlayingLine("عنوان", "Artist"))
    }

    // ---- NFR-15: the date and the music line use inks the home scrim is planned for

    @Test @Config(sdk = [35]) fun NFR_15_theGlanceInksAreTheThemeInksTheScrimIsPlannedFor() {
        for (dark in listOf(true, false)) {
            val t = HyleColors.tokens(if (dark) ThemeVariant.Dark else ThemeVariant.Light)
            assertEquals(t.inkBright, GlanceInk.clock(dark)); assertEquals(t.ink, GlanceInk.secondary(dark))
        }
    }

    @Test @Config(sdk = [35]) fun NFR_15_theClockAndTheDateReachAaOverEveryBackgroundAtEveryLevelWithTheHomeScrim() {
        val choices = BgItem.entries.map { BackgroundChoice(it, BgItem.None) } + BackgroundChoice.DEFAULT +
            listOf(BackgroundChoice(BgItem.Beam, BgItem.RadarSweep), BackgroundChoice(BgItem.Halo, BgItem.BottomGlow), BackgroundChoice(BgItem.GlowLines, BgItem.BreathingDots))
        for (dark in listOf(true, false)) for (intensity in RadarIntensity.entries) for (choice in choices) {
            // the plan is sized from what the app samples (default levels); the check runs against every level, so a level the sampling skips cannot hide a miss
            val planned = BackgroundSamples.colors(choice, intensity, dark)
            val samples = BackgroundSamples.colors(choice, intensity, dark, levels = intArrayOf(0, 1, 2, 3, 4))
            val texts = listOf(GlanceInk.clock(dark), GlanceInk.secondary(dark)).map { it.toArgb() }
            val plan = HomeLegibility.plan(texts, planned)
            assertTrue("$choice dark=$dark $intensity: the scrim plan reaches AA", plan.reachesAa)
            for (bg in samples) {
                val under = if (plan.alpha > 0.0) Contrast.blend(plan.scrimColor, bg, plan.alpha) else bg
                for (t in texts) assertTrue("$choice dark=$dark $intensity: ${Integer.toHexString(t)} over ${Integer.toHexString(under)}", Contrast.meetsAa(t, under))
            }
        }
    }
}
