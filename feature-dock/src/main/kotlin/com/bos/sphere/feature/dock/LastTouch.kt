package com.bos.sphere.feature.dock

import android.graphics.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.bos.sphere.core.input.FRect

/**
 * Remembers where the last finger or pointer went down, without consuming it, so a launch can open out of the item that was
 * tapped (WSP-06) even where the item's own tap callback has no position.
 */
class LastTouch(
    private val origin: () -> Pair<Int, Int> = { 0 to 0 },
    /** Rectangles (window coordinates) of the registered home items; the one under the press becomes the animation source (WSP-06). */
    private val itemRects: () -> List<FRect> = { emptyList() },
) {
    @Volatile private var at: Offset? = null
    internal fun set(o: Offset) { at = o }

    /** A square around the last press in screen coordinates (the window origin is added), as the source rectangle for the launch animation; null if nothing was pressed yet. */
    fun sourceRect(halfSizePx: Int = 48): Rect? = sourceLtrb(halfSizePx)?.let { Rect(it[0], it[1], it[2], it[3]) }

    /** [sourceRect] as left, top, right, bottom, so the arithmetic is testable without the Android Rect. */
    internal fun sourceLtrb(halfSizePx: Int = 48): IntArray? = at?.let {
        val (ox, oy) = origin()
        // The item's own rectangle when one is registered under the press; otherwise a square around the press.
        SourcePick.itemUnder(it.x, it.y, itemRects())?.let { r ->
            return intArrayOf(r.left.toInt() + ox, r.top.toInt() + oy, r.right.toInt() + ox, r.bottom.toInt() + oy)
        }
        intArrayOf((it.x - halfSizePx).toInt() + ox, (it.y - halfSizePx).toInt() + oy, (it.x + halfSizePx).toInt() + ox, (it.y + halfSizePx).toInt() + oy)
    }
}

fun Modifier.trackLastTouch(holder: LastTouch): Modifier = pointerInput(holder) {
    awaitPointerEventScope {
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            ev.changes.firstOrNull { it.pressed && !it.previousPressed }?.let { holder.set(it.position) }
        }
    }
}

/** Picks the launch animation source (WSP-06). Pure, so the choice is testable without a device. */
object SourcePick {
    /**
     * The smallest registered rectangle that contains the press. Large containers (a whole page, a panel) are ignored: a
     * rectangle bigger than [maxAreaPx] is not an item, so a press on empty space falls back to the square around the press.
     */
    fun itemUnder(x: Float, y: Float, rects: List<FRect>, maxAreaPx: Float = 400_000f): FRect? =
        rects.filter { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom && area(it) in 1f..maxAreaPx }.minByOrNull(::area)

    private fun area(r: FRect) = (r.right - r.left) * (r.bottom - r.top)
}
