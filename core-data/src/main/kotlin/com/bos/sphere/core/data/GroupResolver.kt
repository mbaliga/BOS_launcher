package com.bos.sphere.core.data

import java.time.Instant
import java.time.ZoneId

// STR-13: who a notification is from (across apps) and which group it belongs to. Deterministic: the same inputs give the
// same answer. Precedence: a manual assignment (conversation, then person, then app) always beats rules; rules are
// ordered, all conditions AND, the first match wins; anything else stays in the built-in section.

/** A contact as far as matching is concerned. Built from ContactsContract when the grant exists; never required. */
data class ContactIdentity(
    val key: String,
    val displayName: String,
    /** Display, alternative, nick and structured names: any of them identifies this contact. */
    val names: Set<String> = emptySet(),
    val phones: Set<String> = emptySet(),
    val emails: Set<String> = emptySet(),
    val starred: Boolean = false,
)

interface ContactDirectory {
    /** False when there is no grant (or no contacts): contact conditions then never match. */
    val available: Boolean
    fun find(senderRaw: String, senderKey: String): ContactIdentity?
}

object NoContacts : ContactDirectory {
    override val available: Boolean = false
    override fun find(senderRaw: String, senderKey: String): ContactIdentity? = null
}

class ContactIndex(contacts: List<ContactIdentity>) : ContactDirectory {
    private val byName = HashMap<String, ContactIdentity>()
    private val byPhone = HashMap<String, ContactIdentity>()
    private val byEmail = HashMap<String, ContactIdentity>()
    override val available: Boolean = contacts.isNotEmpty()

    init {
        // Sorted by key so that two contacts sharing a name always resolve the same way.
        for (c in contacts.sortedBy { it.key }) {
            (c.names + c.displayName).forEach { n -> PersonNames.key(n).takeIf { it.isNotEmpty() }?.let { byName.putIfAbsent(it, c) } }
            c.phones.forEach { p -> phoneKey(p)?.let { byPhone.putIfAbsent(it, c) } }
            c.emails.forEach { e -> byEmail.putIfAbsent(e.trim().lowercase(), c) }
        }
    }

    override fun find(senderRaw: String, senderKey: String): ContactIdentity? {
        byName[senderKey]?.let { return it }
        val raw = senderRaw.trim()
        if (raw.contains('@')) byEmail[raw.lowercase()]?.let { return it }
        phoneKey(raw)?.let { k -> byPhone[k]?.let { return it } }
        return null
    }

    companion object {
        /** The last nine digits of a number written like a phone number, so "+1 (555) 123-4567" and "5551234567" agree. */
        fun phoneKey(s: String): String? {
            val t = s.trim()
            if (t.isEmpty() || t.any { it.isLetter() }) return null
            val d = ContactLogic.digits(t)
            return if (d.length >= 7) d.takeLast(9) else null
        }
    }
}

enum class GroupSource { None, Manual, Rule }

data class GroupResolution(
    val group: LaunchpadGroup?,
    val source: GroupSource,
    val ruleId: String? = null,
    val assignKind: AssignKind? = null,
) {
    companion object { val NONE = GroupResolution(null, GroupSource.None) }
}

data class RulePreview(val count: Int, val names: List<String>, val keys: List<String>)

