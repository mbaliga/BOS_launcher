package com.bos.sphere.core.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// STR-04: per-app delivery (show immediately, batch into a digest, silent in Launchpad only).
// All of this only changes what Liftoff's own surfaces show. Android's own notifications are untouched.

enum class DeliveryMode(val label: String, val hint: String) {
    Immediate("Show immediately", "Appears in Launchpad as it arrives"),
    Digest("Batch into a digest", "Held back and released at the times you choose"),
    SilentInLaunchpad("Silent, Launchpad only", "Listed in Launchpad, but never counted or highlighted"),
}

/** The sentence Launchpad shows wherever rules are edited (STR-04: "Launchpad says so"). */
const val LAUNCHPAD_SCOPE_NOTICE = "These rules change what Liftoff shows. Android's own notifications are not changed."

/**
 * What one app does in Launchpad. [digestMinutes] are minutes after midnight, local time. [primaryAction] is the
 * action that sliding a row left runs for this app; null means the global gesture setting (default Dismiss).
 */
data class AppPolicy(
    val packageName: String,
    val mode: DeliveryMode = DeliveryMode.Immediate,
    val digestMinutes: List<Int> = DEFAULT_DIGEST_TIMES,
    val primaryAction: LaunchpadAction? = null,
) {
    companion object {
        val DEFAULT_DIGEST_TIMES = listOf(8 * 60, 18 * 60)
        fun formatMinutes(m: Int): String = "%02d:%02d".format(m / 60, m % 60)
    }
}

object DeliveryLogic {
    fun policyFor(pkg: String, policies: List<AppPolicy>): AppPolicy? =
        policies.lastOrNull { it.packageName.equals(pkg, ignoreCase = true) }

    fun modeFor(pkg: String, policies: List<AppPolicy>): DeliveryMode = policyFor(pkg, policies)?.mode ?: DeliveryMode.Immediate

    /** The action a left slide runs for [pkg]: the app's own choice, else [fallback] (the global gesture). */
    fun primaryFor(pkg: String, policies: List<AppPolicy>, fallback: LaunchpadAction): LaunchpadAction =
        policyFor(pkg, policies)?.primaryAction ?: fallback

    /** The most recent scheduled release at or before [now], or null when there are no times. */
    fun lastRelease(now: Long, zone: ZoneId, minutes: List<Int>): Long? {
        if (minutes.isEmpty()) return null
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return candidates(today.minusDays(1), today, zone, minutes).filter { it <= now }.maxOrNull()
    }

    /** The next scheduled release strictly after [now]. */
    fun nextRelease(now: Long, zone: ZoneId, minutes: List<Int>): Long? {
        if (minutes.isEmpty()) return null
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return candidates(today, today.plusDays(1), zone, minutes).filter { it > now }.minOrNull()
    }

    private fun candidates(a: LocalDate, b: LocalDate, zone: ZoneId, minutes: List<Int>): List<Long> =
        listOf(a, b).flatMap { d ->
            minutes.map { m -> d.atTime(LocalTime.of((m / 60).coerceIn(0, 23), (m % 60).coerceIn(0, 59))).atZone(zone).toInstant().toEpochMilli() }
        }

    /** True while [n] belongs to a digest that has not been released yet. */
    fun isHeld(n: HubNotification, policies: List<AppPolicy>, now: Long, zone: ZoneId): Boolean {
        val p = policyFor(n.packageName, policies) ?: return false
        if (p.mode != DeliveryMode.Digest || p.digestMinutes.isEmpty() || n.isOngoing) return false
        val released = lastRelease(now, zone, p.digestMinutes) ?: return false
        return n.timestamp > released
    }

    fun isSilentHere(n: HubNotification, policies: List<AppPolicy>): Boolean =
        modeFor(n.packageName, policies) == DeliveryMode.SilentInLaunchpad
}

/** Everything the Launchpad list needs, computed once from the raw feed. */
data class LaunchpadView(
    /** Pinned first, then the released digest, then the rest. */
    val sections: List<Section>,
    /** Digest items still waiting for their release time. */
    val held: List<HubNotification>,
    /** Earliest upcoming release among apps that currently hold something. */
    val nextRelease: Long?,
    /** Keys of notifications shown but flagged silent. */
    val silentKeys: Set<String>,
) {
    companion object { const val DIGEST_KEY = "digest" }
}

