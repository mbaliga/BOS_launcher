package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
/** The 48 dp view switcher (All, Unread, Read, Files, Usage, Timeline and an Options button): the content of the Launchpad's `viewSwitcher` slot. */
@Composable
fun HubSwitcher(state: HubState, modifier: Modifier = Modifier) {
    val lp = LocalLp.current
    Row(modifier.fillMaxWidth().height(48.dp).testTag("hub.switcher"), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            state.views.forEach { v ->
                val sel = v == state.view
                val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
                LaunchedEffect(sel) { if (sel) bring.bringIntoView() } // a view picked by a swipe or a link scrolls into sight
                Box(
                    Modifier.height(48.dp).widthIn(min = 48.dp).bringIntoViewRequester(bring).clickable { state.select(v) }
                        .semantics { role = Role.Tab; selected = sel }.testTag("hub.view.${v.id}"),
                    Alignment.Center,
                ) {
                    Text(
                        L10n.tr(v.label), color = if (sel) lp.ink else lp.dim, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1, modifier = Modifier.padding(horizontal = 7.dp),
                    )
                    if (sel) Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 7.dp).fillMaxWidth().height(3.dp).background(BlueLight))
                }
            }
        }
        Box(
            Modifier.height(48.dp).widthIn(min = 48.dp).clickable { state.optionsOpen = !state.optionsOpen }
                .semantics { role = Role.Button; contentDescription = L10n.tr("Options") }
                .testTag("hub.options"),
            Alignment.Center,
        ) { Text("⋯", color = lp.ink, fontSize = 22.sp) }
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

/** The inline options of the hub (no popup, so nothing is clipped at the screen edge): default view, density, grouping, mark read, delete history. */
@Composable
internal fun HubOptions(state: HubState, onMarkVisibleRead: () -> Unit) {
    val lp = LocalLp.current
    Column(Modifier.fillMaxWidth().background(lp.surface).padding(horizontal = 16.dp, vertical = 4.dp).testTag("hub.optionsPanel")) {
        val isDefault = state.view == state.defaultView
        HubChip(
            if (isDefault) L10n.tr("Default view") + ": " + L10n.tr(state.view.label) else L10n.tr("Set as default view"),
            isDefault, "hub.setDefault",
        ) { state.setDefault() }
        if (state.view.history && !state.listIsLive) {
            Text(L10n.tr("Density"), color = lp.dim, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HubDensity.entries.forEach { d -> HubChip(L10n.tr(d.label), state.density == d, "hub.density.${d.name}") { state.chooseDensity(d) } }
            }
            Text(L10n.tr("Group by"), color = lp.dim, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HubGrouping.entries.forEach { g -> HubChip(L10n.tr(g.label), state.grouping == g, "hub.group.${g.name}") { state.chooseGrouping(g) } }
            }
        }
        if (state.view.history) HubChip(L10n.tr("Mark visible as read"), false, "hub.markVisible") { onMarkVisibleRead(); state.optionsOpen = false }
        if (!state.confirmDelete) HubChip(L10n.tr("Delete history") + "…", false, "hub.deleteHistory") { state.confirmDelete = true }
        else Column(Modifier.testTag("hub.deleteConfirm")) {
            Text(L10n.tr("Delete all notification history? Notifications in the shade stay."), color = lp.ink, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HubChip(L10n.tr("Cancel"), false, "hub.deleteCancel") { state.confirmDelete = false }
                HubChip(L10n.tr("Delete history"), true, "hub.deleteConfirmYes") { state.store?.deleteAll(); state.confirmDelete = false; state.optionsOpen = false; state.undo = null }
            }
        }
    }
}

