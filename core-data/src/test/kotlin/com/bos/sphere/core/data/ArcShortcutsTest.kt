package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** W28 arcShortcuts: the shortcut rules (at most 4), the visibility table and the persisted prefs. */
class ArcShortcutsTest {
    @Test fun ARC_10_the_shortcut_count_is_clamped_at_four_and_duplicates_drop() {
        assertEquals(4, ArcShortcuts.MAX)
        assertEquals(listOf("a", "b", "c", "d"), ArcShortcuts.clamp(listOf("a", "b", "b", "c", "d", "e", "f")))
        assertEquals(listOf("a", "b", "c", "d"), ArcShortcuts.add(listOf("a", "b", "c", "d"), "e"))
        assertEquals(listOf("a", "b"), ArcShortcuts.add(listOf("a", "b"), "a"))
        assertEquals(listOf("a", "b", "c"), ArcShortcuts.add(listOf("a", "b"), "c"))
        assertEquals(emptyList<String>(), ArcShortcuts.clamp(listOf("", " ")))
    }

    @Test fun ARC_10_shortcuts_can_be_removed_and_reordered() {
        assertEquals(listOf("a", "c"), ArcShortcuts.remove(listOf("a", "b", "c"), "b"))
        assertEquals(listOf("b", "a", "c"), ArcShortcuts.move(listOf("a", "b", "c"), "a", +1))
        assertEquals(listOf("c", "a", "b"), ArcShortcuts.move(listOf("a", "b", "c"), "c", -5))
        assertEquals(listOf("a", "b"), ArcShortcuts.move(listOf("a", "b"), "zzz", 1))
    }

    @Test fun ARC_11_visibility_table_always_only_with_the_wheel_and_off() {
        // Always: at rest and with the wheel. Only with the wheel: only with the wheel. Off (or no apps chosen): nowhere.
        assertTrue(ArcShortcuts.visibleAtRest(true, ShortcutsShown.Always, 2))
        assertTrue(ArcShortcuts.visibleWithWheel(true, 2))
        assertFalse(ArcShortcuts.visibleAtRest(true, ShortcutsShown.OnlyWithWheel, 2))
        assertTrue(ArcShortcuts.visibleWithWheel(true, 2))
        assertFalse(ArcShortcuts.visibleAtRest(false, ShortcutsShown.Always, 2))
        assertFalse(ArcShortcuts.visibleWithWheel(false, 2))
        assertFalse(ArcShortcuts.visibleAtRest(true, ShortcutsShown.Always, 0))
        assertFalse(ArcShortcuts.visibleWithWheel(true, 0))
    }

    @Test fun ARC_12_the_prefs_persist_and_garbage_falls_back() {
        val sp = MapSharedPreferences()
        LauncherPrefsStore(sp).update { it.copy(drawerShortcutsOn = true, drawerShortcuts = listOf("p/a", "q/b"), drawerShortcutsShown = ShortcutsShown.OnlyWithWheel) }
        val back = LauncherPrefsStore(sp).prefs.value
        assertTrue(back.drawerShortcutsOn)
        assertEquals(listOf("p/a", "q/b"), back.drawerShortcuts)
        assertEquals(ShortcutsShown.OnlyWithWheel, back.drawerShortcutsShown)
        val d = LauncherPrefs()
        assertFalse(d.drawerShortcutsOn); assertEquals(ShortcutsShown.Always, d.drawerShortcutsShown)
        val bad = LauncherPrefsStore(MapSharedPreferences(mapOf("drawerShortcutsShown" to "Huge", "drawerShortcuts" to "a\nb\nc\nd\ne\nf"))).prefs.value
        assertEquals(ShortcutsShown.Always, bad.drawerShortcutsShown)
        assertEquals(4, bad.drawerShortcuts.size)
    }

    @Test fun ARC_12_the_old_switcher_style_key_is_retired() {
        assertTrue("switcherStyle" in RetiredPrefKeys.BY_FILE.getValue("launcher_prefs"))
        assertTrue("switcherStyle" in RetiredPrefKeys.DROPPED_ON_IMPORT)
    }
}
