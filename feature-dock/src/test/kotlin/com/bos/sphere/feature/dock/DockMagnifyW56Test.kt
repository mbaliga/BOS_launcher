package com.bos.sphere.feature.dock

import com.bos.sphere.core.data.DockStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Wave 56 (dockMagnify): the kernel, the spread, the clamp, the hit-test mapping, the tooltip placement and the settings model. Pure JVM. */
class DockMagnifyW56Test {
    private val pitch = 60f
    private val half = 26f
    private val iconHalf = 20f

    /** [n] slots 60 apart starting at 40, ids 1..n, ascending physical positions. */
    private fun row(n: Int, firstId: Int = 1) = (0 until n).map { 40f + it * pitch }.toFloatArray() to IntArray(n) { firstId + it }

    private fun lay(n: Int, pointer: Float, factor: Float = 1.6f, amount: Float = 1f, lo: Float = -10_000f, hi: Float = 10_000f, magnifiable: BooleanArray = BooleanArray(n) { true }): DockMagnify.Result {
        val (c, ids) = row(n)
        return DockMagnify.compute(ids, c, FloatArray(n) { half }, magnifiable, pointer, factor, amount, pitch, 4f, lo, hi)
    }

    // --- the kernel -------------------------------------------------------------------------------------------------------------------------

    @Test fun DCK_04_mag_01_theKernelIsOneAtThePointerZeroAtTwoAndAHalfIconsAndSymmetric() {
        val r = DockMagnify.radius(pitch)
        assertEquals(2.5f * 60f, r, 0f)
        assertEquals(1f, DockMagnify.weight(0f, r), 1e-6f)
        assertEquals(0f, DockMagnify.weight(r, r), 1e-6f); assertEquals(0f, DockMagnify.weight(-r, r), 1e-6f); assertEquals(0f, DockMagnify.weight(r * 3, r), 0f)
        for (d in listOf(10f, 33f, 80f, 140f)) assertEquals(DockMagnify.weight(d, r), DockMagnify.weight(-d, r), 1e-6f)
        var last = 1f
        for (i in 1..30) { val w = DockMagnify.weight(i * r / 30f, r); assertTrue("falls off smoothly and monotonically at $i", w <= last + 1e-6f); last = w }
    }

    @Test fun DCK_04_mag_01_theSpreadIsTheIntegralOfTheKernelSoNeighboursNeverCloserThanAtRest() {
        val r = DockMagnify.radius(pitch)
        for (x in listOf(-200f, -90f, -10f, 0f, 25f, 100f, 149f)) {
            val h = 0.01f
            val slope = (DockMagnify.displacement(x + h, r) - DockMagnify.displacement(x - h, r)) / (2 * h)
            assertEquals("d displacement / dx = weight at $x", DockMagnify.weight(x, r), slope, 2e-3f)
        }
        assertEquals(0f, DockMagnify.displacement(0f, r), 1e-6f)
        assertEquals(-DockMagnify.displacement(40f, r), DockMagnify.displacement(-40f, r), 1e-5f)
        assertEquals("far away the push has saturated at half the radius", 0.5f * r, DockMagnify.displacement(10 * r, r), 1e-4f)
    }

    // --- scales and offsets for 3..12 icons -------------------------------------------------------------------------------------------------

    @Test fun DCK_04_mag_02_theIconUnderThePointerGetsTheFullFactorAndFarOnesStayAtOneFor3To12Icons() {
        for (n in 3..12) for (factor in listOf(1.3f, 1.6f, 2.0f)) {
            val (c, _) = row(n)
            val k = n / 2
            val r = lay(n, c[k], factor)
            assertEquals("n=$n factor=$factor: the pointer's icon", factor, r.scale[k], 1e-4f)
            for (i in 0 until n) {
                assertTrue("n=$n i=$i: between 1 and the factor", r.scale[i] in 1f..factor + 1e-4f)
                if (abs(c[i] - c[k]) >= DockMagnify.radius(pitch)) assertEquals("n=$n i=$i: out of range stays at rest size", 1f, r.scale[i], 1e-5f)
            }
            if (k + 1 < n) assertTrue(r.scale[k] > r.scale[k + 1]); if (k > 0) assertTrue(r.scale[k] > r.scale[k - 1])
            assertEquals("symmetric around the middle icon", r.scale[k + 1], r.scale[k - 1], 1e-4f)
        }
    }

