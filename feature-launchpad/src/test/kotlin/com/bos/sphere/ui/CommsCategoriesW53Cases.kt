package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.bos.sphere.core.data.CommsCategory
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadPrefs
import com.bos.sphere.core.data.Rule
import com.bos.sphere.core.data.RuleAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Wave 53 (commsCategories, STR-16 and STR-17): the Messages / Notifications / Alerts views in the Comms menu and the pinned block at the top of the
 * list, on the real Comms screen. Screenshots: build/screenshots/comms-categories-*.png.
 */
abstract class CommsCategoriesW53Cases(private val orientation: String) : LaunchpadShots() {
    private fun n(
        key: String, pkg: String, app: String, title: String, text: String, ts: Long, category: String? = null, messaging: Boolean = false,
        ongoing: Boolean = false, importance: Int = 0, progress: Boolean = false, style: String? = null,
    ) = HubNotification(
        id = key, key = key, source = "x", title = title, summary = text, timestamp = ts, packageName = pkg, appLabel = app,
        conversation = if (messaging) title else null, sender = if (messaging) title else null, isMessaging = messaging,
        category = category, isOngoing = ongoing, importance = importance, progress = progress, style = style,
    )

    private val chat = n("chat1", "demo.chat", "Chat", "Mum", "Are you coming on Sunday?", at(11, 40), "msg", messaging = true)
    private val chat2 = n("chat2", "demo.chat", "Chat", "Team", "Build is green", at(10, 5), "msg", messaging = true)
    private val mail = n("mail1", "demo.mail", "Mail", "Priya Nair", "Re: Budget for Q3", at(9, 30), "email")
    private val otp = n("otp1", "demo.sms", "Messages", "AX-HDFCBK", "482913 is your OTP for login. Do not share it.", at(11, 55), "msg", messaging = true)
    private val alarm = n("alarm1", "demo.clock", "Clock", "Alarm", "07:00", at(7, 0), "alarm")
    private val promo = n("promo1", "demo.shop", "Shop", "Big Sale", "Up to 70% off on electronics", at(9, 0), "promo")
    private val news = n("news1", "demo.news", "News", "Breaking", "Markets close higher", at(8, 0))
    private val media = n("media1", "demo.music", "Music", "Blue in Green", "Miles Davis", at(11, 59), "transport", ongoing = true)
    private val vpn = n("vpn1", "demo.vpn", "VPN", "VPN is on", "Tap to disconnect", at(11, 0), ongoing = true)
    private val timer = n("timer1", "demo.clock", "Clock", "Timer", "04:12 left", at(11, 30), "alarm", ongoing = true)
    private val download = n("dl1", "demo.files", "Downloads", "Downloading report.pdf", "63%", at(11, 20), "progress", ongoing = true, progress = true)

    private val base = listOf(chat, chat2, mail, otp, alarm, promo, news)

    private var live by mutableStateOf(base + listOf(media, vpn))
    private var rules by mutableStateOf(emptyList<Rule>())
    private var prefs by mutableStateOf(LaunchpadPrefs(GroupMode.Flat))
    private var read by mutableStateOf(emptySet<String>())
    private val opened = mutableListOf<String>()
    private val added = mutableListOf<Rule>()

    @Before fun reset() {
        live = base + listOf(media, vpn); rules = emptyList(); prefs = LaunchpadPrefs(GroupMode.Flat); read = emptySet(); opened.clear(); added.clear()
    }

    @Composable
    private fun Stage(dark: Boolean = true) {
        CompositionLocalProvider(LocalLp provides if (dark) LpDark else LpLight) {
            Box(Modifier.fillMaxSize().background(LocalLp.current.bg)) {
                val menu = rememberLaunchpadMenu()
                LaunchpadContent(
                    items = live, rules = rules, prefs = prefs, readKeys = read, hasAccess = true, now = now,
                    onClose = {}, onOpenItem = {}, onOpenApp = {}, onDismissItem = {}, onClearAll = {},
                    onAddRule = { added += it; rules = rules + it }, onSetDefaultMode = {}, onRequestAccess = {}, zone = zone, menu = menu, systemState = system,
                    openItem = { opened += it.key; true },
                )
            }
        }
    }

