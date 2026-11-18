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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import com.bos.sphere.core.data.Connectivity
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.SystemSnapshot
import com.bos.sphere.core.data.SystemStateLogic
import com.bos.sphere.core.data.SystemTileKind
import com.bos.sphere.core.design.L10n
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Wave 27 (commsDesign): the measures of the owner's Comms mock-ups, in one place so rows, header, panel and tests agree. The mock-ups
 * were drawn at 2x; sizes are the ones read off them (56 dp icon, 24 sp title, 40 sp light clock, 28 sp screen title), with the tab
 * text a little smaller (16 sp) so five tabs fit a phone width.
 */
internal object CommsStyle {
    const val ICON_DP = 56
    const val ROW_MIN_DP = 80
    const val H_PAD_DP = 20
    const val ROW_V_PAD_DP = 12
    const val TITLE_SP = 24
    const val SUB_SP = 16
    const val TIME_SP = 14
    const val HEADER_TITLE_SP = 28
    const val CLOCK_SP = 40
    const val TAB_SP = 15
    const val TAB_H_DP = 48
    const val GLYPH_DP = 24
    const val TARGET_DP = 48
    /** Text starts after the icon and a 16 dp gap. */
    const val TEXT_GAP_DP = 16
    /** The menu panel: 58 percent of the window. */
    const val PANEL_FRACTION = 0.58f
    const val GRID_COLUMNS = 3
    const val GRID_ROWS = 2
    const val GRID_CELL_H_DP = 56
    const val SHOW_ICON_DP = 24
    const val SHOW_ICON_GAP_DP = 8
    const val SHOW_ROW_MIN_DP = 52

    /** The Comms dark palette read off the mock-ups; the light one is the shared light tokens (see docs/decisions/commsDesign.md). */
    val DarkBg = Color(0xFF0F1219)
    val DarkPanel = Color(0xFF1A1A1A)
    val DarkInk = Color(0xFFFFFFFF)
    val DarkInk2 = Color(0xFFC9CDD6)
    val DarkDim = Color(0xFF6E7587)
    val DarkLine = Color(0xFF262B38)
    val DarkGridLine = Color(0xFF2C2C2C)
}

/** The mode (or hub grouping) a cell of the GROUP BY grid stands for, in reading order: row 1 time, app, sender; row 2 conversation, app + conversation, none. */
internal val GroupGridOrder = listOf(GroupMode.Time, GroupMode.App, GroupMode.Sender, GroupMode.Conversation, GroupMode.AppConversation, GroupMode.Flat)

/** Clock text like the system: 24 h "23:02", 12 h "11:02" (the am/pm marker is drawn small beside it). */
internal fun clockText(ms: Long, is24: Boolean, zone: ZoneId): String =
    DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm", Locale.getDefault()).format(Instant.ofEpochMilli(ms).atZone(zone))
internal fun clockMarker(ms: Long, is24: Boolean, zone: ZoneId): String =
    if (is24) "" else DateTimeFormatter.ofPattern("a", Locale.getDefault()).format(Instant.ofEpochMilli(ms).atZone(zone))

/**
 * The second row of the Comms header: the clock (light, 40 sp, left; a tap opens the Sound and Vibration popup through [onOpenSound]) and four plain
 * status glyphs on the right (brightness, battery with its level, signal, Wi-Fi). Each glyph is a 48 dp target that opens the public system panel.
 */
