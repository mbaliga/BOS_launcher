package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.bos.sphere.ui.viz.STEPS_HEIGHT
import com.bos.sphere.ui.viz.VizBar
import com.bos.sphere.ui.viz.VizStepsGeometry
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

    /** A ranked row, spoken: the app and its time (no percentage: the block shows none). */
    fun rowDescription(r: UsageRow, labelOf: (String) -> String): String = L10n.tr("%s, %s", nameOf(r.key, labelOf), UsageFormat.full(r.ms))

    /** The chart's accessible name: the week, then the selected day with its top apps. */
    fun summary(week: UsageWeek, selected: Int, labelOf: (String) -> String): String {
        val days = week.columns.indices.joinToString("; ") { columnDescription(it, week) }
        val top = week.rows(selected).joinToString("; ") { rowDescription(it, labelOf) }
        val sel = columnDescription(selected, week)
        return L10n.tr("Screen time, last 7 days. %s. Selected: %s. Top apps: %s.", days, sel, top.ifEmpty { L10n.tr("none") })
    }
}

/** The last answer, kept for the process so reopening the Timeline shows the block at once instead of a placeholder (no jump). */
private object UsageUiLast {
    private class Held(val repo: UsageWeekRepo, val ui: UsageUi)
    @Volatile private var held: Held? = null
    fun of(repo: UsageWeekRepo): UsageUi = held?.takeIf { it.repo === repo }?.ui ?: UsageUi.Loading
    fun keep(repo: UsageWeekRepo, ui: UsageUi) { held = Held(repo, ui) }
}

/**
 * Wave 28 (usageTimeline), wave 29 (usageSimplify, owner: "The usage block is a mess and hard to read"): Usage merged into the Timeline as a
 * small visualization above it, in three parts 12 dp apart and a hairline under them: the selected day and its total (stat), the last seven
 * days as thin columns (steps; tap one to select it), and the ranked list of that day's apps (the top four and Other: icon, name, time, and a
 * thin bar as long as the app's share of the day's longest; tap a row to highlight it and dim the others). All in the ASOC viz language
 * (`com.bos.sphere.ui.viz`): one ink, textures instead of hues, no percentages. Everything sizes with the Comms scale and the font scale and the
 * block is content-sized (nothing fixed), so nothing clips or overlaps. While the data loads a quiet placeholder of the same parts holds the
 * height. Without usage access the block offers an "Allow usage access" text button; with nothing recorded it says so in one quiet line.
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
    val ui by produceState(UsageUiLast.of(repo), repo, resumes) {
        value = withContext(Dispatchers.IO) {
            if (!repo.granted()) UsageUi.NoAccess
            else {
                val t = now()
                val week = UsageShaping.week(repo.load(t), zone, Instant.ofEpochMilli(t).atZone(zone).toLocalDate())
                if (week.hasData) UsageUi.Ready(week) else UsageUi.Empty
            }
        }.also { UsageUiLast.keep(repo, it) }
    }
    val gap = CommsStyle.USAGE_GAP_DP.dp
    Column(modifier.fillMaxWidth().padding(top = 4.dp).testTag("usage.block")) {
        when (val u = ui) {
            UsageUi.Loading -> UsagePlaceholder(tone)
            UsageUi.NoAccess -> NoAccessLine(tone)
            UsageUi.Empty -> Text(
                L10n.tr("No screen time recorded yet."), color = tone.ink.copy(alpha = 0.72f), fontSize = CommsStyle.USAGE_DAY_SP.sp,
                modifier = Modifier.heightIn(min = commsTarget()).wrapContentHeight(Alignment.CenterVertically).testTag("usage.empty"),
            )
            is UsageUi.Ready -> UsageReady(u.week, tone, labelOf)
        }
        Spacer(Modifier.height(gap))
        Box(Modifier.fillMaxWidth().height(CommsStyle.HAIR_DP.dp).background(lp.line).testTag("usage.hairline"))
    }
}

/**
 * Loading: the three parts as quiet blocks (a short line, seven stubs, five bars) of the heights the finished block has, so the block does not
 * start at 1 dp and the list below does not jump when the numbers arrive. Nothing animates; a screen reader hears one short phrase.
 */
