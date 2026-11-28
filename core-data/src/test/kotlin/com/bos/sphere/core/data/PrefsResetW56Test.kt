package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 56 (settingsCore), CFG-30: resetting named preferences to their defaults without touching the others. */
class PrefsResetW56Test {
    @Test fun CFG_30_namesAreTheFieldsOfLauncherPrefs() {
        assertTrue(PrefsReset.names.containsAll(listOf("homeStyle", "iconScale", "hotCornerDwellMs", "calmStyle", "language")))
        assertEquals(PrefsReset.names.size, PrefsReset.names.toSet().size)
    }

    @Test fun CFG_30_resetPutsOnlyTheNamedFieldsBack() {
        val cur = LauncherPrefs(iconScale = 0.6f, themeMode = ThemeMode.Light, hotCornersOn = false, uiSounds = false, onboardingDone = true)
        val r = PrefsReset.reset(cur, setOf("iconScale", "themeMode"))
        assertEquals(LauncherPrefs().iconScale, r.iconScale, 0f)
        assertEquals(ThemeMode.Dark, r.themeMode)
        assertFalse(r.hotCornersOn); assertFalse(r.uiSounds); assertTrue(r.onboardingDone)
        assertEquals("nothing named, nothing changes", cur, PrefsReset.reset(cur, emptySet()))
        assertEquals("unknown names are ignored", cur, PrefsReset.reset(cur, setOf("noSuchField")))
    }

    @Test fun CFG_30_everyFieldCanBeResetOnItsOwn() {
        val changed = LauncherPrefs(iconScale = 0.6f, themeMode = ThemeMode.Light, hotCornersOn = false, glanceHotspots = emptySet(), portraitGrid = GridSpec(7, 9))
        for (f in PrefsReset.diff(changed, LauncherPrefs())) {
            val back = PrefsReset.reset(changed, setOf(f))
            assertFalse("$f is back at its default", f in PrefsReset.diff(back, LauncherPrefs()))
            assertEquals("only $f changed", setOf(f), PrefsReset.diff(changed, back))
        }
    }

    @Test fun CFG_30_diffAndIsDefault() {
        val cur = LauncherPrefs(iconScale = 0.6f, uiSounds = false)
        assertEquals(setOf("iconScale", "uiSounds"), PrefsReset.diff(cur, LauncherPrefs()))
        assertTrue(PrefsReset.isDefault(cur, setOf("themeMode")))
        assertFalse(PrefsReset.isDefault(cur, setOf("iconScale")))
        assertEquals(emptySet<String>(), PrefsReset.diff(LauncherPrefs(), LauncherPrefs()))
    }

    @Test fun CFG_30_resettingEveryFieldGivesTheDefaults() {
        val cur = LauncherPrefs(iconScale = 0.6f, uiSounds = false, homeStyle = HomeStyle.Psp, onboardingDone = true)
        assertEquals(LauncherPrefs(), PrefsReset.reset(cur, PrefsReset.names.toSet()))
    }
}
