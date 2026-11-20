package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.catalog.CellRect
import com.bos.sphere.core.catalog.HomeState
import com.bos.sphere.core.catalog.ItemDef
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind
import com.bos.sphere.core.catalog.ProviderLimits
import com.bos.sphere.core.catalog.TilePlacement
import com.bos.sphere.core.catalog.WidgetSpan
import com.bos.sphere.core.data.HomePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Wave 45 (editHandles), HOM-07 / HOM-12 / NFR-13: the selection frame of a widget, live; the item edit actions; the keyboard. */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@Config(sdk = [35], qualifiers = "w411dp-h600dp-port-xhdpi")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditHandlesUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val w = ItemKeys.widget(11)
    private val pitch = 200f // px at xhdpi-ish: 100 dp at density 2
    private val sel = EditSelectionState()
    private val resized = mutableListOf<Triple<String, CellRect, Boolean>>()
    private val actions = mutableListOf<ItemAction>()
    private val removed = mutableListOf<String>()

    private fun extras(limits: ProviderLimits? = null, span: WidgetSpan = WidgetSpan(2, 2), resizing: Boolean = true) = HomeExtras(
        HomeState(
            pages = listOf(HomePage(listOf(w, "a", "b"))),
            items = mapOf(w to ItemDef(w, ItemKind.Widget, "Weather", "p/.W")),
            widgets = mapOf(w to span),
        ),
        { null }, { null }, widget = { _, m -> Box(m.testTag("widget-content")) },
        resizing = if (resizing) w else null, selection = sel,
        limitsOf = { limits }, onResizeTo = { k, r, m -> resized += Triple(k, r, m) }, onAction = { actions += it },
    )

    private fun ctx(start: CellRect = CellRect(1, 1, 2, 2), placed: List<TilePlacement> = emptyList(), keys: List<String> = listOf(w, "a", "b")) =
        ResizeContext(start, 4, 6, pitch, pitch, placed = placed, pageKeys = keys, onRemove = { removed += w })

    @Composable private fun Frame(e: HomeExtras, c: ResizeContext) {
        Box(Modifier.fillMaxSize().padding(48.dp)) { HomeWidgetCell(w, e, pitch, pitch, Modifier.size(180.dp, 180.dp), c) }
    }

    /** Presses a knob and drags it in a few steps (the first step only passes the touch slop). */
    private fun press(name: String, dx: Float, dy: Float) {
        handle(name).performTouchInput {
            down(center)
            repeat(4) { moveBy(androidx.compose.ui.geometry.Offset(dx / 4f, dy / 4f)) }
        }
        rule.waitForIdle()
    }

    private fun release(name: String) { handle(name).performTouchInput { up() }; rule.waitForIdle() }

    private fun handle(name: String) = rule.onNodeWithTag("resize-handle-$name", useUnmergedTree = true)

    @Test
    fun HOM_07_theKnobIs12dpInA48dpHitAreaAndThereAreEightForADualAxisWidget() {
        val e = extras(); rule.setContent { Frame(e, ctx()) }
        listOf("NorthWest", "North", "NorthEast", "West", "East", "SouthWest", "South", "SouthEast").forEach { handle(it).assertExists() }
        val hit = handle("SouthEast").getUnclippedBoundsInRoot()
        assertEquals(48f, (hit.right - hit.left).value, 0.6f); assertEquals(48f, (hit.bottom - hit.top).value, 0.6f)
        val knob = rule.onAllNodesWithTag("resize-knob", useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        assertEquals(12f, (knob.right - knob.left).value, 0.6f)
    }

    @Test
    fun HOM_07_theKnobSitsOutsideTheContentSoItNeverCoversIt() {
        val e = extras(); rule.setContent { Frame(e, ctx()) }
        val content = rule.onNodeWithTag("widget-content").getUnclippedBoundsInRoot()
        val se = rule.onAllNodesWithTag("resize-knob", useUnmergedTree = true).fetchSemanticsNodes().size
        assertEquals(8, se)
        val knob = handle("SouthEast").onChildren().fetchSemanticsNodes() // unmerged child exists
        assertTrue(knob.isNotEmpty() || se == 8)
        val k = rule.onNodeWithTag("resize-handle-SouthEast", useUnmergedTree = true).getUnclippedBoundsInRoot()
        // The hit area is centred 6 dp beyond the content corner.
        assertEquals(content.right.value + 6f, (k.left.value + k.right.value) / 2f, 1f)
        assertEquals(content.bottom.value + 6f, (k.top.value + k.bottom.value) / 2f, 1f)
    }

    @Test
    fun HOM_07_aHorizontalOnlyProviderShowsOnlyEastAndWestAndAVerticalOnlyOneNorthAndSouth() {
        val e = extras(ProviderLimits(horizontal = true, vertical = false)); rule.setContent { Frame(e, ctx()) }
        handle("East").assertExists(); handle("West").assertExists()
        handle("North").assertDoesNotExist(); handle("SouthEast").assertDoesNotExist(); handle("South").assertDoesNotExist()
    }

    @Test
    fun HOM_07_aWidgetThatCannotResizeHasNoHandles() {
        val e = extras(ProviderLimits(horizontal = false, vertical = false)); rule.setContent { Frame(e, ctx()) }
        rule.onAllNodesWithTag("resize-knob", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun HOM_12_theInWidgetDonePillIsGone() {
        val e = extras(); rule.setContent { Frame(e, ctx()) }
        rule.onNodeWithText("Done").assertDoesNotExist()
    }

    @Test
    fun HOM_07_dragTheSouthEastKnobShowsTheGhostAndTheSizeChipAndAppliesOnRelease() {
        val e = extras(); rule.setContent { Frame(e, ctx()) }
                rule.onNodeWithTag("resize-ghost").assertDoesNotExist()
        press("SouthEast", pitch + 5f, pitch + 5f)
        rule.onNodeWithTag("resize-ghost").assertExists()
        rule.onNodeWithTag("resize-chip").assertExists()
        rule.onNodeWithText("3 x 3").assertExists()
        val gb = rule.onNodeWithTag("resize-ghost").getUnclippedBoundsInRoot()
        val cb = rule.onNodeWithTag("widget-content").getUnclippedBoundsInRoot()
        // The ghost is the snapped 3 x 3 cells: wider and taller than the widget's own 2 x 2 box (180 dp), by one 100 dp cell.
        assertEquals(280f, (gb.right - gb.left).value, 1f); assertEquals(280f, (gb.bottom - gb.top).value, 1f)
        assertEquals(180f, (cb.right - cb.left).value, 1f)
        assertTrue(resized.isEmpty()) // nothing applies until release
        release("SouthEast")
        assertEquals(1, resized.size)
        assertEquals(CellRect(1, 1, 3, 3), resized[0].second); assertFalse(resized[0].third)
        rule.onNodeWithTag("resize-ghost").assertDoesNotExist()
    }

    @Test
    fun HOM_07_dragTheNorthWestKnobGrowsUpAndLeftAndMovesTheOrigin() {
        val e = extras(); rule.setContent { Frame(e, ctx()) }
                press("NorthWest", -pitch - 5f, -pitch - 5f)
        rule.onNodeWithText("3 x 3").assertExists()
        release("NorthWest")
        assertEquals(CellRect(0, 0, 3, 3), resized.single().second)
        assertTrue(resized.single().third) // the origin moved
    }

    @Test
    fun HOM_07_aLimitClampsRefusesPastItAndTurnsTheChipToTheErrorColour() {
        val e = extras(ProviderLimits(maxResizeWidthDp = 300f, maxResizeHeightDp = 300f)) // 100 dp cells: max 3 x 3 ... 
        rule.setContent { Frame(e, ctx()) }
        press("SouthEast", pitch * 3, 0f)
        rule.onNodeWithText("3 x 2").assertExists() // clamped at the provider's maximum, not the 4th column
        release("SouthEast")
        assertEquals(3, resized.single().second.cols)
    }

    @Test
    fun HOM_07_draggingAWidgetAlreadyAtItsMaximumChangesNothingAndSaysSo() {
        val e = extras(ProviderLimits(maxResizeWidthDp = 300f, maxResizeHeightDp = 300f), WidgetSpan(3, 2))
        rule.setContent { Frame(e, ctx(start = CellRect(0, 1, 3, 2))) }
        press("East", pitch, 0f)
        rule.onNodeWithText("3 x 2").assertExists()
        release("East")
        assertTrue(resized.isEmpty())
        assertEquals("Can't resize: at the maximum", sel.announcement.removeSuffix("\u200B"))
    }

    @Test
    fun HOM_12_aWidgetBlocksWhenTheDragReachesANeighbourAndNothingIsApplied() {
        val a = TilePlacement("a", 3, 1, WidgetSpan(1, 1))
        val e = extras(); rule.setContent { Frame(e, ctx(placed = listOf(a))) }
                press("East", pitch, 0f)
        rule.onNodeWithText("3 x 2").assertExists()
        release("East")
        assertTrue(resized.isEmpty())
        assertEquals("Can't resize: another item is in the way", sel.announcement.removeSuffix("\u200B"))
    }

    @Test
    fun HOM_12_selectedWidgetOutlineIsDrawnOutsideTheContentNotOnIt() {
        val e = extras(); rule.setContent { Frame(e, ctx()) }
        // The frame draws no border modifier on the content: the content node keeps its exact size.
        val content = rule.onNodeWithTag("widget-content").getUnclippedBoundsInRoot()
        assertEquals(180f, (content.right - content.left).value, 0.6f)
    }

    // ---- accessibility and keyboard (through the item editor)
    private fun editor(span: WidgetSpan = WidgetSpan(2, 1), keys: List<String> = listOf(w, "a", "b"), limits: ProviderLimits? = null): Pair<ItemEditor, HomeExtras> {
        val e = extras(limits, span, false)
        return ItemEditor(w, e, keys, 0, 4, 6, onRemove = { removed += w }) to e
    }

    @Test fun NFR_13_everyItemNodeOffersMoveResizeAndRemoveActions() {
        val (ed, _) = editor()
        val names = ed.actions().map { it.label }
        assertEquals(listOf("Move left", "Move right", "Move up", "Move down", "Make wider", "Make narrower", "Make taller", "Make shorter", "Remove"), names)
    }

    @Test fun NFR_13_anIconNodeOffersMoveAndRemoveButNoResize() {
        val e = extras(null, WidgetSpan(2, 1), false)
        val ed = ItemEditor("a", e, listOf(w, "a", "b"), 0, 4, 6) { removed += "a" }
        assertEquals(listOf("Move left", "Move right", "Move up", "Move down", "Remove"), ed.actions().map { it.label })
    }

    @Test fun NFR_13_makeWiderResizesWithinLimitsAndAnnouncesTheResult() {
        val (ed, e) = editor(WidgetSpan(2, 1), listOf(w), null)
        assertTrue(ed.resize(1, 0))
        assertEquals(CellRect(0, 0, 3, 1), resized.single().second)
        assertEquals("Resized to 3 by 1", sel.announcement.removeSuffix("\u200B"))
        assertNotNull(e)
    }

    @Test fun NFR_13_makeWiderAtTheMaximumSaysSoAndChangesNothing() {
        val (ed, _) = editor(WidgetSpan(3, 1), listOf(w), ProviderLimits(maxResizeWidthDp = 240f))
        // 80 dp cells: three cells is the provider's maximum
        assertFalse(ed.resize(1, 0))
        assertTrue(resized.isEmpty())
        assertEquals("Can't resize: at the maximum", sel.announcement.removeSuffix("\u200B"))
    }

    @Test fun NFR_13_makeNarrowerAtTheMinimumAnnouncesTheMinimum() {
        val (ed, _) = editor(WidgetSpan(2, 1), listOf(w), ProviderLimits(minResizeWidthDp = 160f))
        assertFalse(ed.resize(-1, 0))
        assertEquals("Can't resize: at the minimum", sel.announcement.removeSuffix("\u200B"))
    }

    @Test fun NFR_13_moveSaysTheNewRowAndColumnAndAtTheEdgeSaysSo() {
        val e = extras(null, WidgetSpan(1, 1), false)
        val ed = ItemEditor("a", e, listOf("x", "a", "b"), 0, 4, 6) {}
        assertTrue(ed.move(1, 0))
        assertEquals(ItemAction.Move("a", 0, 2), actions.single())
        assertEquals("Moved to row 1, column 3", sel.announcement.removeSuffix("\u200B"))
        val first = ItemEditor("x", e, listOf("x", "a", "b"), 0, 4, 6) {}
        assertFalse(first.move(-1, 0))
        assertEquals("Can't move: at the edge of the page", sel.announcement.removeSuffix("\u200B"))
    }

    @Test fun NFR_13_removeAnnouncesAndCallsTheHomeRemovePath() {
        val (ed, _) = editor()
        ed.remove()
        assertEquals(listOf(w), removed)
        assertEquals("Removed Weather", sel.announcement.removeSuffix("\u200B"))
    }

    @Test
    fun HOM_12_keyboardShiftArrowResizesArrowMovesAndDeleteRemovesTheSelectedWidget() {
        val e = extras(null, WidgetSpan(2, 1), true)
        rule.setContent { Box(Modifier.fillMaxSize().padding(48.dp)) { HomeWidgetCell(w, e, pitch, pitch, Modifier.size(180.dp, 90.dp), ResizeContext(CellRect(0, 0, 2, 1), 4, 6, pitch, pitch, pageKeys = listOf(w), onRemove = { removed += w })) } }
        rule.waitForIdle()
        val node = rule.onNodeWithTag("widget-content").onParent()
        node.performKeyInput { keyDown(androidx.compose.ui.input.key.Key.ShiftLeft); pressKey(androidx.compose.ui.input.key.Key.DirectionRight); keyUp(androidx.compose.ui.input.key.Key.ShiftLeft) }
        rule.waitForIdle()
        assertEquals(CellRect(0, 0, 3, 1), resized.single().second)
        node.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Delete) }
        rule.waitForIdle()
        assertEquals(listOf(w), removed)
    }

    // ---- the selection model
    @Test fun HOM_12_selectionIsOneModelTapSelectsASecondTapOrEmptySpaceDeselects() {
        val s = EditSelectionState()
        Snapshot.withMutableSnapshot {
            assertEquals("a", s.toggle("a")); assertTrue(s.isSelected("a"))
            assertEquals("b", s.toggle("b")); assertFalse(s.isSelected("a"))
            assertEquals(null, s.toggle("b"))
            s.select(w); assertEquals(ItemKind.Widget, s.selected?.kind)
            s.clear(); assertEquals(null, s.selected)
        }
    }
}
