package com.bos.sphere

import com.bos.sphere.core.data.CalmStyle
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.LeftPage
import com.bos.sphere.core.data.PageLayout
import com.bos.sphere.core.data.PageShape
import com.bos.sphere.core.data.SettingsBackdrop
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.OneUiModel
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.feature.grid.SettingsIndex
import com.bos.sphere.feature.grid.SettingsTabs
import com.bos.sphere.feature.grid.SetupState
import com.bos.sphere.ui.CATEGORY_APP_DRAWER
import com.bos.sphere.ui.CATEGORY_HOME
import com.bos.sphere.ui.CATEGORY_INDEX_BASE
import com.bos.sphere.ui.CATEGORY_ROOT
import com.bos.sphere.ui.CATEGORY_THIS_PAGE
import com.bos.sphere.ui.CATEGORY_WALLPAPER
import com.bos.sphere.ui.HiddenAppsAccess
import com.bos.sphere.ui.LabEntry
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SettingsReset
import com.bos.sphere.ui.SettingsTabLayout
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
import com.bos.sphere.ui.buildSettingsTopics
import com.bos.sphere.ui.settingsTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Wave 57 (settingsTabs), CFG-40: the section model over the real rows. Every row the builder makes lands in exactly one of the six tabs (nothing
 * orphaned, nothing twice), every row the placement table names is named once, the tabs come in the owner's order with their counts, every tab
 * has a reset of its own fields, and the deep links land on the right tab and row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsTabsW57ModelTest {
    @Before fun setUp() { L10n.setLanguage("en") }

    private fun model(p: LauncherPrefs, pages: Int = 3, lab: Boolean = true) = SettingsModel(
        prefs = p, updatePrefs = {}, pageIndex = 1,
        pages = (1..pages).map { HomePage(keys = emptyList(), id = it.toLong(), layout = PageLayout(shape = PageShape.Grid)) },
        setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> },
        corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
        about = listOf("Version" to "test", "Build" to "1"), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
        pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = true, editCorner = {}, editGesture = {},
        system = SystemLinks({}, {}, {}, {}),
        capabilities = listOf(com.bos.sphere.core.capabilities.Capability("notif", "Notifications", com.bos.sphere.core.capabilities.Access.Grant, com.bos.sphere.core.capabilities.CapState.On)),
        lab = if (lab) listOf(LabEntry("spike", "Frame spike", "ok", null)) else emptyList(),
        restorePages = {},
        hiddenApps = HiddenAppsAccess(count = 2, hiddenKeys = kotlinx.coroutines.flow.MutableStateFlow(emptySet()), allApps = emptyList(), setHidden = { _, _ -> }),
    )

    /** Every conditional row present: a paged home with pages, a widget left page, hot corners, the wallpaper backdrop, an ambient calm style. */
    private val full = LauncherPrefs(
        homePages = HomePages.Paginated, leftPage = LeftPage.Widget, leftWidgetId = 3, hotCornersOn = true, settingsBackdrop = SettingsBackdrop.Wallpaper,
        calmStyle = CalmStyle.entries.first { it.ambient },
    )

    private fun tabs(p: LauncherPrefs = full) = buildSettingsCategories(model(p))

    @Test fun CFG_40_settingsAreSixTabsInTheOwnersOrder() {
        val c = tabs()
        assertEquals(SettingsTabs.KEYS, c.map { SettingsTabs.key(it.title) })
        val withSetup = buildSettingsCategories(model(full).let { m -> SettingsModel(m.prefs, m.updatePrefs, m.pageIndex, m.pages, m.setPageLayout, m.addPageAfter, m.deletePage, m.setClockTile, m.corners, m.gestures, m.setGesture, m.about, m.appsByKey, m.isLandscape, m.chosenApp, m.pickLeftWidget, m.leftWidgetLabel, m.pickAppFor, m.motionAvailable, m.editCorner, m.editGesture, m.system, setup = SetupState(defaultHome = false, notificationAccess = true, usageAccess = true, dndAccess = true, dndActionBound = false)) })
        assertEquals("the Finish setup page is the one extra category, last, and not a tab", 7, withSetup.size)
        assertEquals(OneUiModel.FINISH_SETUP, OneUiModel.key(withSetup.last().title))
        assertEquals(6, SettingsTabs.tabIndices(withSetup).size)
    }

    @Test fun CFG_40_everyBuiltRowLandsInExactlyOneTab() {
        for (p in listOf(full, LauncherPrefs(), LauncherPrefs(homePages = HomePages.Continuous))) {
            val m = model(p)
            val topics = buildSettingsTopics(m)
            val placement = SettingsTabLayout.place(topics)
            assertTrue("rows no tab takes (give each a place in SettingsTabLayout): ${placement.orphans}", placement.orphans.isEmpty())
            val built = topics.flatMap { it.items }
            val placed = placement.tabs.flatMap { it.items }
            assertEquals("every row is placed once", built.size, placed.size)
            for (row in built) assertEquals("${row.title} is placed exactly once", 1, placed.count { it === row })
            // and the real list Settings shows holds the same rows, in six tabs
            val shown = buildSettingsCategories(m).flatMap { it.items }
            assertEquals(built.size, shown.size)
        }
    }

    @Test fun CFG_40_everyRowOfThePlacementTableIsNamedOnceAndExists() {
        val keys = SettingsTabLayout.keys()
        val dupes = keys.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue("a row named twice in the table: $dupes", dupes.isEmpty())
        // every name of the table is a real row in at least one state of the settings (no dead entries that would hide a lost row)
        val states = listOf(full, full.copy(spacingV = 0.3f), LauncherPrefs(), LauncherPrefs(homePages = HomePages.Continuous))
        val placed = states.flatMap { p -> SettingsTabLayout.place(buildSettingsTopics(model(p))).placedKeys.values.flatten() }.toSet()
        val dead = keys.filter { it !in placed }
        assertTrue("table rows that never exist: $dead", dead.isEmpty())
    }

    @Test fun CFG_40_theRowCountsPerTab() {
        val c = tabs()
        val counts = c.associate { SettingsTabs.key(it.title)!! to it.items.size }
        // The numbers of the fullest state (paged home, three pages, Lab visible); a moved row changes two of them on purpose.
        assertEquals(mapOf("Home" to 87, "Look" to 21, "Apps" to 9, "Radar" to 16, "Side panel" to 20, "System" to 17), counts)
        // every tab has sections
        for (t in c) assertTrue("${t.title} has section headers", t.headers.size >= 2)
    }

    @Test fun CFG_40_theSectionsOfEachTabAreTheOwnersSections() {
        val c = tabs()
        fun heads(k: String) = c.first { SettingsTabs.key(it.title) == k }.headers.values.toList()
        // Wave 58 (CFG-69): no header repeats a row title of its section word for word, and a one-row section joined its neighbour.
        assertEquals(listOf("Glance", "Home pages", "Layout", "Corners", "Gestures and motion", "Keyboard and pointer", "Dock and displays"), heads("Home"))
        assertEquals(listOf("Theme", "Font and icons", "Wallpaper", "Calm styles", "Settings screen and text"), heads("Look"))
        assertEquals(listOf("Profiles and groups", "App drawer", "Hidden and default apps"), heads("Apps"))
        assertEquals(listOf("Layout", "Appearance", "Input", "Sources", "Web", "Privacy"), heads("Radar"))
        assertEquals(listOf("Left panel", "Panel theme and size", "Sections", "Comms", "Rules and gestures", "Right edge", "Clean up"), heads("Side panel"))
        assertEquals(listOf("General", "Sound and haptics", "Permissions", "Backup and restore", "Help and recovery", "Lab", "About"), heads("System"))
        // Looks lead the Look tab; Profiles (they replaced Workspaces) lead Apps
        assertEquals("Looks", c.first { SettingsTabs.key(it.title) == "Look" }.items.first().title)
        assertEquals("Profiles", c.first { SettingsTabs.key(it.title) == "Apps" }.items.first().title)
    }

    @Test fun CFG_40_movedRowsLiveInTheirNewTab() {
        val c = tabs()
        fun tabOf(title: String) = c.filter { t -> t.items.any { it.title == title } }.map { SettingsTabs.key(it.title) }
        assertEquals(listOf("Look"), tabOf("Glance wallpaper"))
        assertEquals(listOf("Look"), tabOf("Pages wallpaper"))
        assertEquals(listOf("Radar"), tabOf("Keyboard opens with Radar and drawer · portrait"))
        assertEquals(listOf("Home"), tabOf("Dock"))
        assertEquals(listOf("Apps"), tabOf("Hidden apps"))
        assertEquals(listOf("System"), tabOf("Access and privacy"))
        assertEquals(listOf("System"), tabOf("Launcher vibration"))
        assertEquals(listOf("System"), tabOf("Open Liftoff Studio"))
        assertEquals("the Comms access page is merged into Access and privacy", emptyList<String?>(), tabOf("Access Liftoff asks for"))
    }

    @Test fun CFG_40_everyTabResetsItsOwnFieldsOnly() {
        assertEquals(SettingsTabs.KEYS.toSet(), SettingsReset.PAGES.keys)
        assertTrue("every preference is reset by a tab or excluded: ${SettingsReset.unaccounted()}", SettingsReset.unaccounted().isEmpty())
        val all = SettingsReset.PAGES.values.flatten()
        assertEquals("no field is reset by two tabs", all.size, all.toSet().size)
        for (t in tabs()) assertNotNull("${t.title} offers its reset", t.reset)
    }

    @Test fun CFG_41_deepLinksLandOnTheirTabAndRow() {
        val c = tabs()
        fun at(which: Int): Pair<String?, String?> = settingsTarget(c, which).let { t -> SettingsTabs.key(c.getOrNull(t.category)?.title ?: "") to c.getOrNull(t.category)?.items?.getOrNull(t.row)?.title }
        assertEquals("Home" to "Calm home screen (Glance)", at(CATEGORY_HOME))
        assertEquals("Home" to "Layout", at(CATEGORY_THIS_PAGE))
        assertEquals("Apps" to "Drawer shortcuts", at(CATEGORY_APP_DRAWER))
        assertEquals("Look" to "Glance wallpaper", at(CATEGORY_WALLPAPER))
        assertEquals(-1, settingsTarget(c, CATEGORY_ROOT).category)
        assertEquals(3, settingsTarget(c, CATEGORY_INDEX_BASE + 3).category)
        // a scrolling home has no page list: "this page" opens the Pages section all the same
        val scroll = tabs(LauncherPrefs(homePages = HomePages.Continuous))
        val t = settingsTarget(scroll, CATEGORY_THIS_PAGE)
        assertEquals("Pages", scroll[t.category].items[t.row].title)
    }

    @Test fun CFG_44_thePageListPicksThePageTheRowsBelowEdit() {
        var picked = -1
        val m = model(full).let { base -> SettingsModel(base.prefs, base.updatePrefs, base.pageIndex, base.pages, base.setPageLayout, base.addPageAfter, base.deletePage, base.setClockTile, base.corners, base.gestures, base.setGesture, base.about, base.appsByKey, base.isLandscape, base.chosenApp, base.pickLeftWidget, base.leftWidgetLabel, base.pickAppFor, base.motionAvailable, base.editCorner, base.editGesture, base.system, selectPage = { picked = it }) }
        val home = buildSettingsCategories(m).first()
        @Suppress("UNCHECKED_CAST") val page = home.items.first { it.title == "Page" } as SettingItem.Choice<Int>
        assertEquals(listOf(0, 1, 2), page.options)
        assertEquals(1, page.selected)
        assertEquals("Page 2", page.value)
        assertTrue("three pages are cards", page.preview != null)
        page.onSelect(2)
        assertEquals(2, picked)
        // the page list sits right above the page's own rows, in the Pages section
        val i = home.items.indexOf(page)
        assertEquals("Layout", home.items[i + 1].title)
    }

    @Test fun CFG_43_launcherVibrationIsTheSharedStore() {
        val sys = tabs().first { SettingsTabs.key(it.title) == "System" }
        @Suppress("UNCHECKED_CAST") val row = sys.items.first { it.title == "Launcher vibration" } as SettingItem.Choice<com.bos.sphere.core.data.HapticStrength>
        assertEquals(com.bos.sphere.core.data.HapticStrength.entries, row.options)
        com.bos.sphere.core.data.LauncherHapticSettings.resetForTest()
        com.bos.sphere.core.data.LauncherHapticSettings.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        row.onSelect(com.bos.sphere.core.data.HapticStrength.Strong)
        assertEquals("the Comms sheet reads the same store", com.bos.sphere.core.data.HapticStrength.Strong, com.bos.sphere.core.data.LauncherHapticSettings.strength.value)
        com.bos.sphere.core.data.LauncherHapticSettings.resetForTest()
    }

    @Test fun CFG_41_searchCrossesEveryTabAndNamesTheSection() {
        val c = tabs()
        val idx = SettingsIndex.fromCategories(c)
        val corner = idx.search("corner style").first()
        assertEquals("Home", SettingsTabs.key(corner.category))
        assertEquals("Corners", corner.section)
        val web = idx.search("web engine").first()
        assertEquals("Radar", SettingsTabs.key(web.category))
        assertTrue("the section word finds its rows", idx.search("calm styles").any { it.title == "Calm style" })
        // the grants are one search away, inside Access and privacy
        assertTrue(idx.search("microphone").any { it.parent == "Access and privacy" })
        assertTrue(idx.search("do not disturb").any { it.parent == "Access and privacy" })
        assertTrue(idx.search("studio").any { it.title == "Open Liftoff Studio" })
    }

    @Test fun CFG_44_groundControlIsSettingsInTheUi() {
        assertEquals("Settings", com.bos.sphere.core.input.HomeSheetAction.GroundControl.label)
        assertEquals("Open Settings", com.bos.sphere.core.input.LauncherCommand.OpenSettings.label)
        assertTrue("the old name is still a hidden search word for the Settings action",
            com.bos.sphere.core.data.ActionHits.match("ground control").any { it.actionId == com.bos.sphere.core.data.CornerActionId.LauncherSettings })
    }
}
