package com.bos.sphere.core.data

import android.app.ApplicationExitInfo as Exit
import com.bos.sphere.core.data.CrashLoopMath.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CFG-09. Wave 19 (owner decision): the guard counts CRASHES, not rapid starts. The first four tests are the wave-7 tests that encoded the
 * old rapid-start rule; they keep their names and their intent (a first start is normal, a stable run resets, slow restarts are no loop)
 * and are rewritten around "the previous start ended in a crash". The old claim "two starts inside 30 seconds enter safe mode" is
 * the defect and is replaced by the two tests that follow it.
 */
class CrashLoopMathTest {
    private val crashed = true
    private val notCrashed = false

    @Test fun CFG_09_first_start_is_normal() = assertFalse(CrashLoopMath.decide(null, 1_000, notCrashed).second)

    // was: CFG_09_two_starts_inside_30_seconds_without_stability_enter_safe_mode (counted starts; that was the defect)
    @Test fun CFG_09_two_quick_starts_with_no_crash_are_not_safe_mode() {
        val (s1, _) = CrashLoopMath.decide(null, 1_000, notCrashed)
        val (s2, safe2) = CrashLoopMath.decide(s1, 10_000, notCrashed)
        assertFalse(safe2)
        assertFalse(CrashLoopMath.decide(s2, 12_000, notCrashed).second)
        assertEquals(0, s2.crashes)
    }

    @Test fun CFG_09_a_counted_crash_then_another_within_30_seconds_is_safe_mode() {
        val (s1, _) = CrashLoopMath.decide(null, 1_000, notCrashed)
        val (s2, safe2) = CrashLoopMath.decide(s1, 6_000, crashed) // run 1 crashed 5 s in
        assertFalse("one crash is the recovery screen's business, not safe mode", safe2)
        val (s3, safe3) = CrashLoopMath.decide(s2, 12_000, crashed) // run 2 crashed 6 s in
        assertTrue(safe3); assertEquals(2, s3.crashes)
    }

    @Test fun CFG_09_a_crash_that_comes_long_after_the_start_restarts_the_streak_at_one() {
        val (s1, _) = CrashLoopMath.decide(null, 1_000, notCrashed)
        val (s2, _) = CrashLoopMath.decide(s1, 6_000, crashed)
        val (s3, safe3) = CrashLoopMath.decide(s2, 6_000 + CrashLoopMath.WINDOW_MS + 1, crashed) // run 2 lived over 30 s, then crashed
        assertFalse(safe3); assertEquals(1, s3.crashes)
    }

    @Test fun CFG_09_a_start_that_follows_no_crash_breaks_the_streak() {
        val (s1, _) = CrashLoopMath.decide(null, 1_000, notCrashed)
        val (s2, _) = CrashLoopMath.decide(s1, 6_000, crashed)
        val (s3, _) = CrashLoopMath.decide(s2, 9_000, notCrashed) // e.g. installed over the launcher
        assertEquals(0, s3.crashes)
        assertFalse(CrashLoopMath.decide(s3, 12_000, crashed).second)
    }

    @Test fun CFG_09_a_stable_run_resets_the_count() {
        val (s1, _) = CrashLoopMath.decide(null, 1_000, notCrashed)
        val (s2, _) = CrashLoopMath.decide(s1, 6_000, crashed)
        val stable = CrashLoopMath.markStable(s2)
        assertEquals(0, stable.crashes)
        assertFalse(CrashLoopMath.decide(stable, 10_000, crashed).second)
    }

    @Test fun CFG_09_slow_restarts_are_not_a_loop() {
        val (s1, _) = CrashLoopMath.decide(null, 1_000, notCrashed)
        val (s2, safe) = CrashLoopMath.decide(s1, 1_000 + CrashLoopMath.WINDOW_MS + 1, notCrashed)
        assertFalse(safe); assertEquals(0, s2.crashes)
    }

    // ---- what counts as a crash

    private val marker = State(lastStart = 10_000, lastWasStable = false, crashes = 0)
    private fun ended(reason: Int, at: Long = 12_000, prev: State? = marker, sdk: Int = 34, pending: Boolean = false) =
        CrashLoopMath.endedInCrash(sdk, prev, CrashLoopMath.Exit(reason, at), pending)

