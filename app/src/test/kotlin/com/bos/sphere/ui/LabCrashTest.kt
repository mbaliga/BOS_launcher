package com.bos.sphere.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CFG-17, NFR-09 (wave 18): the Lab row "Crash on purpose (test the recovery screen)". */
class LabCrashTest {
    @Test fun NFR_09_aSecondTapWithinFiveSecondsConfirms() {
        assertFalse(LabCrash.confirmed(null, 1_000))
        assertTrue(LabCrash.confirmed(1_000, 1_000))
        assertTrue(LabCrash.confirmed(1_000, 6_000))
        assertFalse(LabCrash.confirmed(1_000, 6_001))
        assertFalse("a clock that went backwards never confirms", LabCrash.confirmed(5_000, 4_000))
    }

    @Test fun NFR_09_theFirstTapOnlyArmsAndTheSecondThrows() {
        val results = Snapshot.withMutableSnapshot { SnapshotStateMap<String, String>() }
        var armed: Long? = null; var clock = 10_000L; var thrown = 0
        val entry = LabCrash.entry(results, { armed }, { armed = it }, { clock }, { thrown++ })
        entry.run!!.invoke()
        assertEquals(0, thrown); assertNotNull(armed)
        clock += 6_000; entry.run.invoke() // too late: arms again
        assertEquals(0, thrown)
        clock += 1_000; entry.run.invoke()
        assertEquals(1, thrown); assertEquals(null, armed)
    }

    @Test fun NFR_09_theErrorIsThrownUncaughtFromABackgroundThread() {
        val seen = java.util.concurrent.CompletableFuture<Pair<String, Throwable>>()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e -> seen.complete(t.name to e) }
        try {
            val t = LabCrash.fire()
            val (name, e) = seen.get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals("liftoff-lab-crash", name)
            assertTrue(e is IllegalStateException && e.message!!.contains("Crash on purpose"))
            assertFalse(t === Thread.currentThread())
        } finally { Thread.setDefaultUncaughtExceptionHandler(old) }
    }

    @Test fun CFG_17_theRowIsInTheLabListOnly() {
        val lab = java.io.File("src/main/kotlin/com/bos/sphere/ui/Lab.kt").readText()
        assertTrue(lab.contains("LabCrash.entry("))
    }
}
