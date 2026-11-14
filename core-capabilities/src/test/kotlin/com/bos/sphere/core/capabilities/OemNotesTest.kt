package com.bos.sphere.core.capabilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OemNotesTest {
    private val phone = CapabilityInputs(apiLevel = 34, windowWidthDp = 411)

    @Test fun NFR_21_manufacturerMapsToNotes() {
        assertEquals(OemNotes.Oem.Samsung, OemNotes.oemFor("samsung"))
        assertEquals(OemNotes.Oem.Xiaomi, OemNotes.oemFor("Xiaomi"))
        assertEquals(OemNotes.Oem.Xiaomi, OemNotes.oemFor("POCO"))
        assertEquals(OemNotes.Oem.OnePlus, OemNotes.oemFor("OnePlus"))
        assertEquals(OemNotes.Oem.Pixel, OemNotes.oemFor("Google"))
        assertEquals(OemNotes.Oem.Other, OemNotes.oemFor(""))
    }

    @Test fun NFR_21_everyOemHasBatteryAndGuidanceText() {
        OemNotes.Oem.entries.forEach { assertTrue(it.name, OemNotes.text(it).length > 40) }
        listOf(OemNotes.Oem.Samsung, OemNotes.Oem.Xiaomi, OemNotes.Oem.OnePlus).forEach {
            assertTrue(it.name, OemNotes.text(it).contains("attery") || OemNotes.text(it).contains("background"))
        }
    }

    @Test fun NFR_21_reportHasOemNotesRowForTheManufacturer() {
        val row = CapabilityEvaluator.evaluate(phone.copy(manufacturer = "samsung")).first { it.id == "oem_notes" }
        assertEquals("OEM notes", row.title)
        assertTrue(row.reason!!.startsWith("Samsung"))
        assertTrue(CapabilityEvaluator.asText(phone.copy(manufacturer = "samsung")).contains("OEM notes"))
    }

    @Test fun NFR_21_unknownManufacturerStillGetsARow() {
        assertTrue(CapabilityEvaluator.evaluate(phone).any { it.id == "oem_notes" })
    }
}
