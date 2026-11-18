package com.bos.sphere.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bos.sphere.core.design.GrantHooks
import com.bos.sphere.core.design.GrantKind
import com.bos.sphere.core.design.L10n
import com.bos.sphere.ui.viz.VizKey
import com.bos.sphere.ui.viz.VizLegend
import com.bos.sphere.ui.viz.VizPart
import com.bos.sphere.ui.viz.VizStack
import com.bos.sphere.ui.viz.rememberStackLabelFits
import com.bos.sphere.ui.viz.VizStat
import com.bos.sphere.ui.viz.VizStepColumn
import com.bos.sphere.ui.viz.VizSteps
import com.bos.sphere.ui.viz.VizTable
import com.bos.sphere.ui.viz.VizTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** What the block shows: nothing yet, the grant action, one quiet line, or the week. */
internal sealed interface UsageUi {
    data object Loading : UsageUi
    data object NoAccess : UsageUi
    data object Empty : UsageUi
    class Ready(val week: UsageWeek) : UsageUi
}

/** Wave 28: the block's text, kept pure so the screen reader summary is tested without a screen. */
internal object UsageTexts {
    fun dayCaption(index: Int, week: UsageWeek): String = when (index) {
        week.columns.lastIndex -> L10n.tr("Today")
        week.columns.lastIndex - 1 -> L10n.tr("Yesterday")
        else -> week.columns.getOrNull(index)?.dayName.orEmpty()
    }

    fun nameOf(key: String, labelOf: (String) -> String): String = if (key.isEmpty()) L10n.tr("Other") else labelOf(key).ifBlank { key }

    fun columnDescription(index: Int, week: UsageWeek): String = L10n.tr("%s, %s", dayCaption(index, week), UsageFormat.full(week.total(index)))

    fun partDescription(p: UsagePart, labelOf: (String) -> String): String = L10n.tr("%s, %s, %d percent", nameOf(p.key, labelOf), UsageFormat.full(p.ms), p.percent)

    /** The chart's accessible name: the week, then the selected day with its top apps. */
    fun summary(week: UsageWeek, selected: Int, labelOf: (String) -> String): String {
        val days = week.columns.indices.joinToString("; ") { columnDescription(it, week) }
        val top = week.parts(selected).joinToString("; ") { partDescription(it, labelOf) }
        val sel = columnDescription(selected, week)
        return L10n.tr("Screen time, last 7 days. %s. Selected: %s. Top apps: %s.", days, sel, top.ifEmpty { L10n.tr("none") })
    }
}

/**
 * Wave 28 (usageTimeline, owner): Usage merged into Timeline as a small visualization above it. A header with the day's total (stat), a
 * steps chart of the last seven days (tap a column to select it), and one stack of the selected day by app with its legend, all in the
 * ASOC viz language (`com.bos.sphere.ui.viz`): one ink, textures instead of hues. Without usage access the block offers the existing "Allow
 * usage access" action instead of the chart; with nothing recorded it says so in one quiet line.
 *
 * [labelOf] names a package (the app's launcher label); the data comes from [repo], read off the main thread and again when the screen
 * resumes (the user may have just granted the access).
 */
@Composable
fun UsageTimelineBlock(
    repo: UsageWeekRepo,
    labelOf: (String) -> String,
    modifier: Modifier = Modifier,
    now: () -> Long = System::currentTimeMillis,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val lp = LocalLp.current
    val tone = remember(lp) { VizTone.of(lp.ink, lp.bg) }
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val ui by produceState<UsageUi>(UsageUi.Loading, repo, resumes) {
        value = withContext(Dispatchers.IO) {
            if (!repo.granted()) UsageUi.NoAccess
            else {
                val t = now()
                val week = UsageShaping.week(repo.load(t), zone, Instant.ofEpochMilli(t).atZone(zone).toLocalDate())
                if (week.hasData) UsageUi.Ready(week) else UsageUi.Empty
            }
        }
    }
    Column(modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp).testTag("usage.block")) {
        when (val u = ui) {
            UsageUi.Loading -> Spacer(Modifier.height(1.dp))
            UsageUi.NoAccess -> NoAccessLine(tone)
            UsageUi.Empty -> Text(L10n.tr("No screen time recorded yet."), color = tone.ink.copy(alpha = 0.72f), fontSize = 13.sp, modifier = Modifier.heightIn(min = 32.dp).testTag("usage.empty"))
            is UsageUi.Ready -> UsageReady(u.week, tone, labelOf)
        }
    }
}

