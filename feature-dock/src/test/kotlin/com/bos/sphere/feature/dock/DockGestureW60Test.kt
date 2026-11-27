package com.bos.sphere.feature.dock

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.bos.sphere.core.data.DockStyle
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.core.quality.MotionPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Wave 60 (dockMagnifyFix): the press-drag-release launch gesture ("In the magnify mode... finger on the icons strip... either I can have my
 * finger exit the interactive area without lifting it, which cancels the command, or if I lift the finger with an app in focus, then that app
 * should open"). This replaces each icon's own independent clickable/combinedClickable for launching while magnify is on and the dock is not being
 * edited (see `trackMagnify`'s doc and `DockRail`'s `onGestureRelease`/`insideBounds` wiring): one pointer-input handler on the rail tracks the
 * press, the drag (reusing the existing touch-slop/`magnifyTouch` transition) and the release, for touch and for a mouse or stylus alike, while the
 * existing long-press-for-menu and keyboard/D-pad paths are untouched (they are separate detectors reading the same event stream non-destructively,
 * exactly as the magnify tracker always has).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
class DockGestureW60Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ran = mutableListOf<String>()
    private var iconCompositions = 0

    @Before fun setUp() { L10n.setLanguage("en"); DockInput.dialogHook = {}; ran.clear(); iconCompositions = 0; ReducedMotionTesting.force(MotionPrefs(1f, 1f, 1f)) }
    @After fun tearDown() { ReducedMotionTesting.force(null); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }

    private fun content(n: Int) = DockContent(
        pinned = (0 until n).map { DockItem(DockItemRef.app("app$it"), "App ${"ABCDEFGHIJKL"[it]}", false) },
        recent = emptyList(), showEnableRow = false, showRecentSection = false, collapsed = false,
    )
    private val colors = listOf(Color(0xFF4F8CFF), Color(0xFFFF7A59), Color(0xFF3DDC84), Color(0xFFE6B800), Color(0xFFB36BFF), Color(0xFF29C5D6))

    @Composable private fun Scene(n: Int = 4, edge: DockEdge = DockEdge.Bottom) {
        val align = when (edge) { DockEdge.Bottom -> Alignment.BottomCenter; DockEdge.Left -> Alignment.CenterStart; DockEdge.Right -> Alignment.CenterEnd }
        Box(Modifier.fillMaxSize().background(Color(0xFF1B2A55)).padding(16.dp), contentAlignment = align) {
            DockRail(
                content(n), edge, "Home", expanded = true, focusRequest = 0, showNumbers = false, graphBridge = null, style = DockStyle.Standard, dark = true,
                magnify = MagnifyStrength.Medium, editing = false,
                actions = DockActions(onLaunch = { i, _ -> ran += "launch ${i.label}" }, onOpenOptions = { ran += "options" }),
            ) { item, s ->
                SideEffect { iconCompositions++ }
                Box(Modifier.size(s).background(colors[item.ref.id.removePrefix("app").toInt() % colors.size]))
            }
        }
    }
    private fun widthOf(label: String) = rule.onNodeWithContentDescription(label).getBoundsInRoot().width.value
    private fun boundsOf(label: String) = rule.onNodeWithContentDescription(label).getBoundsInRoot()
    private fun settle(ms: Long = 450) { rule.mainClock.advanceTimeBy(ms); rule.waitForIdle() }

    // --- press-anywhere, drag, release-on-target ----------------------------------------------------------------------------------------------

    @Test fun DCK_04_gest_01_pressOnOneIconDragToAnotherAndReleaseLaunchesTheOneUnderTheFingerNotTheOneFirstPressed() {
        rule.setContent { Scene(4) }
        rule.onNodeWithContentDescription("App A").performTouchInput { down(center); moveBy(Offset(10f, 0f)) }
        settle(350)
        rule.onNodeWithContentDescription("App A").performTouchInput { moveTo(Offset(width * 3f, height / 2f)) } // drags across B, C onto D
        settle(350)
        rule.onNodeWithContentDescription("App A").performTouchInput { up() }
        assertEquals("released over D although the press started on A: D launches, not A", listOf("launch App D"), ran)
        settle(700)
    }

    @Test fun DCK_04_gest_02_pressDragOutsideTheRailThenReleaseInsideItCancelsTheWholeGesture() {
        rule.setContent { Scene(4) }
        rule.onNodeWithContentDescription("App B").performTouchInput { down(center); moveBy(Offset(10f, 0f)) }
        settle(300)
        val grownBeforeExit = widthOf("App B")
        assertTrue("engaged before the finger left", grownBeforeExit > 55f)
        // drag far above the dock (off a bottom dock, vertically) — well outside the rail's own bounds
        rule.onNodeWithContentDescription("App B").performTouchInput { moveTo(Offset(width / 2f, -400f)) }
        settle(300)
        assertEquals("springs back at once: cancelling does not wait for a release", 52f, widthOf("App B"), 1.5f)
        // the finger comes back over an icon and lifts: still nothing launches, the gesture stays cancelled for this press
        rule.onNodeWithContentDescription("App B").performTouchInput { moveTo(center); up() }
        settle(400)
        assertEquals("cancelled once, it stays cancelled even back inside", emptyList<String>(), ran)
    }

    @Test fun DCK_04_gest_03_plainTapWithNoDragStillLaunchesImmediatelyUnchanged() {
        rule.setContent { Scene(4) }
        rule.onNodeWithContentDescription("App B").performClick()
        rule.onNodeWithContentDescription("App D").performClick()
        assertEquals(listOf("launch App B", "launch App D"), ran)
    }

    @Test fun DCK_04_gest_04_longPressWithoutDragStillOpensTheMenuNotALaunch() {
        rule.setContent { Scene(4) }
        rule.onNodeWithContentDescription("App C").performTouchInput { down(center); moveBy(Offset(1f, 0f)) }
        rule.mainClock.advanceTimeBy(700)
        rule.waitForIdle()
        rule.onNodeWithTag(DockMenuModel.TAG_ITEM_MENU).assertExists()
        assertEquals("the long press opened the menu, not a launch", emptyList<String>(), ran)
        rule.onNodeWithContentDescription("App C").performTouchInput { up() }
    }

    @Test fun DCK_04_gest_05_aSideDockOnTheLeftCancelsWhenDraggedOffTheScreenSideNotJustAlongTheRow() {
        rule.setContent { Scene(4, edge = DockEdge.Left) }
        val rest = boundsOf("App B").height.value
        rule.onNodeWithContentDescription("App B").performTouchInput { down(center); moveBy(Offset(0f, 8f)) }
        settle(300)
        assertTrue(boundsOf("App B").height.value > rest * 1.4f)
        // a left dock's main axis is vertical; dragging further LEFT (off the screen edge, the cross axis) should cancel just as dragging
        // off either end of the row would.
        rule.onNodeWithContentDescription("App B").performTouchInput { moveTo(Offset(-400f, height / 2f)) }
        settle(300)
        assertEquals("sprang back", rest, boundsOf("App B").height.value, 4f)
        rule.onNodeWithContentDescription("App B").performTouchInput { up() }
        settle(400)
        assertEquals(emptyList<String>(), ran)
    }

    @Test fun DCK_04_gest_06_aSideDockOnTheRightDraggingAlongItsOwnAxisToAnotherIconStillLaunchesOnRelease() {
        rule.setContent { Scene(4, edge = DockEdge.Right) }
        rule.onNodeWithContentDescription("App A").performTouchInput { down(center); moveBy(Offset(0f, 8f)) }
        settle(300)
        rule.onNodeWithContentDescription("App A").performTouchInput { moveTo(Offset(width / 2f, height * 3f)) }
        settle(300)
        rule.onNodeWithContentDescription("App A").performTouchInput { up() }
        assertEquals(listOf("launch App D"), ran)
    }

    @Test fun DCK_04_gest_07_aMouseHasTheSamePressDragReleaseBehaviourAsAFinger() {
        rule.setContent { Scene(4) }
        val restA = boundsOf("App A"); val restD = boundsOf("App D")
        // D's rest centre, expressed in A's own local frame (performMouseInput's coordinates are local to the node the call started on): the
        // point under the pointer keeps its place when magnified, so moving to D's REST centre lands exactly on D once it is the grown one.
        val toD = Offset(restD.left.value + restD.width.value / 2f - restA.left.value, restD.top.value + restD.height.value / 2f - restA.top.value)
        rule.onNodeWithContentDescription("App A").performMouseInput { moveTo(center); press() }
        settle(300)
        rule.onNodeWithContentDescription("App A").performMouseInput { moveTo(Offset(toD.x * density, toD.y * density)) }
        settle(300)
        rule.onNodeWithContentDescription("App A").performMouseInput { release() }
        assertEquals("a mouse press-drag-release launches where the pointer is, not where the button went down", listOf("launch App D"), ran)
    }

    @Test fun DCK_04_gest_08_editDockKeepsTheOldPerItemLaunchUntouchedSoEditingIsNeverAffectedByTheGesture() {
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF1B2A55)).padding(16.dp), contentAlignment = Alignment.BottomCenter) {
                DockRail(
                    content(4), DockEdge.Bottom, "Home", expanded = true, focusRequest = 0, showNumbers = false, graphBridge = null,
                    style = DockStyle.Standard, dark = true, magnify = MagnifyStrength.Medium, editing = true,
                    actions = DockActions(onLaunch = { i, _ -> ran += "launch ${i.label}" }),
                ) { item, s -> Box(Modifier.size(s).background(colors[item.ref.id.removePrefix("app").toInt() % colors.size])) }
            }
        }
        // while editing, magnify is off regardless of the setting (DockRail's own `magnifyOn = magnify != null && !editing`): a drag does not grow
        // or launch anything (the plus tile and remove badges are what editing offers instead).
        val before = widthOf("App A")
        rule.onNodeWithContentDescription("App A").performTouchInput { down(center); moveBy(Offset(10f, 0f)) }
        settle(300)
        assertEquals("no magnification while editing", before, widthOf("App A"), 0.5f)
        rule.onNodeWithContentDescription("App A").performTouchInput { up() }
        assertEquals(emptyList<String>(), ran)
    }
}
