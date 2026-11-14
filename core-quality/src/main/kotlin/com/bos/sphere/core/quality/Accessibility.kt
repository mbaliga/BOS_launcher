package com.bos.sphere.core.quality

import kotlin.math.atan2

/** NFR-13: pure helpers for generic accessibility checks that other surfaces call from their tests. */
object Accessibility {
    const val MIN_TOUCH_TARGET_DP = 48f

    fun meetsTouchTarget(widthDp: Float, heightDp: Float): Boolean = widthDp >= MIN_TOUCH_TARGET_DP && heightDp >= MIN_TOUCH_TARGET_DP

    data class Spot(val id: String, val x: Float, val y: Float)

    /** Linear reading order for a free-form layout: rows top to bottom (items within [rowTolerance] share a row), then left to right. */
    fun readingOrder(spots: List<Spot>, rowTolerance: Float = 24f): List<String> {
        val byY = spots.sortedBy { it.y }
        val rows = mutableListOf<MutableList<Spot>>()
        for (s in byY) {
            val row = rows.lastOrNull()
            if (row != null && s.y - row.first().y <= rowTolerance) row += s else rows += mutableListOf(s)
        }
        return rows.flatMap { r -> r.sortedBy { it.x } }.map { it.id }
    }

    /** Linear order for items on a ring or orbit around ([cx],[cy]): clockwise starting from the top. */
    fun clockwiseOrder(spots: List<Spot>, cx: Float, cy: Float): List<String> =
        spots.sortedBy { s ->
            val a = Math.toDegrees(atan2((s.x - cx).toDouble(), (cy - s.y).toDouble()))
            if (a < 0) a + 360.0 else a
        }.map { it.id }
}
