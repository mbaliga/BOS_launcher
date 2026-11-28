package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Wave 27 (soundPopup): what each launcher haptic strength lets through. */
class LauncherHapticPolicyTest {
    @Test fun SND_10_offPerformsNothing() {
        for (k in HapticKind.entries) assertNull(HapticPolicy.resolve(HapticStrength.Off, k))
    }
    @Test fun SND_11_lightKeepsTheTicksAndDropsTheLongPressBuzz() {
        assertEquals(HapticKind.Tick, HapticPolicy.resolve(HapticStrength.Light, HapticKind.Tick))
        assertNull(HapticPolicy.resolve(HapticStrength.Light, HapticKind.LongPress))
    }
    @Test fun SND_12_mediumIsTheBehaviourOfEveryEarlierBuild() {
        for (k in HapticKind.entries) assertEquals(k, HapticPolicy.resolve(HapticStrength.Medium, k))
        assertEquals(HapticStrength.Medium, HapticStrength.DEFAULT)
    }
    @Test fun SND_13_strongUsesTheFirmerEffectForEverything() {
        for (k in HapticKind.entries) assertEquals(HapticKind.LongPress, HapticPolicy.resolve(HapticStrength.Strong, k))
    }
    @Test fun SND_14_anUnknownStoredValueReadsAsTheDefaultAndStepsClamp() {
        assertEquals(HapticStrength.Medium, HapticStrength.forId("loud"))
        assertEquals(HapticStrength.Medium, HapticStrength.forId(null))
        assertEquals(HapticStrength.Off, HapticStrength.forStep(-3)); assertEquals(HapticStrength.Strong, HapticStrength.forStep(9))
    }
}
