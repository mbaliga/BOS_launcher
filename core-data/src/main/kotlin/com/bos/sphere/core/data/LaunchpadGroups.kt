package com.bos.sphere.core.data

import java.util.Locale
import java.util.UUID

// STR-13: Launchpad groups. User-defined named groups become the sections of the Launchpad column; a group can hold
// apps, conversations and people (one human across apps), and ordered rules assign the rest. Everything here only changes
// what Liftoff's own surfaces show (as STR-04 does); system notifications and Do Not Disturb are untouched.

enum class GroupPriority { Priority, Normal, Quiet }

data class LaunchpadGroup(
    val id: String,
    val name: String,
    /** A short icon name the UI maps to a glyph; blank for none. */
    val icon: String = "",
    val colorArgb: Long? = null,
    val order: Int = 0,
    val priority: GroupPriority = GroupPriority.Normal,
    /**
     * Optional delivery override. Priority groups: Immediate (the default) or SilentInLaunchpad win over the app's own
     * policy (a Digest value is read as Immediate). Normal and Quiet groups: used only for apps that have no policy of their own.
     */
    val delivery: DeliveryMode? = null,
)

/** One human, merged across apps. Aliases are sender names (any app); [contactKey] links to a contact when the grant exists. */
data class Person(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val contactKey: String? = null,
)

enum class AssignKind { Person, App, Conversation }

/** A manual assignment. [groupId] null means "no group": the row stays in the built-in section and no rule may move it. */
data class GroupAssignment(val kind: AssignKind, val target: String, val groupId: String?)

enum class ConditionKind {
    AppIs, PersonIs, SenderContains, SenderStartsWith, SenderEquals, ConversationContains, TextContains, CategoryIs,
    IsConversation, LastNameIsMine, InContacts, StarredContact, TimeWindow,
}

/**
 * One condition of a rule. [value]: package name (AppIs), person id (PersonIs), the text to look for, or the category
 * ("message", "call", "reminder"). TimeWindow uses [from] and [to] in minutes after midnight, local time of the posting
 * (a window that ends before it starts wraps midnight; equal ends mean the whole day). [negate] inverts the result, except
 * where the condition cannot be evaluated at all (no contacts grant, no last name set): then it never matches.
 */
data class Condition(
    val kind: ConditionKind,
    val value: String = "",
    val from: Int = 0,
    val to: Int = 0,
    val negate: Boolean = false,
) {
    /** Conditions that can only be answered with the contacts grant. */
    val needsContacts: Boolean get() = kind == ConditionKind.InContacts || kind == ConditionKind.StarredContact
}

/** All conditions AND; an enabled rule with no conditions matches nothing. Rules are ordered, the first match wins. */
data class GroupRule(
    val id: String,
    val conditions: List<Condition>,
    val groupId: String,
    val enabled: Boolean = true,
    val name: String = "",
)

data class GroupsConfig(
    val groups: List<LaunchpadGroup> = emptyList(),
    val people: List<Person> = emptyList(),
    val assignments: List<GroupAssignment> = emptyList(),
    val rules: List<GroupRule> = emptyList(),
    /** The user's own last name (private; typed, or prefilled from the owner profile when the contacts grant exists). */
    val myLastName: String = "",
    /** Fields of the stored object this build does not know (JSON text), kept for a round trip. */
    val extra: String = "{}",
) {
    val isEmpty: Boolean get() = groups.isEmpty() && people.isEmpty() && assignments.isEmpty() && rules.isEmpty() && myLastName.isBlank()
    fun group(id: String?): LaunchpadGroup? = id?.let { i -> groups.firstOrNull { it.id == i } }
    fun person(id: String): Person? = people.firstOrNull { it.id == id }

    /** Priority groups first, then Normal, then Quiet; each by [LaunchpadGroup.order] (ties by name). */
    val ordered: List<LaunchpadGroup>
        get() = groups.sortedWith(compareBy<LaunchpadGroup> { it.priority.ordinal }.thenBy { it.order }.thenBy { it.name.lowercase() })

    companion object {
        const val MAX_GROUPS = 50
        const val MAX_RULES = 200
        const val MAX_PEOPLE = 500
        const val MAX_ASSIGNMENTS = 2000
        const val MAX_NAME = 40
    }
}