@Composable
private fun UsagePlaceholder(tone: VizTone) {
    val quiet = tone.ink.copy(alpha = 0.08f)
    val gap = CommsStyle.USAGE_GAP_DP.dp
    val density = LocalDensity.current
    val fs = density.fontScale.coerceIn(1f, 2f)
    val measurer = rememberTextMeasurer()
    // The height of a line of text of [sp], measured (not guessed), so each part is as high as the finished one at any text size and Comms size.
    fun line(sp: Float, weight: FontWeight = FontWeight.Normal) = with(density) { measurer.measure("0", TextStyle(fontSize = sp.sp, fontWeight = weight), maxLines = 1).size.height.toDp() }
    val big = density.fontScale > 1.3f
    val figure = line(if (big) minOf(CommsStyle.USAGE_FIGURE_SP, 22f) else CommsStyle.USAGE_FIGURE_SP, FontWeight.Light)
    val statH = if (big) line(CommsStyle.USAGE_DAY_SP) + figure else figure
    val rowText = line(CommsStyle.NOTE_SP)
    val loading = L10n.tr("Loading screen time")
    Column(Modifier.fillMaxWidth().testTag("usage.loading").semantics(mergeDescendants = true) { contentDescription = loading }) {
        Box(Modifier.fillMaxWidth().height(statH).padding(vertical = 6.dp), contentAlignment = Alignment.CenterStart) { Box(Modifier.fillMaxWidth(0.4f).fillMaxHeight().background(quiet, RoundedCornerShape(4.dp))) }
        Spacer(Modifier.height(gap))
        Row(Modifier.fillMaxWidth().height(STEPS_HEIGHT + (VizStepsGeometry.HEAD + VizStepsGeometry.FOOT).dp * (fs - 1f)), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            listOf(0.5f, 0.8f, 0.35f, 0.6f, 1f, 0.45f, 0.7f).forEach { Box(Modifier.width(14.dp).fillMaxHeight(0.2f + 0.3f * it).background(quiet, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))) }
        }
        Spacer(Modifier.height(gap))
        repeat(UsageShaping.TOP + 1) { i ->
            // The same row as a finished one (a line of text height over the bar), so each is as high as a real row.
            Row(Modifier.fillMaxWidth().heightIn(min = CommsStyle.USAGE_ROW_DP.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(CommsStyle.USAGE_ICON_DP.dp).background(quiet, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Spacer(Modifier.height(rowText))
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.height(CommsStyle.USAGE_BAR_DP.dp).fillMaxWidth(1f - i * 0.17f).background(quiet, RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}

@Composable
private fun NoAccessLine(tone: VizTone) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().testTag("usage.noAccess")) {
        Text(L10n.tr("Screen time"), color = tone.ink.copy(alpha = 0.72f), fontSize = CommsStyle.USAGE_DAY_SP.sp)
        // A text button in the accent (today's colour in the chart), 48 dp of real screen high, reading start aligned.
        Text(
            L10n.tr("Allow usage access"), color = tone.accent, fontSize = CommsStyle.BODY_SP.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.heightIn(min = commsTarget()).clickable(role = Role.Button) {
                GrantHooks.ask(GrantKind.UsageAccess) { GrantSupport.request(ctx, GrantKind.UsageAccess) }
            }.wrapContentHeight(Alignment.CenterVertically).testTag("usage.allow"),
        )
    }
}

@Composable
private fun UsageReady(week: UsageWeek, tone: VizTone, labelOf: (String) -> String) {
    var selected by rememberSaveable { mutableIntStateOf(week.columns.lastIndex) }
    var table by rememberSaveable { mutableStateOf(false) }
    val sel = selected.coerceIn(0, week.columns.lastIndex)
    val rows = week.rows(sel)
    val summary = UsageTexts.summary(week, sel, labelOf)
    val show = L10n.tr("Show as table"); val hide = L10n.tr("Show as chart")
    val gap = CommsStyle.USAGE_GAP_DP.dp
    VizStat(UsageTexts.dayCaption(sel, week), UsageFormat.full(week.total(sel)), tone, Modifier.testTag("usage.stat"), captionSp = CommsStyle.USAGE_DAY_SP.toInt(), figureSp = CommsStyle.USAGE_FIGURE_SP.toInt())
    Spacer(Modifier.height(gap))
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
            sel, { selected = it }, tone, tagPrefix = "usage.steps", minCell = commsTarget(),
        )
    }
    Spacer(Modifier.height(gap))
    // A pick belongs to the day it was made on: another day starts with nothing highlighted.
    var picked by rememberSaveable(sel) { mutableIntStateOf(-1) }
    val pick = com.bos.sphere.ui.viz.clampPick(picked, rows.size)
    Column(Modifier.fillMaxWidth().testTag("usage.rows")) {
        rows.forEachIndexed { i, r -> UsageRowLine(r, i, labelOf, tone, dim = pick >= 0 && pick != i, chosen = pick == i) { picked = if (picked == i) -1 else i } }
    }
}

/** One ranked row: icon, name (ellipsizes), time right-aligned in muted ink, and under them the bar. At least 36 dp high, taller when the text is. */
@Composable
private fun UsageRowLine(r: UsageRow, i: Int, labelOf: (String) -> String, tone: VizTone, dim: Boolean, chosen: Boolean, onClick: () -> Unit) {
    val name = UsageTexts.nameOf(r.key, labelOf)
    val spoken = UsageTexts.rowDescription(r, labelOf)
    Row(
        Modifier.fillMaxWidth().heightIn(min = CommsStyle.USAGE_ROW_DP.dp).testTag("usage.row.$i")
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = spoken; selected = chosen }
            .alpha(if (dim) 0.4f else 1f)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Other has no icon of its own: its slot stays so the names line up.
        Box(Modifier.size(CommsStyle.USAGE_ICON_DP.dp).testTag("usage.row.icon.$i")) { if (!r.isOther) AppIcon(r.key, name, CommsStyle.USAGE_ICON_DP) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = tone.ink, fontSize = CommsStyle.NOTE_SP.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).testTag("usage.row.name.$i"))
                Text(UsageFormat.full(r.ms), color = tone.ink.copy(alpha = 0.72f), fontSize = CommsStyle.NOTE_SP.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 10.dp).testTag("usage.row.value.$i"))
            }
            Spacer(Modifier.height(3.dp))
            VizBar(r.share, r.texture, tone, Modifier.testTag("usage.row.bar.$i"), thickness = CommsStyle.USAGE_BAR_DP.dp)
        }
    }
}
