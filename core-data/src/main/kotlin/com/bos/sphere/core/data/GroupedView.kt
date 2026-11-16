package com.bos.sphere.core.data

import java.time.ZoneId

/** One section of the Launchpad column made from a group ([group] null: the built-in section for everything else). */
data class GroupedSection(
    val key: String,
    val group: LaunchpadGroup?,
    /** The by-app / by-conversation sections inside it (the old grouping stays inside a section). Keys are unique across groups. */
    val sections: List<Section>,
    /** Quiet groups start folded. */
    val folded: Boolean,
) {
    val count: Int get() = sections.sumOf { it.items.size }
    val isFallback: Boolean get() = group == null
    companion object { const val FALLBACK_KEY = "group:_" }
}

/**
 * STR-13: builds [LaunchpadView] when the user has groups. The per-app rules of STR-04 stay in force, except that a Priority
 * group's notifications bypass the per-app digest and the silent-in-Launchpad rules and show at once (Liftoff surfaces only).
 * Order: Priority groups, Normal groups, the built-in section, Quiet groups (folded).
 */
object GroupedViewBuilder {
    fun build(
        items: List<HubNotification>, rules: List<Rule>, prefs: LaunchpadPrefs, policies: List<AppPolicy>,
        snoozedUntil: Map<String, Long>, now: Long, zone: ZoneId, digestLabel: String, todayLabel: String,
        yesterdayLabel: String, pinnedLabel: String, flatLabel: String, grouping: GroupResolver,
    ): LaunchpadView {
        val awake = items.filter { !SnoozeLogic.isSnoozed(it.key, snoozedUntil, now) }
        val res = grouping.resolveAll(awake)
        fun groupOf(n: HubNotification) = res[n.key]?.group
        fun policyOf(n: HubNotification): AppPolicy? = GroupLogic.effectivePolicy(n.packageName, groupOf(n), policies)

        val held = awake.filter { DeliveryLogic.isHeld(it, listOfNotNull(policyOf(it)), now, zone) }
        val heldKeys = held.map { it.key }.toSet()
        // The digest section is the app-level digest of the built-in section; grouped notifications land in their group.
        val digestReleased = LaunchpadLogic.visible(
            awake.filter { n ->
                n.key !in heldKeys && groupOf(n) == null && !n.isOngoing && !LaunchpadLogic.isPinned(n, rules) &&
                    DeliveryLogic.modeFor(n.packageName, policies) == DeliveryMode.Digest
            },
            rules, now,
        ).sortedByDescending { it.timestamp }
        val digestKeys = digestReleased.map { it.key }.toSet()
        val rest = awake.filter { it.key !in heldKeys && it.key !in digestKeys }

        fun apply(list: List<HubNotification>) =
            LaunchpadLogic.apply(list, rules, prefs, now, zone, LaunchpadLogic::defaultDayLabel, todayLabel, yesterdayLabel, pinnedLabel, flatLabel)

        val byGroup = rest.groupBy { groupOf(it)?.id }
        val out = ArrayList<GroupedSection>()
        val groupOfKey = HashMap<String, String>()
        val priorityKeys = HashSet<String>()
        fun groupSection(g: LaunchpadGroup): GroupedSection? {
            val list = byGroup[g.id] ?: return null
            val secs = apply(list).map { it.copy(key = "g:${g.id}/${it.key}") }
            if (secs.isEmpty()) return null
            secs.forEach { s -> s.items.forEach { groupOfKey[it.key] = g.id; if (g.priority == GroupPriority.Priority) priorityKeys += it.key } }
            return GroupedSection("group:${g.id}", g, secs, folded = g.priority == GroupPriority.Quiet)
        }
        fun fallbackSection(): GroupedSection? {
            val base = apply(byGroup[null].orEmpty())
            val secs = ArrayList<Section>()
            val pinnedFirst = base.firstOrNull()?.takeIf { it.pinned }
            if (pinnedFirst != null) secs += pinnedFirst
            if (digestReleased.isNotEmpty()) secs += Section(digestLabel, LaunchpadView.DIGEST_KEY, digestReleased)
            secs += base.filter { it !== pinnedFirst }
            return if (secs.isEmpty()) null else GroupedSection(GroupedSection.FALLBACK_KEY, null, secs, folded = false)
        }
        val ordered = grouping.config.ordered
        ordered.filter { it.priority == GroupPriority.Priority }.forEach { groupSection(it)?.let(out::add) }
        ordered.filter { it.priority == GroupPriority.Normal }.forEach { groupSection(it)?.let(out::add) }
        fallbackSection()?.let(out::add)
        ordered.filter { it.priority == GroupPriority.Quiet }.forEach { groupSection(it)?.let(out::add) }

        val next = held.mapNotNull { n ->
            policyOf(n)?.let { DeliveryLogic.nextRelease(now, zone, it.digestMinutes) }
        }.minOrNull()
        val silent = awake.filter { n ->
            val g = groupOf(n)
            DeliveryLogic.isSilentHere(n, listOfNotNull(policyOf(n))) ||
                (g?.priority != GroupPriority.Priority && LaunchpadLogic.isSilent(n, rules))
        }.map { it.key }.toSet()
        return LaunchpadView(out.flatMap { it.sections }, held, next, silent, out, priorityKeys, groupOfKey)
    }
}

