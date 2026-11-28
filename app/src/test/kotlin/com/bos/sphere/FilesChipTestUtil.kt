package com.bos.sphere

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction

/** W54: the chip row's end side is taken by the pinned Add tile (chips scroll under it and fade), so a test that taps or drags a chip first scrolls it into the free stretch. */
/** The node's whole box in root coordinates, not clipped by the scrolling row (a clipped box of a chip that is out of sight is empty). */
internal fun androidx.compose.ui.semantics.SemanticsNode.fullBounds() = androidx.compose.ui.geometry.Rect(positionInRoot, androidx.compose.ui.geometry.Offset(size.width.toFloat(), size.height.toFloat()).let { androidx.compose.ui.geometry.Size(it.x, it.y) })

internal fun ComposeTestRule.revealChip(tag: String, rtl: Boolean = false, margin: Float = 40f, and: String? = null) {
    repeat(3) {
        val row = onNodeWithTag("files.sources", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val add = onNodeWithTag("files.add", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val one = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().fullBounds()
        val chip = if (and == null) one else one.let { a -> val b = onNodeWithTag(and, useUnmergedTree = true).fetchSemanticsNode().fullBounds(); androidx.compose.ui.geometry.Rect(minOf(a.left, b.left), a.top, maxOf(a.right, b.right), a.bottom) }
        val lo = if (rtl) add.right + margin else row.left + margin
        val hi = if (rtl) row.right - margin else add.left - margin
        // A positive scroll reveals what lies towards the end of the row (the right in LTR, the left in RTL).
        val need = when {
            !rtl && chip.right > hi -> chip.right - hi
            !rtl && chip.left < lo -> chip.left - lo
            rtl && chip.left < lo -> lo - chip.left
            rtl && chip.right > hi -> -(chip.right - hi)
            else -> 0f
        }
        if (need == 0f) return
        onNodeWithTag("files.sources", useUnmergedTree = true).performSemanticsAction(SemanticsActions.ScrollBy) { it(need, 0f) }
        waitForIdle()
    }
}
