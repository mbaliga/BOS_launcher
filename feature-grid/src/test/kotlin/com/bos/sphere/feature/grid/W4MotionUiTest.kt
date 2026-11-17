package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
import com.bos.sphere.core.data.BackgroundChoice
import com.bos.sphere.core.data.BgItem
import com.bos.sphere.core.data.RadarIntensity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.quality.Contrast
import com.bos.sphere.core.quality.HomeLegibility
import com.bos.sphere.core.quality.MotionPrefs
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Wave 4: NFR-13 labels, NFR-14 host paging, NFR-15 sampled live scenes and Horizon. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
class W4MotionUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @After fun reset() = ReducedMotionTesting.force(null)

    private val inks = listOf(0xFFE7E9F0.toInt(), 0xFFC9CDD6.toInt())

    /** Wave 15: the live scenes and the Horizon picture are gone; every item of the new background model is sampled the same way. */
    @Test fun NFR_15_everyBackgroundItemIsSampledOffAFrameAndGetsAPlanThatReachesAa() {
        val choices = BgItem.entries.filter { it != BgItem.None }.map { BackgroundChoice(it, BgItem.None) } + BackgroundChoice.DEFAULT
        choices.forEach { choice ->
            val name = choice.items.joinToString("+") { it.id }
            val samples = BackgroundSamples.colors(choice, RadarIntensity.Normal, dark = true)
            assertEquals("$name samples", 25 * 2 * 4, samples.size)
            assertTrue(samples.all { (it ushr 24) == 0xFF })
            val plan = HomeLegibility.plan(inks, samples)
            assertTrue("$name reaches AA", plan.reachesAa)
            samples.forEach { w -> inks.forEach { t -> assertTrue(name, Contrast.ratio(t, Contrast.blend(plan.scrimColor, w, plan.alpha)) >= Contrast.AA_NORMAL - 0.02) } }
        }
    }

    @Test fun NFR_15_samplingIsDeterministicAndNotJustTheFallbackGrey() {
        val a = BackgroundSamples.colors(BackgroundChoice(BgItem.Spire, BgItem.None), RadarIntensity.Normal, true)
        val b = BackgroundSamples.colors(BackgroundChoice(BgItem.Spire, BgItem.None), RadarIntensity.Normal, true)
        assertEquals(a, b)
        assertTrue("not all mid-grey", a.any { it != 0xFF808080.toInt() })
        // A different item gives different pixels, so the sampler is really drawing the item it was asked for.
        assertTrue(a != BackgroundSamples.colors(BackgroundChoice(BgItem.Laser, BgItem.None), RadarIntensity.Normal, true))
    }

    @Test fun NFR_14_hostDrivenAnimatedGoToPageLandsImmediatelyUnderReducedMotion() {
        ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f))
        lateinit var state: GridHomeState
        lateinit var scope: kotlinx.coroutines.CoroutineScope
        rule.setContent {
            state = rememberGridHomeState()
            state.count.intValue = 4
            scope = androidx.compose.runtime.rememberCoroutineScope()
            androidx.compose.foundation.pager.HorizontalPager(state.pager, Modifier.size(100.dp)) { Box(Modifier.size(100.dp)) }
        }
        rule.waitForIdle()
        assertTrue(state.stillPaging)
        rule.mainClock.autoAdvance = false
        scope.launch { state.goToPage(2, animate = true) }
        // Two frames are far too few for an animated scroll to finish; a jump is already there.
        rule.mainClock.advanceTimeByFrame(); rule.mainClock.advanceTimeByFrame()
        assertEquals(2, state.currentPage)
    }

    @Test fun NFR_13_colourDotsAndFolderTilesHaveNames() {
        rule.setContent {
            ColorDot(0xFF8E7BFF, 24.dp, onClick = {}, selected = true)
        }
        rule.onNodeWithContentDescription("#8E7BFF").assertContentDescriptionEquals("#8E7BFF")
        val node = rule.onNodeWithContentDescription("#8E7BFF").fetchSemanticsNode()
        assertTrue(node.config.contains(SemanticsProperties.Role))
        assertTrue(node.config[SemanticsProperties.Selected])
    }

    @Test fun NFR_19_predictiveBackHandlerIsEnabledOnlyWhileTheSurfaceIsOpenAndCommitsOnce() {
        var open by androidx.compose.runtime.mutableStateOf(true)
        var backs = 0
        rule.setContent { com.bos.sphere.core.design.rememberPredictiveBackProgress(open) { backs++ } }
        rule.waitForIdle()
        val dispatcher = rule.activity.onBackPressedDispatcher
        assertTrue("enabled while open", dispatcher.hasEnabledCallbacks())
        rule.runOnUiThread { dispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals(1, backs)
        // Once the exit has begun (open = false) the surface no longer holds back.
        rule.runOnUiThread { open = false }
        rule.waitForIdle()
        assertTrue("disabled once closing", !dispatcher.hasEnabledCallbacks())
    }
}
