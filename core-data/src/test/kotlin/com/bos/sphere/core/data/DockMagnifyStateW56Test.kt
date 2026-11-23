package com.bos.sphere.core.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Wave 56 (dockMagnify): `dock.magnify` and `dock.magnifyStrength` live in the dock settings object of the workspace file, which settings export and
 * import carry verbatim with the `dock` section; no preference key is added (so nothing for the retired-key audit), and an older file simply has neither.
 */
class DockMagnifyStateW56Test {
    private fun tmp(): File = File.createTempFile("ws56", ".json").also { it.deleteOnExit() }
    private val ws = """"activeId":"default","workspaces":[{"id":"default","name":"Home","pins":[],"widgets":[],"actions":[],"notifyApps":null}],"groups":[],"sessions":[]"""

    @Test fun DCK_04_state_magnifySettingsTravelThroughExportAndImportWithTheDockSection() {
        val a = tmp(); val b = tmp(); b.delete()
        a.writeText("""{"schemaVersion":1,"dock":{"enabled":true,"mode":"PinnedPlusRecent","edge":"Bottom","groupShelf":true,"style":"3d","magnify":true,"magnifyStrength":"large"},$ws}""")
        val src = WorkspaceFileSource(a)
        val objects = src.readObjects()
        val settings = objects.getValue(StateSchema.K_DOCK).getJSONObject("settings")
        assertTrue(settings.getBoolean("magnify")); assertEquals("large", settings.getString("magnifyStrength"))
        WorkspaceFileSource(b).write(src.read(), objects, setOf(StateSchema.K_WORKSPACES, StateSchema.K_SESSIONS, StateSchema.K_DOCK))
        val written = JSONObject(b.readText()).getJSONObject("dock")
        assertTrue(written.getBoolean("magnify")); assertEquals("large", written.getString("magnifyStrength")); assertEquals("3d", written.getString("style"))
    }

    @Test fun DCK_04_state_aBackupFromBeforeMagnificationHasNeitherKeyAndTheDockReadsItAsOff() {
        val target = tmp()
        target.writeText("""{"schemaVersion":1,"dock":{"enabled":null,"mode":"PinnedPlusRecent","edge":"Bottom"},$ws}""")
        val oldSettings = JSONObject("""{"enabled":null,"mode":"PinnedPlusRecent","edge":"Bottom"}""")
        WorkspaceFileSource(target).write(emptyMap(), mapOf(StateSchema.K_DOCK to JSONObject().put("settings", oldSettings).put("activeId", "default").put("groups", org.json.JSONArray())), setOf(StateSchema.K_DOCK))
        val dock = JSONObject(target.readText()).getJSONObject("dock")
        assertFalse(dock.has("magnify")); assertFalse(dock.has("magnifyStrength"))
        assertFalse("absent reads as off", dock.optBoolean("magnify", false))
    }

    @Test fun DCK_04_state_noPreferenceKeyWasAddedForMagnificationSoTheRetiredKeyAuditHasNothingToAdd() {
        val retired = File(System.getProperty("user.dir"), "src/main/kotlin/com/bos/sphere/core/data/RetiredPrefKeys.kt").readText()
        assertFalse(retired.contains("magnify"))
        val prefs = File(System.getProperty("user.dir"), "src/main/kotlin/com/bos/sphere/core/data").walkTopDown().filter { it.extension == "kt" }.map { it.readText() }.joinToString("\n")
        assertFalse("magnification is dock state in the workspace file, not a launcher preference", Regex("""(getBoolean|putBoolean|getString|putString)\("magnify""").containsMatchIn(prefs))
    }
}
