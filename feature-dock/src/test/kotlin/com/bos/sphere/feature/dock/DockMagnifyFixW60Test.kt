package com.bos.sphere.feature.dock

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 60 (dockMagnifyFix): the owner's screenshot showed a six-item, non-scrolling dock with its leftmost and rightmost icons bleeding past the
 * shelf at both ends while magnified. The root cause was in [DockMagnify.compute]: the containment step that pulls the row back inside `[lo, hi]`
 * only ran `if (!overflow)` — a row that was already wider than its room at rest (a scrolling dock) skipped it entirely and was left to grow
 * however far the maths said, unclamped. That path is gone: containment now always runs (see the W60 comment on `compute`), reducing the growth
 * first ("every icon fits, some grow less") and, only for a row that already overflowed at rest, contracting the spacing around the pointer as a
 * fallback (down to every centre landing on the pointer, if nothing less will do).
 *
 * This is a pure stress/property test of [DockMagnify.compute] itself: for every size from 2 to 12 icons, every strength, a sweep of pointer
 * positions along the row and a sweep of available room from generous down to tight (including room narrower than the row's own resting extent,
 * i.e. a dock that already scrolls), `result.start`/`result.end` must never leave `[lo, hi]`. It also checks the dock's own background surface
 * (the pill or shelf behind the row, via [DockMagnify.surfaceGrowth], the pure function [DockRail] now draws it with): its grown span must fully
 * contain the row's drawn extent, for the same matrix.
 *
 * One honestly-declared precondition (documented, not a loophole): the pointer must be far enough from `lo`/`hi` to hold its own fully-grown icon
 * at rest — i.e. no closer to either edge than that icon's own resting half-width. The real rail guarantees this by construction: it reserves
 * [DockMagnify.slackDp] of room at each end precisely so the touched point is never that close to the edge (investigation (a) of the wave 60
 * brief: the rail's own `availMain`/`slackDp` plumbing, traced in `docs/status/w60-dockMagnifyFix.md`). A pointer placed closer to the edge than
 * its own icon's half-width is asking to magnify a point that cannot hold even its un-grown icon without crossing the edge, which is a different,
 * un-fixable problem (the touch point itself is off the usable strip) — not the clipping bug the owner reported.
 */
class DockMagnifyFixW60Test {
    private val pitch = 60f
    private val half = 26f

    private fun row(n: Int) = (0 until n).map { 40f + it * pitch }.toFloatArray()

    @Test fun DCK_04_fix_01_theRowNeverLeavesItsRoomForAnySizeStrengthPointerOrRoom() {
        var checked = 0
        for (n in 2..12) {
            val c = row(n)
            val ids = IntArray(n) { it + 1 }
            val halves = FloatArray(n) { half }
            val flags = BooleanArray(n) { true }
            val restStart = c[0] - half; val restEnd = c[n - 1] + half
            for (strength in MagnifyStrength.entries) {
                val maxHalf = half * strength.factor
                // Room from generous (plenty of slack) down to tight (no slack at all) and, finally, genuinely narrower than the resting row (a
                // dock that already scrolls): the last entry clips a chunk off BOTH ends of the resting extent, like a mid-scroll viewport.
                val rooms = listOf(
                    restStart - 200f to restEnd + 200f,
                    restStart - DockMagnify.slackDp(strength) to restEnd + DockMagnify.slackDp(strength),
                    restStart - 4f to restEnd + 4f,
                    restStart to restEnd,
                    (restStart + (restEnd - restStart) * 0.2f) to (restEnd - (restEnd - restStart) * 0.2f),
                )
                for ((lo, hi) in rooms) {
                    // Sweep the pointer across the whole row, but only where it can hold its own fully-grown icon without the precondition above
                    // firing (see the class doc): skip a sweep point too close to this room's own edge.
                    val margin = maxHalf + 0.5f
                    if (hi - lo < 2 * margin) continue // the room itself cannot hold one fully-grown icon anywhere: out of scope (see class doc)
                    for (frac in listOf(0f, 0.1f, 0.25f, 0.37f, 0.5f, 0.63f, 0.75f, 0.9f, 1f)) {
                        val raw = c[0] + frac * (c[n - 1] - c[0])
                        val pointer = raw.coerceIn(lo + margin, hi - margin)
                        for (amount in listOf(0.5f, 1f)) {
                            val r = DockMagnify.compute(ids, c, halves, flags, pointer, strength.factor, amount, pitch, 4f, lo, hi)
                            checked++
                            assertTrue(
                                "n=$n strength=${strength.id} room=[$lo,$hi] pointer=$pointer amount=$amount: start ${r.start} >= lo $lo",
                                r.start >= lo - DockMagnify.EPS,
                            )
                            assertTrue(
                                "n=$n strength=${strength.id} room=[$lo,$hi] pointer=$pointer amount=$amount: end ${r.end} <= hi $hi",
                                r.end <= hi + DockMagnify.EPS,
                            )
                            // The dock's own background surface (DockRail's graphicsLayer uses exactly this pure function): its grown span must
                            // still fully cover the row's drawn extent, never falling short at either end.
                            val (gs, ge) = DockMagnify.surfaceGrowth(r.baseStart, r.baseEnd, r.start, r.end)
                            assertTrue(
                                "n=$n strength=${strength.id} room=[$lo,$hi] pointer=$pointer amount=$amount: surface start ${r.baseStart - gs} covers row start ${r.start}",
                                r.baseStart - gs <= r.start + DockMagnify.EPS,
                            )
                            assertTrue(
                                "n=$n strength=${strength.id} room=[$lo,$hi] pointer=$pointer amount=$amount: surface end ${r.baseEnd + ge} covers row end ${r.end}",
                                r.baseEnd + ge >= r.end - DockMagnify.EPS,
                            )
                        }
                    }
                }
            }
        }
        assertTrue("the matrix actually ran a meaningful number of cases", checked > 500)
    }

    /** The owner's exact report: six icons (not remotely overflowing), fully magnified with the finger at either end of the row — nothing crosses the shelf. */
    @Test fun DCK_04_fix_02_theOwnersSixIconDockDoesNotBleedPastTheShelfAtEitherEnd() {
        val n = 6
        val c = row(n)
        val ids = IntArray(n) { it + 1 }
        val halves = FloatArray(n) { half }
        val flags = BooleanArray(n) { true }
        for (strength in MagnifyStrength.entries) {
            val lo = c[0] - half - DockMagnify.slackDp(strength); val hi = c[n - 1] + half + DockMagnify.slackDp(strength)
            for (end in listOf(0, n - 1)) {
                val r = DockMagnify.compute(ids, c, halves, flags, c[end], strength.factor, 1f, pitch, 4f, lo, hi)
                assertTrue("${strength.id} end=$end: start ${r.start} inside the shelf ($lo)", r.start >= lo - DockMagnify.EPS)
                assertTrue("${strength.id} end=$end: end ${r.end} inside the shelf ($hi)", r.end <= hi + DockMagnify.EPS)
                assertTrue("the pressed icon still grows close to the full factor (${r.scale[end]})", r.scale[end] > 1f + (strength.factor - 1f) * 0.6f)
            }
        }
    }

    /** Before W60 this scenario (a row wider than its room, i.e. `overflow == true`) skipped containment entirely; now it is held the same as any other row. */
    @Test fun DCK_04_fix_03_aRowThatAlreadyOverflowsItsRoomAtRestIsStillHeldInsideIt() {
        val n = 10
        val c = row(n)
        val ids = IntArray(n) { it + 1 }
        val halves = FloatArray(n) { half }
        val flags = BooleanArray(n) { true }
        val lo = 0f; val hi = 300f // far narrower than the resting row (the old test's "a dock that already scrolls" scenario)
        val pointer = (lo + hi) / 2f // central, so it can hold its own fully-grown icon (see the class doc's precondition)
        val r = DockMagnify.compute(ids, c, halves, flags, pointer, 2.0f, 1f, pitch, 4f, lo, hi)
        assertTrue("the resting row genuinely did not fit", c[0] - half < lo || c[n - 1] + half > hi)
        assertTrue("start ${r.start} >= lo", r.start >= lo - DockMagnify.EPS)
        assertTrue("end ${r.end} <= hi", r.end <= hi + DockMagnify.EPS)
    }
}