class GroupResolver(
    val config: GroupsConfig,
    private val contacts: ContactDirectory = NoContacts,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val keyCache = HashMap<String, String>()
    private val groupsById = config.groups.associateBy { it.id }
    private val aliasOwner: Map<String, Person> = HashMap<String, Person>().also { m ->
        for (p in config.people) (p.aliases + p.name).forEach { a -> PersonNames.key(a).takeIf { it.isNotEmpty() }?.let { m.putIfAbsent(it, p) } }
    }
    private val appAssign = config.assignments.filter { it.kind == AssignKind.App }.associateBy { it.target }
    private val convAssign = config.assignments.filter { it.kind == AssignKind.Conversation }.associateBy { it.target }
    private val personAssign = config.assignments.filter { it.kind == AssignKind.Person }.associateBy { it.target }
    private val myForms: Set<String> = LastNames.mineForms(config.myLastName)
    private val compiled: List<Pair<GroupRule, List<(Facts) -> Boolean>>> =
        config.rules.filter { it.enabled && it.conditions.isNotEmpty() && groupsById.containsKey(it.groupId) }
            .map { r -> r to r.conditions.map(::compile) }

    private fun keyOf(s: String) = keyCache.getOrPut(s) { PersonNames.key(s) }

    /** What is known about one notification, computed lazily and once. */
    inner class Facts(val n: HubNotification) {
        val sender: String = LaunchpadLogic.senderOf(n)
        val senderKey: String by lazy { keyOf(sender) }
        val contact: ContactIdentity? by lazy { if (contacts.available && sender.isNotBlank()) contacts.find(sender, senderKey) else null }
        val storedPerson: Person? by lazy {
            aliasOwner[senderKey] ?: contact?.let { c -> config.people.firstOrNull { it.contactKey == c.key } }
        }
        /** Every identity this sender has, most specific first. */
        val personIds: List<String> by lazy {
            buildList {
                storedPerson?.let { p ->
                    add(p.id)
                    p.contactKey?.let { add("c:$it") }
                    p.aliases.forEach { a -> keyOf(a).takeIf { it.isNotEmpty() }?.let { add("n:$it") } }
                }
                contact?.let { add("c:${it.key}") }
                if (senderKey.isNotEmpty()) add("n:$senderKey")
            }.distinct()
        }
        val convTitle: String by lazy { n.conversation?.takeIf { it.isNotBlank() } ?: sender }
        val convFolded: String by lazy { PersonNames.normalise(convTitle) }
        val senderFolded: String by lazy { PersonNames.normalise(sender) }
        /** A group chat: a messaging notification whose conversation is not the sender's own 1:1 chat. */
        val isGroupChat: Boolean by lazy { n.isMessaging && !n.conversation.isNullOrBlank() && keyOf(n.conversation!!) != senderKey }
        val lastNames: Set<String> by lazy { LastNames.candidates(sender) }
    }

    fun facts(n: HubNotification) = Facts(n)

    // ---- conditions ---------------------------------------------------------------------

    private fun compile(c: Condition): (Facts) -> Boolean {
        val folded = PersonNames.normalise(c.value)
        val test: (Facts) -> Boolean? = when (c.kind) {
            ConditionKind.AppIs -> { f -> c.value.isNotBlank() && c.value.equals(f.n.packageName, ignoreCase = true) }
            ConditionKind.PersonIs -> { f -> c.value.isNotBlank() && c.value in f.personIds }
            ConditionKind.SenderContains -> { f -> folded.isNotEmpty() && f.senderFolded.contains(folded) }
            ConditionKind.SenderStartsWith -> { f -> folded.isNotEmpty() && f.senderFolded.startsWith(folded) }
            ConditionKind.SenderEquals -> { f -> folded.isNotEmpty() && (f.senderFolded == folded || f.senderKey == keyOf(c.value)) }
            ConditionKind.ConversationContains -> { f -> folded.isNotEmpty() && f.convFolded.contains(folded) }
            ConditionKind.TextContains -> { f ->
                c.value.isNotBlank() && (f.n.title.contains(c.value, ignoreCase = true) || f.n.summary.contains(c.value, ignoreCase = true))
            }
            ConditionKind.CategoryIs -> { f -> categoryOf(f.n, c.value) }
            ConditionKind.IsConversation -> { f -> f.n.isMessaging || f.n.style.equals("messaging", true) }
            ConditionKind.LastNameIsMine -> { f -> if (myForms.isEmpty()) null else f.lastNames.any { it in myForms } }
            ConditionKind.InContacts -> { f -> if (!contacts.available) null else f.contact != null }
            ConditionKind.StarredContact -> { f -> if (!contacts.available) null else f.contact?.starred == true }
            ConditionKind.TimeWindow -> { f -> inWindow(f.n.timestamp, c.from, c.to) }
        }
        // An unevaluable condition (no grant, no last name) never matches, negated or not.
        return { f -> test(f)?.let { it != c.negate } ?: false }
    }

    private fun categoryOf(n: HubNotification, kind: String): Boolean {
        val cat = n.category?.lowercase()
        return when (kind.lowercase()) {
            "message" -> n.isMessaging || cat == "msg" || n.style.equals("messaging", true)
            "call" -> cat == "call" || n.style.equals("call", true)
            "reminder" -> cat == "reminder" || cat == "alarm" || cat == "event"
            else -> false
        }
    }

    private fun inWindow(timestamp: Long, from: Int, to: Int): Boolean {
        val t = Instant.ofEpochMilli(timestamp).atZone(zone)
        val m = t.hour * 60 + t.minute
        val a = from.coerceIn(0, 1439)
        val b = to.coerceIn(0, 1439)
        return when {
            a == b -> true
            a < b -> m in a until b
            else -> m >= a || m < b
        }
    }

    fun conditionMatches(c: Condition, n: HubNotification): Boolean = compile(c)(Facts(n))

    // ---- resolution ---------------------------------------------------------------------

    private fun resolved(a: GroupAssignment?, kind: AssignKind): GroupResolution? {
        a ?: return null
        if (a.groupId == null) return GroupResolution(null, GroupSource.Manual, assignKind = kind)
        val g = groupsById[a.groupId] ?: return null
        return GroupResolution(g, GroupSource.Manual, assignKind = kind)
    }

    private fun manual(f: Facts): GroupResolution? {
        if (convAssign.isNotEmpty()) resolved(convAssign[AssignTargets.conversation(f.n.packageName, f.convTitle)], AssignKind.Conversation)?.let { return it }
        if (personAssign.isNotEmpty() && !f.isGroupChat) {
            for (id in f.personIds) resolved(personAssign[id], AssignKind.Person)?.let { return it }
        }
        if (appAssign.isNotEmpty()) resolved(appAssign[AssignTargets.app(f.n.packageName)], AssignKind.App)?.let { return it }
        return null
    }

    private fun byRules(f: Facts, rules: List<Pair<GroupRule, List<(Facts) -> Boolean>>>): GroupResolution? {
        for ((r, tests) in rules) if (tests.all { it(f) }) return GroupResolution(groupsById[r.groupId], GroupSource.Rule, r.id)
        return null
    }

    fun resolve(n: HubNotification): GroupResolution {
        if (config.groups.isEmpty()) return GroupResolution.NONE
        val f = Facts(n)
        return manual(f) ?: byRules(f, compiled) ?: GroupResolution.NONE
    }

    /** Resolves a whole feed in one pass; the map is keyed by notification key. */
    fun resolveAll(list: List<HubNotification>): Map<String, GroupResolution> {
        val out = HashMap<String, GroupResolution>(list.size * 2)
        for (n in list) out[n.key] = resolve(n)
        return out
    }

    // ---- previews ----------------------------------------------------------------------

    /** How many of [list] the conditions match (ignoring manual assignments and other rules), with the first few sender names. */
    fun preview(conditions: List<Condition>, list: List<HubNotification>, limit: Int = 3): RulePreview {
        if (conditions.isEmpty()) return RulePreview(0, emptyList(), emptyList())
        val tests = conditions.map(::compile)
        val hits = list.filter { n -> val f = Facts(n); tests.all { it(f) } }
        return RulePreview(hits.size, hits.map { LaunchpadLogic.senderOf(it) }.distinct().take(limit), hits.map { it.key })
    }

    /**
     * Which of [list] the rule would actually win: matches, minus those a manual assignment or an earlier rule already
     * decides. [rule] replaces the stored rule with the same id, or is appended when new.
     */
    fun previewWinning(rule: GroupRule, list: List<HubNotification>, limit: Int = 3): RulePreview {
        val rules = if (config.rules.any { it.id == rule.id }) config.rules.map { if (it.id == rule.id) rule else it } else config.rules + rule
        val r2 = GroupResolver(config.copy(rules = rules.map { if (it.id == rule.id) it.copy(enabled = true) else it }), contacts, zone)
        val hits = list.filter { r2.resolve(it).ruleId == rule.id }
        return RulePreview(hits.size, hits.map { LaunchpadLogic.senderOf(it) }.distinct().take(limit), hits.map { it.key })
    }

    /** The sample-sender test field: would [rule]'s conditions match a notification from [sender]? */
    fun testSample(conditions: List<Condition>, sender: String, packageName: String = "", conversation: String? = null, now: Long = System.currentTimeMillis()): Boolean {
        if (conditions.isEmpty()) return false
        val n = sampleNotification(sender, packageName, conversation, now)
        val f = Facts(n)
        return conditions.map(::compile).all { it(f) }
    }

    fun sampleNotification(sender: String, packageName: String = "", conversation: String? = null, now: Long = System.currentTimeMillis()) = HubNotification(
        id = "sample", key = "sample", source = packageName, title = sender, summary = "", timestamp = now,
        packageName = packageName, sender = sender, conversation = conversation, isMessaging = true, style = "messaging",
    )
}

