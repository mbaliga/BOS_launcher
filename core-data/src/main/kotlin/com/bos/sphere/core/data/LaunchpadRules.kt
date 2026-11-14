package com.bos.sphere.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

enum class RuleAction {
    /** Never shown (unless a more specific Pin/Important rule rescues it). */
    Hide,
    /** Shown, but contributes nothing to rail badges / counts. */
    Silent,
    /** Stays on top, in a "Pinned" section. */
    Pin,
    /** Floats to the top of its section. */
    Important,
    /** Removed from the Launchpad [Rule.minutes] minutes after it was posted. */
    AutoClear,
    GroupApp,
    GroupSender,
    GroupConversation,
    Flat;

    val isGrouping: Boolean get() = groupMode != null

    val groupMode: GroupMode?
        get() = when (this) {
            GroupApp -> GroupMode.App
            GroupSender -> GroupMode.Sender
            GroupConversation -> GroupMode.Conversation
            Flat -> GroupMode.Flat
            else -> null
        }
}

enum class GroupMode {
    Time, App, Sender, Conversation, Flat,

    /** STR-02: by app, then by conversation inside the app; apps and conversations newest first. */
    AppConversation,
}

/**
 * A user rule. Every non-null matcher must match (case-insensitive; [text] is "contains" over
 * title + summary, the rest are equality). A rule with no matcher is global.
 */
data class Rule(
    val id: String,
    val packageName: String? = null,
    val sender: String? = null,
    val channel: String? = null,
    val text: String? = null,
    val action: RuleAction,
    val minutes: Int = 0,
    /** Conversation / chat title matcher (equality, case-insensitive). */
    val conversation: String? = null,
) {
    fun matches(n: HubNotification): Boolean {
        if (packageName != null && !packageName.equals(n.packageName, ignoreCase = true)) return false
        if (sender != null && !sender.equals(LaunchpadLogic.senderOf(n), ignoreCase = true)) return false
        if (conversation != null && !conversation.equals(LaunchpadLogic.conversationOf(n), ignoreCase = true)) return false
        if (channel != null &&
            !channel.equals(n.channelId, ignoreCase = true) && !channel.equals(n.channelName, ignoreCase = true)
        ) return false
        if (text != null &&
            !n.title.contains(text, ignoreCase = true) && !n.summary.contains(text, ignoreCase = true)
        ) return false
        return true
    }

    /** sender > text > channel > app > global. */
    val specificity: Int
        get() = (if (sender != null) 1000 else 0) + (if (conversation != null) 500 else 0) + (if (text != null) 100 else 0) +
            (if (channel != null) 10 else 0) + (if (packageName != null) 1 else 0)

    /** Plain-English description, e.g. "Pin Mum in Messages", "Hide notifications from WhatsApp". */
    fun describe(appLabel: String?): String {
        val core = sender ?: conversation?.let { "conversation $it" } ?: "notifications"
        val loc = (appLabel ?: packageName)?.let { if (sender != null || conversation != null) "in $it" else "from $it" }
        val what = listOfNotNull(
            core, loc,
            if (sender != null) conversation?.let { "(chat $it)" } else null,
            channel?.let { "(channel $it)" },
            text?.let { "containing “$it”" },
        ).joinToString(" ")
        return when (action) {
            RuleAction.Hide -> "Hide $what"
            RuleAction.Silent -> "Mute $what (no badges)"
            RuleAction.Pin -> "Pin $what"
            RuleAction.Important -> "Mark $what important"
            RuleAction.AutoClear -> "Auto-clear $what after ${formatMinutes(minutes)}"
            RuleAction.GroupApp -> "Group $what by app"
            RuleAction.GroupSender -> "Group $what by sender"
            RuleAction.GroupConversation -> "Group $what by conversation"
            RuleAction.Flat -> "Show $what as a flat list"
        }
    }

    companion object {
        fun create(
            action: RuleAction,
            packageName: String? = null,
            sender: String? = null,
            channel: String? = null,
            text: String? = null,
            minutes: Int = 0,
            conversation: String? = null,
        ) = Rule(
            UUID.randomUUID().toString(), packageName?.ifBlank { null }, sender?.ifBlank { null },
            channel?.ifBlank { null }, text?.ifBlank { null }, action, minutes, conversation?.ifBlank { null },
        )

        fun formatMinutes(m: Int): String = when {
            m <= 0 -> "0 min"
            m % 1440 == 0 -> if (m == 1440) "1 day" else "${m / 1440} days"
            m % 60 == 0 -> if (m == 60) "1 hour" else "${m / 60} hours"
            else -> "$m min"
        }
    }
}