object GroupLogic {
    /**
     * The delivery policy that applies to [pkg] inside [group]. A Priority group's override (default: show immediately)
     * sits above the app's own policy; a Normal or Quiet group's override is only the default for an app with no policy.
     */
    fun effectivePolicy(pkg: String, group: LaunchpadGroup?, policies: List<AppPolicy>): AppPolicy? {
        val own = DeliveryLogic.policyFor(pkg, policies)
        if (group == null) return own
        if (group.priority == GroupPriority.Priority) {
            val mode = if (group.delivery == DeliveryMode.SilentInLaunchpad) DeliveryMode.SilentInLaunchpad else DeliveryMode.Immediate
            return (own ?: AppPolicy(pkg)).copy(mode = mode)
        }
        return own ?: group.delivery?.let { AppPolicy(pkg, it) }
    }

    /** Priority notifications that are still unread: what may raise the handle / waiting indicator with an accent. */
    fun priorityWaiting(view: LaunchpadView, readKeys: Set<String>): Int = view.priorityKeys.count { it !in readKeys }

    /** The accent colour for the indicator: the colour of the first Priority group that has an unread item, else null. */
    fun priorityAccent(view: LaunchpadView, readKeys: Set<String>): Long? =
        view.grouped.firstOrNull { gs ->
            gs.group?.priority == GroupPriority.Priority && gs.sections.any { s -> s.items.any { it.key !in readKeys } }
        }?.group?.colorArgb
}

/** What the left-edge indicator (STR-09) shows about Priority groups. */
data class PriorityWaiting(val count: Int, val accentArgb: Long?, val groupName: String?)

object GroupWaiting {
    /**
     * Unread items that count towards the indicator, group-aware: a Priority group's items count even when the app is on a digest
     * or silent in Launchpad, and a Quiet group's items never count (the group is folded). Without groups it is [WaitingLogic.waiting].
     */
    fun waiting(
        items: List<HubNotification>, rules: List<Rule>, policies: List<AppPolicy>, readKeys: Set<String>,
        snoozedUntil: Map<String, Long>, now: Long, zone: ZoneId, grouping: GroupResolver?,
    ): List<HubNotification> {
        if (grouping == null || grouping.config.groups.isEmpty()) return WaitingLogic.waiting(items, rules, policies, readKeys, snoozedUntil, now, zone)
        return LaunchpadLogic.visible(items, rules, now).filter { n ->
            val g = grouping.resolve(n).group
            val pol = listOfNotNull(GroupLogic.effectivePolicy(n.packageName, g, policies))
            g?.priority != GroupPriority.Quiet && !n.isOngoing && n.key !in readKeys &&
                (g?.priority == GroupPriority.Priority || !LaunchpadLogic.isSilent(n, rules)) &&
                !DeliveryLogic.isSilentHere(n, pol) && !DeliveryLogic.isHeld(n, pol, now, zone) &&
                !SnoozeLogic.isSnoozed(n.key, snoozedUntil, now)
        }
    }

    /** How many of [waiting] sit in Priority groups, and the colour of the first such group (in display order) that has one. */
    fun priority(waiting: List<HubNotification>, grouping: GroupResolver?): PriorityWaiting {
        if (grouping == null || grouping.config.groups.isEmpty()) return PriorityWaiting(0, null, null)
        val byGroup = waiting.mapNotNull { grouping.resolve(it).group }.filter { it.priority == GroupPriority.Priority }
        if (byGroup.isEmpty()) return PriorityWaiting(0, null, null)
        val first = grouping.config.ordered.first { g -> byGroup.any { it.id == g.id } }
        return PriorityWaiting(byGroup.size, first.colorArgb, first.name)
    }
}
