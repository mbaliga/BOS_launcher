package com.bos.sphere

import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.PageLayout
import com.bos.sphere.core.data.PageShape
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.feature.grid.SetupState
import com.bos.sphere.ui.SettingsHelpDiet
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Wave 58 (settingsPolish) on the real categories: the help diet as a failing guard (CFG-65). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class SettingsPolishW58AppTest {
    private var prefs = LauncherPrefs()

    private fun model(p: LauncherPrefs, shape: PageShape = PageShape.Grid) = SettingsModel(
        prefs = p, updatePrefs = { f -> prefs = f(prefs) }, pageIndex = 0,
        pages = (1..2).map { HomePage(keys = emptyList(), id = it.toLong(), layout = PageLayout(shape = shape)) },
        setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> },
        corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
        about = listOf("Version" to "test"), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
        pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false, editCorner = {}, editGesture = {},
        system = SystemLinks({}, {}, {}, {}), lab = emptyList(), setup = SetupState.Complete, restorePages = {},
    )

    private fun cats(p: LauncherPrefs, shape: PageShape = PageShape.Grid): List<SettingCategory> = buildSettingsCategories(model(p, shape))

    /** The configurations the guard reads: every conditional row and every hint variant shows in at least one. */
    private val configs = listOf(
        LauncherPrefs(),
        LauncherPrefs(homePages = HomePages.Paginated),
        LauncherPrefs(homePages = HomePages.Paginated, adaptiveGrid = true, hotCornersOn = true),
        LauncherPrefs(homeStyle = HomeStyle.Dsi),
        LauncherPrefs(homeStyle = HomeStyle.Psp, restoreOpenSurfaces = true),
        LauncherPrefs(homeStyle = HomeStyle.List, leftPage = com.bos.sphere.core.data.LeftPage.Widget),
    )

    @Test fun CFG_65_everyRowsGreyLineIsAStateOrAFewWords() {
        val offenders = LinkedHashSet<String>()
        val seen = LinkedHashSet<String>()
        for (p in configs) for (shape in listOf(PageShape.Grid, PageShape.Circle)) for (c in cats(p, shape)) for (i in c.items) {
            val key = "${c.title} / ${i.title}"
            seen += key
            val s = SettingsHelpDiet.summary(i)
            if (SettingsHelpDiet.words(s) > SettingsHelpDiet.MAX_WORDS && key !in SettingsHelpDiet.ALLOWED.keys.map { "${it.first} / ${it.second}" }) offenders += "$key (${SettingsHelpDiet.words(s)} words): $s"
        }
        File(System.getProperty("user.dir"), "build/help-diet-w58.txt").also { it.parentFile.mkdirs() }.writeText("rows: ${seen.size}\n" + offenders.joinToString("\n"))
        assertTrue("rows whose grey line is prose (more than ${SettingsHelpDiet.MAX_WORDS} words):\n" + offenders.joinToString("\n"), offenders.isEmpty())
        assertTrue("the allow-list stays under 5 percent of the rows (${SettingsHelpDiet.ALLOWED.size} of ${seen.size})", SettingsHelpDiet.ALLOWED.size * 20 <= seen.size)
    }

    private fun all(p: LauncherPrefs) = cats(p).flatMap { it.items }
    private fun row(p: LauncherPrefs, title: String) = all(p).first { it.title == title }
    private fun tab(p: LauncherPrefs, key: String) = cats(p).first { com.bos.sphere.feature.grid.SettingsTabs.key(it.title) == key }

    // ---- CFG-65: what left the grey lines is still reachable ---------------------------------------------------------------------------

    @Test fun CFG_68_thePrivacyLineOfWebSuggestionsNamesTheEngineAndTheWebSectionNamesThePlayStore() {
        val p = LauncherPrefs(webEngine = com.bos.sphere.core.data.WebEngine.DuckDuckGo)
        val line = SettingsHelpDiet.summary(row(p, "Web suggestions"))
        assertTrue("the line says what is sent and where: $line", line.contains("what you type") && line.contains("DuckDuckGo"))
        val radar = tab(p, "Radar")
        val web = radar.headers.entries.first { it.value == "Web" }.key
        assertTrue("the Web section's note names the Play Store", radar.notes[web]!!.contains("Play Store"))
        assertEquals("Gets only what you send", SettingsHelpDiet.summary(row(p, "Web engine")))
    }

    @Test fun CFG_68_theConsequencesThatLeftTheRowsAreSectionNotes() {
        val c = cats(LauncherPrefs())
        val notes = c.flatMap { t -> t.notes.map { (i, n) -> "${t.headers[i]}: $n" } }
        for (want in listOf("Glance: Glance is the calm first screen", "Comms: Pinned items never count as unread", "Sound and haptics: Android's own sound and vibration", "Calm styles: Calm styles never cover", "Theme: A look never replaces"))
            assertTrue("note \"$want\" in $notes", notes.any { it.startsWith(want) })
        for (t in c) for ((i, n) in t.notes) {
            assertTrue("a note sits under a header (${t.title} $i)", i in t.headers)
            assertTrue("a note is one short sentence: $n", SettingsHelpDiet.words(n) <= 12 && n.count { it == '.' } <= 1)
        }
    }

    @Test fun CFG_65_howToReachSettingsFromAHomeWithoutEmptySpaceStaysOnItsStyleRow() {
        assertEquals("Hold the clock band for Settings", SettingsHelpDiet.summary(row(LauncherPrefs(homeStyle = HomeStyle.Psp), "Style")))
        assertEquals("Long-press the margin for Settings", SettingsHelpDiet.summary(row(LauncherPrefs(homeStyle = HomeStyle.List), "Style")))
        assertEquals("", SettingsHelpDiet.summary(row(LauncherPrefs(), "Style"))) // the cards are enough on the Pages home
    }

    @Test fun CFG_65_theGuardCountsWordsNotSeparators() {
        assertEquals(6, SettingsHelpDiet.words("Home default: 4 × 6 · 5 × 4"))
        assertEquals(0, SettingsHelpDiet.words(" · "))
        val longLine = SettingItem.Toggle("T", com.bos.sphere.feature.grid.Glyphs.Apps, true, "one two three four five six seven") {}
        assertEquals(7, SettingsHelpDiet.words(SettingsHelpDiet.summary(longLine)))
        assertEquals("a row that is off shows its reason", "Off here", SettingsHelpDiet.summary(longLine.also { it.meta.disabledReason = "Off here" }))
    }

    // ---- CFG-66: rows that stay, dimmed, with the reason ---------------------------------------------------------------------------------

    @Test fun CFG_66_backdropDimmingStaysDimmedOverPitchBlack() {
        assertNull(row(LauncherPrefs(settingsBackdrop = com.bos.sphere.core.data.SettingsBackdrop.Wallpaper), "Backdrop dimming").meta.disabledReason)
        assertEquals("Backdrop is Pitch black", row(LauncherPrefs(settingsBackdrop = com.bos.sphere.core.data.SettingsBackdrop.Black), "Backdrop dimming").meta.disabledReason)
    }

    @Test fun CFG_66_regularBatteryAndSignalStaysDimmedBesideOffOrRegular() {
        fun reason(s: com.bos.sphere.core.data.CalmStyle) = row(LauncherPrefs(calmStyle = s), "Also show regular battery and signal").meta.disabledReason
        assertEquals("Calm style is Off", reason(com.bos.sphere.core.data.CalmStyle.None))
        assertEquals("Calm style is Regular", reason(com.bos.sphere.core.data.CalmStyle.Regular))
        assertNull(reason(com.bos.sphere.core.data.CalmStyle.EdgeDots))
    }

    @Test fun CFG_66_thePinchDensityRowStaysOnAHomeWithoutADensity() {
        for (style in listOf(HomeStyle.Psp, HomeStyle.List)) {
            val r = row(LauncherPrefs(homeStyle = style), "Pinch changes grid density")
            assertTrue("$style: ${r.meta.disabledReason}", r.meta.disabledReason!!.startsWith("No grid density on"))
        }
        assertNull(row(LauncherPrefs(homePages = HomePages.Paginated), "Pinch changes grid density").meta.disabledReason)
    }

    @Test fun CFG_66_theNewDimmedRowsAreStillFoundWithTheirReason() {
        val idx = com.bos.sphere.feature.grid.SettingsIndex.fromCategories(cats(LauncherPrefs(settingsBackdrop = com.bos.sphere.core.data.SettingsBackdrop.Black, calmStyle = com.bos.sphere.core.data.CalmStyle.None)))
        assertEquals("Backdrop is Pitch black", idx.search("backdrop dimming").first { it.title == "Backdrop dimming" }.disabledReason)
        assertEquals("Calm style is Off", idx.search("regular battery").first { it.title == "Also show regular battery and signal" }.disabledReason)
    }

    // ---- CFG-67: the panes' rows are search entries -------------------------------------------------------------------------------

    @Test fun CFG_67_theRowsOfThemesDrawerShortcutsProfilesAndTheRadarPanesAreFound() {
        val m = model(LauncherPrefs()).let { base ->
            SettingsModel(prefs = base.prefs, updatePrefs = base.updatePrefs, pageIndex = 0, pages = base.pages, setPageLayout = base.setPageLayout, addPageAfter = base.addPageAfter,
                deletePage = base.deletePage, setClockTile = base.setClockTile, corners = emptyMap(), gestures = emptyMap(), setGesture = base.setGesture, about = base.about,
                appsByKey = emptyMap(), isLandscape = false, chosenApp = { null }, pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false,
                editCorner = {}, editGesture = {}, system = base.system, puckSegments = 7, searchProviders = listOf("demo.notes" to "Notes search"))
        }
        val c = buildSettingsCategories(m)
        val idx = com.bos.sphere.feature.grid.SettingsIndex.fromCategories(c)
        fun hit(q: String, id: String, pane: String) {
            val e = idx.search(q).firstOrNull { it.paneEntryId == id }
            assertTrue("\"$q\" finds $id: ${idx.search(q).map { it.title to it.paneEntryId }}", e != null)
            assertEquals(pane, e!!.parent)
            assertTrue("the hit opens the pane row", c[e.categoryIndex].items[e.itemIndex] is SettingItem.Pane)
        }
        val ids = com.bos.sphere.ui.SettingsRowMeta.PaneIds
        hit("liftofftheme", ids.THEMES_IMPORT, "Themes")
        hit("export current look", ids.THEMES_EXPORT, "Themes")
        hit("undo last theme", ids.THEMES_UNDO, "Themes")
        hit("segment 7", ids.drawerSegment(7), "Drawer shortcuts")
        assertTrue("only the segments the ring has", idx.search("segment 8").none { it.paneEntryId == ids.drawerSegment(8) })
        hit("new profile", ids.PROFILES_NEW, "Profiles")
        hit("work apps", ids.PROFILES_SUGGESTIONS, "Profiles")
        hit("calm centre", ids.PROFILES_CALM, "Profiles")
        hit("notes search", ids.provider("demo.notes"), "Search providers from other apps")
        hit("reindex", ids.FOLDERS_REINDEX, "Search folders")
        hit("forget what radar", ids.HISTORY_FORGET, "Search history and aliases")
        assertEquals("ids are unique", idx.entries.size, idx.entries.map { it.id }.toSet().size)
    }

    @Test fun CFG_67_thePanesDrawTheirRowsWithTheIdsTheSearchUses() {
        fun src(p: String) = File(p).readText()
        val themes = src("src/main/kotlin/com/bos/sphere/ui/ThemesUi.kt")
        for (c in listOf("THEMES_IMPORT", "THEMES_EXPORT", "THEMES_RESET", "THEMES_UNDO")) assertTrue("ThemesPane draws $c", themes.contains("flashId = SettingsRowMeta.PaneIds.$c"))
        val puck = src("src/main/kotlin/com/bos/sphere/ui/PuckActionsPane.kt")
        assertTrue(puck.contains("flashId = SettingsRowMeta.PaneIds.drawerSegment(i + 1)") && puck.contains("paneFlash(SettingsRowMeta.PaneIds.DRAWER_RESET)"))
        val profiles = src("../feature-dock/src/main/kotlin/com/bos/sphere/feature/dock/ProfilesPage.kt")
        for (c in listOf("NEW", "LIST", "SUGGESTIONS", "CALM", "RADAR_ACTIONS")) assertTrue("ProfilesContent draws $c", profiles.contains("paneFlash(ProfilePaneIds.$c)"))
        val radar = src("../feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/PaletteSettingsPanes.kt")
        val ids = com.bos.sphere.ui.SettingsRowMeta.PaneIds
        for (id in listOf(ids.FOLDERS_ADD, ids.FOLDERS_REINDEX, ids.HISTORY_FORGET)) assertTrue("the Radar panes draw $id", radar.contains("flashId = \"$id\""))
        assertTrue(radar.contains("flashId = \"providers.\${c.packageName}\"") && ids.provider("x") == "providers.x")
    }

    // ---- CFG-69: the six tabs read the same way ------------------------------------------------------------------------------------

    @Test fun CFG_69_sectionHeadersAreSentenceCaseUniqueAndNeverARowTitleOfTheirSection() {
        for (p in configs) {
            val c = cats(p).filter { com.bos.sphere.feature.grid.SettingsTabs.key(it.title) != null }
            assertEquals("six tabs", 6, c.size)
            for (t in c) {
                val heads = t.headers.toSortedMap()
                assertEquals("${t.title}: headers unique ${heads.values}", heads.size, heads.values.toSet().size)
                assertTrue("${t.title}: no orphan rows", "Other" !in heads.values)
                val starts = heads.keys.toList()
                starts.forEachIndexed { n, start ->
                    val h = heads[start]!!
                    assertTrue("sentence case: $h", h.first().isUpperCase() && h.drop(1).split(' ').all { w -> w.isEmpty() || !w.first().isUpperCase() || w in PROPER })
                    val end = starts.getOrNull(n + 1) ?: t.items.size
                    val rows = t.items.subList(start, end).map { it.title }
                    assertTrue("${t.title} > $h repeats a row title: $rows", rows.none { it.equals(h, ignoreCase = true) })
                }
                assertTrue("${t.title} ends with Reset this page to defaults", t.reset != null)
            }
        }
    }

    @Test fun CFG_69_theSameKindOfChoiceUsesTheSameControlOnEveryTab() {
        for (p in configs) for (i in all(p)) {
            // an on / off setting is a switch everywhere (never a two-card choice of On and Off)
            if (i is SettingItem.Choice<*>) assertTrue("${i.title} is an on/off choice drawn as cards; it should be a switch", onOffLabels(i).toSet() != setOf("On", "Off"))
            // a choice of 2 to 9 is drawn cards on every tab (wave 54's rule)
            if (i is SettingItem.Choice<*> && i.options.size in 2..com.bos.sphere.feature.grid.OneUiModel.VISUAL_MAX) assertTrue("${i.title} has cards", i.preview != null)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun onOffLabels(c: SettingItem.Choice<*>): List<String> = (c as SettingItem.Choice<Any?>).let { ch -> ch.options.map { ch.label(it) } }

    private companion object { val PROPER = setOf("Comms", "Radar", "Glance", "Android", "Liftoff") }
}
