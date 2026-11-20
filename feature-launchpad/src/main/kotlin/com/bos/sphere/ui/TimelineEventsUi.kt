package com.bos.sphere.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.DayRelative
import com.bos.sphere.core.data.DaySummary
import com.bos.sphere.core.data.TimelineDay
import com.bos.sphere.core.data.TimelineEvent
import com.bos.sphere.core.data.TimelineInputs
import com.bos.sphere.core.data.TimelineKind
import com.bos.sphere.core.data.TimelineModel
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Wave 41 (timelineRich): where the Timeline merge runs, as [CommsMergeConfig] does for Comms: a small input inline, a large one on [dispatcher]. */
internal object TimelineMergeConfig {
    @Volatile var dispatcher: CoroutineDispatcher = Dispatchers.Default
    @Volatile var instantLimit: Int = 400
    fun reset() { dispatcher = Dispatchers.Default; instantLimit = 400 }
}

/** What a tap, a long-press menu and the next-alarm row do. The host decides: the Timeline only reports. */
class TimelineActions(
    /** Open the event's app (a notification through its live content intent when it still has one). A no-op for an app that is gone. */
    val open: (TimelineEvent) -> Unit = {},
    /** Hide the event's app; null hides the menu entry. */
    val hideApp: ((TimelineEvent) -> Unit)? = null,
    /** Show the system alarm app; null makes the next-alarm row inert. */
    val openNextAlarm: (() -> Unit)? = null,
)

/** The next alarm the system exposes, pinned at the top of Today (it is not a past event). */
class NextAlarm(val atMillis: Long)

private sealed interface Flat {
    val key: String
    class Head(val day: TimelineDay, val index: Int, override val key: String) : Flat
    class Alarm(val at: Long, override val key: String) : Flat
    class Ev(val e: TimelineEvent, val index: Int, override val key: String) : Flat
}

/** The days of [inputs]; see [TimelineMergeConfig]. */
@Composable
internal fun rememberTimelineDays(inputs: TimelineInputs, nowMs: Long, zone: ZoneId): List<TimelineDay> {
    val size = inputs.opens.size + inputs.notifications.size + inputs.stored.size
    if (size <= TimelineMergeConfig.instantLimit) return remember(inputs, nowMs, zone) { TimelineModel.build(inputs, nowMs, zone) }
    val days by produceState(emptyList<TimelineDay>(), inputs, nowMs, zone) { value = withContext(TimelineMergeConfig.dispatcher) { TimelineModel.build(inputs, nowMs, zone) } }
    return days
}

private fun flatten(days: List<TimelineDay>, nextAlarm: NextAlarm?, nowMs: Long, zone: ZoneId): List<Flat> {
    val out = ArrayList<Flat>(days.sumOf { it.items.size + 1 } + 2)
    val seen = HashSet<String>()
    fun unique(k: String): String { var u = k; var n = 1; while (!seen.add(u)) u = "$k#${n++}"; return u }
    var evIndex = 0
    var alarmPlaced = nextAlarm == null
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    var shown = days
    if (nextAlarm != null && days.none { it.relative == DayRelative.Today }) {
        // A day without events still carries the alarm: a synthetic Today heading, placed where Today belongs.
        val synth = TimelineDay(today, today.atStartOfDay(zone).toInstant().toEpochMilli(), DayRelative.Today, emptyList(), DaySummary(0, 0, 0, 0))
        val at = days.indexOfFirst { it.startMs < synth.startMs }.let { if (it < 0) days.size else it }
        shown = days.toMutableList().also { it.add(at, synth) }
    }
    shown.forEachIndexed { i, d ->
        out += Flat.Head(d, i, unique("day|${d.startMs}"))
        if (!alarmPlaced && d.relative == DayRelative.Today) { out += Flat.Alarm(nextAlarm!!.atMillis, unique("next-alarm")); alarmPlaced = true }
        for (e in d.items) out += Flat.Ev(e, evIndex++, unique(e.id))
    }
    return out
}

