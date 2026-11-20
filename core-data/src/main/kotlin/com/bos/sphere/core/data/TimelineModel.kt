package com.bos.sphere.core.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Wave 41 (timelineRich): one row of the Timeline. [count] is more than 1 for a collapsed run of opens or a burst of notifications ([children] then
 * holds each one, newest first). [key] is what opening it needs: the app key of an open, the notification key of a notification.
 */
data class TimelineEvent(
    val id: String,
    val timeMs: Long,
    val kind: TimelineKind,
    val packageName: String,
    val label: String,
    val title: String,
    val detail: String = "",
    val unread: Boolean = false,
    val count: Int = 1,
    val durationMs: Long? = null,
    val key: String = "",
    /** A scheduled launch that has not fired yet. */
    val upcoming: Boolean = false,
    val children: List<TimelineEvent> = emptyList(),
) {
    val isBurst: Boolean get() = children.isNotEmpty()
}

enum class DayRelative { Today, Yesterday, Tomorrow, Other }

/** The plain counts under a day heading. Not a filter. */
data class DaySummary(val opens: Int, val notifications: Int, val alerts: Int, val packages: Int) {
    val isEmpty: Boolean get() = opens + notifications + alerts + packages == 0
}

data class TimelineDay(val date: LocalDate, val startMs: Long, val relative: DayRelative, val items: List<TimelineEvent>, val summary: DaySummary)

/** An app session from the usage events: [packageName] was in the foreground from [startMs] to [endMs]. */
data class UsageSession(val packageName: String, val startMs: Long, val endMs: Long) { val durationMs: Long get() = endMs - startMs }

/** One raw usage event: [resumed] true for an activity coming to the foreground, false for leaving it. */
data class RawUsageEvent(val packageName: String, val timeMs: Long, val resumed: Boolean)

/** Pairs foreground and background usage events into sessions (a pause followed by a resume of the same app within [mergeGapMs] stays one session). */
class UsageSessionIndex(sessions: List<UsageSession>) {
    private val byPackage: Map<String, List<UsageSession>> = sessions.groupBy { it.packageName }.mapValues { (_, l) -> l.sortedBy { it.startMs } }

    /** The length of the session of [packageName] that an open at [timeMs] started: its start lies within [slackBeforeMs] before to [slackAfterMs] after the open. */
    fun durationOf(packageName: String, timeMs: Long, slackBeforeMs: Long = 15_000L, slackAfterMs: Long = 45_000L): Long? {
        val list = byPackage[packageName] ?: return null
        return list.filter { it.startMs in (timeMs - slackAfterMs)..(timeMs + slackBeforeMs) }
            .minByOrNull { kotlin.math.abs(it.startMs - timeMs) }?.durationMs?.takeIf { it > 0 }
    }

    companion object {
        fun from(events: List<RawUsageEvent>, mergeGapMs: Long = 3_000L): UsageSessionIndex {
            val open = HashMap<String, Long>()
            val out = ArrayList<UsageSession>()
            for (e in events.sortedBy { it.timeMs }) {
                if (e.resumed) { if (e.packageName !in open) open[e.packageName] = e.timeMs }
                else open.remove(e.packageName)?.let { s -> if (e.timeMs > s) out += UsageSession(e.packageName, s, e.timeMs) }
            }
            val merged = ArrayList<UsageSession>()
            for (s in out.sortedWith(compareBy({ it.packageName }, { it.startMs }))) {
                val last = merged.lastOrNull()
                if (last != null && last.packageName == s.packageName && s.startMs - last.endMs <= mergeGapMs) merged[merged.size - 1] = last.copy(endMs = maxOf(last.endMs, s.endMs))
                else merged += s
            }
            return UsageSessionIndex(merged)
        }
    }
}

/** Which kind a recorded notification is, from its category, priority, package and text. Everything is a plain rule, nothing is learned. */
object TimelineClassifier {
    private val clockPackages = setOf(
        "com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage", "com.oneplus.deskclock",
        "com.huawei.deskclock", "com.miui.clock", "com.coloros.alarmclock", "com.asus.deskclock",
    )
    private val systemPackages = setOf("android", "com.android.systemui", "com.android.settings", "com.google.android.gms", "com.google.android.apps.safetyhub", "com.samsung.android.lool")
    private val missedWords = Regex("missed|manqu|verpasst|perdid|perso|未接|不在着信|فائت|मिस", RegexOption.IGNORE_CASE)

