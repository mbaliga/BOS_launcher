package com.bos.sphere.feature.grid

import com.bos.sphere.core.design.L10n
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Wave 26 (audit of wave 25): the drawer's details line ("category, N launches, last used") is translated. */
class DrawerDetailsW26Test {
    @After fun reset() { L10n.setLanguage("en") }
    private val now = 1_000_000_000L

    @Test fun LST_09_detailsLineInEnglish() {
        L10n.setLanguage("en")
        assertEquals("Games  ·  3 launches  ·  5 min ago", DrawerOrdering.detailsLine("Games", 3, now, now - 5 * 60_000))
        assertEquals("Other  ·  1 launch  ·  Never", DrawerOrdering.detailsLine("Other", 1, now, 0))
    }

    @Test fun LST_09_detailsLineIsTranslatedInEveryLanguage() {
        for (code in listOf("es", "fr", "de", "pt", "hi", "zh", "ja", "ar")) {
            L10n.setLanguage(code)
            val line = DrawerOrdering.detailsLine("Games", 3, now, now - 2 * DrawerOrdering.DAY_MS)
            assertFalse("$code: 'launches' stays English: $line", line.contains("launches"))
            assertFalse("$code: 'ago' stays English: $line", line.contains(" ago"))
            assertFalse("$code: the category stays English: $line", line.contains("Games"))
            val never = DrawerOrdering.detailsLine("Other", 0, now, 0)
            assertFalse("$code: 'Never' stays English: $never", never.contains("Never") || never.contains("launch"))
            val mo = DrawerOrdering.relativeTime(now, now - 70 * DrawerOrdering.DAY_MS)
            assertFalse("$code: months stay English: $mo", mo.contains(" ago"))
        }
    }
}