/**
 * The Timeline: [header] (the usage block) then, for each day, a heading with weekday, date and a plain count line, and one row per event with its own
 * icon, title, detail and time. The merge runs off the main thread for a large input; the list is lazy with stable keys. No filter of any kind.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineEventsList(
    inputs: TimelineInputs,
    nowMs: Long,
    is24: Boolean,
    actions: TimelineActions,
    modifier: Modifier = Modifier,
    nextAlarm: NextAlarm? = null,
    zone: ZoneId = ZoneId.systemDefault(),
    header: @Composable () -> Unit = {},
) {
    val lp = LocalLp.current
    val days = rememberTimelineDays(inputs, nowMs, zone)
    val flat = remember(days, nextAlarm?.atMillis, nowMs, zone) { flatten(days, nextAlarm, nowMs, zone) }
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    val locale = Locale.getDefault()
    val timeFmt = remember(is24, locale) { DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", locale) }
    val nowYear = remember(nowMs, zone) { Instant.ofEpochMilli(nowMs).atZone(zone).year }
    val dateFmt = remember(locale) { DateTimeFormatter.ofPattern("EEEE, d MMMM", locale) }
    val dateYearFmt = remember(locale) { DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale) }
    val timeOf: (Long) -> String = remember(timeFmt, zone) { { ms -> timeFmt.format(Instant.ofEpochMilli(ms).atZone(zone)) } }
    LazyColumn(modifier.testTag("timeline.list")) {
        item(key = "timeline.header") { header() }
        if (flat.isEmpty()) item(key = "timeline.empty") {
            Text(
                L10n.tr("Nothing yet. Opened apps, notifications, alerts and alarms appear here."), color = lp.ink2, fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp).testTag("timeline.empty"),
            )
        }
        items(flat, key = { it.key }, contentType = { it::class }) { f ->
            when (f) {
                is Flat.Head -> DayHeading(f.day, f.index, nowYear, dateFmt, dateYearFmt)
                is Flat.Alarm -> NextAlarmRow(f.at, timeFmt, zone, actions.openNextAlarm, locale)
                is Flat.Ev -> EventRow(
                    f.e, f.index, expanded = f.e.id in expanded, timeOf = timeOf, actions = actions,
                    onToggle = { expanded = if (f.e.id in expanded) expanded - f.e.id else expanded + f.e.id },
                )
            }
        }
    }
}

@Composable
private fun DayHeading(day: TimelineDay, index: Int, nowYear: Int, dateFmt: DateTimeFormatter, dateYearFmt: DateTimeFormatter) {
    val lp = LocalLp.current
    val date = (if (day.date.year == nowYear) dateFmt else dateYearFmt).format(day.date)
    val title = when (day.relative) {
        DayRelative.Today -> L10n.tr("Today") + " · " + date
        DayRelative.Yesterday -> L10n.tr("Yesterday") + " · " + date
        DayRelative.Tomorrow -> L10n.tr("Tomorrow") + " · " + date
        DayRelative.Other -> date
    }
    val summary = timelineSummaryText(day.summary)
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp).testTag("timeline.day.$index")) {
        Text(title, color = lp.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        if (summary.isNotEmpty()) Text(summary, color = lp.dim, fontSize = 12.sp, modifier = Modifier.testTag("timeline.summary.$index"))
    }
}

/** "18 opens · 42 notifications · 3 alerts", zero counts left out; plain text, not a filter. */
internal fun timelineSummaryText(s: DaySummary): String = buildList {
    if (s.opens > 0) add(if (s.opens == 1) L10n.tr("1 open") else L10n.tr("%d opens", s.opens))
    if (s.notifications > 0) add(if (s.notifications == 1) L10n.tr("1 notification") else L10n.tr("%d notifications", s.notifications))
    if (s.alerts > 0) add(if (s.alerts == 1) L10n.tr("1 alert") else L10n.tr("%d alerts", s.alerts))
    if (s.packages > 0) add(if (s.packages == 1) L10n.tr("1 app change") else L10n.tr("%d app changes", s.packages))
}.joinToString(" · ")

internal fun timelineDuration(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 1 -> L10n.tr("<1 min")
        min < 60 -> L10n.tr("%d min", min.toInt())
        else -> L10n.tr("%d h %d min", (min / 60).toInt(), (min % 60).toInt())
    }
}

