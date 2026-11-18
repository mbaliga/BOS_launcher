package com.bos.sphere.core.data

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 24 second review: "Link another name" from a group's member list ([GroupPeople.linkInto]). */
class StrGroupsReviewW24Test {
    private val zone = ZoneOffset.UTC
    private fun msg(key: String, pkg: String, sender: String) = HubNotification(
        id = key, key = key, source = "x", title = sender, summary = "t", timestamp = 1L, packageName = pkg, appLabel = pkg,
        conversation = sender, sender = sender, isMessaging = true, style = "messaging",
    )
    private val cfg = GroupsConfig(
        groups = listOf(LaunchpadGroup("fam", "Family", order = 0), LaunchpadGroup("work", "Work", order = 1)),
        people = listOf(Person("p:priya", "Priya Sharma", listOf("Priya Sharma")), Person("p:ravi", "Ravi Kumar", listOf("Ravi Kumar"), contactKey = "k1")),
        assignments = listOf(GroupAssignment(AssignKind.Person, "p:priya", "fam"), GroupAssignment(AssignKind.Person, "p:ravi", "work")),
    )
    private val feed = listOf(msg("a", "demo.chat", "Priya Sharma"), msg("c", "demo.wa", "Ravi Kumar"))

    @Test fun STR_13_linkingKeepsThePersonInTheGroupItWasDoneFromAndKeepsItsContact() {
        val priya = GroupPeople.candidates(feed, GroupResolver(cfg, zone = zone)).single { it.identity == "p:priya" }
        val next = GroupPeople.linkInto(cfg, "p:ravi", priya, "work")
        val ravi = next.person("p:ravi")!!
        assertTrue(ravi.aliases.contains("Priya Sharma"))
        assertEquals("k1", ravi.contactKey)
        assertEquals(listOf(GroupAssignment(AssignKind.Person, "p:ravi", "work")), next.assignments)
        val r = GroupResolver(next, zone = zone)
        assertEquals("work", r.resolve(feed[0]).group?.id)
        assertEquals("work", r.resolve(feed[1]).group?.id)
    }

    @Test fun STR_13_linkingToAMissingPersonOrGroupChangesNothing() {
        val priya = GroupPeople.candidates(feed, GroupResolver(cfg, zone = zone)).single { it.identity == "p:priya" }
        assertSame(cfg, GroupPeople.linkInto(cfg, "p:gone", priya, "work"))
        assertSame(cfg, GroupPeople.linkInto(cfg, "p:ravi", priya, "nope"))
    }
}
