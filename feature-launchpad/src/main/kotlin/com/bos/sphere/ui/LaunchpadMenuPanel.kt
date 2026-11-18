package com.bos.sphere.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.SourceCount
import com.bos.sphere.core.design.L10n

/**
 * Wave 25 (STR-01): the pure maths of the Launchpad menu side panel. [dir] is +1 in left-to-right layouts (the panel comes from the left, the
 * list and the home card leave to the right) and -1 in right-to-left ones. With reduced motion nothing travels: the panel and the home card
 * only fade, as the rest of Launchpad does (NFR-14).
 */
object LaunchpadMenuMotion {
    /** Wave 27: the panel is 58 percent of the window (the owner's mock-up), at most 380 dp on a wide window. */
    const val MAX_PANEL_DP = 380f
    const val SCRIM_ALPHA = 0.45f

    fun panelWidthDp(windowWidthDp: Float): Float = minOf(MAX_PANEL_DP, windowWidthDp * CommsStyle.PANEL_FRACTION)

    /** The panel's own travel: from fully outside its edge at 0 to in place at 1. */
    fun panelTranslationPx(panelPx: Float, p: Float, reduced: Boolean, dir: Float = 1f): Float = if (reduced) 0f else -dir * panelPx * (1f - p.coerceIn(0f, 1f))
    fun panelAlpha(p: Float, reduced: Boolean): Float = if (reduced) p.coerceIn(0f, 1f) else 1f

    /** The notification list shifts away by the panel's width. */
    fun listShiftPx(panelPx: Float, p: Float, reduced: Boolean, dir: Float = 1f): Float = if (reduced) 0f else dir * panelPx * p.coerceIn(0f, 1f)

    /** The floating home card (already pushed to the far edge) slides fully out of view the same way; with reduced motion it fades and then is moved away once gone. */
    fun cardTranslationPx(windowPx: Float, p: Float, reduced: Boolean, dir: Float = 1f): Float =
        if (reduced) (if (p >= 0.999f) dir * windowPx else 0f) else dir * windowPx * p.coerceIn(0f, 1f)
    fun cardAlpha(p: Float, reduced: Boolean): Float = if (reduced) 1f - p.coerceIn(0f, 1f) else 1f

    fun scrimAlpha(p: Float): Float = SCRIM_ALPHA * p.coerceIn(0f, 1f)
}

/** Open or closed, plus a live 0..1 progress shared by the panel, the list, and (through the host) the home card. */
@Stable
class LaunchpadMenuState internal constructor() {
    private val anim = Animatable(0f)
    var open by mutableStateOf(false)
    internal var reduced: Boolean = false
    val progress: Float get() = anim.value
    /** True while the panel is on screen at all, including during the close animation. */
    val visible: Boolean get() = open || anim.value > 0.001f

    internal suspend fun settle() {
        val target = if (open) 1f else 0f
        // The same ease-out settle as the home card (LaunchpadCardGeometry.SETTLE_MS); reduced motion: a short fade.
        if (reduced) anim.animateTo(target, tween(90))
        else anim.animateTo(target, tween(LaunchpadCardGeometry.SETTLE_MS, easing = CubicBezierEasing(0.15f, 0.85f, 0.25f, 1f)))
    }
}

@Composable
fun rememberLaunchpadMenu(): LaunchpadMenuState {
    val s = remember { LaunchpadMenuState() }
    s.reduced = com.bos.sphere.core.design.rememberMotionPrefs().reduced
    LaunchedEffect(s.open, s.reduced) { s.settle() }
    return s
}

/** Everything the side panel needs, as plain values and callbacks. */
internal class MenuActions(
    val mode: GroupMode,
    val filter: String?,
    val counts: List<SourceCount>,
    val canClear: Boolean,
    val onMode: (GroupMode) -> Unit,
    val onFilter: (String?) -> Unit,
    val onGroups: () -> Unit,
    val onClearAll: () -> Unit,
    /** Wave 27: the hub, for the Options rows (density, default view, read history, mark read, delete history); null on a bare panel. */
    val hub: HubState? = null,
    val onMarkRead: () -> Unit = {},
)

internal fun groupModeText(m: GroupMode) = when (m) {
    GroupMode.Time -> L10n.tr("Time")
    GroupMode.App -> L10n.tr("App")
    GroupMode.Sender -> L10n.tr("Sender")
    GroupMode.Conversation -> L10n.tr("Conversation")
    GroupMode.Flat -> L10n.tr("Flat")
    GroupMode.AppConversation -> L10n.tr("App and conversation")
}

