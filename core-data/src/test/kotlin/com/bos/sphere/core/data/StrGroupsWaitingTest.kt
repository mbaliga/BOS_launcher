package com.bos.sphere.core.data

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** STR-13 and STR-09: what counts towards the edge indicator with groups, and the Priority accent. */
class StrGroupsWaitingTest {
    private val zone = ZoneOffset.UTC
    private val now = 1_700_000_000_000L
    private fun n(key: String, pkg: String, sender: String? = null) = HubNotification(
        id = key, key = key, source = "x", title = sender ?: key, summary = "t", timestamp = now - 1000, packageName = pkg,
        conversation = sender, sender = sender, isMessaging = sender != null,
    )
    private val cfg = GroupsConfig(
        groups = listOf(
            LaunchpadGroup("fam", "Family", colorArgb = 0xFFE5547A, order = 0, priority = GroupPriority.Priority),
            LaunchpadGroup("quiet", "Promos", order = 1, priority = GroupPriority.Quiet),
        ),
        assignments = listOf(GroupAssignment(AssignKind.App, "demo.shop", "quiet")),
        rules = listOf(GroupRule("r", listOf(Condition(ConditionKind.SenderContains, "mum")), "fam")),
    )
    private val digestPolicies = listOf(AppPolicy("demo.chat", DeliveryMode.Digest, listOf(8 * 60)), AppPolicy("demo.cal", DeliveryMode.SilentInLaunchpad))

    @Test fun STR_13_withoutGroupsTheIndicatorIsExactlyWaitingLogic() {
        val items = listOf(n("a", "demo.chat", "Mum"), n("b", "demo.mail"))
        val old = WaitingLogic.waiting(items, emptyList(), digestPolicies, emptySet(), emptyMap(), now, zone)
        assertEquals(old, GroupWaiting.waiting(items, emptyList(), digestPolicies, emptySet(), emptyMap(), now, zone, null))
        assertEquals(old, GroupWaiting.waiting(items, emptyList(), digestPolicies, emptySet(), emptyMap(), now, zone, GroupResolver(GroupsConfig())))
        assertEquals(PriorityWaiting(0, null, null), GroupWaiting.priority(old, null))
    }

    @Test fun STR_13_aPriorityItemCountsEvenWhenItsAppIsOnADigest() {
        val items = listOf(n("a", "demo.chat", "Mum"), n("b", "demo.chat", "Bob"))
        val w = GroupWaiting.waiting(items, emptyList(), digestPolicies, emptySet(), emptyMap(), now, zone, GroupResolver(cfg, zone = zone))
        assertEquals(listOf("a"), w.map { it.key })
        assertEquals(listOf<String>(), WaitingLogic.waiting(items, emptyList(), digestPolicies, emptySet(), emptyMap(), now, zone).map { it.key })
    }

    @Test fun STR_13_aPriorityItemCountsEvenWhenTheAppIsSilentInLaunchpadOrMuted() {
        val items = listOf(n("a", "demo.cal", "Mum"))
        val muted = listOf(Rule.create(RuleAction.Silent, packageName = "demo.cal"))
        val w = GroupWaiting.waiting(items, muted, digestPolicies, emptySet(), emptyMap(), now, zone, GroupResolver(cfg, zone = zone))
        assertEquals(1, w.size)
    }

    @Test fun STR_13_quietGroupItemsNeverCount() {
        val items = listOf(n("s", "demo.shop"))
        assertTrue(GroupWaiting.waiting(items, emptyList(), emptyList(), emptySet(), emptyMap(), now, zone, GroupResolver(cfg, zone = zone)).isEmpty())
    }

    @Test fun STR_13_readAndSnoozedPriorityItemsDoNotCount() {
        val items = listOf(n("a", "demo.chat", "Mum"), n("b", "demo.chat", "Mum Two"))
        val r = GroupResolver(cfg, zone = zone)
        assertEquals(listOf("b"), GroupWaiting.waiting(items, emptyList(), emptyList(), setOf("a"), emptyMap(), now, zone, r).map { it.key })
        assertEquals(listOf("a"), GroupWaiting.waiting(items, emptyList(), emptyList(), emptySet(), mapOf("b" to now + 5000), now, zone, r).map { it.key })
    }

    @Test fun STR_09_priorityAccentIsTheFirstPriorityGroupWithAWaitingItem() {
        val r = GroupResolver(cfg, zone = zone)
        val w = GroupWaiting.waiting(listOf(n("a", "demo.chat", "Mum"), n("b", "demo.mail")), emptyList(), emptyList(), emptySet(), emptyMap(), now, zone, r)
        val p = GroupWaiting.priority(w, r)
        assertEquals(1, p.count)
        assertEquals(0xFFE5547A, p.accentArgb)
        assertEquals("Family", p.groupName)
        assertFalse(GroupWaiting.priority(w.filter { it.key == "b" }, r).count > 0)
    }

    @Test fun STR_13_rowMenuOffersTheGroupActionsInEveryRoute() {
        val c = RowMenuContext()
        for (e in listOf(LaunchpadRowMenus.entries(c), LaunchpadRowMenus.onLongPress(c), LaunchpadRowMenus.onSecondaryClick(c), LaunchpadRowMenus.onMenuKey(c), LaunchpadRowMenus.palette(c))) {
            assertTrue(RowCommand.MoveToGroup in e && RowCommand.GroupRule in e)
        }
        assertEquals("Move to group...", RowCommand.MoveToGroup.label)
        assertEquals("Make a rule from this", RowCommand.GroupRule.label)
    }
}
