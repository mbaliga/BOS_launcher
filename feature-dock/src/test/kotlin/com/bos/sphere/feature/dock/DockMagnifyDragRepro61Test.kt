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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.center
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.DockStyle
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.quality.MotionPrefs
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Wave 61 (owner's screen recording: "the icons currently bunch into the corners"). Three real bugs were found and fixed while
 * investigating (see `docs/decisions/dockMagnifyBunch.md`): `DockMagnify`'s documented `MIN_PITCH_FRACTION` floor was never actually
 * enforced (the contraction bisection always ran all the way to 0, the harshest fallback, whenever the row didn't fit at full growth);
 * the slack/head padding that reserves room for the spread was wired through a `LaunchedEffect`/`snapshotFlow` collector that lagged at
 * least a frame behind the actual touch, so `compute()` could see the still-unpadded (tighter) room on the first frame(s) of a touch;
 * and `trackMagnify` recorded the raw touch position into `field.pointer` before checking whether it had left the dock's own bounds, so
 * a pointer already well outside `[lo, hi]` could be fed into `compute()` for a frame or two while the cancel was taking effect - a
 * pointer outside its own room is explicitly out of scope for `compute()`'s own contract (`DockMagnifyFixW60Test`'s documented
 * precondition), and feeding it one anyway is exactly the "every icon collapses onto the pointer" shape the owner described.
 *
 * This is a regression guard for the case now directly verified fixed: a continuous drag along a dock that is NOT already tightly
 * packed (real slack at rest, matching the owner's own screenshot) never bunches, at any point along the row.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w700dp-h891dp-port-xhdpi")
class DockMagnifyDragRepro61Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val labels = "ABCDEFGH".map { "App $it" }

    @Before fun setUp() { L10n.setLanguage("en"); DockInput.dialogHook = {}; ReducedMotionTesting.force(MotionPrefs(1f, 1f, 1f)) }
    @After fun tearDown() { ReducedMotionTesting.force(null); HyleTheme.apply(com.bos.sphere.core.design.ThemeVariant.Dark, 0xFF8E7BFF) }

    private fun content(n: Int) = DockContent(
        pinned = (0 until n).map { DockItem(DockItemRef.app("app$it"), "App ${"ABCDEFGH"[it]}", false) },
        recent = emptyList(), showEnableRow = false, showRecentSection = false, collapsed = false,
    )

    @Composable private fun Scene() {
        Box(Modifier.fillMaxSize().background(Color(0xFF1B2A55)).padding(16.dp), contentAlignment = Alignment.BottomCenter) {
            DockRail(
                content(8), DockEdge.Bottom, "Home", expanded = true, focusRequest = 0, showNumbers = false, graphBridge = null,
                style = DockStyle.Standard, dark = true, magnify = MagnifyStrength.Medium, editing = false, actions = DockActions(),
            ) { item, s -> Box(Modifier.size(s).background(Color(0xFF4F8CFF))) }
        }
    }

    @Test fun W61_dragAcrossAnEightIconDockWithRealSlackNeverBunchesAnyTwoIconsTogether() {
        rule.setContent { Scene() }
        val restBounds = labels.associateWith { rule.onNodeWithContentDescription(it).getBoundsInRoot() }
        // Scenario sanity: this must be a dock with real slack at rest (every icon measured, none degenerate) - not already scrolling.
        for (label in labels) assertTrue("$label has real rest bounds", restBounds.getValue(label).right.value > restBounds.getValue(label).left.value)

        rule.onNodeWithContentDescription("App A").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(300); rule.waitForIdle()

        val totalDx = restBounds.getValue("App H").right.value - restBounds.getValue("App A").left.value
        assertTrue("dragging rightward across the row", totalDx > 0f)
        val steps = 8
        for (s in 1..steps) {
            val dx = totalDx * s / steps.toFloat()
            rule.onNodeWithContentDescription("App A").performTouchInput { moveTo(Offset(dx, center.y)) }
            rule.mainClock.advanceTimeBy(150); rule.waitForIdle()

            val now = labels.associateWith { rule.onNodeWithContentDescription(it).getBoundsInRoot() }
            for (i in labels.indices) for (j in i + 1 until labels.size) {
                val a = now.getValue(labels[i]); val b = now.getValue(labels[j])
                assertTrue(
                    "step=$s: ${labels[i]} [${a.left.value},${a.right.value}] and ${labels[j]} [${b.left.value},${b.right.value}] must not overlap",
                    a.right.value <= b.left.value || b.right.value <= a.left.value,
                )
            }
        }
        rule.onNodeWithContentDescription("App A").performTouchInput { up() }
    }
}
