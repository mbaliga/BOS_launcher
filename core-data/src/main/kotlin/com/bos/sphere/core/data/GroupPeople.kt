package com.bos.sphere.core.data

import android.app.NotificationManager
import android.content.Context
import java.util.UUID

/**
 * Wave 24 (STR-13): system Do Not Disturb, read through the public interruption filter (no permission, no policy access).
 * Liftoff only reads it; it never changes it from here.
 */
object DndState {
    /** True for every filter that silences something (priority only, alarms only, none); unknown counts as off. */
    fun isOnFor(filter: Int): Boolean = filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
        filter == NotificationManager.INTERRUPTION_FILTER_ALARMS || filter == NotificationManager.INTERRUPTION_FILTER_NONE

    fun isOn(context: Context): Boolean = runCatching {
        isOnFor((context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).currentInterruptionFilter)
    }.getOrDefault(false)
}

/** Wave 24 (STR-13): whether Priority groups break through Do Not Disturb in Liftoff's own indicator right now. */
object GroupDnd {
    fun breaksThrough(config: GroupsConfig, dndOn: Boolean): Boolean =
        dndOn && config.priorityBreaksDnd && config.groups.any { it.priority == GroupPriority.Priority }
}

/** A person the Groups manager can offer: one human, merged across every messaging app that has a 1:1 chat with them now. */
data class PersonCandidate(
    /** The identity the resolver uses: a stored person id ("p:..."), a contact ("c:...") or a normalised name ("n:..."). */
    val identity: String,
    val name: String,
    /** The raw sender names seen, in feed order. */
    val senders: List<String>,
    /** The app labels their chats come from, in feed order. */
    val apps: List<String>,
    val count: Int,
    /** The group their chats land in now (by hand or by a rule), or null. */
    val currentGroupId: String?,
    val contactKey: String? = null,
)

/** One manual assignment that points at a group, for the members list of the Groups manager. */
data class GroupMember(val kind: AssignKind, val target: String, val label: String)

/**
 * Wave 24 (STR-13, owner request "pick a person and pull their conversations from every messaging app into one group"): the
 * pure half of the Groups manager's people picker. The UI lists [candidates], and a pick is [addToGroup]: a stored person is made
 * (or reused) and a Person assignment points it at the group, which moves that person's 1:1 chats from every app at once.
 */
object GroupPeople {
    fun candidates(feed: List<HubNotification>, resolver: GroupResolver): List<PersonCandidate> {
        data class Acc(val name: String, val senders: LinkedHashSet<String>, val apps: LinkedHashSet<String>, var count: Int, val first: HubNotification, val contact: String?)
        val out = LinkedHashMap<String, Acc>()
        for (n in feed) {
            if (!n.isMessaging) continue
            val f = resolver.facts(n)
            if (f.sender.isBlank() || f.senderKey.isEmpty() || f.isGroupChat) continue
            val id = f.storedPerson?.id ?: f.contact?.let { "c:${it.key}" } ?: "n:${f.senderKey}"
            val acc = out.getOrPut(id) {
                Acc(f.storedPerson?.name ?: f.contact?.displayName ?: PersonNames.clean(f.sender).ifEmpty { f.sender.trim() }, LinkedHashSet(), LinkedHashSet(), 0, n, f.contact?.key)
            }
            acc.senders += f.sender
            acc.apps += n.appLabel.ifBlank { n.packageName }
            acc.count += 1
        }
        return out.map { (id, a) -> PersonCandidate(id, a.name, a.senders.toList(), a.apps.toList(), a.count, resolver.resolve(a.first).group?.id, a.contact) }
    }

    private fun personWithName(c: GroupsConfig, name: String): Person? {
        val key = PersonNames.key(name)
        return c.people.firstOrNull { p -> (p.aliases + p.name).any { PersonNames.key(it) == key } }
    }

    /** Makes sure [candidate] is a stored person (every sender name it was seen under is an alias) and puts it in [groupId]. */
    fun addToGroup(c: GroupsConfig, candidate: PersonCandidate, groupId: String, id: String = "p:" + UUID.randomUUID()): GroupsConfig {
        if (c.group(groupId) == null) return c
        val stored = c.person(candidate.identity)
        var next = c
        val personId = if (stored != null) stored.id else {
            val first = candidate.senders.firstOrNull() ?: candidate.name
            next = GroupsEdit.linkSenders(next, candidate.name, first, candidate.contactKey, id)
            candidate.senders.drop(1).forEach { s -> next = GroupsEdit.linkSenders(next, candidate.name, s, candidate.contactKey) }
            personWithName(next, candidate.name)?.id ?: return c
        }
        return GroupsEdit.assign(next, AssignKind.Person, personId, groupId)
    }

    /**
     * Wave 24 review: "Link another name" on a person who is a member of [groupId]: every sender name of [other] becomes an alias
     * of that stored person (found by id, not by its label), and the person stays in [groupId], the group the link was made
     * from, even when [other] already had its own assignment elsewhere (a plain merge keeps whichever assignment came first).
     */
    fun linkInto(c: GroupsConfig, personId: String, other: PersonCandidate, groupId: String): GroupsConfig {
        val p = c.person(personId) ?: return c
        if (c.group(groupId) == null || other.identity == personId) return c
        var next = c
        val names = other.senders.ifEmpty { listOf(other.name) }
        names.forEach { s -> next = GroupsEdit.linkSenders(next, p.name, s, p.contactKey ?: other.contactKey) }
        if (next.person(personId) == null) return c
        return GroupsEdit.assign(next, AssignKind.Person, personId, groupId)
    }

    /** A typed name (someone with nothing in the feed right now): a stored person with that name, put in [groupId]. */
    fun addNameToGroup(c: GroupsConfig, name: String, groupId: String, id: String = "p:" + UUID.randomUUID()): GroupsConfig {
        if (PersonNames.key(name).isEmpty() || c.group(groupId) == null) return c
        val existing = personWithName(c, name)
        val next = if (existing != null) c else GroupsEdit.linkSenders(c, name, name, null, id)
        val pid = personWithName(next, name)?.id ?: return c
        return GroupsEdit.assign(next, AssignKind.Person, pid, groupId)
    }

    /** The manual assignments that point at [groupId], people first, then apps, then single chats, with readable labels. */
    fun members(c: GroupsConfig, groupId: String, feed: List<HubNotification>): List<GroupMember> {
        val mine = c.assignments.filter { it.groupId == groupId }
        fun label(a: GroupAssignment): String = when (a.kind) {
            AssignKind.Person -> c.person(a.target)?.name ?: a.target.substringAfter(':')
            AssignKind.App -> feed.firstOrNull { AssignTargets.app(it.packageName) == a.target && it.appLabel.isNotBlank() }?.appLabel ?: a.target
            AssignKind.Conversation -> feed.firstOrNull { n ->
                val t = n.conversation?.takeIf { it.isNotBlank() } ?: LaunchpadLogic.senderOf(n).ifBlank { n.title }
                AssignTargets.conversation(n.packageName, t) == a.target
            }?.let { it.conversation?.takeIf { s -> s.isNotBlank() } ?: LaunchpadLogic.senderOf(it) } ?: a.target.substringAfter('|')
        }
        return listOf(AssignKind.Person, AssignKind.App, AssignKind.Conversation).flatMap { k ->
            mine.filter { it.kind == k }.map { GroupMember(it.kind, it.target, label(it)) }
        }
    }
}
