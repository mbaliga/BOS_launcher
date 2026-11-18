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
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.layout.wrapContentHeight
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
import com.bos.sphere.core.data.CommsScale
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.SourceCount
import com.bos.sphere.core.design.L10n

/**
 * Wave 25 (STR-01): the pure maths of the Launchpad menu side panel. [dir] is +1 in left-to-right layouts (the panel comes from the left, the
 * list and the home card leave to the right) and -1 in right-to-left ones. With reduced motion nothing travels: the panel and the home card
 * only fade, as the rest of Launchpad does (NFR-14).
 */
object LaunchpadMenuMotion {
    /** Wave 27: the panel is 58.4 percent of the window (240 dp of the mock-up's 411), at most 380 dp on a wide window. */
    const val MAX_PANEL_DP = CommsStyle.PANEL_MAX_DP
    /** Wave 28: the mock-up shows the shifted screen undimmed; the scrim stays as the tap target that closes the panel. */
    const val SCRIM_ALPHA = 0f

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
    /** Wave 28: the Comms size, and its change (the Options row of the same stepper as Ground Control's). */
    val scalePercent: Int = CommsScale.DEFAULT,
    val onScale: (Int) -> Unit = {},
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
        CommsScaleHost(actions.scalePercent) {
            LazyColumn(Modifier.weight(1f).statusBarsPadding().navigationBarsPadding().testTag("lp.menu.list")) {
                item { MenuLabel(L10n.tr("Group by").uppercase(), CommsStyle.GROUP_BY_BASELINE_DP, CommsStyle.GRID_TOP_DP - CommsStyle.GROUP_BY_BASELINE_DP, "lp.menu.label.groupBy") }
                item { GroupGrid(actions) }
                item { MenuLabel(L10n.tr("Show").uppercase(), CommsStyle.SHOW_BASELINE_FROM_GRID_DP, CommsStyle.SHOW_BELOW_BASELINE_DP, "lp.menu.label.show") }
                item { ShowRow(L10n.tr("All Comms"), selected = actions.filter == null, tag = "lp.menu.show.all", count = actions.counts.sumOf { it.count }, pkg = null) { actions.onFilter(null) } }
                items(actions.counts, key = { "src:" + it.packageName }) { c ->
                    ShowRow(c.appLabel, selected = actions.filter == c.packageName, tag = "lp.menu.show.${c.packageName}", count = c.count, pkg = c.packageName) { actions.onFilter(c.packageName) }
                }
                item { MenuLabel(L10n.tr("Options").uppercase(), CommsStyle.SECTION_GAP_DP + 14f, CommsStyle.SHOW_BELOW_BASELINE_DP, "lp.menu.label.options") }
                optionRows(actions, onClose)
            }
        }
    }
}

/** A small capitals label of the panel (GROUP BY, SHOW, OPTIONS): its baseline [top] dp below whatever is above, and [bottom] dp above whatever follows. */
@Composable
private fun MenuLabel(text: String, top: Float, bottom: Float, tag: String) {
    Text(
        text, color = LocalLp.current.ink, fontSize = CommsStyle.LABEL_SP.sp, maxLines = 1,
        modifier = Modifier.fillMaxWidth().padding(start = CommsStyle.H_PAD_DP.dp).paddingFromBaseline(top = top.dp, bottom = bottom.dp).testTag(tag),
    )
}

/**
 * The GROUP BY grid: 3 columns by 2 rows of icon-only cells (41.4 dp high) with one-dp hairline dividers, between the 32 dp margin and the panel's
 * 19.5 dp end margin; the selected cell is drawn in the full ink colour, the others dim.
 */
