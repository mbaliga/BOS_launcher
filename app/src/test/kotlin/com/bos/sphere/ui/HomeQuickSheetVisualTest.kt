package com.bos.sphere.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.HomeOrder
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.PageTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOM-21 (wave 51, homeMenuVisual, docs/decisions/homeMenuVisual.md): every choice of the Home quick sheet is a drawn card with the selected state in
 * its semantics, the three entry tiles lead the sheet, and the old text pills are gone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class HomeQuickSheetVisualTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private class Seen { var prefs = LauncherPrefs(); var apps = 0; var widgets = 0; var edit = 0; var home = 0; var hotspots = 0 }

    private fun sheet(start: LauncherPrefs, seen: Seen, page: Int? = null) = rule.setContent {
        var p by remember { mutableStateOf(start) }
        HomeQuickSheetBody(p, { f -> p = f(p); seen.prefs = p }, page, 2, {}, {}, {}, { seen.apps++ }, { seen.widgets++ }, { seen.edit++ }, onHotspots = { seen.hotspots++ })
    }

    private fun tag(t: String) = rule.onNodeWithTag(t).performScrollTo()

    @Test fun HOM_21_w51_theThreeEntryTilesCallTheEditEntryPoints() {
        val seen = Seen(); sheet(LauncherPrefs(), seen)
        tag("quick.add.apps").performClick(); tag("quick.add.widgets").performClick(); tag("quick.edit").performClick()
        assertEquals(listOf(1, 1, 1), listOf(seen.apps, seen.widgets, seen.edit))
    }

    @Test fun HOM_21_w51_everyPagesCardSelectsItsValueAndExposesTheSelectedState() {
        val seen = Seen(); sheet(LauncherPrefs(homePages = HomePages.Off), seen)
        tag("quick.pages.Off").assertIsSelected()
        for (m in listOf(HomePages.Paginated, HomePages.Continuous, HomePages.Off)) {
            tag("quick.pages.${m.name}").performClick()
            assertEquals(m, seen.prefs.homePages)
            tag("quick.pages.${m.name}").assertIsSelected()
            for (o in HomePages.entries.filter { it != m }) tag("quick.pages.${o.name}").assertIsNotSelected()
        }
    }

    @Test fun HOM_21_w51_everyOrderCardSelectsItsValue() {
        val seen = Seen(); sheet(LauncherPrefs(), seen)
        tag("quick.order.LeftToRight").assertIsSelected()
        tag("quick.order.RightToLeft").performClick()
        assertEquals(HomeOrder.RightToLeft, seen.prefs.homeOrder)
        tag("quick.order.RightToLeft").assertIsSelected(); tag("quick.order.LeftToRight").assertIsNotSelected()
    }

    // Wave 59 (glanceTop): the Hotspots cards (Four, Two (bottom), One, None) were replaced by one tile that opens the Hotspots editor.
    @Test fun CFG_77_theHotspotsTileOpensTheEditorAndTheOldCardsAreGone() {
        val seen = Seen(); sheet(LauncherPrefs(), seen)
        tag("quick.hotspots").assertHasClickAction().performClick()
        assertEquals(1, seen.hotspots)
        assertEquals("the stored set is untouched", LauncherPrefs().glanceHotspots, seen.prefs.glanceHotspots)
        for (n in listOf("Four", "Two", "One", "None")) assertTrue(rule.onAllNodes(hasTestTag("quick.hotspots.$n")).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText("Two (bottom)").fetchSemanticsNodes().isEmpty())
    }

    @Test fun HOM_21_w51_thePageCardsAlignMakeHomeAndEveryTransition() {
        val seen = Seen(); sheet(LauncherPrefs(homePages = HomePages.Paginated, gridBias = 0.5f, homePage = 1), seen, page = 0)
        tag("quick.align.bottom").performClick(); assertEquals(1f, seen.prefs.gridBias); tag("quick.align.bottom").assertIsSelected(); tag("quick.align.top").assertIsNotSelected()
        tag("quick.align.top").performClick(); assertEquals(0f, seen.prefs.gridBias); tag("quick.align.top").assertIsSelected()
        tag("quick.makehome").assertIsNotSelected().performClick(); assertEquals(0, seen.prefs.homePage); tag("quick.makehome").assertIsSelected()
        for (t in PageTransition.entries) {
            tag("quick.transition.${t.name}").performClick()
            assertEquals(t, seen.prefs.pageTransition)
            tag("quick.transition.${t.name}").assertIsSelected()
        }
    }

    @Test fun HOM_21_w51_theLabelIsTheCardsAccessibleTextAndTheRoleIsRadio() {
        val seen = Seen(); sheet(LauncherPrefs(), seen)
        val n = rule.onNodeWithTag("quick.order.RightToLeft").fetchSemanticsNode().config
        assertEquals(HomeOrder.RightToLeft.label, n[SemanticsProperties.Text].joinToString { it.text })
        assertEquals(androidx.compose.ui.semantics.Role.RadioButton, n[SemanticsProperties.Role])
        assertTrue(n.contains(SemanticsProperties.Selected))
    }

    @Test fun HOM_21_w51_cardsAndTilesAreAtLeast48dp() {
        val seen = Seen(); sheet(LauncherPrefs(), seen)
        val density = rule.density
        for (t in listOf("quick.pages.Off", "quick.order.LeftToRight", "quick.hotspots", "quick.add.apps", "quick.edit")) {
            val h = rule.onNodeWithTag(t).fetchSemanticsNode().size
            assertTrue("$t is at least 48 dp", h.height >= with(density) { 48.dp.roundToPx() } && h.width >= with(density) { 48.dp.roundToPx() })
        }
        assertTrue(rule.onAllNodes(hasTestTag("quick.chip")).fetchSemanticsNodes().isEmpty())
    }
}