@Composable
private fun NoAccessLine(tone: VizTone) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().testTag("usage.noAccess"), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(L10n.tr("Screen time"), color = tone.ink.copy(alpha = 0.72f), fontSize = 13.sp)
        Text(
            L10n.tr("Allow usage access"), color = tone.ink, fontSize = 14.sp,
            modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) {
                GrantHooks.ask(GrantKind.UsageAccess) { GrantSupport.request(ctx, GrantKind.UsageAccess) }
            }.wrapContentHeight(Alignment.CenterVertically).padding(start = 12.dp).testTag("usage.allow"),
        )
    }
}

@Composable
private fun UsageReady(week: UsageWeek, tone: VizTone, labelOf: (String) -> String) {
    var selected by rememberSaveable { mutableIntStateOf(week.columns.lastIndex) }
    var table by rememberSaveable { mutableStateOf(false) }
    val sel = selected.coerceIn(0, week.columns.lastIndex)
    val parts = week.parts(sel)
    val summary = UsageTexts.summary(week, sel, labelOf)
    val show = L10n.tr("Show as table"); val hide = L10n.tr("Show as chart")
    VizStat(UsageTexts.dayCaption(sel, week), UsageFormat.full(week.total(sel)), tone, Modifier.testTag("usage.stat"))
    Box(
        Modifier.fillMaxWidth().testTag("usage.chart").semantics {
            contentDescription = summary
            customActions = listOf(CustomAccessibilityAction(if (table) hide else show) { table = !table; true })
        },
    ) {
        if (table) VizTable(
            listOf(L10n.tr("Day"), L10n.tr("Screen time")),
            week.columns.indices.map { listOf(UsageTexts.dayCaption(it, week), UsageFormat.full(week.total(it))) }, tone, tagPrefix = "usage.table",
        ) else VizSteps(
            week.columns.mapIndexed { i, c -> VizStepColumn(c.initial, c.totalMs.toDouble(), UsageFormat.compact(c.totalMs), UsageTexts.columnDescription(i, week), accent = c.isToday) },
            sel, { selected = it }, tone, tagPrefix = "usage.steps",
        )
    }
    Spacer(Modifier.height(4.dp))
    // Wave 29: the percentage is printed on the stack parts wide enough for it and in the legend line of the others: one place each, never both.
    var picked by rememberSaveable(sel) { mutableIntStateOf(-1) }
    val vparts = parts.map { VizPart(it.ms.toDouble(), it.texture, "${it.percent}%", UsageTexts.partDescription(it, labelOf), it.key) }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fits = rememberStackLabelFits(vparts, maxWidth)
        Column(Modifier.fillMaxWidth()) {
            VizStack(vparts, tone, tagPrefix = "usage.stack", labelFits = fits, highlight = picked)
            Spacer(Modifier.height(8.dp))
            VizLegend(
                parts.mapIndexed { i, it -> VizKey(it.texture, UsageTexts.nameOf(it.key, labelOf), UsageFormat.full(it.ms), it.key, percent = if (fits.getOrNull(i) == true) null else "${it.percent}%") }, tone,
                Modifier, tagPrefix = "usage.legend", textSp = CommsStyle.NOTE_SP,
                selected = picked, onSelect = { picked = if (picked == it) -1 else it },
                lead = { k -> if (k.tag.isNotEmpty()) AppIcon(k.tag, k.label, 16) },
            )
        }
    }
}
