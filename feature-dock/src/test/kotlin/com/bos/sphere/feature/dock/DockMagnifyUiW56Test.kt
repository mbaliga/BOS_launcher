package com.bos.sphere.feature.dock

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.LayoutDirection
import com.bos.sphere.core.capabilities.Tier
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
 * Wave 56 (dockMagnify): the magnifying dock as the real [DockRail] draws and handles it: a finger drag along it grows the icons around the finger and
 * a release springs them back, a plain tap still launches exactly the pressed icon, the tooltip names the icon under the finger, Edit dock turns it
 * off, reduced motion applies it without a spring, side docks grow towards the screen centre, a right-to-left dock works, nothing recomposes per move.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
class DockMagnifyUiW56Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ran = mutableListOf<String>()
    private var iconCompositions = 0

    @Before fun setUp() { L10n.setLanguage("en"); DockInput.dialogHook = {}; ran.clear(); iconCompositions = 0; ReducedMotionTesting.force(MotionPrefs(1f, 1f, 1f)) }
    @After fun tearDown() { ReducedMotionTesting.force(null); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }

    private fun content(n: Int, used: Set<Int> = emptySet()) = DockContent(
        pinned = (0 until n).map { DockItem(DockItemRef.app("app$it"), "App ${"ABCDEFGHIJKL"[it]}", it in used) },
        recent = emptyList(), showEnableRow = false, showRecentSection = false, collapsed = false,
    )
    private val colors = listOf(Color(0xFF4F8CFF), Color(0xFFFF7A59), Color(0xFF3DDC84), Color(0xFFE6B800), Color(0xFFB36BFF), Color(0xFF29C5D6))

    @Composable private fun Scene(
        n: Int = 4, style: DockStyle = DockStyle.Standard, magnify: MagnifyStrength? = MagnifyStrength.Medium, edge: DockEdge = DockEdge.Bottom, editing: Boolean = false,
        rtl: Boolean = false, used: Set<Int> = emptySet(),
    ) {
        val align = when (edge) { DockEdge.Bottom -> Alignment.BottomCenter; DockEdge.Left -> Alignment.CenterStart; DockEdge.Right -> Alignment.CenterEnd }
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Box(Modifier.fillMaxSize().background(Color(0xFF1B2A55)).padding(16.dp), contentAlignment = align) {
                DockRail(
                    content(n, used), edge, "Home", expanded = true, focusRequest = 0, showNumbers = false, graphBridge = null, style = style, dark = true, magnify = magnify, editing = editing,
                    actions = DockActions(onLaunch = { i, _ -> ran += "launch ${i.label}" }, onOpenOptions = { ran += "options" }),
                ) { item, s ->
                    SideEffect { iconCompositions++ }
                    Box(Modifier.size(s).background(colors[item.ref.id.removePrefix("app").toInt() % colors.size]))
                }
            }
        }
    }
    private fun widthOf(label: String) = rule.onNodeWithContentDescription(label).getBoundsInRoot().width.value
    private fun boundsOf(label: String) = rule.onNodeWithContentDescription(label).getBoundsInRoot()
    private fun settle(ms: Long = 450) { rule.mainClock.advanceTimeBy(ms); rule.waitForIdle() }
    private fun tooltips() = rule.onAllNodesWithTag(DockMagnifyTags.TOOLTIP, useUnmergedTree = true)
    private fun tooltipText(): String? = tooltips().fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }

    // --------------------------------------------------------------------------------------------------------------------------------------

    @Test fun DCK_04_mag_ui_01_aTouchDragAlongTheDockGrowsTheIconsAroundTheFingerAndAReleaseSpringsThemBack() {
        rule.setContent { Scene(4) }
        val rest = listOf("App A", "App B", "App C", "App D").map { widthOf(it) }
        assertEquals("resting slot width is 52 dp", 52f, rest[2], 0.5f)
        rule.onNodeWithContentDescription("App C").performTouchInput { down(center); moveBy(Offset(10f, 0f)) }
        settle(350)
        val grown = listOf("App A", "App B", "App C", "App D").map { widthOf(it) }
        assertEquals("the icon under the finger grows by the Medium factor (1.6)", 52f * 1.6f, grown[2], 2f)
        assertTrue("its neighbours grow less (${grown[1]} / ${grown[3]})", grown[1] > rest[1] * 1.05f && grown[1] < grown[2] && grown[3] > rest[3] * 1.05f && grown[3] < grown[2])
        // the finger moves along the dock: the icon under it is now the big one
        rule.onNodeWithContentDescription("App C").performTouchInput { moveTo(Offset(width * 1.1f, height / 2f)) }
        settle(350)
        assertTrue("moving right shifts the peak right: D (${widthOf("App D")}) is now bigger than B (${widthOf("App B")})", widthOf("App D") > widthOf("App B"))
        rule.onNodeWithContentDescription("App C").performTouchInput { up() }
        settle(700)
        listOf("App A", "App B", "App C", "App D").forEachIndexed { i, l -> assertEquals("$l springs back to its resting size", rest[i], widthOf(l), 0.8f) }
        assertEquals("nothing launched by a drag", emptyList<String>(), ran)
    }

    @Test fun DCK_04_mag_ui_01_theGrowthIsAnimatedNotInstant() {
        rule.mainClock.autoAdvance = false
        rule.setContent { Scene(4) }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithContentDescription("App C").performTouchInput { down(center); moveBy(Offset(10f, 0f)) }
        rule.mainClock.advanceTimeBy(32)
        val early = widthOf("App C")
        rule.mainClock.advanceTimeBy(400)
        val late = widthOf("App C")
        assertTrue("after two frames the spring has not finished ($early vs $late)", early < late - 3f)
        assertEquals(52f * 1.6f, late, 2f)
        rule.onNodeWithContentDescription("App C").performTouchInput { up() }
    }

    @Test fun DCK_04_mag_ui_02_aPlainTapStillLaunchesExactlyThePressedIcon() {
        rule.setContent { Scene(4) }
        rule.onNodeWithContentDescription("App B").performClick()
        rule.onNodeWithContentDescription("App D").performClick()
        assertEquals(listOf("launch App B", "launch App D"), ran)
    }

    @Test fun DCK_04_mag_ui_02_aMouseHoveringTheDockMagnifiesItAndAClickLaunchesTheIconUnderThePointerWithTheMagnifiedGeometry() {
        rule.setContent { Scene(4) }
        rule.onNodeWithContentDescription("App B").performMouseInput { moveTo(center) }
        settle()
        assertEquals("hovering grows B at once, no press needed", 52f * 1.6f, widthOf("App B"), 2f)
        // the click goes to where D is drawn NOW (magnified, shifted), not where it rested
        rule.onNodeWithContentDescription("App D").performMouseInput { moveTo(center) }
        settle()
        assertTrue("the pointer is over D, so D is the big one now (${widthOf("App D")}; the room at the end of the row limits it a little)", widthOf("App D") > 52f * 1.4f)
        rule.onNodeWithContentDescription("App D").performMouseInput { click() }
        assertEquals(listOf("launch App D"), ran)
    }

    @Test fun DCK_04_mag_ui_02_aClickOnTheMagnifiedIconsOuterPartHitsItNotItsNeighbour() {
        rule.setContent { Scene(4) }
        rule.onNodeWithContentDescription("App B").performMouseInput { moveTo(center) }
        settle()
        val b = boundsOf("App B"); val a = boundsOf("App A")
        // B's right part (past where its resting slot ended) now belongs to B; the neighbour C starts further right
        val x = b.right.value - 4f; val y = (b.top.value + b.bottom.value) / 2f
        assertTrue("B reaches beyond its resting width", b.width.value > 60f)
        rule.onNodeWithContentDescription("App B").performMouseInput { moveTo(Offset(width - 4f * density, height / 2f)); click(Offset(width - 4f * density, height / 2f)) }
        assertEquals(listOf("launch App B"), ran)
        assertTrue("A is to the left of B and did not launch (${a.right.value} <= $x at y=$y)", a.right.value <= b.left.value + 1f)
    }

    @Test fun DCK_04_mag_ui_03_theTooltipNamesTheIconUnderTheFingerAndGoesWhenItLifts() {
        rule.setContent { Scene(4) }
        tooltips().assertCountEquals(0)
        rule.onNodeWithContentDescription("App C").performTouchInput { down(center); moveBy(Offset(6f, 0f)) }
        settle(300)
        assertEquals("App C", tooltipText())
        rule.onNodeWithContentDescription("App C").performTouchInput { moveTo(Offset(width * 1.1f, height / 2f)) }
        settle(300)
        assertEquals("the bubble follows the finger to the next icon", "App D", tooltipText())
        rule.onNodeWithContentDescription("App C").performTouchInput { up() }
        settle(700)
        tooltips().assertCountEquals(0)
    }

    @Test fun DCK_04_mag_ui_03_theTooltipIsAboveTheIconForTheBottomDock() {
        rule.setContent { Scene(4) }
        rule.onNodeWithContentDescription("App C").performTouchInput { down(center); moveBy(Offset(6f, 0f)) }
        settle(300)
        val tip = tooltips().fetchSemanticsNodes().first().boundsInRoot
        val icon = boundsOf("App C")
        assertTrue("bubble bottom (${tip.bottom}) is above the icon's top (${icon.top.value})", tip.bottom <= rule.onNodeWithContentDescription("App C").fetchSemanticsNode().boundsInRoot.top + 8f)
        rule.onNodeWithContentDescription("App C").performTouchInput { up() }
    }

    @Test fun DCK_04_mag_ui_04_editDockTurnsItOffNoGrowthAndNoTooltip() {
        rule.setContent { Scene(4, editing = true) }
        val before = widthOf("App C")
        rule.onNodeWithContentDescription("App C").performTouchInput { down(center); moveBy(Offset(10f, 0f)) }
        settle(350)
        assertEquals("no magnification while editing", before, widthOf("App C"), 0.5f)
        tooltips().assertCountEquals(0)
        rule.onNodeWithContentDescription("App C").performTouchInput { up() }
    }

    @Test fun DCK_04_mag_ui_04_withTheSettingOffNothingGrowsAndOnlyThe3DLookShowsATooltip() {
        rule.setContent { Scene(4, magnify = null) }
        val before = widthOf("App C")
        rule.onNodeWithContentDescription("App C").performTouchInput { down(center); moveBy(Offset(10f, 0f)) }
        settle(350)
        assertEquals(before, widthOf("App C"), 0.5f); tooltips().assertCountEquals(0)
        rule.onNodeWithContentDescription("App C").performTouchInput { up() }
    }

    @Test fun DCK_04_mag_ui_04_the3DLookShowsTheTooltipOnAPressEvenWithoutMagnification() {
        rule.setContent { Scene(4, style = DockStyle.ThreeD, magnify = null) }
        val before = widthOf("App C")
        rule.onNodeWithContentDescription("App C").performTouchInput { down(center) }
        settle(100)
        assertEquals("App C", tooltipText()); assertEquals("no growth without the setting", before, widthOf("App C"), 0.5f)
        rule.onNodeWithContentDescription("App C").performTouchInput { up() }
        assertEquals("the press still launched it", listOf("launch App C"), ran)
    }

    @Test fun DCK_04_mag_ui_05_reducedMotionAppliesTheEffectWithoutASpringAndWithoutOvershoot() {
        ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f))
        rule.mainClock.autoAdvance = false
        rule.setContent { Scene(4) }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithContentDescription("App C").performTouchInput { down(center); moveBy(Offset(10f, 0f)) }
        rule.mainClock.advanceTimeBy(32)
        val early = widthOf("App C")
        assertEquals("at once, at the final size", 52f * 1.6f, early, 1.5f)
        var peak = early
        for (i in 0 until 12) { rule.mainClock.advanceTimeBy(16); peak = maxOf(peak, widthOf("App C")) }
        assertTrue("no overshoot ($peak)", peak <= 52f * 1.6f + 1f)
        rule.onNodeWithContentDescription("App C").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(32)
        assertEquals("and it is gone at once on release", 52f, widthOf("App C"), 1f)
    }

    @Test fun DCK_04_mag_ui_06_aSideDockOnTheLeftGrowsTowardsTheScreenCentre() {
        rule.setContent { Scene(4, edge = DockEdge.Left) }
        val rest = boundsOf("App C")
        rule.onNodeWithContentDescription("App C").performTouchInput { down(center); moveBy(Offset(0f, 8f)) }
        settle(350)
        val big = boundsOf("App C")
        assertTrue("it grew (${big.height.value})", big.height.value > rest.height.value * 1.4f && big.width.value > rest.width.value * 1.4f)
        assertEquals("the screen-side edge stays where it was", rest.left.value, big.left.value, 3f)
        assertTrue("the icon grows towards the centre: its right edge moved right", big.right.value > rest.right.value + 10f)
        rule.onNodeWithContentDescription("App C").performTouchInput { up() }
    }

    @Test fun DCK_04_mag_ui_06_aSideDockOnTheRightGrowsLeftTowardsTheCentre() {
        rule.setContent { Scene(4, edge = DockEdge.Right) }
        val rest = boundsOf("App C")
        rule.onNodeWithContentDescription("App C").performTouchInput { down(center); moveBy(Offset(0f, 8f)) }
        settle(350)
        val big = boundsOf("App C")
        assertTrue(big.height.value > rest.height.value * 1.4f)
        assertEquals("the screen-side edge stays", rest.right.value, big.right.value, 3f)
        assertTrue("the left edge moved left", big.left.value < rest.left.value - 10f)
        rule.onNodeWithContentDescription("App C").performTouchInput { up() }
    }

    @Test fun DCK_04_mag_ui_07_aRightToLeftDockMagnifiesTheIconUnderTheFinger() {
        rule.setContent { Scene(4, rtl = true) }
        // in RTL the first app is the rightmost; the finger on C (physical middle) grows C, not its mirror image
        val rest = widthOf("App C")
        rule.onNodeWithContentDescription("App C").performTouchInput { down(center); moveBy(Offset(-8f, 0f)) }
        settle(350)
        assertEquals(52f * 1.6f, widthOf("App C"), 2f)
        assertTrue(widthOf("App C") > rest * 1.4f)
        assertEquals("App C", tooltipText())
        val c = boundsOf("App C"); val b = boundsOf("App B"); val d = boundsOf("App D")
        assertTrue("order is mirrored: B is right of C, D left of C", b.left.value > c.left.value && d.left.value < c.left.value)
        rule.onNodeWithContentDescription("App C").performTouchInput { up() }
    }

    @Test fun DCK_04_mag_ui_08_aDockOfTenIconsMagnifiesWithoutRecomposingOnEveryMove() {
        rule.setContent { Scene(10, magnify = MagnifyStrength.Large) }
        rule.onNodeWithContentDescription("App C").performTouchInput { down(center); moveBy(Offset(4f, 0f)) }
        settle(350)
        val before = iconCompositions
        for (i in 0 until 30) {
            rule.onNodeWithContentDescription("App C").performTouchInput { moveBy(Offset(if (i % 2 == 0) 2f else -2f, 0f)) }
            rule.mainClock.advanceTimeBy(16)
        }
        rule.waitForIdle()
        assertTrue("magnified with 10 icons", widthOf("App C") > 52f * 1.5f)
        assertEquals("30 pointer moves composed no icon again (only graphics layers moved)", before, iconCompositions)
        rule.onNodeWithContentDescription("App C").performTouchInput { up() }
    }

    @Test fun DCK_04_mag_ui_09_arrowKeysMoveTheEffectAlongTheDockWithFocus() {
        rule.setContent { Scene(4) }
        rule.onNodeWithContentDescription("App B").requestFocus()
        settle(500)
        assertTrue("the focused icon grows (${widthOf("App B")})", widthOf("App B") > 52f * 1.4f)
        assertEquals("and shows its name", "App B", tooltipText())
        rule.onNodeWithContentDescription("App B").performKeyInput { pressKey(Key.DirectionRight) }
        settle(600)
        assertTrue("focus moved to C and so did the effect (C ${widthOf("App C")} vs B ${widthOf("App B")})", widthOf("App C") > widthOf("App B"))
        assertEquals("App C", tooltipText())
    }

    @Test fun DCK_04_mag_ui_10_aLongPressOnAnIconStillOpensItsMenuWhileMagnified() {
        rule.setContent { Scene(4) }
        rule.onNodeWithContentDescription("App C").performTouchInput { down(center); moveBy(Offset(1f, 0f)) }
        rule.mainClock.advanceTimeBy(700)
        rule.waitForIdle()
        rule.onNodeWithTag(DockMenuModel.TAG_ITEM_MENU).assertExists()
        rule.onNodeWithContentDescription("App C").performTouchInput { up() }
    }

    @Test fun DCK_04_mag_ui_11_theDotsOfRunningAppsStayOnTheShelfWhileTheIconsGrow() {
        rule.setContent { Scene(4, style = DockStyle.ThreeD, used = setOf(1, 2)) }
        rule.onAllNodesWithTag("dock.activeDot", useUnmergedTree = true).assertCountEquals(2)
        val dot0 = rule.onAllNodesWithTag("dock.activeDot", useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
        rule.onNodeWithContentDescription("App B, used this session").performTouchInput { down(center); moveBy(Offset(6f, 0f)) }
        settle(350)
        val dot1 = rule.onAllNodesWithTag("dock.activeDot", useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
        assertEquals("the dot keeps its height on the shelf lip", dot0.center.y, dot1.center.y, 4f)
        rule.onNodeWithContentDescription("App B, used this session").performTouchInput { up() }
    }

    // ---- the options (the dock sheet and Settings > Dock share this body) ------------------------------------------------------------------------

    @Composable private fun LiveScene(m: WorkspaceManager) {
        val st by m.state.collectAsState()
        Column(Modifier.fillMaxSize().background(Color(0xFF1B2A55))) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                DockRail(
                    content(4), DockEdge.Bottom, "Home", expanded = true, focusRequest = 0, showNumbers = false, graphBridge = null, style = st.dock.style, dark = true,
                    magnify = st.dock.magnifyStrength.takeIf { st.dock.magnify }, actions = DockActions(onLaunch = { i, _ -> ran += "launch ${i.label}" }),
                ) { _, s -> Box(Modifier.size(s).background(Color(0xFF3DDC84))) }
            }
            Column(Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState())) { DockOptionsBody(m, Tier.T2, DockOptionsScope()) }
        }
    }

    @Test fun DCK_04_mag_ui_12_theMagnifySwitchIsOffByDefaultThenShowsThreeStrengthCardsAndEveryChoiceIsLiveOnTheDock() {
        val m = WorkspaceManager(InMemoryWorkspaceStore())
        rule.setContent { LiveScene(m) }
        rule.onNodeWithTag(DockOptionTags.MAGNIFY).performScrollTo().assertExists()
        assertFalse("off by default", m.current.dock.magnify)
        MagnifyStrength.entries.forEach { rule.onNodeWithTag(DockOptionTags.strength(it)).assertDoesNotExist() }
        // off: a drag on the dock grows nothing
        val rest = widthOf("App C")
        rule.onNodeWithContentDescription("App C").performTouchInput { down(center); moveBy(Offset(10f, 0f)) }
        settle(350); assertEquals(rest, widthOf("App C"), 0.5f)
        rule.onNodeWithContentDescription("App C").performTouchInput { up() }; settle(500)
        // on: three drawn cards (radio buttons, Medium selected), the dock reacts at once
        rule.onNodeWithTag(DockOptionTags.MAGNIFY).performScrollTo().performClick(); settle(100)
        assertTrue(m.current.dock.magnify)
        MagnifyStrength.entries.forEach { st ->
            val node = rule.onNodeWithTag(DockOptionTags.strength(st)).performScrollTo()
            node.assertExists()
            assertEquals(androidx.compose.ui.semantics.Role.RadioButton, node.fetchSemanticsNode().config[SemanticsProperties.Role])
        }
        rule.onNodeWithTag(DockOptionTags.strength(MagnifyStrength.Medium)).assertIsSelected()
        rule.onNodeWithText("Medium 1.6x").assertExists(); rule.onNodeWithText("Subtle 1.3x").assertExists(); rule.onNodeWithText("Large 2.0x").assertExists()
        rule.onNodeWithContentDescription("App C").performTouchInput { down(center); moveBy(Offset(10f, 0f)) }
        settle(350); val medium = widthOf("App C")
        rule.onNodeWithContentDescription("App C").performTouchInput { up() }; settle(500)
        assertEquals(52f * 1.6f, medium, 2f)
        rule.onNodeWithTag(DockOptionTags.strength(MagnifyStrength.Large)).performScrollTo().performClick(); settle(100)
        assertEquals(MagnifyStrength.Large, m.current.dock.magnifyStrength)
        rule.onNodeWithTag(DockOptionTags.strength(MagnifyStrength.Large)).assertIsSelected()
        rule.onNodeWithContentDescription("App C").performTouchInput { down(center); moveBy(Offset(10f, 0f)) }
        settle(350); val large = widthOf("App C")
        rule.onNodeWithContentDescription("App C").performTouchInput { up() }; settle(500)
        assertTrue("Large grows more than Medium ($large vs $medium)", large > medium + 10f)
        rule.onNodeWithTag(DockOptionTags.strength(MagnifyStrength.Subtle)).performScrollTo().performClick(); settle(100)
        rule.onNodeWithContentDescription("App C").performTouchInput { down(center); moveBy(Offset(10f, 0f)) }
        settle(350); val subtle = widthOf("App C")
        rule.onNodeWithContentDescription("App C").performTouchInput { up() }; settle(500)
        assertEquals(52f * 1.3f, subtle, 2f)
        // off again: the cards go
        rule.onNodeWithTag(DockOptionTags.MAGNIFY).performScrollTo().performClick(); settle(100)
        assertFalse(m.current.dock.magnify)
        MagnifyStrength.entries.forEach { rule.onNodeWithTag(DockOptionTags.strength(it)).assertDoesNotExist() }
    }
}
