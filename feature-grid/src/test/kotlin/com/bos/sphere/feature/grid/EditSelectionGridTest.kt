package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.bos.sphere.core.catalog.HomeState
import com.bos.sphere.core.catalog.ItemDef
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind
import com.bos.sphere.core.catalog.WidgetSpan
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Wave 45 (editHandles), HOM-12 / HOM-07: selection, ghost, blocking and the fallback Remove on the live grid page. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
class EditSelectionGridTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val labels = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot")
    private val apps = labels.mapIndexed { i, n ->
        AppEntry("demo.s$i", ComponentName("demo.s$i", "demo.s$i.Main"), n, Process.myUserHandle()) { ColorDrawable(0xFF3355AA.toInt() + i * 0x101010) }
    }
    private val byKey = apps.associateBy { it.key }
    private val w = ItemKeys.widget(5)
    private val sel = EditSelectionState()

    private class Probe { var changes = 0; var last: List<HomePage> = emptyList(); val removed = mutableListOf<String>() }

    private val extras = HomeExtras(
        HomeState(
            pages = listOf(HomePage(listOf(w) + apps.map { it.key }, id = 1L)),
            items = mapOf(w to ItemDef(w, ItemKind.Widget, "Weather", "p/.W")),
            widgets = mapOf(w to WidgetSpan(2, 2)),
        ),
        { byKey[it] }, { null }, widget = { _, m -> Box(m.testTag("widget-content")) }, selection = sel,
    )

    @Composable private fun Scene(probe: Probe, edit: Boolean = true) {
        val prefs = LauncherPrefs()
        val state = rememberGridHomeState()
        val pages = extras.state.pages
        Box(Modifier.fillMaxSize()) {
            GridHome(
                pages = pages, appsByKey = byKey, prefs = prefs, isLandscape = false, state = state,
                gesturesBound = setOf(HomeGesture.PinchIn), capacityOf = { it.capacity(false, prefs) },
                onLaunch = {}, onAppLongPress = {},
                onPagesChange = { probe.changes++; probe.last = it },
                onEmptyLongPress = {}, onGesture = {}, onClockTap = {}, onTileLongPress = { _, _ -> },
                extras = extras, editMode = edit, onRemoveItem = { probe.removed += it },
            )
        }
    }

    private fun start(probe: Probe, edit: Boolean = true) {
        rule.mainClock.autoAdvance = false
        rule.setContent { Scene(probe, edit) }
        rule.mainClock.advanceTimeBy(500)
    }

    private fun center(desc: String) = rule.onNodeWithContentDescription(desc).fetchSemanticsNode().boundsInRoot.center
    private fun settle() { rule.runOnUiThread { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }; rule.mainClock.advanceTimeBy(300) }
    private fun tapAt(p: Offset) { rule.onRoot().performTouchInput { down(p); advanceEventTime(40); up() }; settle() }

    @Test fun HOM_12_aTapSelectsAnIconASecondTapDeselectsAndEmptySpaceDeselects() {
        start(Probe())
        val c = center("Echo")
        tapAt(c)
        assertEquals(apps[4].key, sel.selectedKey)
        tapAt(c)
        assertNull(sel.selectedKey)
        tapAt(c); assertEquals(apps[4].key, sel.selectedKey)
        tapAt(Offset(c.x, 20f)) // the top margin band: no item
        assertNull(sel.selectedKey)
    }

    @Test fun HOM_12_aTapSelectsAWidgetAndItGetsHandlesWhileTheIconsDoNot() {
        start(Probe())
        tapAt(rule.onNodeWithTag("widget-content").fetchSemanticsNode().boundsInRoot.center)
        assertEquals(w, sel.selectedKey)
        rule.onNodeWithTag("resize-handle-SouthEast", useUnmergedTree = true).assertExists()
        rule.onAllNodesWithTag("resize-handle-SouthEast", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun HOM_12_theSelectedIconGetsAFortyEightDpRemoveButtonThatRemovesIt() {
        val probe = Probe()
        start(probe)
        tapAt(center("Delta"))
        val b = rule.onNodeWithTag("selection-remove").fetchSemanticsNode().boundsInRoot
        assertTrue("48 dp at xhdpi is 96 px: ${b.height}", b.height >= 95f)
        rule.onNodeWithTag("selection-remove").performClick()
        assertEquals(listOf(apps[3].key), probe.removed)
    }

    @Test fun HOM_12_leavingEditModeClearsTheSelection() {
        val probe = Probe()
        val edit = androidx.compose.runtime.mutableStateOf(true)
        rule.mainClock.autoAdvance = false
        rule.setContent { Scene(probe, edit.value) }
        rule.mainClock.advanceTimeBy(500)
        tapAt(center("Alpha")); assertEquals(apps[0].key, sel.selectedKey)
        rule.runOnUiThread { androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { edit.value = false } }
        rule.mainClock.advanceTimeBy(300)
        assertNull(sel.selectedKey)
    }

    @Test fun HOM_12_dragAnIconShowsTheSnappedGhostAndPushesTheOthersAside() {
        val probe = Probe()
        start(probe)
        val from = center("Alpha"); val to = center("Foxtrot")
        rule.onRoot().performTouchInput {
            down(from); advanceEventTime(30); moveTo(Offset(from.x + 40f, from.y + 40f)); advanceEventTime(30); moveTo(to); advanceEventTime(30)
        }
        settle()
        rule.onNodeWithTag("move-ghost").assertExists() // the ghost of where it lands
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.advanceTimeBy(300)
        assertTrue(probe.changes >= 1)
        assertEquals(apps[0].key, sel.selectedKey) // dragging selects what is dragged
    }

    @Test fun HOM_12_aWidgetDroppedOnAnotherItemIsRefusedAndNothingMoves() {
        val probe = Probe()
        start(probe)
        val wc = rule.onNodeWithTag("widget-content").fetchSemanticsNode().boundsInRoot.center
        val to = center("Delta") // an icon beside / below the widget's own cells
        rule.onRoot().performTouchInput {
            down(wc); advanceEventTime(900) // a widget is picked up by a long press
            moveTo(Offset(wc.x + 30f, wc.y + 30f)); advanceEventTime(30); moveTo(to); advanceEventTime(30)
        }
        settle()
        rule.onNodeWithTag("move-ghost").assertExists()
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.advanceTimeBy(300)
        assertEquals("a widget blocks: dropped over an icon nothing is applied", 0, probe.changes)
    }
}