object LaunchpadViewBuilder {
    fun build(
        items: List<HubNotification>,
        rules: List<Rule>,
        prefs: LaunchpadPrefs,
        policies: List<AppPolicy>,
        snoozedUntil: Map<String, Long>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        digestLabel: String = "Digest",
        todayLabel: String = "Today",
        yesterdayLabel: String = "Yesterday",
        pinnedLabel: String = "Pinned",
        flatLabel: String = "All",
    ): LaunchpadView {
        val awake = items.filter { !SnoozeLogic.isSnoozed(it.key, snoozedUntil, now) }
        val held = awake.filter { DeliveryLogic.isHeld(it, policies, now, zone) }
        val heldKeys = held.map { it.key }.toSet()
        val digestApps = policies.filter { it.mode == DeliveryMode.Digest }.map { it.packageName.lowercase() }.toSet()
        val digestReleased = LaunchpadLogic.visible(
            awake.filter { it.key !in heldKeys && it.packageName.lowercase() in digestApps && !it.isOngoing && !LaunchpadLogic.isPinned(it, rules) },
            rules, now,
        ).sortedByDescending { it.timestamp }
        val digestKeys = digestReleased.map { it.key }.toSet()
        val rest = awake.filter { it.key !in heldKeys && it.key !in digestKeys }
        val base = LaunchpadLogic.apply(rest, rules, prefs, now, zone, LaunchpadLogic::defaultDayLabel, todayLabel, yesterdayLabel, pinnedLabel, flatLabel)
        val sections = ArrayList<Section>()
        val pinnedFirst = base.firstOrNull()?.takeIf { it.pinned }
        if (pinnedFirst != null) sections += pinnedFirst
        if (digestReleased.isNotEmpty()) sections += Section(digestLabel, LaunchpadView.DIGEST_KEY, digestReleased)
        sections += base.filter { it !== pinnedFirst }
        val next = held.mapNotNull { n ->
            DeliveryLogic.policyFor(n.packageName, policies)?.let { DeliveryLogic.nextRelease(now, zone, it.digestMinutes) }
        }.minOrNull()
        val silent = awake.filter { DeliveryLogic.isSilentHere(it, policies) || LaunchpadLogic.isSilent(it, rules) }.map { it.key }.toSet()
        return LaunchpadView(sections, held, next, silent)
    }
}

// ---------------------------------------------------------------- snooze (STR-03 palette)

object SnoozeLogic {
    enum class Preset(val label: String) { TenMinutes("10 minutes"), OneHour("1 hour"), ThisEvening("This evening"), Tomorrow("Tomorrow morning") }

    fun until(p: Preset, now: Long, zone: ZoneId): Long {
        val at = Instant.ofEpochMilli(now).atZone(zone)
        return when (p) {
            Preset.TenMinutes -> now + 10 * 60_000L
            Preset.OneHour -> now + 60 * 60_000L
            Preset.ThisEvening -> {
                val evening = at.toLocalDate().atTime(18, 0).atZone(zone)
                (if (evening.toInstant().toEpochMilli() > now) evening else at.toLocalDate().plusDays(1).atTime(18, 0).atZone(zone)).toInstant().toEpochMilli()
            }
            Preset.Tomorrow -> at.toLocalDate().plusDays(1).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        }
    }

    fun isSnoozed(key: String, snoozed: Map<String, Long>, now: Long): Boolean = (snoozed[key] ?: 0L) > now

    /** Drops entries that have expired or whose notification no longer exists. */
    fun prune(snoozed: Map<String, Long>, activeKeys: Set<String>, now: Long): Map<String, Long> =
        snoozed.filter { (k, until) -> until > now && k in activeKeys }
}

// ---------------------------------------------------------------- the one row menu (STR-03, INP-03)

enum class RowCommand(val label: String) {
    Open("Open"), Dismiss("Dismiss"), MarkRead("Mark as read"), Snooze("Snooze"),
    MuteApp("Mute this app"), MakeRule("Make a rule"), HideApp("Hide this app"),
    PinSender("Pin this sender"), Reply("Reply"),
}

data class RowMenuContext(
    val hasContentIntent: Boolean = true,
    val canDismiss: Boolean = true,
    val unread: Boolean = true,
    val hasSender: Boolean = true,
    val hasPackage: Boolean = true,
    val canReply: Boolean = false,
)

/**
 * One definition feeds every route to a row's actions: the slide-right palette, the long-press menu, the
 * secondary click and the Menu key all call [entries], so they cannot differ (the same idea as INP-03).
 */
object LaunchpadRowMenus {
    fun entries(c: RowMenuContext): List<RowCommand> = buildList {
        add(RowCommand.Open)
        if (c.canReply) add(RowCommand.Reply)
        if (c.canDismiss) add(RowCommand.Dismiss)
        if (c.unread) add(RowCommand.MarkRead)
        add(RowCommand.Snooze)
        if (c.hasPackage) { add(RowCommand.MuteApp); add(RowCommand.HideApp) }
        if (c.hasSender) add(RowCommand.PinSender)
        add(RowCommand.MakeRule)
    }

