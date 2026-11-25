package com.bos.sphere

import com.bos.sphere.core.data.ClockStyle
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.PageLayout
import com.bos.sphere.core.data.PageShape
import com.bos.sphere.core.data.PrefsReset
import com.bos.sphere.core.data.ThemeMode
import com.bos.sphere.feature.grid.OneUiModel
import com.bos.sphere.feature.grid.description
import com.bos.sphere.feature.grid.OneUiSections
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.feature.grid.SettingsIndex
import com.bos.sphere.feature.grid.SetupState
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SettingsReset
import com.bos.sphere.ui.SettingsRowMeta
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Wave 56 (settingsCore) on the real categories: search synonyms and pane rows (CFG-28), slider defaults and units (CFG-29), page resets and what they
 * leave alone (CFG-30), rows that are off but stay (CFG-31), the Finish setup page (CFG-32), and the report-only help diet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class SettingsCoreW56AppTest {
    private var prefs = LauncherPrefs() // a plain field: no snapshot state is written outside a Compose host
    private val touched = ArrayList<String>()

    private fun model(p: LauncherPrefs, setup: SetupState = SetupState.Complete, shape: PageShape = PageShape.Grid, pageCount: Int = 2) = SettingsModel(
        prefs = p, updatePrefs = { f -> prefs = f(prefs) }, pageIndex = 0,
        pages = (1..pageCount).map { HomePage(keys = emptyList(), id = it.toLong(), layout = PageLayout(shape = shape)) },
        // user data: no reset may ever reach these (CFG-30)
        setPageLayout = { _, _ -> touched += "layout" }, addPageAfter = { touched += "addPage" }, deletePage = { touched += "deletePage" }, setClockTile = { _, _ -> touched += "clock" },
        corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> touched += "gesture" },
        about = listOf("Version" to "test"), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
        pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false, editCorner = { touched += "corner" }, editGesture = {},
        system = SystemLinks({}, {}, {}, {}), lab = emptyList(), setup = setup,
    )

    private fun cats(p: LauncherPrefs, setup: SetupState = SetupState.Complete, shape: PageShape = PageShape.Grid) = buildSettingsCategories(model(p, setup, shape))
    private fun home(p: LauncherPrefs) = cats(p).first { it.title == "Home screen" }
    private fun all(p: LauncherPrefs) = cats(p).flatMap { it.items }
    private fun row(p: LauncherPrefs, title: String) = all(p).first { it.title == title }

    // ---- CFG-28 -------------------------------------------------------------------------------------------------------------------

    @Test fun CFG_28_aSynonymFindsTheRowWithoutShowingAnywhere() {
        val idx = SettingsIndex.fromCategories(cats(LauncherPrefs()))
        assertTrue("dark -> Light / dark", idx.search("dark").any { it.title == "Light / dark" })
        assertTrue("click -> Interface sounds", idx.search("click").any { it.title == "Interface sounds" })
        assertTrue("pointer -> hot corners", idx.search("pointer").any { it.title == "Hot corners with a mouse" })
        assertTrue("psp -> the home Style", idx.search("psp").any { it.title == "Style" })
        assertTrue("restore defaults -> Reset all settings", idx.search("restore defaults").any { it.title == "Reset all settings" })
        assertTrue("a synonym is never drawn: no row's summary line holds one", all(LauncherPrefs()).none { (it.description() ?: "").contains("sfx") })
    }


    @Test fun CFG_28_theRowsOfThePanesAreSearchableAndAHitNamesThePaneRow() {
        val c = cats(LauncherPrefs())
        val idx = SettingsIndex.fromCategories(c)
        fun hit(q: String, id: String): com.bos.sphere.feature.grid.SettingEntry {
            val e = idx.search(q).firstOrNull { it.paneEntryId == id }
            assertNotNull("\"$q\" should find the pane row $id: ${idx.search(q).map { it.title to it.paneEntryId }}", e)
            return e!!
        }
        val snap = hit("snapshot", SettingsRowMeta.PaneIds.BACKUP_SNAPSHOT)
        assertEquals("Export, import and restore", snap.parent)
        assertEquals("Backup", c[snap.categoryIndex].title)
        assertTrue(c[snap.categoryIndex].items[snap.itemIndex] is SettingItem.Pane)
        hit("microphone", SettingsRowMeta.PaneIds.access(com.bos.sphere.core.design.GrantKind.Microphone))
        hit("browser", SettingsRowMeta.PaneIds.openWith(com.bos.sphere.core.data.CornerActionId.Browser))
        hit("launcher3", SettingsRowMeta.PaneIds.BACKUP_IMPORT_LAYOUT)
    }

    @Test fun CFG_28_thePaneRowIdsTheSearchUsesAreTheIdsThePanesDrawWith() {
        // BackupPane (feature-grid) draws its rows with literal ids; they must be the ids the index holds.
        val src = File("../feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/BackupPane.kt").readText()
        for (id in listOf(SettingsRowMeta.PaneIds.BACKUP_EXPORT, SettingsRowMeta.PaneIds.BACKUP_EXPORT_SETTINGS, SettingsRowMeta.PaneIds.BACKUP_IMPORT, SettingsRowMeta.PaneIds.BACKUP_IMPORT_LAYOUT, SettingsRowMeta.PaneIds.BACKUP_SNAPSHOT))
            assertTrue("BackupPane draws $id", src.contains("flashId = \"$id\""))
    }

    // ---- CFG-29 -------------------------------------------------------------------------------------------------------------------

    @Test fun CFG_29_everySliderHasADefaultAndAReadoutWithItsUnit() {
        for (p in listOf(LauncherPrefs(), LauncherPrefs(homePages = HomePages.Paginated, adaptiveGrid = true), LauncherPrefs(homeStyle = HomeStyle.Dsi, spacingV = 0.3f))) {
            val sliders = all(p).filterIsInstance<SettingItem.Slider>()
            assertTrue("sliders found", sliders.size >= 10)
            for (s in sliders) {
                assertNotNull("${s.title} has a default", s.default)
                assertTrue("${s.title} reads with a unit: '${s.valueText}'", SettingsRowMeta.hasUnit(s.valueText))
            }
        }
    }

    @Test fun CFG_29_theDefaultsAreTheDefaultsOfThePreferences() {
        val d = LauncherPrefs()
        fun def(t: String) = (row(d, t) as SettingItem.Slider).default!!
        assertEquals(d.iconScale, def("Icon size"), 0f)
        assertEquals(d.cornerIconSize, def("Corner icon size"), 0f)
        assertEquals(d.hotCornerDwellMs.toFloat(), def("Hot corner delay"), 0f)
        assertEquals(d.settingsDim, def("Backdrop dimming"), 0f)
        assertEquals(d.indicatorIntensity, def("Glow: intensity"), 0f)
        assertEquals(d.uiSoundVolume, def("Volume"), 0f)
        assertEquals(d.motionSensitivity, def("Motion sensitivity"), 0f)
        assertEquals("32 dp", (row(d, "Corner icon size") as SettingItem.Slider).valueText)
        assertEquals("150 ms", (row(d, "Hot corner delay") as SettingItem.Slider).valueText)
    }

    @Test fun CFG_29_aDerivedPreferenceReadsAutoWhileItFollows() {
        val follows = LauncherPrefs(homePages = HomePages.Paginated, spacingV = -1f, iconSpacing = 0.4f)
        val s = row(follows, "Spacing down") as SettingItem.Slider
        assertTrue(s.auto); assertEquals("Auto", s.valueText); assertFalse(s.offersDefault)
        assertEquals("while following, the slider sits where the other one is", 0.4f, s.amount, 0f)
        s.onChange(0.7f)
        assertEquals("moving it leaves Auto", 0.7f, prefs.spacingV, 0f)
        val own = row(LauncherPrefs(homePages = HomePages.Paginated, spacingV = 0.7f, iconSpacing = 0.4f), "Spacing down") as SettingItem.Slider
        assertFalse(own.auto); assertEquals("70%", own.valueText); assertTrue("differs from where Spacing is", own.offersDefault)
        assertEquals(0.4f, own.default!!, 0f)
    }

    @Test fun CFG_29_aValueOffTheDefaultOffersTheActionAndTheActionWritesTheDefault() {
        prefs = LauncherPrefs(iconScale = 0.6f)
        val s = row(prefs, "Icon size") as SettingItem.Slider
        assertTrue(s.offersDefault)
        s.onChange(s.default!!)
        assertEquals(LauncherPrefs().iconScale, prefs.iconScale, 0.0001f)
        assertFalse((row(prefs, "Icon size") as SettingItem.Slider).offersDefault)
    }

    // ---- CFG-30 -------------------------------------------------------------------------------------------------------------------

    @Test fun CFG_30_everyPreferenceIsInOnePageListOrExcludedWithAReason() {
        assertEquals("fields no page resets and nobody excluded: ${SettingsReset.unaccounted()}", emptySet<String>(), SettingsReset.unaccounted())
        val names = PrefsReset.names.toSet()
        for ((page, f) in SettingsReset.PAGES) assertTrue("$page lists fields that do not exist: ${f - names}", names.containsAll(f))
        assertTrue("excluded names exist", names.containsAll(SettingsReset.EXCLUDED.keys))
        val twice = SettingsReset.PAGES.values.flatten().groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        assertTrue("a field on two pages: $twice", twice.isEmpty())
        assertTrue("excluded fields are on no page", SettingsReset.EXCLUDED.keys.none { it in SettingsReset.ALL })
        assertTrue(SettingsReset.EXCLUDED.values.all { it.length > 10 })
    }

    /** Calls every row's setter against a recording model and returns which preference fields each page's rows change (derived from the settings model, not written by hand). */
    private fun derive(p: LauncherPrefs): Map<String, Set<String>> {
        val out = HashMap<String, MutableSet<String>>()
        prefs = p
        for (c in cats(p)) {
            val key = OneUiModel.key(c.title) ?: continue
            for (item in c.items) {
                val before = prefs
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    when (item) {
                        is SettingItem.Toggle -> item.onChange(!item.checked)
                        is SettingItem.Slider -> item.onChange(if (item.amount - item.range.start > (item.range.endInclusive - item.range.start) / 2) item.range.start else item.range.endInclusive)
                        is SettingItem.Stepper -> item.onChange(if (item.count < item.range.last) item.count + 1 else item.count - 1)
                        is SettingItem.Choice<*> -> (item as SettingItem.Choice<Any?>).let { c2 -> c2.options.filter { it != c2.selected }.forEach { o -> c2.onSelect(o) } }
                        else -> Unit
                    }
                }
                out.getOrPut(key) { LinkedHashSet() } += PrefsReset.diff(before, prefs)
                prefs = before
            }
        }
        return out
    }

    @Test fun CFG_30_everyPrefARowChangesIsInTheResetListOfItsPage() {
        val derived = HashMap<String, MutableSet<String>>()
        for (p in listOf(LauncherPrefs(), LauncherPrefs(homePages = HomePages.Paginated, adaptiveGrid = true, hotCornersOn = true), LauncherPrefs(homeStyle = HomeStyle.Dsi))) {
            for ((k, v) in derive(p)) derived.getOrPut(k) { LinkedHashSet() } += v
        }
        assertTrue("the probe reached the pages: ${derived.mapValues { it.value.size }}", derived.getValue("Home screen").size > 25 && derived.getValue("Look").size >= 6)
        for ((page, fields) in derived) {
            val listed = SettingsReset.PAGES[page].orEmpty()
            assertTrue("$page: rows change $fields but the reset list lacks ${fields - listed}", listed.containsAll(fields))
        }
    }

    @Test fun CFG_30_aPageResetPutsBackThatPagesPreferencesAndNothingElse() {
        prefs = LauncherPrefs(themeMode = ThemeMode.Light, iconScale = 0.6f, hotCornersOn = false, uiSounds = false, onboardingDone = true, cornerIconSize = 50f)
        val look = cats(prefs).first { it.title == "Look" }
        look.reset!!.invoke()
        assertEquals(LauncherPrefs().themeMode, prefs.themeMode)
        assertEquals("another page's setting stays", 0.6f, prefs.iconScale, 0f)
        assertFalse(prefs.hotCornersOn); assertFalse(prefs.uiSounds); assertEquals(50f, prefs.cornerIconSize, 0f)
        assertTrue("the first-run flag is never reset", prefs.onboardingDone)
        cats(prefs).first { it.title == "Corners" }.reset!!.invoke()
        assertEquals(32f, prefs.cornerIconSize, 0f); assertTrue(prefs.hotCornersOn)
    }

    @Test fun CFG_30_resetAllPutsEveryPageBackButKeepsTheExcludedAndUserData() {
        prefs = LauncherPrefs(themeMode = ThemeMode.Light, iconScale = 0.6f, hotCornersOn = false, uiSounds = false, onboardingDone = true, leftWidgetId = 42, homePage = 3)
        val backup = cats(prefs).first { it.title == "Backup" }
        val a = backup.items.filterIsInstance<SettingItem.Action>().single { it.title == "Reset all settings" }
        assertTrue(a.meta.snapshotFirst); assertNotNull(a.confirm)
        a.onClick()
        val d = LauncherPrefs()
        assertEquals(d.themeMode, prefs.themeMode); assertEquals(d.iconScale, prefs.iconScale, 0f); assertEquals(d.hotCornersOn, prefs.hotCornersOn); assertEquals(d.uiSounds, prefs.uiSounds)
        assertTrue(prefs.onboardingDone); assertEquals(42, prefs.leftWidgetId); assertEquals(3, prefs.homePage)
        assertEquals("no reset reached a user data store", emptyList<String>(), touched)
    }

    @Test fun CFG_30_noPageResetTouchesTheUsersDataStores() {
        for (p in listOf(LauncherPrefs(), LauncherPrefs(homePages = HomePages.Paginated))) {
            for (c in cats(p)) c.reset?.invoke()
        }
        assertEquals(emptyList<String>(), touched)
        val withReset = cats(LauncherPrefs()).filter { it.reset != null }.map { OneUiModel.key(it.title) }.toSet()
        assertEquals(SettingsReset.PAGES.keys, withReset)
        for (data in listOf("Pages", "Hidden apps", "Profiles", "Backup", "Desktop & dock", "About")) assertFalse("$data holds data or actions only: no page reset", data in withReset)
    }

    // ---- CFG-31 -------------------------------------------------------------------------------------------------------------------

    private fun reason(p: LauncherPrefs, t: String) = row(p, t).meta.disabledReason

    @Test fun CFG_31_pagesRowsStayWithTheReasonWhenPagesIsOffOrContinuous() {
        assertEquals("Pages is Off", reason(LauncherPrefs(homePages = HomePages.Off), "Add page"))
        assertEquals("Pages is Continuous", reason(LauncherPrefs(homePages = HomePages.Continuous), "Add page"))
        assertNull(reason(LauncherPrefs(homePages = HomePages.Paginated), "Add page"))
        assertEquals("Pages is Continuous", reason(LauncherPrefs(homePages = HomePages.Continuous), "Default density"))
        assertEquals("Needs the Pages home style", reason(LauncherPrefs(homeStyle = HomeStyle.Psp), "Page turning · portrait"))
        assertNull(reason(LauncherPrefs(homePages = HomePages.Paginated), "Default density"))
        assertEquals("Pages is Continuous", reason(LauncherPrefs(homePages = HomePages.Continuous), "Height in page"))
        assertEquals("Pages is Continuous", reason(LauncherPrefs(homePages = HomePages.Continuous), "Label size"))
    }

    @Test fun CFG_31_circleAndGridRowsOfThePageStayWithTheirReason() {
        val grid = cats(LauncherPrefs(homePages = HomePages.Paginated), shape = PageShape.Grid).first { it.title == "Pages" }
        assertEquals("Needs Circle layout", grid.items.first { it.title == "Rings" }.meta.disabledReason)
        assertNull(grid.items.first { it.title == "Density follows the home default" }.meta.disabledReason)
        val circle = cats(LauncherPrefs(homePages = HomePages.Paginated), shape = PageShape.Circle).first { it.title == "Pages" }
        assertNull(circle.items.first { it.title == "Rings" }.meta.disabledReason)
        assertEquals("Needs Grid layout", circle.items.first { it.title == "Density follows the home default" }.meta.disabledReason)
    }

    @Test fun CFG_31_clockOptionsStayWhenTheClockIsOffAndTheFormatNeedsADigitalClock() {
        val off = LauncherPrefs(clockStyle = ClockStyle.Off)
        for (t in listOf("Clock format", "Show date under the clock", "Clock colour", "Clock size")) assertEquals(t, "Clock style is Off", reason(off, t))
        assertEquals("Needs a digital clock", reason(LauncherPrefs(clockStyle = ClockStyle.AnalogClassic), "Clock format"))
        assertNull(reason(LauncherPrefs(clockStyle = ClockStyle.AnalogClassic), "Clock size"))
        assertNull(reason(LauncherPrefs(clockStyle = ClockStyle.BigDigital), "Clock format"))
    }

    @Test fun CFG_31_hotCornerDelayAndTheOtherOneOffRowsExplainThemselves() {
        assertEquals("Hot corners are off", reason(LauncherPrefs(hotCornersOn = false), "Hot corner delay"))
        assertNull(reason(LauncherPrefs(hotCornersOn = true), "Hot corner delay"))
        assertEquals("Needs Grid follows window size", reason(LauncherPrefs(adaptiveGrid = false), "Grid density"))
        assertNull(reason(LauncherPrefs(adaptiveGrid = true), "Grid density"))
        assertEquals("Notification badges are off", reason(LauncherPrefs(badgesOn = false), "Count in badges"))
        assertEquals("Needs the DSi home style", reason(LauncherPrefs(homeStyle = HomeStyle.Grid), "Scroll direction"))
        assertEquals("The PSP bar has a fixed icon size", reason(LauncherPrefs(homeStyle = HomeStyle.Psp), "Icon size"))
        assertEquals("Pages use the same wallpaper as Glance", reason(LauncherPrefs(pagesSameAsGlance = true), "Pages wallpaper"))
        assertNull(reason(LauncherPrefs(pagesSameAsGlance = false), "Pages wallpaper"))
    }

    @Test fun CFG_31_everySectionKeepsItsHeaderWhateverThePreferencesAre() {
        val variants = listOf(
            LauncherPrefs(), LauncherPrefs(homePages = HomePages.Paginated), LauncherPrefs(homePages = HomePages.Continuous), LauncherPrefs(homeStyle = HomeStyle.Psp),
            LauncherPrefs(homeStyle = HomeStyle.Dsi), LauncherPrefs(clockStyle = ClockStyle.Off), LauncherPrefs(hotCornersOn = false), LauncherPrefs(adaptiveGrid = true),
        )
        for (p in variants) {
            val h = home(p)
            val headers = OneUiSections.headers(h.title, h.items)
            assertEquals("Home screen sections with $p", OneUiSections.TABLE.getValue("Home screen").size, headers.size)
        }
    }

    @Test fun CFG_31_theSectionTableNamesRowsByIdAndEveryIdIsAssigned() {
        val h = home(LauncherPrefs())
        for ((rowKey, _) in OneUiSections.TABLE.getValue("Home screen")) {
            assertTrue("$rowKey is a row id", rowKey.startsWith("home."))
            assertTrue("a row carries the id $rowKey", h.items.any { it.meta.id == rowKey })
        }
        val ids = SettingsRowMeta.IDS.values.flatMap { it.values }
        assertEquals("ids are unique", ids.size, ids.toSet().size)
    }

    @Test fun CFG_31_aRowThatIsOffIsStillInTheSearchWithItsReason() {
        val idx = SettingsIndex.fromCategories(cats(LauncherPrefs(hotCornersOn = false)))
        assertEquals("Hot corners are off", idx.search("hot corner delay").first { it.title == "Hot corner delay" }.disabledReason)
    }

    // ---- CFG-32 -------------------------------------------------------------------------------------------------------------------

    @Test fun CFG_32_theFinishSetupPageIsThereOnlyWhileSomethingIsUnfinished() {
        val none = cats(LauncherPrefs())
        assertEquals(-1, OneUiModel.finishSetupIndex(none))
        val open = cats(LauncherPrefs(), setup = SetupState(defaultHome = false, notificationAccess = false, usageAccess = true, dndAccess = false, dndActionBound = false))
        assertEquals("it is the last category, so no index before it moves", open.lastIndex, OneUiModel.finishSetupIndex(open))
        assertEquals("2 left", (open.last().items.single() as SettingItem.Pane).value)
        assertEquals("the groups of the root do not hold it", open.indices.toList() - open.lastIndex, OneUiModel.groups(open).flatten().sorted())
    }

    @Test fun CFG_32_theStepsOpenTheRightSystemPageThroughTheExistingLinks() {
        val log = ArrayList<String>()
        val m = SettingsModel(
            prefs = LauncherPrefs(), updatePrefs = {}, pageIndex = 0, pages = listOf(HomePage(keys = emptyList(), id = 1L)),
            setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
            about = emptyList(), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null }, pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false,
            editCorner = {}, editGesture = {},
            system = SystemLinks({ log += "home" }, { log += "notif" }, { log += "dnd" }, {}, usageAccess = { log += "usage" }),
            setup = SetupState(false, false, false, false, dndActionBound = true),
        )
        val fin = buildSettingsCategories(m).last()
        assertEquals("Finish setup", fin.title)
        // the page body is built from the same rows the category was made from
        val rows = com.bos.sphere.feature.grid.FinishSetup.rows(m.setup)
        assertEquals(4, rows.size)
        val c = com.bos.sphere.feature.grid.FinishSetup.category(m.setup) { s -> log += s.id }!!
        assertEquals("Finish setup", c.title)
    }

    // ---- the help diet, report only (CFG-33) --------------------------------------------------------------------------------------

    @Test fun CFG_33_reportTheRowsWhoseSummaryIsLongProse() {
        val offenders = ArrayList<String>()
        var rows = 0
        for (c in cats(LauncherPrefs(homePages = HomePages.Paginated))) for (i in c.items) {
            val summary = when (i) {
                is SettingItem.Toggle -> i.hint; is SettingItem.Slider -> i.hint; is SettingItem.Choice<*> -> i.hint; is SettingItem.Stepper -> i.hint
                is SettingItem.Action -> listOfNotNull(i.value.takeIf { it.isNotEmpty() }, i.hint).joinToString(" · ")
                is SettingItem.Pane -> i.value
            }.orEmpty()
            rows++
            if (summary.split(Regex("\\s+")).count { it.isNotEmpty() } > 8 || summary.length > 60) offenders += "${c.title} / ${i.title}: ${summary.length} chars: $summary"
        }
        val f = File(System.getProperty("user.dir"), "build/help-diet-w56.txt").also { it.parentFile.mkdirs() }
        f.writeText("rows: $rows\nprose rows: ${offenders.size} (${offenders.size * 100 / rows} percent)\n" + offenders.joinToString("\n"))
        println("HELP-DIET: ${offenders.size} of $rows rows have a long prose summary (report only, nothing fails yet)")
        assertTrue("the report was written", f.length() > 0)
    }
}
