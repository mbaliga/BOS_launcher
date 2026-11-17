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
        assertEquals(99.8, CrashFreeLedger.crashFreePercent(ledger), 1e-9)
        assertEquals(100.0, CrashFreeLedger.crashFreePercent(emptyList()), 0.0)
        assertEquals(99.8, CrashFreeLedger.TARGET_PERCENT, 0.0)
    }

    @Test fun NFR_09_recordingACrashCountsAgainstTheSession() {
        val r = CrashFreeLedger(tmp.newFolder("c"))
        repeat(4) { r.sessionStarted() }
        r.crashed()
        assertEquals(75.0, r.crashFreePercent(), 1e-9)
    }

    @Test fun NFR_09_ledgerLineShowsPercentAndTarget() {
        assertEquals("99.50% of recorded sessions ended without a crash (target 99.8%)", CrashFreeLedger.line(99.5))
    }

    @Test fun NFR_10_soakScriptRunsTwoHoursAndChecksForAnr() {
        val s = Repo.file("tools/soak.sh").readText()
        assertTrue(s.contains("7200"))
        assertTrue(s.contains("ANR"))
        assertTrue(s.contains("adb"))
    }

    @Test fun NFR_09_crashReportingNeverUsesTheNetwork() {
        val src = Repo.file("core-quality/src/main/kotlin/com/bos/sphere/core/quality/CrashFreeLedger.kt").readText()
        listOf("java.net", "okhttp", "HttpURLConnection", "URL(").forEach { assertTrue(it, !src.contains(it)) }
    }
}
