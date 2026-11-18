package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.HistoryEntry
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
/**
 * Wave 27: the tab row of the owner's Comms mock-up: ALL UNREAD FILES TIMELINE in capitals with letter spacing, the selected one in the full ink
 * colour and the others grey (no underline, no options button: Options live in the Comms menu). 48 dp high; it scrolls when the labels do not fit.
 */
@Composable
fun HubSwitcher(state: HubState, modifier: Modifier = Modifier) {
    val lp = LocalLp.current
    Row(
        modifier.fillMaxWidth().height(CommsStyle.TAB_H_DP.dp).horizontalScroll(rememberScrollState()).testTag("hub.switcher"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width((CommsStyle.H_PAD_DP - 9).dp))
        state.tabs.forEach { v ->
            val sel = v == state.view
            val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
            LaunchedEffect(sel) { if (sel) bring.bringIntoView() } // a view picked by a swipe or a link scrolls into sight
            Box(
                Modifier.height(CommsStyle.TAB_H_DP.dp).widthIn(min = 48.dp).bringIntoViewRequester(bring).clickable { state.select(v) }
                    .semantics { role = Role.Tab; selected = sel; contentDescription = L10n.tr(v.label) }.testTag("hub.view.${v.id}"),
                Alignment.Center,
            ) {
                Text(
                    L10n.tr(v.label).uppercase(), color = if (sel) lp.ink else lp.dim, fontSize = CommsStyle.TAB_SP.sp, fontWeight = FontWeight.Normal,
                    letterSpacing = 1.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun HubChip(text: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val lp = LocalLp.current
    Box(Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).clickable(onClick = onClick).semantics { role = Role.Button; this.selected = selected }.testTag(tag), Alignment.Center) { // wave 26: 48 dp both ways, so a short label ("Day") has no narrow, shared hit area
        Text(
            text, color = if (selected) Color.White else lp.ink2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (selected) BlueDark else lp.ink.copy(alpha = 0.07f)).padding(horizontal = 10.dp, vertical = 4.dp).widthIn(max = 180.dp),
        )
    }
}

internal fun stampOf(ts: Long, now: Long, is24: Boolean, zone: ZoneId): String {
    val t = Instant.ofEpochMilli(ts).atZone(zone); val n = Instant.ofEpochMilli(now).atZone(zone)
    val loc = Locale.getDefault()
    return if (t.toLocalDate() == n.toLocalDate()) t.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", loc))
    else t.format(DateTimeFormatter.ofPattern("MMM d", loc))
}

private fun dayTitle(d: LocalDate, today: LocalDate): String = when (d) {
    today -> L10n.tr("Today")
    today.minusDays(1) -> L10n.tr("Yesterday")
    else -> d.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
}

/** Marks everything the current view and filters show as read; the undo bar reverts exactly what changed. */
fun HubState.markVisibleRead(now: Long, zone: ZoneId) {
    val s = store ?: return
    if (!view.history) return
    val ids = HubLogic.query(s.entries.value, view, filters(), now, zone).filter { !it.read }.map { it.id }
    if (ids.isEmpty()) return
    val changed = s.setRead(ids, true)
    if (changed.isNotEmpty()) undo = HubUndo(L10n.tr("Marked %d as read", changed.size), changed)
}

/**
 * The hub's body: the history list of a history view with its quick filters, or [panelBody] for Files and Timeline. Tapping a row
 * marks it read and expands it; its buttons open it, flip its read mark, or delete it from history.
 */
@Composable
fun HubBody(
    state: HubState, now: Long, zone: ZoneId, is24: Boolean,
    liveKeys: Set<String>, onOpen: (HistoryEntry) -> Unit, onMore: (HistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
    hasAccess: Boolean = true, onRequestAccess: () -> Unit = {},
    panelBody: @Composable (HubView) -> Unit = {},
) {
    val lp = LocalLp.current
    Column(modifier.fillMaxWidth().fillMaxHeight().testTag("hub.body")) {
        if (!hasAccess && state.view.history) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("hub.noAccess"), verticalAlignment = Alignment.CenterVertically) {
            Text(L10n.tr("Notification access is off, so new notifications are not recorded."), color = lp.dim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            HubChip(L10n.tr("Turn on"), true, "hub.grant", onRequestAccess)
        }
        if (!state.view.history) { Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().testTag("hub.panel.${state.view.id}")) { panelBody(state.view) }; return@Column }
        val flow = remember(state.store) { state.store?.entries ?: MutableStateFlow(emptyList()) }
        val all by flow.collectAsState()
        val shown = remember(all, state.view, state.filters(), now) { HubLogic.query(all, state.view, state.filters(), now, zone) }
        var expanded by remember { mutableStateOf<String?>(null) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), Alignment.TopCenter) {
                    Text(
                        L10n.tr(when {
                            state.filters().active -> "Nothing matches these filters"
                            state.view == HubView.Unread -> "No unread notifications"
                            state.view == HubView.Read -> "Nothing read yet"
                            else -> "No notifications in history yet"
                        }),
                        color = lp.dim, fontSize = 16.sp, modifier = Modifier.testTag("hub.empty"),
                    )
                }
            } else {
                val today = remember(now, zone) { Instant.ofEpochMilli(now).atZone(zone).toLocalDate() }
                val groups = remember(shown, state.grouping) { shown.groupBy { HubLogic.groupKey(it, state.grouping, now, zone) }.entries.toList() }
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding().testTag("hub.list")) {
                    groups.forEach { (key, list) ->
                        // Wave 26: a day band always shows; an app or sender header only when it holds two or more rows (it carries the count),
                        // because with one row the header would repeat what the row already says.
                        if (HubLogic.showsGroupHeader(state.grouping, list.size)) item(key = "h:$key") {
                            val title = when (state.grouping) {
                                HubGrouping.Day -> dayTitle(Instant.ofEpochMilli(list.first().timeMillis).atZone(zone).toLocalDate(), today)
                                HubGrouping.App -> list.first().appLabel.ifBlank { list.first().packageName }
                                HubGrouping.Sender -> list.first().who
                                HubGrouping.Flat -> ""
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp).testTag("hub.header.$key"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(title, color = lp.dim, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (state.grouping != HubGrouping.Day) Text("${list.size}", color = lp.dim, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp).testTag("hub.headerCount.$key"))
                            }
                        }
                        items(list, key = { it.id }) { e ->
                            HubRow(
                                e, state.density, stampOf(e.timeMillis, now, is24, zone), open = expanded == e.id, live = e.key in liveKeys,
                                onTap = { state.store?.setRead(listOf(e.id), true); expanded = if (expanded == e.id) null else e.id },
                                onOpen = { onOpen(e) }, onMore = { onMore(e) },
                                onToggleRead = { state.store?.setRead(listOf(e.id), !e.read) },
                                onDelete = { state.store?.delete(listOf(e.id)) },
                            )
                        }
                    }
                }
            }
            state.undo?.let { u ->
                LaunchedEffect(u) { delay(6000); if (state.undo === u) state.undo = null }
                Row(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
                        .background(Color(0xE6202228), RoundedCornerShape(50)).padding(start = 18.dp).testTag("hub.undoBar"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(u.message, color = Color.White, fontSize = 13.sp)
                    Text(
                        L10n.tr("Undo"), color = Color(0xFF9DB7FF), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.heightIn(min = 48.dp).clickable { state.store?.setRead(u.ids, false); state.undo = null }
                            .semantics { role = Role.Button }.padding(horizontal = 16.dp).padding(top = 14.dp).testTag("hub.undo"),
                    )
                }
            }
        }
    }
}