    fun classify(category: String?, priority: Int, packageName: String, title: String = "", text: String = ""): TimelineKind {
        val c = category.orEmpty().lowercase()
        return when {
            c == "alarm" || packageName in clockPackages -> TimelineKind.Alarm
            c == "call" -> if (missedWords.containsMatchIn(title) || missedWords.containsMatchIn(text)) TimelineKind.MissedCall else TimelineKind.Call
            c == "missed_call" -> TimelineKind.MissedCall
            c == "msg" -> TimelineKind.Message
            c == "email" -> TimelineKind.Email
            c == "event" || c == "reminder" -> TimelineKind.Calendar
            c == "progress" -> TimelineKind.Download
            c == "err" -> TimelineKind.Alert
            c == "sys" || packageName in systemPackages -> TimelineKind.Security
            c == "promo" || c == "recommendation" -> TimelineKind.Promo
            packageName.contains("downloads") || packageName.contains("download") -> TimelineKind.Download
            priority >= 1 -> TimelineKind.Alert
            else -> TimelineKind.Notification
        }
    }
}

/** What the Timeline reads. Every list may be empty; nothing here is copied from another store. */
class TimelineInputs(
    val opens: List<LaunchEvent> = emptyList(),
    val notifications: List<HistoryEntry> = emptyList(),
    val stored: List<StoredTimelineEvent> = emptyList(),
    val upcoming: List<UpcomingLaunch> = emptyList(),
    /** App key (component#user) to label, and package to label; either may miss an app that is gone. */
    val labelOfKey: Map<String, String> = emptyMap(),
    val labelOfPackage: Map<String, String> = emptyMap(),
    val sessions: UsageSessionIndex? = null,
)

data class UpcomingLaunch(val id: String, val label: String, val atMillis: Long, val packageName: String = "")

/** The merge: sources to day sections, newest first. Pure; runs off the main thread. */
object TimelineModel {
    const val COLLAPSE_OPENS_MS = 2 * 60_000L
    const val BURST_MS = 5 * 60_000L
    const val MAX_EVENTS = 5000

    fun packageOfKey(key: String): String = key.substringBefore('/').substringBefore('#')