    @Test fun DCK_04_mag_02_aPointerBetweenTwoIconsGrowsBothEqually() {
        val (c, _) = row(6)
        val r = lay(6, (c[2] + c[3]) / 2f)
        assertEquals(r.scale[2], r.scale[3], 1e-4f)
        assertTrue(r.scale[2] > 1.4f && r.scale[2] < 1.6f)
    }

    @Test fun DCK_04_mag_02_noAmountMeansRestAndHalfAmountIsInBetween() {
        val (c, _) = row(8)
        val rest = lay(8, c[3], amount = 0f)
        for (i in 0 until 8) { assertEquals(1f, rest.scale[i], 0f); assertEquals(0f, rest.offset[i], 0f); assertEquals(0f, rest.lift[i], 0f) }
        val full = lay(8, c[3]); val mid = lay(8, c[3], amount = 0.5f)
        assertEquals(1.3f, mid.scale[3], 1e-4f); assertTrue(mid.offset[5] > 0f && mid.offset[5] < full.offset[5])
    }

    @Test fun DCK_04_mag_02_aNonMagnifiableMemberIsPushedButNotScaled() {
        val flags = BooleanArray(6) { it != 3 }
        val (c, _) = row(6)
        val r = lay(6, c[2], magnifiable = flags)
        assertEquals("a hosted widget keeps its size", 1f, r.scale[3], 0f)
        assertTrue("but it makes room like the rest", r.offset[3] > 0f)
        assertEquals(0f, r.lift[3], 0f)
    }

    // --- spreading ------------------------------------------------------------------------------------------------------------------------

    @Test fun DCK_04_mag_03_neighboursSpreadToMakeRoomWithoutTheIconsTouchingFor3To12Icons() {
        for (n in 3..12) for (factor in listOf(1.3f, 1.6f, 2.0f)) for (p in listOf(0f, 0.37f, 0.5f, 1f)) {
            val (c, _) = row(n)
            val pointer = c[0] + p * (c[n - 1] - c[0])
            val r = lay(n, pointer, factor)
            for (i in 0 until n - 1) {
                val gap = r.center(i + 1) - r.center(i)
                assertTrue("n=$n f=$factor p=$p i=$i: never closer than at rest ($gap)", gap >= pitch - 1e-3f)
                val needed = iconHalf * (r.scale[i] + r.scale[i + 1])
                assertTrue("n=$n f=$factor p=$p i=$i: the 40 dp icons do not overlap ($gap < $needed)", gap >= needed)
            }
            assertTrue("the row is never narrower than at rest", r.end - r.start >= r.baseEnd - r.baseStart - 1e-3f)
        }
    }

    @Test fun DCK_04_mag_03_thePointOnTheIconUnderThePointerStaysUnderIt() {
        val (c, _) = row(7)
        val r = lay(7, c[3] + 9f)
        assertEquals("the icon under the finger keeps its place (the stretch is centred on the pointer)", c[3], r.center(3), 14f)
        val under = r.indexAt(c[3] + 9f)
        assertEquals(3, under)
        assertEquals(c[3], c[3] + r.offset[3], 12f)
    }

    // --- clamping inside bounds ----------------------------------------------------------------------------------------------------------------

