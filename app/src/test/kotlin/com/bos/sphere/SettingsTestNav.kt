package com.bos.sphere

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingsTabTags
import com.bos.sphere.feature.grid.SettingsTabs

/**
 * Wave 57 (settingsTabs): how a test reaches a row now that Settings is six tabs. The tests used to open a category by its title ("Home screen",
 * "Corners", "App drawer"); [tabOf] maps those old category names (and the rows that moved out of them) to the tab that holds them now, [goTo]
 * selects that tab and scrolls its list to the row, and [tab] finds a tab's rows in a built list.
 */
object SettingsTestNav {
    /** The tab that holds a row which used to be under [oldCategory]; [row] matters for the rows that moved out of their old page. */
    fun tabOf(oldCategory: String, row: String? = null): String = when {
        row != null && row in LOOK_ROWS -> SettingsTabs.LOOK
        row != null && row in RADAR_ROWS -> SettingsTabs.RADAR
        row != null && row in SIDE_ROWS -> SettingsTabs.SIDE_PANEL
        row != null && row in SYSTEM_ROWS -> SettingsTabs.SYSTEM
        row != null && row in APPS_ROWS -> SettingsTabs.APPS
        else -> when (oldCategory) {
            "Pages", "Home screen", "Corners", "Gestures" -> SettingsTabs.HOME
            "Desktop & dock" -> if (row == "App groups") SettingsTabs.APPS else SettingsTabs.HOME
            "Look" -> SettingsTabs.LOOK
            "App drawer", "Hidden apps", "Workspaces" -> SettingsTabs.APPS
            "Radar" -> SettingsTabs.RADAR
            "Side panel", "Comms" -> SettingsTabs.SIDE_PANEL
            else -> SettingsTabs.SYSTEM // System & sound, Backup, Capabilities, Lab, About
        }
    }

    private val LOOK_ROWS = setOf("Glance wallpaper", "Pages use the same wallpaper as Glance", "Pages wallpaper")
    private val RADAR_ROWS = setOf("Keyboard opens with Radar and drawer · portrait", "Keyboard opens with Radar and drawer · landscape")
    private val SIDE_ROWS = setOf("Right side", "Recent apps shown", "Include apps opened from elsewhere")
    private val SYSTEM_ROWS = setOf("Restore open screens")
    private val APPS_ROWS = setOf("App groups", "Workspaces", "Hidden apps")

    /** The built tab of [key] ("Home", "Look" ...). */
    fun tab(categories: List<SettingCategory>, key: String): SettingCategory = categories.first { SettingsTabs.key(it.title) == key }

    /** Selects the tab [tab] (by its English title) and, when [row] is given, brings that row into sight through the search (it opens the row's own tab). */
    fun goTo(rule: ComposeTestRule, tab: String, row: String? = null, substring: Boolean = false) {
        rule.onNodeWithTag(SettingsTabTags.tab(tab), useUnmergedTree = true).performClick()
        rule.mainClock.advanceTimeBy(700); rule.waitForIdle()
        if (row != null) {
            // Through Settings' own search (as a user would): the hit switches to the row's tab, scrolls the list to it and flashes it. A scroll
            // through the list's own accessibility actions waits on the UI thread for frames, which the whole-app tests (their main looper is
            // driven by hand) do not give it, so it is not used here.
            val search = rule.onNodeWithTag("oneui.search", useUnmergedTree = true)
            search.performTextClearance(); search.performTextInput(row)
            idle(rule, 600)
            rule.onAllNodes(androidx.compose.ui.test.hasTestTag("oneui.result") and androidx.compose.ui.test.hasAnyDescendant(hasText(row, substring = substring)), useUnmergedTree = true)[0].performClick()
            idle(rule, 1200); idle(rule, 1200)
        }
    }

    private fun idle(rule: ComposeTestRule, ms: Long) {
        org.robolectric.shadows.ShadowLooper.idleMainLooper(); rule.mainClock.advanceTimeBy(ms); org.robolectric.shadows.ShadowLooper.idleMainLooper(); rule.waitForIdle()
    }

    /** [goTo] by an old category name. */
    fun open(rule: ComposeTestRule, oldCategory: String, row: String? = null, substring: Boolean = false) = goTo(rule, tabOf(oldCategory, row), row, substring)

    /** Whether the tab [tab]'s list is on screen (the tab is selected). */
    fun tabShown(rule: ComposeTestRule, tab: String) = rule.onAllNodesWithTag(SettingsTabTags.list(tab), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
}