/**
 * Wave 27: a history row in the Comms row style, the same as the live list: a 56 dp app icon, the headline (a message's text, else its title) in 24 sp,
 * a grey second line (the sender, else the text) and the time right-aligned on that line, a thin inset hairline below, no stripe. Compact density
 * drops the second line and shrinks the icon; Expanded lets the headline use three lines. Tapping marks the row read and opens its buttons.
 */
@Composable
private fun HubRow(
    e: HistoryEntry, density: HubDensity, stamp: String, open: Boolean, live: Boolean,
    onTap: () -> Unit, onOpen: () -> Unit, onMore: () -> Unit, onToggleRead: () -> Unit, onDelete: () -> Unit,
) {
    val lp = LocalLp.current
    val unread = !e.read
    // The stored entry does not say whether it was a message (the sender falls back to the title), so history always reads text first, then who.
    val headline = e.text.lineSequence().firstOrNull().orEmpty().ifBlank { e.title.ifBlank { e.appLabel } }
    val second = e.who
    val iconDp = if (density == HubDensity.Compact) 40 else CommsStyle.ICON_DP
    Column(Modifier.fillMaxWidth().testTag("hub.row.${e.id}")) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (density == HubDensity.Compact) 56.dp else CommsStyle.ROW_MIN_DP.dp).hubTap(onTap).semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = L10n.tr(if (unread) "Unread" else "Read")
            }.padding(horizontal = CommsStyle.H_PAD_DP.dp, vertical = if (density == HubDensity.Compact) 8.dp else CommsStyle.ROW_V_PAD_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(e.packageName, e.appLabel.ifBlank { e.who }, iconDp, Modifier.testTag("hub.badge.${e.id}"))
            Spacer(Modifier.width(CommsStyle.TEXT_GAP_DP.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    headline, color = if (unread) lp.ink else lp.ink2, fontSize = if (density == HubDensity.Compact) 18.sp else CommsStyle.TITLE_SP.sp,
                    maxLines = if (open) 12 else if (density == HubDensity.Expanded) 3 else 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("hub.title.${e.id}"),
                )
                if (density != HubDensity.Compact || open) Row(verticalAlignment = Alignment.Bottom) {
                    Text(second, color = lp.dim, fontSize = CommsStyle.SUB_SP.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(stamp, color = lp.dim, fontSize = CommsStyle.TIME_SP.sp, maxLines = 1, modifier = Modifier.testTag("hub.time.${e.id}"))
                } else Text(stamp, color = lp.dim, fontSize = CommsStyle.TIME_SP.sp, maxLines = 1, modifier = Modifier.align(Alignment.End).testTag("hub.time.${e.id}"))
            }
        }
        if (open) Text(
            (e.appLabel.ifBlank { e.packageName }) + (if (e.conversation.isNotBlank() && e.conversation != e.who) " · " + e.conversation else "") + (if (e.dismissed) " · " + L10n.tr("Cleared from the shade") else ""),
            color = lp.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = HubStyle.TEXT_START_DP.dp, end = 16.dp),
        )
        if (open) Row(Modifier.fillMaxWidth().padding(start = HubStyle.TEXT_START_DP.dp - 10.dp).horizontalScroll(rememberScrollState()).testTag("hub.rowActions.${e.id}"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HubChip(L10n.tr("Open"), false, "hub.open.${e.id}", onOpen)
            HubChip(L10n.tr(if (unread) "Mark as read" else "Mark as unread"), false, "hub.toggleRead.${e.id}", onToggleRead)
            if (live) HubChip(L10n.tr("More options"), false, "hub.more.${e.id}", onMore)
            HubChip(L10n.tr("Delete from history"), false, "hub.delete.${e.id}", onDelete)
        }
        Box(Modifier.padding(horizontal = CommsStyle.H_PAD_DP.dp).fillMaxWidth().height(0.5.dp).background(lp.line))
    }
}
