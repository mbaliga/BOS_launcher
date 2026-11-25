package dev.aarso.search.palette

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 57 (profiles), CFG-48: Radar's launcher actions name profiles: "Switch to Work", found by "switch to", "profile" and the old "workspace". */
class ProfilesRadarW57Test {
    @Test fun CFG_48_switchToAProfileByNameAndByTheOldWord() = runBlocking {
        val p = LauncherActionProvider(workspaces = { listOf("Home", "Work", "Calm") })
        val hit = p.query(PaletteQuery("switch to calm"), 5).first { it.payload["command"] == "GoToWorkspace" }
        assertEquals("Switch to Calm", hit.title)
        assertEquals("2", hit.payload["index"])
        assertTrue(p.query(PaletteQuery("switch workspace work"), 5).any { it.payload["index"] == "1" })
        assertTrue(p.query(PaletteQuery("next profile"), 5).any { it.payload["command"] == "NextWorkspace" && it.title == "Switch to next profile" })
    }
}
