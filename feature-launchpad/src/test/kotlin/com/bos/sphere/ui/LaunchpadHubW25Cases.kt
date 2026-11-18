package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 25 (launchpadHub; STR-01, STR-02): the menu side panel, the home card sliding out of view, the hub-style rows and the floating compose
 * button. Screenshots go to build/screenshots/launchpad-hub-*.png (portrait and landscape, dark and light, 0 / 1 / 30 notifications).
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
abstract class LaunchpadHubW25Cases(private val orientation: String) : LaunchpadShots() {
    private val apps = listOf("com.whatsapp" to "WhatsApp", "com.slack" to "Slack", "com.google.android.gm" to "Mail", "com.instagram.android" to "Instagram", "demo.cal" to "Calendar", "demo.chat" to "Chat")

    private fun hub(count: Int): List<HubNotification> = (0 until count).map { i ->
        val (pkg, app) = apps[i % apps.size]
        val ts = at(11, 50) - i * 47L * 60_000L // spreads over several days for 30 rows
        HubNotification(
            id = "h$i", key = "h$i", source = "x", title = listOf("Trevor Cameron", "Blake Smith", "Andrew Paterson", "Ian Dundas", "Hisham Atas")[i % 5],
            summary = "Who won the game last night? It was a long preview line that must be cut to one line $i", timestamp = ts,
            packageName = pkg, appLabel = app,
        )
    }

    private var composed = 0
    private var menuRef: LaunchpadMenuState? = null

