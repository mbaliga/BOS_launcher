package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

/** Wave 52 (settingsFont), CFG-21: the UI font is a stored setting: default, persisted, exported and imported with the settings file, and optional in a theme pack. */
class UiFontW52Test {
    @Test fun CFG_21_theDefaultFontIsSpaceGroteskAndTheIdsAreTheKnownSet() {
        assertEquals("space_grotesk", LauncherPrefs().uiFont)
        assertEquals(listOf("space_grotesk", "system", "inter", "manrope", "dm_sans", "jetbrains_mono", "atkinson", "lexend"), UiFonts.IDS)
        assertEquals("space_grotesk", UiFonts.normalize("Wingdings")); assertEquals("space_grotesk", UiFonts.normalize(null)); assertEquals("inter", UiFonts.normalize("inter"))
    }

    @Test fun CFG_21_theChosenFontSurvivesTheSettingsFileExportAndImport() {
        val doc = SettingsCodec.encode(LauncherPrefs(uiFont = "jetbrains_mono"))
        assertEquals("jetbrains_mono", doc.getJSONObject("prefs").getString("uiFont"))
        assertEquals("jetbrains_mono", SettingsCodec.decode(doc).prefs.uiFont)
    }

    @Test fun CFG_21_aFileWithoutAFontOrWithAnUnknownOneIsTheDefaultWhereItIsUsed() {
        val none = JSONObject().put("prefs", JSONObject().put("themeMode", "Dark"))
        assertEquals("space_grotesk", SettingsCodec.decode(none).prefs.uiFont)
        val bad = JSONObject().put("prefs", JSONObject().put("uiFont", "Comic Sans"))
        assertEquals("space_grotesk", UiFonts.normalize(SettingsCodec.decode(bad).prefs.uiFont))
    }

    @Test fun CFG_21_aStoredFontIsReadBackFromThePreferenceFile() {
        val sp = MapSharedPreferences()
        LauncherPrefsStore(sp).update { it.copy(uiFont = "lexend") }
        assertEquals("lexend", sp.all["uiFont"])
        assertEquals("lexend", LauncherPrefsStore(sp).prefs.value.uiFont)
    }

    private val head = """{"format":"liftoff-theme","formatVersion":1,"name":"Mono","settings":"""
    private fun ok(settings: String) = (ThemePacks.parse("$head$settings}") as ThemeParse.Ok).pack

    @Test fun CFG_21_aThemePackMayCarryTheFontAndAnOldPackWithoutOneLeavesItAlone() {
        val p = ok("""{"uiFont":"jetbrains_mono","themeMode":"dark"}""")
        assertEquals("jetbrains_mono", p.settings.uiFont)
        assertEquals("jetbrains_mono", ThemeLook.apply(LauncherPrefs(), p.settings).uiFont)
        val old = ok("""{"themeMode":"light"}""")
        assertNull(old.settings.uiFont)
        assertEquals("inter", ThemeLook.apply(LauncherPrefs(uiFont = "inter"), old.settings).uiFont)
        val back = ok(JSONObject(ThemePacks.encode(p)).getJSONObject("settings").toString())
        assertEquals("the font is written back out", "jetbrains_mono", back.settings.uiFont)
    }

    @Test fun CFG_21_aThemePackWithAnUnknownFontIsRefusedNotApplied() {
        val r = ThemePacks.parse("""$head{"uiFont":"../../etc/passwd","themeMode":"dark"}}""")
        assertTrue(r is ThemeParse.Failed)
        assertTrue((r as ThemeParse.Failed).errors.any { it.field == ThemeField.Font })
        assertFalse(ThemeSettings(uiFont = "inter").isEmpty)
    }

    @Test fun CFG_21_resetAndUndoRestoreTheFont() {
        val before = LauncherPrefs(uiFont = "manrope")
        val snap = ThemeLook.snapshot(before, DockStyle.Standard)
        val changed = ThemeLook.apply(before, ThemeSettings(uiFont = "inter"))
        assertEquals("manrope", ThemeLook.restore(changed, snap).uiFont)
        assertEquals("space_grotesk", ThemeLook.restore(changed, ThemeLook.defaultSnapshot()).uiFont)
        assertEquals("jetbrains_mono", ThemeLook.capture(LauncherPrefs(uiFont = "jetbrains_mono"), DockStyle.Standard, "N").settings.uiFont)
    }
}
