package com.bos.sphere.feature.dock

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.bos.sphere.core.capabilities.Tier
import com.bos.sphere.core.data.DockStyle
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.core.launch.LaunchTarget
import com.bos.sphere.feature.grid.LocalPaneFlash
import com.bos.sphere.feature.grid.OneUiTags
import com.bos.sphere.feature.grid.PaneFlashed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Wave 58 (settingsPolish): the dock's options stay on the page, dimmed with the reason, while the dock is off; where a surface forces the dock
 * the Show the dock switch stays, dimmed, with why (CFG-66). The Profiles page lights the row a Settings search named (CFG-67).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h1600dp-port-xhdpi")
class DockOptionsW58Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private class Hooks : WorkspaceHooks {
        override fun captureHome(): String? = null
        override fun applyHome(arrangement: String?) {}
    }
    private var seq = 0
    private fun manager() = WorkspaceManager(InMemoryWorkspaceStore(), { Hooks() }, newId = { "id${seq++}" })
    private val apps = listOf("Alpha", "Bravo").map { PanelApp("k$it", it, "p.$it", LaunchTarget("p.$it", null, 0)) }

    @Before fun setUp() { L10n.setLanguage("en"); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF); DockInput.dialogHook = {} }

    private fun body(m: WorkspaceManager, scope: DockOptionsScope = DockOptionsScope()) {
        rule.setContent { DockPagesScope { Column(Modifier.verticalScroll(rememberScrollState())) { DockOptionsBody(m, Tier.T2, scope) } } }
        rule.waitForIdle()
    }

    @Test fun CFG_66_withTheDockOffItsOptionsStayDimmedWithTheReason() {
        val m = manager(); m.setDock { it.copy(enabled = false) }
        body(m)
        assertEquals("the style, position and mode cards are dimmed blocks", 3, rule.onAllNodesWithTag(OneUiTags.DISABLED_ROW).fetchSemanticsNodes().size)
        for (tag in listOf(DockOptionTags.MAGNIFY, DockOptionTags.GROUP_SHELF)) {
            val c = rule.onNodeWithTag(tag).fetchSemanticsNode().config
            assertTrue("$tag is dimmed", c.contains(SemanticsProperties.Disabled))
            assertEquals("Dock is off", c.getOrNull(SemanticsProperties.StateDescription))
        }
        for (label in listOf("Dock style", "Position", "Dock mode")) rule.onNodeWithContentDescription("$label, Unavailable, Dock is off").assertExists()
        rule.onNodeWithContentDescription("Magnify icons, Unavailable, Dock is off").assertExists()
        // a tap on a dimmed card changes nothing
        rule.onAllNodesWithTag(OneUiTags.DISABLED_ROW)[0].performClick(); rule.waitForIdle()
        assertEquals(DockStyle.entries.first { it == m.current.dock.style }, m.current.dock.style)
        // Show the dock itself is never dimmed by "Dock is off": it is the way back
        rule.onNodeWithTag(DockPageTags.SHOW_DOCK).performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(true, m.current.dock.enabled)
        assertEquals("with the dock on nothing is dimmed", 0, rule.onAllNodesWithTag(OneUiTags.DISABLED_ROW).fetchSemanticsNodes().size)
    }

    @Test fun CFG_66_whereASurfaceForcesTheDockShowTheDockStaysDimmedWithWhy() {
        val m = manager()
        body(m, DockOptionsScope(canHide = false, forcedReason = "Right side is Dock rail"))
        val n = rule.onNodeWithTag(DockPageTags.SHOW_DOCK).fetchSemanticsNode()
        assertTrue(n.config.contains(SemanticsProperties.Disabled))
        assertEquals("Right side is Dock rail", n.config.getOrNull(SemanticsProperties.StateDescription))
        rule.onNodeWithTag(DockPageTags.SHOW_DOCK).performScrollTo().performClick(); rule.waitForIdle()
        assertNull("the switch does not react", m.current.dock.enabled)
    }

    @Test fun CFG_66_theRulesOfTheDimmedDockOptions() {
        assertEquals("Dock is off", DockOptionRules.offReason(enabled = false, canHide = true))
        assertNull("a forced dock is never off", DockOptionRules.offReason(enabled = false, canHide = false))
        assertNull(DockOptionRules.offReason(enabled = true, canHide = true))
        assertEquals("The desktop always shows it", DockOptionRules.forcedReason(forceEnabled = true, alwaysShown = false))
        assertEquals("Right side is Dock rail", DockOptionRules.forcedReason(forceEnabled = false, alwaysShown = true))
        assertNull(DockOptionRules.forcedReason(forceEnabled = false, alwaysShown = false))
    }

    @Test fun CFG_67_theProfilesPageLightsTheRowASearchNamed() {
        val m = manager()
        val which = androidx.compose.runtime.mutableStateOf(ProfilePaneIds.NEW)
        rule.setContent {
            CompositionLocalProvider(LocalPaneFlash provides which.value) { DockPagesScope { Column(Modifier.verticalScroll(rememberScrollState())) { ProfilesContent(m, apps) } } }
        }
        for (id in listOf(ProfilePaneIds.NEW, ProfilePaneIds.LIST, ProfilePaneIds.CALM)) {
            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { which.value = id }
            rule.waitForIdle()
            val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(PaneFlashed), useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals("one lit row for $id", 1, nodes.size)
            assertEquals(id, nodes[0].config[PaneFlashed])
        }
    }
}
