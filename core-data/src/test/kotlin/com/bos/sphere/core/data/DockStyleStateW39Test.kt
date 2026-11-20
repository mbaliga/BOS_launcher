package com.bos.sphere.core.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Wave 39 (dockThemes): the dock style is stored in the dock settings object of the workspace file (`dock.style`), which is exported and
 * restored verbatim with the `dock` section, so a backup carries it with no schema change. A theme pack itself is never stored.
 */
class DockStyleStateW39Test {
    private fun tmp(): File = File.createTempFile("ws39", ".json").also { it.deleteOnExit() }

    @Test fun CFG_01_state_dockStyleTravelsThroughExportAndRestoreWithTheDockSection() {
        val a = tmp(); val b = tmp(); b.delete()
        a.writeText("""{"schemaVersion":1,"dock":{"enabled":true,"mode":"PinnedPlusRecent","edge":"Left","groupShelf":true,"style":"3d"},"activeId":"default","workspaces":[{"id":"default","name":"Home","pins":[],"widgets":[],"actions":[],"notifyApps":null}],"groups":[],"sessions":[]}""")
        val src = WorkspaceFileSource(a)
        val objects = src.readObjects()
        assertEquals("3d", objects.getValue(StateSchema.K_DOCK).getJSONObject("settings").getString("style"))
        WorkspaceFileSource(b).write(src.read(), objects, setOf(StateSchema.K_WORKSPACES, StateSchema.K_SESSIONS, StateSchema.K_DOCK))
        assertEquals("3d", JSONObject(b.readText()).getJSONObject("dock").getString("style"))
    }

    @Test fun CFG_01_state_aBackupFromBeforeTheStyleExistedLeavesTheStyleAsItIs() {
        val target = tmp()
        target.writeText("""{"schemaVersion":1,"dock":{"enabled":null,"mode":"PinnedPlusRecent","edge":"Bottom","style":"transparent"},"activeId":"default","workspaces":[{"id":"default","name":"Home","pins":[],"widgets":[],"actions":[],"notifyApps":null}],"groups":[],"sessions":[]}""")
        // an old export's dock settings carry no style: the section is written whole, so the style falls back to the default on the next read
        val oldSettings = JSONObject("""{"enabled":null,"mode":"PinnedPlusRecent","edge":"Bottom"}""")
        WorkspaceFileSource(target).write(emptyMap(), mapOf(StateSchema.K_DOCK to JSONObject().put("settings", oldSettings).put("activeId", "default").put("groups", org.json.JSONArray())), setOf(StateSchema.K_DOCK))
        assertFalse(JSONObject(target.readText()).getJSONObject("dock").has("style"))
        assertEquals(DockStyle.Standard, DockStyle.orDefault(JSONObject(target.readText()).getJSONObject("dock").optString("style", null)))
    }

    @Test fun CFG_01_state_noThemePackIsStoredAnywhere() {
        // the only persisted look settings are the ordinary ones; the pack model has no store or file-writing code of its own
        val src = File(System.getProperty("user.dir"), "src/main/kotlin/com/bos/sphere/core/data").let { listOf("ThemePack.kt", "ThemeLook.kt").map { n -> File(it, n).readText() } }
        src.forEach { s -> listOf("SharedPreferences", "TolerantPrefs", "File(", "openFileOutput", "writeText").forEach { w -> assertFalse("$w in a theme pack source", s.contains(w)) } }
        assertTrue(src.all { it.isNotEmpty() })
    }
}
