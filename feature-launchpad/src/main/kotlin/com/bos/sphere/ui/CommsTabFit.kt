package com.bos.sphere.ui

/**
 * Wave 33 (commsDebt): how the Comms tab row (ALL UNREAD FILES TIMELINE) fits a narrow row, at a Comms size of 125 percent or a large font. Kept pure so the
 * rules are tested on the JVM. First the labels keep the mock-up's slots; when the slots do not fit they give up their minimum width (a tab is its label
 * and padding); when that does not fit either the letters tighten, down to [MAX_TIGHTEN_EM] of an em each; and when even that does not fit the row scrolls
 * horizontally (the selected tab is scrolled into sight by the row).
 */
internal object CommsTabFit {
    /** Tightest letter spacing, in em (negative: closer letters). About 1 sp at the 17 sp tab size. */
    const val MAX_TIGHTEN_EM = 0.06f

    class Plan(
        /** Extra letter spacing in sp (zero or negative). */
        val trackingSp: Float,
        /** True when the mock-up's slot widths are kept. */
        val useSlots: Boolean,
        /** True when the row still does not fit and must scroll. */
        val scrolls: Boolean,
    )

    /**
     * @param labelPx each label's width in px at no extra spacing
     * @param chars each label's length (spacing is added per letter)
     * @param slotPx each tab's minimum width (the mock-up slot) in px
     * @param targetPx the least width of a tab without its slot (the 48 dp touch target); @param padPx a tab's padding on each side, @param fixedPx the start and end spacers of the row
     * @param availablePx the row's width; @param spPx one sp in px
     */
    fun plan(labelPx: List<Float>, chars: List<Int>, slotPx: List<Float>, padPx: Float, fixedPx: Float, availablePx: Float, spPx: Float, tabSp: Float, targetPx: Float = 0f): Plan {
        fun total(slots: Boolean, trackPx: Float) = fixedPx + labelPx.indices.sumOf { i ->
            val w = labelPx[i] + trackPx * chars[i] + 2 * padPx
            (if (slots) maxOf(w, slotPx.getOrElse(i) { 0f }) else maxOf(w, targetPx)).toDouble()
        }.toFloat()
        if (total(true, 0f) <= availablePx) return Plan(0f, true, false)
        if (total(false, 0f) <= availablePx) return Plan(0f, false, false)
        val minTrack = -MAX_TIGHTEN_EM * tabSp * spPx
        val letters = chars.sum().coerceAtLeast(1)
        val need = (total(false, 0f) - availablePx) / letters // px per letter to take away
        return if (need <= -minTrack) Plan(-need / spPx, false, false) else Plan(minTrack / spPx, false, true)
    }
}
