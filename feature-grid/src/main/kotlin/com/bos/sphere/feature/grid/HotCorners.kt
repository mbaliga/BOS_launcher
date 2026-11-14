package com.bos.sphere.feature.grid

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.input.HotCornerId
import com.bos.sphere.core.input.HotCornerTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the home gives the hot-corner layer: the dwell, the label of each corner's action and what a click does. */
class HotCornerBinding(
    val dwellMs: Long,
    val label: (HotCornerId) -> String?,
    val onActivate: (HotCornerId) -> Unit,
)

fun Corner.toHot(): HotCornerId = when (this) {
    Corner.TopLeft -> HotCornerId.TopLeft
    Corner.TopRight -> HotCornerId.TopRight
    Corner.BottomRight -> HotCornerId.BottomRight
    Corner.BottomLeft -> HotCornerId.BottomLeft
}

fun HotCornerId.toCorner(): Corner = when (this) {
    HotCornerId.TopLeft -> Corner.TopLeft
    HotCornerId.TopRight -> Corner.TopRight
    HotCornerId.BottomRight -> Corner.BottomRight
    HotCornerId.BottomLeft -> Corner.BottomLeft
}

/**
 * Hot corners for a pointer (INP-16). Put on the root, above the home: it only observes (Initial pass), so touch and
 * every other pointer behaviour are untouched, except that a click on a revealed corner is claimed. Mouse only; a finger never
 * reveals a corner and nothing happens while a button is held (a drag).
 */
fun Modifier.hotCorners(): Modifier = composed {
    val binding = LauncherInput.hotCorner.value
    val tracker = remember { HotCornerTracker() }
    var revealed by remember { mutableStateOf<HotCornerId?>(null) }
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    this
        .pointerInput(binding) {
            if (binding == null || binding.dwellMs <= 0) { tracker.reset(); revealed = null; return@pointerInput }
            tracker.dwellMs = binding.dwellMs
            val zone = HotCornerTracker.DEFAULT_ZONE_DP * density
            var timer: Job? = null
            awaitPointerEventScope {
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val c = ev.changes.firstOrNull() ?: continue
                    if (c.type != PointerType.Mouse) { tracker.reset(); revealed = null; continue }
                    val dragging = ev.changes.any { it.pressed } && ev.type != PointerEventType.Press
                    if (ev.type == PointerEventType.Press && ev.buttons.isPrimaryPressed) {
                        tracker.onClick()?.let { hot ->
                            c.consume()
                            tracker.reset(); revealed = null
                            binding.onActivate(hot)
                        }
                        continue
                    }
                    if (ev.type == PointerEventType.Exit) { timer?.cancel(); tracker.reset(); revealed = null; continue }
                    val zoneId = HotCornerTracker.zoneAt(c.position.x, c.position.y, size.width.toFloat(), size.height.toFloat(), zone)
                    val now = c.uptimeMillis
                    tracker.onPointer(zoneId?.takeIf { binding.label(it) != null }, now, dragging)
                    revealed = tracker.revealed
                    timer?.cancel()
                    tracker.pendingMs(now)?.let { ms ->
                        timer = scope.launch {
                            delay(ms)
                            tracker.tick(now + ms)
                            revealed = tracker.revealed
                        }
                    }
                }
            }
        }
        .drawWithContent {
            drawContent()
            val r = revealed ?: return@drawWithContent
            val text = binding?.label?.invoke(r) ?: return@drawWithContent
            val radius = 96.dp.toPx()
            val x = if (r == HotCornerId.TopRight || r == HotCornerId.BottomRight) size.width else 0f
            val y = if (r == HotCornerId.BottomLeft || r == HotCornerId.BottomRight) size.height else 0f
            drawCircle(HyleColors.Violet.copy(alpha = 0.35f), radius = radius, center = Offset(x, y))
            drawCircle(HyleColors.InkBright.copy(alpha = 0.5f), radius = radius, center = Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f * density))
            val layout = measurer.measure(text, TextStyle(color = Color.White, fontSize = 12.sp))
            val tx = if (x > 0f) x - layout.size.width - 14.dp.toPx() else 14.dp.toPx()
            val ty = if (y > 0f) y - layout.size.height - 14.dp.toPx() else 14.dp.toPx()
            drawText(layout, topLeft = Offset(tx, ty))
        }
}