@Composable
internal fun CommsClockRow(
    snapshot: SystemSnapshot?, now: Long, is24: Boolean, zone: ZoneId,
    onOpenPanel: (SystemTileKind) -> Unit, onOpenSound: () -> Unit, modifier: Modifier = Modifier,
) {
    val lp = LocalLp.current
    val text = clockText(now, is24, zone)
    val marker = clockMarker(now, is24, zone)
    Row(modifier.fillMaxWidth().padding(start = CommsStyle.H_PAD_DP.dp, end = CommsStyle.H_PAD_DP.dp - 12.dp).heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.heightIn(min = CommsStyle.TARGET_DP.dp).clip(RoundedCornerShape(12.dp))
                .semantics(mergeDescendants = true) { contentDescription = L10n.tr("Time") + ": " + text + (if (marker.isEmpty()) "" else " $marker"); role = Role.Button }
                .clickable(onClick = onOpenSound).testTag("lp.clock"),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text40(text, lp.ink)
            if (marker.isNotEmpty()) androidx.compose.material3.Text(marker, color = lp.dim, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp, bottom = 9.dp), maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        if (snapshot != null) {
            val conn = snapshot.connectivity
            StatusGlyph("lp.sys.brightness", L10n.tr("Brightness"), SystemTileKind.Brightness, onOpenPanel) { c -> BrightnessGlyph(c, lp.bg) }
            StatusGlyph("lp.sys.battery", L10n.tr("Battery") + (if (snapshot.batteryPercent in 0..100) ": ${snapshot.batteryPercent}%" else "") + (if (snapshot.charging) ", " + L10n.tr("Charging").lowercase() else ""), SystemTileKind.Battery, onOpenPanel) { c -> BatteryGlyph(c, snapshot.batteryPercent) }
            StatusGlyph("lp.sys.signal", L10n.tr("Connectivity") + ": " + connectivityText(conn), SystemTileKind.Connectivity, onOpenPanel) { c -> SignalGlyph(if (conn == Connectivity.None) lp.dim else c) }
            StatusGlyph("lp.sys.wifi", L10n.tr("Connectivity") + ": " + connectivityText(conn), SystemTileKind.Connectivity, onOpenPanel) { c -> WifiGlyph(if (conn == Connectivity.Wifi) c else lp.dim) }
        }
    }
}

private fun connectivityText(c: Connectivity) = L10n.tr(when (c) {
    Connectivity.None -> "Offline"; Connectivity.Wifi -> "Wi-Fi"; Connectivity.Cellular -> "Mobile"; Connectivity.Ethernet -> "Ethernet"; Connectivity.Other -> "Connected"
})

@Composable
private fun Text40(text: String, color: Color) = androidx.compose.material3.Text(text, color = color, fontSize = CommsStyle.CLOCK_SP.sp, fontWeight = FontWeight.Light, maxLines = 1)

@Composable
private fun StatusGlyph(tag: String, description: String, kind: SystemTileKind, onOpen: (SystemTileKind) -> Unit, glyph: @Composable (Color) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = CircleShape
    Box(
        Modifier.size(CommsStyle.TARGET_DP.dp).clip(shape)
            .testTag(tag)
            .semantics(mergeDescendants = true) { contentDescription = description; role = Role.Button }
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(2.dp, FocusRing, shape) else Modifier)
            .clickable { onOpen(kind) },
        Alignment.Center,
    ) { glyph(LocalLp.current.ink) }
}

// ---- the glyphs: plain strokes and fills, no resources ----

@Composable internal fun BrightnessGlyph(color: Color, cut: Color) {
    Canvas(Modifier.size(CommsStyle.GLYPH_DP.dp)) {
        val k = size.minDimension / 24f
        val c = Offset(12f * k, 12f * k)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0 + 22.5)
            val d = Offset(Math.cos(a).toFloat(), Math.sin(a).toFloat())
            drawLine(color, c + d * (9f * k), c + d * (11.2f * k), 2f * k, StrokeCap.Round)
        }
        drawCircle(color, 6.4f * k, c)
        // the dial's needle, cut out of the disc
        drawLine(cut, c, Offset(15.5f * k, 8.5f * k), 1.8f * k, StrokeCap.Round)
        drawCircle(cut, 1.3f * k, c)
    }
}

@Composable internal fun BatteryGlyph(color: Color, percent: Int) {
    Canvas(Modifier.size(CommsStyle.GLYPH_DP.dp)) {
        val k = size.minDimension / 24f
        drawRoundRect(color, Offset(6.5f * k, 4f * k), Size(11f * k, 18f * k), CornerRadius(1.6f * k), style = Stroke(1.8f * k))
        drawRect(color, Offset(9.5f * k, 2f * k), Size(5f * k, 2f * k))
        val bars = if (percent < 0) 0 else Math.ceil(percent.coerceIn(0, 100) / 20.0).toInt()
        for (i in 0 until bars) drawRect(color, Offset(8.4f * k, (19.6f - i * 3.2f) * k), Size(7.2f * k, 2.2f * k))
    }
}

