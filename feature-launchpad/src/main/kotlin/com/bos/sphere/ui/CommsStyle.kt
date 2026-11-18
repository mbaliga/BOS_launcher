package com.bos.sphere.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bos.sphere.core.data.CommsScale
import com.bos.sphere.core.data.CommsScaleSettings
import com.bos.sphere.core.data.Connectivity
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.SystemSnapshot
import com.bos.sphere.core.data.SystemTileKind
import com.bos.sphere.core.design.L10n
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Wave 28 (commsLayout): every dimension of the Comms surfaces, in dp and sp at scale 1.0, read off the owner's two mock-ups (1 dp = 2.2 px there:
 * 411 dp wide, 352 dpi; the measured table is in docs/decisions/commsLayout.md). Nothing in the Comms surfaces keeps a private dp or sp.
 *
 * The scale (75 to 125 percent, see [CommsScale]) multiplies dp and sp together: [CommsScaleHost] hands its content a [Density] whose density is the
 * system's times the factor, so `CommsStyle.ICON_DP.dp` is 38 dp x factor of real screen, and `sp` follows the same factor on top of the user's font scale.
 * Minimum touch targets are the exception: [CommsScaleHost] also scales the view configuration's minimum touch target the other way, so a target never falls
 * under 48 dp of real screen (and [target] gives the matching size for explicit 48 dp boxes).
 */
internal object CommsStyle {
    // ---- the content area ----
    /** Left margin of everything: header, clock, tabs, hairlines, rows. */
    const val H_PAD_DP = 32f
    /** Right margin, measured from the edge of the area beside the home card's sliver. */
    const val END_PAD_DP = 18f
    const val HAIR_DP = 1f

    // ---- header: the menu glyph and the title ----
    const val HEADER_TOP_DP = 19f
    const val HEADER_ROW_DP = 48f
    const val MENU_GLYPH_DP = 12.8f
    const val MENU_GLYPH_STROKE_DP = 1.8f
    const val TITLE_SP = 17f
    /** Where the title text starts, from the left edge; its cap centre sits 1.6 dp above the menu glyph's centre. */
    const val TITLE_X_DP = 57.7f
    const val TITLE_DY_DP = -1.6f

    // ---- clock row ----
    const val CLOCK_ROW_DP = 48f
    const val CLOCK_SP = 23f
    const val CLOCK_MARKER_SP = 12f
    /** The four status glyphs (brightness, battery, signal, Wi-Fi): ink sizes and the gap between neighbouring inks. */
    const val SUN_DP = 20.4f
    const val BATTERY_W_DP = 10.5f
    const val BATTERY_H_DP = 16.4f
    const val SIGNAL_DP = 16.4f
    const val WIFI_W_DP = 20.5f
    const val WIFI_H_DP = 16.4f
    const val GLYPH_GAP_DP = 11.7f
    /** The sun's ink sits 1.4 dp higher than the others. */
    const val SUN_DY_DP = -1.4f

    // ---- tabs ----
    /** Gap between the clock row and the tab row. */
    const val TABS_GAP_DP = 3.4f
    const val TAB_H_DP = 35f
    const val TAB_SP = 17f
    const val TAB_PAD_DP = 8.8f
    /** From one tab label's left edge to the next one's, for ALL, UNREAD, FILES and USAGE: a tab is at least this wide, so labels start where the mock-up has them whatever the font. */
    val TAB_SLOTS_DP = floatArrayOf(46.4f, 83.4f, 60.0f, 70.9f)

    // ---- rows ----
    const val ROW_DP = 70f
    const val ICON_DP = 38f
    /** Rows: the icon's top is 16 dp below the row's top; the text starts 16 dp after the icon. */
    const val ICON_TOP_DP = 16f
    const val TEXT_GAP_DP = 16f
    const val ICON_RADIUS_FRACTION = 0.15f
    const val TITLE_ROW_SP = 17f
    const val SUB_SP = 11f
    const val TIME_SP = 11f
    /** Baselines, from the row's top. */
    const val TITLE_BASELINE_DP = 33.8f
    const val SUB_BASELINE_DP = 53.3f
    const val TIME_BASELINE_DP = 51.2f
    /** The headline stops this far short of the right margin. */
    const val TITLE_END_DP = 8f
    /** Section and sub headings, notices and the like. */
    const val HEADING_SP = 12f
    const val NOTE_SP = 12f
    const val HEAD_ROW_DP = 36f
    const val CHIP_SP = 12f
    const val BODY_SP = 15f
    const val TARGET_DP = 48f
    const val EMPTY_SP = 15f