@Composable
private fun GroupGrid(actions: MenuActions) {
    val ink = LocalLp.current
    val grid = GroupGridOrder
    Column(Modifier.fillMaxWidth().padding(start = CommsStyle.H_PAD_DP.dp, end = CommsStyle.PANEL_END_PAD_DP.dp).testTag("lp.menu.grid")) {
        for (r in 0 until CommsStyle.GRID_ROWS) {
            if (r > 0) Box(Modifier.fillMaxWidth().height(CommsStyle.HAIR_DP.dp).background(ink.gridLine).testTag("lp.menu.gridLine.h"))
            Row(Modifier.fillMaxWidth().height(CommsStyle.GRID_CELL_H_DP.dp)) {
                for (c in 0 until CommsStyle.GRID_COLUMNS) {
                    val m = grid[r * CommsStyle.GRID_COLUMNS + c]
                    val sel = m == actions.mode
                    if (c > 0) Box(Modifier.width(CommsStyle.HAIR_DP.dp).fillMaxHeight().background(ink.gridLine).testTag("lp.menu.gridLine.v$c"))
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .semantics { this.selected = sel; role = Role.RadioButton; contentDescription = groupModeText(m) }
                            .clickable { actions.onMode(m) }.testTag("lp.menu.mode.${m.name}"),
                        Alignment.Center,
                    ) { GroupModeGlyph(m, if (sel) ink.ink else ink.glyphDim) }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.optionRows(actions: MenuActions, onClose: () -> Unit) {
    val hub = actions.hub
    item { ScaleOption(actions) }
    if (hub != null) {
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
                    L10n.tr("Delete all notification history? Notifications in the shade stay."), color = LocalLp.current.ink2, fontSize = CommsStyle.BODY_SP.sp,
                    modifier = Modifier.padding(start = CommsStyle.H_PAD_DP.dp, end = CommsStyle.PANEL_END_PAD_DP.dp, top = 8.dp, bottom = 8.dp),
                )
                OptionRow(L10n.tr("Cancel"), "lp.menu.opt.deleteCancel") { hub.confirmDelete = false }
                OptionRow(L10n.tr("Delete history"), "lp.menu.opt.deleteYes") { hub.store?.deleteAll(); hub.confirmDelete = false; hub.undo = null; onClose() }
            }
        }
    }
    item { OptionRow(L10n.tr("Groups and rules…"), "lp.menu.groups") { actions.onGroups() } }
    if (actions.canClear) item { OptionRow(L10n.tr("Clear all"), "lp.menu.clear") { actions.onClearAll() } }
}

/** The Comms size option: its label and, beneath it, the minus, percent and plus controls (see [CommsScaleStepper]). */
@Composable
private fun ScaleOption(actions: MenuActions) {
    val ink = LocalLp.current
    Column(Modifier.fillMaxWidth().padding(start = CommsStyle.H_PAD_DP.dp, end = CommsStyle.PANEL_END_PAD_DP.dp).testTag("lp.menu.opt.scale")) {
        Text(L10n.tr("Comms size"), color = ink.ink2, fontSize = CommsStyle.SHOW_SP.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = CommsStyle.SHOW_ROW_DP.dp).wrapContentHeight(Alignment.CenterVertically))
        CommsScaleStepper(actions.scalePercent, actions.onScale)
    }
}

/**
 * A Show row (36 dp, the mock-up's pitch): the app's real coloured icon miniaturised to 16.5 dp (the All Comms row has the comms glyph), a 16 dp gap, the
 * name (17 sp), the count right-aligned and dim.
 */
@Composable
private fun ShowRow(label: String, selected: Boolean, tag: String, count: Int, pkg: String?, onClick: () -> Unit) {
    val ink = LocalLp.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = CommsStyle.SHOW_ROW_DP.dp).then(if (count == 0 && pkg != null) Modifier.graphicsLayer { alpha = 0.45f } else Modifier) // wave 29: an app with nothing under the active filter stays listed, dimmed
            .semantics(mergeDescendants = true) { this.selected = selected; role = Role.RadioButton }
            .clickable(onClick = onClick).testTag(tag).padding(start = CommsStyle.H_PAD_DP.dp, end = CommsStyle.PANEL_END_PAD_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pkg == null) Box(Modifier.size(CommsStyle.SHOW_ICON_DP.dp).testTag("$tag.icon"), Alignment.Center) { GroupModeGlyph(GroupMode.Conversation, ink.ink, CommsStyle.SHOW_ICON_DP) }
        else AppIcon(pkg, label, CommsStyle.SHOW_ICON_DP, Modifier.testTag("$tag.icon"))
        Spacer(Modifier.width(CommsStyle.SHOW_ICON_GAP_DP.dp))
        Text(label, color = ink.ink, fontSize = CommsStyle.SHOW_SP.sp, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(count.toString(), color = ink.count, fontSize = CommsStyle.SHOW_SP.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp).testTag("$tag.count"))
    }
}

/** An Options row: plain text like the Show rows, with an optional value right-aligned in dim. 36 dp high at least (the touch target grows to 48 dp of real screen). */
@Composable
private fun OptionRow(label: String, tag: String, trailing: String = "", onClick: () -> Unit) {
    val ink = LocalLp.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = CommsStyle.SHOW_ROW_DP.dp).semantics(mergeDescendants = true) { role = Role.Button }
            .clickable(onClick = onClick).testTag(tag).padding(start = CommsStyle.H_PAD_DP.dp, end = CommsStyle.PANEL_END_PAD_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = ink.ink2, fontSize = CommsStyle.SHOW_SP.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (trailing.isNotEmpty()) Text(trailing, color = ink.dim, fontSize = CommsStyle.BODY_SP.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
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
