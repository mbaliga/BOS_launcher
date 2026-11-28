package com.bos.sphere.core.data

import android.app.NotificationManager
import java.time.ZoneOffset
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 24 (launchpadRules): Priority groups break through system Do Not Disturb inside Liftoff's own edge indicator, the
 * people picker of the Groups manager (one person across every messaging app), the members of a group, and their persistence.
 */
class StrGroupsW24Test {
    private val zone = ZoneOffset.UTC
    private val now = 1_700_000_000_000L
    private fun msg(key: String, pkg: String, app: String, sender: String, conv: String? = sender) = HubNotification(
        id = key, key = key, source = "x", title = sender, summary = "t", timestamp = now - 1000, packageName = pkg, appLabel = app,
        conversation = conv, sender = sender, isMessaging = true, style = "messaging",
    )
    private fun plain(key: String, pkg: String) = HubNotification(id = key, key = key, source = "x", title = key, summary = "t", timestamp = now - 1000, packageName = pkg)

    private val fam = LaunchpadGroup("fam", "Family", order = 0, priority = GroupPriority.Priority)
    private val work = LaunchpadGroup("work", "Work", order = 1)
    private val cfg = GroupsConfig(
        groups = listOf(fam, work),
        rules = listOf(GroupRule("r", listOf(Condition(ConditionKind.LastNameIsMine)), "fam")),
        myLastName = "Sharma",
    )
    private val feed = listOf(
        msg("a", "demo.chat", "Chat", "Priya Sharma"),
        msg("b", "demo.sms", "SMS", "Priya Sharma (SMS)"),
        msg("c", "demo.wa", "Whats", "Ravi Kumar (WhatsApp)"),
        msg("d", "demo.chat", "Chat", "Ravi Kumar"),
        plain("e", "demo.mail"),
    )

    // ---- Do Not Disturb breakthrough (Liftoff surfaces only) ----

    @Test fun STR_13_whileDndIsOnOnlyPriorityGroupItemsCountTowardsTheEdgeIndicator() {
        val r = GroupResolver(cfg, zone = zone)
        val off = GroupWaiting.waiting(feed, emptyList(), emptyList(), emptySet(), emptyMap(), now, zone, r, dndOn = false)
        assertEquals(setOf("a", "b", "c", "d", "e"), off.map { it.key }.toSet())
        val on = GroupWaiting.waiting(feed, emptyList(), emptyList(), emptySet(), emptyMap(), now, zone, r, dndOn = true)
        assertEquals("the Priority group breaks through, nothing else lights the edge", setOf("a", "b"), on.map { it.key }.toSet())
        assertEquals(2, GroupWaiting.priority(on, r).count)
    }

    @Test fun STR_13_dndBreakthroughCanBeTurnedOffAndThenDndChangesNothing() {
        val r = GroupResolver(cfg.copy(priorityBreaksDnd = false), zone = zone)
        val on = GroupWaiting.waiting(feed, emptyList(), emptyList(), emptySet(), emptyMap(), now, zone, r, dndOn = true)
        assertEquals(5, on.size)
    }

    @Test fun STR_13_withoutAPriorityGroupDndChangesNothing() {
        // No group can break through, so holding everything back would just hide the indicator: the old behaviour stays.
        val r = GroupResolver(cfg.copy(groups = listOf(work), rules = emptyList()), zone = zone)
        assertEquals(5, GroupWaiting.waiting(feed, emptyList(), emptyList(), emptySet(), emptyMap(), now, zone, r, dndOn = true).size)
        assertEquals(5, GroupWaiting.waiting(feed, emptyList(), emptyList(), emptySet(), emptyMap(), now, zone, null, dndOn = true).size)
    }

    @Test fun STR_13_priorityStillBreaksThroughADigestWhileDndIsOn() {
        val r = GroupResolver(cfg, zone = zone)
        val digest = listOf(AppPolicy("demo.chat", DeliveryMode.Digest, listOf(8 * 60)), AppPolicy("demo.sms", DeliveryMode.SilentInLaunchpad))
        val on = GroupWaiting.waiting(feed, emptyList(), digest, emptySet(), emptyMap(), now, zone, r, dndOn = true)
        assertEquals(setOf("a", "b"), on.map { it.key }.toSet())
    }

    @Test fun STR_13_dndIsReadFromThePublicInterruptionFilter() {
        assertFalse(DndState.isOnFor(NotificationManager.INTERRUPTION_FILTER_ALL))
        assertFalse("unknown is not quiet", DndState.isOnFor(NotificationManager.INTERRUPTION_FILTER_UNKNOWN))
        assertTrue(DndState.isOnFor(NotificationManager.INTERRUPTION_FILTER_PRIORITY))
        assertTrue(DndState.isOnFor(NotificationManager.INTERRUPTION_FILTER_ALARMS))
        assertTrue(DndState.isOnFor(NotificationManager.INTERRUPTION_FILTER_NONE))
        assertTrue(GroupDnd.breaksThrough(cfg, dndOn = true))
        assertFalse(GroupDnd.breaksThrough(cfg, dndOn = false))
        assertFalse(GroupDnd.breaksThrough(cfg.copy(priorityBreaksDnd = false), dndOn = true))
    }

