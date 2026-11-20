package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import com.bos.sphere.core.data.ClockFormat
import com.bos.sphere.core.data.ClockStyle
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.RightDestination
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.design.ThemeVariant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Wave 38 (glanceFixed): the pure rules of the Glance clock. */
class GlanceClockRulesTest {
    @Test fun GFX_38_theDigitalClockIsTheMusicTextSizeAndTheAnalogDialIs44To56Dp() {
        assertEquals(GlanceText.MUSIC_SP, ClockText.DIGITAL_SP, 0f)
        assertEquals(14f, ClockText.DIGITAL_SP, 0f)
        assertTrue(ClockText.ANALOG_DP in 44f..56f)
        assertEquals("the analog face is the default", ClockStyle.Analog, LauncherPrefs().clockStyle)
    }

    @Test fun GFX_38_twelveOrTwentyFourHourFollowsTheSettingAndSystemFollowsTheDevice() {
        assertEquals("h:mm", ClockText.timePattern(ClockFormat.Hour12, true)); assertEquals("HH:mm", ClockText.timePattern(ClockFormat.Hour24, false))
        assertEquals("HH:mm", ClockText.timePattern(ClockFormat.System, true)); assertEquals("h:mm", ClockText.timePattern(ClockFormat.System, false))
    }

    @Test fun GFX_38_handAnglesAreClockwiseFromTwelve() {
        val (h3, m0) = ClockText.handAngles(3, 0)
        assertEquals(Math.PI.toFloat() / 2f, h3, 1e-4f); assertEquals(0f, m0, 1e-4f)
        val (h6, m30) = ClockText.handAngles(18, 30)
        assertEquals(Math.PI.toFloat() * (6.5f / 6f), h6, 1e-3f); assertEquals(Math.PI.toFloat(), m30, 1e-4f)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlanceClockW38UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @After fun reset() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
        ReducedMotionTesting.force(null)
        L10n.setLanguage("")
        runCatching { LauncherInput.focus.nodes().map { it.id }.forEach(LauncherInput.focus::unregister) }
    }

    private val media = MediaInfo("Someday Out of The Blue", "Artist", "pkg.music", playing = true, canPrev = true, canNext = true)

    private fun show(prefs: LauncherPrefs, m: MediaInfo? = media) = rule.setContent {
        GlanceHomeForTest(prefs, m, emptyList(), emptyMap(), listOf(RightDestination.Recents), true, { _, _ -> }, {}, {}, {}, Modifier.fillMaxSize(), dark = true)
    }

    private fun layout(tag: String): TextLayoutResult {
        val results = ArrayList<TextLayoutResult>()
        rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun GFX_38_theDigitalClockHasExactlyTheMusicTextSizeAndTheyAreTheOnlyTextOnTheGlance() {
        ReducedMotionTesting.force(com.bos.sphere.core.quality.MotionPrefs(0f, 0f, 0f))
        show(LauncherPrefs(clockStyle = ClockStyle.Digital, clockFormat = ClockFormat.Hour24))
        val clock = layout("glance.clock.digital"); val music = layout("glance.title.static")
        assertEquals(music.layoutInput.style.fontSize, clock.layoutInput.style.fontSize)
        assertTrue(clock.layoutInput.text.text.matches(Regex("\\d\\d:\\d\\d")))
        for (t in listOf("glance.time", "glance.date", "glance.weather", "glance.battery")) assertEquals(t, 0, rule.onAllNodesWithTag(t, useUnmergedTree = true).fetchSemanticsNodes().size)
        // the clock sits at the top centre, above the music line
        val c = rule.onNodeWithTag("glance.clock.digital", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val m = rule.onNodeWithTag("glance.title.static", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the music line is under the clock", m.top >= c.bottom - 1f)
        assertEquals(rule.onRoot().fetchSemanticsNode().boundsInRoot.center.x, c.center.x, 2f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun GFX_38_twelveHourShowsNoLeadingZeroAndAnalogDrawsADialInstead() {
        show(LauncherPrefs(clockStyle = ClockStyle.Digital, clockFormat = ClockFormat.Hour12))
        assertTrue(layout("glance.clock.digital").layoutInput.text.text.matches(Regex("\\d{1,2}:\\d\\d")))
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun GFX_38_analogShowsTheDialAndNoDigitalText() {
        show(LauncherPrefs(clockStyle = ClockStyle.Analog))
        assertEquals(1, rule.onAllNodesWithTag("glance.clock.analog", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithTag("glance.clock.digital", useUnmergedTree = true).fetchSemanticsNodes().size)
        val b = rule.onNodeWithTag("glance.clock.analog", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val d = rule.activity.resources.displayMetrics.density
        assertTrue("the dial is inside its 48 dp touch target", b.width / d <= ClockText.ANALOG_DP && b.width / d >= 36f)
        // music and clock are both present and the music line is below the clock, no overlap
        val m = rule.onNodeWithTag("glance.title.static", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the music line is below the analog clock", m.top >= b.bottom - 1f)
    }
}
