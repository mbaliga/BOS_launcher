package com.bos.sphere.ui

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import com.bos.sphere.core.data.AssignKind
import com.bos.sphere.core.data.AssignTargets
import com.bos.sphere.core.data.ConditionKind
import com.bos.sphere.core.data.GroupAssignment
import com.bos.sphere.core.data.GroupPeople
import com.bos.sphere.core.data.GroupPriority
import com.bos.sphere.core.data.GroupResolver
import com.bos.sphere.core.data.GroupsConfig
import com.bos.sphere.core.data.GroupsSettings
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadGroup
import com.bos.sphere.core.data.LaunchpadSettings
import com.bos.sphere.core.data.NotificationProvider
import com.bos.sphere.core.data.PendingIntentLauncher
import com.bos.sphere.core.data.PendingIntentSender
import com.bos.sphere.core.data.SphereNotificationListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Wave 24 (owner request): Launchpad grouping end to end through the real `LaunchpadPanel` and its own UI, with seeded
 * notifications (the system listener is not running in Robolectric; the feed is published the way the listener publishes it).
 * A rule made in the UI regroups the feed, deleting it falls back, a person picked in the manager pulls their chats from every
 * app into a group and that survives a restart of the stores, Priority ordering and the Do Not Disturb breakthrough hold, and a
 * row tap goes through `PendingIntentLauncher` with the background-start opt-in.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h2400dp-xhdpi", sdk = [35])
class LaunchpadGroupsEndToEndW24Test {
    @get:org.junit.Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = rule.activity.applicationContext
    private val sent = mutableListOf<Pair<PendingIntent, Bundle?>>()
    private val original = PendingIntentLauncher.sender

    private fun pi(i: Int) = PendingIntent.getActivity(app, i, Intent("e2e.open.$i").setPackage(app.packageName), PendingIntent.FLAG_IMMUTABLE)

    private fun msg(i: Int, key: String, pkg: String, appLabel: String, sender: String) = HubNotification(
        id = key, key = key, source = "x", title = sender, summary = "message $key", timestamp = System.currentTimeMillis() - i * 60_000L,
        packageName = pkg, appLabel = appLabel, conversation = sender, sender = sender, isMessaging = true, style = "messaging", contentIntent = pi(i),
    )

    private val feed by lazy {
        listOf(
            msg(1, "a", "demo.chat", "Chat", "Priya Sharma"),
            msg(2, "b", "demo.sms", "SMS", "Priya Sharma (SMS)"),
            msg(3, "c", "demo.wa", "Whats", "Ravi Kumar (WhatsApp)"),
            msg(4, "d", "demo.chat", "Chat", "Ravi Kumar"),
            HubNotification(id = "e", key = "e", source = "x", title = "Sale ends today", summary = "50% off", timestamp = System.currentTimeMillis() - 5 * 60_000L, packageName = "demo.shop", appLabel = "Shop", contentIntent = pi(5)),
        )
    }

    private var shown by mutableStateOf(true)

    @Before fun seed() {
        android.provider.Settings.Secure.putString(
            app.contentResolver, "enabled_notification_listeners", ComponentName(app.packageName, SphereNotificationListener::class.java.name).flattenToString(),
        )
        // Each test has a new Application: bind the stores to it (a store bound to an earlier test's prefs would fake persistence).
        GroupsSettings.forgetInMemoryCopy(); GroupsSettings.init(app); GroupsSettings.restore(GroupsConfig())
        LaunchpadSettings.init(app); LaunchpadSettings.restore(com.bos.sphere.core.data.LaunchpadRulesSnapshot()); LaunchpadSettings.pruneRead(emptySet())
        NotificationProvider.publishForTest(feed)
        PendingIntentLauncher.sender = PendingIntentSender { p, _, _, options -> sent += p to options }
    }

