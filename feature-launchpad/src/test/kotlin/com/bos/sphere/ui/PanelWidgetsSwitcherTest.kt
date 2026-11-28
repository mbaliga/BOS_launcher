package com.bos.sphere.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Wave 52 (panelWidgets, STR-15): the panel's own section switcher shows the Widgets tab only when the setting is on, as the fifth tab, and it stays reachable on a narrow phone and in RTL. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi", sdk = [35])
class PanelWidgetsSwitcherTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun reset() { com.bos.sphere.core.data.CommsScaleSettings.resetForTest() }

    private fun host(hub: HubState, rtl: Boolean = false) = rule.setContent {
        CompositionLocalProvider(LocalLp provides LpDark, LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            CommsScaleHost(100) { Box(Modifier.fillMaxWidth()) { HubSwitcher(hub) } }
        }
    }
    private fun exists(t: String) = rule.onAllNodes(hasTestTag(t)).fetchSemanticsNodes().isNotEmpty()

    @Test fun STR_15_theWidgetsTabIsNotThereUntilTheSettingIsOnThenItIsTheFifth() {
        val hub = HubState(HubPrefs(null), null, true)
        host(hub); rule.waitForIdle()
        assertFalse(exists("hub.view.widgets"))
        rule.runOnIdle { Snapshot.withMutableSnapshot { hub.applyWidgets(true, WidgetsSlot.Last) } }; rule.waitForIdle()
        assertTrue(exists("hub.view.widgets"))
        val xs = hub.tabs.map { rule.onNodeWithTag("hub.view.${it.id}").fetchSemanticsNode().boundsInRoot.left }
        assertEquals(5, xs.size); assertEquals(xs.sorted(), xs)
        rule.onNodeWithTag("hub.view.widgets").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("hub.view.widgets").assertIsSelected()
        assertEquals(HubView.Widgets, hub.view)
    }

    @Test fun STR_15_inRightToLeftTheFifthTabIsOnTheLeftAndStillInSight() {
        val hub = HubState(HubPrefs(null), null, true)
        rule.runOnUiThread { Snapshot.withMutableSnapshot { hub.applyWidgets(true, WidgetsSlot.Last) } }
        host(hub, rtl = true); rule.waitForIdle()
        val row = rule.onNodeWithTag("hub.switcher").fetchSemanticsNode().boundsInRoot
        val first = rule.onNodeWithTag("hub.view.all").fetchSemanticsNode().boundsInRoot
        val last = rule.onNodeWithTag("hub.view.widgets").fetchSemanticsNode().boundsInRoot
        assertTrue("All is the right-most tab in RTL", first.left > last.left)
        rule.runOnIdle { Snapshot.withMutableSnapshot { hub.select(HubView.Widgets) } }; rule.waitForIdle()
        val sel = rule.onNodeWithTag("hub.view.widgets").fetchSemanticsNode().boundsInRoot
        assertTrue("the selected Widgets tab is scrolled into the row", sel.left >= row.left - 1f && sel.right <= row.right + 1f)
    }
}
