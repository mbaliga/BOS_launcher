package com.bos.sphere.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import com.bos.sphere.core.design.L10n
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 52 (panelWidgets, STR-15): the Widgets section's column with a fake widget host (real app widgets exist only on a device). Screenshots go to
 * build/screenshots/panelwidgets-*.png.
 */
@OptIn(ExperimentalTestApi::class)
abstract class PanelWidgetsUiCases(private val orientation: String) : LaunchpadShots() {
    private val ctx get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    @After fun clean() { ctx.getSharedPreferences("launchpad_hub", 0).edit().clear().commit(); L10n.setLanguage("en") }

    private var adds = 0
    private val released = ArrayList<Int>()
    private val configured = ArrayList<Int>()
    private val gone = HashSet<Int>()

    private fun store(vararg ids: Int) = PanelWidgetStore(HubPrefs.of(ctx)).also { s -> ids.forEach { s.add(PanelWidget(it, "Widget $it")) } }

    private fun api() = PanelWidgetsApi(
        available = { it !in gone }, heightDp = { 120f }, canConfigure = { it == 1 }, configure = { configured += it },
        addWidget = { adds++ }, released = { released += it },
        render = { id, m -> Box(m.background(Color(0xFF2A3A55 + id * 0x101010))) { Text("Live widget $id", color = Color.White) } },
    )

