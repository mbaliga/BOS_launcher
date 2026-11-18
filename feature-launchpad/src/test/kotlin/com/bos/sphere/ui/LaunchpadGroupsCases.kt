package com.bos.sphere.ui

import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import com.bos.sphere.core.data.AssignKind
import com.bos.sphere.core.data.AssignTargets
import com.bos.sphere.core.data.Condition
import com.bos.sphere.core.data.ConditionKind
import com.bos.sphere.core.data.GroupAssignment
import com.bos.sphere.core.data.GroupPriority
import com.bos.sphere.core.data.GroupRule
import com.bos.sphere.core.data.GroupsConfig
import com.bos.sphere.core.data.GroupsSettings
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadGroup
import com.bos.sphere.core.data.LaunchpadPrefs
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.NoContacts
import com.bos.sphere.core.data.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * STR-13 UI: the Launchpad column made of group sections, the row actions, the Groups manager and the rule editor.
 * Screenshots go to build/screenshots/launchpad-groups-*.png. Subclasses fix the orientation.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
abstract class LaunchpadGroupsCases(private val orientation: String) : LaunchpadShots() {
    private fun msg(key: String, pkg: String, app: String, sender: String, text: String, ts: Long) = HubNotification(
        id = key, key = key, source = "x", title = sender, summary = text, timestamp = ts, packageName = pkg, appLabel = app,
        conversation = sender, sender = sender, isMessaging = true, style = "messaging",
    )

    protected val feed = listOf(
        msg("f1", "demo.chat", "Chat", "Priya Sharma", "Lunch on Sunday?", at(11, 50)),
        msg("f2", "demo.sms", "SMS", "Priya Sharma (SMS)", "Call me", at(11, 40)),
        msg("w1", "demo.chat", "Chat", "Ravi Kumar", "Deck is ready", at(11, 20)),
        msg("w2", "demo.wa", "Whats", "Ravi Kumar (WhatsApp)", "Review at 3?", at(11, 10)),
        HubNotification(id = "c1", key = "c1", source = "x", title = "Standup in 15 minutes", summary = "Room 4", timestamp = at(11, 0), packageName = "demo.cal", appLabel = "Calendar"),
        HubNotification(id = "n1", key = "n1", source = "x", title = "Sale ends today", summary = "50% off", timestamp = at(9, 0), packageName = "demo.shop", appLabel = "Shop"),
    )

    protected val config = GroupsConfig(
        groups = listOf(
            LaunchpadGroup("fam", "Family", colorArgb = 0xFFE5547A, order = 0, priority = GroupPriority.Priority),
            LaunchpadGroup("work", "Work", order = 1),
            LaunchpadGroup("promo", "Promos", order = 2, priority = GroupPriority.Quiet),
        ),
        people = listOf(Person("p:ravi", "Ravi Kumar", listOf("Ravi Kumar"))),
        assignments = listOf(
            GroupAssignment(AssignKind.Person, "p:ravi", "work"),
            GroupAssignment(AssignKind.App, AssignTargets.app("demo.shop"), "promo"),
        ),
        rules = listOf(GroupRule("r1", listOf(Condition(ConditionKind.LastNameIsMine)), "fam")),
        myLastName = "Sharma",
    )

    @Before fun reset() {
        GroupsSettings.init(rule.activity)
        GroupsSettings.restore(GroupsConfig())
    }

    @Composable
    private fun Stage(dark: Boolean, rtl: Boolean, groups: GroupsConfig, items: List<HubNotification> = feed) {
        CompositionLocalProvider(
            LocalLp provides if (dark) LpDark else LpLight,
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            LaunchpadContent(
                items = items, rules = emptyList(), prefs = LaunchpadPrefs(GroupMode.AppConversation), readKeys = setOf("c1"), hasAccess = true, now = now,
                onClose = {}, onOpenItem = {}, onOpenApp = {}, onDismissItem = {}, onClearAll = {}, onAddRule = {},
                onSetDefaultMode = {}, onRequestAccess = {}, groups = groups, contacts = NoContacts, systemState = system, zone = zone,
            )
        }
    }

    private fun render(name: String, dark: Boolean, rtl: Boolean = false, groups: GroupsConfig = config): Double {
        rule.setContent { Stage(dark, rtl, groups) }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Family", substring = false).fetchSemanticsNodes().isNotEmpty() || groups.groups.isEmpty() }
        return shoot("launchpad-groups-$orientation-$name-${if (dark) "dark" else "light"}")
    }

