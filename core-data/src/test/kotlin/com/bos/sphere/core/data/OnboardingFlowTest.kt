package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowTest {
    @Test
    fun CFG_06_firstRunAsksForHomeStyleAndAnOptionalImportAndNothingElse() {
        assertEquals(listOf(OnboardingStep.SetAsHome, OnboardingStep.HomeStyle, OnboardingStep.Import), OnboardingFlow.steps)
        assertTrue(OnboardingStep.Import.optional)
        assertTrue(OnboardingFlow.upFrontGrants.isEmpty())
    }

    @Test
    fun CFG_06_showsOnlyUntilDone() {
        assertTrue(OnboardingFlow.shouldShow(LauncherPrefs(onboardingDone = false)))
        assertFalse(OnboardingFlow.shouldShow(LauncherPrefs(onboardingDone = true)))
    }
}
