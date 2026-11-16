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

    fun encode(c: GroupsConfig): JSONObject {
        val o = JSONObject()
        runCatching { JSONObject(c.extra) }.getOrNull()?.let { x -> x.keys().forEach { k -> if (k !in KNOWN) o.put(k, x.get(k)) } }
        o.put("v", VERSION)
        o.put("groups", JSONArray().also { a ->
            c.groups.forEach { g ->
                a.put(JSONObject().put("id", g.id).put("name", g.name).put("order", g.order).put("priority", g.priority.name).apply {
                    if (g.icon.isNotEmpty()) put("icon", g.icon)
                    g.colorArgb?.let { put("color", it) }
                    g.delivery?.let { put("delivery", it.name) }
                })
            }
        })
        o.put("people", JSONArray().also { a ->
            c.people.forEach { p ->
                a.put(JSONObject().put("id", p.id).put("name", p.name).put("aliases", JSONArray(p.aliases)).apply { p.contactKey?.let { put("contact", it) } })
            }
        })
        o.put("assignments", JSONArray().also { a ->
            c.assignments.forEach { x ->
                a.put(JSONObject().put("kind", x.kind.name).put("target", x.target).apply { if (x.groupId != null) put("group", x.groupId) })
            }
        })
        o.put("rules", JSONArray().also { a ->
            c.rules.forEach { r ->
                a.put(JSONObject().put("id", r.id).put("group", r.groupId).put("enabled", r.enabled).apply {
                    if (r.name.isNotEmpty()) put("name", r.name)
                    put("conditions", JSONArray().also { ca ->
                        r.conditions.forEach { x ->
                            ca.put(JSONObject().put("kind", x.kind.name).apply {
                                if (x.value.isNotEmpty()) put("value", x.value)
                                if (x.kind == ConditionKind.TimeWindow) { put("from", x.from); put("to", x.to) }
                                if (x.negate) put("negate", true)
                            })
                        }
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
            val extra = JSONObject().also { x -> o.keys().forEach { k -> if (k !in KNOWN) x.put(k, o.get(k)) } }
            val ids = HashSet<String>()
            val groups = o.optJSONArray("groups").objects().mapNotNull { g ->
                val id = g.optString("id").ifBlank { return@mapNotNull null }
                if (!ids.add(id)) return@mapNotNull null
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
                Person(id, p.optString("name").ifBlank { aliases.firstOrNull() ?: return@mapNotNull null }, aliases, p.optString("contact").ifEmpty { null })
            }.distinctBy { it.id }.take(GroupsConfig.MAX_PEOPLE)
            val assignments = o.optJSONArray("assignments").objects().mapNotNull { a ->
                val kind = enumOf<AssignKind>(a.optString("kind")) ?: return@mapNotNull null
                val target = a.optString("target").ifBlank { return@mapNotNull null }
                GroupAssignment(kind, target, a.optString("group").ifEmpty { null })
            }.distinctBy { it.kind to it.target }.take(GroupsConfig.MAX_ASSIGNMENTS)
            val rules = o.optJSONArray("rules").objects().mapNotNull { r ->
                val conds = r.optJSONArray("conditions").objects().mapNotNull { x ->
                    val kind = enumOf<ConditionKind>(x.optString("kind")) ?: return@mapNotNull null
                    Condition(kind, x.optString("value"), x.optInt("from", 0), x.optInt("to", 0), x.optBoolean("negate", false))
                }
                GroupRule(r.optString("id").ifBlank { return@mapNotNull null }, conds, r.optString("group").ifBlank { return@mapNotNull null },
                    r.optBoolean("enabled", true), r.optString("name"))
            }.distinctBy { it.id }.take(GroupsConfig.MAX_RULES)
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
        val s = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
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
        DestructiveEdits.before("unlink person")
        edit { GroupsEdit.unlinkAlias(it, personId, alias) }
    }

    fun setMyLastName(name: String) = edit { GroupsEdit.setMyLastName(it, name) }
}
