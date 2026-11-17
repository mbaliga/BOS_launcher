package com.bos.sphere.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * STR-13: JSON codec for [GroupsConfig]. Tolerant: unknown enum values fall back, malformed entries are skipped, sizes are
 * capped, and fields this build does not know are kept in [GroupsConfig.extra]. An absent or empty document is the
 * empty config, which behaves exactly like a build without groups.
 */
object GroupsJson {
    const val VERSION = 1
    private val KNOWN = setOf("v", "groups", "people", "assignments", "rules", "myLastName")

    /**
     * CFG-03: a reserved key inside [GroupsConfig.extra]. It is never written to the file under this name: [ENTRIES] holds the
     * fields of single groups, people, assignments and rules that this build does not know (keyed by kind and id). A rule with a
     * condition this build cannot read (a kind from a newer build) is loaded switched off, because evaluating it without that
     * condition would match more notifications than its author meant; its unreadable conditions and its stored on/off state
     * are kept under [UNREADABLE] in the rule's entry and written back with it.
     */
    private const val ENTRIES = "~entries"
    private const val UNREADABLE = "~unreadable"
    private val GROUP_KEYS = setOf("id", "name", "icon", "color", "order", "priority", "delivery")
    private val PERSON_KEYS = setOf("id", "name", "aliases", "contact")
    private val ASSIGN_KEYS = setOf("kind", "target", "group")
    private val RULE_KEYS = setOf("id", "group", "enabled", "name", "conditions")

    private fun assignKey(kind: String, target: String) = "a:$kind|$target"

    private fun JSONObject.withExtras(extras: JSONObject?, key: String, known: Set<String>): JSONObject {
        val x = extras?.optJSONObject(key) ?: return this
        x.keys().forEach { k -> if (k !in known && !has(k)) put(k, x.get(k)) }
        return this
    }

    fun encode(c: GroupsConfig): JSONObject {
        val o = JSONObject()
        val extra = runCatching { JSONObject(c.extra) }.getOrNull()
        extra?.let { x -> x.keys().forEach { k -> if (k !in KNOWN && k != ENTRIES) o.put(k, x.get(k)) } }
        val entries = extra?.optJSONObject(ENTRIES)
        o.put("v", VERSION)
        o.put("groups", JSONArray().also { a ->
            c.groups.forEach { g ->
                a.put(JSONObject().put("id", g.id).put("name", g.name).put("order", g.order).put("priority", g.priority.name).apply {
                    if (g.icon.isNotEmpty()) put("icon", g.icon)
                    g.colorArgb?.let { put("color", it) }
                    g.delivery?.let { put("delivery", it.name) }
                }.withExtras(entries, "g:${g.id}", GROUP_KEYS))
            }
        })
        o.put("people", JSONArray().also { a ->
            c.people.forEach { p ->
                a.put(JSONObject().put("id", p.id).put("name", p.name).put("aliases", JSONArray(p.aliases)).apply { p.contactKey?.let { put("contact", it) } }
                    .withExtras(entries, "p:${p.id}", PERSON_KEYS))
            }
        })
        o.put("assignments", JSONArray().also { a ->
            c.assignments.forEach { x ->
                a.put(JSONObject().put("kind", x.kind.name).put("target", x.target).apply { if (x.groupId != null) put("group", x.groupId) }
                    .withExtras(entries, assignKey(x.kind.name, x.target), ASSIGN_KEYS))
            }
        })
        o.put("rules", JSONArray().also { a ->
            c.rules.forEach { r ->
                val lost = entries?.optJSONObject("r:${r.id}")?.optJSONObject(UNREADABLE)
                a.put(JSONObject().put("id", r.id).put("group", r.groupId).put("enabled", r.enabled || lost?.optBoolean("enabled", false) == true)
                    .withExtras(entries, "r:${r.id}", RULE_KEYS + UNREADABLE).apply {
                    if (r.name.isNotEmpty()) put("name", r.name)
                    put("conditions", JSONArray().also { ca ->
                        r.conditions.forEach { x ->
                            ca.put(JSONObject().put("kind", x.kind.name).apply {
                                if (x.value.isNotEmpty()) put("value", x.value)
                                if (x.kind == ConditionKind.TimeWindow) { put("from", x.from); put("to", x.to) }
                                if (x.negate) put("negate", true)
                            })
                        }
                        lost?.optJSONArray("conditions")?.let { l -> for (i in 0 until l.length()) l.optJSONObject(i)?.let { ca.put(it) } }
                    })
                })
            }
        })
        if (c.myLastName.isNotBlank()) o.put("myLastName", c.myLastName)
        return o
    }