/** One-tap quick filters of a history view: time range, unread only, then the busiest apps and senders. State is kept per view. */
@Composable
internal fun HubFilterRow(state: HubState, entries: List<HistoryEntry>) {
    val f = state.filters()
    val lp = LocalLp.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("hub.filters"),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        HubChip(L10n.tr("Today"), f.range == HubRange.Today, "hub.f.today") { state.setFilters(f.copy(range = if (f.range == HubRange.Today) HubRange.Any else HubRange.Today)) }
        HubChip(L10n.tr("7 days"), f.range == HubRange.Week, "hub.f.week") { state.setFilters(f.copy(range = if (f.range == HubRange.Week) HubRange.Any else HubRange.Week)) }
        if (state.view == HubView.All) HubChip(L10n.tr("Unread only"), f.unreadOnly, "hub.f.unread") { state.setFilters(f.copy(unreadOnly = !f.unreadOnly)) }
        HubLogic.topApps(entries).forEach { (pkg, label) -> HubChip(label, f.app == pkg, "hub.f.app.$pkg") { state.setFilters(f.copy(app = if (f.app == pkg) null else pkg)) } }
        HubLogic.topSenders(entries).forEach { s -> HubChip(s, f.sender == s, "hub.f.sender.$s") { state.setFilters(f.copy(sender = if (f.sender == s) null else s)) } }
        if (f.active) Text(L10n.tr("Clear filters"), color = BlueLight, fontSize = 13.sp, modifier = Modifier.heightIn(min = 48.dp).clickable { state.clearFilters() }.padding(horizontal = 10.dp).testTag("hub.f.clear").semantics { role = Role.Button }.padding(top = 14.dp))
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
 * The hub's body: the history list of a history view with its quick filters, or [panelBody] for Files, Usage and Timeline. Tapping a row
 * marks it read and expands it; its buttons open it, flip its read mark, or delete it from history.
 */
@Composable
fun HubBody(
    state: HubState, now: Long, zone: ZoneId, is24: Boolean,
    liveKeys: Set<String>, onOpen: (HistoryEntry) -> Unit, onMore: (HistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
    hasAccess: Boolean = true, onRequestAccess: () -> Unit = {}, showOptions: Boolean = true,
    panelBody: @Composable (HubView) -> Unit = {},
) {
    val lp = LocalLp.current
    Column(modifier.fillMaxWidth().fillMaxHeight().testTag("hub.body")) {
        if (showOptions && state.optionsOpen) HubOptions(state) { state.markVisibleRead(now, zone) }
        if (!hasAccess && state.view.history) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("hub.noAccess"), verticalAlignment = Alignment.CenterVertically) {
            Text(L10n.tr("Notification access is off, so new notifications are not recorded."), color = lp.dim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            HubChip(L10n.tr("Turn on"), true, "hub.grant", onRequestAccess)
        }
        if (!state.view.history) { Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().testTag("hub.panel.${state.view.id}")) { panelBody(state.view) }; return@Column }
        val flow = remember(state.store) { state.store?.entries ?: MutableStateFlow(emptyList()) }
        val all by flow.collectAsState()
        val viewScope = remember(all, state.view) { all.filter { (state.view != HubView.Unread || !it.read) && (state.view != HubView.Read || it.read) } }
        val shown = remember(all, state.view, state.filters(), now) { HubLogic.query(all, state.view, state.filters(), now, zone) }
        HubFilterRow(state, viewScope)
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
                        item(key = "h:$key") {
                            val title = when (state.grouping) {
                                HubGrouping.Day -> dayTitle(Instant.ofEpochMilli(list.first().timeMillis).atZone(zone).toLocalDate(), today)
                                HubGrouping.App -> list.first().appLabel.ifBlank { list.first().packageName }
                                HubGrouping.Sender -> list.first().who
                            }
                            Text(
                                title, color = lp.dim, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp).testTag("hub.header.$key"),
                            )
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

@Composable
private fun HubRow(
    e: HistoryEntry, density: HubDensity, stamp: String, open: Boolean, live: Boolean,
    onTap: () -> Unit, onOpen: () -> Unit, onMore: () -> Unit, onToggleRead: () -> Unit, onDelete: () -> Unit,
) {
    val lp = LocalLp.current
    val unread = !e.read
    val minH = when (density) { HubDensity.Compact -> 48.dp; HubDensity.Comfortable -> 64.dp; HubDensity.Expanded -> 88.dp }
    val preview = if (e.title.isNotBlank() && e.title != e.who && density == HubDensity.Expanded) e.title + "\n" + e.text else e.text
    Column(Modifier.fillMaxWidth().testTag("hub.row.${e.id}")) {
        Row(
            Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).heightIn(min = minH).hubTap(onTap).semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = L10n.tr(if (unread) "Unread" else "Read")
            },
        ) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(HubLogic.stripe(e.packageName)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = if (density == HubDensity.Compact) 4.dp else 8.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (density == HubDensity.Compact) e.who + "  " + e.text.lineSequence().firstOrNull().orEmpty() else e.who,
                        color = lp.ink, fontSize = 15.sp, fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(stamp, color = if (unread) lp.ink else lp.dim, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                }
                if (density != HubDensity.Compact) Text(
                    preview, color = if (unread) lp.ink2 else lp.dim, fontSize = 14.sp,
                    maxLines = if (open) 12 else if (density == HubDensity.Expanded) 3 else 2, overflow = TextOverflow.Ellipsis,
                )
                if (density == HubDensity.Expanded || open) Text(
                    (e.appLabel.ifBlank { e.packageName }) + (if (e.conversation.isNotBlank() && e.conversation != e.who) " · " + e.conversation else "") + (if (e.dismissed) " · " + L10n.tr("Cleared from the shade") else ""),
                    color = lp.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (open) Row(Modifier.fillMaxWidth().padding(start = 16.dp).horizontalScroll(rememberScrollState()).testTag("hub.rowActions.${e.id}"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HubChip(L10n.tr("Open"), false, "hub.open.${e.id}", onOpen)
            HubChip(L10n.tr(if (unread) "Mark as read" else "Mark as unread"), false, "hub.toggleRead.${e.id}", onToggleRead)
            if (live) HubChip(L10n.tr("More options"), false, "hub.more.${e.id}", onMore)
            HubChip(L10n.tr("Delete from history"), false, "hub.delete.${e.id}", onDelete)
        }
    }
}