data class LaunchpadPrefs(
    val defaultMode: GroupMode = GroupMode.AppConversation,
    /** Per-app (package name) grouping override. */
    val perApp: Map<String, GroupMode> = emptyMap(),
)

data class DayGroup(val label: String, val items: List<HubNotification>)

data class SourceCount(val packageName: String, val appLabel: String, val count: Int)

data class Section(
    val title: String,
    val key: String,
    val items: List<HubNotification>,
    val pinned: Boolean = false,
    /** Secondary label (the app, for sender / conversation sections). */
    val subtitle: String = "",
)

/** Pure, Android-free Launchpad logic. */
object LaunchpadLogic {

    const val PINNED_KEY = "pinned"
    const val FLAT_KEY = "flat"

    private val dayFormat = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
    fun defaultDayLabel(d: LocalDate): String = dayFormat.format(d)

    fun senderOf(n: HubNotification): String = n.sender?.takeIf { it.isNotBlank() } ?: n.title
    fun conversationOf(n: HubNotification): String =
        n.conversation?.takeIf { it.isNotBlank() } ?: senderOf(n)
    fun appNameOf(n: HubNotification): String = n.appLabel.ifBlank { n.packageName.ifBlank { n.source } }

    // ---- basics -------------------------------------------------------------------------

    fun groupByDay(
        list: List<HubNotification>,
        now: Long,
        zone: ZoneId,
        labelFor: (LocalDate) -> String = ::defaultDayLabel,
        todayLabel: String = "Today",
        yesterdayLabel: String = "Yesterday",
    ): List<DayGroup> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return list.sortedByDescending { it.timestamp }
            .groupBy { dayOf(it, zone) }
            .entries.sortedByDescending { it.key }
            .map { (d, items) ->
                val label = when (d) {
                    today -> todayLabel
                    today.minusDays(1) -> yesterdayLabel
                    else -> labelFor(d)
                }
                DayGroup(label, items)
            }
    }

    private fun dayOf(n: HubNotification, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(n.timestamp).atZone(zone).toLocalDate()

    fun countsBySource(list: List<HubNotification>): List<SourceCount> =
        list.groupBy { it.packageName }
            .map { (pkg, items) -> SourceCount(pkg, appNameOf(items.first()), items.size) }
            .sortedWith(compareByDescending<SourceCount> { it.count }.thenBy { it.appLabel.lowercase() })

    fun filter(list: List<HubNotification>, packageName: String?): List<HubNotification> =
        if (packageName == null) list else list.filter { it.packageName == packageName }

    // ---- rules --------------------------------------------------------------------------

    /** The winning rule for [action]: most specific match; ties go to the most recently added. */
    fun bestRule(rules: List<Rule>, n: HubNotification, action: RuleAction): Rule? =
        rules.withIndex()
            .filter { it.value.action == action && it.value.matches(n) }
            .maxWithOrNull(compareBy<IndexedValue<Rule>> { it.value.specificity }.thenBy { it.index })
            ?.value

    fun isPinned(n: HubNotification, rules: List<Rule>) = bestRule(rules, n, RuleAction.Pin) != null
    fun isImportant(n: HubNotification, rules: List<Rule>) = bestRule(rules, n, RuleAction.Important) != null
    fun isSilent(n: HubNotification, rules: List<Rule>) = bestRule(rules, n, RuleAction.Silent) != null

    /** Hidden by a Hide rule, unless a strictly more specific Pin / Important rule rescues it. */
    fun isHidden(n: HubNotification, rules: List<Rule>): Boolean {
        val hide = bestRule(rules, n, RuleAction.Hide) ?: return false
        val rescue = maxOf(
            bestRule(rules, n, RuleAction.Pin)?.specificity ?: -1,
            bestRule(rules, n, RuleAction.Important)?.specificity ?: -1,
        )
        return rescue <= hide.specificity
    }

    fun isExpired(n: HubNotification, rules: List<Rule>, now: Long): Boolean {
        val r = bestRule(rules, n, RuleAction.AutoClear) ?: return false
        return r.minutes > 0 && now - n.timestamp >= r.minutes * 60_000L
    }

    /** Grouping mode for one notification: most specific grouping rule, else per-app, else default. */
    fun effectiveMode(n: HubNotification, rules: List<Rule>, prefs: LaunchpadPrefs): GroupMode {
        val rule = rules.withIndex()
            .filter { it.value.action.isGrouping && it.value.matches(n) }
            .maxWithOrNull(compareBy<IndexedValue<Rule>> { it.value.specificity }.thenBy { it.index })
            ?.value
        return rule?.action?.groupMode ?: prefs.perApp[n.packageName] ?: prefs.defaultMode
    }

    /** Drops hidden and auto-cleared notifications. */
    fun visible(list: List<HubNotification>, rules: List<Rule>, now: Long): List<HubNotification> =
        list.filter { !isHidden(it, rules) && !isExpired(it, rules, now) }

    /**
     * The final ordered sections: hide / auto-clear removed, pinned first in a "Pinned"
     * section, the rest grouped per effective mode (sections ordered by newest item).
     */
    fun apply(
        list: List<HubNotification>,
        rules: List<Rule>,
        prefs: LaunchpadPrefs,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        labelFor: (LocalDate) -> String = ::defaultDayLabel,
        todayLabel: String = "Today",
        yesterdayLabel: String = "Yesterday",
        pinnedLabel: String = "Pinned",
        flatLabel: String = "All",
    ): List<Section> {
        val shown = visible(list, rules, now)
        val itemOrder = compareByDescending<HubNotification> { isImportant(it, rules) }
            .thenByDescending { it.timestamp }
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

        val (pinned, rest) = shown.partition { isPinned(it, rules) }
        val sections = ArrayList<Section>()
        if (pinned.isNotEmpty()) {
            sections += Section(pinnedLabel, PINNED_KEY, pinned.sortedWith(itemOrder), pinned = true)
        }

        // key -> (title, subtitle, items), insertion-ordered
        class Bucket(val title: String, val subtitle: String, val cluster: String? = null, val items: MutableList<HubNotification> = mutableListOf())
        val buckets = LinkedHashMap<String, Bucket>()
        for (n in rest) {
            val mode = effectiveMode(n, rules, prefs)
            val (key, title, sub) = when (mode) {
                GroupMode.Time -> {
                    val d = dayOf(n, zone)
                    val label = when (d) {
                        today -> todayLabel
                        today.minusDays(1) -> yesterdayLabel
                        else -> labelFor(d)
                    }
                    Triple("day:$d", label, "")
                }
                GroupMode.App -> Triple("app:${n.packageName}", appNameOf(n), "")
                GroupMode.Sender ->
                    Triple("sender:${n.packageName}:${senderOf(n).lowercase()}", senderOf(n), appNameOf(n))
                GroupMode.Conversation ->
                    Triple("conv:${n.packageName}:${conversationOf(n).lowercase()}", conversationOf(n), appNameOf(n))
                GroupMode.Flat -> Triple(FLAT_KEY, flatLabel, "")
                GroupMode.AppConversation ->
                    Triple("appconv:${n.packageName}:${conversationOf(n).lowercase()}", conversationOf(n), appNameOf(n))
            }
            buckets.getOrPut(key) { Bucket(title, sub, if (mode == GroupMode.AppConversation) n.packageName else null) }.items += n
        }
        // Conversations of one app stay together, apps ordered by their newest item, conversations by theirs.
        val appNewest = HashMap<String, Long>()
        for (b in buckets.values) b.cluster?.let { c -> appNewest[c] = maxOf(appNewest[c] ?: 0L, b.items.maxOf { it.timestamp }) }
        sections += buckets.map { (k, b) -> Triple(Section(b.title, k, b.items.sortedWith(itemOrder), false, b.subtitle), b.cluster, b) }
            .sortedWith(
                compareByDescending<Triple<Section, String?, Bucket>> { (sec, c, _) -> c?.let { appNewest[it] } ?: sec.items.maxOf { it.timestamp } }
                    .thenBy { (_, c, _) -> c ?: "" }
                    .thenByDescending { (sec, _, _) -> sec.items.maxOf { it.timestamp } },
            ).map { it.first }
        return sections
    }

    /**
     * Per-app unread counts for the rail: ignores hidden, auto-cleared, Silent, ongoing
     * notifications and those whose key is in [readKeys].
     */
    fun unreadCounts(
        list: List<HubNotification>,
        rules: List<Rule>,
        readKeys: Set<String>,
        now: Long,
    ): List<SourceCount> = countsBySource(
        visible(list, rules, now).filter { !isSilent(it, rules) && !it.isOngoing && it.key !in readKeys },
    )

    fun unreadTotal(list: List<HubNotification>, rules: List<Rule>, readKeys: Set<String>, now: Long): Int =
        unreadCounts(list, rules, readKeys, now).sumOf { it.count }
}

