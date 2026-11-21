package com.bos.sphere.feature.grid

import com.bos.sphere.core.design.L10n
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** PAL-18 (wave 23 sweep): the Radar refinement chips are in the app language, and the "Name contains" chip is dropped by the model's English, not by the translated words. */
class SuggestionChipsW23Test {
    @Before fun english() { L10n.setLanguage("en") }
    @After fun reset() { L10n.setLanguage("en") }

    @Test fun PAL_18_chipLabelsAreTranslatedAroundTheUsersText() {
        L10n.setLanguage("de")
        assertEquals("Paket enthält „maps“", localizeChip("Package contains “maps”"))
        assertEquals("Genutzt: heute", localizeChip("Used: today"))
        assertEquals("Ist: häufig", localizeChip("Is: frequent"))
        assertEquals("Starts >5", localizeChip("Launches >5"))
        assertEquals("Art: Games", localizeChip("Kind: Games"))
        assertEquals("music von gestern", localizeChip("music I used yesterday"))
        assertEquals("something else", localizeChip("something else"))
    }

    @Test fun PAL_18_inEnglishTheChipsAreUnchanged() {
        for (s in listOf("Package contains “x”", "Name contains “x”", "Kind: Games", "Used: this week", "Is: work", "Launches >20", "games I used yesterday")) assertEquals(s, localizeChip(s))
    }

    @Test fun PAL_18_theNameChipIsNotRepeatedUnderRefineAppsOnATranslatedPhone() {
        L10n.setLanguage("de")
        val groups = buildSuggestionGroups("zzq", listOf("Games"))
        val refine = groups.firstOrNull { it.header == L10n.tr("Refine apps") }
        // Filenames carries the one "Name contains" row; the refine group has only the package chip, and nothing in it is English.
        assertTrue(refine != null)
        val labels = refine!!.items.map { it.label }
        assertEquals(listOf("Paket enthält „zzq“"), labels)
        assertFalse(labels.any { it.startsWith("Name contains") || it.startsWith("Package contains") })
    }
}