object AssignTargets {
    fun conversation(pkg: String, title: String): String = pkg.lowercase(Locale.ROOT) + "|" + title.trim().lowercase(Locale.ROOT)
    fun app(pkg: String): String = pkg.lowercase(Locale.ROOT)
}

/** Pure edits on a [GroupsConfig]; the store applies them and the UI never builds a config by hand. */
object GroupsEdit {
    private fun cleanName(s: String) = s.trim().replace(Regex("\\s+"), " ").take(GroupsConfig.MAX_NAME)

    private fun renumber(list: List<LaunchpadGroup>) = list.mapIndexed { i, g -> g.copy(order = i) }

    fun addGroup(
        c: GroupsConfig, name: String, priority: GroupPriority = GroupPriority.Normal, icon: String = "",
        colorArgb: Long? = null, id: String = UUID.randomUUID().toString(),
    ): GroupsConfig {
        val n = cleanName(name)
        if (n.isEmpty() || c.groups.size >= GroupsConfig.MAX_GROUPS || c.groups.any { it.id == id }) return c
        return c.copy(groups = renumber(c.groups + LaunchpadGroup(id, n, icon, colorArgb, c.groups.size, priority)))
    }

    private fun update(c: GroupsConfig, id: String, f: (LaunchpadGroup) -> LaunchpadGroup) =
        c.copy(groups = c.groups.map { if (it.id == id) f(it) else it })

    fun rename(c: GroupsConfig, id: String, name: String): GroupsConfig {
        val n = cleanName(name)
        return if (n.isEmpty()) c else update(c, id) { it.copy(name = n) }
    }

    fun setPriority(c: GroupsConfig, id: String, p: GroupPriority) = update(c, id) { it.copy(priority = p) }
    fun setDelivery(c: GroupsConfig, id: String, d: DeliveryMode?) = update(c, id) { it.copy(delivery = d) }
    fun setAppearance(c: GroupsConfig, id: String, icon: String, colorArgb: Long?) = update(c, id) { it.copy(icon = icon, colorArgb = colorArgb) }

    /** Moves a group one place up (-1) or down (+1) within the stored order (the keyboard path for reordering). */
    fun move(c: GroupsConfig, id: String, delta: Int): GroupsConfig {
        val list = c.groups.sortedBy { it.order }.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return c
        val g = list.removeAt(i)
        list.add(j, g)
        return c.copy(groups = renumber(list))
    }

    /** Removes the group, the assignments that point at it and the rules that target it. */
    fun delete(c: GroupsConfig, id: String): GroupsConfig = c.copy(
        groups = renumber(c.groups.filterNot { it.id == id }.sortedBy { it.order }),
        assignments = c.assignments.filterNot { it.groupId == id },
        rules = c.rules.filterNot { it.groupId == id },
    )

    fun assign(c: GroupsConfig, kind: AssignKind, target: String, groupId: String?): GroupsConfig {
        if (target.isBlank() || (groupId != null && c.group(groupId) == null)) return c
        val kept = c.assignments.filterNot { it.kind == kind && it.target == target }
        if (kept.size >= GroupsConfig.MAX_ASSIGNMENTS) return c
        return c.copy(assignments = kept + GroupAssignment(kind, target, groupId))
    }

    fun unassign(c: GroupsConfig, kind: AssignKind, target: String) =
        c.copy(assignments = c.assignments.filterNot { it.kind == kind && it.target == target })

    fun addRule(c: GroupsConfig, rule: GroupRule): GroupsConfig {
        if (c.group(rule.groupId) == null || c.rules.size >= GroupsConfig.MAX_RULES || c.rules.any { it.id == rule.id }) return c
        return c.copy(rules = c.rules + rule)
    }

    fun updateRule(c: GroupsConfig, rule: GroupRule): GroupsConfig =
        if (c.group(rule.groupId) == null) c else c.copy(rules = c.rules.map { if (it.id == rule.id) rule else it })

    fun removeRule(c: GroupsConfig, id: String) = c.copy(rules = c.rules.filterNot { it.id == id })

    fun moveRule(c: GroupsConfig, id: String, delta: Int): GroupsConfig {
        val list = c.rules.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return c
        val r = list.removeAt(i)
        list.add(j, r)
        return c.copy(rules = list)
    }

    fun setMyLastName(c: GroupsConfig, name: String) = c.copy(myLastName = name.trim().take(60))

