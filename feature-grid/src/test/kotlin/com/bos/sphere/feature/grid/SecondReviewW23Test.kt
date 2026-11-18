package com.bos.sphere.feature.grid

import androidx.compose.ui.graphics.luminance
import com.bos.sphere.core.design.L10n
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Wave 23 second review (auditSweep): the name chip without file search, the wheel's band words and the plain edge bars on the light theme. */
class SecondReviewW23Test {
    @Before fun english() { L10n.setLanguage("en") }
    @After fun reset() { L10n.setLanguage("en") }

    @Test fun PAL_18_withoutFileSearchTheNameChipStaysUnderRefineApps() {
        val groups = buildSuggestionGroups("zzq", emptyList(), filesAvailable = false, deepAvailable = false)
        val refine = groups.single { it.header == L10n.tr("Refine apps") }.items.map { it.label }
        assertEquals(listOf("Name contains “zzq”", "Package contains “zzq”"), refine)
        // with file search the name row is the Filenames one, once
        val withFiles = buildSuggestionGroups("zzq", emptyList())
        assertEquals(1, withFiles.flatMap { it.items }.count { it.label.startsWith("Name contains") })
    }

    @Test fun PAL_18_theNameChipIsTranslatedOnATranslatedPhoneWithoutFileSearch() {
        L10n.setLanguage("de")
        val refine = buildSuggestionGroups("zzq", emptyList(), filesAvailable = false, deepAvailable = false).single { it.header == L10n.tr("Refine apps") }.items.map { it.label }
        assertEquals(listOf(L10n.tr("Name contains “%s”", "zzq"), "Paket enthält „zzq“"), refine)
        assertTrue(refine.none { it.startsWith("Name contains") })
    }

    @Test fun CFG_19_theWheelBandsAreInTheAppLanguage() {
        L10n.setLanguage("de")
        assertEquals("Spiele", wheelGroupLabel("Games"))
        assertEquals("Bedienungshilfen", wheelGroupLabel("Accessibility"))
        assertEquals(L10n.tr("Recent"), wheelGroupLabel("Recent"))
        for (name in WheelCategoryOrder) assertTrue("$name has a German word", L10n.tableFor("de")!!.containsKey(name))
        L10n.setLanguage("en")
        assertEquals("Games", wheelGroupLabel("Games"))
    }

    @Test fun STR_01_thePlainEdgeBarShowsOnBothThemes() {
        assertTrue(HomeHandleBar.color(true).copy(alpha = 1f).luminance() > 0.6f)
        assertTrue(HomeHandleBar.color(false).copy(alpha = 1f).luminance() < 0.1f)
        assertEquals(HomeHandleBar.ALPHA, HomeHandleBar.color(false).alpha, 0.01f)
    }
}
