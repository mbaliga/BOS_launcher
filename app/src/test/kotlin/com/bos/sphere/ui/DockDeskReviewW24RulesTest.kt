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

    @Test fun CFG_14_settingsSearchFindsTheDockPanelByWorkspacesAndGroups() {
        L10n.setLanguage("en")
        val index = SettingsIndex.fromCategories(listOf(desktopCategory()))
        listOf("workspaces", "groups", "sessions").forEach { q ->
            assertTrue("\"$q\" finds the dock panel row", index.search(q).any { it.title == "Workspaces, groups and sessions" })
        }
    }

    @Test fun INP_05_theToastsNameGroundControlWithTheSettingsOwnLabelsInEveryLanguage() {
        // Ground Control's category is "Home screen" (SettingsCategories' HOME_SCREEN_TITLE); there is no category called "Home".
        listOf("en", "es", "fr", "de", "pt", "hi", "zh", "ja", "ar").forEach { code ->
            L10n.setLanguage(code)
            if (code != "en") assertTrue("$code: the category title is translated", L10n.tr("Home screen") != "Home screen")
            val landing = L10n.tr(FocusDockRouting.NoDock.Landing.message)
            listOf("Home screen", "Right side", "Dock rail").forEach { label ->
                assertTrue("$code: the landing toast names \"$label\" as Ground Control shows it (${L10n.tr(label)}): $landing", landing.contains(L10n.tr(label)))
            }
            val off = L10n.tr(FocusDockRouting.NoDock.Off.message)
            assertTrue("$code: the dock-off toast names the Desktop & dock page as shown: $off", off.contains(L10n.tr("Desktop & dock")))
        }
    }
}
