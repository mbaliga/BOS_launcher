package com.bos.sphere.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.dock.DockWorkspaceState
import com.bos.sphere.feature.dock.InMemoryWorkspaceStore
import com.bos.sphere.feature.dock.MapProfileSettings
import com.bos.sphere.feature.dock.ProfileTags
import com.bos.sphere.feature.dock.WorkspaceManager
import com.bos.sphere.feature.grid.SettingsIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Wave 57 (profiles), CFG-48: the profile chip row at the top of the long-press Home sheet (the sheet is not restructured, the row is added
 * under its title) switches with the profile's settings; CFG-45: the Settings row is Profiles where Workspaces was, and "workspace" finds it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class ProfilesHomeSheetW57Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val port = MapProfileSettings(mutableMapOf("themeMode" to "\"Dark\""))
    private var seq = 0

    @Before fun setUp() { L10n.setLanguage("en") }

    private fun manager(two: Boolean = true) = WorkspaceManager(InMemoryWorkspaceStore(), newId = { "p${seq++}" }, settingsPort = { port }).also {
        if (two) it.createProfile("Calm", null, setOf("look"), mapOf("themeMode" to "\"Light\""))
    }

    private fun sheet(m: WorkspaceManager) = rule.setContent {
        val s by m.state.collectAsState()
        Box(Modifier.background(Color.Black)) { HomeQuickSheetBody(LauncherPrefs(), {}, null, 1, {}, {}, {}, profiles = s, onProfile = { m.switchTo(it) }) }
    }

    @Test fun CFG_48_theHomeSheetHasAProfileChipRowUnderItsTitleAndATapSwitches() {
        val m = manager(); sheet(m)
        rule.onNodeWithTag(ProfileTags.CHIPS).assertIsDisplayed()
        assertTrue("under the title", rule.onNodeWithTag(ProfileTags.CHIPS).getUnclippedBoundsInRoot().top > rule.onNodeWithTag("quick.sheet").getUnclippedBoundsInRoot().bottom)
        assertTrue("above the entry tiles", rule.onNodeWithTag(ProfileTags.CHIPS).getUnclippedBoundsInRoot().bottom < rule.onNodeWithTag("quick.add.apps").getUnclippedBoundsInRoot().top)
        val calm = m.current.workspaces.first { it.name == "Calm" }.id
        assertEquals(true, rule.onNodeWithTag(ProfileTags.chip(DockWorkspaceState.DEFAULT_ID)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        rule.onNodeWithTag(ProfileTags.chip(calm)).performClick(); rule.waitForIdle()
        assertEquals(calm, m.current.activeId)
        assertEquals("\"Light\"", port.values["themeMode"])
        assertEquals(true, rule.onNodeWithTag(ProfileTags.chip(calm)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        shoot("w57-home-sheet-profiles")
    }

    @Test fun CFG_48_withOneProfileTheSheetHasNoChipRow() {
        sheet(manager(two = false))
        rule.onAllNodesWithTag(ProfileTags.CHIPS).assertCountEquals(0)
    }

    @Test fun CFG_45_theSettingsRowIsProfilesAndTheOldWordStillFindsIt() {
        val c = profilesCategory()
        assertEquals("Profiles", c.title)
        val index = SettingsIndex.fromCategories(listOf(c))
        assertTrue(index.search("workspace").any { it.title == "Profiles" })
        assertTrue(index.search("profile").any { it.title == "Profiles" })
    }

    private fun shoot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
