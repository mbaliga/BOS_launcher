package com.bos.sphere.core.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 23 (NFR-05, NFR-12, CFG-17): the pure maths of the Lab's device checks. */
class DeviceKitTest {
    private val ms = 1_000_000L

    @Test fun NFR_05_percentilesUseNearestRankAndJankCountsFramesOverTheRefreshInterval() {
        val s = FrameStats()
        for (i in 1..100) s.add(i * ms) // 1..100 ms
        val r = s.summary(intervalNs = 16 * ms, atMs = 5)!!
        assertEquals(100, r.frames)
        assertEquals(50 * ms, r.p50Ns); assertEquals(95 * ms, r.p95Ns); assertEquals(99 * ms, r.p99Ns)
        assertEquals(100 * ms, r.slowestNs)
        assertEquals(84.0, r.jankyPercent, 0.0001) // 17..100 are over 16 ms
    }

    @Test fun NFR_05_aFrameExactlyAtTheIntervalIsNotJankyAndAnEmptyRunHasNoSummary() {
        val s = FrameStats()
        assertNull(s.summary(16 * ms, 0))
        s.add(16 * ms); s.add(17 * ms)
        assertEquals(50.0, s.summary(16 * ms, 0)!!.jankyPercent, 0.0001)
        assertNull(s.summary(0, 0))
    }

    @Test fun NFR_05_theCapAndNegativeDurationsAreIgnored() {
        val s = FrameStats(cap = 3)
        s.add(-1); s.add(1); s.add(2); s.add(3); s.add(4)
        assertEquals(3, s.count)
    }

    @Test fun NFR_05_theIntervalFollowsTheRefreshRateAndFallsBackTo60() {
        assertEquals(16_666_666L, FrameStats.intervalNs(60f))
        assertEquals(8_333_333L, FrameStats.intervalNs(120f))
        assertEquals(16_666_666L, FrameStats.intervalNs(0f))
    }

    @Test fun NFR_05_aStoredResultReadsBackAndGarbageReadsAsNothing() {
        val r = FrameStats().apply { add(8 * ms); add(30 * ms) }.summary(16 * ms, 99)!!
        assertEquals(r, ProbeResult.decode(r.encode()))
        assertNull(ProbeResult.decode(null)); assertNull(ProbeResult.decode("x")); assertNull(ProbeResult.decode("v1|a|b|c|d|e|f|g|h")); assertNull(ProbeResult.decode("v0|1|1|1|1|1.0|1|1|1"))
        assertTrue(r.line().contains("janky 50.0%"))
    }

    @Test fun NFR_12_coldStartIsTheGapFromProcessStartAndNullWhenTheClocksDisagree() {
        assertEquals(850L, ColdStartMath.elapsedMs(1_000, 1_850))
        assertNull(ColdStartMath.elapsedMs(2_000, 1_000)); assertNull(ColdStartMath.elapsedMs(0, 1_000))
    }

    @Test fun NFR_13_everyCheckHasAUniqueIdAndNamesTheRowItIsEvidenceFor() {
        assertEquals(DeviceCheck.entries.size, DeviceCheck.entries.map { it.id }.toSet().size)
        assertEquals(ProbeSurface.entries.size, ProbeSurface.entries.map { it.id }.toSet().size)
        DeviceCheck.entries.forEach { assertTrue(it.closes.isNotBlank()) }
        assertEquals(CheckStatus.Unset, CheckStatus.of("nonsense")); assertEquals(CheckStatus.Fail, CheckStatus.of("fail"))
    }

    @Test fun SPK_01_theReportCarriesVersionDeviceProbesChecklistAndSaysItClosesNothing() {
        val probe = FrameStats().apply { add(10 * ms) }.summary(16 * ms, 1)
        val text = DeviceReport.build(
            "0.2.0 (abc)", "Acme Phone", "Android 14 (API 34)", 640,
            mapOf(ProbeSurface.Glance to probe),
            listOf(CheckEntry(DeviceCheck.ExternalDisplay, CheckStatus.Pass, "became home\non the monitor"), CheckEntry(DeviceCheck.TalkBack, CheckStatus.Unset, "")),
        )
        assertTrue(text.contains("App: 0.2.0 (abc)") && text.contains("Device: Acme Phone") && text.contains("Android: Android 14 (API 34)"))
        assertTrue(text.contains("640 ms"))
        assertTrue(text.contains("- Glance: 1 frames"))
        assertTrue(text.contains("- Drawer scroll: not run"))
        assertTrue(text.contains("[Pass] External display (SPK-01, EXT-02): became home on the monitor"))
        assertTrue(text.contains("[Not checked] TalkBack order (NFR-13)"))
        assertTrue(text.contains("no spec row is closed"))
        assertNotNull(DeviceReport.build("v", "d", "a", null, emptyMap(), emptyList()).takeIf { it.contains("not captured") })
    }
}

/** Wave 23 review: a saved cold start from an earlier process is never reported as this process. */
class DeviceReportColdStartLabelTest {
    @org.junit.Test fun theReportSaysWhenTheColdStartIsFromAnEarlierProcess() {
        fun r(thisProcess: Boolean) = DeviceReport.build("1", "d", "a", 900, emptyMap(), emptyList(), coldStartThisProcess = thisProcess)
        org.junit.Assert.assertTrue(r(true).contains("this process): 900 ms"))
        org.junit.Assert.assertTrue(r(false).contains("EARLIER process") && !r(false).contains("this process"))
    }
}
