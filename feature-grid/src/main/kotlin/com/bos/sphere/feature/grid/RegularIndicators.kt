package com.bos.sphere.feature.grid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.IndicatorColors
import com.bos.sphere.core.data.IndicatorReading
import com.bos.sphere.core.data.ReadingKind
import com.bos.sphere.core.data.RegularIndicator
import com.bos.sphere.core.design.L10n

/** Wave 55: where the regular battery and signal indicators sit in the Glance chrome (pure, in dp, measured from the top of the safe area). */
object RegularIndicatorGeometry {
    /** The row is this tall and, at the widest, this wide (a battery with a percentage, and the bars). */
    const val HEIGHT_DP = 16f
    const val MAX_WIDTH_DP = 104f

    /** Under the music line when something plays (the line's slot ends at [GlanceNotch.TITLE_TOP_DP] + 40), else where the line would start. */
    fun topDp(musicShown: Boolean): Float = GlanceNotch.TITLE_TOP_DP + if (musicShown) 40f else 0f

    /** The row is centred, so it needs room on both sides for the top hotspots: true when it clears both [FixedHomeChrome.HOTSPOT_TOUCH_DP] squares in a window [windowDp] wide. */
    fun clearsHotspots(windowDp: Float, widthDp: Float = MAX_WIDTH_DP): Boolean = (windowDp - widthDp) / 2f >= FixedHomeChrome.HOTSPOT_TOUCH_DP
}

/**
 * The ordinary battery and signal indicators (wave 55): signal bars with a slash when there is no connectivity, then a small battery glyph with a charging bolt and the
 * percentage. Small, in the chrome ink; it never animates. Test tags: `regular.indicator` (the row), `regular.signal`, `regular.battery`.
 */
@Composable
fun RegularIndicators(signal: IndicatorReading, battery: IndicatorReading, dark: Boolean, modifier: Modifier = Modifier, low: Long = 0xFFFF4D4D) {
    val ink = GlanceInk.secondary(dark)
    val bars = RegularIndicator.bars(signal)
    val lost = RegularIndicator.signalLost(signal)
    val pct = RegularIndicator.percent(battery)
    val batLow = RegularIndicator.batteryLow(battery)
    val sigText = when {
        lost -> L10n.tr("No signal")
        signal.kind == ReadingKind.Idle -> L10n.tr("Airplane mode")
        else -> L10n.tr("Signal: %s of %s bars", bars, RegularIndicator.BARS)
    }
    val batText = when {
        pct == null -> L10n.tr("Battery unknown")
        battery.charging -> L10n.tr("Battery %s percent, charging", pct)
        else -> L10n.tr("Battery %s percent", pct)
    }
    Row(modifier.height(RegularIndicatorGeometry.HEIGHT_DP.dp).testTag("regular.indicator"), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 16.dp, height = 12.dp).testTag("regular.signal").semantics { contentDescription = sigText }) { drawBars(ink, bars, lost, Color(IndicatorColors.URGENT_RED.toInt())) }
        Row(Modifier.testTag("regular.battery").semantics(mergeDescendants = true) { contentDescription = batText }, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            val fill = if (batLow) Color(low.toInt()) else ink
            Canvas(Modifier.size(width = 20.dp, height = 11.dp)) { drawBattery(ink, fill, if (pct == null) 0f else pct / 100f, battery.charging) }
            if (pct != null) Text("$pct%", color = ink, fontSize = 11.sp, maxLines = 1)
        }
    }
}

private fun DrawScope.drawBars(ink: Color, bars: Int, lost: Boolean, lostInk: Color) {
    val n = RegularIndicator.BARS
    val gap = 1.5.dp.toPx(); val bw = (size.width - gap * (n - 1)) / n
    for (i in 0 until n) {
        val h = size.height * (0.35f + 0.65f * i / (n - 1))
        drawRoundRect(if (i < bars) ink else ink.copy(alpha = 0.3f), Offset(i * (bw + gap), size.height - h), Size(bw, h), CornerRadius(0.8.dp.toPx()))
    }
    if (lost) drawLine(lostInk, Offset(0f, size.height), Offset(size.width, 0f), 1.6.dp.toPx(), cap = StrokeCap.Round)
}

private fun DrawScope.drawBattery(ink: Color, fill: Color, level: Float, charging: Boolean) {
    val stroke = 1.3.dp.toPx(); val nub = 2.dp.toPx()
    val bw = size.width - nub; val inset = stroke / 2f
    drawRoundRect(ink, Offset(inset, inset), Size(bw - stroke, size.height - stroke), CornerRadius(2.dp.toPx()), style = Stroke(stroke))
    drawRoundRect(ink, Offset(bw, size.height * 0.32f), Size(nub * 0.8f, size.height * 0.36f), CornerRadius(0.8.dp.toPx()))
    val pad = stroke + 1.dp.toPx()
    val inner = bw - 2 * pad
    if (level > 0f) drawRoundRect(fill, Offset(pad, pad), Size(inner * level.coerceIn(0.06f, 1f), size.height - 2 * pad), CornerRadius(0.8.dp.toPx()))
    if (charging) {
        val cx = bw / 2f; val cy = size.height / 2f; val u = size.height / 10f
        val bolt = Path().apply {
            moveTo(cx + u * 0.6f, cy - u * 3.4f); lineTo(cx - u * 2f, cy + u * 0.4f); lineTo(cx - u * 0.1f, cy + u * 0.4f)
            lineTo(cx - u * 0.6f, cy + u * 3.4f); lineTo(cx + u * 2f, cy - u * 0.4f); lineTo(cx + u * 0.1f, cy - u * 0.4f); close()
        }
        drawPath(bolt, Color.Black.copy(alpha = 0.55f), style = Stroke(1.dp.toPx()))
        drawPath(bolt, Color.White)
    }
}
