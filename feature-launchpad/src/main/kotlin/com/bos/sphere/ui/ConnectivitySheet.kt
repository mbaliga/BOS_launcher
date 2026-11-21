package com.bos.sphere.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.delay

private val Ink = Color.White
private val Accent = Color(0xFF1E88FF)       // the connected network's blue in the mock-up
private val ToggleOn = Color(0xFFA6B800)     // the mock-up's olive green
private val ToggleOff = Color(0xFF8C8C91)
private val RowMin = 56.dp

/**
 * Wave 34 (connectivity, owner mock-up): the Connectivity sheet, which replaces the system Internet panel behind the signal and Wi-Fi glyphs.
 * Same scaffolding as the Sound and Vibration sheet ([PopupSheet]). Android 12+ gives apps no way to switch mobile data or Wi-Fi, so the toggles
 * show what the system holds and open the matching system panel on a tap; they never show a change that did not happen. The state comes from
 * [source] and is re-read on resume and every 1.5 s while open.
 */
@Composable
fun ConnectivitySheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    source: ConnectivitySource = rememberAndroidConnectivitySource(),
    actions: ConnectivityActions = rememberAndroidConnectivityActions(),
) {
    var state by remember(source) { mutableStateOf(source.read()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, source) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) state = source.read() }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    LaunchedEffect(source) { while (true) { delay(1500); state = source.read() } }
    ConnectivityContent(state, onDismiss, actions, modifier)
}

@Composable fun rememberAndroidConnectivitySource(): ConnectivitySource { val c = LocalContext.current; return remember(c) { AndroidConnectivitySource(c) } }
@Composable fun rememberAndroidConnectivityActions(): ConnectivityActions { val c = LocalContext.current; return remember(c) { AndroidConnectivityActions(c) } }

/** Whether the Connectivity sheet is showing. The signal and Wi-Fi glyphs of the Comms header call [show]. */
class ConnectivitySheetState { var open by mutableStateOf(false); fun show() { open = true } }
@Composable fun rememberConnectivitySheetState(): ConnectivitySheetState = remember { ConnectivitySheetState() }

@Composable
internal fun ConnectivityContent(state: ConnectivityState, onDismiss: () -> Unit, actions: ConnectivityActions, modifier: Modifier = Modifier) {
    PopupSheet(L10n.tr("Connectivity"), L10n.tr("Close Connectivity"), "cn", onDismiss, modifier) { compact ->
        val gap = if (compact) 4.dp else 14.dp
        if (state.showMobileRow) {
            ToggleRow("cn.mobile", if (state.snapshot.carrier.isNullOrBlank()) L10n.tr(state.mobileLabel) else state.mobileLabel, state.mobileOn, { SignalIcon(Ink) }, actions::openMobilePanel)
            Spacer(Modifier.height(gap))
        }
        ToggleRow("cn.wifi", L10n.tr("WiFi"), state.wifiOn, null, actions::openWifiPanel)
        Spacer(Modifier.height(gap))
        Column(Modifier.fillMaxWidth().graphicsLayer { alpha = if (state.listDimmed) 0.7f else 1f }.testTag("cn.list")) {
            state.visibleNetworks.forEachIndexed { i, n -> NetworkRow(i, n, state.listDimmed, actions) }
        }
        state.note?.let {
            Text(L10n.tr(it.english), color = Color(0xB3FFFFFF), fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 2.dp).testTag("cn.note"))
        }
        ActionRow("cn.seeall", L10n.tr("See all"), actions::openWifiPanel) { ChevronIcon() }
        Spacer(Modifier.height(if (compact) 4.dp else 20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ActionRow("cn.share", L10n.tr("Share Wifi"), actions::shareWifi, Modifier.weight(1f)) { ShareIcon() }
            Box(
                Modifier.padding(start = 12.dp).heightIn(min = RowMin).widthIn(min = 72.dp).testTag("cn.done")
                    .clip(RoundedCornerShape(12.dp)).semantics(mergeDescendants = true) { role = Role.Button }.clickable(onClick = onDismiss),
                Alignment.CenterEnd, // wave 38: flush with the right margin, like the switches above (the mock-up's Done ends where the switch ends)
            ) { Text(L10n.tr("Done"), color = Ink, fontSize = 20.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp)) }
        }
        Spacer(Modifier.height(if (compact) 8.dp else 24.dp))
    }
}

/** A switch row. The switch shows the system's state; a tap hands over to the system panel and changes nothing here. */
@Composable
private fun ToggleRow(tag: String, label: String, on: Boolean, icon: (@Composable () -> Unit)?, open: () -> Unit) {
    val stateText = L10n.tr(if (on) "On" else "Off")
    Row(
        Modifier.fillMaxWidth().heightIn(min = RowMin).testTag(tag).clip(RoundedCornerShape(12.dp))
            .semantics(mergeDescendants = true) { role = Role.Switch; toggleableState = if (on) ToggleableState.On else ToggleableState.Off; stateDescription = stateText }
            .clickable(onClickLabel = L10n.tr("Opens the system panel"), onClick = open),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Box(Modifier.width(48.dp), Alignment.CenterStart) { icon() }
        Text(label, color = Ink, fontSize = 20.sp, maxLines = 1, modifier = Modifier.weight(1f))
        SwitchVisual(on)
    }
}

@Composable
private fun SwitchVisual(on: Boolean) {
    Box(
        Modifier.width(56.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) ToggleOn else ToggleOff).testTag("cn.switch.${if (on) "on" else "off"}").padding(3.dp),
        if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).background(Color.White)) }
}

