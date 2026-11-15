package com.bos.sphere.core.catalog

/**
 * HOM-17: the split bar drawn inside the Usage tile on home. It is the same proportional bar the Usage panel draws, cut down
 * to what fits a tile: the biggest [MAX_SEGMENTS] slices by time, each given at least [MIN_SHARE] of the width so a thin one
 * stays visible, then scaled so the widths add up to the whole bar.
 */
object UsageTileBar {
    const val MAX_SEGMENTS = 5
    const val MIN_SHARE = 0.04f

    /** One coloured stretch of the bar: the slice's index in the panel's colour order and its share of the width (sums to 1). */
    data class Segment(val sliceIndex: Int, val share: Float)

    fun segments(slices: List<UsageSlice>): List<Segment> {
        if (slices.isEmpty()) return emptyList()
        val picked = slices.mapIndexed { i, s -> i to s }.sortedByDescending { it.second.ms }.take(MAX_SEGMENTS).sortedBy { it.first }
        val total = picked.sumOf { it.second.ms }
        if (total <= 0L) return emptyList()
        val raw = picked.map { (i, s) -> i to maxOf(s.ms.toFloat() / total, MIN_SHARE) }
        val sum = raw.sumOf { it.second.toDouble() }.toFloat()
        return raw.map { (i, w) -> Segment(i, w / sum) }
    }

    /** The tile's spoken description: the total and the top app label when [labelOf] knows it. */
    fun description(slices: List<UsageSlice>, labelOf: (String) -> String?): String {
        if (slices.isEmpty()) return ""
        val top = slices.maxByOrNull { it.ms } ?: return ""
        val name = if (top.packageName.isEmpty()) null else labelOf(top.packageName)
        return UsageSplit.duration(UsageSplit.totalMs(slices)) + (name?.let { ", $it" } ?: "")
    }
}
