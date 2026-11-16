package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** STR-13: sender-name normalisation and last-name logic. */
class StrGroupsNamesTest {
    private fun key(s: String) = PersonNames.key(s)

    @Test fun STR_13_caseDiacriticsAndEmojiDoNotSeparateOnePerson() {
        val k = key("Priya Sharma")
        assertEquals(k, key("PRIYA SHARMA"))
        assertEquals(key("José Álvarez"), key("jose alvarez"))
        assertEquals(k, key("Priya Sharma 😊"))
        assertEquals(k, key("★ Priya ❤️ Sharma ★"))
        assertEquals(k, key("𝓟𝓻𝓲𝔂𝓪 Sharma")) // mathematical script letters
        assertEquals(k, key("~Priya   Sharma"))
    }

    @Test fun STR_13_platformSuffixesAreStripped() {
        val k = key("Priya Sharma")
        assertEquals(k, key("Priya Sharma (WhatsApp)"))
        assertEquals(k, key("Priya Sharma [Telegram]"))
        assertEquals(k, key("Priya Sharma - WhatsApp"))
        assertEquals(k, key("Priya Sharma via Signal"))
        assertEquals(k, key("Priya Sharma · Messenger"))
        // A name that merely ends in an app word is not a suffix (no separator).
        assertEquals("anna line", key("Anna Line"))
        // A sender that is only an app word stays.
        assertEquals("whatsapp", key("WhatsApp"))
    }

    @Test fun STR_13_lastCommaFirstIsTurnedAround() {
        assertEquals(key("Priya Sharma"), key("Sharma, Priya"))
        assertEquals(key("Priya Sharma"), key("Priya Sharma, Jr."))
        assertEquals("", key("  😊 "))
    }

    @Test fun STR_13_scriptsOtherThanLatinKeepTheirMarks() {
        // Devanagari vowel signs and the virama are part of the letters; only Latin, Greek, Cyrillic, Arabic, Hebrew marks fold.
        val hindi = "प्रिया"
        assertEquals(hindi, key(hindi))
        assertEquals(key("محمد"), key("مُحَمَّد"))
    }

    @Test fun STR_13_lastNameExtractionHandlesOrderParticlesMultipleSurnamesAndSingleNames() {
        assertTrue(LastNames.matches("Priya Sharma", "Sharma"))
        assertTrue(LastNames.matches("Sharma, Priya", "sharma"))
        assertTrue(LastNames.matches("Priya Sharma Jr.", "Sharma"))
        assertTrue(LastNames.matches("Ludwig van Beethoven", "van Beethoven"))
        assertTrue(LastNames.matches("Ludwig van Beethoven", "Beethoven"))
        assertTrue(LastNames.matches("Jan van Dijk", "Van Dijk"))
        assertTrue(LastNames.matches("Maria de la Cruz", "de la Cruz"))
        assertTrue(LastNames.matches("María García López", "García López"))
        assertTrue(LastNames.matches("María García López", "lopez"))
        assertTrue(LastNames.matches("Ana Smith-Jones", "Jones"))
        assertTrue(LastNames.matches("Ana Smith-Jones", "Smith-Jones"))
        assertTrue(LastNames.matches("García López, María", "Garcia"))
        assertFalse(LastNames.matches("Priya Sharma", "Sharmas"))
        assertFalse(LastNames.matches("Priya Sharma", "Priya"))
    }

    @Test fun STR_13_aGivenNameThatIsAParticleStaysAGivenName() {
        assertEquals("Morrison", LastNames.suggestFor("Van Morrison"))
        assertTrue(LastNames.matches("Van Morrison", "Morrison"))
        assertEquals("van Dijk", LastNames.suggestFor("Jan van Dijk"))
    }

    @Test fun STR_13_aSingleNameNeverMatchesAndNeverCrashes() {
        for (s in listOf("Mum", "", " ", "😊", ",", "Sharma,", "+1 555 0100", "Jr", "de")) {
            LastNames.candidates(s)
            assertFalse("single name $s", LastNames.matches(s, "Sharma"))
        }
        assertEquals(null, LastNames.suggestFor("Mum"))
        assertFalse(LastNames.matches("Priya Sharma", ""))
        assertFalse(LastNames.matches("Priya Sharma", "  "))
    }

    @Test fun STR_13_suggestKeepsTheCaseOfTheOwnerProfileName() {
        assertEquals("Sharma", LastNames.suggestFor("Priya Sharma"))
        assertEquals("Sharma", LastNames.suggestFor("Sharma, Priya"))
        assertEquals("Sharma", LastNames.suggestFor("Priya Sharma III"))
    }
}
