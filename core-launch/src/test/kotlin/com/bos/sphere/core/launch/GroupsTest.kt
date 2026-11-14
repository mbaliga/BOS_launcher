package com.bos.sphere.core.launch

import com.bos.sphere.core.capabilities.CapabilityInputs
import com.bos.sphere.core.capabilities.DisplayInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupsTest {
    private fun app(n: Int) = LaunchRequest(LaunchTarget("pkg.$n"))
    private fun group(n: Int, layout: GroupLayout = GroupLayout.Tile) = AppGroup("g", List(n) { app(it) }, layout)
    private val full = LaunchCaps(setOf(0), setOf(0), boundsHonoured = true, adjacentSupported = true)

    @Test fun WSP_05_tileRectsCoverTheDisplayWithoutOverlap() {
        for (n in 2..4) {
            val r = GroupLayout.Tile.rects(n, 1000, 800)
            assertEquals(n, r.size)
            val area = r.sumOf { (it.right - it.left) * (it.bottom - it.top) }
            assertEquals(800_000, area)
        }
    }

    @Test fun WSP_05_boundsModeAssignsOneRectEach() {
        val p = GroupPlanner.plan(group(3), 1000, 800, full)
        assertEquals(GroupMode.Bounds, p.mode)
        assertEquals(3, p.steps.size)
        assertTrue(p.steps.all { it.bounds != null })
        assertTrue(p.switcher.isEmpty())
    }

    @Test fun WSP_05_splitModeOpensRestBeside() {
        val p = GroupPlanner.plan(group(2), 1000, 800, full.copy(boundsHonoured = false))
        assertEquals(GroupMode.Split, p.mode)
        assertEquals(Placement.Default, p.steps[0].placement)
        assertEquals(Placement.Adjacent, p.steps[1].placement)
    }

    @Test fun WSP_05_t1WithNoSplitOpensFirstAndOffersRestAsSwitcher() {
        val p = GroupPlanner.plan(group(3), 1000, 800, full.copy(boundsHonoured = false, adjacentSupported = null))
        assertEquals(GroupMode.FirstOnly, p.mode)
        assertEquals(1, p.steps.size)
        assertEquals(listOf(app(1), app(2)), p.switcher)
    }

    @Test fun WSP_05_runnerRechecksEachResultAndRetriesOnce() = runBlocking {
        val attempts = mutableMapOf<String, Int>()
        val plan = GroupPlanner.plan(group(3), 1000, 800, full)
        val res = Resolution(null, null, false, emptyList())
        val out = GroupRunner.run(plan, 0, { }) { r ->
            val n = attempts.merge(r.target.packageName, 1, Int::plus)!!
            // pkg.1 succeeds only on retry, pkg.2 never starts.
            LaunchResult(r.target.packageName == "pkg.0" || (r.target.packageName == "pkg.1" && n >= 2), res)
        }
        assertEquals(2, attempts["pkg.1"]); assertEquals(2, attempts["pkg.2"])
        assertEquals(listOf(2), out.failed)
        assertEquals(3, out.results.size)
    }

    @Test fun WSP_05_aGroupHasTwoToFourApps() {
        assertFalse(runCatching { group(5) }.isSuccess)
        assertTrue(runCatching { group(4) }.isSuccess)
    }

    @Test fun WSP_06_sourceRectIsRelativeToTheWindow() {
        assertEquals(Bounds(10, 20, 110, 120), SourceRects.relativeTo(Bounds(110, 220, 210, 320), 100, 200))
    }

    private val displays = listOf(DisplayRef(0, "Built-in"), DisplayRef(5, "Desk monitor"))

    @Test fun WSP_09_sessionCapturesDisplayTargetsAndSaysAppsOnly() {
        val s = SessionRestorer.capture("s1", "Desk", group(2), { if (it == 1) displays[1] else null }, 0L)
        assertNull(s.entries[0].displayName); assertEquals("Desk monitor", s.entries[1].displayName)
        assertTrue(SessionRestorer.plan(s, displays).notice.contains("not restored"))
    }

    @Test fun WSP_10_missingDisplayFallsBackToCurrentAndReports() {
        val s = SessionRestorer.capture("s1", "Desk", group(2), { displays[1] }, 0L)
        val r = SessionRestorer.plan(s, listOf(displays[0]))
        assertTrue(r.plan.steps.all { it.displayId == null })
        assertEquals(1, r.report.size)
        assertTrue(r.report.single().contains("Desk monitor"))
    }

    @Test fun WSP_10_connectedDisplayIsMatchedByNameEvenWhenIdChanged() {
        val s = Session("s", "n", listOf(SessionEntry(LaunchTarget("a"), displayId = 5, displayName = "Desk monitor")))
        val r = SessionRestorer.plan(s, listOf(DisplayRef(0, "Built-in"), DisplayRef(9, "Desk monitor")))
        assertEquals(9, r.plan.steps.single().displayId)
        assertTrue(r.report.isEmpty())
    }

    @Test fun WSP_02_spikeOutcomesDriveCaps() {
        val mem = mutableMapOf<String, String>()
        val spikes = SpikeResults(object : SpikeStorage { override fun get(key: String) = mem[key]; override fun put(key: String, value: String) { mem[key] = value } })
        val inputs = CapabilityInputs(apiLevel = 35, windowWidthDp = 400, displays = listOf(DisplayInfo(0, "Built-in", 1, 1, 1, true, true), DisplayInfo(3, "Ext", 1, 1, 1, false, true)))
        assertNull(LaunchCapsFactory.from(inputs, spikes).adjacentSupported)
        assertFalse(LaunchCapsFactory.from(inputs, spikes).boundsHonoured)
        spikes.record(SpikeResults.SPK_ADJACENT, SpikeOutcome.Proven)
        spikes.record(SpikeResults.SPK_BOUNDS, SpikeOutcome.Proven)
        val caps = LaunchCapsFactory.from(inputs, spikes)
        assertEquals(true, caps.adjacentSupported); assertTrue(caps.boundsHonoured)
        assertEquals(listOf(3), LaunchResolver.offeredDisplays(caps, 0))
        spikes.record(SpikeResults.SPK_ADJACENT, SpikeOutcome.NotProven)
        assertEquals(false, LaunchCapsFactory.from(inputs, spikes).adjacentSupported)
    }
}
