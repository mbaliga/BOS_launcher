package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StepMathTest {
    @Test
    fun `first reading of a day starts at zero`() {
        assertEquals(0 to 5000f, StepMath.today(5000f, null))
    }

    @Test
    fun `steps are counter minus the day's baseline`() {
        assertEquals(1234 to 5000f, StepMath.today(6234f, 5000f))
    }

    @Test
    fun `a reboot restarts the counter without going negative`() {
        assertEquals(300 to 0f, StepMath.today(300f, 5000f))
    }
}