// ---- persistence ------------------------------------------------------------------------

/** JSON (org.json) codec for rules and prefs; pure apart from org.json. */
object LaunchpadJson {
    fun encodeRules(rules: List<Rule>): String = JSONArray().also { arr ->
        rules.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("action", r.action.name)
                r.packageName?.let { put("pkg", it) }
                r.sender?.let { put("sender", it) }
                r.channel?.let { put("channel", it) }
                r.text?.let { put("text", it) }
                r.conversation?.let { put("conv", it) }
                put("minutes", r.minutes)
            })
        }
    }.toString()

    fun decodeRules(json: String?): List<Rule> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val action = runCatching { RuleAction.valueOf(o.getString("action")) }.getOrNull()
                    ?: return@mapNotNull null
                Rule(
                    id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
                    packageName = o.optString("pkg").ifEmpty { null },
                    sender = o.optString("sender").ifEmpty { null },
                    channel = o.optString("channel").ifEmpty { null },
                    text = o.optString("text").ifEmpty { null },
                    action = action,
                    minutes = o.optInt("minutes", 0),
                    conversation = o.optString("conv").ifEmpty { null },
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encodePrefs(p: LaunchpadPrefs): String = JSONObject().apply {
        put("default", p.defaultMode.name)
        put("perApp", JSONObject().also { o -> p.perApp.forEach { (k, v) -> o.put(k, v.name) } })
    }.toString()

    fun decodePrefs(json: String?): LaunchpadPrefs {
        if (json.isNullOrBlank()) return LaunchpadPrefs()
        return runCatching {
            val o = JSONObject(json)
            val per = o.optJSONObject("perApp")
            val map = HashMap<String, GroupMode>()
            per?.keys()?.forEach { k ->
                runCatching { GroupMode.valueOf(per.getString(k)) }.getOrNull()?.let { map[k] = it }
            }
            LaunchpadPrefs(
                runCatching { GroupMode.valueOf(o.optString("default")) }.getOrDefault(GroupMode.Time),
                map,
            )
        }.getOrDefault(LaunchpadPrefs())
    }
}