    // ---- the menu panel ----
    const val PANEL_FRACTION = 0.584f
    const val PANEL_MAX_DP = 380f
    /** The Comms screen behind is pushed right by the panel plus this much. */
    const val PANEL_GAP_DP = 10.5f
    const val PANEL_END_PAD_DP = 19.5f
    const val LABEL_SP = 12f
    const val GROUP_BY_BASELINE_DP = 41.4f
    const val GRID_TOP_DP = 60f
    const val GRID_CELL_H_DP = 41.4f
    const val GRID_COLUMNS = 3
    const val GRID_ROWS = 2
    const val GRID_GLYPH_DP = 23f
    const val SHOW_BASELINE_FROM_GRID_DP = 32.3f
    const val SHOW_BELOW_BASELINE_DP = 10.9f
    const val SHOW_ROW_DP = 36f
    const val SHOW_ICON_DP = 16.5f
    const val SHOW_ICON_GAP_DP = 16.5f
    const val SHOW_SP = 17f
    const val SECTION_GAP_DP = 24f

    /** The Comms dark palette read off the mock-ups; the light one is the shared light tokens (see docs/decisions/commsDesign.md). */
    val DarkBg = Color(0xFF0E121A)
    val DarkPanel = Color(0xFF181818)
    val DarkInk = Color(0xFFFFFFFF)
    val DarkInk2 = Color(0xFFD7DAE0)
    val DarkDim = Color(0xFF8B8F95)
    val DarkLine = Color(0xFF262A33)
    val DarkGridLine = Color(0xFF242424)
    /** Tabs that are not selected, the grid's unselected glyphs and the menu counts. */
    val DarkTabDim = Color(0xFF43464E)
    val DarkGlyphDim = Color(0xFF4D4D4D)
    val DarkCount = Color(0xFF343434)

    /** Dp at scale [factor] (for tests and for code outside a [CommsScaleHost]). */
    fun scaled(dp: Float, factor: Float): Float = dp * factor
}

/** The scale factor of the enclosing [CommsScaleHost] (1 outside one). */
internal val LocalCommsScale = staticCompositionLocalOf { 1f }

/** 48 dp of real screen whatever the scale: the size for an explicit minimum target inside a [CommsScaleHost]. */
@Composable
internal fun commsTarget(): Dp = (CommsStyle.TARGET_DP / LocalCommsScale.current).dp

/** The persisted percent as state; recomposes when the stepper, the pinch or an import changes it. */
@Composable
internal fun rememberCommsScalePercent(): Int {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    remember(ctx) { CommsScaleSettings.init(ctx); true }
    val p by CommsScaleSettings.percent.collectAsStateWithLifecycle()
    return p
}

/**
 * Scales everything inside by [percent] (see [CommsStyle]): dp and sp through the density, and the minimum touch target back to 48 dp of real screen.
 * Do not nest hosts.
 */
@Composable
internal fun CommsScaleHost(percent: Int, content: @Composable () -> Unit) {
    val f = CommsScale.factor(percent)
    val base = LocalDensity.current
    val vc = LocalViewConfiguration.current
    val scaled = remember(base, f) { Density(base.density * f, base.fontScale) }
    val view = remember(vc, f) { ScaledViewConfiguration(vc, f) }
    CompositionLocalProvider(LocalDensity provides scaled, LocalViewConfiguration provides view, LocalCommsScale provides f, content = content)
}

private class ScaledViewConfiguration(private val base: ViewConfiguration, private val factor: Float) : ViewConfiguration by base {
    override val minimumTouchTargetSize: DpSize get() = DpSize((CommsStyle.TARGET_DP / factor).dp, (CommsStyle.TARGET_DP / factor).dp)
}