    fun build(input: TimelineInputs, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<TimelineDay> {
        val events = ArrayList<TimelineEvent>()
        events += collapseOpens(input)
        events += bursts(notificationEvents(input))
        for (s in input.stored) {
            val label = input.labelOfPackage[s.packageName] ?: s.label.ifBlank { s.packageName }
            events += TimelineEvent(s.id, s.timeMillis, s.kind, s.packageName, label, label, s.detail)
        }
        for (u in input.upcoming) if (u.atMillis >= nowMs) events += TimelineEvent(
            "up|${u.id}", u.atMillis, TimelineKind.ScheduledLaunch, u.packageName, u.label, u.label, upcoming = true,
        )
        val sorted = events.sortedWith(compareByDescending<TimelineEvent> { it.timeMs }.thenByDescending { it.id }).take(MAX_EVENTS)
        return days(sorted, nowMs, zone)
    }

    /** The opens, newest first, with consecutive opens of one app less than [COLLAPSE_OPENS_MS] apart as one event with a count. */
    fun collapseOpens(input: TimelineInputs): List<TimelineEvent> {
        val asc = input.opens.sortedBy { it.timeMillis }
        val out = ArrayList<TimelineEvent>()
        var run = ArrayList<LaunchEvent>()
        fun flush() {
            if (run.isEmpty()) return
            val first = run.first(); val last = run.last()
            val pkg = packageOfKey(first.key)
            val label = input.labelOfKey[first.key] ?: input.labelOfPackage[pkg] ?: pkg
            val durations = run.mapNotNull { input.sessions?.durationOf(pkg, it.timeMillis) }
            out += TimelineEvent(
                "open|${first.timeMillis}|${first.key}", last.timeMillis, TimelineKind.AppOpened, pkg, label, label,
                count = run.size, durationMs = if (durations.isEmpty()) null else durations.sum(), key = last.key,
            )
            run = ArrayList()
        }
        for (e in asc) {
            val prev = run.lastOrNull()
            if (prev != null && packageOfKey(prev.key) == packageOfKey(e.key) && e.timeMillis - prev.timeMillis <= COLLAPSE_OPENS_MS) run += e else { flush(); run += e }
        }
        flush()
        return out.asReversed()
    }

    fun notificationEvents(input: TimelineInputs): List<TimelineEvent> = input.notifications.map { h ->
        val label = input.labelOfPackage[h.packageName] ?: h.appLabel.ifBlank { h.packageName }
        TimelineEvent(
            h.id, h.timeMillis, TimelineClassifier.classify(h.category, h.priority, h.packageName, h.title, h.text), h.packageName, label,
            title = h.who, detail = h.text.ifBlank { h.title }.let { if (it == h.who) "" else it },
            unread = !h.read, key = h.key,
        )
    }

    /** Notifications of the same app, [BURST_MS] or less apart, become one event holding them (newest first); alerts, calls and alarms stay single. */
    fun bursts(events: List<TimelineEvent>): List<TimelineEvent> {
        val single = ArrayList<TimelineEvent>()
        val groupable = events.filter { if (it.kind.family == TimelineKind.Family.Notification && it.packageName.isNotEmpty()) true else { single += it; false } }
        val out = ArrayList<TimelineEvent>(single)
        for ((_, list) in groupable.groupBy { it.packageName }) {
            val asc = list.sortedWith(compareBy({ it.timeMs }, { it.id }))
            var run = ArrayList<TimelineEvent>()
            fun flush() {
                if (run.isEmpty()) return
                if (run.size == 1) out += run[0] else {
                    val newest = run.last()
                    val kinds = run.map { it.kind }.toSet()
                    out += TimelineEvent(
                        "burst|${run.first().id}", newest.timeMs, if (kinds.size == 1) kinds.first() else TimelineKind.Notification, newest.packageName, newest.label,
                        title = newest.label, detail = newest.title, unread = run.any { it.unread }, count = run.size, key = newest.key, children = run.asReversed(),
                    )
                }
                run = ArrayList()
            }
            for (e in asc) { val prev = run.lastOrNull(); if (prev != null && e.timeMs - prev.timeMs > BURST_MS) flush(); run += e }
            flush()
        }
        return out
    }

    fun days(sortedNewestFirst: List<TimelineEvent>, nowMs: Long, zone: ZoneId): List<TimelineDay> {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val out = ArrayList<TimelineDay>()
        var date: LocalDate? = null
        var bucket = ArrayList<TimelineEvent>()
        fun flush() {
            val d = date ?: return
            val diff = java.time.temporal.ChronoUnit.DAYS.between(today, d)
            out += TimelineDay(d, d.atStartOfDay(zone).toInstant().toEpochMilli(), when (diff) { 0L -> DayRelative.Today; -1L -> DayRelative.Yesterday; 1L -> DayRelative.Tomorrow; else -> DayRelative.Other }, bucket, summarize(bucket))
            bucket = ArrayList()
        }
        for (e in sortedNewestFirst) {
            val d = Instant.ofEpochMilli(e.timeMs).atZone(zone).toLocalDate()
            if (d != date) { flush(); date = d }
            bucket += e
        }
        flush()
        return out
    }

    fun summarize(items: List<TimelineEvent>): DaySummary {
        var opens = 0; var notifications = 0; var alerts = 0; var packages = 0
        for (e in items) when (e.kind.family) {
            TimelineKind.Family.Open -> if (!e.upcoming) opens += e.count
            TimelineKind.Family.Notification -> notifications += e.count
            TimelineKind.Family.Alert -> alerts += e.count
            TimelineKind.Family.Package -> packages += e.count
            TimelineKind.Family.Other -> {}
        }
        return DaySummary(opens, notifications, alerts, packages)
    }
}
