package com.bos.sphere.core.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Wave 58 (defects58, CFG-60): every preference file the sources open is classified (exported, or device-local and excluded from system backup and
 * transfer), and every exported file survives export, a fresh install and import. The device-bound part of a file (the Comms widget ids) never leaves.
 */
class BackupGapsW58Test {
    private fun root(): File {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, "app/src/main").isDirectory) d = d.parentFile
        return d!!
    }

    /** Every preference file name the main sources open: literal names and the FILE constants of the files that open preferences. */
    private fun openedFiles(): Set<String> {
        val literal = Regex("""(?:getSharedPreferences\(\s*|TolerantPrefs\.of\([^,()]+(?:\([^()]*\))?[^,()]*,\s*)"([A-Za-z0-9_]+)"""")
        val constant = Regex("""const val (?:FILE|[A-Z_]+_FILE) = "([A-Za-z0-9_]+)"""")
        val found = HashSet<String>()
        root().walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" && it.name != "test" && it.name != "node_modules" }
            .filter { it.isFile && it.extension == "kt" }
            .forEach { f ->
                val t = f.readText()
                literal.findAll(t).forEach { found += it.groupValues[1] }
                if (t.contains("getSharedPreferences") || t.contains("TolerantPrefs") || f.name == "StateSchema.kt") constant.findAll(t).forEach { found += it.groupValues[1] }
            }
        return found
    }

    private val exported = setOf("launcher_prefs", "home_layout", "shortcuts", "launchpad_rules", "hidden_apps", "state_meta") + PrefFilesSource.ALLOWED
    private val deviceLocal = StateSchema.BACKUP_EXCLUDED_PREF_FILES + StateSchema.NEVER_EXPORTED_STORES

    @Test fun CFG_60_everyPreferenceFileIsEitherExportedOrDeviceLocalAndTheTwoNeverOverlap() {
        val found = openedFiles()
        for (f in listOf("theming", "launchpad_hub", "icon_colors", "grant_history", "comms_widgets", "theme_undo")) assertTrue("the scan sees $f", f in found)
        assertEquals("classify these: ${found - exported - deviceLocal}", emptySet<String>(), found - exported - deviceLocal)
        assertEquals(emptySet<String>(), exported intersect deviceLocal)
    }

    @Test fun CFG_60_theUserChoicesThatWereLeftOutAreExportedAndTheDeviceBoundOnesStayOut() {
        for (f in listOf("theming", "launchpad_hub", "icon_colors")) assertTrue("$f is exported", f in PrefFilesSource.ALLOWED)
        for (f in listOf(StateSchema.COMMS_WIDGETS_FILE, "preview_window", EditChromeStore.FILE, DeviceBound.FILE, StateSchema.GRANT_HISTORY_FILE, StateSchema.THEME_UNDO_FILE))
            assertTrue("$f stays on the device", f in StateSchema.BACKUP_EXCLUDED_PREF_FILES && f !in PrefFilesSource.ALLOWED)
        val rules = listOf("app/src/main/res/xml/data_extraction_rules.xml", "app/src/main/res/xml/backup_rules.xml").map { File(root(), it).readText() }
        for (r in rules) {
            assertFalse("launchpad_hub is backed up now", r.contains("""path="launchpad_hub.xml""""))
            assertFalse("icon_colors is backed up now", r.contains("""path="icon_colors.xml""""))
            assertTrue(r.contains("""path="comms_widgets.xml""""))
        }
    }

    /** A sample value of each type for every exported file, written into a "phone", exported, and imported into a fresh install. */
    @Test fun CFG_60_everyExportedFileRoundTripsExportFreshInstallImport() {
        val phone = HashMap<String, MapSharedPreferences>()
        fun open(m: HashMap<String, MapSharedPreferences>) = { f: String -> m.getOrPut(f) { MapSharedPreferences() } }
        for (f in PrefFilesSource.ALLOWED) open(phone)(f).edit().putString("s.$f", "v-$f").putBoolean("b", true).putInt("i", 7).putLong("l", 0xFF123456L).putFloat("f", 0.25f).putStringSet("set", setOf("a", "b")).commit()
        open(phone)("launchpad_hub").edit().putString("defaultView", "unread").putBoolean("clearFromShade", false).putBoolean("widgetsSection", true).putString("widgetsSlot", "first")
            .putString("widgets", "[{\"id\":41}]").commit() // an install from before wave 58 still holds widget ids here
        open(phone)("icon_colors").edit().putLong("com.app/.Main#0", 0xFF00AA00L).commit()
        open(phone)("theming").edit().putString("settings", """{"source":"Wallpaper","density":0.9,"type":1.15}""").commit()

        val text = PrefFilesSource(open(phone)).readObjects().getValue(StateSchema.K_PREF_FILES).toString()
        assertFalse("no widget id leaves the device", text.contains("\"widgets\""))

        val fresh = HashMap<String, MapSharedPreferences>()
        open(fresh)("launchpad_hub").edit().putString("widgets", "[{\"id\":7}]").commit() // the new device's own list
        PrefFilesSource(open(fresh)).write(emptyMap(), mapOf(StateSchema.K_PREF_FILES to JSONObject(text)), setOf(StateSchema.K_PREF_FILES))
        for (f in PrefFilesSource.ALLOWED) {
            val want = phone.getValue(f).all.filterKeys { it !in PrefFilesSource.DEVICE_KEYS[f].orEmpty() }
            val got = fresh.getValue(f).all.filterKeys { it !in PrefFilesSource.DEVICE_KEYS[f].orEmpty() }
            assertEquals("$f round trips", want, got)
        }
        assertEquals("the import never replaces the device's own widget list", "[{\"id\":7}]", fresh.getValue("launchpad_hub").getString("widgets", null))
    }
}