    @Test fun DCK_04_mag_04_theRowStaysInsideTheRoomItHasEvenAtTheEnds() {
        for (n in 3..12) for (factor in listOf(1.3f, 1.6f, 2.0f)) for (end in listOf(0, n - 1)) {
            val (c, _) = row(n)
            val lo = c[0] - half - 10f - 20f      // 20 dp of slack at each end over the resting row
            val hi = c[n - 1] + half + 10f + 20f
            val r = lay(n, c[end], factor, lo = lo, hi = hi)
            assertTrue("n=$n f=$factor end=$end: start ${r.start} >= $lo", r.start >= lo - 0.1f)
            assertTrue("n=$n f=$factor end=$end: end ${r.end} <= $hi", r.end <= hi + 0.1f)
            for (i in 0 until n) assertTrue("scales still in range", r.scale[i] in 1f..factor + 1e-4f)
            for (i in 0 until n - 1) assertTrue("still no overlap after clamping ($n/$factor/$end/$i)", r.center(i + 1) - r.center(i) >= iconHalf * (r.scale[i] + r.scale[i + 1]) - 0.01f)
        }
    }

    @Test fun DCK_04_mag_04_whenThereIsRoomTheSpreadIsNotShrunk() {
        val (c, _) = row(5)
        val free = lay(5, c[2], 2.0f)
        val roomy = lay(5, c[2], 2.0f, lo = free.start - 1f, hi = free.end + 1f)
        assertEquals(free.scale[2], roomy.scale[2], 1e-5f)
        for (i in 0 until 5) assertEquals(free.offset[i], roomy.offset[i], 1e-3f)
    }

    // W60 (dockMagnifyFix): this used to assert the OPPOSITE — that a row already wider than its room at rest (a scrolling dock) skipped
    // containment and was left to spread unclamped ("the viewport clips it"). The owner's screenshot showed that reasoning does not hold for the
    // real UI (a clip only hides a GPU layer's pixels at a hard edge; it does not stop the icon from visibly bleeding past the dock's own rounded
    // background, which is drawn as part of the same layer tree, not as a clip boundary). `compute` now always contains the spread; see
    // `DockMagnifyFixW60Test` for the full matrix.
    @Test fun DCK_04_mag_04_aDockThatAlreadyScrollsIsStillHeldInsideItsRoom() {
        val (c, _) = row(10)
        val lo = 0f; val hi = 300f // the row is wider than the viewport already
        val pointer = (lo + hi) / 2f // central: it can still hold its own fully-grown icon (26 * 2.0 = 52, well under the 150 dp either side)
        val r = DockMagnify.compute(row(10).second, c, FloatArray(10) { half }, BooleanArray(10) { true }, pointer, 2.0f, 1f, pitch, 4f, lo, hi)
        assertTrue("start inside the room", r.start >= lo - DockMagnify.EPS)
        assertTrue("end inside the room", r.end <= hi + DockMagnify.EPS)
    }

    // --- side docks and right-to-left ---------------------------------------------------------------------------------------------------------

    @Test fun DCK_04_mag_05_aSideDockUsesTheSameMathsAlongYSoItBehavesLikeTheBottomDock() {
        // a side dock's main axis is y: the same arrays describe it
        val (c, ids) = row(6)
        val bottom = lay(6, c[2])
        val side = DockMagnify.compute(ids, c, FloatArray(6) { half }, BooleanArray(6) { true }, c[2], 1.6f, 1f, pitch, 4f, -10_000f, 10_000f)
        for (i in 0 until 6) { assertEquals(bottom.scale[i], side.scale[i], 0f); assertEquals(bottom.offset[i], side.offset[i], 0f) }
    }

    @Test fun DCK_04_mag_05_aRightToLeftRowIsTheSameRowSortedByPhysicalPosition() {
        // In RTL the first id sits at the largest x. The field sorts by physical centre, so ids descend along the arrays: the result mirrors the LTR one.
        val n = 6
        val (c, _) = row(n)
        val rtlIds = IntArray(n) { n - it } // id n at the smallest x
        val ltr = lay(n, c[1])
        val rtl = DockMagnify.compute(rtlIds, c, FloatArray(n) { half }, BooleanArray(n) { true }, c[1], 1.6f, 1f, pitch, 4f, -10_000f, 10_000f)
        for (i in 0 until n) assertEquals(ltr.scale[i], rtl.scale[i], 0f)
        assertEquals("the pointer over the second slot from the left is id 5 in RTL", 5, rtl.ids[rtl.indexAt(c[1])])
        assertEquals(rtlIds.indexOf(5), rtl.indexOfId(5))
    }

