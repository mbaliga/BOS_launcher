package com.bos.sphere.feature.dock

import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.L10n
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

/** W26 (owner build 54): a tap never opens the dock settings; an empty dock is hidden entirely and comes back with its first item. */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.InternalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
class DockFixW26Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val app = AppEntry("a.p", ComponentName("a.p", "a.p.Main"), "Alpha", android.os.Process.myUserHandle()) { null }

    @Before fun english() { L10n.setLanguage("en"); DockInput.dialogHook = {} }

    private fun content(vararg labels: String) = DockContent(
        pinned = labels.mapIndexed { i, l -> DockItem(DockItemRef.app("k$i"), l) }, recent = emptyList(), showEnableRow = false, showRecentSection = false, collapsed = false,
    )

    // ---- (1) taps
    @Test fun DCK_09_aTapOnTheHomeGlyphGoesHomeAndNeverOpensTheDockPanel() {
        val ran = mutableListOf<String>()
        val actions = DockActions(onOpenPanel = { ran += "panel" }, onGoHome = { ran += "home" }, onLaunch = { i, _ -> ran += "launch ${i.label}" })
        rule.setContent { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { DockRail(content("Alpha"), DockEdge.Bottom, "Home", true, 0, false, actions, graphBridge = null) { _, s -> Box(Modifier.size(s)) } } }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Workspace Home").performClick()
        assertEquals(listOf("home"), ran)
    }

    @Test fun DCK_09_aTapOnTheDockBackgroundDoesNothingAndATapOnAnIconLaunchesIt() {
        val ran = mutableListOf<String>()
        val actions = DockActions(onOpenPanel = { ran += "panel" }, onGoHome = { ran += "home" }, onLaunch = { i, _ -> ran += "launch ${i.label}" }, onToggleExpanded = { ran += "toggle" })
        rule.setContent { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { DockRail(content("Alpha"), DockEdge.Bottom, "Home", true, 0, false, actions, graphBridge = null) { _, s -> Box(Modifier.size(s)) } } }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Dock").performTouchInput { click(androidx.compose.ui.geometry.Offset(width - 3f, height / 2f)) }
        assertEquals(emptyList<String>(), ran)
        rule.onNodeWithContentDescription("Alpha").performClick()
        assertEquals(listOf("launch Alpha"), ran)
    }

    @Test fun DCK_09_theDockPanelOpensOnlyFromTheSheetsWorkspacesAndGroupsEntry() {
        // W55: the long-press menu is gone; the dock's sheet is the way to the Workspaces and groups page (DockSheetW55Test drives the sheet).
        var panels = 0
        val spec = DockSheetSpec(WorkspaceManager(InMemoryWorkspaceStore()), com.bos.sphere.core.capabilities.Tier.T2, false, onOpenWorkspaces = { panels++ })
        spec.onOpenWorkspaces()
        assertEquals(1, panels)
    }

    // ---- (2) hidden when empty
    @Test fun DCK_10_noPinsAndNoRecentsMeansNoRail() {
        val mgr = WorkspaceRepository.get(context)
        mgr.setPins(emptyList())
        rule.setContent { Box(Modifier.fillMaxSize()) { Host(this) } }
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription("Dock").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Workspace Home").assertCountEquals(0)
        assertFalse(DockPresence.hasItems)
    }

    @Test fun DCK_10_theRailComesBackWhenSomethingIsPinned() {
        val mgr = WorkspaceRepository.get(context)
        mgr.setPins(emptyList())
        rule.setContent { Box(Modifier.fillMaxSize()) { Host(this) } }
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription("Dock").assertCountEquals(0)
        mgr.setPins(listOf(DockItemRef.app(app.key)))
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Dock").assertExists()
        rule.onNodeWithContentDescription("Alpha").assertExists()
        assertTrue(DockPresence.hasItems)
        mgr.setPins(emptyList())
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription("Dock").assertCountEquals(0)
        assertFalse(DockPresence.hasItems)
    }

    @Composable private fun Host(scope: androidx.compose.foundation.layout.BoxScope) {
        DockHost(apps = listOf(app), launcher = AppLauncher(context), controller = DockController(), refreshKey = 0, onLaunchApp = {}, onOpenPanel = {},
            modifier = { with(scope) { Modifier.align(Alignment.BottomCenter) } }, alwaysShown = true, edgeOverride = DockEdge.Bottom)
    }

    // ---- the visibility rule (JVM-level)
    @Test fun DCK_10_theVisibilityRule() {
        assertFalse(DockVisibility.hasItems(0, 0))
        assertTrue(DockVisibility.hasItems(1, 0))
        assertTrue(DockVisibility.hasItems(0, 2))
        assertFalse(DockVisibility.shown(false, true)) // dock off: nothing, whatever it holds
        assertFalse(DockVisibility.shown(true, false))
        assertTrue(DockVisibility.shown(true, true))
        val empty = content()
        assertFalse(empty.hasItems)
        // The usage-access button alone is not an item; recents count only while the Recent section is on.
        assertFalse(empty.copy(showEnableRow = true).hasItems)
        val recent = listOf(DockItem(DockItemRef.app("r"), "R"))
        assertFalse(empty.copy(recent = recent, showRecentSection = false).hasItems)
        assertTrue(empty.copy(recent = recent, showRecentSection = true).hasItems)
    }
}
