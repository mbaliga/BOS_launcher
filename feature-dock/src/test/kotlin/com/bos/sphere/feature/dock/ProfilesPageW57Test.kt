package com.bos.sphere.feature.dock

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import com.bos.sphere.core.data.HomeLayoutCodec
import com.bos.sphere.core.data.ProfileGroup
import com.bos.sphere.core.data.ProfileStart
import com.bos.sphere.core.data.SuggestApp
import com.bos.sphere.core.data.SuggestionKind
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.core.launch.LaunchTarget
import com.bos.sphere.feature.grid.OneUiDialogTags
import com.bos.sphere.feature.grid.OneUiPageFrame
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
 * Wave 57 (profiles): the Profiles page (CFG-45), the New profile page with starters and settings that ride along (CFG-45, CFG-46),
 * the suggested app groups with their edit page (CFG-47), switching and Switch back from the page and the chip row (CFG-48), screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
class ProfilesPageW57Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun setUp() { L10n.setLanguage("en"); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF); DockInput.dialogHook = {} }
    @After fun tearDown() { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF); L10n.setLanguage("en") }

    private class Hooks : WorkspaceHooks {
        var home: String? = ProfilesFixtures.HOME
        val switched = mutableListOf<String>()
        override fun captureHome() = home
        override fun applyHome(arrangement: String?) { if (arrangement != null) home = arrangement }
        override fun onSwitched(workspace: Workspace) { switched += workspace.name }
    }

    private val hooks = Hooks()
    private val port = MapProfileSettings(mutableMapOf("themeMode" to "\"Dark\"", "searchFiles" to "false", "searchWeb" to "true", "homePages" to "\"Paginated\""))
    private var seq = 0
    private val packages = listOf(
        "com.google.android.gm" to "Gmail", "com.Slack" to "Slack", "com.microsoft.teams" to "Teams", "us.zoom.videomeetings" to "Zoom", "com.google.android.calendar" to "Calendar",
        "com.whatsapp" to "WhatsApp", "org.telegram.messenger" to "Telegram", "com.instagram.android" to "Instagram",
        "com.google.android.youtube" to "YouTube", "com.spotify.music" to "Spotify",
        "com.google.android.deskclock" to "Clock",
    )
    private val apps = packages.map { (p, l) -> PanelApp("$p/.Main#0", l, p, LaunchTarget(p, "$p.Main", 0)) }
    private val facts = apps.map { SuggestApp(it.key, it.packageName, it.label) }

    private fun manager(second: Boolean = true): WorkspaceManager {
        val m = WorkspaceManager(InMemoryWorkspaceStore(), { hooks }, newId = { "id${seq++}" }, settingsPort = { port })
        if (second) m.createProfile("Calm", null, setOf("look"), mapOf("themeMode" to "\"Light\""), homeArrangement = ProfilesFixtures.CALM)
        return m
    }

    private fun show(m: WorkspaceManager, light: Boolean = false, font: Float = 1f) {
        if (light) HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF)
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, font)) {
                DockPagesScope {
                    val s by m.state.collectAsState()
                    OneUiPageFrame("Profiles", {}, subtitle = WorkspaceSummary.activeLine(s), tag = DockPanelTags.PAGE) {
                        item { androidx.compose.foundation.layout.Column { ProfilesContent(m, apps, facts) } }
                    }
                }
            }
        }
        settle()
    }

    private fun settle() { rule.mainClock.advanceTimeBy(400); rule.waitForIdle() }

    /** Brings a row of the New profile page (a lazy list) on screen. */
    private fun onNew(tag: String): androidx.compose.ui.test.SemanticsNodeInteraction {
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction() and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag(ProfileTags.NEW_PAGE)))
            .performScrollToNode(androidx.compose.ui.test.hasTestTag(tag))
        settle()
        return rule.onNodeWithTag(tag)
    }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(outDir, "$name.png").length() > 2000)
    }

    private fun text(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()

    // ---- the page

    @Test fun CFG_45_theActiveProfileIsADrawnCardThatSaysWhatItHolds() {
        val m = manager(); show(m)
        rule.onNodeWithTag(ProfileTags.ACTIVE_CARD).assertIsDisplayed()
        val d = text(ProfileTags.ACTIVE_CARD)
        assertTrue(d, d.startsWith("Active profile: Home"))
        assertTrue(d, "Calm centre: All apps" in d && "No settings ride along" in d && "2 pages" in d)
    }

    @Test fun CFG_45_eachProfileHasSwitchRenameDuplicateSettingsAndDeleteInItsMenu() {
        val m = manager(); show(m)
        val calm = m.current.workspaces.first { it.name == "Calm" }.id
        rule.onNodeWithTag(ProfileTags.more(calm)).performScrollTo().performClick(); settle()
        listOf("Switch", "Rename", "Duplicate", "Settings that ride along", "Delete").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithText("Duplicate").performClick(); settle()
        val copy = m.current.workspaces.first { it.name == "Calm copy" }
        assertEquals(setOf("look"), copy.carries)
        assertEquals("\"Light\"", copy.settings["themeMode"])
    }

    @Test fun CFG_48_switchingFromThePageAppliesTheProfileAndOffersSwitchBack() {
        val m = manager(); show(m)
        val calm = m.current.workspaces.first { it.name == "Calm" }.id
        rule.onNodeWithTag(ProfileTags.row(calm)).performScrollTo().performClick(); settle()
        assertEquals(calm, m.current.activeId)
        assertEquals("\"Light\"", port.values["themeMode"])
        assertEquals(listOf("Calm"), hooks.switched)
        rule.onNodeWithTag(ProfileTags.UNDO).assertIsDisplayed()
        rule.onNodeWithText("Switch back to Home").assertIsDisplayed()
        rule.onNodeWithTag(ProfileTags.UNDO).performClick(); settle()
        assertEquals(DockWorkspaceState.DEFAULT_ID, m.current.activeId)
        assertEquals("\"Dark\"", port.values["themeMode"])
        rule.onAllNodesWithTag(ProfileTags.UNDO).assertCountEquals(0)
    }

    @Test fun CFG_46_theSettingsThatRideAlongAreACheckPage() {
        val m = manager(); show(m)
        rule.onNodeWithTag(ProfileTags.more(DockWorkspaceState.DEFAULT_ID)).performScrollTo().performClick(); settle()
        rule.onNodeWithText("Settings that ride along").performClick(); settle()
        rule.onNodeWithTag(OneUiDialogTags.option(ProfileGroup.Radar.ordinal)).performClick()
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).performClick(); settle()
        assertEquals(setOf("radar"), m.current.active.carries)
        assertEquals("false", m.current.active.settings["searchFiles"])
        assertTrue(text(ProfileTags.ACTIVE_CARD).contains("Settings: Radar"))
    }

    // ---- suggestions

    @Test fun CFG_47_suggestedGroupsAreListedWithTheirAppsAndSavedAfterEditing() {
        val m = manager(); show(m)
        rule.onNodeWithTag(ProfileTags.SUGGESTIONS).performScrollTo()
        rule.onNodeWithTag(ProfileTags.suggestion(SuggestionKind.Work)).assertExists()
        rule.onNodeWithText("Gmail, Calendar, Teams, Slack and 1 more").assertExists()
        rule.onNodeWithTag(ProfileTags.suggestion(SuggestionKind.Work)).performClick(); settle()
        rule.onNodeWithTag(ProfileTags.SUGGESTION_PAGE).assertIsDisplayed()
        // four are ticked; a fifth cannot be added until one is taken off
        rule.onNodeWithTag(ProfileTags.suggestionApp(apps[3].key)).performScrollTo().performClick(); settle()
        rule.onNodeWithTag(ProfileTags.suggestionApp(apps[1].key)).performScrollTo().performClick(); settle() // Slack off
        rule.onNodeWithTag(ProfileTags.suggestionApp(apps[3].key)).performClick(); settle() // Zoom on
        rule.onNodeWithTag(ProfileTags.SUGGESTION_SAVE).performScrollTo().performClick(); settle()
        val g = m.current.groups.single()
        assertEquals("Work apps", g.name)
        assertEquals(listOf("com.google.android.gm", "com.google.android.calendar", "com.microsoft.teams", "us.zoom.videomeetings"), g.members.map { it.target.packageName })
        assertEquals(listOf(g.id), m.current.active.groupLinks)
        assertTrue("pinned to the active profile's dock", DockItemRef.group(g.id) in m.current.active.dockPins)
        rule.onAllNodesWithTag(ProfileTags.suggestion(SuggestionKind.Work)).assertCountEquals(0) // saved: no longer suggested
    }

    @Test fun CFG_47_aSuggestionNeedsTwoAppsAndCanBeSavedWithoutPinning() {
        val m = manager(); show(m)
        rule.onNodeWithTag(ProfileTags.suggestion(SuggestionKind.Media)).performScrollTo().performClick(); settle()
        rule.onNodeWithTag(ProfileTags.suggestionApp(apps[8].key)).performClick(); settle()
        rule.onNodeWithTag(ProfileTags.SUGGESTION_SAVE).performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag(ProfileTags.suggestionApp(apps[8].key)).performScrollTo().performClick(); settle()
        rule.onNodeWithTag(ProfileTags.SUGGESTION_PIN).performScrollTo().performClick(); settle()
        rule.onNodeWithTag(ProfileTags.SUGGESTION_SAVE).performScrollTo().assertIsEnabled().performClick(); settle()
        val g = m.current.groups.single()
        assertEquals("Media", g.name)
        assertTrue(DockItemRef.group(g.id) !in m.current.active.dockPins)
    }

    // ---- New profile

    @Test fun CFG_45_newProfileIsAPageAndTheWorkStarterFillsPage1NotificationsRadarAndAGroup() {
        val m = manager(second = false); show(m)
        rule.onNodeWithTag(ProfileTags.NEW).performScrollTo().performClick(); settle()
        rule.onNodeWithTag(ProfileTags.NEW_PAGE).assertIsDisplayed()
        onNew(ProfileTags.CREATE).assertIsNotEnabled() // a name is needed
        onNew(ProfileTags.start(ProfileStart.Work)).performClick(); settle()
        assertEquals("Work", onNew(ProfileTags.NAME).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text)
        assertEquals(true, onNew(ProfileTags.carry(ProfileGroup.Radar)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState)?.let { it == androidx.compose.ui.state.ToggleableState.On })
        onNew(ProfileTags.CREATE).performClick(); settle()
        val w = m.current.workspaces.first { it.name == "Work" }
        assertEquals(setOf("radar"), w.carries)
        assertEquals("true", w.settings["searchFiles"])
        assertEquals("true", w.settings["searchWeb"])
        val page1 = HomeLayoutCodec.decode(JSONObject(w.homeArrangement!!).getString("pages")).first().keys
        assertEquals(apps.take(4).map { it.key }.toSet() + apps[4].key, page1.toSet())
        assertEquals(setOf("com.google.android.gm", "com.Slack", "com.microsoft.teams", "us.zoom.videomeetings", "com.google.android.calendar"), w.notificationApps)
        val g = m.current.groups.single { it.name == "Work apps" }
        assertEquals(listOf(g.id), w.groupLinks)
        assertTrue(HomeDockKeys.dockOf(w.homeArrangement)!!.contains(HomeDockKeys.encode(DockItemRef.group(g.id))))
        assertEquals("not switched to on its own", DockWorkspaceState.DEFAULT_ID, m.current.activeId)
    }

    @Test fun CFG_45_theCalmStarterCarriesLookAndHomeAndTheChecksCanBeChanged() {
        val m = manager(second = false); show(m)
        rule.onNodeWithTag(ProfileTags.NEW).performScrollTo().performClick(); settle()
        onNew(ProfileTags.start(ProfileStart.Calm)).performClick(); settle()
        onNew(ProfileTags.carry(ProfileGroup.Sound)).performClick(); settle()
        onNew(ProfileTags.CREATE).performClick(); settle()
        val w = m.current.workspaces.first { it.name == "Calm" }
        assertEquals(setOf("look", "home", "sound"), w.carries)
        assertEquals("\"Off\"", w.settings["homePages"])
        assertEquals("\"EdgeDots\"", w.settings["calmStyle"])
        assertEquals(setOf("com.whatsapp", "org.telegram.messenger", "com.google.android.deskclock", "com.google.android.calendar"), w.notificationApps)
    }

    @Test fun CFG_45_currentSetupCopiesTheHomeAndAUsedNameIsRefused() {
        val m = manager(); show(m)
        rule.onNodeWithTag(ProfileTags.NEW).performScrollTo().performClick(); settle()
        rule.onNodeWithTag(ProfileTags.NAME).performTextInput("calm"); settle()
        rule.onNodeWithText("A profile with this name already exists").assertIsDisplayed()
        onNew(ProfileTags.CREATE).assertIsNotEnabled()
        rule.onNodeWithTag(ProfileTags.NAME).performTextClearance(); rule.onNodeWithTag(ProfileTags.NAME).performTextInput("Evening"); settle()
        onNew(ProfileTags.CREATE).performClick(); settle()
        val w = m.current.workspaces.first { it.name == "Evening" }
        assertEquals(ProfilesFixtures.HOME, w.homeArrangement)
        assertTrue(w.carries.isEmpty())
    }

    @Test fun CFG_47_aSuggestionCanBeEditedOnTheNewProfilePageBeforeItIsMade() {
        val m = manager(second = false); show(m)
        rule.onNodeWithTag(ProfileTags.NEW).performScrollTo().performClick(); settle()
        rule.onNodeWithTag(ProfileTags.NAME).performTextInput("Fun"); settle()
        onNew(ProfileTags.edit(SuggestionKind.Personal)).performClick(); settle()
        rule.onNodeWithTag(ProfileTags.suggestionApp(apps[7].key)).performClick(); settle() // Instagram off
        rule.onNodeWithTag(ProfileTags.SUGGESTION_SAVE).performScrollTo().performClick(); settle()
        onNew(ProfileTags.CREATE).performClick(); settle()
        val g = m.current.groups.single()
        assertEquals("Personal apps", g.name)
        assertEquals(listOf("com.whatsapp", "org.telegram.messenger"), g.members.map { it.target.packageName })
        assertEquals(listOf(g.id), m.current.workspaces.first { it.name == "Fun" }.groupLinks)
    }

    // ---- chips

    @Test fun CFG_48_theChipRowSwitchesAndIsHiddenWithOneProfile() {
        val m = manager()
        rule.setContent { val s by m.state.collectAsState(); ProfileChipRow(s, { m.switchTo(it) }, dark = true) }
        settle()
        val calm = m.current.workspaces.first { it.name == "Calm" }.id
        assertEquals(true, rule.onNodeWithTag(ProfileTags.chip(DockWorkspaceState.DEFAULT_ID)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        rule.onNodeWithTag(ProfileTags.chip(calm)).performClick(); settle()
        assertEquals(calm, m.current.activeId)
        m.delete(DockWorkspaceState.DEFAULT_ID); settle()
        rule.onAllNodesWithTag(ProfileTags.CHIPS).assertCountEquals(0)
    }

    // ---- screenshots (read and judged; see docs/status/w57-profiles.md)

    @Test fun CFG_45_screenshotProfilesDark() { val m = manager(); show(m); shoot("w57-profiles-dark") }
    @Test fun CFG_45_screenshotProfilesLight() { val m = manager(); show(m, light = true); shoot("w57-profiles-light") }
    @Test fun CFG_45_screenshotProfilesFontScale2() { val m = manager(); show(m, font = 2f); shoot("w57-profiles-fontscale2") }
    @Test fun CFG_45_screenshotNewProfilePage() {
        val m = manager()
        rule.setContent { DockPagesScope { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(com.bos.sphere.core.design.HyleColors.Background)) { NewProfileBody(m, apps, facts) {} } } }
        settle()
        onNew(ProfileTags.start(ProfileStart.Work)).performClick(); settle()
        shoot("w57-new-profile")
        onNew(ProfileTags.CREATE)
        shoot("w57-new-profile-bottom")
    }
    @Test fun CFG_47_screenshotSuggestionPage() {
        val s = com.bos.sphere.core.data.AppGroupSuggester.suggest(facts).first()
        rule.setContent { DockPagesScope { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(com.bos.sphere.core.design.HyleColors.Background)) { SuggestionBody(s, "Home", s.preselected.map { it.key }, { _, _ -> }, {}) } } }
        settle()
        shoot("w57-suggestion")
    }
    @Test fun CFG_45_screenshotProfilesAfterSwitch() {
        val m = manager(); show(m)
        rule.onNodeWithTag(ProfileTags.row(m.current.workspaces.first { it.name == "Calm" }.id)).performScrollTo().performClick(); settle()
        rule.onNodeWithTag(ProfileTags.ACTIVE_CARD).performScrollTo()
        shoot("w57-profiles-switched")
    }
}

internal object ProfilesFixtures {
    val HOME = JSONObject().put("pages", HomeLayoutCodec.encode(listOf(com.bos.sphere.core.data.HomePage(listOf("a", "b", "c"), id = 1L), com.bos.sphere.core.data.HomePage(id = 2L)))).put("removed", org.json.JSONArray()).put("dock", org.json.JSONArray(listOf("pin:app:a", "pin:app:b"))).toString()
    val CALM = JSONObject().put("pages", HomeLayoutCodec.encode(listOf(com.bos.sphere.core.data.HomePage(id = 1L)))).put("removed", org.json.JSONArray()).put("dock", org.json.JSONArray()).toString()
}