    /** STR-02: the column is one long list, so a title further down is reached by scrolling to it first. */
    private fun scrollTo(text: String, substring: Boolean = false) {
        runCatching { rule.onNodeWithTag("lp.list").performScrollToNode(androidx.compose.ui.test.hasText(text, substring = substring)); rule.waitForIdle() }
    }

    private fun shown(text: String, substring: Boolean = false) {
        scrollTo(text, substring)
        assertTrue("'$text' is not on screen", rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithText(text, substring = substring)[0].assertIsDisplayed()
    }

    @Test fun STR_13_column_sectionsAreTheGroups_light() {
        render("sections", dark = false)
        // STR-02 (wave 11): every group is open in the one list; the long list is scrolled to reach the lower titles.
        shown("Family"); shown("Work")
        shown("Promos"); shown("Everything else"); shown("Quiet", substring = true)
        shown("Priority", substring = true)
        shown("Priya Sharma")
        scrollTo("Priya Sharma (SMS)", substring = true) // the list is one long scroll; the second app's row may be just below the first (wave 13: 48 dp targets made the strip and "+N more" taller)
        assertTrue("the priority rows of both apps sit under Family", rule.onAllNodesWithText("Priya Sharma (SMS)", substring = true).fetchSemanticsNodes().isNotEmpty())
        // Updated from the old accordion ("work rows are not shown until Work is selected"): nothing is hidden behind a selection any more.
        shown("Deck is ready")
        assertTrue("work rows are in the list too", rule.onAllNodesWithText("Deck is ready").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun STR_13_column_sectionsAreTheGroups_dark() { render("sections", dark = true); shown("Family"); shown("Work") }

    private fun headTags(): List<String> = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("group head") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("lp.group.") == true
    }).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag)!!.removePrefix("lp.group.") }

    @Test fun STR_13_column_orderIsPriorityNormalBuiltInThenQuiet() {
        render("order", dark = false)
        val expected = listOf("group:fam", "group:work", "group:_", "group:promo")
        val top = headTags()
        scrollTo("Quiet", substring = true)
        val bottom = headTags()
        assertTrue("Priority first: $top", top.first() == "group:fam")
        for (seen in listOf(top, bottom)) assertEquals(seen, expected.filter { it in seen })
        assertTrue("Quiet last: $bottom", bottom.last() == "group:promo")
    }

    @Test fun STR_13_column_aWorkSectionShowsOnePersonFromEveryApp() {
        render("work", dark = false)
        // Updated from "selecting the Work title shows its rows": the rows are always there; the strip jumps to the section.
        rule.onNodeWithTag("lp.strip.group:work").performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithTag("lp.group.group:work").assertIsDisplayed()
        shown("Deck is ready"); shown("Review at 3?")
    }

    @Test fun STR_13_column_rtl() { render("rtl", dark = false, rtl = true); shown("Family"); shown("Work") }

    @Test fun STR_13_column_noGroupsLooksLikeBefore() {
        render("none", dark = false, groups = GroupsConfig())
        // Wave 26: the conversation headers of one-item groups are gone; the rows remain.
        shown("Deck is ready")
        assertTrue(rule.onAllNodesWithText("Everything else").fetchSemanticsNodes().isEmpty())
    }

    @Test fun STR_13_keyboard_arrowsReachAGroupTitleAndEnterSelectsIt() {
        render("keys", dark = false)
        // Family head, its two rows, then the Work head (every section is open, so the walk is one straight line).
        val log = ArrayList<String>()
        repeat(4) { rule.onNodeWithTag("lp.root").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionDown) }; rule.waitForIdle()
            log += rule.onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().map { n -> (n.config.getOrNull(SemanticsProperties.TestTag) ?: "?") }.joinToString("+") }
        assertEquals(listOf("lp.group.group:fam", "lp.focus.f1", "lp.focus.f2", "lp.group.group:work"), log)
        // Arrow left on a title folds it (the user's choice); arrow right opens it again. Nothing is folded by default.
        shown("Deck is ready")
        rule.onNodeWithTag("lp.root").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionLeft) }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("Deck is ready").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("lp.root").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionRight) }
        rule.waitForIdle()
        shown("Deck is ready")
    }

    @Test fun STR_13_priorityGroupAccentAndBadgeAreNotColourOnly() {
        render("badge", dark = false)
        shown("Priority", substring = true)
    }

    // ---- row actions --------------------------------------------------------------------

    @Test fun STR_13_rowAction_moveToGroupAssignsThePersonAcrossApps() {
        GroupsSettings.restore(GroupsConfig(groups = listOf(LaunchpadGroup("work", "Work", order = 0))))
        rule.setContent { Themed(false) { Box(Modifier.fillMaxSize()) { GroupAssignContent(lpGColors(), feed[2], GroupsSettings.snapshot(), NoContacts, feed, {}, {}) } } }
        rule.onNodeWithTag("grp.pick.work").performScrollTo().performClick(); rule.waitForIdle()
        val a = GroupsSettings.snapshot().assignments
        assertEquals(1, a.size)
        assertEquals(AssignKind.Person, a[0].kind)
        assertEquals("work", a[0].groupId)
        assertEquals(1, GroupsSettings.snapshot().people.size)
        // The same human on another platform now resolves to the same group.
        val r = com.bos.sphere.core.data.GroupResolver(GroupsSettings.snapshot())
        assertEquals("work", r.resolve(feed[3]).group?.id)
    }

    @Test fun STR_13_rowAction_alwaysPutAppAndChatScopes() {
        GroupsSettings.restore(GroupsConfig(groups = listOf(LaunchpadGroup("work", "Work", order = 0))))
        rule.setContent { Themed(false) { Box(Modifier.fillMaxSize()) { GroupAssignContent(lpGColors(), feed[4], GroupsSettings.snapshot(), NoContacts, feed, {}, {}) } } }
        rule.onNodeWithTag("grp.scope.app").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("grp.pick.work").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(listOf(GroupAssignment(AssignKind.App, "demo.cal", "work")), GroupsSettings.snapshot().assignments)
    }

    @Test fun STR_13_rowAction_createAGroupFromTheSheet() {
        rule.setContent { Themed(false) { Box(Modifier.fillMaxSize()) { GroupAssignContent(lpGColors(), feed[0], GroupsSettings.config.value, NoContacts, feed, {}, {}) } } }
        rule.onNodeWithTag("grp.newName").performScrollTo().performTextInput("Friends")
        rule.onNodeWithTag("grp.create").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(listOf("Friends"), GroupsSettings.snapshot().groups.map { it.name })
    }

    @Test fun STR_13_rowAction_linkTwoSendersIntoOnePerson() {
        rule.setContent { Themed(false) { Box(Modifier.fillMaxSize()) { GroupAssignContent(lpGColors(), feed[0], GroupsConfig(), NoContacts, listOf(feed[0], feed[2]), {}, {}) } } }
        rule.onNodeWithTag("grp.same").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithText("Ravi Kumar").performClick(); rule.waitForIdle()
        val p = GroupsSettings.snapshot().people
        assertEquals(1, p.size)
        assertTrue(p[0].aliases.any { it.contains("Priya") } && p[0].aliases.any { it.contains("Ravi") })
    }

    // ---- rule editor --------------------------------------------------------------------

    @Test fun STR_13_ruleEditor_previewSummaryAndSampleTest() {
        val cfg = config.copy(rules = emptyList())
        var saved: GroupRule? = null
        rule.setContent {
            Themed(false) {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    GroupRuleEditor(lpGColors(), cfg, GroupRule("new", listOf(Condition(ConditionKind.LastNameIsMine)), "fam"), feed, NoContacts, emptyList(), { saved = it }, {})
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Matches 2 current notifications", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("grp.summary").assertExists()
        val summary = rule.onNodeWithTag("grp.summary").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
        assertEquals("Anyone whose last name is Sharma → Family (Priority)", summary)
        rule.onNodeWithTag("grp.sample").performScrollTo().performTextInput("Anil Sharma")
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("Would match: Anil Sharma").fetchSemanticsNodes().isNotEmpty())
        shot("rule-editor")
        rule.onNodeWithTag("grp.save").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals("fam", saved?.groupId)
        assertEquals(listOf(ConditionKind.LastNameIsMine), saved?.conditions?.map { it.kind })
    }

    @Test fun STR_13_ruleEditor_addAConditionRowAndRemoveIt() {
        val cfg = config.copy(rules = emptyList())
        rule.setContent { Themed(true) { Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { GroupRuleEditor(lpGColors(), cfg, GroupRule("n", emptyList(), "work"), feed, NoContacts, emptyList(), {}, {}) } } }
        rule.waitForIdle()
        shown("Add a condition to see what it matches.")
        rule.onNodeWithTag("grp.addCondition").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithText("Sender name contains").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("grp.value.0").performScrollTo().performTextInput("ravi")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Matches 2 current notifications", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("grp.remove.0").performScrollTo().performClick(); rule.waitForIdle()
        assertTrue(rule.onAllNodesWithTag("grp.condition.0").fetchSemanticsNodes().isEmpty())
        shot("rule-editor-dark")
    }

    @Test fun STR_13_ruleEditor_saveIsRefusedWithoutAnyCondition() {
        var saved = 0
        rule.setContent { Themed(false) { Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { GroupRuleEditor(lpGColors(), config, GroupRule("n", emptyList(), "work"), feed, NoContacts, emptyList(), { saved++ }, {}) } } }
        rule.onNodeWithTag("grp.save").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(0, saved)
    }

    // ---- manager ------------------------------------------------------------------------

    @Test fun STR_13_manager_createReorderPriorityAndDelete() {
        rule.setContent { Themed(false) { Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { LaunchpadGroupsPane() } } }
        rule.onNodeWithTag("grp.pane.newName").performScrollTo().performTextInput("Work")
        rule.onNodeWithTag("grp.pane.add").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("grp.pane.newName").performScrollTo().performTextInput("Family")
        rule.onNodeWithTag("grp.pane.add").performScrollTo().performClick(); rule.waitForIdle()
        var g = GroupsSettings.snapshot().groups
        assertEquals(listOf("Work", "Family"), g.sortedBy { it.order }.map { it.name })
        val fam = g.first { it.name == "Family" }.id
        rule.onNodeWithTag("grp.card.$fam.up").performScrollTo().performClick(); rule.waitForIdle()   // keyboard reachable Move up
        g = GroupsSettings.snapshot().groups
        assertEquals(listOf("Family", "Work"), g.sortedBy { it.order }.map { it.name })
        rule.onNodeWithTag("grp.card.$fam.Priority").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(GroupPriority.Priority, GroupsSettings.snapshot().group(fam)?.priority)
        shot("manager")
        rule.onNodeWithTag("grp.card.$fam.delete").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("grp.card.$fam.confirm").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(listOf("Work"), GroupsSettings.snapshot().groups.map { it.name })
    }

    @Test fun STR_13_manager_lastNameIsStoredAndRulesCanBeReordered() {
        GroupsSettings.restore(config.copy(myLastName = "", rules = listOf(
            GroupRule("a", listOf(Condition(ConditionKind.LastNameIsMine)), "fam"),
            GroupRule("b", listOf(Condition(ConditionKind.AppIs, "demo.cal")), "work"),
        )))
        rule.setContent { Themed(true) { Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { LaunchpadGroupsPane() } } }
        rule.onNodeWithTag("grp.myLast").performScrollTo().performTextInput("Sharma"); rule.waitForIdle()
        assertEquals("Sharma", GroupsSettings.snapshot().myLastName)
        rule.onNodeWithTag("grp.rule.1.up").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(listOf("b", "a"), GroupsSettings.snapshot().rules.map { it.id })
        shot("manager-rules")
    }

    // ---- indicator ----------------------------------------------------------------------

    @Test fun STR_09_indicator_priorityRaisesAnAccentAndIsAnnounced() {
        rule.setContent { Themed(false) { WaitingIndicatorView(5, null, {}, priorityCount = 2, accent = androidx.compose.ui.graphics.Color(0xFFE5547A)) } }
        rule.waitForIdle()
        shown("★ 5")
        val d = rule.onNode(androidx.compose.ui.test.hasContentDescription("5 waiting, 2 priority. Comms")).fetchSemanticsNode()
        assertNotNull(d)
    }

    @Test fun STR_09_indicator_withoutPriorityKeepsTheOldLook() {
        rule.setContent { Themed(false) { WaitingIndicatorView(5, null, {}) } }
        rule.waitForIdle()
        shown("5")
        assertTrue(rule.onAllNodesWithText("★", substring = true).fetchSemanticsNodes().isEmpty())
    }

    private fun shot(name: String) { shoot("launchpad-groups-$orientation-$name") }
}
