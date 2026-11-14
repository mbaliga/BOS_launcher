package com.bos.sphere.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogKeysTest {
    @Test fun INP_20_back_and_escape_close_a_dialog_and_confirm_acts_as_enter() {
        assertEquals(Keys.BACK, DialogKeys.translate(Keys.BUTTON_B))
        assertEquals(Keys.BACK, DialogKeys.translate(Keys.ESCAPE))
        assertEquals(Keys.DPAD_CENTER, DialogKeys.translate(Keys.BUTTON_A))
        assertEquals(Keys.MENU, DialogKeys.translate(Keys.BUTTON_X))
    }

    @Test fun INP_20_dpad_and_ordinary_keys_pass_through_and_the_left_shoulder_is_shift_tab() {
        for (k in listOf(Keys.DPAD_UP, Keys.DPAD_DOWN, Keys.DPAD_LEFT, Keys.DPAD_RIGHT, Keys.ENTER, Keys.TAB, 29)) assertEquals(k, DialogKeys.translate(k))
        assertEquals(Keys.TAB, DialogKeys.translate(Keys.BUTTON_L1)); assertTrue(DialogKeys.shiftFor(Keys.BUTTON_L1))
        assertEquals(Keys.TAB, DialogKeys.translate(Keys.BUTTON_R1)); assertTrue(!DialogKeys.shiftFor(Keys.BUTTON_R1))
    }

    @Test fun INP_20_the_activity_mapping_is_unchanged_for_surfaces_outside_dialogs() {
        assertEquals(Keys.ESCAPE, ControllerKeys.translate(Keys.BUTTON_B))
    }
}