    // --- the hit-test mapping -------------------------------------------------------------------------------------------------------------

    @Test fun DCK_04_mag_06_theHitTestUsesTheMagnifiedGeometryNotTheRestingOne() {
        val (c, _) = row(7)
        val r = lay(7, c[3] + 5f, 2.0f)
        // at rest, 40 dp right of icon 3's centre is icon 4's territory (the boundary is 30 dp out)
        assertEquals(4, DockMagnify.compute(row(7).second, c, FloatArray(7) { half }, BooleanArray(7) { true }, 0f, 1f, 0f, pitch, 4f, 0f, 1000f).indexAt(c[3] + 40f))
        // magnified, icon 3 is much bigger: the same point is still inside it
        assertTrue("the magnified icon 3 reaches 40 dp right of its resting centre", r.center(3) + (half + 4f) * r.scale[3] >= c[3] + 40f)
        assertEquals(3, r.indexAt(c[3] + 40f))
        // every point inside the row maps to the member whose reach holds it, and the member whose centre is nearest wins where reaches overlap
        for (p in 0..600) {
            val x = c[0] - 30f + p
            val i = r.indexAt(x)
            fun reach(j: Int) = (half + 4f) * r.scale[j]
            if (i >= 0) {
                val d = abs(x - r.center(i))
                assertTrue("the owner's reach holds the point at $x", d <= reach(i) + 1e-3f)
                for (j in 0 until 7) if (j != i && abs(x - r.center(j)) <= reach(j)) assertTrue("of the members whose reach holds $x the nearest centre wins", abs(x - r.center(j)) >= d - 1e-3f)
            } else for (j in 0 until 7) assertTrue("no member claims $x", abs(x - r.center(j)) > reach(j))
        }
        assertEquals("outside the row there is nothing", -1, r.indexAt(c[0] - 200f))
    }

    @Test fun DCK_04_mag_06_theGapBetweenTwoSlotsBelongsToTheNearerOne() {
        val (c, _) = row(4)
        val r = lay(4, c[1], 1.6f, amount = 0f)
        assertEquals(1, r.indexAt(c[1] + 29f)); assertEquals(2, r.indexAt(c[1] + 31f))
    }

    // --- the tooltip's place --------------------------------------------------------------------------------------------------------------

    @Test fun DCK_04_mag_07_theTooltipSitsAboveABottomDockIconAndPointsAtItsCentre() {
        val g = DockTooltip.place(DockEdge.Bottom, centre = 400f, pivot = 200f, extent = 128f, w = 114f, h = 56f, density = 2f, railX = 32f, railY = 400f, winW = 800f, winH = 800f)
        assertEquals(400f, g.tipX); assertTrue("the tip is above the icon's top (200 - 128)", g.tipY < 200f - 128f)
        assertTrue("the bubble is above its pointer", g.top + 56f < g.tipY)
        assertEquals("centred on the icon", 400f, g.left + 57f, 0.01f)
    }

    @Test fun DCK_04_mag_07_theTooltipStaysOnTheScreenAtTheEnds() {
        val left = DockTooltip.place(DockEdge.Bottom, 20f, 200f, 80f, 114f, 56f, 2f, railX = 32f, railY = 400f, winW = 800f, winH = 800f)
        assertTrue("left edge is inside the window: ${left.left + 32f}", left.left + 32f >= 0f)
        val right = DockTooltip.place(DockEdge.Bottom, 760f, 200f, 80f, 114f, 56f, 2f, railX = 32f, railY = 400f, winW = 800f, winH = 800f)
        assertTrue("right edge is inside the window", right.left + 32f + 114f <= 800f)
        assertEquals("the pointer still points at the icon", 760f, right.tipX)
    }

