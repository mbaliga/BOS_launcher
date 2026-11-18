package com.bos.sphere.ui

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import com.bos.sphere.core.data.AssignKind
import com.bos.sphere.core.data.GroupAssignment
import com.bos.sphere.core.data.GroupsConfig
import com.bos.sphere.core.data.GroupsSettings
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadGroup
import com.bos.sphere.core.data.LaunchpadSettings
import com.bos.sphere.core.data.NotificationProvider
import com.bos.sphere.core.data.Person
import com.bos.sphere.core.data.SphereNotificationListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Wave 24 second review: what the first pass did not check about the Groups manager opened inside Launchpad. Its Done button
 * must not sit under the home card's return zone, Back and Escape close only the manager (Launchpad stays), and "Link another
 * name" from a group's member keeps that person in the group it was done from.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h2400dp-xhdpi", sdk = [35])
class LaunchpadGroupsReviewW24Test {
    @get:org.junit.Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = rule.activity.applicationContext
    private var dismissed = 0

    private fun pi(i: Int) = PendingIntent.getActivity(app, i, Intent("r.open.$i").setPackage(app.packageName), PendingIntent.FLAG_IMMUTABLE)
    private fun msg(i: Int, key: String, pkg: String, appLabel: String, sender: String) = HubNotification(
        id = key, key = key, source = "x", title = sender, summary = "message $key", timestamp = System.currentTimeMillis() - i * 60_000L,
        packageName = pkg, appLabel = appLabel, conversation = sender, sender = sender, isMessaging = true, style = "messaging", contentIntent = pi(i),
    )
    private val feed by lazy {
        listOf(
            msg(1, "a", "demo.chat", "Chat", "Priya Sharma"),
            msg(2, "c", "demo.wa", "Whats", "Ravi Kumar"),
        )
    }

    @Before fun seed() {
        android.provider.Settings.Secure.putString(
            app.contentResolver, "enabled_notification_listeners", ComponentName(app.packageName, SphereNotificationListener::class.java.name).flattenToString(),
        )
        GroupsSettings.forgetInMemoryCopy(); GroupsSettings.init(app); GroupsSettings.restore(GroupsConfig())
        LaunchpadSettings.init(app); LaunchpadSettings.restore(com.bos.sphere.core.data.LaunchpadRulesSnapshot())
        NotificationProvider.publishForTest(feed)
    }

    @After fun restore() {
        GroupsSettings.restore(GroupsConfig())
        NotificationProvider.publishForTest(emptyList())
    }

    private fun launch(underlay: Boolean = false) {
        rule.setContent { LaunchpadPanel(visible = true, onDismiss = { dismissed++ }, onOpenApp = {}, underlay = underlay) }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("lp.row.a").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openGroups() {
        rule.onNodeWithTag("lp.menu").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("lp.menu.groups").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("lp.groups").assertExists()
    }

    private fun groupsOpen() = rule.onAllNodesWithTag("lp.groups").fetchSemanticsNodes().isNotEmpty()

    @Test fun STR_01_besideTheHomeCardTheManagersDoneButtonIsClearOfTheCardsReturnZone() {
        launch(underlay = true)
        openGroups()
        val density = rule.activity.resources.displayMetrics.density
        val width = rule.onNodeWithTag("lp.root").fetchSemanticsNode().boundsInRoot.right
        // At 400 dp the sliver is thinner than 48 dp, so the return zone is the 48 dp touch target on the right.
        val zoneLeft = width - 48f * density
        val done = rule.onNodeWithTag("lp.groups.done").fetchSemanticsNode().boundsInRoot
        assertTrue("Done ends at ${done.right / density} dp, the card's return zone starts at ${zoneLeft / density} dp", done.right <= zoneLeft + 0.5f)
    }

    @Test fun STR_08_escapeClosesOnlyTheManagerNotLaunchpad() {
        launch()
        openGroups()
        rule.onNodeWithTag("lp.groups").performKeyInput { pressKey(Key.Escape) }
        rule.waitForIdle()
        assertTrue(!groupsOpen())
        assertEquals("Launchpad itself was not dismissed", 0, dismissed)
    }

    @Test fun STR_08_backClosesOnlyTheManagerNotLaunchpad() {
        launch()
        openGroups()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertTrue("Back closed the manager", !groupsOpen())
        assertEquals("Launchpad itself was not dismissed", 0, dismissed)
        rule.onNodeWithTag("lp.root").assertExists()
    }

    @Test fun STR_13_linkingAnotherNameFromAGroupKeepsThePersonInThatGroup() {
        // Priya's stored person was put in Family first; Ravi was then picked into Work. From Work, "Link another name" joins
        // Priya's name to Ravi (the same human under another name): the result must stay in Work, where the user did it.
        GroupsSettings.restore(
            GroupsConfig(
                groups = listOf(LaunchpadGroup("fam", "Family", order = 0), LaunchpadGroup("work", "Work", order = 1)),
                people = listOf(Person("p:priya", "Priya Sharma", listOf("Priya Sharma")), Person("p:ravi", "Ravi Kumar", listOf("Ravi Kumar"))),
                assignments = listOf(GroupAssignment(AssignKind.Person, "p:priya", "fam"), GroupAssignment(AssignKind.Person, "p:ravi", "work")),
            ),
        )
        launch()
        openGroups()
        rule.onNodeWithTag("grp.card.work.member.0").performScrollTo().assertExists()
        rule.onNodeWithTag("grp.card.work.member.0.link").performScrollTo().performClick(); rule.waitForIdle()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("grp.card.work.member.0.link.0").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("grp.card.work.member.0.link.0").performScrollTo().performClick(); rule.waitForIdle()
        val c = GroupsSettings.snapshot()
        val ravi = c.person("p:ravi")
        assertTrue("Priya's name is now an alias of Ravi's person: ${c.people}", ravi != null && ravi.aliases.any { it == "Priya Sharma" })
        assertEquals("the linked person stays in Work", "work", c.assignments.single { it.kind == AssignKind.Person && it.target == "p:ravi" }.groupId)
    }
}