    private inline fun <reified E : Enum<E>> enumOf(s: String?): E? = enumValues<E>().firstOrNull { it.name == s }

    fun decode(o: JSONObject?): GroupsConfig {
        if (o == null) return GroupsConfig()
        return runCatching {
            val extra = JSONObject().also { x -> o.keys().forEach { k -> if (k !in KNOWN && k != ENTRIES) x.put(k, o.get(k)) } }
            val entries = JSONObject()
            fun keep(key: String, e: JSONObject, known: Set<String>) {
                var unk: JSONObject? = null
                e.keys().forEach { k -> if (k !in known) { if (unk == null) unk = JSONObject(); unk!!.put(k, e.get(k)) } }
                unk?.let { entries.put(key, it) }
            }
            val ids = HashSet<String>()
            val groups = o.optJSONArray("groups").objects().mapNotNull { g ->
                val id = g.optString("id").ifBlank { return@mapNotNull null }
                if (!ids.add(id)) return@mapNotNull null
                keep("g:$id", g, GROUP_KEYS)
                LaunchpadGroup(
                    id = id,
                    name = g.optString("name").trim().ifEmpty { "Group" }.take(GroupsConfig.MAX_NAME),
                    icon = g.optString("icon"),
                    colorArgb = if (g.has("color")) g.optLong("color") else null,
                    order = g.optInt("order", 0),
                    priority = enumOf<GroupPriority>(g.optString("priority")) ?: GroupPriority.Normal,
                    delivery = enumOf<DeliveryMode>(g.optString("delivery")),
                )
            }.take(GroupsConfig.MAX_GROUPS).sortedBy { it.order }.mapIndexed { i, g -> g.copy(order = i) }
            val people = o.optJSONArray("people").objects().mapNotNull { p ->
                val id = p.optString("id").ifBlank { return@mapNotNull null }
                val aliases = p.optJSONArray("aliases")?.let { a -> List(a.length()) { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
                keep("p:$id", p, PERSON_KEYS)
                Person(id, p.optString("name").ifBlank { aliases.firstOrNull() ?: return@mapNotNull null }, aliases, p.optString("contact").ifEmpty { null })
            }.distinctBy { it.id }.take(GroupsConfig.MAX_PEOPLE)
            val assignments = o.optJSONArray("assignments").objects().mapNotNull { a ->
                val kind = enumOf<AssignKind>(a.optString("kind")) ?: return@mapNotNull null
                val target = a.optString("target").ifBlank { return@mapNotNull null }
                keep(assignKey(kind.name, target), a, ASSIGN_KEYS)
                GroupAssignment(kind, target, a.optString("group").ifEmpty { null })
            }.distinctBy { it.kind to it.target }.take(GroupsConfig.MAX_ASSIGNMENTS)
            val rules = o.optJSONArray("rules").objects().mapNotNull { r ->
                val condObjs = r.optJSONArray("conditions").objects()
                val conds = condObjs.mapNotNull { x ->
                    val kind = enumOf<ConditionKind>(x.optString("kind")) ?: return@mapNotNull null
                    Condition(kind, x.optString("value"), x.optInt("from", 0), x.optInt("to", 0), x.optBoolean("negate", false))
                }
                val rid = r.optString("id").ifBlank { return@mapNotNull null }
                val gid = r.optString("group").ifBlank { return@mapNotNull null }
                keep("r:$rid", r, RULE_KEYS)
                // A condition this build cannot read: the rule loads switched off and the unreadable parts are kept (see UNREADABLE).
                val unreadable = condObjs.filter { x -> enumOf<ConditionKind>(x.optString("kind")) == null }
                if (unreadable.isNotEmpty()) {
                    val e = entries.optJSONObject("r:$rid") ?: JSONObject().also { entries.put("r:$rid", it) }
                    e.put(UNREADABLE, JSONObject().put("enabled", r.optBoolean("enabled", true)).put("conditions", JSONArray(unreadable)))
                }
                GroupRule(rid, conds, gid, r.optBoolean("enabled", true) && unreadable.isEmpty(), r.optString("name"))
            }.distinctBy { it.id }.take(GroupsConfig.MAX_RULES)
            if (entries.length() > 0) extra.put(ENTRIES, entries)
            GroupsConfig(groups, people, assignments, rules, o.optString("myLastName").trim(), extra.toString())
        }.getOrDefault(GroupsConfig())
    }

    fun decodeString(s: String?): GroupsConfig = if (s.isNullOrBlank()) GroupsConfig() else decode(runCatching { JSONObject(s) }.getOrNull())

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}

/**
 * STR-13: the live, persisted groups. Own preference file ("launchpad_groups"): exported with the config (the state
 * store's launchpad section) but kept out of cloud backup and device transfer like other personal state (NFR-18,
 * [StateSchema.BACKUP_EXCLUDED_PREF_FILES]), because it holds people's names and the user's last name.
 */
object GroupsSettings {
    const val FILE = "launchpad_groups"
    private var sp: android.content.SharedPreferences? = null
    private val _config = MutableStateFlow(GroupsConfig())
    val config: StateFlow<GroupsConfig> = _config.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (sp != null) return
        val s = TolerantPrefs.of(context.applicationContext, FILE)
        sp = s
        _config.value = GroupsJson.decodeString(s.getString("config", null))
    }

    @Synchronized
    private fun write(c: GroupsConfig) {
        _config.value = c
        sp?.edit()?.putString("config", GroupsJson.encode(c).toString())?.apply()
    }

    @Synchronized
    fun edit(f: (GroupsConfig) -> GroupsConfig) {
        val next = f(_config.value)
        if (next != _config.value) write(next)
    }

    /** Replaces everything (an import or a restore). The import path has already taken its snapshot. */
    fun restore(c: GroupsConfig) = write(c)

    fun snapshot(): GroupsConfig = _config.value

    fun addGroup(name: String, priority: GroupPriority = GroupPriority.Normal, icon: String = "", colorArgb: Long? = null) =
        edit { GroupsEdit.addGroup(it, name, priority, icon, colorArgb) }
    fun renameGroup(id: String, name: String) = edit { GroupsEdit.rename(it, id, name) }
    fun setPriority(id: String, p: GroupPriority) = edit { GroupsEdit.setPriority(it, id, p) }
    fun setDelivery(id: String, d: DeliveryMode?) = edit { GroupsEdit.setDelivery(it, id, d) }
    fun moveGroup(id: String, delta: Int) = edit { GroupsEdit.move(it, id, delta) }

    /** CFG-04: snapshot first, then delete the group together with the assignments and rules that pointed at it. */
    fun deleteGroup(id: String) {
        if (_config.value.group(id) == null) return
        DestructiveEdits.before("delete Launchpad group")
        edit { GroupsEdit.delete(it, id) }
    }

    fun assign(kind: AssignKind, target: String, groupId: String?) {
        if (groupId == null && _config.value.assignments.none { it.kind == kind && it.target == target }) return
        edit { GroupsEdit.assign(it, kind, target, groupId) }
    }

    fun unassign(kind: AssignKind, target: String) {
        if (_config.value.assignments.none { it.kind == kind && it.target == target }) return
        DestructiveEdits.before("remove Launchpad assignment")
        edit { GroupsEdit.unassign(it, kind, target) }
    }

    fun addRule(rule: GroupRule) = edit { GroupsEdit.addRule(it, rule) }
    fun updateRule(rule: GroupRule) = edit { GroupsEdit.updateRule(it, rule) }
    fun moveRule(id: String, delta: Int) = edit { GroupsEdit.moveRule(it, id, delta) }
    fun removeRule(id: String) {
        if (_config.value.rules.none { it.id == id }) return
        DestructiveEdits.before("remove Launchpad group rule")
        edit { GroupsEdit.removeRule(it, id) }
    }

    fun linkSenders(nameA: String, nameB: String, contactKey: String? = null) = edit { GroupsEdit.linkSenders(it, nameA, nameB, contactKey) }
    fun unlinkAlias(personId: String, alias: String) {
        if (GroupsEdit.unlinkAlias(_config.value, personId, alias) == _config.value) return
        DestructiveEdits.before("unlink person")
        edit { GroupsEdit.unlinkAlias(it, personId, alias) }
    }

    /** CFG-04: snapshot first, then remove the person link with the assignments and rule conditions that used it. */
    fun removePerson(personId: String) {
        if (_config.value.person(personId) == null) return
        DestructiveEdits.before("remove person link")
        edit { GroupsEdit.removePerson(it, personId) }
    }

    fun setMyLastName(name: String) = edit { GroupsEdit.setMyLastName(it, name) }
}