    /**
     * "This is the same person as...": joins two sender names (or a contact) into one stored [Person]. Assignments made for
     * either name (or for a merged person) move onto the person; the first assignment wins on a clash.
     */
    fun linkSenders(c: GroupsConfig, nameA: String, nameB: String, contactKey: String? = null, id: String = "p:" + UUID.randomUUID()): GroupsConfig {
        val ka = PersonNames.key(nameA)
        val kb = PersonNames.key(nameB)
        if (ka.isEmpty() || kb.isEmpty() || c.people.size >= GroupsConfig.MAX_PEOPLE) return c
        fun owner(k: String) = c.people.firstOrNull { p -> (p.aliases + p.name).any { PersonNames.key(it) == k } }
        val pa = owner(ka)
        val pb = owner(kb)
        if (pa != null && pa == pb) return c
        val merged = Person(
            id = pa?.id ?: pb?.id ?: id,
            name = pa?.name ?: pb?.name ?: PersonNames.clean(nameA).ifEmpty { nameA.trim() },
            aliases = ((pa?.aliases.orEmpty() + pb?.aliases.orEmpty() + PersonNames.clean(nameA) + PersonNames.clean(nameB)).filter { it.isNotBlank() })
                .distinctBy { PersonNames.key(it) },
            contactKey = contactKey ?: pa?.contactKey ?: pb?.contactKey,
        )
        val oldIds = buildSet {
            add("n:$ka"); add("n:$kb")
            listOfNotNull(pa, pb).forEach { add(it.id); it.aliases.forEach { a -> add("n:" + PersonNames.key(a)) } }
            remove(merged.id)
        }
        val moved = LinkedHashMap<String, GroupAssignment>()
        for (a in c.assignments) {
            val isPerson = a.kind == AssignKind.Person
            val t = if (isPerson && a.target in oldIds) merged.id else a.target
            moved.putIfAbsent((if (isPerson) "P" else a.kind.name) + "\u0000" + t, a.copy(target = t))
        }
        val others = c.people.filter { it.id != pa?.id && it.id != pb?.id }
        return c.copy(people = others + merged, assignments = moved.values.toList(),
            rules = c.rules.map { r -> r.copy(conditions = r.conditions.map { x -> if (x.kind == ConditionKind.PersonIs && x.value in oldIds) x.copy(value = merged.id) else x }) })
    }

    /** Takes [alias] (a sender name) out of person [personId]; a person left with no alias is removed with its assignments and the rules that required it. */
    fun unlinkAlias(c: GroupsConfig, personId: String, alias: String): GroupsConfig {
        val k = PersonNames.key(alias)
        val p = c.person(personId) ?: return c
        val rest = p.aliases.filter { PersonNames.key(it) != k }
        return if (rest.size <= 1 && p.contactKey == null) removePerson(c, personId)
        else c.copy(people = c.people.map { if (it.id == personId) it.copy(aliases = rest) else it })
    }

    fun removePerson(c: GroupsConfig, personId: String) = c.copy(
        people = c.people.filterNot { it.id == personId },
        assignments = c.assignments.filterNot { it.kind == AssignKind.Person && it.target == personId },
        // A rule that required this person can never match again, so it goes; keeping it without that condition would let it
        // match everyone who fits its other conditions ("Priya, on weekdays" would become "anyone, on weekdays"). A negated
        // condition ("not this person") has nothing left to exclude and is simply dropped.
        rules = c.rules.filterNot { r -> r.conditions.any { it.kind == ConditionKind.PersonIs && it.value == personId && !it.negate } }
            .map { r -> r.copy(conditions = r.conditions.filterNot { it.kind == ConditionKind.PersonIs && it.value == personId }) }
            .filter { it.conditions.isNotEmpty() },
    )
}

/** "Make a rule from this": a starting rule for the editor, from a notification. */
object RuleDrafts {
    fun fromNotification(n: HubNotification, groupId: String, id: String = UUID.randomUUID().toString()): GroupRule {
        val sender = LaunchpadLogic.senderOf(n)
        val conds = ArrayList<Condition>()
        if (n.isMessaging && sender.isNotBlank()) {
            conds += Condition(ConditionKind.SenderEquals, PersonNames.clean(sender).ifEmpty { sender })
        } else if (n.packageName.isNotBlank()) {
            conds += Condition(ConditionKind.AppIs, n.packageName)
        }
        if (conds.isEmpty()) conds += Condition(ConditionKind.TextContains, n.title.take(40))
        return GroupRule(id, conds, groupId)
    }
}
