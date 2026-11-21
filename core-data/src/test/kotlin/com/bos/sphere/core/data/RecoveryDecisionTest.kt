package com.bos.sphere.core.data

import com.bos.sphere.core.data.RecoveryDecision.Screen
import org.junit.Assert.assertEquals
import org.junit.Test

/** CFG-09 (wave 18): which screen a start shows with the shared recovery screen in front of safe mode. */
class RecoveryDecisionTest {
    @Test fun CFG_09_aPendingReportAlwaysShowsTheRecoveryScreenFirst() {
        for (guard in listOf(false, true)) for (streak in 0..4) assertEquals(Screen.Recovery, RecoveryDecision.screen(true, guard, streak))
    }

    @Test fun CFG_09_withoutAReportASingleCrashGoesBackToHome() {
        assertEquals(Screen.Home, RecoveryDecision.screen(false, false, 0))
        assertEquals(Screen.Home, RecoveryDecision.screen(false, false, 1))
    }

    @Test fun CFG_09_aCrashThatRecurredAfterContinueIsSafeMode() {
        assertEquals(Screen.SafeMode, RecoveryDecision.screen(false, false, 2))
        assertEquals(Screen.SafeMode, RecoveryDecision.screen(false, false, 5))
    }

    @Test fun CFG_09_theLoopGuardStillDecidesOnItsOwn_nowFromCounted_crashesNotRapidStarts() {
        assertEquals(Screen.SafeMode, RecoveryDecision.screen(false, true, 0))
    }

    @Test fun CFG_09_theLibraryStreakPathIsUnchangedByTheGuardChange() {
        assertEquals(Screen.Home, RecoveryDecision.screen(false, false, 1))
        assertEquals(Screen.SafeMode, RecoveryDecision.screen(false, false, RecoveryDecision.LOOP_STREAK))
    }

    @Test fun CFG_09_theLoopStreakIsTwoMatchingTheLibrarysRepeatThreshold() {
        assertEquals(2, RecoveryDecision.LOOP_STREAK)
    }
}
