package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AppPolicy
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadPrefs
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STR-01, STR-07, STR-08 for the card layout: Launchpad as the background of the shrunken home card.
 * The stage is the same composition the app builds (neighbour card, notifications column, card layer, card overlay) with a
 * stand-in home; screenshots go to build/screenshots/launchpad-card-*.png. Subclasses fix the orientation.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
abstract class LaunchpadCardCases(private val orientation: String) : LaunchpadShots() {
    protected var returned = 0

    @Composable
    private fun Stage(
        fraction: Float, dark: Boolean, rtl: Boolean, items: List<HubNotification>, hasAccess: Boolean = true,
        policies: List<AppPolicy> = emptyList(), mode: GroupMode = GroupMode.AppConversation,
    ) {
        CompositionLocalProvider(
            LocalLp provides if (dark) LpDark else LpLight,
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = androidx.compose.ui.platform.LocalDensity.current.density
                val maxW = maxWidth
                val geo = LaunchpadCardGeometry(maxWidth.value * density, maxHeight.value * density, CardEdge.forLaunchpad(rtl), density)
                Box(Modifier.fillMaxSize().launchpadNeighbourCard(rtl, false, LocalLp.current.bg, LocalLp.current.surface) { fraction }) {
                    LaunchpadContent(
                        items = items, rules = emptyList(), prefs = LaunchpadPrefs(mode), readKeys = setOf("c1"), hasAccess = hasAccess, now = now,
                        onClose = { returned++ }, onOpenItem = {}, onOpenApp = {}, onDismissItem = {}, onClearAll = {}, onAddRule = {},
                        onSetDefaultMode = {}, onRequestAccess = {}, policies = policies, systemState = system, zone = zone,
                        endInset = (geo.cardCoverFraction * maxW.value).dp,
                    )
                }
                // A stand-in for the live home: it is composed at full size and only the layer shrinks it.
                Box(
                    Modifier.fillMaxSize().homeCardLayer(geo) { fraction }
                        .background(Brush.linearGradient(listOf(Color(0xFFFF6B6B), Color(0xFFF2B8A8)), Offset.Zero, Offset(900f, 1800f))),
                    Alignment.Center,
                ) { Text("Home", color = Color.White) }
                if (fraction >= 1f) StageOverlay(geo) { returned++ }
            }
        }
    }

    @Composable
    private fun StageOverlay(geo: LaunchpadCardGeometry, onReturn: () -> Unit) {
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val reveal = remember { LaunchpadRevealState(scope) }
        Box(Modifier.fillMaxSize()) { LaunchpadCardOverlay(geo, reveal, onReturn, slideDp = 200f) }
    }

    private fun render(name: String, dark: Boolean, rtl: Boolean = false, fraction: Float = 1f, items: List<HubNotification> = grouped,
                       hasAccess: Boolean = true, policies: List<AppPolicy> = emptyList()): Double {
        rule.setContent { Stage(fraction, dark, rtl, items, hasAccess, policies) }
        return shoot("launchpad-card-$orientation-$name-${if (dark) "dark" else "light"}")
    }

    private fun shown(text: String, substring: Boolean = false) {
        assertTrue("'$text' is not on screen", rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithText(text, substring = substring)[0].assertIsDisplayed()
    }

    @Test fun STR_07_card_grouped_light() { render("grouped", dark = false); shown("Mum"); shown("Chat", substring = true) }
    @Test fun STR_07_card_grouped_dark() { render("grouped", dark = true); shown("Mum") }
    @Test fun STR_07_card_digest_light() { render("digest", dark = false, items = digestItems, policies = digestPolicies); shown("Digest") }
    @Test fun STR_07_card_digest_dark() { render("digest", dark = true, items = digestItems, policies = digestPolicies); shown("Digest") }
    @Test fun STR_07_card_empty_light() { render("empty", dark = false, items = emptyList()); shown("All caught up") }
    @Test fun STR_07_card_empty_dark() { render("empty", dark = true, items = emptyList()); shown("All caught up") }
    @Test fun STR_07_card_noGrant_light() { render("no-grant", dark = false, hasAccess = false); shown("Allow notification access") }
    @Test fun STR_07_card_noGrant_dark() { render("no-grant", dark = true, hasAccess = false); shown("Allow notification access") }
    @Test fun STR_07_card_rtlPutsTheCardOnTheLeft() { render("rtl", dark = false, rtl = true); shown("Mum") }
    @Test fun STR_01_card_midDragPeek() { render("peek", dark = false, fraction = 0.4f); shown("Mum") }

    @Test fun STR_01_card_overlayIsTheReturnControlOnTheFarSide() {
        render("overlay", dark = false)
        val b = rule.onNodeWithTag("lp.card").getUnclippedBoundsInRoot()
        val root = rule.onNodeWithTag("lp.root").getUnclippedBoundsInRoot()
        assertTrue("card overlay sits in the right part of the window", b.left.value > (root.right.value - root.left.value) * 0.4f)
        rule.onNodeWithTag("lp.card").performClick(); rule.waitForIdle()
        assertEquals(1, returned)
    }

    @Test fun STR_01_card_rtlOverlayIsOnTheLeft() {
        render("overlay-rtl", dark = false, rtl = true)
        val b = rule.onNodeWithTag("lp.card").getUnclippedBoundsInRoot()
        val rootW = rule.onNodeWithTag("lp.root").getUnclippedBoundsInRoot().right.value
        assertTrue("overlay at the left edge (left=${b.left.value})", b.left.value <= 1f && b.right.value < rootW * 0.7f)
    }

    @Test fun STR_01_card_titleSelectsASectionAndItsRowsSitBeneathIt() {
        render("select", dark = false, items = digestItems, policies = digestPolicies)
        // No horizontally scrolling panes: every section title is a node of the one vertical list.
        shown("Digest"); shown("Mum")
        assertTrue(rule.onAllNodesWithText("Bring the dessert").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("Mum").performClick(); rule.waitForIdle()
        shown("Bring the dessert")
    }

    @Test fun STR_08_card_arrowsReachTitlesAndEscReturns() {
        render("keys", dark = false)
        rule.onNodeWithTag("lp.root").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionDown) }
        rule.waitForIdle()
        val first = rule.onAllNodesWithText("Chat", substring = true).fetchSemanticsNodes().isNotEmpty()
        assertTrue(first)
        rule.onNodeWithTag("lp.root").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Escape) }
        assertEquals(1, returned)
    }

    @Test fun NFR_13_card_menuAndCardHaveSpokenLabels() {
        render("labels", dark = false)
        rule.onNodeWithTag("lp.menu").assertIsDisplayed()
        val desc = rule.onNodeWithTag("lp.card").fetchSemanticsNode().config
            .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)
        assertEquals(listOf("Home card. Return to home"), desc)
    }
}