/**
 * Process-wide Launchpad settings: rules + grouping prefs (persisted in SharedPreferences) and
 * the in-memory set of notification keys the user has opened. Call [init] once with any
 * context before use (the Launchpad composables do this themselves).
 */
/** Things you can do to the Launchpad itself, each bindable to a [LaunchpadAction]. */
enum class LaunchpadGesture(val label: String, val hint: String) {
    PinchIn("Pinch in", "Two fingers together on the list"),
    PinchOut("Pinch out", "Two fingers apart on the list"),
    SwipeItemRight("Slide an item right", "Opens the palette of actions (fixed)"),
    SwipeItemLeft("Slide an item left", "Drag a notification to the left"),
    LongPressItem("Long-press an item", "Hold a notification"),
    DoubleTapHeader("Double-tap the header", "Tap the blue bar twice"),
}

enum class LaunchpadAction(val label: String, val needsItem: Boolean) {
    None("Nothing", false),
    ShowUnread("Show unread only", false),
    ShowAll("Show everything", false),
    ToggleUnread("Switch unread / everything", false),
    NextGrouping("Next grouping", false),
    CollapseAll("Collapse / expand groups", false),
    ClearAll("Clear all", false),
    Dismiss("Dismiss", true),
    MarkRead("Mark as read", true),
    OpenApp("Open the app", true),
    MoreOptions("More options…", true),
    MuteApp("Mute this app", true),
    HideApp("Hide this app", true),
    PinSender("Pin this sender", true),
}

