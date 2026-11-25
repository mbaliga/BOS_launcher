package com.bos.sphere.ui

import com.bos.sphere.core.data.ChromeSettings
import com.bos.sphere.core.data.HomeShown
import com.bos.sphere.core.data.RightSide
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.SettingsIndex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 24 second review (dockDesk), JVM rules: what "Focus the dock" says while the home is edited, that Ground Control's search (and
 * Radar's, CFG-14) finds the dock panel by "workspaces" or "groups" with the dock off, and that the toasts name the Ground Control
 * path with the labels the settings themselves show in every language. No device run.
 */
class DockDeskReviewW24RulesTest {
    @After fun english() = L10n.setLanguage("en")

    @Test fun INP_05_whileTheHomeIsEditedFocusDockSaysWhyInsteadOfNothing() {
        val c = ChromeSettings()
        assertEquals(FocusDockRouting.NoDock.Editing, FocusDockRouting.noDock(HomeShown.Pages, c, dockEnabled = true, editing = true))
        assertEquals(FocusDockRouting.NoDock.Editing, FocusDockRouting.noDock(HomeShown.Glance, c.copy(rightSide = RightSide.DockRail), dockEnabled = false, editing = true))
        assertEquals("the dock being off is the reason to fix first", FocusDockRouting.NoDock.Off, FocusDockRouting.noDock(HomeShown.Pages, c, dockEnabled = false, editing = true))
        assertNull(FocusDockRouting.noDock(HomeShown.Pages, c, dockEnabled = true, editing = false))
    }

    @Test fun CFG_14_settingsSearchFindsTheDockPagesByWorkspacesAndGroups() {
        L10n.setLanguage("en")
        val index = SettingsIndex.fromCategories(listOf(profilesCategory(), desktopCategory())) // wave 52: its own category; wave 57: Profiles (was Workspaces)
        assertTrue("workspaces (a hidden alias since wave 57) finds the Profiles page", index.search("workspaces").any { it.title == "Profiles" })
        assertTrue("profiles finds the Profiles page", index.search("profiles").any { it.title == "Profiles" })
        assertTrue("groups finds the App groups page", index.search("groups").any { it.title == "App groups" })
        assertTrue("sessions finds the App groups page by its summary", index.search("sessions").any { it.title == "App groups" })
        assertTrue("dock finds the Dock page", index.search("dock").any { it.title == "Dock" })
    }

    @Test fun INP_05_theToastsNameSettingsWithTheSettingsOwnLabelsInEveryLanguage() {
        // Wave 57: the toasts say Settings (not Ground Control) and name the tab and the row as Settings shows them: Side panel > Right side, Home > Dock.
        listOf("en", "es", "fr", "de", "pt", "hi", "zh", "ja", "ar").forEach { code ->
            L10n.setLanguage(code)
            val landing = L10n.tr(FocusDockRouting.NoDock.Landing.message)
            listOf("Settings", "Side panel", "Right side", "Dock rail").forEach { label ->
                assertTrue("$code: the landing toast names \"$label\" as Settings shows it (${L10n.tr(label)}): $landing", landing.contains(L10n.tr(label)))
            }
            val off = L10n.tr(FocusDockRouting.NoDock.Off.message)
            listOf("Settings", "Home", "Dock").forEach { label -> assertTrue("$code: the dock-off toast names \"$label\" as shown: $off", off.contains(L10n.tr(label))) }
            assertTrue("$code: no Ground Control", !landing.contains("Ground Control") && !off.contains("Ground Control"))
        }
    }
}
