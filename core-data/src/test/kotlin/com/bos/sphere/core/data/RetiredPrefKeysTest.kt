package com.bos.sphere.core.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** CFG-03 (wave 20 cleanup): the keys of the retired features leave the private files once, tolerantly, and never come back through an import. */
class RetiredPrefKeysTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun files(): Map<String, MapSharedPreferences> = mapOf(
        "launcher_prefs" to MapSharedPreferences(mapOf("bgKind" to "Live", "bgPreset" to "aurora", "bgA" to 1L, "wallpaper" to true, "minimalHints" to true, "networkDots" to true, "style" to "Grid", "bgSlot1" to "spire")),
        "palette" to MapSharedPreferences(mapOf("scopeMapping" to "a=Apps", "prefixes" to "x", "usage" to "u", "aliases" to "a\tb")),
        "crash_loop" to MapSharedPreferences(mapOf("rapid" to 3, "crashes" to 1)),
    )

    @Test fun CFG_03_everyRetiredKeyIsRemovedAndEveryLiveKeyStays() {
        val f = files()
        val n = RetiredPrefKeys.purge { f[it] }
        assertEquals("4 background + minimalHints + networkDots + 2 palette + rapid", 9, n)
        for (k in listOf("bgKind", "bgPreset", "bgA", "wallpaper", "minimalHints", "networkDots")) assertFalse(k, f.getValue("launcher_prefs").contains(k))
        assertTrue(f.getValue("launcher_prefs").contains("style")); assertTrue(f.getValue("launcher_prefs").contains("bgSlot1"))
        assertFalse(f.getValue("palette").contains("scopeMapping")); assertFalse(f.getValue("palette").contains("prefixes"))
        assertTrue(f.getValue("palette").contains("usage")); assertTrue(f.getValue("palette").contains("aliases"))
        assertFalse(f.getValue("crash_loop").contains("rapid")); assertTrue(f.getValue("crash_loop").contains("crashes"))
    }

    @Test fun CFG_03_w29_theArcDrawerPrefsOfBuilds55To58AreDroppedClean() {
        val keys = listOf("switcherStyle", "drawerShortcutsOn", "drawerShortcuts", "drawerShortcutsShown")
        val f = mapOf("launcher_prefs" to MapSharedPreferences(keys.associateWith { "x" } + mapOf("wheelGrouping" to "Letter")))
        assertEquals(4, RetiredPrefKeys.purge { f[it] })
        assertTrue("the wheel groups setting is live again and stays", f.getValue("launcher_prefs").contains("wheelGrouping"))
        for (k in keys) assertTrue("$k is dropped when a settings file is imported", k in RetiredPrefKeys.DROPPED_ON_IMPORT)
    }

    @Test fun CFG_03_aSecondPurgeFindsNothingAndAMissingOrBrokenFileIsSkipped() {
        val f = files()
        RetiredPrefKeys.purge { f[it] }
        assertEquals(0, RetiredPrefKeys.purge { f[it] })
        assertEquals(0, RetiredPrefKeys.purge { null })
        assertEquals(0, RetiredPrefKeys.purge { error("unreadable file") })
    }

    @Test fun CFG_03_theMarkerMakesItRunOncePerInstall() {
        val f = files(); val marker = tmp.root.resolve("sub/retired")
        assertTrue(RetiredPrefKeys.purgeOnce(marker) { f[it] } > 0)
        assertTrue(marker.exists())
        f.getValue("palette").edit().putString("prefixes", "again").apply()
        assertEquals("a marked install is never touched again", 0, RetiredPrefKeys.purgeOnce(marker) { f[it] })
        assertTrue(f.getValue("palette").contains("prefixes"))
    }

    @Test fun CFG_03_anOldSettingsFileDropsTheRetiredKeysInsteadOfKeepingThemAsUnknownFields() {
        val doc = SettingsCodec.encode(LauncherPrefs())
        doc.getJSONObject("prefs").put("minimalHints", false).put("bgKind", "Live")
        val decoded = SettingsCodec.decode(doc)
        assertFalse(decoded.unknown.has("minimalHints")); assertFalse(decoded.unknown.has("bgKind"))
        assertFalse(SettingsCodec.encode(decoded.prefs).getJSONObject("prefs").has("minimalHints"))
    }

    @Test fun CFG_03_theLauncherStoreNoLongerWritesTheRetiredHintKey() {
        val sp: SharedPreferences = MapSharedPreferences(emptyMap())
        val store = LauncherPrefsStore(sp)
        store.update { it.copy(recentsLimit = 20) }
        assertFalse(sp.contains("minimalHints"))
        assertEquals(20, store.prefs.value.recentsLimit)
    }
}
