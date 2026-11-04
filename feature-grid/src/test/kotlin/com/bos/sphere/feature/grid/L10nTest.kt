package com.bos.sphere.feature.grid

import com.bos.sphere.core.design.L10n
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class L10nTest {
    private val codes = listOf("es", "fr", "de", "pt", "hi", "zh", "ja", "ar")

    @After fun reset() = L10n.setLanguage("")

    @Test fun everyTableKeyExistsInEnglish() {
        for (c in codes) {
            val extra = L10n.tableFor(c)!!.keys - L10n.englishKeys
            assertTrue("$c has keys not in English: $extra", extra.isEmpty())
        }
    }

    @Test fun noTableIsMissingAKeyFromSpanish() {
        val ref = L10n.tableFor("es")!!.keys
        assertEquals(L10n.englishKeys, ref)
        for (c in codes) {
            val missing = ref - L10n.tableFor(c)!!.keys
            assertTrue("$c is missing: $missing", missing.isEmpty())
        }
    }

    @Test fun placeholdersAreKept() {
        val re = Regex("%[sd]")
        for (c in codes) for ((k, v) in L10n.tableFor(c)!!) {
            assertEquals("$c: $k", re.findAll(k).map { it.value }.sorted().toList(), re.findAll(v).map { it.value }.sorted().toList())
        }
    }

    @Test fun translatesAndFallsBack() {
        L10n.setLanguage("es")
        assertEquals("Hecho", L10n.tr("Done"))
        assertEquals("Página 3", L10n.tr("Page %d", 3))
        assertEquals("Not a known key", L10n.tr("Not a known key"))
        L10n.setLanguage("en")
        assertEquals("Done", L10n.tr("Done"))
        L10n.setLanguage("xx")
        assertEquals("Done", L10n.tr("Done"))
        L10n.setLanguage("ja")
        assertNotEquals("Done", L10n.tr("Done"))
    }
}
