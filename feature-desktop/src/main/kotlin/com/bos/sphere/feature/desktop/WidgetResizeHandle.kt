package com.bos.sphere.feature.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Test tag of the resize handle of the widget item [itemId]. */
fun resizeHandleTag(itemId: String) = "desktop.resize.$itemId"

/**
 * HOM-13: the drag handle at a hosted widget's bottom-end corner. It reports the drag in layout dp (pixels divided by [scale]):
 * [onPreview] with the total so far while dragging, [onCommit] with the total when the finger lifts, [onCancel] when it is cancelled.
 * The handle is the widget's own, so the hosted view (which takes the touches that start on it) is never involved.
 */
@Composable
fun WidgetResizeHandle(scale: Float, onPreview: (dxDp: Float, dyDp: Float) -> Unit, onCommit: (dxDp: Float, dyDp: Float) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier, key: Any? = null) {
    val preview by rememberUpdatedState(onPreview)
    val commit by rememberUpdatedState(onCommit)
    val cancel by rememberUpdatedState(onCancel)
    val sc = scale.coerceAtLeast(0.01f)
    Box(
        modifier.size(28.dp).testTag(key?.let { resizeHandleTag(it.toString()) } ?: "desktop.resize").clip(RoundedCornerShape(topStart = 12.dp, bottomEnd = 12.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(sc) {
                var dx = 0f; var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDragEnd = { commit(dx / sc, dy / sc) },
                    onDragCancel = { cancel() },
                ) { change, d -> change.consume(); dx += d.x; dy += d.y; preview(dx / sc, dy / sc) }
            },
    ) {
        // Two short diagonal ticks read as a resize corner without needing an icon font.
        Box(Modifier.align(Alignment.BottomEnd).size(10.dp, 2.dp).background(Color.White.copy(alpha = 0.85f)))
        Box(Modifier.align(Alignment.BottomEnd).size(2.dp, 10.dp).background(Color.White.copy(alpha = 0.85f)))
    }
}
