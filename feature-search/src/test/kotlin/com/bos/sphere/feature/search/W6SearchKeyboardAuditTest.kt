package com.bos.sphere.feature.search

import com.bos.sphere.core.design.L10n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

/** Wave 6 audit of the always-open keyboard (PAL-01 entry, accessibility): symbol keys are named for a screen reader. */
class W6SearchKeyboardAuditTest {
    @Before fun english() { L10n.setLanguage("en") }

    @Test fun PAL_01_symbol_keys_have_spoken_names_and_letters_speak_as_themselves() {
        assertEquals("Backspace", keyDescription("⌫"))
        assertEquals("Space", keyDescription("space"))
        assertEquals("Shift", keyDescription("⇧"))
        assertEquals("Numbers", keyDescription("123"))
        assertEquals("q", keyDescription("q"))
        L10n.setLanguage("de")
        assertNotEquals("Backspace", keyDescription("⌫"))
        L10n.setLanguage("en")
    }
}
