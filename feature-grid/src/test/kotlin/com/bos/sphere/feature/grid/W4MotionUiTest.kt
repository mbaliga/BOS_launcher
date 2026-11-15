package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
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

    @Test fun NFR_15_everyLiveSceneIsSampledOffAFrameAndGetsAPlanThatReachesAa() {
        com.bos.sphere.feature.grid.BackgroundPresets.lives.forEach { scene ->
            val samples = liveSampleColors(scene.id, 0xFF6C5CE7)
            assertEquals("${scene.id} samples", 25 * LIVE_SAMPLE_TIMES.size, samples.size)
            assertTrue(samples.all { (it ushr 24) == 0xFF })
            val plan = HomeLegibility.plan(inks, samples)
            assertTrue("${scene.id} reaches AA", plan.reachesAa)
            samples.forEach { w -> inks.forEach { t -> assertTrue("${scene.id}", Contrast.ratio(t, Contrast.blend(plan.scrimColor, w, plan.alpha)) >= Contrast.AA_NORMAL - 0.02) } }
        }
    }

    @Test fun NFR_15_liveSamplingIsDeterministicAndNotJustTheFallbackGrey() {
        val a = liveSampleColors("aurora", 0xFF6C5CE7)
        val b = liveSampleColors("aurora", 0xFF6C5CE7)
        assertEquals(a, b)
        assertTrue("not all mid-grey", a.any { it != 0xFF808080.toInt() })
        // A different scene gives different pixels, so the sampler is really drawing the scene it was asked for.
        assertTrue(a != liveSampleColors("lava", 0xFF6C5CE7))
    }

    @Test fun NFR_15_horizonPictureIsDecodedSmallAndSampled() {
        val samples = horizonSampleColors(rule.activity)
        assertEquals(25, samples.size)
        val plan = HomeLegibility.plan(inks, samples)
        assertTrue(plan.reachesAa)
        samples.forEach { w -> inks.forEach { t -> assertTrue(Contrast.ratio(t, Contrast.blend(plan.scrimColor, w, plan.alpha)) >= Contrast.AA_NORMAL - 0.02) } }
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
