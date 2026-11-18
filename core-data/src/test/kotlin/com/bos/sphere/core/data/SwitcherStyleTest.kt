package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** ARC-02 (W26): "Switcher: Compact or Expanded" is a persisted setting, Expanded by default, with only those two options. */
class SwitcherStyleTest {
    @Test fun ARC_02_onlyCompactAndExpandedExistAndExpandedIsTheDefault() {
        assertEquals(listOf(SwitcherStyle.Compact, SwitcherStyle.Expanded), SwitcherStyle.entries)
        assertEquals(SwitcherStyle.Expanded, LauncherPrefs().switcherStyle)
    }

    @Test fun ARC_02_theChoiceSurvivesARestart() {
        val sp = MapSharedPreferences()
        LauncherPrefsStore(sp).update { it.copy(switcherStyle = SwitcherStyle.Compact) }
        assertEquals(SwitcherStyle.Compact, LauncherPrefsStore(sp).prefs.value.switcherStyle)
        assertEquals("Compact", sp.getString("switcherStyle", null))
    }

    @Test fun ARC_02_aMissingOrUnknownStoredValueIsExpanded() {
        assertEquals(SwitcherStyle.Expanded, LauncherPrefsStore(MapSharedPreferences()).prefs.value.switcherStyle)
        assertEquals(SwitcherStyle.Expanded, LauncherPrefsStore(MapSharedPreferences(mapOf("switcherStyle" to "Huge"))).prefs.value.switcherStyle)
    }
}
