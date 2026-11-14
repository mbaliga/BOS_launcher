package com.bos.sphere.feature.dock

import android.graphics.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Remembers where the last finger or pointer went down, without consuming it, so a launch can open out of the item that was
 * tapped (WSP-06) even where the item's own tap callback has no position.
 */
class LastTouch {
    @Volatile private var at: Offset? = null
    internal fun set(o: Offset) { at = o }

    /** A square around the last press, as the source rectangle for the launch animation; null if nothing was pressed yet. */
    fun sourceRect(halfSizePx: Int = 48): Rect? = at?.let { Rect((it.x - halfSizePx).toInt(), (it.y - halfSizePx).toInt(), (it.x + halfSizePx).toInt(), (it.y + halfSizePx).toInt()) }
}

fun Modifier.trackLastTouch(holder: LastTouch): Modifier = pointerInput(holder) {
    awaitPointerEventScope {
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            ev.changes.firstOrNull { it.pressed && !it.previousPressed }?.let { holder.set(it.position) }
        }
    }
}