/** Plain-language summary of a rule, in English (the UI maps the pieces to the user's language). */
object GroupSummaries {
    fun clause(c: Condition, appLabel: (String) -> String = { it }, personName: (String) -> String = { it }, myLastName: String = ""): String {
        val v = c.value
        val q = "“$v”"
        val neg = c.negate
        return when (c.kind) {
            ConditionKind.AppIs -> (if (neg) "not in " else "in ") + appLabel(v)
            ConditionKind.PersonIs -> (if (neg) "who is not " else "who is ") + personName(v)
            ConditionKind.SenderContains -> (if (neg) "whose name does not contain " else "whose name contains ") + q
            ConditionKind.SenderStartsWith -> (if (neg) "whose name does not start with " else "whose name starts with ") + q
            ConditionKind.SenderEquals -> (if (neg) "not named " else "named ") + q
            ConditionKind.ConversationContains -> (if (neg) "in chats whose title does not contain " else "in chats whose title contains ") + q
            ConditionKind.TextContains -> (if (neg) "whose text does not contain " else "whose text contains ") + q
            ConditionKind.CategoryIs -> (if (neg) "that is not a " else "that is a ") + v
            ConditionKind.IsConversation -> if (neg) "outside a conversation" else "in a conversation"
            ConditionKind.LastNameIsMine -> (if (neg) "whose last name is not " else "whose last name is ") + myLastName.ifBlank { "(not set)" }
            ConditionKind.InContacts -> if (neg) "who is not in my contacts" else "who is in my contacts"
            ConditionKind.StarredContact -> if (neg) "who is not a starred contact" else "who is a starred contact"
            ConditionKind.TimeWindow -> (if (neg) "outside " else "between ") + AppPolicy.formatMinutes(c.from) + (if (neg) " to " else " and ") + AppPolicy.formatMinutes(c.to)
        }
    }

    private val senderKinds = setOf(
        ConditionKind.LastNameIsMine, ConditionKind.InContacts, ConditionKind.StarredContact, ConditionKind.PersonIs,
        ConditionKind.SenderContains, ConditionKind.SenderStartsWith, ConditionKind.SenderEquals,
    )

    /** "Anyone whose last name is Sharma -> Family (Priority)". */
    fun describe(
        rule: GroupRule, group: LaunchpadGroup?, myLastName: String = "",
        appLabel: (String) -> String = { it }, personName: (String) -> String = { it },
    ): String {
        val subject = if (rule.conditions.any { it.kind in senderKinds }) "Anyone" else "Any notification"
        val body = rule.conditions.joinToString(" and ") { clause(it, appLabel, personName, myLastName) }
        val target = group?.let { it.name + if (it.priority == GroupPriority.Normal) "" else " (${it.priority.name})" } ?: "(no group)"
        return (if (body.isEmpty()) "$subject (no conditions yet)" else "$subject $body") + " -> $target"
    }
}