    @Test fun STR_13_theDndSwitchPersistsInTheGroupsDocumentAndTheDefaultIsOn() {
        assertTrue(GroupsConfig().priorityBreaksDnd)
        val encoded = GroupsJson.encode(cfg.copy(priorityBreaksDnd = false))
        assertFalse(encoded.getBoolean("priorityBreaksDnd"))
        assertFalse(GroupsJson.decode(JSONObject(encoded.toString())).priorityBreaksDnd)
        assertTrue("an older document without the key keeps the default", GroupsJson.decode(JSONObject("{\"v\":1}")).priorityBreaksDnd)
        assertTrue(GroupsJson.decode(GroupsJson.encode(cfg)).priorityBreaksDnd)
        assertFalse("turning it off is a change, so the state is not empty", GroupsConfig(priorityBreaksDnd = false).isEmpty)
        assertTrue(GroupsConfig().isEmpty)
    }

    // ---- the people picker: one person across every messaging app ----

    @Test fun STR_13_thePeoplePickerMergesOneSenderAcrossApps() {
        val people = GroupPeople.candidates(feed, GroupResolver(cfg, zone = zone))
        assertEquals(listOf("Priya Sharma", "Ravi Kumar"), people.map { it.name })
        val priya = people.first()
        assertEquals(listOf("Chat", "SMS"), priya.apps)
        assertEquals(2, priya.count)
        assertEquals("fam", priya.currentGroupId)
        assertEquals(null, people[1].currentGroupId)
    }

    @Test fun STR_13_pickingAPersonPullsTheirChatsFromEveryAppIntoTheGroup() {
        var c = cfg
        val ravi = GroupPeople.candidates(feed, GroupResolver(c, zone = zone)).first { it.name == "Ravi Kumar" }
        c = GroupPeople.addToGroup(c, ravi, "work", id = "p:ravi")
        val r = GroupResolver(c, zone = zone)
        assertEquals("work", r.resolve(feed.first { it.key == "c" }).group?.id)
        assertEquals("work", r.resolve(feed.first { it.key == "d" }).group?.id)
        assertEquals(GroupSource.Manual, r.resolve(feed.first { it.key == "c" }).source)
        // A typed name (someone with nothing in the feed now) works the same way.
        c = GroupPeople.addNameToGroup(c, "Anita Rao", "work", id = "p:anita")
        assertEquals("work", GroupResolver(c, zone = zone).resolve(msg("z", "demo.signal", "Signal", "Anita Rao (Signal)")).group?.id)
    }

    @Test fun STR_13_theMembersOfAGroupAreListedAndRemovable() {
        var c = GroupPeople.addNameToGroup(cfg, "Ravi Kumar", "work", id = "p:ravi")
        c = GroupsEdit.assign(c, AssignKind.App, AssignTargets.app("demo.mail"), "work")
        c = GroupsEdit.assign(c, AssignKind.Conversation, AssignTargets.conversation("demo.chat", "Team"), "work")
        val m = GroupPeople.members(c, "work", feed)
        assertEquals(listOf(AssignKind.Person, AssignKind.App, AssignKind.Conversation), m.map { it.kind })
        assertEquals("Ravi Kumar", m[0].label)
        assertEquals("demo.mail", m[1].label)
        assertEquals("the stored chat key is all there is when the chat is not in the feed", "team", m[2].label)
        assertTrue(GroupPeople.members(c, "fam", feed).isEmpty())
        val removed = GroupsEdit.unassign(c, m[0].kind, m[0].target)
        assertEquals(null, GroupResolver(removed, zone = zone).resolve(feed.first { it.key == "d" }).group?.id)
    }

    @Test fun STR_13_aSecondNameCanBeLinkedToAPickedPersonFromTheManager() {
        var c = GroupPeople.addNameToGroup(cfg, "Ravi", "work", id = "p:ravi")
        assertEquals(null, GroupResolver(c, zone = zone).resolve(feed.first { it.key == "c" }).group?.id)
        c = GroupsEdit.linkSenders(c, "Ravi", "Ravi Kumar (WhatsApp)")
        val r = GroupResolver(c, zone = zone)
        assertEquals("work", r.resolve(feed.first { it.key == "c" }).group?.id)
        assertEquals("work", r.resolve(feed.first { it.key == "d" }).group?.id)
    }

    @Test fun STR_13_groupChatsAreNotOfferedAsPeople() {
        val list = feed + msg("g", "demo.chat", "Chat", "Ana", conv = "Team")
        assertFalse(GroupPeople.candidates(list, GroupResolver(cfg, zone = zone)).any { it.name == "Ana" })
    }
}