@Composable internal fun SignalGlyph(color: Color) {
    Canvas(Modifier.size(CommsStyle.GLYPH_DP.dp)) {
        val k = size.minDimension / 24f
        val p = Path().apply { moveTo(2.5f * k, 20f * k); lineTo(21.5f * k, 20f * k); lineTo(21.5f * k, 3.5f * k); close() }
        drawPath(p, color)
    }
}

@Composable internal fun WifiGlyph(color: Color) {
    Canvas(Modifier.size(CommsStyle.GLYPH_DP.dp)) {
        val k = size.minDimension / 24f
        drawArc(color, 225f, 90f, true, Offset(-5f * k, 4f * k), Size(34f * k, 34f * k))
    }
}

/** One cell glyph of the GROUP BY grid on a 24 grid; [on] is the selected one (drawn in the full ink colour, the others dim). */
@Composable
internal fun GroupModeGlyph(mode: GroupMode, color: Color) {
    Canvas(Modifier.size(CommsStyle.GLYPH_DP.dp)) {
        val k = size.minDimension / 24f
        val st = Stroke(2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * k, y * k)
        fun bubble(x: Float, y: Float, w: Float, h: Float, fill: Boolean) {
            val path = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(x * k, y * k, (x + w) * k, (y + h) * k, CornerRadius(1.6f * k)))
                moveTo((x + w - 4f) * k, (y + h) * k); lineTo((x + w) * k, (y + h + 3.6f) * k); lineTo((x + w) * k, (y + h) * k); close()
            }
            if (fill) drawPath(path, color) else drawPath(path, color, style = st)
        }
        when (mode) {
            GroupMode.Time -> {
                drawRoundRect(color, p(5f, 5f), Size(14f * k, 14f * k), CornerRadius(3f * k), style = st)
                drawLine(color, p(8f, 2f), p(16f, 2f), 2f * k, StrokeCap.Round); drawLine(color, p(8f, 22f), p(16f, 22f), 2f * k, StrokeCap.Round)
                drawLine(color, p(12f, 9f), p(12f, 12f), 1.8f * k, StrokeCap.Round); drawLine(color, p(12f, 12f), p(14.5f, 13.5f), 1.8f * k, StrokeCap.Round)
            }
            GroupMode.App -> {
                val t = Path().apply { moveTo(4f * k, 3f * k); lineTo(4f * k, 21f * k); lineTo(20.5f * k, 12f * k); close() }
                drawPath(t, color, style = st)
                drawLine(color, p(4f, 3f), p(14f, 13f), 1.8f * k, StrokeCap.Round)
            }
            GroupMode.Sender -> {
                drawRoundRect(color, p(3f, 3f), Size(18f * k, 18f * k), CornerRadius(2.5f * k))
                drawCircle(Color.Black.copy(alpha = 0.55f), 3f * k, p(12f, 9.5f))
                drawArc(Color.Black.copy(alpha = 0.55f), 180f, 180f, true, p(6.5f, 14f), Size(11f * k, 9f * k))
            }
            GroupMode.Conversation -> { bubble(2.5f, 3f, 12f, 8f, false); bubble(9.5f, 10f, 12f, 8f, true) }
            GroupMode.AppConversation -> {
                val t = Path().apply { moveTo(2.5f * k, 3f * k); lineTo(2.5f * k, 19f * k); lineTo(15f * k, 11f * k); close() }
                drawPath(t, color, style = st)
                bubble(12f, 11f, 9f, 6f, true)
            }
            GroupMode.Flat -> {
                drawCircle(color, 9f * k, p(12f, 12f), style = Stroke(2.4f * k))
                drawLine(color, p(5.6f, 5.6f), p(18.4f, 18.4f), 2.4f * k, StrokeCap.Round)
            }
        }
    }
}

/** Wave 28: the colours of the Comms page for a panel the host draws inside it (the app's Timeline body), so it follows the dark and light looks. */
class CommsPanelColors(val ink: Color, val ink2: Color, val dim: Color, val line: Color, val bg: Color)

@androidx.compose.runtime.Composable
fun commsPanelColors(): CommsPanelColors = LocalLp.current.let { CommsPanelColors(it.ink, it.ink2, it.dim, it.line, it.bg) }