object LaunchpadGestures {
    val defaults: Map<LaunchpadGesture, LaunchpadAction> = mapOf(
        LaunchpadGesture.PinchIn to LaunchpadAction.ShowUnread,
        LaunchpadGesture.PinchOut to LaunchpadAction.ShowAll,
        LaunchpadGesture.SwipeItemRight to LaunchpadAction.Dismiss,
        LaunchpadGesture.SwipeItemLeft to LaunchpadAction.Dismiss,
        LaunchpadGesture.LongPressItem to LaunchpadAction.MoreOptions,
        LaunchpadGesture.DoubleTapHeader to LaunchpadAction.NextGrouping,
    )

    /** Which actions make sense for [g]: item actions need an item under the finger. */
    fun allowed(g: LaunchpadGesture): List<LaunchpadAction> = when (g) {
        LaunchpadGesture.PinchIn, LaunchpadGesture.PinchOut, LaunchpadGesture.DoubleTapHeader ->
            LaunchpadAction.entries.filter { !it.needsItem }
        LaunchpadGesture.SwipeItemRight, LaunchpadGesture.SwipeItemLeft, LaunchpadGesture.LongPressItem ->
            LaunchpadAction.entries.filter { it != LaunchpadAction.ShowUnread && it != LaunchpadAction.ShowAll && it != LaunchpadAction.ToggleUnread }
    }

    fun encode(m: Map<LaunchpadGesture, LaunchpadAction>): String = m.entries.joinToString(";") { "${it.key.name}=${it.value.name}" }

    fun decode(raw: String?): Map<LaunchpadGesture, LaunchpadAction> {
        val out = LinkedHashMap(defaults)
        raw.orEmpty().split(';').forEach { part ->
            val (k, v) = part.split('=').takeIf { it.size == 2 } ?: return@forEach
            val g = LaunchpadGesture.entries.firstOrNull { it.name == k } ?: return@forEach
            val a = LaunchpadAction.entries.firstOrNull { it.name == v } ?: return@forEach
            if (a in allowed(g)) out[g] = a
        }
        return out
    }
}

object LaunchpadSettings {
    private const val FILE = "launchpad_rules"
    private var sp: android.content.SharedPreferences? = null

    private val _rules = MutableStateFlow<List<Rule>>(emptyList())
    private val _prefs = MutableStateFlow(LaunchpadPrefs())
    private val _read = MutableStateFlow<Set<String>>(emptySet())

    private val _gestures = MutableStateFlow(LaunchpadGestures.defaults)
    val gestures: StateFlow<Map<LaunchpadGesture, LaunchpadAction>> = _gestures.asStateFlow()

    fun setGesture(g: LaunchpadGesture, a: LaunchpadAction) {
        val m = _gestures.value.toMutableMap().also { it[g] = a }
        _gestures.value = m
        sp?.edit()?.putString("gestures", LaunchpadGestures.encode(m))?.apply()
    }

    fun resetGestures() {
        _gestures.value = LaunchpadGestures.defaults
        sp?.edit()?.remove("gestures")?.apply()
    }

    private val _policies = MutableStateFlow<List<AppPolicy>>(emptyList())
    /** STR-04: per-app delivery (immediate, digest, silent in Launchpad) and the per-app left-slide action. */
    val policies: StateFlow<List<AppPolicy>> = _policies.asStateFlow()

    private val _snoozed = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Notification key to the time it comes back. Held in memory: it follows the notification, not the config. */
    val snoozed: StateFlow<Map<String, Long>> = _snoozed.asStateFlow()

