package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLoopMathTest {
    @Test fun CFG_09_first_start_is_normal() = assertFalse(CrashLoopMath.decide(null, 1_000).second)

    @Test fun CFG_09_two_starts_inside_30_seconds_without_stability_enter_safe_mode() {
        val (s1, _) = CrashLoopMath.decide(null, 1_000)
        val (_, safe) = CrashLoopMath.decide(s1, 10_000)
        assertTrue(safe)
    }
    @Test fun CFG_09_a_stable_run_resets_the_count() {
        val (s1, _) = CrashLoopMath.decide(null, 1_000)
        val stable = CrashLoopMath.markStable(s1)
        assertFalse(CrashLoopMath.decide(stable, 10_000).second)
    }
    @Test fun CFG_09_slow_restarts_are_not_a_loop() {
        val (s1, _) = CrashLoopMath.decide(null, 1_000)
        val (s2, safe) = CrashLoopMath.decide(s1, 1_000 + CrashLoopMath.WINDOW_MS + 1)
        assertFalse(safe); assertEquals(0, s2.rapidStarts)
    }
}
