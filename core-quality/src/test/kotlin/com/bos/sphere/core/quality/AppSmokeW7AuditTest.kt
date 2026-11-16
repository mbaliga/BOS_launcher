package com.bos.sphere.core.quality

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 7 whole-app smoke audits: the suite in the app module exists, covers its requirement IDs, and the start-up guards it exposed stay in place. */
class AppSmokeW7AuditTest {
    private fun text(p: String) = Repo.file(p).readText()
    private val smoke get() = text("app/src/test/kotlin/com/bos/sphere/AppSmokeTest.kt")
    private val main get() = text("app/src/main/kotlin/com/bos/sphere/MainActivity.kt")

    @Test fun NFR_11_aRecreateInTheSameProcessIsNotCountedAsAStartByTheCrashLoopGuard() {
        assertTrue("a recreate hands the state back and must not count as a start",
            main.contains("if (savedInstanceState == null) com.bos.sphere.core.data.CrashLoopGuard.onStart(this)"))
        assertTrue(smoke.contains("NFR_11_aRecreateOfTheActivityInTheSameProcessIsNotTakenForACrashLoop"))
    }

    @Test fun HOM_01_theHomeActivityReadsItsDisplayIdWithoutThrowingWhereTheContextHasNoDisplay() {
        assertTrue(main.contains("runCatching { display?.displayId }.getOrNull() ?: 0"))
        assertFalse("no unguarded display read in onStart", Regex("""homeStarted\(this,\s*display\?\.displayId""").containsMatchIn(main))
    }

    @Test fun theSmokeSuiteRunsTheRealActivityAndCoversEveryIdOfTheStream() {
        assertTrue(smoke.contains("AppSmokeHarness.launch()"))
        assertTrue(text("app/src/test/kotlin/com/bos/sphere/AppSmokeHarness.kt").contains("ActivityScenario.launch(MainActivity::class.java)"))
        for (id in listOf("INP_05", "INP_01", "NFR_19", "NFR_11", "HOM_01", "PAL_01")) {
            assertTrue("a smoke test named for $id", Regex("""fun ${id}_\w+\(""").containsMatchIn(smoke))
        }
    }

    @Test fun theSmokeSuiteChecksForPendingGlobalSnapshotWritesAfterEveryTest() {
        assertTrue(smoke.contains("Snapshot.current.hasPendingChanges()"))
        assertTrue("writes outside a Compose host go through a snapshot", smoke.contains("Snapshot.withMutableSnapshot"))
    }

    @Test fun theSuiteDrivesBackThroughTheDispatcherNeverTheLegacyKeyOrMethod() {
        val harness = text("app/src/test/kotlin/com/bos/sphere/AppSmokeHarness.kt")
        assertTrue(harness.contains("onBackPressedDispatcher.onBackPressed()"))
        assertFalse(harness.contains("KEYCODE_BACK"))
        assertFalse(smoke.contains("KEYCODE_BACK"))
    }
}