@Composable
private fun NetworkRow(index: Int, n: WifiNetworkRow, dimmed: Boolean, actions: ConnectivityActions) {
    val color = if (n.current) Accent else Ink
    val name = n.name ?: L10n.tr("Connected network")
    val secured = L10n.tr("Secured")
    val connected = L10n.tr("Connected")
    Row(Modifier.fillMaxWidth().heightIn(min = RowMin).testTag("cn.netrow.$index"), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = RowMin).testTag("cn.net.$index").clip(RoundedCornerShape(12.dp))
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    val parts = buildList { if (n.current && !dimmed) add(connected); if (n.secured && !n.current) add(secured) }
                    if (parts.isNotEmpty()) stateDescription = parts.joinToString(", ")
                }
                .clickable(onClickLabel = L10n.tr("Opens the system panel"), onClick = actions::openWifiPanel),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(48.dp), Alignment.CenterStart) { WifiIcon(color) }
            Text(name, color = color, fontSize = 20.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f).testTag("cn.net.$index.name"))
        }
        if (n.current) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).testTag("cn.tune")
                    .semantics { role = Role.Button; contentDescription = L10n.tr("Network settings") }
                    .clickable(onClick = actions::openWifiSettings),
                Alignment.CenterEnd, // wave 38: the glyph ends at the right margin
            ) { TuneIcon() }
        } else if (n.secured) {
            Box(Modifier.size(48.dp).testTag("cn.lock.$index"), Alignment.CenterEnd) { LockIcon() }
        }
    }
}

@Composable
private fun ActionRow(tag: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: @Composable () -> Unit) {
    Row(
        modifier.heightIn(min = RowMin).testTag(tag).clip(RoundedCornerShape(12.dp)).semantics(mergeDescendants = true) { role = Role.Button }.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(48.dp), Alignment.CenterStart) { icon() }
        Text(label, color = Ink, fontSize = 20.sp, maxLines = 1)
    }
}

// ---- glyphs, white on black on a 24 grid; all decorative (the row carries the meaning) -------------------------------------------------------

@Composable private fun SignalIcon(c: Color) = Canvas(Modifier.size(26.dp).clearAndSetSemantics { }) {
    drawPath(Path().apply { moveTo(0f, size.height); lineTo(size.width, size.height); lineTo(size.width, 0f); close() }, c.copy(alpha = 0.85f))
}

@Composable private fun WifiIcon(c: Color) = Canvas(Modifier.size(28.dp, 22.dp).clearAndSetSemantics { }) {
    val w = size.width; val h = size.height; val rx = w / 1.4142f
    drawArc(c, 225f, 90f, true, Offset(w / 2f - rx, 0f), Size(2f * rx, 2f * h))
}

@Composable private fun LockIcon() = Canvas(Modifier.size(22.dp).clearAndSetSemantics { }) {
    val k = size.minDimension / 24f
    drawRoundRect(Ink, Offset(4f * k, 10f * k), Size(16f * k, 12f * k), CornerRadius(2.5f * k))
    val shackle = Path().apply { moveTo(7.5f * k, 10f * k); lineTo(7.5f * k, 7f * k); cubicTo(7.5f * k, 1.5f * k, 16.5f * k, 1.5f * k, 16.5f * k, 7f * k); lineTo(16.5f * k, 10f * k) }
    drawPath(shackle, Ink, style = Stroke(2.6f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(Color.Black, 1.7f * k, Offset(12f * k, 15.5f * k))
}

@Composable private fun TuneIcon() = Canvas(Modifier.size(24.dp).clearAndSetSemantics { }) {
    val k = size.minDimension / 24f
    val st = Stroke(2.4f * k, cap = StrokeCap.Round)
    drawLine(Ink, Offset(2f * k, 7f * k), Offset(9f * k, 7f * k), st.width, StrokeCap.Round); drawLine(Ink, Offset(15f * k, 7f * k), Offset(22f * k, 7f * k), st.width, StrokeCap.Round)
    drawCircle(Ink, 3.4f * k, Offset(12f * k, 7f * k), style = st)
    drawLine(Ink, Offset(2f * k, 17f * k), Offset(9f * k, 17f * k), st.width, StrokeCap.Round); drawLine(Ink, Offset(15f * k, 17f * k), Offset(22f * k, 17f * k), st.width, StrokeCap.Round)
    drawCircle(Ink, 3.4f * k, Offset(12f * k, 17f * k), style = st)
    drawRect(Ink, Offset(2f * k, 5f * k), Size(1f, 1f)) // keeps the canvas non-empty for hit testing
}

@Composable private fun ChevronIcon() {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(Modifier.size(24.dp).graphicsLayer { scaleX = if (rtl) -1f else 1f }.clearAndSetSemantics { }) {
        val k = size.minDimension / 24f
        val p = Path().apply { moveTo(8f * k, 3f * k); lineTo(17f * k, 12f * k); lineTo(8f * k, 21f * k) }
        drawPath(p, Ink, style = Stroke(2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable private fun ShareIcon() = Canvas(Modifier.size(26.dp).clearAndSetSemantics { }) {
    val k = size.minDimension / 24f
    val a = Offset(18f * k, 5f * k); val b = Offset(6f * k, 12f * k); val c = Offset(18f * k, 19f * k)
    drawLine(Ink, a, b, 2f * k); drawLine(Ink, b, c, 2f * k)
    for (o in listOf(a, b, c)) drawCircle(Ink, 3.4f * k, o)
}
