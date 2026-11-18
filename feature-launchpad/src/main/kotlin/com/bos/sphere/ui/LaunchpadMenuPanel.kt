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
    const val MAX_PANEL_DP = 320f
    const val SCRIM_ALPHA = 0.45f

    fun panelWidthDp(windowWidthDp: Float): Float = minOf(MAX_PANEL_DP, windowWidthDp * 0.86f)

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
)

internal fun groupModeText(m: GroupMode) = when (m) {
    GroupMode.Time -> L10n.tr("Time")
    GroupMode.App -> L10n.tr("App")
    GroupMode.Sender -> L10n.tr("Sender")
    GroupMode.Conversation -> L10n.tr("Conversation")
    GroupMode.Flat -> L10n.tr("Flat")
    GroupMode.AppConversation -> L10n.tr("App and conversation")
}

/** The scrim over the shifted list and the panel itself, laid over the notifications layer; the panel is on the physical left (right in RTL). */
@Composable
internal fun androidx.compose.foundation.layout.BoxScope.LaunchpadMenuLayer(menu: LaunchpadMenuState, rtl: Boolean, actions: MenuActions, onClose: () -> Unit) {
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
        Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp).padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(L10n.tr("Comms menu"), color = ink.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .semantics { contentDescription = L10n.tr("Close menu") }
                    .clickable(onClickLabel = L10n.tr("Close menu"), role = Role.Button, onClick = onClose).testTag("lp.menu.close"),
                Alignment.Center,
            ) { CloseIcon(ink.ink) }
        }
        Box(Modifier.fillMaxWidth().height1().background(ink.line))
        LazyColumn(Modifier.weight(1f).navigationBarsPadding().testTag("lp.menu.list")) {
            item { MenuHeading(L10n.tr("Group by")) }
            items(GroupMode.entries.toList(), key = { "mode:" + it.name }) { m ->
                MenuRow(groupModeText(m), selected = m == actions.mode, tag = "lp.menu.mode.${m.name}", radio = true) { actions.onMode(m) }
            }
            item { MenuHeading(L10n.tr("Show")) }
            item { MenuRow(L10n.tr("All"), selected = actions.filter == null, tag = "lp.menu.show.all", radio = true, trailing = actions.counts.sumOf { it.count }.toString()) { actions.onFilter(null) } }
            items(actions.counts, key = { "src:" + it.packageName }) { c ->
                MenuRow(c.appLabel, selected = actions.filter == c.packageName, tag = "lp.menu.show.${c.packageName}", radio = true, trailing = c.count.toString(), pkg = c.packageName) { actions.onFilter(c.packageName) }
            }
            item { Box(Modifier.fillMaxWidth().padding(top = 8.dp).height1().background(ink.line)) }
            item { MenuRow(L10n.tr("Groups and rules…"), selected = false, tag = "lp.menu.groups", radio = false) { actions.onGroups() } }
            if (actions.canClear) item { MenuRow(L10n.tr("Clear all"), selected = false, tag = "lp.menu.clear", radio = false) { actions.onClearAll() } }
        }
    }
}

private fun Modifier.height1(): Modifier = this.height(0.5.dp)

@Composable
private fun MenuHeading(text: String) {
    Text(
        text.uppercase(), color = LocalLp.current.dim, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** One real list row, 48 dp or taller; the chosen option has a check mark and is announced as selected. */
@Composable
private fun MenuRow(label: String, selected: Boolean, tag: String, radio: Boolean, trailing: String = "", pkg: String = "", onClick: () -> Unit) {
    val ink = LocalLp.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) { if (radio) { this.selected = selected; role = Role.RadioButton } else role = Role.Button }
            .clickable(onClick = onClick).testTag(tag).padding(start = 16.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), Alignment.Center) { if (selected) CheckIcon(BlueLight) }
        Spacer(Modifier.width(12.dp))
        if (pkg.isNotEmpty()) { AppIcon(pkg, label, 24); Spacer(Modifier.width(12.dp)) }
        Text(label, color = if (selected) ink.ink else ink.ink2, fontSize = 16.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (trailing.isNotEmpty()) Text(trailing, color = ink.dim, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun CheckIcon(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val sw = 2.4.dp.toPx(); val w = size.width; val h = size.height
        drawLine(color, Offset(w * 0.1f, h * 0.55f), Offset(w * 0.4f, h * 0.82f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.4f, h * 0.82f), Offset(w * 0.92f, h * 0.2f), sw, StrokeCap.Round)
    }
}

@Composable
private fun CloseIcon(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val sw = 2.2.dp.toPx(); val w = size.width; val h = size.height
        drawLine(color, Offset(w * 0.1f, h * 0.1f), Offset(w * 0.9f, h * 0.9f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.9f, h * 0.1f), Offset(w * 0.1f, h * 0.9f), sw, StrokeCap.Round)
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