    private fun render(dark: Boolean = true) { rule.setContent { Stage(dark) }; rule.waitForIdle() }
    private fun edit(block: () -> Unit) { rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { block() } }; rule.waitForIdle() }
    private fun present(key: String) = rule.onAllNodesWithTag("lp.row.$key").fetchSemanticsNodes().isNotEmpty()
    private fun openMenu() { rule.onNodeWithTag("lp.menu").performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000); rule.waitForIdle() }
    private fun pick(tag: String) { rule.onNodeWithTag(tag, useUnmergedTree = true).performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000); rule.waitForIdle() }
    private fun text(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text }
    private fun top(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
    private fun keysOnScreen(vararg keys: String) = keys.filter { present(it) }

    // ---- the menu ----

    @Test fun STR_17_theMenuListsAllThenMessagesNotificationsAlertsBelowIt() {
        render(); openMenu()
        val tops = listOf("lp.menu.show.all", "lp.menu.cat.messages", "lp.menu.cat.notifications", "lp.menu.cat.alerts", "lp.menu.show.demo.chat").map { top(it) }
        assertEquals("order below All, then the apps", tops.sorted(), tops)
        assertEquals("Messages", text("lp.menu.cat.messages.label")); assertEquals("Notifications", text("lp.menu.cat.notifications.label")); assertEquals("Alerts", text("lp.menu.cat.alerts.label"))
        shoot("comms-categories-$orientation-menu")
    }

    @Test fun STR_17_eachCategoryHasItsOneLineDefinitionInTheMenu() {
        render(); openMenu()
        assertEquals("Chats, SMS, email and calls to you", text("lp.menu.cat.messages.caption"))
        assertEquals("Promos, news and app updates", text("lp.menu.cat.notifications.caption"))
        assertEquals("Codes, alarms, reminders, warnings", text("lp.menu.cat.alerts.caption"))
    }

    @Test fun STR_17_theCountsAreUnreadAndLeavePinnedRowsOut() {
        render(); openMenu()
        // Messages: chat1, chat2, mail1 (the OTP SMS is an Alert). Alerts: otp1, alarm1. Notifications: promo1, news1. media and vpn are pinned, never counted.
        assertEquals("3", text("lp.menu.cat.messages.count")); assertEquals("2", text("lp.menu.cat.notifications.count")); assertEquals("2", text("lp.menu.cat.alerts.count"))
        edit { read = setOf("chat1", "otp1") }
        assertEquals("2", text("lp.menu.cat.messages.count")); assertEquals("1", text("lp.menu.cat.alerts.count"))
    }

    @Test fun STR_17_theCategoryRowsAreRadiosWithTalkBackLabels() {
        render(); openMenu()
        val n = rule.onNodeWithTag("lp.menu.cat.alerts").fetchSemanticsNode().config
        assertEquals(androidx.compose.ui.semantics.Role.RadioButton, n.getOrNull(SemanticsProperties.Role))
        assertEquals("Alerts, Codes, alarms, reminders, warnings, 2 unread", n.getOrNull(SemanticsProperties.ContentDescription)!!.joinToString())
        assertFalse(n.getOrNull(SemanticsProperties.Selected)!!)
        pick("lp.menu.cat.alerts"); openMenu()
        assertTrue(rule.onNodeWithTag("lp.menu.cat.alerts").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)!!)
        assertFalse(rule.onNodeWithTag("lp.menu.show.all").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)!!)
    }

    // ---- the views ----

    @Test fun STR_17_aCategoryViewShowsOnlyItsRows() {
        render()
        assertTrue(keysOnScreen("chat1", "otp1", "promo1").size == 3)
        openMenu(); pick("lp.menu.cat.alerts")
        assertEquals(listOf("otp1", "alarm1"), keysOnScreen("chat1", "otp1", "alarm1", "promo1", "news1"))
        openMenu(); pick("lp.menu.cat.messages")
        assertEquals(listOf("chat1", "chat2", "mail1"), keysOnScreen("chat1", "chat2", "mail1", "otp1", "alarm1", "promo1", "news1"))
        openMenu(); pick("lp.menu.cat.notifications")
        assertEquals(listOf("promo1", "news1"), keysOnScreen("chat1", "mail1", "otp1", "alarm1", "promo1", "news1"))
        openMenu(); pick("lp.menu.show.all")
        assertEquals(7, keysOnScreen("chat1", "chat2", "mail1", "otp1", "alarm1", "promo1", "news1").size)
        shoot("comms-categories-$orientation-all")
    }

    @Test fun STR_17_theHeaderNamesTheCategory() {
        render(); openMenu(); pick("lp.menu.cat.alerts")
        assertEquals("Alerts", text("lp.title"))
        shoot("comms-categories-$orientation-alerts")
    }

    @Test fun STR_17_aCategoryComposesWithTheReadFilter() {
        render()
        edit { read = setOf("otp1") }
        openMenu(); pick("lp.menu.cat.alerts")
        assertEquals(listOf("otp1", "alarm1"), keysOnScreen("otp1", "alarm1"))
        rule.onNodeWithTag("lp.root").performTouchInput { pinch(center + androidx.compose.ui.geometry.Offset(-200f, 0f), center + androidx.compose.ui.geometry.Offset(-40f, 0f), center + androidx.compose.ui.geometry.Offset(200f, 0f), center + androidx.compose.ui.geometry.Offset(40f, 0f)) }
        rule.waitForIdle()
        assertEquals("Unread inside Alerts: only the alarm", listOf("alarm1"), keysOnScreen("otp1", "alarm1", "chat1"))
    }

    @Test fun STR_17_theDefaultCategoryOpensCommsOnThatCategory() {
        prefs = LaunchpadPrefs(GroupMode.Flat, defaultCategory = CommsCategory.Notifications)
        render()
        assertEquals(listOf("promo1", "news1"), keysOnScreen("chat1", "otp1", "promo1", "news1"))
        openMenu()
        assertTrue(rule.onNodeWithTag("lp.menu.cat.notifications").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)!!)
    }

    @Test fun STR_17_aTreatAsRuleMovesARowToTheOwnersCategory() {
        edit { rules = listOf(Rule.create(RuleAction.AsAlerts, packageName = "demo.shop")) }
        render(); openMenu()
        assertEquals("3", text("lp.menu.cat.alerts.count")); assertEquals("1", text("lp.menu.cat.notifications.count"))
        pick("lp.menu.cat.alerts")
        assertTrue(present("promo1"))
    }

    @Test fun STR_17_theLongPressChipsOfferTheOtherTwoCategoriesAndAddTheRule() {
        render()
        rule.onNodeWithTag("lp.focus.promo1").performTouchInput { longClick() }; rule.waitForIdle()
        rule.onNodeWithTag("lp.chips.cat.promo1").assertExists()
        rule.onNodeWithTag("lp.chip.make.alerts.promo1").assertExists(); rule.onNodeWithTag("lp.chip.make.messages.promo1").assertExists()
        assertTrue("not its own category", rule.onAllNodesWithTag("lp.chip.make.notifications.promo1").fetchSemanticsNodes().isEmpty())
        assertEquals("Make this an Alert", text("lp.chip.make.alerts.promo1"))
        rule.onNodeWithTag("lp.chip.make.alerts.promo1").performClick(); rule.waitForIdle()
        assertEquals(1, added.size)
        assertEquals(RuleAction.AsAlerts, added[0].action); assertEquals("demo.shop", added[0].packageName)
        openMenu()
        assertEquals("3", text("lp.menu.cat.alerts.count"))
    }

    // ---- the pinned block ----

    @Test fun STR_16_ongoingNotificationsLeadInAPinnedBlockWithAPinGlyph() {
        render()
        rule.onNodeWithTag("lp.group.pinned").assertIsDisplayed()
        rule.onNodeWithTag("lp.pinned.glyph", useUnmergedTree = true).assertExists()
        assertTrue(top("lp.row.media1") < top("lp.row.otp1") && top("lp.row.vpn1") < top("lp.row.chat1"))
        assertTrue("newest pinned first", top("lp.row.media1") < top("lp.row.vpn1"))
        rule.onNodeWithTag("lp.pin.media1", useUnmergedTree = true).assertExists()
        shoot("comms-categories-$orientation-pinned")
    }

    @Test fun STR_16_pinnedRowsAreNeverUnreadAndSwipingDoesNothingButSaysWhy() {
        render()
        assertTrue("no unread dot", rule.onAllNodesWithTag("lp.row.unreadDot.media1", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("lp.row.unreadDot.chat1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("lp.focus.media1").performTouchInput { swipeLeft() }; rule.waitForIdle()
        assertTrue("the row is still there and unmarked", present("media1"))
        rule.onNodeWithText("Pinned notifications stay until the app ends them").assertIsDisplayed()
        assertEquals("a swipe marks nothing read", emptySet<String>(), com.bos.sphere.core.data.LaunchpadSettings.readKeys.value.filter { it == "media1" }.toSet())
    }

    @Test fun STR_16_aTapOpensThePinnedNotificationLikeAnyOther() {
        render()
        rule.onNodeWithTag("lp.focus.media1").performClick(); rule.waitForIdle()
        assertEquals(listOf("media1"), opened)
    }

    @Test fun STR_16_pinnedRowsUpdateLive() {
        render()
        assertEquals("Blue in Green", text("lp.title.media1"))
        edit { live = live.map { if (it.key == "media1") it.copy(title = "So What", timestamp = at(11, 59) + 1000) else it } }
        assertEquals("So What", text("lp.title.media1"))
        edit { live = live.filter { it.key != "vpn1" } }
        assertFalse(present("vpn1"))
    }

    @Test fun STR_16_moreThanThreePinnedFoldIntoACompactRowWithShowAll() {
        edit { live = base + listOf(media, vpn, timer, download) }
        render()
        rule.onNodeWithTag("lp.pinned.compact").assertIsDisplayed()
        assertTrue("rows folded", keysOnScreen("media1", "vpn1", "timer1", "dl1").isEmpty())
        assertEquals("4 pinned", rule.onNodeWithTag("lp.pinned.compact").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)!!.first().substringBefore(","))
        assertEquals("Show all", text("lp.pinned.toggle"))
        shoot("comms-categories-$orientation-pinned-compact")
        rule.onNodeWithTag("lp.pinned.toggle", useUnmergedTree = true).performClick(); rule.waitForIdle()
        assertEquals(4, keysOnScreen("media1", "vpn1", "timer1", "dl1").size)
        assertEquals("Show fewer", text("lp.pinned.toggle"))
    }

    @Test fun STR_16_threePinnedStayAsFullRows() {
        edit { live = base + listOf(media, vpn, timer) }
        render()
        assertEquals(3, keysOnScreen("media1", "vpn1", "timer1").size)
        assertTrue(rule.onAllNodesWithTag("lp.pinned.compact").fetchSemanticsNodes().isEmpty())
    }

    @Test fun STR_16_showPinnedAtTheTopOffListsThemWithTheOthersStillLocked() {
        prefs = LaunchpadPrefs(GroupMode.Flat, pinnedAtTop = false)
        render()
        assertTrue(rule.onAllNodesWithTag("lp.group.pinned").fetchSemanticsNodes().isEmpty())
        assertTrue(present("media1") && present("vpn1"))
        rule.onNodeWithTag("lp.focus.media1").performTouchInput { swipeLeft() }; rule.waitForIdle()
        assertTrue(present("media1"))
    }

    @Test fun STR_16_aPinBySenderRuleJoinsTheSameBlockWithARuleBadge() {
        edit { rules = listOf(Rule.create(RuleAction.Pin, sender = "Mum")) }
        render()
        rule.onNodeWithTag("lp.group.pinned").assertIsDisplayed()
        assertEquals("one Pinned heading", 1, rule.onAllNodesWithTag("lp.group.pinned").fetchSemanticsNodes().size)
        assertTrue(top("lp.row.chat1") < top("lp.row.otp1"))
        assertEquals("Rule", text("lp.pin.chat1.rule")) // an unread rule-pinned row keeps its dot; the badge is the word
        rule.onNodeWithTag("lp.pin.media1", useUnmergedTree = true).assertExists()
        shoot("comms-categories-$orientation-pinned-rule")
    }

    @Test fun STR_16_pinnedRowsIgnoreTheReadFilterAndObeyTheCategory() {
        render()
        rule.onNodeWithTag("lp.root").performTouchInput { pinch(center + androidx.compose.ui.geometry.Offset(-200f, 0f), center + androidx.compose.ui.geometry.Offset(-40f, 0f), center + androidx.compose.ui.geometry.Offset(200f, 0f), center + androidx.compose.ui.geometry.Offset(40f, 0f)) }
        rule.waitForIdle()
        assertTrue("still listed under Unread only", present("media1") && present("vpn1"))
        openMenu(); pick("lp.menu.cat.alerts")
        assertFalse("the music row is a Notification, not an Alert", present("media1"))
    }

    @Test fun STR_16_aPinnedCallIsAMessageAndATimerIsAnAlert() {
        val call = n("call1", "demo.dialer", "Phone", "Mum", "Ongoing call", at(11, 58), "call", ongoing = true, style = "call")
        edit { live = base + listOf(media, timer, call) }
        render(); openMenu(); pick("lp.menu.cat.messages")
        assertTrue(present("call1")); assertFalse(present("timer1")); assertFalse(present("media1"))
        openMenu(); pick("lp.menu.cat.alerts")
        assertTrue(present("timer1")); assertFalse(present("call1"))
    }

    @Test fun STR_16_rightToLeftMirrorsThePinMarginAndTheCategoryRows() {
        rule.setContent { CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) { Stage() } }
        rule.waitForIdle()
        val width = rule.onNodeWithTag("lp.root").fetchSemanticsNode().boundsInRoot.width
        assertTrue("the pin sits at the start edge, which is the right", rule.onNodeWithTag("lp.pin.media1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center.x > width / 2)
        openMenu()
        assertTrue("the panel and its category rows are on the right", rule.onNodeWithTag("lp.menu.cat.alerts").fetchSemanticsNode().boundsInRoot.center.x > width / 2)
        shoot("comms-categories-$orientation-rtl")
    }

    @Test fun STR_16_theLightLookDrawsTheBlockToo() {
        render(dark = false)
        rule.onNodeWithTag("lp.group.pinned").assertIsDisplayed()
        shoot("comms-categories-$orientation-pinned-light")
    }
}
