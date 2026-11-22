package com.bos.sphere.core.design

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/** Wave 52 (settingsFont), CFG-21: the bundled UI fonts, their fallback and the capitals of titles. */
class FontCatalogW52Test {
    private val fontDir = listOf("src/main/res/font", "core-design/src/main/res/font").map { File(it) }.first { it.isDirectory }

    @Test fun CFG_21_theDefaultIsSpaceGroteskAndSystemDefaultIsAChoiceToo() {
        assertEquals("space_grotesk", FontCatalog.DEFAULT)
        assertEquals("Space Grotesk", FontCatalog.entry(null).label)
        assertTrue(FontCatalog.ids.containsAll(listOf("space_grotesk", "system")))
        assertTrue("at least five bundled fonts besides Space Grotesk and the system one", FontCatalog.entries.count { it.files.isNotEmpty() } >= 6)
        assertEquals("an unknown or hand-edited id is the default", "space_grotesk", FontCatalog.normalize("Comic"))
    }

    @Test fun CFG_21_everyFileTheCatalogNamesIsBundledInTheOneFontFolder() {
        val missing = FontCatalog.entries.flatMap { it.files }.map { it.resName }.filter { !File(fontDir, "$it.ttf").isFile }
        assertEquals("font files named by the catalog but absent from res/font: $missing", emptyList<String>(), missing)
        val unused = fontDir.listFiles { f -> f.extension == "ttf" }!!.map { it.nameWithoutExtension } - FontCatalog.entries.flatMap { it.files }.map { it.resName }.toSet()
        assertEquals("font files nothing uses", emptyList<String>(), unused)
        assertTrue("small: all bundled files together stay under 1 MB", fontDir.listFiles()!!.sumOf { it.length() } < 1_000_000)
    }

    @Test fun CFG_21_aMissingFontFileFallsBackToTheSystemSansNeverACrash() {
        val built = ArrayList<String>()
        val all = FontCatalog.family("inter", { true }) { f, w -> built += "${f.resName}@$w"; Font(resId = 1, weight = FontWeight(w)) }
        assertNotEquals(FontFamily.Default, all)
        assertEquals("the variable file is asked at every weight", FontCatalog.VARIABLE_WEIGHTS.map { "inter@$it" }, built)
        assertEquals("the file is gone", FontFamily.Default, FontCatalog.family("inter", { false }) { _, _ -> error("must not load a missing file") })
        assertEquals("Atkinson needs both of its files", FontFamily.Default, FontCatalog.family("atkinson", { it.endsWith("regular") }) { _, _ -> error("not loaded") })
        assertEquals("Space Grotesk missing: still the system sans", FontFamily.Default, FontCatalog.family(FontCatalog.SPACE_GROTESK, { false }) { _, _ -> error("not loaded") })
        assertEquals("System default has no file", FontFamily.Default, FontCatalog.family("system", { true }) { _, _ -> error("not loaded") })
    }

    @Test fun CFG_21_staticFilesAreAskedOnceAtTheirOwnWeight() {
        val built = ArrayList<String>()
        FontCatalog.family("atkinson", { true }) { f, w -> built += "${f.resName}@$w"; Font(resId = 1, weight = FontWeight(w)) }
        assertEquals(listOf("atkinson_hyperlegible_regular@400", "atkinson_hyperlegible_bold@700"), built)
    }

    @Test fun CFG_21_theTypographyCarriesTheChosenFamilyInEveryStyle() {
        val family = FontFamily.Monospace
        val t = hyleTypography(family)
        assertEquals(listOf(family, family, family, family), listOf(t.displayLarge, t.headlineMedium, t.bodyLarge, t.labelSmall).map { it.fontFamily })
        assertEquals(FontFamily.Default, HyleTypography.bodyLarge.fontFamily)
    }

    @Test fun CFG_21_theChosenFontIsLiveStateAndAnUnknownIdIsTheDefault() {
        HyleFont.apply("lexend"); assertEquals("lexend", HyleFont.id)
        HyleFont.apply("nonsense"); assertEquals("space_grotesk", HyleFont.id)
        HyleFont.apply(null); assertEquals("space_grotesk", HyleFont.id)
    }

    @Test fun CFG_21_titlesAreInCapitalsWithTheLocalesRulesAndScriptsWithoutCaseStay() {
        assertEquals("SETTINGS", titleCaps("Settings", Locale.ENGLISH))
        assertEquals("Wallpaper palette, density and text size".uppercase(), titleCaps("Wallpaper palette, density and text size", Locale.ENGLISH))
        assertEquals("the German sharp s", "STRASSE", titleCaps("Straße", Locale.GERMAN))
        assertEquals("the Turkish dotted i", "İŞLEM", titleCaps("işlem", Locale.forLanguageTag("tr")))
        for (s in listOf("الإعدادات", "सेटिंग", "设置", "設定")) assertEquals("no case in $s", s, titleCaps(s, Locale.ENGLISH))
        L10n.setLanguage("de"); try { assertEquals("EINSTELLUNGEN", titleCaps("Einstellungen")) } finally { L10n.setLanguage("") }
    }
}