internal fun timelineTitle(e: TimelineEvent): String = when {
    e.isBurst -> L10n.tr("%d notifications from %s", e.count, e.label)
    e.kind == TimelineKind.AppOpened -> L10n.tr("Opened %s", e.label)
    e.kind == TimelineKind.ScheduledLaunch -> L10n.tr("Scheduled: %s", e.title)
    e.kind == TimelineKind.PackageInstalled -> L10n.tr("Installed %s", e.title)
    e.kind == TimelineKind.PackageUpdated -> L10n.tr("Updated %s", e.title)
    e.kind == TimelineKind.PackageRemoved -> L10n.tr("Removed %s", e.title)
    e.kind == TimelineKind.AppHidden -> L10n.tr("Hid %s", e.title)
    e.kind == TimelineKind.AppUnhidden -> L10n.tr("Showed %s", e.title)
    else -> e.title
}

internal fun timelineDetail(e: TimelineEvent, time: String): String = when {
    e.isBurst -> e.detail
    e.kind == TimelineKind.AppOpened -> e.durationMs?.let { L10n.tr("Opened · %s", timelineDuration(it)) } ?: L10n.tr("Opened")
    e.kind == TimelineKind.ScheduledLaunch -> if (e.upcoming) L10n.tr("Scheduled for %s", time) else L10n.tr("Notification sent")
    e.kind.family == TimelineKind.Family.Package -> L10n.tr(TimelineGlyphs.of(e.kind).nameKey)
    e.detail.isBlank() -> L10n.tr("from %s · %s", e.label, L10n.tr(TimelineGlyphs.of(e.kind).nameKey))
    e.title == e.label -> e.detail
    else -> e.label + " · " + e.detail
}

internal fun timelineDescription(e: TimelineEvent, time: String): String =
    listOf(L10n.tr(TimelineGlyphs.of(e.kind).nameKey) + ": " + e.label, time, timelineTitle(e).takeIf { it != e.label }, timelineDetail(e, time).takeIf { it.isNotBlank() })
        .filterNotNull().distinct().joinToString(", ")