/** The mode (or hub grouping) a cell of the GROUP BY grid stands for, in reading order: row 1 time, app, sender; row 2 conversation, app + conversation, none. */
internal val GroupGridOrder = listOf(GroupMode.Time, GroupMode.App, GroupMode.Sender, GroupMode.Conversation, GroupMode.AppConversation, GroupMode.Flat)

/** Clock text like the system: 24 h "23:02", 12 h "11:02" (the am/pm marker is drawn small beside it). */
internal fun clockText(ms: Long, is24: Boolean, zone: ZoneId): String =
    DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm", Locale.getDefault()).format(Instant.ofEpochMilli(ms).atZone(zone))
internal fun clockMarker(ms: Long, is24: Boolean, zone: ZoneId): String =
    if (is24) "" else DateTimeFormatter.ofPattern("a", Locale.getDefault()).format(Instant.ofEpochMilli(ms).atZone(zone))

/**
 * The second row of the Comms header: the clock (light, 24 sp, left; a tap opens the Sound and Vibration popup through [onOpenSound]) and four plain
 * status glyphs on the right (brightness, battery with its level, signal, Wi-Fi), spaced by the mock-up's gaps between their inks. Each glyph is as high
 * as the row (48 dp) and as wide as its ink plus half a gap on either side, and opens the public system panel.
 */
@Composable
internal fun CommsClockRow(
    snapshot: SystemSnapshot?, now: Long, is24: Boolean, zone: ZoneId,
    onOpenPanel: (SystemTileKind) -> Unit, onOpenSound: () -> Unit, modifier: Modifier = Modifier,
) {
    val lp = LocalLp.current
    val text = clockText(now, is24, zone)
    val marker = clockMarker(now, is24, zone)
    Row(
        modifier.fillMaxWidth().padding(start = CommsStyle.H_PAD_DP.dp, end = (CommsStyle.END_PAD_DP - CommsStyle.GLYPH_GAP_DP / 2).dp).height(CommsStyle.CLOCK_ROW_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.height(CommsStyle.CLOCK_ROW_DP.dp).clip(RoundedCornerShape(8.dp))
                .semantics(mergeDescendants = true) { contentDescription = L10n.tr("Time") + ": " + text + (if (marker.isEmpty()) "" else " $marker"); role = Role.Button }
                .clickable(onClick = onOpenSound).testTag("lp.clock"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Text(text, color = lp.ink, fontSize = CommsStyle.CLOCK_SP.sp, fontWeight = FontWeight.Light, maxLines = 1)
            if (marker.isNotEmpty()) androidx.compose.material3.Text(marker, color = lp.dim, fontSize = CommsStyle.CLOCK_MARKER_SP.sp, modifier = Modifier.padding(start = 3.dp, top = 8.dp), maxLines = 1)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        if (snapshot != null) {
            val conn = snapshot.connectivity
            val half = (CommsStyle.GLYPH_GAP_DP / 2)
            StatusGlyph("lp.sys.brightness", L10n.tr("Brightness"), SystemTileKind.Brightness, onOpenPanel, CommsStyle.SUN_DP, CommsStyle.SUN_DP, CommsStyle.SUN_DY_DP) { c -> BrightnessGlyph(c, lp.bg) }
            StatusGlyph("lp.sys.battery", L10n.tr("Battery") + (if (snapshot.batteryPercent in 0..100) ": ${snapshot.batteryPercent}%" else "") + (if (snapshot.charging) ", " + L10n.tr("Charging").lowercase() else ""), SystemTileKind.Battery, onOpenPanel, CommsStyle.BATTERY_W_DP, CommsStyle.BATTERY_H_DP) { c -> BatteryGlyph(c, snapshot.batteryPercent) }
            StatusGlyph("lp.sys.signal", L10n.tr("Connectivity") + ": " + connectivityText(conn), SystemTileKind.Connectivity, onOpenPanel, CommsStyle.SIGNAL_DP, CommsStyle.SIGNAL_DP) { c -> SignalGlyph(if (conn == Connectivity.None) lp.dim else c) }
            StatusGlyph("lp.sys.wifi", L10n.tr("Connectivity") + ": " + connectivityText(conn), SystemTileKind.Connectivity, onOpenPanel, CommsStyle.WIFI_W_DP, CommsStyle.WIFI_H_DP) { c -> WifiGlyph(if (conn == Connectivity.Wifi) c else lp.dim) }
        }
    }
}

private fun connectivityText(c: Connectivity) = L10n.tr(when (c) {
    Connectivity.None -> "Offline"; Connectivity.Wifi -> "Wi-Fi"; Connectivity.Cellular -> "Mobile"; Connectivity.Ethernet -> "Ethernet"; Connectivity.Other -> "Connected"
})

@Composable
private fun StatusGlyph(
    tag: String, description: String, kind: SystemTileKind, onOpen: (SystemTileKind) -> Unit,
    inkW: Float, inkH: Float, dy: Float = 0f, glyph: @Composable (Color) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.width((inkW + CommsStyle.GLYPH_GAP_DP).dp).requiredHeight(maxOf(CommsStyle.CLOCK_ROW_DP.dp, commsTarget())).clip(shape)
            .testTag(tag)
            .semantics(mergeDescendants = true) { contentDescription = description; role = Role.Button }
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(2.dp, FocusRing, shape) else Modifier)
            .clickable { onOpen(kind) },
        Alignment.Center,
    ) { Box(Modifier.offset(y = dy.dp).size(inkW.dp, inkH.dp).testTag("$tag.ink")) { glyph(LocalLp.current.ink) } }
}