    @Test fun CFG_09_crash_shaped_exit_reasons_count() {
        assertTrue(ended(Exit.REASON_CRASH))
        assertTrue(ended(Exit.REASON_CRASH_NATIVE))
        assertTrue(ended(Exit.REASON_INITIALIZATION_FAILURE))
    }

    @Test fun CFG_09_an_anr_counts() = assertTrue(ended(Exit.REASON_ANR))

    @Test fun CFG_09_user_requested_stops_and_package_replacement_never_count() {
        for (r in listOf(Exit.REASON_USER_REQUESTED, Exit.REASON_OTHER, Exit.REASON_USER_STOPPED, Exit.REASON_DEPENDENCY_DIED,
            Exit.REASON_LOW_MEMORY, Exit.REASON_SIGNALED, Exit.REASON_EXIT_SELF, Exit.REASON_PERMISSION_CHANGE, Exit.REASON_UNKNOWN))
            assertFalse("reason $r", ended(r))
    }

    @Test fun CFG_09_an_exit_record_older_than_the_marker_is_not_evidence() = assertFalse(ended(Exit.REASON_CRASH, at = 9_999))

    @Test fun CFG_09_no_exit_record_or_no_marker_is_not_a_crash() {
        assertFalse(CrashLoopMath.endedInCrash(34, marker, null, false))
        assertFalse(ended(Exit.REASON_CRASH, prev = marker.copy(lastWasStable = true)))
        assertFalse(ended(Exit.REASON_CRASH, prev = null))
    }

    @Test fun CFG_09_below_api_30_only_a_pending_jvm_report_counts() {
        assertFalse(ended(Exit.REASON_CRASH, sdk = 29))
        assertTrue(ended(Exit.REASON_USER_REQUESTED, sdk = 29, pending = true))
        assertFalse(CrashLoopMath.endedInCrash(29, marker.copy(lastWasStable = true), null, true))
    }

    // ---- wave 20 audit

    @Test fun CFG_09_aClockThatWentBackNeverCountsTheNextCrashAsInsideTheWindow() {
        val prev = State(lastStart = 500_000, lastWasStable = false, crashes = 1)
        val (next, safe) = CrashLoopMath.decide(prev, now = 100_000, previousEndedInCrash = true) // the clock is now 400 s EARLIER than the marker
        assertFalse("a negative age is not 'within 30 seconds'", safe)
        assertEquals(1, next.crashes)
    }

    @Test fun CFG_09_aCrashExactlyAtTheWindowEdgeStartsANewStreak() {
        val prev = State(0, false, 1)
        assertTrue(CrashLoopMath.decide(prev, CrashLoopMath.WINDOW_MS - 1, true).second)
        assertFalse(CrashLoopMath.decide(prev, CrashLoopMath.WINDOW_MS, true).second)
    }

    @Test fun CFG_09_aMarkerFromTheFutureIsNotCrashEvidenceEvenWithACrashRecord() {
        // The exit record is stamped by the same clock as the marker; a record from before a clock step cannot be at or after a marker written after it.
        val prev = State(lastStart = 900_000, lastWasStable = false, crashes = 0)
        assertFalse(CrashLoopMath.endedInCrash(34, prev, CrashLoopMath.Exit(Exit.REASON_CRASH, 100_000), false))
    }

    @Test fun CFG_09_everyCrashReasonConstantIsCountedAndNoOtherKnownReasonIs() {
        val all = listOf(Exit.REASON_ANR, Exit.REASON_CRASH, Exit.REASON_CRASH_NATIVE, Exit.REASON_DEPENDENCY_DIED, Exit.REASON_EXCESSIVE_RESOURCE_USAGE,
            Exit.REASON_EXIT_SELF, Exit.REASON_FREEZER, Exit.REASON_INITIALIZATION_FAILURE, Exit.REASON_LOW_MEMORY, Exit.REASON_OTHER, Exit.REASON_PERMISSION_CHANGE,
            Exit.REASON_SIGNALED, Exit.REASON_UNKNOWN, Exit.REASON_USER_REQUESTED, Exit.REASON_USER_STOPPED)
        val counted = all.filter { CrashLoopMath.endedInCrash(34, State(0, false, 0), CrashLoopMath.Exit(it, 1), false) }.toSet()
        assertEquals(setOf(Exit.REASON_ANR, Exit.REASON_CRASH, Exit.REASON_CRASH_NATIVE, Exit.REASON_INITIALIZATION_FAILURE), counted)
    }
}