    fun palette(c: RowMenuContext) = entries(c)
    fun onLongPress(c: RowMenuContext) = entries(c)
    fun onSecondaryClick(c: RowMenuContext) = entries(c)
    fun onMenuKey(c: RowMenuContext) = entries(c)

    /** The four palette cells shown when a row is slid right: the common ones, the rest sit behind More. */
    fun paletteCells(c: RowMenuContext): List<RowCommand> {
        val all = entries(c)
        return listOf(RowCommand.Snooze, RowCommand.MarkRead, RowCommand.MuteApp, RowCommand.Open).filter { it in all }
    }
}

/** Keys on a focused row (STR-03). Android key codes written as ints so this stays JVM-testable. */
object RowKeys {
    const val DEL = 67; const val FORWARD_DEL = 112; const val MENU = 82; const val ENTER = 66; const val NUMPAD_ENTER = 160
    const val DPAD_UP = 19; const val DPAD_DOWN = 20; const val DPAD_LEFT = 21; const val DPAD_RIGHT = 22
    const val ESCAPE = 111; const val MOVE_HOME = 122; const val MOVE_END = 123; const val SPACE = 62; const val F10 = 141

    sealed interface Result {
        data object Primary : Result
        data object Palette : Result
        data object Activate : Result
        data object Close : Result
        data class Move(val delta: Int) : Result
        data object First : Result
        data object Last : Result
        data object Collapse : Result
        data object Expand : Result
    }

    fun map(keyCode: Int, shift: Boolean = false): Result? = when (keyCode) {
        FORWARD_DEL, DEL -> Result.Primary
        MENU -> Result.Palette
        F10 -> if (shift) Result.Palette else null
        ENTER, NUMPAD_ENTER, SPACE -> Result.Activate
        ESCAPE -> Result.Close
        DPAD_DOWN -> Result.Move(1)
        DPAD_UP -> Result.Move(-1)
        DPAD_LEFT -> Result.Collapse
        DPAD_RIGHT -> Result.Expand
        MOVE_HOME -> Result.First
        MOVE_END -> Result.Last
        else -> null
    }
}

// ---------------------------------------------------------------- keyboard focus over groups and rows (STR-08)

sealed interface FocusTarget {
    val key: String
    data class Group(override val key: String) : FocusTarget
    data class Row(override val key: String, val group: String) : FocusTarget
}

/** The flat tab order of Launchpad: each group header, then its rows unless the group is collapsed. */
object LaunchpadFocus {
    fun order(sections: List<Section>, collapsed: Set<String>): List<FocusTarget> = buildList {
        for (s in sections) {
            add(FocusTarget.Group(s.key))
            if (s.key !in collapsed) s.items.forEach { add(FocusTarget.Row(it.key, s.key)) }
        }
    }

    /** Moves by [delta] with no wrap; a lost or null [current] lands on the first target. */
    fun move(order: List<FocusTarget>, current: FocusTarget?, delta: Int): FocusTarget? {
        if (order.isEmpty()) return null
        val i = order.indexOfFirst { it.key == current?.key && it::class == current::class }
        if (i < 0) return order.first()
        return order[(i + delta).coerceIn(0, order.lastIndex)]
    }

    /** Arrow left on a row jumps to its group header, arrow left on a header collapses it. */
    fun owningGroup(order: List<FocusTarget>, current: FocusTarget): FocusTarget? =
        (current as? FocusTarget.Row)?.let { r -> order.firstOrNull { it is FocusTarget.Group && it.key == r.group } }

    /** After the target disappears (dismissed, snoozed), focus goes to the neighbour, never to nothing. */
    fun afterRemoval(before: List<FocusTarget>, removed: FocusTarget, after: List<FocusTarget>): FocusTarget? {
        if (after.isEmpty()) return null
        val i = before.indexOfFirst { it.key == removed.key && it::class == removed::class }.coerceAtLeast(0)
        val survivors = before.drop(i + 1).firstOrNull { b -> after.any { it.key == b.key && it::class == b::class } }
            ?: before.take(i).lastOrNull { b -> after.any { it.key == b.key && it::class == b::class } }
        return survivors?.let { s -> after.first { it.key == s.key && it::class == s::class } } ?: after.first()
    }
}

// ---------------------------------------------------------------- waiting indicator (STR-09)

