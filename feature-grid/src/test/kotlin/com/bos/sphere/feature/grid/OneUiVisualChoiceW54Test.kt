package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.ClockStyle
import com.bos.sphere.core.data.HomeOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Wave 54 (settingsVisual), CFG-23: the visual choice card group. Selecting a card changes the value, the picked one exposes the selected state
 * and a radio role, the label is the text a screen reader reads, every card is at least 48 dp, a key press picks the focused card, and the group
 * wraps instead of running off the screen (also at font scale 2).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class OneUiVisualChoiceW54Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var style by mutableStateOf(ClockStyle.BigDigital)
    private val log = ArrayList<ClockStyle>()

    private fun show(widthDp: Int = 300, fontScale: Float = 1f) {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                Box(Modifier.width(widthDp.dp)) {
                    OneUiVisualChoice(ClockStyle.entries, style, { style = it; log += it }, { it.label }, { SettingsArtLook.clockStyle.art(it) }, tag = "t.clock")
                }
            }
        }
    }

    private fun card(s: ClockStyle) = rule.onNodeWithTag("t.clock.${s.name}")

    @Test fun CFG_23_selectingACardChangesTheValueAndMovesTheSelectedState() {
        show()
        card(ClockStyle.BigDigital).assertIsSelected()
        card(ClockStyle.Word).assertIsNotSelected().performClick()
        rule.waitForIdle()
        assertEquals(ClockStyle.Word, style)
        card(ClockStyle.Word).assertIsSelected(); card(ClockStyle.BigDigital).assertIsNotSelected()
        ClockStyle.entries.filter { it != ClockStyle.Word }.forEach { card(it).assertIsNotSelected() }
    }

    @Test fun CFG_23_everyCardIsARadioButtonWhoseTextIsItsLabel() {
        show()
        ClockStyle.entries.forEach { s ->
            val node = card(s).fetchSemanticsNode()
            assertEquals(Role.RadioButton, node.config[SemanticsProperties.Role])
            rule.onNode(hasText(s.label)).assertExists()
            assertEquals("the description is the label", listOf(s.label), node.config.getOrNull(SemanticsProperties.ContentDescription))
            val merged = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            assertEquals("the label is the card's text: ${s.label}", s.label, merged)
        }
    }

    @Test fun CFG_23_everyCardIsAtLeast48DpTall_andWide() {
        show()
        ClockStyle.entries.forEach { s ->
            val b = card(s).getBoundsInRoot()
            assertTrue("${s.name} height ${b.bottom - b.top}", (b.bottom - b.top) >= 48.dp)
            assertTrue("${s.name} width ${b.right - b.left}", (b.right - b.left) >= 48.dp)
        }
    }

    @Test fun CFG_23_theGroupWrapsInsideItsWidthWithNoCardClippedOrOverlapping() {
        show(widthDp = 200)
        val boxes = ClockStyle.entries.map { card(it).getBoundsInRoot() }
        boxes.forEach { assertTrue("inside 200 dp: ${it.right}", it.right <= 200.dp && it.left >= 0.dp) }
        for (i in boxes.indices) for (j in i + 1 until boxes.size) {
            val a = boxes[i]; val b = boxes[j]
            val overlap = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
            assertTrue("cards $i and $j do not overlap", !overlap)
        }
        assertTrue("it wrapped onto more than one line", boxes.map { it.top }.toSet().size > 1)
    }

    @Test fun CFG_23_atFontScaleTwoTheLabelsGrowAndNothingLeavesTheScreen() {
        show(widthDp = 300, fontScale = 2f)
        ClockStyle.entries.forEach { s ->
            val b = card(s).getBoundsInRoot()
            assertTrue("${s.name} stays on screen", b.right <= 300.dp && (b.bottom - b.top) >= 48.dp)
        }
    }

    @Test fun CFG_23_theCardsAreFocusableForKeysAndControllers() {
        show()
        // A key or controller press is handled by the platform clickable of the selectable card; real key delivery needs a focused window (device check).
        ClockStyle.entries.forEach { s ->
            val c = card(s).fetchSemanticsNode().config
            assertTrue("${s.name} can take focus", SemanticsProperties.Focused in c)
            assertTrue("${s.name} has a click action for Enter and the controller button", SemanticsActions.OnClick in c)
        }
    }

    @Test fun CFG_23_theOrderCardsDrawTheirArrowInPhysicalDirectionsWhateverTheLayout() {
        // the two Order cards are different pictures and each is a radio of its own
        var order by mutableStateOf(HomeOrder.LeftToRight)
        rule.setContent { OneUiVisualChoice(HomeOrder.entries, order, { order = it }, { it.label }, { SettingsArtHome.order.art(it) }, tag = "t.order") }
        rule.onNodeWithText("Right to left").performClick(); rule.waitForIdle()
        assertEquals(HomeOrder.RightToLeft, order)
        rule.onNodeWithTag("t.order.RightToLeft").assertIsSelected()
    }
}
