package com.bos.sphere.ui

import com.bos.sphere.core.design.L10n
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 26 (audit of wave 25): the hub's labels come from enums and `when` branches, which the source scan of TranslationCoverageTest
 * (direct `L10n.tr("...")` literals only) cannot see. Every one of them must exist in all eight languages.
 */
class HubStringsW26Test {
    @After fun reset() { L10n.setLanguage("en") }

    private val keys: List<String> =
        HubView.entries.map { it.label } + HubGrouping.entries.map { it.label } + HubRange.entries.filter { it != HubRange.Any }.map { it.label } +
            listOf(
                "Nothing matches these filters", "No unread notifications", "Nothing read yet", "No notifications in history yet",
                "Unread", "Read", "Cleared from the shade", "Marked %d as read", "Mark as unread", "Mark as read", "More options", "Delete from history",
                "Delete all notification history? Notifications in the shade stay.", "Notification access is off, so new notifications are not recorded.",
            )

    @Test fun HUB_21_everyHubLabelHasATranslationInEveryLanguage() {
        val missing = ArrayList<String>()
        for (code in listOf("es", "fr", "de", "pt", "hi", "zh", "ja", "ar")) {
            val table = L10n.tableFor(code)!!
            for (k in keys) if (k !in table) missing += "$code: $k"
        }
        assertTrue("hub strings with no translation:\n" + missing.joinToString("\n"), missing.isEmpty())
    }
}