object WaitingLogic {
    /** Unread items that count towards the left-edge indicator: visible, awake, not held, not silent, not ongoing. */
    fun waiting(
        items: List<HubNotification>, rules: List<Rule>, policies: List<AppPolicy>, readKeys: Set<String>,
        snoozedUntil: Map<String, Long>, now: Long, zone: ZoneId = ZoneId.systemDefault(),
    ): List<HubNotification> = LaunchpadLogic.visible(items, rules, now).filter {
        !it.isOngoing && it.key !in readKeys && !LaunchpadLogic.isSilent(it, rules) &&
            !DeliveryLogic.isSilentHere(it, policies) && !DeliveryLogic.isHeld(it, policies, now, zone) &&
            !SnoozeLogic.isSnoozed(it.key, snoozedUntil, now)
    }

    /** Keys that are in [now] but were not in [before]: the indicator expands briefly for these. */
    fun fresh(before: Set<String>, now: List<HubNotification>): List<HubNotification> = now.filter { it.key !in before }

    const val EXPAND_MS = 3_500L
}

// ---------------------------------------------------------------- state snapshot (STR-04, CFG-01)

/**
 * Everything Launchpad persists, as one JSON document the state store and the config export can carry.
 * Notification content, read state and snoozes are not included (CFG-01: no personal content).
 * Fields from a newer version are kept in [extra] and written back unchanged.
 */
data class LaunchpadRulesSnapshot(
    val version: Int = VERSION,
    val rules: List<Rule> = emptyList(),
    val prefs: LaunchpadPrefs = LaunchpadPrefs(),
    val gestures: Map<LaunchpadGesture, LaunchpadAction> = LaunchpadGestures.defaults,
    val policies: List<AppPolicy> = emptyList(),
    /** Unknown top-level fields from a newer version, as a JSON object, written back unchanged. */
    val extra: String = "{}",
) {
    fun toJson(): String = JSONObject().apply {
        runCatching { JSONObject(extra).let { e -> e.keys().forEach { k -> put(k, e.get(k)) } } }
        put("version", version)
        put("rules", JSONArray(LaunchpadJson.encodeRules(rules)))
        put("prefs", JSONObject(LaunchpadJson.encodePrefs(prefs)))
        put("gestures", LaunchpadGestures.encode(gestures))
        put("policies", PolicyJson.encode(policies))
    }.toString()

    companion object {
        const val VERSION = 1
        private val KNOWN = setOf("version", "rules", "prefs", "gestures", "policies")

        fun fromJson(json: String?): LaunchpadRulesSnapshot {
            if (json.isNullOrBlank()) return LaunchpadRulesSnapshot()
            return runCatching {
                val o = JSONObject(json)
                val extra = JSONObject()
                o.keys().forEach { k -> if (k !in KNOWN) extra.put(k, o.get(k)) }
                LaunchpadRulesSnapshot(
                    version = o.optInt("version", VERSION),
                    rules = LaunchpadJson.decodeRules(o.optJSONArray("rules")?.toString()),
                    prefs = LaunchpadJson.decodePrefs(o.optJSONObject("prefs")?.toString()),
                    gestures = LaunchpadGestures.decode(o.optString("gestures")),
                    policies = PolicyJson.decode(o.optJSONArray("policies")),
                    extra = extra.toString(),
                )
            }.getOrDefault(LaunchpadRulesSnapshot())
        }
    }
}

object PolicyJson {
    fun encode(list: List<AppPolicy>): JSONArray = JSONArray().also { arr ->
        list.forEach { p ->
            arr.put(JSONObject().apply {
                put("pkg", p.packageName)
                put("mode", p.mode.name)
                put("times", JSONArray(p.digestMinutes))
                p.primaryAction?.let { put("primary", it.name) }
            })
        }
    }

    fun decode(arr: JSONArray?): List<AppPolicy> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val pkg = o.optString("pkg").ifBlank { return@mapNotNull null }
            val mode = runCatching { DeliveryMode.valueOf(o.getString("mode")) }.getOrNull() ?: return@mapNotNull null
            val times = o.optJSONArray("times")?.let { t -> (0 until t.length()).map { t.optInt(it, -1) }.filter { it in 0 until 24 * 60 } }
                ?: AppPolicy.DEFAULT_DIGEST_TIMES
            val primary = runCatching { LaunchpadAction.valueOf(o.getString("primary")) }.getOrNull()
                ?.takeIf { it in LaunchpadGestures.allowed(LaunchpadGesture.SwipeItemLeft) }
            AppPolicy(pkg, mode, times.distinct().sorted(), primary)
        }
    }

    fun decode(json: String?): List<AppPolicy> = runCatching { decode(JSONArray(json)) }.getOrDefault(emptyList())
}