/**
 * The scrim over the shifted list and the panel itself, laid over the notifications layer; the panel is on the physical left (right in RTL).
 * Wave 27 (the owner's Comms menu mock-up): 58 percent wide, #1A1A1A in the dark look; GROUP BY is an icons-only 3 x 2 grid (the labels are
 * only the cells' accessibility descriptions), then "Show" with a row per app (its real coloured icon, the name, the count right-aligned; All Comms
 * first), then the Options rows. A tap on a grouping or an app applies it and closes the panel.
 */
@Composable
internal fun androidx.compose.foundation.layout.BoxScope.LaunchpadMenuLayer(menu: LaunchpadMenuState, rtl: Boolean, actions: MenuActions, onClose: () -> Unit) {
    val hubForReset = actions.hub
    androidx.compose.runtime.LaunchedEffect(menu.open) { if (!menu.open) hubForReset?.confirmDelete = false }
    if (!menu.visible) return
    val dir = if (rtl) -1f else 1f
    val widthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.toFloat()
    val panelDp = LaunchpadMenuMotion.panelWidthDp(widthDp)
    val ink = LocalLp.current
    // The scrim: a tap on the part of the list that is still showing closes the panel.
    Box(
        Modifier.matchParentSize().graphicsLayer { alpha = LaunchpadMenuMotion.scrimAlpha(menu.progress) }.background(Color.Black)
            .testTag("lp.menu.scrim")
            .semantics { contentDescription = L10n.tr("Close menu"); role = Role.Button }
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, onClick = onClose),
    )
    Column(
        Modifier.align(BiasAbsoluteAlignment(if (rtl) 1f else -1f, 0f)).width(panelDp.dp).fillMaxHeight()
            .graphicsLayer {
                translationX = LaunchpadMenuMotion.panelTranslationPx(panelDp * density, menu.progress, menu.reduced, dir)
                alpha = LaunchpadMenuMotion.panelAlpha(menu.progress, menu.reduced)
            }
            .shadow(8.dp).background(ink.surface)
            .testTag("lp.menu.panel")
            .semantics { paneTitle = L10n.tr("Comms menu"); isTraversalGroup = true; traversalIndex = -1f },
    ) {
        LazyColumn(Modifier.weight(1f).statusBarsPadding().navigationBarsPadding().testTag("lp.menu.list")) {
            item { MenuHeading(L10n.tr("Group by").uppercase()) }
            item { GroupGrid(actions) }
            item { MenuHeading(L10n.tr("Show"), top = 28.dp) }
            item { ShowRow(L10n.tr("All Comms"), selected = actions.filter == null, tag = "lp.menu.show.all", count = actions.counts.sumOf { it.count }, pkg = null) { actions.onFilter(null) } }
            items(actions.counts, key = { "src:" + it.packageName }) { c ->
                ShowRow(c.appLabel, selected = actions.filter == c.packageName, tag = "lp.menu.show.${c.packageName}", count = c.count, pkg = c.packageName) { actions.onFilter(c.packageName) }
            }
            item { MenuHeading(L10n.tr("Options"), top = 28.dp) }
            optionRows(actions, onClose)
        }
    }
}