    fun snooze(key: String, until: Long) { _snoozed.value = _snoozed.value + (key to until) }
    fun unsnooze(key: String) { _snoozed.value = _snoozed.value - key }
    fun pruneSnoozed(activeKeys: Set<String>, now: Long) {
        val p = SnoozeLogic.prune(_snoozed.value, activeKeys, now)
        if (p.size != _snoozed.value.size) _snoozed.value = p
    }

    fun setPolicy(policy: AppPolicy) {
        val kept = _policies.value.filterNot { it.packageName.equals(policy.packageName, ignoreCase = true) }
        savePolicies(if (policy == AppPolicy(policy.packageName)) kept else kept + policy)
    }

    /** STR-04: replaces every per-app policy (import, restore). */
    fun replacePolicies(list: List<AppPolicy>) = savePolicies(list)

    private var unknownExtra = "{}"

    /** The whole persisted state as one document, for the state store and the config export. */
    fun snapshot(): LaunchpadRulesSnapshot =
        LaunchpadRulesSnapshot(rules = _rules.value, prefs = _prefs.value, gestures = _gestures.value, policies = _policies.value, extra = unknownExtra)

    /** Replaces everything from [snap] (an import or a restore) and writes it through. */
    fun restore(snap: LaunchpadRulesSnapshot) {
        unknownExtra = snap.extra
        saveRules(snap.rules); savePrefs(snap.prefs); savePolicies(snap.policies)
        _gestures.value = snap.gestures
        sp?.edit()?.putString("gestures", LaunchpadGestures.encode(snap.gestures))?.apply()
    }

    private fun savePolicies(p: List<AppPolicy>) {
        _policies.value = p
        sp?.edit()?.putString("policies", PolicyJson.encode(p).toString())?.apply()
    }

    val rules: StateFlow<List<Rule>> = _rules.asStateFlow()
    val prefs: StateFlow<LaunchpadPrefs> = _prefs.asStateFlow()
    /** Keys of notifications the user has opened (not persisted). */
    val readKeys: StateFlow<Set<String>> = _read.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (sp != null) return
        val s = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        sp = s
        _rules.value = LaunchpadJson.decodeRules(s.getString("rules", null))
        _prefs.value = LaunchpadJson.decodePrefs(s.getString("prefs", null))
        _gestures.value = LaunchpadGestures.decode(s.getString("gestures", null))
        _policies.value = PolicyJson.decode(s.getString("policies", null))
    }

    /** Adds [rule], replacing an existing rule with identical matchers and action. */
    fun addRule(rule: Rule) {
        val kept = _rules.value.filterNot {
            it.packageName == rule.packageName && it.sender == rule.sender && it.channel == rule.channel &&
                it.text == rule.text && it.conversation == rule.conversation && it.action == rule.action
        }
        saveRules(kept + rule)
    }

    fun removeRule(id: String) = saveRules(_rules.value.filterNot { it.id == id })

    fun setDefaultMode(mode: GroupMode) = savePrefs(_prefs.value.copy(defaultMode = mode))

    /** Per-app override; null clears it. */
    fun setAppMode(packageName: String, mode: GroupMode?) {
        val m = _prefs.value.perApp.toMutableMap()
        if (mode == null) m.remove(packageName) else m[packageName] = mode
        savePrefs(_prefs.value.copy(perApp = m))
    }

    fun markRead(key: String) { _read.value = _read.value + key }

    /** Forget read keys for notifications that no longer exist. */
    fun pruneRead(activeKeys: Set<String>) {
        val cur = _read.value
        if (cur.any { it !in activeKeys }) _read.value = cur.filter { it in activeKeys }.toSet()
    }

    private fun saveRules(r: List<Rule>) {
        _rules.value = r
        sp?.edit()?.putString("rules", LaunchpadJson.encodeRules(r))?.apply()
    }

    private fun savePrefs(p: LaunchpadPrefs) {
        _prefs.value = p
        sp?.edit()?.putString("prefs", LaunchpadJson.encodePrefs(p))?.apply()
    }
}
