package com.bos.sphere.feature.grid

import androidx.compose.ui.graphics.vector.ImageVector
import org.junit.Assert.assertEquals
import org.junit.Test

/** Wave 28 review: a TalkBack set-progress on a stepper row is snapped to its step and kept inside its range. */
class StepperProgressW28Test {
    private fun s(step: Int) = SettingItem.Stepper("Comms size", ImageVector.Builder("g", androidx.compose.ui.unit.Dp(1f), androidx.compose.ui.unit.Dp(1f), 1f, 1f).build(), 100, 75..125, step = step) {}

    @Test fun STP_01_setProgressIsClampedToTheRangeAndSnappedToTheStep() {
        assertEquals(125, s(5).fromProgress(500f))
        assertEquals(75, s(5).fromProgress(-20f))
        assertEquals(115, s(5).fromProgress(113.4f))
        assertEquals(80, s(5).fromProgress(77.5f))
        assertEquals(90, s(1).fromProgress(90f))
    }
}
