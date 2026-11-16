package com.bos.sphere.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import com.bos.sphere.core.data.Connectivity
import com.bos.sphere.core.data.SoundState
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.SystemSnapshot
import com.bos.sphere.core.data.SystemStateLogic
import com.bos.sphere.core.data.SystemTileKind
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * STR-10 (wave 9, owner request): time, battery, connectivity and sound at the top of Launchpad as four icon-only buttons in one row.
 * Each icon is a vector shape drawn from the state (clock hands, battery level and bolt, link type, ringer mode) and has a content
 * description that carries the state ("Battery: 82%, charging"); tapping one opens the matching system panel. They display state and
 * do not claim to toggle what Android blocks. Each is focusable with a visible focus ring.
 */
@Composable
fun SystemStateRow(snapshot: SystemSnapshot, is24: Boolean, onOpenPanel: (SystemTileKind) -> Unit, modifier: Modifier = Modifier) {
    val tiles = SystemStateLogic.tiles(snapshot)
    val zone = remember { ZoneId.systemDefault() }
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).testTag("lp.system"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.forEach { t ->
            val time = if (t.kind == SystemTileKind.Time) {
                DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", Locale.getDefault()).format(Instant.ofEpochMilli(snapshot.now).atZone(zone))
            } else ""
            val value = if (t.kind == SystemTileKind.Time) time else L10n.tr(t.value)
            val detail = if (t.detail.isEmpty()) "" else ", " + L10n.tr(t.detail).lowercase()
            IconButtonTile(
                tag = "lp.sys.${t.kind.name.lowercase()}",
                description = "${L10n.tr(t.kind.label)}${if (value.isEmpty()) "" else ": $value"}$detail",
                onClick = { onOpenPanel(t.kind) },
            ) { color, dim ->
                SystemStateIcon(t.kind, snapshot, zone, color, dim, Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun RowScope.IconButtonTile(tag: String, description: String, onClick: () -> Unit, icon: @Composable (Color, Color) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.weight(1f).heightIn(min = 48.dp).clip(shape).background(LocalLp.current.surface)
            .testTag(tag)
            .semantics(mergeDescendants = true) { contentDescription = description; role = Role.Button }
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(2.dp, FocusRing, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon(LocalLp.current.ink, LocalLp.current.dim) }
}

/** The state icon for [kind] on a 24 x 24 grid; pure vector drawing, no resources. */
@Composable
fun SystemStateIcon(kind: SystemTileKind, s: SystemSnapshot, zone: ZoneId, color: Color, dim: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val k = size.minDimension / 24f
        val w = 2f * k
        val line = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * k, y * k)
        when (kind) {
            SystemTileKind.Time -> {
                drawCircle(color, 9.5f * k, p(12f, 12f), style = line)
                val t = Instant.ofEpochMilli(s.now).atZone(zone)
                val min = t.minute / 60.0; val hr = ((t.hour % 12) + min) / 12.0
                fun hand(frac: Double, len: Float) {
                    val a = frac * 2 * Math.PI
                    drawLine(color, p(12f, 12f), p(12f + len * sin(a).toFloat(), 12f - len * cos(a).toFloat()), w, StrokeCap.Round)
                }
                hand(hr, 4.5f); hand(min, 7f)
            }
            SystemTileKind.Battery -> {
                val level = (s.batteryPercent.coerceIn(0, 100)) / 100f
                val low = s.batteryPercent in 0..15 && !s.charging
                val fill = if (low) Color(0xFFE5484D) else color
                drawRoundRect(color, p(2f, 7f), Size(17f * k, 10f * k), CornerRadius(2.5f * k), style = Stroke(1.6f * k))
                drawRoundRect(color, p(20f, 10.5f), Size(2f * k, 3f * k), CornerRadius(1f * k))
                if (s.batteryPercent >= 0) drawRoundRect(fill, p(3.8f, 8.8f), Size(13.4f * k * level, 6.4f * k), CornerRadius(1f * k))
                if (s.charging) {
                    val bolt = Path().apply { moveTo(12.5f * k, 6f * k); lineTo(8f * k, 12.6f * k); lineTo(11.3f * k, 12.6f * k); lineTo(10.2f * k, 18f * k); lineTo(15f * k, 11.2f * k); lineTo(11.6f * k, 11.2f * k); close() }
                    drawPath(bolt, Color.Black.copy(alpha = 0.85f)); drawPath(bolt, Color.White.copy(alpha = 0.9f), style = Stroke(0.8f * k))
                    drawPath(bolt, Color(0xFFFFC53D))
                }
            }
            SystemTileKind.Connectivity -> when (s.connectivity) {
                Connectivity.Wifi, Connectivity.None -> {
                    val c = if (s.connectivity == Connectivity.None) dim else color
                    for (r in listOf(10f, 6.6f, 3.3f)) drawArc(c, 225f, 90f, false, p(12f - r, 19f - r), Size(2 * r * k, 2 * r * k), style = line)
                    drawCircle(c, 1.4f * k, p(12f, 18.4f))
                    if (s.connectivity == Connectivity.None) drawLine(color, p(4f, 4f), p(20f, 20f), w, StrokeCap.Round)
                }
                Connectivity.Cellular -> for (i in 0..3) {
                    val h = (5 + i * 4).toFloat()
                    drawRoundRect(color, p(4f + i * 4.5f, 20f - h), Size(3f * k, h * k), CornerRadius(1f * k))
                }
                Connectivity.Ethernet -> {
                    drawRoundRect(color, p(4f, 6f), Size(16f * k, 9f * k), CornerRadius(2f * k), style = line)
                    drawLine(color, p(12f, 15f), p(12f, 20f), w, StrokeCap.Round)
                    for (x in listOf(8f, 12f, 16f)) drawLine(color, p(x, 6f), p(x, 10f), 1.4f * k, StrokeCap.Round)
                }
                Connectivity.Other -> {
                    drawCircle(color, 8f * k, p(12f, 12f), style = line)
                    drawCircle(color, 2.2f * k, p(12f, 12f))
                }
            }
            SystemTileKind.Sound -> {
                val speaker = Path().apply { moveTo(3f * k, 9f * k); lineTo(7f * k, 9f * k); lineTo(12f * k, 4.5f * k); lineTo(12f * k, 19.5f * k); lineTo(7f * k, 15f * k); lineTo(3f * k, 15f * k); close() }
                when (s.sound) {
                    SoundState.Normal -> {
                        drawPath(speaker, color)
                        drawArc(color, -45f, 90f, false, p(9f, 8f), Size(8f * k, 8f * k), style = line)
                        drawArc(color, -45f, 90f, false, p(6f, 4f), Size(14f * k, 16f * k), style = line)
                    }
                    SoundState.Vibrate -> {
                        drawRoundRect(color, p(8f, 3f), Size(8f * k, 18f * k), CornerRadius(2f * k), style = line)
                        drawLine(color, p(4f, 8f), p(4f, 16f), w, StrokeCap.Round); drawLine(color, p(20f, 8f), p(20f, 16f), w, StrokeCap.Round)
                        drawLine(color, p(11f, 18f), p(13f, 18f), w, StrokeCap.Round)
                    }
                    SoundState.Silent -> {
                        drawPath(speaker, dim)
                        drawLine(color, p(15f, 9f), p(21f, 15f), w, StrokeCap.Round); drawLine(color, p(21f, 9f), p(15f, 15f), w, StrokeCap.Round)
                    }
                }
            }
        }
    }
}

/** The live snapshot, refreshed every 20 seconds while composed. */
@Composable
fun rememberSystemSnapshot(): SystemSnapshot {
    val ctx = LocalContext.current
    val s by produceState(SystemStateLogic.read(ctx)) {
        while (true) { delay(20_000); value = SystemStateLogic.read(ctx) }
    }
    return s
}

/** Starts the public system panel for [kind]; tiles whose panel nothing handles simply do nothing. */
fun openSystemPanel(ctx: android.content.Context, kind: SystemTileKind) {
    SystemStateLogic.intentFor(ctx, kind)?.let { runCatching { ctx.startActivity(it) } }
}