    @Test fun DCK_04_mag_07_aSideDockGetsItsTooltipTowardsTheScreenCentre() {
        val l = DockTooltip.place(DockEdge.Left, centre = 300f, pivot = 40f, extent = 100f, w = 114f, h = 56f, density = 2f, railX = 24f, railY = 100f, winW = 800f, winH = 800f)
        assertTrue("left dock: the bubble is to the right of the icon", l.left > 40f + 100f); assertEquals(300f, l.tipY)
        val r = DockTooltip.place(DockEdge.Right, centre = 300f, pivot = 500f, extent = 100f, w = 114f, h = 56f, density = 2f, railX = 600f, railY = 100f, winW = 800f, winH = 800f)
        assertTrue("right dock: the bubble is to the left of the icon", r.left + 114f < 500f - 100f)
    }

    // --- the settings model -----------------------------------------------------------------------------------------------------------------

    @Test fun DCK_04_mag_08_magnificationIsOffByDefaultMediumWhenOnAndTheThreeStrengthsAreTheOwnersNumbers() {
        val d = DockSettings()
        assertFalse(d.magnify); assertEquals(MagnifyStrength.Medium, d.magnifyStrength)
        assertEquals(listOf(1.3f, 1.6f, 2.0f), MagnifyStrength.entries.map { it.factor })
        assertEquals(listOf("Subtle", "Medium", "Large"), MagnifyStrength.entries.map { it.label })
        assertEquals(listOf("subtle", "medium", "large"), MagnifyStrength.entries.map { it.id })
    }

    @Test fun DCK_04_mag_08_theSettingIsStoredWithTheDockSettingsAndOlderFilesReadAsOff() {
        val s = DockWorkspaceState(dock = DockSettings(style = DockStyle.ThreeD, magnify = true, magnifyStrength = MagnifyStrength.Large), workspaces = listOf(Workspace("default", "Home")))
        val text = WorkspaceJson.encode(s)
        val back = (WorkspaceJson.decode(text) as WorkspaceJson.Decoded.Ok).state.dock
        assertTrue(back.magnify); assertEquals(MagnifyStrength.Large, back.magnifyStrength); assertEquals(DockStyle.ThreeD, back.style)
        val o = org.json.JSONObject(text); o.getJSONObject("dock").remove("magnify"); o.getJSONObject("dock").remove("magnifyStrength")
        val old = (WorkspaceJson.decode(o.toString()) as WorkspaceJson.Decoded.Ok).state.dock
        assertFalse("a file from before wave 56 reads as off", old.magnify); assertEquals(MagnifyStrength.Medium, old.magnifyStrength)
        o.getJSONObject("dock").put("magnifyStrength", "enormous").put("magnify", true)
        val odd = (WorkspaceJson.decode(o.toString()) as WorkspaceJson.Decoded.Ok).state.dock
        assertTrue(odd.magnify); assertEquals("an unknown strength from a newer build reads as Medium", MagnifyStrength.Medium, odd.magnifyStrength)
    }

    @Test fun DCK_04_mag_08_theHeadroomAndSlackGrowWithTheStrength() {
        assertTrue(DockMagnify.slackDp(MagnifyStrength.Subtle) < DockMagnify.slackDp(MagnifyStrength.Medium)); assertTrue(DockMagnify.slackDp(MagnifyStrength.Medium) < DockMagnify.slackDp(MagnifyStrength.Large))
        assertTrue("the grown icon of the largest strength fits in its headroom plus the 16 dp already above it", DockMagnify.headDp(MagnifyStrength.Large) + 16f >= (2.0f - 1f) * DockMagnify.ICON_DP + DockMagnify.RISE_DP)
        assertNotEquals(DockMagnify.headDp(MagnifyStrength.Subtle), DockMagnify.headDp(MagnifyStrength.Large))
    }
}
