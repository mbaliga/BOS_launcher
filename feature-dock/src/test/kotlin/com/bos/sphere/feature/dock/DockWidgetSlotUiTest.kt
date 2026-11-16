package com.bos.sphere.feature.dock

import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** HOM-02: on the rail a pinned widget does not fight the rail's reorder drag: long-press and drag on its grip, the body is the widget's. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DockWidgetSlotUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val widgetRef = DockItemRef(ItemKind.Widget, "widget:5")
    private val content = DockContent(
        pinned = listOf(DockItem(DockItemRef.app("a"), "A"), DockItem(widgetRef, "W", span = 2), DockItem(DockItemRef.app("c"), "C")),
        recent = emptyList(), showEnableRow = false, showRecentSection = false, media = null, collapsed = false,
    )

    private class Seen { var reorders = mutableListOf<Pair<Int, Int>>(); var bodyDrags = 0; var bodyDown = 0 }

    private fun show(seen: Seen) {
        rule.setContent {
            DockRail(
                content, DockEdge.Bottom, "Home", expanded = true, focusRequest = 0, showNumbers = false,
                actions = DockActions(onReorder = { f, t -> seen.reorders += f to t }), graphBridge = null,
                // The hosted view: takes every drag that starts on it, like an AppWidgetHostView with a scrolling list.
                widget = { _, w, h -> Box(Modifier.size(w, h).testTag("hosted").pointerInput(Unit) { detectDragGestures(onDragStart = { seen.bodyDown++ }) { c, _ -> c.consume(); seen.bodyDrags++ } }) { Text("hosted widget") } },
            ) { item, _ -> Box(Modifier.size(40.dp).testTag("icon.${item.ref.id}")) }
        }
        rule.waitForIdle()
    }

    private fun longPressDrag(tag: String, dx: Float, dy: Float = 0f) {
        rule.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput {
            down(center)
            advanceEventTime(700)  // past the long-press timeout
            moveBy(Offset(dx, dy), 100)
            up()
        }
        rule.waitForIdle()
    }

    @Test @Config(sdk = [35], qualifiers = "w800dp-h400dp-land-mdpi")
    fun HOM_02_aLongPressAndDragOnTheWidgetsGripReordersItEvenThoughTheHostedViewTakesDrags() {
        val seen = Seen()
        show(seen)
        longPressDrag(widgetGripTag(widgetRef.encode()), dx = 160f)
        assertTrue("reorders: ${seen.reorders}", seen.reorders.isNotEmpty())
        assertEquals(1, seen.reorders.last().first)
        assertTrue(seen.reorders.last().second > 1)
        assertEquals(0, seen.bodyDrags)
    }

    @Test @Config(sdk = [35], qualifiers = "w800dp-h400dp-land-mdpi")
    fun HOM_02_aDragOnTheWidgetBodyBelongsToTheHostedViewAndNeverReordersTheRail() {
        val seen = Seen()
        show(seen)
        rule.onNodeWithTag("hosted", useUnmergedTree = true).performTouchInput { down(Offset(width / 2f, height * 0.7f)); advanceEventTime(700); moveBy(Offset(120f, 0f), 100); up() }
        rule.waitForIdle()
        assertTrue("the hosted view saw the drag", seen.bodyDrags > 0)
        assertTrue(seen.reorders.isEmpty())
    }

    @Test @Config(sdk = [35], qualifiers = "w800dp-h400dp-land-mdpi")
    fun HOM_02_aShortDragOnTheGripWithoutALongPressDoesNotReorder() {
        val seen = Seen()
        show(seen)
        rule.onNodeWithTag(widgetGripTag(widgetRef.encode()), useUnmergedTree = true).performTouchInput { down(center); moveBy(Offset(160f, 0f), 100); up() }
        rule.waitForIdle()
        assertTrue(seen.reorders.isEmpty())
    }

    @Test @Config(sdk = [35], qualifiers = "w800dp-h400dp-land-mdpi")
    fun HOM_02_anIconSlotStillReordersWithALongPressAnywhereOnIt() {
        val seen = Seen()
        show(seen)
        longPressDrag("icon.a", dx = 140f)
        assertTrue("reorders: ${seen.reorders}", seen.reorders.isNotEmpty())
        assertEquals(0, seen.reorders.last().first)
    }
}
