package com.bos.sphere

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.DockStyle
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.IconShape
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.ThemeApplier
import com.bos.sphere.core.data.ThemeField
import com.bos.sphere.core.data.ThemeMode
import com.bos.sphere.core.data.ThemePacks
import com.bos.sphere.core.data.ThemeParse
import com.bos.sphere.core.data.ThemeStore
import com.bos.sphere.core.data.ThemeUndo
import com.bos.sphere.core.data.WallpaperKind
import com.bos.sphere.core.data.WallpaperSurface
import com.bos.sphere.core.data.rawWallpaper
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.ui.DockStylePane
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.ThemeFiles
import com.bos.sphere.ui.ThemeHost
import com.bos.sphere.ui.ThemeImportFlow
import com.bos.sphere.ui.ThemeTags
import com.bos.sphere.ui.ThemesPane
import com.bos.sphere.ui.buildSettingsCategories
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * Wave 39 (dockThemes): Ground Control's Themes rows, the import flow behind a fake picker, the preview and the error states, the undo, the
 * view-a-file route, the Home screen's Dock style row, and the manifest entry. The real document picker is device-only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
class ThemesW39Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private class FakeFiles : ThemeFiles {
        var next: ByteArray? = ByteArray(0); var picks = 0
        var exportName: String? = null; var exportText: String? = null; var exportOk = true
        override fun pickImport(onBytes: (ByteArray?) -> Unit) { picks++; onBytes(next) }
        override fun saveExport(fileName: String, text: String, onDone: (Boolean) -> Unit) { exportName = fileName; exportText = text; onDone(exportOk) }
    }

    private var prefs = LauncherPrefs(themeMode = ThemeMode.Dark, accentColor = 0xFF8E7BFF, iconShape = IconShape.Rounded)
    private var dock = DockStyle.Standard
    private val files = FakeFiles()
    private fun host() = ThemeHost({ prefs }, ThemeApplier({ prefs }, { f -> prefs = f(prefs) }, { dock }, { dock = it }))

    @Before fun setUp() { L10n.setLanguage("en"); ThemeUndo.clear(); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
    @After fun tearDown() { ThemeUndo.clear(); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }

    private val dusk = """{"format":"liftoff-theme","formatVersion":1,"name":"Dusk","author":"A. Person","version":"1.2","settings":{"themeMode":"light","accent":"#FF8800","iconShape":"circle","dockStyle":"3d","wallpaper":{"kind":"solid","color":"#203040"}}}"""

    @Composable private fun Screen(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
        SphereTheme { Column(Modifier.fillMaxSize().background(HyleColors.Background).verticalScroll(rememberScrollState()).padding(16.dp), content = content) }
    }

    private fun pane() = rule.setContent { Screen { ThemesPane(files, host()) } }

    private fun shoot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun importWith(text: String) { files.next = text.toByteArray(); rule.onNodeWithText("Import theme").performClick(); rule.waitForIdle() }

    // --- the rows ---------------------------------------------------------------------------------------------------------------------------

    @Test fun CFG_10_packUi01_theThemesRowsAreImportExportResetAndNoStoreOrLinkAnywhere() {
        pane()
        listOf("Import theme", "Export current look", "Reset to default look").forEach { rule.onNodeWithText(it).assertExists() }
        rule.onAllNodesWithText("Undo last theme change").assertCountEquals(0) // nothing to undo yet
        // owner decision: no store row, no address, no browse link
        listOf("store", "browse", "http", "www.", ".com", "asystemofcells").forEach { w -> rule.onAllNodesWithText(w, substring = true, ignoreCase = true).assertCountEquals(0) }
        assertNull(ThemeStore.ADDRESS)
    }

    @Test fun CFG_10_packUi02_groundControlListsThemesUnderLookAndDockStyleUnderHomeScreen() {
        var p = LauncherPrefs()
        val cats = buildSettingsCategories(
            SettingsModel(
                prefs = p, updatePrefs = { f -> p = f(p) }, pageIndex = 0, pages = listOf(HomePage(keys = emptyList(), id = 1L)),
                setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
                requestHealth = {}, about = listOf("Version" to "test"), healthAvailable = false, appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
                pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false, editCorner = {}, editGesture = {},
                system = SystemLinks({}, {}, {}, {}), lab = emptyList(),
            ),
        )
        val look = cats.first { it.title == "Look" }.items
        assertTrue("Themes is a pane under Look", look.any { it.title == "Themes" && it is SettingItem.Pane })
        val home = cats.first { it.title == "Home screen" }.items
        assertTrue("Dock style is a pane under Home screen", home.any { it.title == "Dock style" && it is SettingItem.Pane })
        assertEquals("one of each", 1, cats.flatMap { it.items }.count { it.title == "Themes" })
    }

    // --- import: preview, apply, cancel, undo -----------------------------------------------------------------------------------------------

    @Test fun CFG_10_packUi10_aValidFileShowsAPreviewWithNameAuthorAndBeforeAfterChipsAndChangesNothingYet() {
        pane(); importWith(dusk)
        rule.onNodeWithTag(ThemeTags.PREVIEW).assertExists()
        rule.onNodeWithText("Dusk").assertExists(); rule.onNodeWithText("By A. Person").assertExists(); rule.onNodeWithText("Version 1.2").assertExists()
        listOf(ThemeField.Mode, ThemeField.Accent, ThemeField.IconShape, ThemeField.DockStyle, ThemeField.Wallpaper).forEach { rule.onNodeWithTag(ThemeTags.change(it)).assertExists() }
        rule.onAllNodesWithText("Icon colour").assertCountEquals(0) // a setting the pack does not carry is not listed
        // before and after, with the colours
        val acc = rule.onNodeWithTag(ThemeTags.change(ThemeField.Accent)).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertEquals("Accent: #8E7BFF, will become #FF8800", acc)
        assertEquals("Dock style: Standard, will become 3D", rule.onNodeWithTag(ThemeTags.change(ThemeField.DockStyle)).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single())
        rule.onNodeWithTag(ThemeTags.APPLY).assertExists(); rule.onNodeWithTag(ThemeTags.CANCEL).assertExists()
        assertEquals("nothing is applied before Apply", ThemeMode.Dark, prefs.themeMode); assertEquals(DockStyle.Standard, dock)
        shoot("w39-theme-preview-dark")
    }

    @Test fun CFG_10_packUi11_applyChangesTheLookAndOffersUndoWhichRestoresIt() {
        pane(); importWith(dusk)
        rule.onNodeWithText("Apply").performClick(); rule.waitForIdle()
        assertEquals(ThemeMode.Light, prefs.themeMode); assertEquals(0xFFFF8800L, prefs.accentColor); assertEquals(IconShape.Circle, prefs.iconShape); assertEquals(DockStyle.ThreeD, dock)
        assertEquals(WallpaperKind.Solid, prefs.rawWallpaper(WallpaperSurface.Glance).kind)
        rule.onNodeWithTag(ThemeTags.PREVIEW).assertDoesNotExist()
        rule.onNodeWithTag(ThemeTags.APPLIED).assertExists()
        rule.onNodeWithText("Undo last theme change").assertExists()
        rule.onNodeWithText("Undo last theme change").performClick(); rule.waitForIdle()
        assertEquals(ThemeMode.Dark, prefs.themeMode); assertEquals(0xFF8E7BFFL, prefs.accentColor); assertEquals(IconShape.Rounded, prefs.iconShape); assertEquals(DockStyle.Standard, dock)
        assertEquals("the signal wallpaper is back", WallpaperKind.Live, prefs.rawWallpaper(WallpaperSurface.Glance).kind)
        rule.onAllNodesWithText("Undo last theme change").assertCountEquals(0)
    }

    @Test fun CFG_10_packUi12_cancelLeavesEverythingAsItWas() {
        pane(); importWith(dusk)
        rule.onNodeWithText("Cancel").performClick(); rule.waitForIdle()
        assertEquals(ThemeMode.Dark, prefs.themeMode); assertEquals(DockStyle.Standard, dock); assertNull(ThemeUndo.snapshot.value)
        rule.onNodeWithText("Import theme").assertExists()
    }

    // --- error states -----------------------------------------------------------------------------------------------------------------------

    private fun assertError(text: String, vararg expected: String) {
        pane(); importWith(text)
        rule.onNodeWithTag(ThemeTags.ERRORS).assertExists()
        rule.onNodeWithText("This file cannot be used as a theme").assertExists()
        expected.forEach { rule.onNodeWithText(it, substring = true).assertExists() }
        rule.onNodeWithText("Nothing was changed.").assertExists()
        rule.onNodeWithTag(ThemeTags.PREVIEW).assertDoesNotExist()
        assertEquals(ThemeMode.Dark, prefs.themeMode); assertEquals(DockStyle.Standard, dock); assertNull(ThemeUndo.snapshot.value)
    }

    @Test fun CFG_10_packUi20_notJsonIsRefusedInline() = assertError("hello", "This file is not valid JSON.")
    @Test fun CFG_10_packUi20_aStateBackupIsNotATheme() = assertError("""{"format":"liftoff-state","schemaVersion":3}""", "This is not a Liftoff theme file.")
    @Test fun CFG_10_packUi20_aBadColourNamesTheField() = assertError("""{"format":"liftoff-theme","formatVersion":1,"name":"x","settings":{"accent":"purple"}}""", "The accent colour is not a colour like #RRGGBB.")
    @Test fun CFG_10_packUi20_aPictureWallpaperIsRefused() = assertError("""{"format":"liftoff-theme","formatVersion":1,"name":"x","settings":{"wallpaper":{"kind":"picture","uri":"content://x"}}}""", "A theme's wallpaper can only be a solid colour or a gradient.")
    @Test fun CFG_10_packUi20_aFileOver64KbIsRefused() = assertError("""{"format":"liftoff-theme","formatVersion":1,"name":"x","pad":"${"y".repeat(70_000)}","settings":{"dockStyle":"3d"}}""", "This file is larger than 64 KB, so it is not a theme.")
    @Test fun CFG_10_packUi20_aNewerFormatIsRefused() = assertError("""{"format":"liftoff-theme","formatVersion":9,"name":"x","settings":{"dockStyle":"3d"}}""", "This theme was made for a newer version of Liftoff.")
    @Test fun CFG_10_packUi20_severalProblemsAreAllListed() = assertError("""{"format":"liftoff-theme","formatVersion":1,"name":"x","settings":{"themeMode":"neon","iconShape":"star"}}""", "The light / dark mode is not a value Liftoff knows.", "The icon shape is not a value Liftoff knows.")

    @Test fun CFG_10_packUi21_anUnreadableFileIsToldNotIgnored() {
        files.next = null
        pane(); rule.onNodeWithText("Import theme").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Liftoff could not read that file.", substring = true).assertExists()
        shoot("w39-theme-error-dark")
    }

    @Test fun CFG_10_packUi22_aGoodFileAfterABadOneClearsTheError() {
        pane(); importWith("nope"); rule.onNodeWithTag(ThemeTags.ERRORS).assertExists()
        importWith(dusk); rule.onNodeWithTag(ThemeTags.ERRORS).assertDoesNotExist(); rule.onNodeWithTag(ThemeTags.PREVIEW).assertExists()
    }

    // --- export and reset ---------------------------------------------------------------------------------------------------------------------

    @Test fun CFG_10_packUi30_exportSavesTheCurrentLookAsAThemeFileThatImportsBack() {
        prefs = prefs.copy(themeMode = ThemeMode.Amoled, accentColor = 0xFF123456, iconShape = IconShape.Squircle); dock = DockStyle.Transparent
        pane(); rule.onNodeWithText("Export current look").performClick(); rule.waitForIdle()
        assertEquals("My-look.liftofftheme", files.exportName)
        val back = (ThemePacks.parse(files.exportText!!) as ThemeParse.Ok).pack
        assertEquals(ThemeMode.Amoled, back.settings.themeMode); assertEquals(0xFF123456L, back.settings.accent); assertEquals(IconShape.Squircle, back.settings.iconShape); assertEquals(DockStyle.Transparent, back.settings.dockStyle)
        rule.onNodeWithText("Theme exported. A wallpaper picture, video or live wallpaper is not included.").assertExists()
        assertEquals("exporting changes nothing", ThemeMode.Amoled, prefs.themeMode)
    }

    @Test fun CFG_10_packUi31_aFailedSaveSaysSo() {
        files.exportOk = false
        pane(); rule.onNodeWithText("Export current look").performClick(); rule.waitForIdle()
        rule.onNodeWithText("The theme could not be saved.").assertExists()
    }

    @Test fun CFG_10_packUi32_resetGoesToTheDefaultLookAndCanBeUndone() {
        prefs = prefs.copy(themeMode = ThemeMode.Light, accentColor = 0xFF111111, iconShape = IconShape.Square); dock = DockStyle.ThreeD
        pane(); rule.onNodeWithText("Reset to default look").performClick(); rule.waitForIdle()
        val d = LauncherPrefs()
        assertEquals(d.themeMode, prefs.themeMode); assertEquals(d.accentColor, prefs.accentColor); assertEquals(d.iconShape, prefs.iconShape); assertEquals(DockStyle.Standard, dock)
        rule.onNodeWithText("Undo last theme change").performClick(); rule.waitForIdle()
        assertEquals(ThemeMode.Light, prefs.themeMode); assertEquals(0xFF111111L, prefs.accentColor); assertEquals(DockStyle.ThreeD, dock)
    }

    // --- opening a file from outside ---------------------------------------------------------------------------------------------------------

    @Test fun CFG_10_packUi40_openingAFileFromOutsideLandsOnTheSamePreviewAndAppliesOnlyOnApply() {
        var closed = 0
        rule.setContent { Screen { ThemeImportFlow({ dusk.toByteArray() }, host(), { closed++ }) } }
        rule.waitForIdle()
        rule.onNodeWithTag(ThemeTags.PREVIEW).assertExists(); rule.onNodeWithText("Dusk").assertExists()
        assertEquals(DockStyle.Standard, dock)
        rule.onNodeWithText("Apply").performClick(); rule.waitForIdle()
        assertEquals(DockStyle.ThreeD, dock); rule.onNodeWithTag(ThemeTags.APPLIED).assertExists()
        rule.onNodeWithText("Undo").performClick(); rule.waitForIdle()
        assertEquals(DockStyle.Standard, dock)
        rule.onNodeWithText("Cancel").performClick(); assertEquals(1, closed)
    }

    @Test fun CFG_10_packUi41_openingABadFileFromOutsideShowsTheErrorAndChangesNothing() {
        var closed = 0
        rule.setContent { Screen { ThemeImportFlow({ "{".toByteArray() }, host(), { closed++ }) } }
        rule.waitForIdle()
        rule.onNodeWithTag(ThemeTags.ERRORS).assertExists(); rule.onNodeWithText("Close").performClick(); assertEquals(1, closed)
        assertEquals(DockStyle.Standard, dock)
    }

    @Test fun CFG_10_packUi42_aLoaderThatFailsIsAnUnreadableFileNotACrash() {
        rule.setContent { Screen { ThemeImportFlow({ error("boom") }, host(), {}) } }
        rule.waitForIdle()
        rule.onNodeWithText("Liftoff could not read that file.", substring = true).assertExists()
    }

    @Test fun CFG_10_packUi43_theManifestHandlesViewOfAThemeFileWithoutNewPermissionsOrNetwork() {
        val m = File("src/main/AndroidManifest.xml").readText()
        val act = m.substringAfter("android:name=\".ThemeImportActivity\"").substringBefore("</activity>")
        assertTrue(act.contains("android.intent.action.VIEW")); assertTrue(act.contains(".liftofftheme")); assertTrue(act.contains("android:scheme=\"content\""))
        assertFalse("not browsable from web pages", act.contains("BROWSABLE"))
        assertFalse("no storage permission: the file arrives through the Uri it was handed", m.contains("READ_EXTERNAL_STORAGE")); assertFalse(act.contains("uses-permission"))
    }

    // --- Dock style pane ----------------------------------------------------------------------------------------------------------------------

    @Test fun DCK_04_style_40_theDockStylePaneStoresTheChoiceInTheDockSettings() {
        val manager = com.bos.sphere.feature.dock.WorkspaceManager(com.bos.sphere.feature.dock.InMemoryWorkspaceStore())
        rule.setContent { Screen { DockStylePane(manager) } }
        assertEquals(DockStyle.Standard, manager.current.dock.style)
        rule.onNodeWithTag(com.bos.sphere.feature.dock.DockStyleUi.rowTag(DockStyle.Transparent)).performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(DockStyle.Transparent, manager.current.dock.style)
        rule.onNodeWithTag(com.bos.sphere.feature.dock.DockStyleUi.rowTag(DockStyle.ThreeD)).performClick(); rule.waitForIdle()
        assertEquals(DockStyle.ThreeD, manager.current.dock.style)
        shoot("w39-dock-style-pane-dark")
    }

    @Test fun CFG_10_packUi50_screenshotLightPreview() {
        HyleTheme.apply(ThemeVariant.Light, 0xFF8E7BFF)
        pane(); importWith(dusk)
        shoot("w39-theme-preview-light")
    }

    @Test fun CFG_10_packUi51_screenshotThemesRows() { pane(); shoot("w39-themes-rows-dark") }
}
