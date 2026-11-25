package com.bos.sphere.feature.dock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Wave 57 (profiles), CFG-48: Radar's "Switch to Work" result runs the switch with the profile's settings, by name and by the old word. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileSwitchPointsW57Test {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val port = MapProfileSettings(mutableMapOf("themeMode" to "\"Dark\"", "searchFiles" to "false"))

    @Before fun setUp() { L10n.setLanguage("en"); WorkspaceRepository.settings = port }
    @After fun tearDown() { WorkspaceRepository.get(context).switchTo(DockWorkspaceState.DEFAULT_ID); WorkspaceRepository.settings = null }

    @Test fun CFG_48_radarOffersSwitchToWorkAndRunningItAppliesTheProfile() = runBlocking {
        val m = WorkspaceRepository.get(context)
        val name = "Work" + System.nanoTime() % 1000
        val id = m.createProfile(name, null, setOf("radar"), mapOf("searchFiles" to "true"))
        val p = DockSearchProvider({ m.current }, { t -> "run:${t.kind}:${t.id}" })
        val hit = p.search("switch to $name", 8).single { it.id == "workspace:$id" }
        assertEquals("Switch to $name", hit.title)
        assertEquals("Profile", hit.subtitle)
        assertTrue("the old word still finds it", p.search("workspace", 8).any { it.id == "workspace:$id" })
        assertTrue("and the new one", p.search("profile", 8).any { it.id == "workspace:$id" })
        RunTargets.run(context, RunTarget(RunKind.Workspace, id))
        assertEquals(id, m.current.activeId)
        assertEquals("true", port.values["searchFiles"])
        m.switchTo(DockWorkspaceState.DEFAULT_ID)
        assertEquals("false", port.values["searchFiles"])
        m.delete(id)
    }
}