/** The GROUP BY grid: 3 columns by 2 rows of icon-only cells with hairline dividers; the selected cell is drawn in the full ink colour, the others dim. */
@Composable
private fun GroupGrid(actions: MenuActions) {
    val ink = LocalLp.current
    val grid = GroupGridOrder
    Column(Modifier.fillMaxWidth().padding(horizontal = CommsStyle.H_PAD_DP.dp, vertical = 8.dp).testTag("lp.menu.grid")) {
        for (r in 0 until CommsStyle.GRID_ROWS) {
            if (r > 0) Box(Modifier.fillMaxWidth().height1().background(ink.line))
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until CommsStyle.GRID_COLUMNS) {
                    val m = grid[r * CommsStyle.GRID_COLUMNS + c]
                    val sel = m == actions.mode
                    if (c > 0) Box(Modifier.width(0.5.dp).height(CommsStyle.GRID_CELL_H_DP.dp).background(ink.line))
                    Box(
                        Modifier.weight(1f).height(CommsStyle.GRID_CELL_H_DP.dp)
                            .semantics { this.selected = sel; role = Role.RadioButton; contentDescription = groupModeText(m) }
                            .clickable { actions.onMode(m) }.testTag("lp.menu.mode.${m.name}"),
                        Alignment.Center,
                    ) { GroupModeGlyph(m, if (sel) ink.ink else ink.dim.copy(alpha = 0.75f)) }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.optionRows(actions: MenuActions, onClose: () -> Unit) {
    val hub = actions.hub
    if (hub != null) {
        if (hub.view.history && !hub.listIsLive) item {
            OptionRow(L10n.tr("Density"), "lp.menu.opt.density", trailing = L10n.tr(hub.density.label)) { hub.chooseDensity(hub.density.looser ?: HubDensity.Compact) }
        }
        item {
            val isDefault = hub.view == hub.defaultView
            OptionRow(
                if (isDefault) L10n.tr("Default view") else L10n.tr("Set as default view"), "lp.menu.opt.default",
                trailing = if (isDefault) L10n.tr(hub.view.label) else "",
            ) { hub.setDefault(); onClose() }
        }
        item { OptionRow(L10n.tr("Read history"), "lp.menu.opt.read") { hub.select(HubView.Read); onClose() } }
        if (hub.view.history) item { OptionRow(L10n.tr("Mark visible as read"), "lp.menu.opt.markRead") { actions.onMarkRead() } }
        if (!hub.confirmDelete) item { OptionRow(L10n.tr("Delete history") + "…", "lp.menu.opt.delete") { hub.confirmDelete = true } }
        else item {
            Column(Modifier.testTag("hub.deleteConfirm")) {
                Text(
                    L10n.tr("Delete all notification history? Notifications in the shade stay."), color = LocalLp.current.ink2, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = CommsStyle.H_PAD_DP.dp, vertical = 8.dp),
                )
                OptionRow(L10n.tr("Cancel"), "lp.menu.opt.deleteCancel") { hub.confirmDelete = false }
                OptionRow(L10n.tr("Delete history"), "lp.menu.opt.deleteYes") { hub.store?.deleteAll(); hub.confirmDelete = false; hub.undo = null; onClose() }
            }
        }
    }
    item { OptionRow(L10n.tr("Groups and rules…"), "lp.menu.groups") { actions.onGroups() } }
    if (actions.canClear) item { OptionRow(L10n.tr("Clear all"), "lp.menu.clear") { actions.onClearAll() } }
}

private fun Modifier.height1(): Modifier = this.height(0.5.dp)

@Composable
private fun MenuHeading(text: String, top: androidx.compose.ui.unit.Dp = 24.dp) {
    Text(
        text, color = LocalLp.current.ink2, fontSize = 15.sp, letterSpacing = 0.5.sp,
        modifier = Modifier.fillMaxWidth().padding(start = CommsStyle.H_PAD_DP.dp, end = 16.dp, top = top, bottom = 8.dp),
    )
}

/** A Show row: the app's real coloured icon miniaturised to 24 dp (the All Comms row has the comms glyph), an 8 dp gap, the name, the count right-aligned and dim. */
@Composable
private fun ShowRow(label: String, selected: Boolean, tag: String, count: Int, pkg: String?, onClick: () -> Unit) {
    val ink = LocalLp.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = CommsStyle.SHOW_ROW_MIN_DP.dp)
            .semantics(mergeDescendants = true) { this.selected = selected; role = Role.RadioButton }
            .clickable(onClick = onClick).testTag(tag).padding(horizontal = CommsStyle.H_PAD_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pkg == null) Box(Modifier.size(CommsStyle.SHOW_ICON_DP.dp), Alignment.Center) { GroupModeGlyph(GroupMode.Conversation, ink.ink) }
        else AppIcon(pkg, label, CommsStyle.SHOW_ICON_DP, Modifier.testTag("$tag.icon"))
        Spacer(Modifier.width(CommsStyle.SHOW_ICON_GAP_DP.dp))
        Text(label, color = ink.ink, fontSize = 18.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(count.toString(), color = ink.dim, fontSize = 18.sp, maxLines = 1, modifier = Modifier.padding(start = 12.dp).testTag("$tag.count"))
    }
}

/** An Options row: plain text like the Show rows, with an optional value right-aligned in dim. At least 48 dp high. */
@Composable
private fun OptionRow(label: String, tag: String, trailing: String = "", onClick: () -> Unit) {
    val ink = LocalLp.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) { role = Role.Button }
            .clickable(onClick = onClick).testTag(tag).padding(horizontal = CommsStyle.H_PAD_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = ink.ink2, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (trailing.isNotEmpty()) Text(trailing, color = ink.dim, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
    }
}

/** The host's half of the menu motion: the floating home card (and its return zone) leaves the screen as the menu panel arrives, and comes back with it. */
fun Modifier.launchpadMenuAside(menu: LaunchpadMenuState, windowPx: Float, rtl: Boolean): Modifier = this.graphicsLayer {
    val p = menu.progress
    if (p > 0.001f) {
        translationX = LaunchpadMenuMotion.cardTranslationPx(windowPx, p, menu.reduced, if (rtl) -1f else 1f)
        alpha = LaunchpadMenuMotion.cardAlpha(p, menu.reduced)
    }
}
