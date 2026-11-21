package com.bos.sphere.core.capabilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** EXT-11: a display that mirrors the phone gives the T2 rules, never T3, and never counts as an external surface host. */
class MirroredTierTest {
    private val phone = CapabilityInputs(apiLevel = 36, windowWidthDp = 411)
    private val main = DisplayInfo(0, "Built-in", 1080, 2400, 420, isDefault = true, canHostTasks = true)
    private val mirror = DisplayInfo(3, "HDMI", 1080, 2400, 420, isDefault = false, canHostTasks = false)

    @Test fun EXT_11_aMirroringDisplayGivesTheT2RulesToAPhoneWindow() {
        val i = phone.copy(displays = listOf(main, mirror))
        assertTrue(CapabilityEvaluator.mirrored(i))
        assertEquals(Tier.T2, CapabilityEvaluator.tier(i))
    }

    @Test fun EXT_11_withoutTheMirrorThePhoneStaysT1() {
        assertEquals(Tier.T1, CapabilityEvaluator.tier(phone.copy(displays = listOf(main))))
        assertFalse(CapabilityEvaluator.mirrored(phone.copy(displays = listOf(main))))
    }

    @Test fun EXT_11_aDifferentSizedPresentationDisplayIsNotTakenForAMirror() {
        val other = mirror.copy(widthPx = 3840, heightPx = 2160)
        assertFalse(CapabilityEvaluator.mirrored(phone.copy(displays = listOf(main, other))))
        assertEquals(Tier.T1, CapabilityEvaluator.tier(phone.copy(displays = listOf(main, other))))
    }

    @Test fun EXT_11_aDisplayThatCanHostTasksIsAnExternalSurfaceNotAMirror() {
        val host = mirror.copy(canHostTasks = true)
        val i = phone.copy(displays = listOf(main, host))
        assertFalse(CapabilityEvaluator.mirrored(i))
        assertEquals(Tier.T3, CapabilityEvaluator.tier(i)) // unchanged: T3 wins on Android 16+
    }

    @Test fun EXT_11_theMirroredTierMatchesTheDesktopModuleRuleSoBothAgree() {
        // The desktop module infers mirroring from the same three facts (see DisplayTracker.looksMirrored).
        val text = java.io.File(generateSequence(java.io.File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }.first { java.io.File(it, "settings.gradle.kts").exists() },
            "feature-desktop/src/main/kotlin/com/bos/sphere/feature/desktop/DisplayTracker.kt").readText()
        assertTrue(text.contains("d.canHostTasks != true") && text.contains("d.widthPx == primary.widthPx") && text.contains("d.heightPx == primary.heightPx"))
    }
}