    @Composable
    private fun Stage(items: List<HubNotification>, dark: Boolean, rtl: Boolean = false, mode: GroupMode = GroupMode.Time, switcher: @Composable () -> Unit = {}, home: Boolean = false) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalLp provides if (dark) LpDark else LpLight,
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val menu = rememberLaunchpadMenu().also { menuRef = it }
                val density = androidx.compose.ui.platform.LocalDensity.current.density
                val geo = LaunchpadCardGeometry(maxWidth.value * density, maxHeight.value * density, CardEdge.forLaunchpad(rtl), density)
                Box(Modifier.fillMaxSize().launchpadBackdrop(LocalLp.current.bg, LocalLp.current.surface)) {
                    LaunchpadContent(
                        items = items, rules = emptyList(), prefs = LaunchpadPrefs(mode), readKeys = setOf("h1", "h2"), hasAccess = true, now = now,
                        onClose = {}, onOpenItem = {}, onOpenApp = {}, onDismissItem = {}, onClearAll = {}, onAddRule = {},
                        onSetDefaultMode = {}, onRequestAccess = {}, zone = zone, menu = menu, onCompose = { composed++ }, viewSwitcher = switcher,
                        endInset = if (home) (geo.returnZonePx / density).dp else 0.dp,
                    )
                }
                if (home) Box(
                    Modifier.fillMaxSize().launchpadMenuAside(menu, geo.widthPx, rtl).homeCardLayer(geo) { 1f }.background(Color(0xFFE57373)).testTag("home.card"),
                ) { Text("Home") }
            }
        }
    }

    private fun render(items: List<HubNotification>, dark: Boolean, name: String? = null, rtl: Boolean = false, home: Boolean = false, switcher: @Composable () -> Unit = {}) {
        rule.setContent { Stage(items, dark, rtl, home = home, switcher = switcher) }
        rule.waitForIdle()
        if (name != null) shoot("launchpad-hub-$orientation-$name-${if (dark) "dark" else "light"}")
    }

    private fun density() = rule.activity.resources.displayMetrics.density
    private fun winW() = rule.activity.resources.displayMetrics.let { it.widthPixels / it.density }
    private fun winH() = rule.onRoot().getUnclippedBoundsInRoot().bottom.value

    // ---- screenshots: 0, 1 and 30 notifications, dark and light --------------------------------------------------------------------
    @Test fun STR_02_hub_screens_zero_dark() = render(emptyList(), true, "0")
    @Test fun STR_02_hub_screens_zero_light() = render(emptyList(), false, "0")
    @Test fun STR_02_hub_screens_one_dark() = render(hub(1), true, "1")
    @Test fun STR_02_hub_screens_one_light() = render(hub(1), false, "1")
    @Test fun STR_02_hub_screens_thirty_dark() = render(hub(30), true, "30")
    @Test fun STR_02_hub_screens_thirty_light() = render(hub(30), false, "30")

    @Test fun STR_01_menuPanel_screens_dark() { render(hub(30), true, home = true); openMenu(); shoot("launchpad-hub-$orientation-menu-dark") }
    @Test fun STR_01_menuPanel_screens_light() { render(hub(30), false, home = true); openMenu(); shoot("launchpad-hub-$orientation-menu-light") }

    private fun openMenu() { rule.onNodeWithTag("lp.menu").performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000); rule.waitForIdle() }

    // ---- the side panel ------------------------------------------------------------------------------------------------------------
    @Test fun STR_01_menu_isASidePanelNotAPopup_opensFromTheLeftAndClosesOnTheScrim() {
        render(hub(5), false)
        assertTrue("closed: no panel", rule.onAllNodesWithTag("lp.menu.panel").fetchSemanticsNodes().isEmpty())
        openMenu()
        assertTrue(menuRef!!.open)
        val panel = rule.onNodeWithTag("lp.menu.panel").getUnclippedBoundsInRoot()
        val w = winW()
        assertEquals("the panel sits on the left edge", 0f, panel.left.value, 1f)
        assertEquals("full height", winH(), panel.bottom.value - panel.top.value, 1f)
        assertTrue("at most 320 dp wide and never the whole window (${panel.right.value})", panel.right.value <= 320.5f && panel.right.value < w)
        // The notification list has shifted right by the panel's width.
        val list = rule.onNodeWithTag("lp.root").getUnclippedBoundsInRoot()
        assertTrue("the list shifted right (left=${list.left.value})", list.left.value >= panel.right.value - 1f)
        // A tap on the scrim closes it.
        rule.onNodeWithTag("lp.menu.scrim").performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000); rule.waitForIdle()
        assertFalse(menuRef!!.open)
        assertTrue("closed again: panel gone", rule.onAllNodesWithTag("lp.menu.panel").fetchSemanticsNodes().isEmpty())
        assertEquals("the list is back", 0f, rule.onNodeWithTag("lp.root").getUnclippedBoundsInRoot().left.value, 1f)
    }

    @Test fun STR_01_menu_closeButtonAndEscapeAlsoClose() {
        render(hub(5), false)
        openMenu()
        rule.onNodeWithTag("lp.menu.close").performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000)
        assertFalse(menuRef!!.open)
        openMenu()
        rule.onNodeWithTag("lp.root").performKeyEscape()
        rule.waitForIdle()
        assertFalse("Esc closes the panel before it would leave Launchpad", menuRef!!.open)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performKeyEscape() =
        performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Escape) }

    @Test fun STR_01_menu_optionsAreRealRowsOf48dpWithTheCurrentChoiceMarked() {
        render(hub(12), false)
        openMenu()
        val sel = rule.onNodeWithTag("lp.menu.mode.Time").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)
        val other = rule.onNodeWithTag("lp.menu.mode.Flat").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)
        assertEquals(true, sel); assertEquals(false, other)
        val tags = GroupMode.entries.map { "lp.menu.mode.${it.name}" } + listOf("lp.menu.show.all", "lp.menu.show.com.whatsapp", "lp.menu.groups", "lp.menu.clear")
        for (t in tags) {
            if (rule.onAllNodesWithTag(t).fetchSemanticsNodes().isEmpty()) rule.onNodeWithTag("lp.menu.list").performScrollToNodeTag(t)
            val b = rule.onNodeWithTag(t).getUnclippedBoundsInRoot()
            assertTrue("$t is ${b.bottom.value - b.top.value} dp tall, under 48", b.bottom.value - b.top.value >= 47.9f)
        }
        val count = rule.onNodeWithTag("lp.menu.show.com.whatsapp").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
        assertTrue("the app row reads its label and its count: $count", count!!.joinToString { it.text }.contains("WhatsApp") && count.joinToString { it.text }.contains("2"))
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performScrollToNodeTag(tag: String) =
        performScrollToNode(androidx.compose.ui.test.hasTestTag(tag))

    @Test fun STR_01_menu_choosingAGroupingClosesThePanelAndReportsIt() {
        val chosen = mutableListOf<GroupMode>()
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalLp provides LpLight) {
                val menu = rememberLaunchpadMenu().also { menuRef = it }
                LaunchpadContent(
                    items = hub(3), rules = emptyList(), prefs = LaunchpadPrefs(GroupMode.Time), readKeys = emptySet(), hasAccess = true, now = now,
                    onClose = {}, onOpenItem = {}, onOpenApp = {}, onDismissItem = {}, onClearAll = {}, onAddRule = {},
                    onSetDefaultMode = { chosen += it }, onRequestAccess = {}, zone = zone, menu = menu,
                )
            }
        }
        openMenu()
        rule.onNodeWithTag("lp.menu.mode.App").performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000)
        assertEquals(listOf(GroupMode.App), chosen)
        assertFalse(menuRef!!.open)
    }

    // ---- the home card leaves with the panel ---------------------------------------------------------------------------------------
    @Test fun STR_01_homeCard_isFullyOffScreenWhileThePanelIsOpen_andBackWhenItCloses() {
        render(hub(5), false, home = true)
        val w = winW()
        val before = rule.onNodeWithTag("home.card").fetchSemanticsNode().boundsInRoot
        assertTrue("at rest the card is a sliver at the right (left=${before.left})", before.left < w * density() && before.right > w * density() * 0.9f)
        openMenu()
        val open = rule.onNodeWithTag("home.card").fetchSemanticsNode().boundsInRoot
        assertTrue("the card is fully out of view to the right (left=${open.left}, window=${w * density()})", open.left >= w * density() - 1f)
        rule.onNodeWithTag("lp.menu.scrim").performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000); rule.waitForIdle()
        val back = rule.onNodeWithTag("home.card").fetchSemanticsNode().boundsInRoot
        assertTrue("and the card is back (left=${back.left})", back.left < w * density())
    }

    @Test fun STR_01_homeCard_isFullyOffScreenInRightToLeftToo() {
        render(hub(5), false, home = true, rtl = true)
        openMenu()
        val open = rule.onNodeWithTag("home.card").fetchSemanticsNode().boundsInRoot
        assertTrue("the card left the screen to the left (right=${open.right})", open.right <= 1f)
        val panel = rule.onNodeWithTag("lp.menu.panel").getUnclippedBoundsInRoot()
        assertEquals("the panel comes from the right edge", winW(), panel.right.value, 1f)
    }

    // ---- the floating compose button -----------------------------------------------------------------------------------------------
    @Test fun STR_02_compose_isACircular56dpBlueButtonBottomRightLabelledCompose() {
        render(hub(30), false)
        val b = rule.onNodeWithTag("lp.compose").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals(56f, b.right.value - b.left.value, 0.5f); assertEquals(56f, b.bottom.value - b.top.value, 0.5f)
        assertEquals("16 dp from the right edge", winW() - 16f, b.right.value, 1f)
        assertTrue("bottom right, in the lower half (top=${b.top.value})", b.top.value > winH() / 2)
        assertTrue("clear of the bottom edge by at least 16 dp (bottom=${b.bottom.value}, window=${winH()})", b.bottom.value <= winH() - 15.9f)
        assertEquals(listOf("Compose"), rule.onNodeWithTag("lp.compose").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(0xFF0F8FD8.toInt(), HubStyle.FabBlue.value.shr(32).toInt())
        rule.onNodeWithTag("lp.compose").performClick()
        assertEquals(1, composed)
    }

    @Test fun STR_02_compose_thereIsNoDockedComposeStrip() {
        render(hub(3), false)
        assertTrue(rule.onAllNodesWithTag("lp.composebar").fetchSemanticsNodes().isEmpty())
        assertEquals("only the floating button composes", 1, rule.onAllNodesWithTag("lp.compose").fetchSemanticsNodes().size)
    }

    // ---- rows ----------------------------------------------------------------------------------------------------------------------
    @Test fun STR_02_row_hubGeometry_stripeBadgeTitleTimePreview() {
        render(hub(8), false)
        val row = rule.onNodeWithTag("lp.row.h0", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val h = row.bottom.value - row.top.value
        assertTrue("row height $h dp is not 72..90", h in 72f..90f)
        val stripe = rule.onNodeWithTag("lp.stripe.h0", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("stripe is 4 dp wide", 4f, stripe.right.value - stripe.left.value, 0.5f)
        assertEquals("on the leading edge", 0f, stripe.left.value, 0.5f)
        assertEquals("as tall as the row less its divider", h, stripe.bottom.value - stripe.top.value, 1.5f)
        val badge = rule.onNodeWithTag("lp.badge.h0", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(32f, badge.right.value - badge.left.value, 0.5f)
        assertEquals("badge starts 16 dp from the edge", 16f, badge.left.value, 0.5f)
        val time = rule.onNodeWithTag("lp.time.h0", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("time is right-aligned with the 16 dp padding", winW() - 16f, time.right.value, 1f)
        val preview = rule.onNodeWithTag("lp.preview.h0", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("one line of preview (at most 22 dp)", true, preview.bottom.value - preview.top.value <= 22f)
        assertEquals("the preview aligns with the title", HubStyle.TEXT_START_DP.toFloat(), preview.left.value, 1f)
        // Rows are separated by a thin divider only, never a gap: the next row starts where this one ends.
        val next = rule.onNodeWithTag("lp.row.h1", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(row.bottom.value, next.top.value, 1f)
    }

    @Test fun STR_02_row_noChipsUntilTheRowIsExpanded() {
        val pi = android.app.PendingIntent.getBroadcast(rule.activity, 0, android.content.Intent("x"), android.app.PendingIntent.FLAG_IMMUTABLE)
        val withAction = hub(1).map { it.copy(actions = listOf(com.bos.sphere.core.data.HubAction("Archive it", intent = pi))) }
        render(withAction, false)
        assertTrue(rule.onAllNodesWithTag("lp.chips.h0", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test fun STR_02_dayHeaderReadsLikeTheHub() {
        render(hub(2), false)
        // 10 Mar 2024 was a Sunday.
        rule.onNodeWithTag("lp.group.day:2024-03-10").assertIsDisplayed()
        assertEquals("Sun 10 Mar 2024", HubStyle.dayHeader("day:2024-03-10", "Today", java.util.Locale.ENGLISH))
        assertEquals("an app section keeps its title", "Chat", HubStyle.dayHeader("app:demo.chat", "Chat"))
    }

    @Test fun STR_02_stripeColour_isStablePerAppAndNotOneColourForAll() {
        assertEquals(HubStyle.stripeFor("com.example.a"), HubStyle.stripeFor("com.example.a"))
        val distinct = (0 until 20).map { HubStyle.stripeFor("com.example.app$it") }.toSet()
        assertTrue("20 apps share only ${distinct.size} colours", distinct.size >= 6)
        assertNotEquals(HubStyle.stripeFor("com.whatsapp"), HubStyle.stripeFor("com.slack"))
        assertEquals(Color(0xFF25D366), HubStyle.stripeFor("com.whatsapp"))
    }

    // ---- the view switcher slot ----------------------------------------------------------------------------------------------------
    // Owner: the list looks like the BlackBerry Hub, whose views are ONE flat tab row directly under the title bar, across the full width.
    @Test fun STR_02_topBarHasASlotForTheViewSwitcher() {
        render(hub(3), false, switcher = { Text("VIEWS", Modifier.fillMaxWidth().testTag("slot.views")) })
        val slot = rule.onNodeWithTag("slot.views").assertIsDisplayed().getUnclippedBoundsInRoot()
        val menu = rule.onNodeWithTag("lp.menu").getUnclippedBoundsInRoot()
        assertTrue("the switcher row sits directly below the title bar (menu bottom=${menu.bottom.value}, slot top=${slot.top.value})", slot.top.value >= menu.bottom.value && slot.top.value - menu.bottom.value <= 12f)
        assertEquals("the switcher row starts at the left edge", 0f, slot.left.value, 1f)
        assertEquals("the switcher row spans the width", winW(), slot.right.value - slot.left.value, 1f)
    }
}
