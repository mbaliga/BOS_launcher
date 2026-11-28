package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** STR-13: the Launchpad view split by group, priority semantics against STR-04. */
class StrGroupsViewTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2024, 3, 10)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val now = at(12)

    private fun n(key: String, sender: String, pkg: String = "chat.a", ts: Long = at(10), ongoing: Boolean = false, conv: String? = null) =
        HubNotification(id = key, key = key, source = "x", title = sender, summary = "hi", timestamp = ts, packageName = pkg, appLabel = pkg,
            sender = sender, conversation = conv ?: sender, isMessaging = true, isOngoing = ongoing)

    private fun build(
        items: List<HubNotification>, c: GroupsConfig, policies: List<AppPolicy> = emptyList(), rules: List<Rule> = emptyList(),
        withResolver: Boolean = true,
    ) = LaunchpadViewBuilder.build(
        items, rules, LaunchpadPrefs(), policies, emptyMap(), now, zone,
        grouping = if (withResolver) GroupResolver(c, NoContacts, zone) else null,
    )

    private val family = LaunchpadGroup("fam", "Family", priority = GroupPriority.Priority, order = 0, colorArgb = 0xFFE91E63)
    private val work = LaunchpadGroup("work", "Work", order = 1)
    private val noise = LaunchpadGroup("noise", "Noise", priority = GroupPriority.Quiet, order = 2)
    private fun config(vararg extra: GroupAssignment, groups: List<LaunchpadGroup> = listOf(family, work, noise)) =
        GroupsConfig(groups = groups, assignments = extra.toList())

    @Test fun STR_13_withoutGroupsTheViewIsExactlyToday() {
        val items = listOf(n("1", "A"), n("2", "B", pkg = "chat.b"), n("3", "C", ts = at(11)))
        val policies = listOf(AppPolicy("chat.b", DeliveryMode.SilentInLaunchpad))
        val today = LaunchpadViewBuilder.build(items, emptyList(), LaunchpadPrefs(), policies, emptyMap(), now, zone)
        for (cfg in listOf(GroupsConfig(), GroupsConfig(assignments = listOf(GroupAssignment(AssignKind.App, "chat.a", "ghost"))))) {
            val v = build(items, cfg, policies)
            assertEquals(today, v)
            assertTrue(v.grouped.isEmpty())
            assertTrue(v.priorityKeys.isEmpty())
        }
        assertEquals(today, build(items, config(), policies, withResolver = false))
    }

    @Test fun STR_13_sectionsFollowGroupsPriorityFirstQuietLastAndTheBuiltInSectionBetween() {
        val cfg = config(
            GroupAssignment(AssignKind.App, "chat.a", "work"), GroupAssignment(AssignKind.App, "chat.b", "fam"),
            GroupAssignment(AssignKind.App, "chat.c", "noise"),
        )
        val items = listOf(n("1", "A", "chat.a"), n("2", "B", "chat.b"), n("3", "C", "chat.c"), n("4", "D", "chat.d"))
        val v = build(items, cfg)
        assertEquals(listOf("group:fam", "group:work", GroupedSection.FALLBACK_KEY, "group:noise"), v.grouped.map { it.key })
        assertEquals(listOf(false, false, false, true), v.grouped.map { it.folded })
        assertEquals(setOf("2"), v.priorityKeys)
        assertEquals(mapOf("1" to "work", "2" to "fam", "3" to "noise"), v.groupOfKey)
        assertEquals(4, v.sections.sumOf { it.items.size })
        assertEquals(v.sections.size, v.sections.map { it.key }.toSet().size) // keys stay unique across groups
        assertTrue(v.grouped.first().group!!.priority == GroupPriority.Priority)
    }

    @Test fun STR_13_theOldGroupingStaysInsideASection() {
        val cfg = config(GroupAssignment(AssignKind.App, "chat.a", "work"))
        val items = listOf(n("1", "A", "chat.a", conv = "Team"), n("2", "B", "chat.a", conv = "Boss"), n("3", "A", "chat.a", ts = at(9), conv = "Team"))
        val work = build(items, cfg).grouped.single { it.group?.id == "work" }
        assertEquals(2, work.sections.size) // by conversation inside the section
        assertEquals(setOf("Team", "Boss"), work.sections.map { it.title }.toSet())
    }

    @Test fun STR_13_emptyGroupsAreNotSectionsAndAnEmptyFeedHasNone() {
        val cfg = config(GroupAssignment(AssignKind.App, "chat.a", "work"))
        val v = build(listOf(n("1", "A", "chat.a")), cfg)
        assertEquals(listOf("group:work"), v.grouped.map { it.key })
        assertTrue(build(emptyList(), cfg).grouped.isEmpty())
    }

    @Test fun STR_13_aPriorityGroupBypassesThePerAppDigestAndShowsAtOnce() {
        val digest = listOf(AppPolicy("chat.a", DeliveryMode.Digest))
        val fresh = n("1", "Mum", "chat.a", ts = at(11)) // newer than the 08:00 release: held by the digest
        val plain = build(listOf(fresh), config(), digest)
        assertEquals(listOf("1"), plain.held.map { it.key })
        val prio = build(listOf(fresh), config(GroupAssignment(AssignKind.App, "chat.a", "fam")), digest)
        assertTrue(prio.held.isEmpty())
        assertEquals(setOf("1"), prio.priorityKeys)
        assertNull(prio.nextRelease)
        // A Normal group does not bypass it: the app policy still holds the row.
        assertEquals(listOf("1"), build(listOf(fresh), config(GroupAssignment(AssignKind.App, "chat.a", "work")), digest).held.map { it.key })
    }

    @Test fun STR_13_aPriorityGroupBypassesSilentInLaunchpadAndSilentRules() {
        val silent = listOf(AppPolicy("chat.a", DeliveryMode.SilentInLaunchpad))
        val mute = listOf(Rule.create(RuleAction.Silent, packageName = "chat.a"))
        val a = n("1", "Mum", "chat.a")
        assertEquals(setOf("1"), build(listOf(a), config(), silent).silentKeys)
        assertEquals(setOf("1"), build(listOf(a), config(), rules = mute).silentKeys)
        val toFam = config(GroupAssignment(AssignKind.App, "chat.a", "fam"))
        assertTrue(build(listOf(a), toFam, silent).silentKeys.isEmpty())
        assertTrue(build(listOf(a), toFam, rules = mute).silentKeys.isEmpty())
        val toWork = config(GroupAssignment(AssignKind.App, "chat.a", "work"))
        assertEquals(setOf("1"), build(listOf(a), toWork, silent).silentKeys)
        assertEquals(setOf("1"), build(listOf(a), toWork, rules = mute).silentKeys)
    }

    @Test fun STR_13_aPriorityGroupsOwnOverrideCanMakeItSilentAndOtherGroupsOverridesAreOnlyDefaults() {
        val a = n("1", "Mum", "chat.a", ts = at(11))
        val quietFam = family.copy(delivery = DeliveryMode.SilentInLaunchpad)
        val assign = GroupAssignment(AssignKind.App, "chat.a", "fam")
        assertEquals(setOf("1"), build(listOf(a), config(assign, groups = listOf(quietFam, work, noise))).silentKeys)
        // A Digest override on a Priority group is read as Immediate.
        val digestFam = family.copy(delivery = DeliveryMode.Digest)
        assertTrue(build(listOf(a), config(assign, groups = listOf(digestFam, work, noise))).held.isEmpty())
        // A Normal group's override is the default for an app with no policy ...
        val digestWork = work.copy(delivery = DeliveryMode.Digest)
        val toWork = GroupAssignment(AssignKind.App, "chat.a", "work")
        assertEquals(listOf("1"), build(listOf(a), config(toWork, groups = listOf(family, digestWork, noise))).held.map { it.key })
        // ... and the app's own policy wins over it.
        assertTrue(build(listOf(a), config(toWork, groups = listOf(family, digestWork, noise)), listOf(AppPolicy("chat.a", DeliveryMode.Immediate))).held.isEmpty())
    }

    @Test fun STR_13_releasedDigestItemsOfAGroupedAppShowInTheirGroupNotInTheDigestSection() {
        val digest = listOf(AppPolicy("chat.a", DeliveryMode.Digest))
        val old = n("1", "A", "chat.a", ts = at(7)) // before the 08:00 release
        val ungrouped = build(listOf(old), config(), digest)
        assertEquals(LaunchpadView.DIGEST_KEY, ungrouped.sections.single().key)
        val grouped = build(listOf(old), config(GroupAssignment(AssignKind.App, "chat.a", "work")), digest)
        assertEquals(listOf("group:work"), grouped.grouped.map { it.key })
        assertFalse(grouped.sections.any { it.key == LaunchpadView.DIGEST_KEY })
    }

    @Test fun STR_13_priorityWaitingCountsUnreadPriorityRowsAndGivesTheGroupAccent() {
        val cfg = config(GroupAssignment(AssignKind.App, "chat.b", "fam"))
        val v = build(listOf(n("1", "A", "chat.a"), n("2", "B", "chat.b"), n("3", "C", "chat.b", ts = at(9))), cfg)
        assertEquals(2, GroupLogic.priorityWaiting(v, emptySet()))
        assertEquals(1, GroupLogic.priorityWaiting(v, setOf("2")))
        assertEquals(0xFFE91E63, GroupLogic.priorityAccent(v, emptySet()))
        assertNull(GroupLogic.priorityAccent(v, setOf("2", "3")))
    }

    @Test fun STR_13_hideAndSnoozeStillApplyInsideGroups() {
        val cfg = config(GroupAssignment(AssignKind.App, "chat.a", "fam"))
        val hide = listOf(Rule.create(RuleAction.Hide, sender = "Spam"))
        val v = LaunchpadViewBuilder.build(listOf(n("1", "Mum"), n("2", "Spam")), hide, LaunchpadPrefs(), emptyList(), mapOf("1" to now + 60_000), now, zone,
            grouping = GroupResolver(cfg, NoContacts, zone))
        assertTrue(v.sections.isEmpty())
        assertEquals(emptySet<String>(), v.priorityKeys)
    }
}
