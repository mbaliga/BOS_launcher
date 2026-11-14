package com.bos.sphere.core.capabilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityEvaluatorTest {
    private val phone = CapabilityInputs(apiLevel = 34, windowWidthDp = 411)
    private val ext = DisplayInfo(2, "HDMI", 3840, 2160, 320, isDefault = false, canHostTasks = true)
    private val main = DisplayInfo(0, "Built-in", 1080, 2400, 420, isDefault = true, canHostTasks = true)

    @Test fun phone_is_t1() = assertEquals(Tier.T1, CapabilityEvaluator.tier(phone))
    @Test fun large_window_is_t2() = assertEquals(Tier.T2, CapabilityEvaluator.tier(phone.copy(windowWidthDp = 840)))
    @Test fun keyboard_makes_t2() = assertEquals(Tier.T2, CapabilityEvaluator.tier(phone.copy(hasKeyboard = true)))
    @Test fun pointer_makes_t2() = assertEquals(Tier.T2, CapabilityEvaluator.tier(phone.copy(hasPointer = true)))
    @Test fun external_display_needs_android_16_for_t3() {
        val i = phone.copy(displays = listOf(main, ext))
        assertEquals(Tier.T1, CapabilityEvaluator.tier(i))
        assertEquals(Tier.T3, CapabilityEvaluator.tier(i.copy(apiLevel = 36)))
    }
    @Test fun desktop_windowing_is_t3_on_36() = assertEquals(Tier.T3, CapabilityEvaluator.tier(phone.copy(apiLevel = 36, desktopWindowing = true)))
    @Test fun presentation_display_does_not_host_tasks() {
        val pres = ext.copy(canHostTasks = false)
        assertEquals(Tier.T1, CapabilityEvaluator.tier(phone.copy(apiLevel = 36, displays = listOf(main, pres))))
    }

    @Test fun grants_off_by_default_with_fix_text() {
        val c = CapabilityEvaluator.evaluate(phone).first { it.id == "notification_access" }
        assertEquals(CapState.Off, c.state)
        assertTrue(c.fix!!.isNotBlank())
    }
    @Test fun private_space_needs_android_15_and_home_role() {
        assertEquals(CapState.Off, CapabilityEvaluator.evaluate(phone).first { it.id == "private_space" }.state)
        assertEquals(CapState.On, CapabilityEvaluator.evaluate(phone.copy(apiLevel = 35, isHomeRole = true)).first { it.id == "private_space" }.state)
    }
    @Test fun the_no_rows_stay_off() {
        val off = CapabilityEvaluator.evaluate(phone.copy(apiLevel = 36)).filter { it.access == Access.No }
        assertTrue(off.isNotEmpty() && off.all { it.state == CapState.Off && it.reason != null })
    }
    @Test fun report_text_lists_every_row() {
        val i = phone
        val text = CapabilityEvaluator.asText(i)
        CapabilityEvaluator.evaluate(i).forEach { assertTrue(it.title, text.contains(it.title)) }
    }
}