@Composable
private fun Tile(icon: TimelineIcon, tag: String, badgePkg: String?, badgeLabel: String) {
    Box(Modifier.size(46.dp), Alignment.Center) {
        Box(Modifier.size(40.dp).background(icon.tile, CircleShape).testTag(tag), Alignment.Center) {
            Image(rememberVectorPainter(icon.glyph), null, Modifier.size(22.dp))
        }
        if (!badgePkg.isNullOrBlank()) Box(Modifier.align(Alignment.BottomEnd).testTag("$tag.badge")) { AppIcon(badgePkg, badgeLabel, 16) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EventRow(e: TimelineEvent, index: Int, expanded: Boolean, timeOf: (Long) -> String, actions: TimelineActions, onToggle: () -> Unit) {
    val lp = LocalLp.current
    val tone = timelineTone(e)
    val time = timeOf(e.timeMs)
    val clip = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    val title = timelineTitle(e)
    val detail = timelineDetail(e, time)
    val desc = timelineDescription(e, time)
    val hasApp = e.packageName.isNotBlank()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = commsTarget()), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).heightIn(min = commsTarget()).testTag("timeline.row.$index")
                    .combinedClickable(onClick = { actions.open(e) }, onLongClick = { menu = true })
                    .semantics(mergeDescendants = true) { contentDescription = desc; role = Role.Button; if (e.unread) stateDescription = L10n.tr("Unread") },
            ) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(14.dp), Alignment.CenterStart) {
                        if (e.unread) Box(Modifier.testTag("timeline.dot.$index").size(CommsStyle.UNREAD_DOT_DP.dp).background(unreadDotColor(lp), CircleShape))
                    }
                    Tile(TimelineGlyphs.of(e.kind), "timeline.icon.${e.kind.name}.$index", e.packageName.takeIf { hasApp && e.kind != TimelineKind.PackageRemoved }, e.label)
                    Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title, color = tone.title, fontWeight = tone.titleWeight, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (e.count > 1 && !e.isBurst) Text("x${e.count}", color = tone.time, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp).testTag("timeline.count.$index"))
                        }
                        if (detail.isNotBlank()) Text(detail, color = tone.sub, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("timeline.detail.$index"))
                    }
                    Text(time, color = tone.time, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(end = 4.dp).testTag("timeline.time.$index"))
                }
                DropdownMenu(menu, { menu = false }) {
                    if (hasApp && e.kind != TimelineKind.PackageRemoved) DropdownMenuItem(text = { Text(L10n.tr("Open app")) }, onClick = { menu = false; actions.open(e) }, modifier = Modifier.testTag("timeline.menu.open"))
                    actions.hideApp?.let { hide -> if (hasApp && e.kind != TimelineKind.PackageRemoved) DropdownMenuItem(text = { Text(L10n.tr("Hide this app")) }, onClick = { menu = false; hide(e) }, modifier = Modifier.testTag("timeline.menu.hide")) }
                    DropdownMenuItem(text = { Text(L10n.tr("Copy text")) }, onClick = { menu = false; clip.setText(AnnotatedString(copyText(e, title, detail))) }, modifier = Modifier.testTag("timeline.menu.copy"))
                }
            }
            if (e.isBurst) Box(
                Modifier.size(commsTarget()).clickable(role = Role.Button, onClick = onToggle).semantics { contentDescription = L10n.tr(if (expanded) "Hide notifications" else "Show notifications") }.testTag("timeline.chevron.$index"),
                Alignment.Center,
            ) { Image(rememberVectorPainter(if (expanded) TimelineGlyphs.ChevronUp else TimelineGlyphs.ChevronDown), null, Modifier.size(20.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(lp.dim)) }
        }
        if (e.isBurst && expanded) Column(Modifier.padding(start = 14.dp).testTag("timeline.children.$index")) {
            e.children.forEachIndexed { j, c -> ChildRow(c, "$index.$j", timeOf(c.timeMs), actions) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChildRow(c: TimelineEvent, tag: String, time: String, actions: TimelineActions) {
    val tone = rememberRowTone(c.unread)
    val lp = LocalLp.current
    val detail = c.detail
    Row(
        Modifier.fillMaxWidth().heightIn(min = commsTarget()).testTag("timeline.child.$tag").combinedClickable(onClick = { actions.open(c) })
            .semantics(mergeDescendants = true) { contentDescription = listOf(L10n.tr(TimelineGlyphs.of(c.kind).nameKey) + ": " + c.label, time, c.title, detail).filter { it.isNotBlank() }.joinToString(", "); role = Role.Button; if (c.unread) stateDescription = L10n.tr("Unread") },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(14.dp), Alignment.CenterStart) { if (c.unread) Box(Modifier.testTag("timeline.child.dot.$tag").size(CommsStyle.UNREAD_DOT_DP.dp).background(unreadDotColor(lp), CircleShape)) }
        Box(Modifier.width(46.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
            Text(c.title, color = tone.title, fontWeight = tone.titleWeight, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail.isNotBlank()) Text(detail, color = tone.sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(time, color = tone.time, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(end = 4.dp))
    }
}

/** Notifications and alerts take the Comms read / unread look; an open, a package change or a scheduled launch has no read state and uses the plain ink. */
@Composable
private fun timelineTone(e: TimelineEvent): CommsRowTone {
    val lp = LocalLp.current
    val tracked = e.kind.family == TimelineKind.Family.Notification || e.kind.family == TimelineKind.Family.Alert
    val tone = rememberRowTone(e.unread)
    return if (tracked) tone else CommsRowTone(false, lp.ink2, FontWeight.Normal, lp.dim, lp.dim, 1f)
}

private fun copyText(e: TimelineEvent, title: String, detail: String): String =
    if (e.isBurst) e.children.joinToString("\n") { listOf(it.title, it.detail).filter { s -> s.isNotBlank() }.joinToString(": ") }
    else listOf(title, detail).filter { it.isNotBlank() }.joinToString("\n")

@Composable
private fun NextAlarmRow(at: Long, timeFmt: DateTimeFormatter, zone: ZoneId, onOpen: (() -> Unit)?, locale: Locale) {
    val lp = LocalLp.current
    val z = Instant.ofEpochMilli(at).atZone(zone)
    val whenText = DateTimeFormatter.ofPattern("EEE", locale).format(z) + " " + timeFmt.format(z)
    val desc = L10n.tr("Next alarm") + ", " + whenText
    Row(
        Modifier.fillMaxWidth().heightIn(min = commsTarget()).testTag("timeline.nextAlarm")
            .then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = desc },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(14.dp))
        Tile(TimelineGlyphs.NextAlarm, "timeline.icon.NextAlarm", null, "")
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
            Text(L10n.tr("Next alarm"), color = lp.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(whenText, color = lp.dim, fontSize = 12.5.sp)
        }
    }
}
