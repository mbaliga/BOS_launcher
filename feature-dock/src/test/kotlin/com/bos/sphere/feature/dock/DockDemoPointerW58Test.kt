package com.bos.sphere.feature.dock

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.bos.sphere.core.data.DockStyle
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.quality.MotionPrefs
import org.junit.After
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
 * Wave 58 (studioPreview), CFG-73: the Settings Preview draws the dock's magnification as a sample bulge. [DockRail]'s optional `demoPointer`
 * (default off) rests a still pointer on the row: with magnification on, the icon under it is at the strength's peak and its neighbours grow less,
 * with no finger, no tooltip and taps still going to the icon pressed; without magnification, or without the pointer, the row is at rest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
class DockDemoPointerW58Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ran = mutableListOf<String>()

    @Before fun setUp() { L10n.setLanguage("en"); DockInput.dialogHook = {}; ran.clear(); ReducedMotionTesting.force(MotionPrefs(1f, 1f, 1f)) }
    @After fun tearDown() { ReducedMotionTesting.force(null) }

    private val content = DockContent((0 until 4).map { DockItem(DockItemRef.app("app$it"), "App ${"ABCD"[it]}") }, emptyList(), showEnableRow = false, showRecentSection = false, collapsed = false)

    @Composable private fun Scene(magnify: MagnifyStrength?, demo: Float?, edge: DockEdge = DockEdge.Bottom) {
        Box(Modifier.fillMaxSize().background(Color(0xFF1B2A55)).padding(16.dp), contentAlignment = if (edge == DockEdge.Bottom) Alignment.BottomCenter else Alignment.CenterStart) {
            DockRail(
                content, edge, "Home", expanded = true, focusRequest = 0, showNumbers = false, graphBridge = null, style = DockStyle.Standard, dark = true, magnify = magnify,
                actions = DockActions(onLaunch = { i, _ -> ran += "launch ${i.label}" }), demoPointer = demo,
            ) { _, s -> Box(Modifier.size(s).background(Color(0xFF4F8CFF))) }
        }
    }
    private fun width(l: String) = rule.onNodeWithContentDescription(l).getBoundsInRoot().width.value
    private fun height(l: String) = rule.onNodeWithContentDescription(l).getBoundsInRoot().height.value
    private fun settle() { rule.mainClock.advanceTimeBy(600); rule.waitForIdle() }

    @Test fun CFG_73_aDemoPointerDrawsTheMagnificationBulgeWithNoFinger() {
        rule.setContent { Scene(MagnifyStrength.Medium, DEMO_POINTER) }
        settle()
        val w = listOf("App A", "App B", "App C", "App D").map { width(it) }
        val peak = w.indices.maxBy { w[it] }
        assertTrue("the icon under the sample pointer is the big one: $w", peak == 1 || peak == 0)
        assertTrue("it grows towards the Medium factor (${w[peak]})", w[peak] > 52f * 1.4f)
        assertTrue("the far icon grows less: $w", w[3] < w[peak] - 8f)
        rule.onAllNodesWithTag(DockMagnifyTags.TOOLTIP, useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithContentDescription("App D").performClick()
        assertEquals(listOf("launch App D"), ran)
    }

    @Test fun CFG_73_withoutMagnificationOrWithoutThePointerTheRowIsAtRest() {
        rule.setContent { Scene(null, DEMO_POINTER) }
        settle()
        listOf("App A", "App B", "App C", "App D").forEach { assertEquals(it, 52f, width(it), 0.8f) }
    }

    @Test fun CFG_73_theDefaultDockHasNoDemoPointer() {
        rule.setContent { Scene(MagnifyStrength.Large, null) }
        settle()
        listOf("App A", "App B", "App C", "App D").forEach { assertEquals(it, 52f, width(it), 0.8f) }
    }

    @Test fun CFG_73_aSideDockBulgesTooAlongItsColumn() {
        rule.setContent { Scene(MagnifyStrength.Large, DEMO_POINTER, DockEdge.Left) }
        settle()
        val h = listOf("App A", "App B", "App C", "App D").map { height(it) }
        assertTrue("a side dock grows along its column: $h", h.max() > 52f * 1.5f && h[3] < h.max() - 8f)
    }
}