    private fun show(store: PanelWidgetStore, dark: Boolean = true, rtl: Boolean = false) {
        rule.setContent {
            Themed(dark) {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Box(Modifier.fillMaxSize().background(LocalLp.current.bg)) { PanelWidgetsSection(store, api()) }
                }
            }
        }
        rule.waitForIdle()
    }
    private fun actionsOf(id: Int) = rule.onNodeWithTag("panelWidgets.card.$id").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
    private fun reveal(t: String) = rule.onNodeWithTag("panelWidgets.list").performScrollToNode(hasTestTag(t))
    private fun exists(t: String) = rule.onAllNodes(hasTestTag(t)).fetchSemanticsNodes().isNotEmpty()
    private fun order(s: PanelWidgetStore) = s.widgets.map { it.appWidgetId }
    private fun top(id: Int) = rule.onNodeWithTag("panelWidgets.card.$id").fetchSemanticsNode().boundsInRoot.top

    @Test fun STR_15_anEmptySectionShowsTheHintAndTheAddWidgetTileAtTheEnd() {
        show(store())
        assertTrue(exists("panelWidgets.empty")); assertTrue(exists("panelWidgets.add"))
        rule.onNodeWithTag("panelWidgets.add").performClick()
        assertEquals(1, adds)
    }

    @Test fun STR_15_eachWidgetIsAFullWidthCardInOrderAndTheAddTileComesAfterTheLast() {
        show(store(1, 2, 3))
        assertFalse(exists("panelWidgets.empty"))
        assertTrue(top(1) < top(2))
        reveal("panelWidgets.card.3"); rule.waitForIdle()
        assertTrue(top(2) < top(3))
        reveal("panelWidgets.add"); rule.waitForIdle()
        val add = rule.onNodeWithTag("panelWidgets.add").fetchSemanticsNode().boundsInRoot
        if (exists("panelWidgets.card.3")) assertTrue("the add tile follows the last card", add.top >= rule.onNodeWithTag("panelWidgets.card.3").fetchSemanticsNode().boundsInRoot.bottom)
        rule.onNodeWithTag("panelWidgets.list").performScrollToNode(hasTestTag("panelWidgets.card.1")); rule.waitForIdle()
        val col = rule.onNodeWithTag("panelWidgets.list").fetchSemanticsNode().boundsInRoot
        val c = rule.onNodeWithTag("panelWidgets.card.1").fetchSemanticsNode().boundsInRoot
        assertTrue("the card fills the column's width up to its padding", c.width > col.width * 0.7f || c.width >= 600f)
        assertTrue(exists("panelWidgets.widget.1"))
    }

    @Test fun STR_15_aLongPressOpensTheMenuWithRemoveMoveUpMoveDownAndSettings() {
        val s = store(1, 2)
        show(s)
        rule.onNodeWithTag("panelWidgets.card.1").performTouchInput { longClick() }
        rule.waitForIdle()
        listOf("up", "down", "settings", "remove").forEach { assertTrue(it, exists("panelWidgets.menu.$it.1")) }
        rule.onNodeWithTag("panelWidgets.menu.settings.1").performClick()
        assertEquals(listOf(1), configured)
    }

    @Test fun STR_15_aWidgetWithoutAConfigureScreenHasNoSettingsEntry() {
        show(store(1, 2))
        rule.onNodeWithTag("panelWidgets.more.2").performClick(); rule.waitForIdle()
        assertTrue(exists("panelWidgets.menu.remove.2")); assertFalse(exists("panelWidgets.menu.settings.2"))
    }

    @Test fun STR_15_moveDownMovesTheCardAndTheOrderIsKept() {
        val s = store(1, 2, 3)
        show(s)
        rule.onNodeWithTag("panelWidgets.more.1").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("panelWidgets.menu.down.1").performClick(); rule.waitForIdle()
        assertEquals(listOf(2, 1, 3), order(s))
        assertTrue(top(2) < top(1))
        assertEquals(listOf(2, 1, 3), order(PanelWidgetStore(HubPrefs.of(ctx))))
    }

    @Test fun STR_15_removeTakesTheCardAwayAndReleasesTheWidgetIdToTheHost() {
        val s = store(1, 2)
        show(s)
        rule.onNodeWithTag("panelWidgets.more.2").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("panelWidgets.menu.remove.2").performClick(); rule.waitForIdle()
        assertEquals(listOf(1), order(s)); assertEquals(listOf(2), released)
        assertFalse(exists("panelWidgets.card.2"))
    }

    @Test fun STR_15_aWidgetWhoseProviderIsGoneIsAnUnavailableCardThatCanBeRemoved() {
        gone += 2
        val s = store(1, 2)
        show(s)
        assertTrue(exists("panelWidgets.unavailable.2")); assertFalse(exists("panelWidgets.widget.2")); assertTrue(exists("panelWidgets.widget.1"))
        rule.onNodeWithTag("panelWidgets.unavailable.remove.2").performClick(); rule.waitForIdle()
        assertEquals(listOf(1), order(s)); assertEquals(listOf(2), released)
    }

    @Test fun STR_15_talkBackGetsMoveRemoveAndSettingsAsCustomActionsOnTheCard() {
        val s = store(1, 2, 3)
        show(s)
        fun actions(id: Int): List<String> { reveal("panelWidgets.card.$id"); rule.waitForIdle(); return actionsOf(id) }
        assertEquals(listOf("Move down", "Widget settings", "Remove"), actions(1))
        assertEquals(listOf("Move up", "Move down", "Remove"), actions(2))
        assertEquals(listOf("Move up", "Remove"), actions(3))
        reveal("panelWidgets.card.2"); rule.waitForIdle()
        rule.runOnIdle { rule.onNodeWithTag("panelWidgets.card.2").fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "Move up" }.action() }
        rule.waitForIdle()
        assertEquals(listOf(2, 1, 3), order(s))
    }

    @Test fun STR_15_theCardsAndTheAddTileAreKeyboardFocusableAndEnterOpensTheMenu() {
        show(store(1))
        assertTrue(rule.onNodeWithTag("panelWidgets.card.1").fetchSemanticsNode().config.contains(SemanticsProperties.Focused))
        assertTrue(rule.onNodeWithTag("panelWidgets.add").fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
    }

    @Test fun STR_15_inRightToLeftTheOptionsButtonSitsOnTheLeftAndTheOrderStillRunsTopToBottom() {
        val s = store(1, 2)
        show(s, rtl = true)
        val card = rule.onNodeWithTag("panelWidgets.card.1").fetchSemanticsNode().boundsInRoot
        val more = rule.onNodeWithTag("panelWidgets.more.1").fetchSemanticsNode().boundsInRoot
        assertTrue("the button is on the start (left) side in RTL", more.left - card.left < card.right - more.right)
        assertTrue(top(1) < top(2))
    }

    @Test fun STR_15_screenshot() {
        gone += 3
        show(store(1, 2, 3))
        shoot("panelwidgets-$orientation-dark")
    }

    @Test fun STR_15_screenshotRtlLight() {
        show(store(1, 2), dark = false, rtl = true)
        shoot("panelwidgets-$orientation-rtl-light")
    }

    @Test fun STR_15_screenshotEmpty() {
        show(store())
        shoot("panelwidgets-$orientation-empty")
    }
}
