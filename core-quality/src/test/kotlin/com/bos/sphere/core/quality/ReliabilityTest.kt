package com.bos.sphere.core.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReliabilityTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun NFR_09_crashFreeRateFromLocalLedger() {
        val ledger = List(1000) { "S" } + "C" + "C"
        assertEquals(99.8, CrashReports.crashFreePercent(ledger), 1e-9)
        assertEquals(100.0, CrashReports.crashFreePercent(emptyList()), 0.0)
        assertEquals(99.8, CrashReports.TARGET_PERCENT, 0.0)
    }

    @Test fun NFR_09_recordingACrashCountsAgainstTheSession() {
        val r = CrashReports(tmp.newFolder("c"))
        repeat(4) { r.sessionStarted() }
        r.record(IllegalStateException("boom"), "main", 1L)
        assertEquals(75.0, r.crashFreePercent(), 1e-9)
    }

    @Test fun NFR_17_reportIsSavedLocallyAndOnlyReadForSharing() {
        val r = CrashReports(tmp.newFolder("c2"))
        val f = r.record(RuntimeException("x"), "main", 5L)
        assertTrue(f.exists())
        assertTrue(r.textForSharing(f).contains("never uploaded"))
        assertEquals(listOf(f), r.list())
    }

    @Test fun NFR_17_oldReportsArePruned() {
        val r = CrashReports(tmp.newFolder("c3"), maxReports = 3)
        (1..6).forEach { r.record(RuntimeException("e$it"), "main", it.toLong() * 1000) }
        assertEquals(3, r.list().size)
    }

    @Test fun NFR_10_soakScriptRunsTwoHoursAndChecksForAnr() {
        val s = Repo.file("tools/soak.sh").readText()
        assertTrue(s.contains("7200"))
        assertTrue(s.contains("ANR"))
        assertTrue(s.contains("adb"))
    }

    @Test fun NFR_09_crashReportingNeverUsesTheNetwork() {
        val src = Repo.file("core-quality/src/main/kotlin/com/bos/sphere/core/quality/CrashReports.kt").readText()
        listOf("java.net", "okhttp", "HttpURLConnection", "URL(").forEach { assertTrue(it, !src.contains(it)) }
    }
}