// ---- the glyphs: plain strokes and fills, no resources; each fills the box it is given ----

@Composable internal fun BrightnessGlyph(color: Color, cut: Color) {
    Canvas(Modifier.size(CommsStyle.SUN_DP.dp)) {
        val k = size.minDimension / 24f
        val c = Offset(12f * k, 12f * k)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0 + 22.5)
            val d = Offset(Math.cos(a).toFloat(), Math.sin(a).toFloat())
            drawLine(color, c + d * (9.4f * k), c + d * (11.6f * k), 2f * k, StrokeCap.Round)
        }
        drawCircle(color, 6.6f * k, c)
        // the dial's needle, cut out of the disc
        drawLine(cut, c, Offset(15.5f * k, 8.5f * k), 1.8f * k, StrokeCap.Round)
        drawCircle(cut, 1.3f * k, c)
    }
}

@Composable internal fun BatteryGlyph(color: Color, percent: Int) {
    Canvas(Modifier.size(CommsStyle.BATTERY_W_DP.dp, CommsStyle.BATTERY_H_DP.dp)) {
        val w = size.width; val h = size.height
        val capH = h * 0.1f
        drawRect(color, Offset(w * 0.3f, 0f), Size(w * 0.4f, capH))
        val bodyTop = capH
        drawRoundRect(color, Offset(w * 0.04f, bodyTop), Size(w * 0.92f, h - bodyTop), CornerRadius(w * 0.1f), style = Stroke(w * 0.1f))
        val bars = if (percent < 0) 0 else Math.ceil(percent.coerceIn(0, 100) / 20.0).toInt()
        val inner = h - bodyTop - w * 0.34f
        val barH = inner / 5f
        for (i in 0 until bars) drawRect(color, Offset(w * 0.22f, h - w * 0.17f - (i + 1) * barH + barH * 0.18f), Size(w * 0.56f, barH * 0.64f))
    }
}

@Composable internal fun SignalGlyph(color: Color) {
    Canvas(Modifier.size(CommsStyle.SIGNAL_DP.dp)) {
        val p = Path().apply { moveTo(0f, size.height); lineTo(size.width, size.height); lineTo(size.width, 0f); close() }
        drawPath(p, color)
    }
}

@Composable internal fun WifiGlyph(color: Color) {
    Canvas(Modifier.size(CommsStyle.WIFI_W_DP.dp, CommsStyle.WIFI_H_DP.dp)) {
        val w = size.width; val h = size.height
        val rx = w / 1.4142f
        drawArc(color, 225f, 90f, true, Offset(w / 2f - rx, 0f), Size(2f * rx, 2f * h))
    }
}

/** One cell glyph of the GROUP BY grid on a 24 grid; [on] is the selected one (drawn in the full ink colour, the others dim). */
@Composable
internal fun GroupModeGlyph(mode: GroupMode, color: Color, glyphDp: Float = CommsStyle.GRID_GLYPH_DP) {
    Canvas(Modifier.size(glyphDp.dp)) {
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