    @After fun restore() {
        PendingIntentLauncher.sender = original
        GroupsSettings.restore(GroupsConfig())
        NotificationProvider.publishForTest(emptyList())
        setDnd(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    private fun setDnd(filter: Int) {
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Shadows.shadowOf(nm).setNotificationPolicyAccessGranted(true)
        nm.setInterruptionFilter(filter)
    }

    private fun launch() {
        rule.setContent { if (shown) LaunchpadPanel(visible = true, onDismiss = {}, onOpenApp = {}) }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("lp.row.a").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun tagOf(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.TestTag).orEmpty()

    /** The column top to bottom as (section key, row keys), read from where the real heads and rows are laid out. */
    private fun column(): List<Pair<String, List<String>>> {
        rule.waitForIdle()
        val nodes = rule.onAllNodes(SemanticsMatcher("head or row") { tagOf(it).startsWith("lp.group.") || tagOf(it).startsWith("lp.row.") }, useUnmergedTree = true)
            .fetchSemanticsNodes().sortedBy { it.boundsInRoot.top }
        val out = ArrayList<Pair<String, MutableList<String>>>()
        nodes.forEach { n ->
            val t = tagOf(n)
            if (t.startsWith("lp.group.")) out += t.removePrefix("lp.group.") to mutableListOf()
            else out.lastOrNull()?.second?.add(t.removePrefix("lp.row."))
        }
        return out
    }

    private fun sectionOf(key: String) = column().firstOrNull { key in it.second }?.first

    private fun openGroups() {
        rule.onNodeWithTag("lp.menu").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("lp.menu.groups").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("lp.groups").assertExists()
    }

    private fun closeGroups() {
        rule.onNodeWithTag("lp.groups.done").performClick(); rule.waitForIdle()
        assertTrue(rule.onAllNodesWithTag("lp.groups").fetchSemanticsNodes().isEmpty())
    }

    private fun click(tag: String) { rule.onNodeWithTag(tag).performScrollTo().performClick(); rule.waitForIdle() }
    private fun type(tag: String, text: String) { rule.onNodeWithTag(tag).performScrollTo().performTextInput(text); rule.waitForIdle() }

    /** Through the UI only: a Priority group "Family", my last name, and the rule "sender last name is my last name -> Family". */
    private fun makeFamilyRuleInTheUi(): String {
        openGroups()
        type("grp.pane.newName", "Family")
        click("grp.pane.add")
        val fam = GroupsSettings.snapshot().groups.single { it.name == "Family" }.id
        click("grp.card.$fam.Priority")
        type("grp.myLast", "Sharma")
        click("grp.pane.addRule")
        click("grp.addCondition")
        rule.onNodeWithText(GroupTexts.kindLabel(ConditionKind.LastNameIsMine)).performClick(); rule.waitForIdle()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Matches 2 current notifications", substring = true).fetchSemanticsNodes().isNotEmpty() }
        click("grp.save")
        closeGroups()
        return fam
    }

    @Test fun STR_13_aRuleCreatedInTheLaunchpadUiRegroupsTheSeededNotifications() {
        launch()
        assertTrue("no groups yet: one plain column", column().none { it.first.startsWith("group:") })
        val fam = makeFamilyRuleInTheUi()
        val cfg = GroupsSettings.snapshot()
        assertEquals(GroupPriority.Priority, cfg.group(fam)?.priority)
        assertEquals(listOf(ConditionKind.LastNameIsMine), cfg.rules.single().conditions.map { it.kind })
        rule.waitUntil(10_000) { sectionOf("a") == "group:$fam" }
        val col = column()
        assertEquals("the Priority group is the first section", "group:$fam", col.first().first)
        assertEquals("both apps' chats with Priya are in Family", setOf("a", "b"), col.first().second.toSet())
        assertEquals("everything else stays in the built-in section", "group:_", sectionOf("c"))
        assertEquals("group:_", sectionOf("e"))
    }

    @Test fun STR_13_deletingTheRuleInTheUiFallsBackCleanly() {
        launch()
        val fam = makeFamilyRuleInTheUi()
        rule.waitUntil(10_000) { sectionOf("a") == "group:$fam" }
        openGroups()
        click("grp.rule.0.delete")
        assertTrue(GroupsSettings.snapshot().rules.isEmpty())
        assertNotNull("the group itself stays", GroupsSettings.snapshot().group(fam))
        closeGroups()
        rule.waitUntil(10_000) { sectionOf("a") == "group:_" }
        val col = column()
        assertFalse("an empty group draws no section", col.any { it.first == "group:$fam" })
        assertEquals("every notification is still listed, nothing lost", setOf("a", "b", "c", "d", "e"), col.flatMap { it.second }.toSet())
    }

    @Test fun STR_13_aPersonPickedInTheManagerPullsTheirChatsFromEveryAppIntoTheGroupAndSurvivesARestart() {
        launch()
        openGroups()
        type("grp.pane.newName", "Work")
        click("grp.pane.add")
        val work = GroupsSettings.snapshot().groups.single { it.name == "Work" }.id
        click("grp.card.$work.addPeople")
        val idx = GroupPeople.candidates(feed, GroupResolver(GroupsSettings.snapshot())).indexOfFirst { it.name == "Ravi Kumar" }
        assertTrue(idx >= 0)
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("grp.card.$work.person.$idx").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("grp.card.$work.person.$idx").performScrollTo().assertExists()
        assertTrue("the picker names both apps", rule.onAllNodesWithText("Ravi Kumar  ·  Whats, Chat", substring = true).fetchSemanticsNodes().isNotEmpty())
        click("grp.card.$work.person.$idx")
        assertEquals(AssignKind.Person, GroupsSettings.snapshot().assignments.single().kind)
        rule.onNodeWithTag("grp.card.$work.member.0").performScrollTo().assertExists()
        closeGroups()
        rule.waitUntil(10_000) { sectionOf("c") == "group:$work" }
        assertEquals("group:$work", sectionOf("d"))
        assertEquals("group:_", sectionOf("a"))

        // Restart: the panel goes, the in-memory copy is dropped, and a new panel reads the stored groups back.
        rule.runOnIdle { Snapshot.withMutableSnapshot { shown = false } }
        rule.waitForIdle()
        GroupsSettings.forgetInMemoryCopy()
        assertTrue("nothing is left in memory", GroupsSettings.snapshot().groups.isEmpty())
        rule.runOnIdle { Snapshot.withMutableSnapshot { shown = true } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("lp.row.c").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(10_000) { sectionOf("c") == "group:$work" }
        assertEquals("group:$work", sectionOf("d"))
        assertEquals("Work", GroupsSettings.snapshot().group(work)?.name)
    }

    @Test fun STR_13_priorityOrderingHoldsInThePanelPriorityNormalBuiltInQuiet() {
        GroupsSettings.restore(
            GroupsConfig(
                groups = listOf(
                    LaunchpadGroup("work", "Work", order = 0),
                    LaunchpadGroup("promo", "Promos", order = 1, priority = GroupPriority.Quiet),
                    LaunchpadGroup("fam", "Family", order = 2, priority = GroupPriority.Priority),
                ),
                assignments = listOf(
                    GroupAssignment(AssignKind.App, AssignTargets.app("demo.shop"), "promo"),
                    GroupAssignment(AssignKind.App, AssignTargets.app("demo.wa"), "work"),
                    GroupAssignment(AssignKind.App, AssignTargets.app("demo.sms"), "fam"),
                ),
            ),
        )
        launch()
        rule.waitUntil(10_000) { sectionOf("e") == "group:promo" }
        assertEquals(listOf("group:fam", "group:work", "group:_", "group:promo"), column().map { it.first })
        // STR-02 (owner complaint): a Quiet group sits last but is not folded, its rows are in the list.
        assertEquals(listOf("e"), column().last().second)
    }

    @Test fun STR_13_whileDndIsOnPriorityBreaksThroughInLiftoffAndTheColumnSaysSo() {
        GroupsSettings.restore(
            GroupsConfig(
                groups = listOf(LaunchpadGroup("fam", "Family", priority = GroupPriority.Priority)),
                assignments = listOf(GroupAssignment(AssignKind.App, AssignTargets.app("demo.sms"), "fam")),
            ),
        )
        setDnd(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        var gate by mutableStateOf(true)
        rule.setContent {
            if (gate) androidx.compose.foundation.layout.Column {
                LaunchpadWaitingIndicator(onOpen = {})
                LaunchpadPanel(visible = true, onDismiss = {}, onOpenApp = {})
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("lp.dnd").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(10_000) { rule.onAllNodes(SemanticsMatcher("indicator") { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() == "1 waiting, 1 priority. Comms" }).fetchSemanticsNodes().isNotEmpty() }
        // The list still shows everything; only the edge indicator is held back.
        assertEquals(setOf("a", "b", "c", "d", "e"), column().flatMap { it.second }.toSet())
        // Switched off in the manager: Do Not Disturb no longer holds anything back.
        rule.runOnIdle { GroupsSettings.setPriorityBreaksDnd(false) }
        rule.waitUntil(10_000) { rule.onAllNodes(SemanticsMatcher("indicator") { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() == "5 waiting, 1 priority. Comms" }).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodesWithTag("lp.dnd").fetchSemanticsNodes().isEmpty())
        rule.runOnIdle { Snapshot.withMutableSnapshot { gate = false } }
    }

    @Test fun STR_13_theGroupsManagerOpensFromLaunchpadAndEscapeClosesIt() {
        launch()
        openGroups()
        rule.onNodeWithTag("lp.groups").performKeyInput { pressKey(Key.Escape) }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithTag("lp.groups").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("lp.root").assertExists()
    }

    @Test fun STR_02_aTapOnAGroupedRowSendsItsOwnPendingIntentWithTheBackgroundStartOptIn() {
        GroupsSettings.restore(GroupsConfig(groups = listOf(LaunchpadGroup("w", "Work")), assignments = listOf(GroupAssignment(AssignKind.App, AssignTargets.app("demo.wa"), "w"))))
        launch()
        rule.waitUntil(10_000) { sectionOf("c") == "group:w" }
        rule.onNodeWithTag("lp.focus.c", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertEquals(1, sent.size)
        assertEquals(feed.first { it.key == "c" }.contentIntent, sent.single().first)
        assertNotNull("Android 14+: the sender's background-activity-start opt-in travels with it", sent.single().second)
        // Review: not just any bundle; the same opt-in options PendingIntentLauncher builds, and not an empty one.
        val keys = sent.single().second!!.keySet()
        assertTrue("the options bundle is not empty", keys.isNotEmpty())
        assertEquals(PendingIntentLauncher.options()!!.keySet(), keys)
        assertTrue("the tapped row is marked read", "c" in LaunchpadSettings.readKeys.value)
    }

    @Test fun STR_02_groupedRowsAre70dpOrMore() {
        GroupsSettings.restore(GroupsConfig(groups = listOf(LaunchpadGroup("w", "Work")), assignments = listOf(GroupAssignment(AssignKind.App, AssignTargets.app("demo.wa"), "w"))))
        launch()
        rule.waitUntil(10_000) { sectionOf("c") == "group:w" }
        val density = rule.activity.resources.displayMetrics.density
        rule.onAllNodes(hasTestTagPrefix("lp.row."), useUnmergedTree = true).fetchSemanticsNodes().forEach { n ->
            assertTrue("${tagOf(n)} is ${n.boundsInRoot.height / density} dp", n.boundsInRoot.height / density >= 70f - 0.5f)
        }
    }

    private fun hasTestTagPrefix(p: String) = SemanticsMatcher("tag starts with $p") { tagOf(it).startsWith(p) }

    @Suppress("unused") private val keepImport = hasTestTag("x")
}
