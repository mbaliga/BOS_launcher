package com.bos.sphere

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.CalmStyle
import com.bos.sphere.core.data.DockStyle
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.LookPresets
import com.bos.sphere.core.data.ThemeApplier
import com.bos.sphere.core.data.ThemeMode
import com.bos.sphere.core.data.ThemeUndo
import com.bos.sphere.core.data.WallpaperKind
import com.bos.sphere.core.data.WallpaperSurface
import com.bos.sphere.core.data.rawWallpaper
import com.bos.sphere.core.data.withWallpaper
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.feature.grid.CalibratorTags
import com.bos.sphere.feature.grid.LooksTags
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.feature.grid.SettingsSpace
import com.bos.sphere.ui.LooksRailSection
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.ThemeHost
import com.bos.sphere.ui.buildSettingsCategories
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Wave 56 (looksPresets): the Looks rail at the top of the Look page (CFG-26) and the Try it calibrators on the Corners, Home screen and Motion pages (CFG-27), through the
 * real settings builder, with fake settings behind the theme host. Screenshots of the real Look and Corners pages: `app/build/screenshots/settings-w56-*.png` (read by eye; not a device run).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2300dp-port-xhdpi")
class LooksCalibratorsW56Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var prefs by mutableStateOf(LauncherPrefs().withWallpaper(WallpaperSurface.Glance, com.bos.sphere.core.data.Wallpaper(kind = WallpaperKind.Solid, solid = 0xFF222222)))
    private var dock = DockStyle.Standard
    private fun host() = ThemeHost({ prefs }, ThemeApplier({ prefs }, { f -> prefs = f(prefs) }, { dock }, { dock = it }))

    @Before fun setUp() { L10n.setLanguage("en"); ThemeUndo.clear(); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
    @After fun tearDown() { ThemeUndo.clear(); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }

    private fun model(p: LauncherPrefs, motion: Boolean = true) = SettingsModel(
        prefs = p, updatePrefs = { f -> prefs = f(prefs) }, pageIndex = 0, pages = listOf(HomePage(keys = emptyList(), id = 1L)),
        setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
        about = listOf("Version" to "test"), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
        pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = motion, editCorner = {}, editGesture = {},
        system = SystemLinks({}, {}, {}, {}), lab = emptyList(),
    )

    private fun cats() = buildSettingsCategories(model(prefs))
    private fun look() = cats().first { it.title == "Look" }.items
    private fun all() = cats().flatMap { it.items }

    private fun rail() = rule.setContent { SphereTheme { Column(Modifier.fillMaxSize().background(HyleColors.Background).padding(16.dp)) { LooksRailSection(prefs, host(), dock) } } }

    // --- the Looks rail -------------------------------------------------------------------------------------------------------------------

    @Test fun CFG_26_w56_theLookPageStartsWithTheLooksRailAboveLightDark() {
        val items = look()
        assertEquals("Looks", items.first().title)
        val row = items.first() as SettingItem.Choice<*>
        assertNotNull("it is a rail row (cards inline, no page)", row.preview)
        assertTrue(row.hint!!.contains("left alone"))
        assertTrue(items.indexOfFirst { it.title == "Light / dark" } > 0)
        assertEquals("one Looks row", 1, all().count { it.title == "Looks" })
    }

    @Test fun CFG_26_w56_tappingALookAppliesItShowsTheAppliedLineAndTheUndoRowAndUndoBringsTheOldLookBack() {
        val before = prefs
        rail()
        LookPresets.all.forEach { rule.onNodeWithTag(LooksTags.card(it.id)).assertExists() }
        rule.onNodeWithTag(LooksTags.UNDO).assertDoesNotExist()
        rule.onNodeWithTag(LooksTags.card("terminal")).performClick(); rule.waitForIdle()
        val t = LookPresets.byId("terminal")!!.settings
        assertEquals(t.themeMode, prefs.themeMode); assertEquals(t.iconShape, prefs.iconShape); assertEquals(t.uiFont, prefs.uiFont); assertEquals(t.calmStyle, prefs.calmStyle)
        rule.onNodeWithTag(LooksTags.card("terminal")).assertIsSelected()
        rule.onNodeWithTag(LooksTags.APPLIED).assertExists(); rule.onNodeWithTag(LooksTags.UNDO).assertExists()
        rule.onNodeWithTag(LooksTags.card("midnight")).performClick(); rule.waitForIdle()
        rule.onNodeWithTag(LooksTags.card("midnight")).assertIsSelected()
        rule.onNodeWithTag(LooksTags.UNDO).performClick(); rule.waitForIdle()
        assertEquals("undo restores the look before the LAST tap (the one in-memory undo)", LookPresets.byId("terminal")!!.settings.themeMode, prefs.themeMode)
        assertNull(ThemeUndo.snapshot.value)
        rule.onNodeWithTag(LooksTags.UNDO).assertDoesNotExist()
        assertTrue(before.themeMode != ThemeMode.Light)
    }

    @Test fun CFG_26_w56_aLookOverAPictureOrLiveWallpaperLeavesItAloneAndTheHandEditedLookPicksNothing() {
        prefs = LauncherPrefs() // Signal wallpaper
        val own = prefs.rawWallpaper(WallpaperSurface.Glance)
        rail()
        rule.onNodeWithTag(LooksTags.card("sunrise")).performClick(); rule.waitForIdle()
        assertEquals(own, prefs.rawWallpaper(WallpaperSurface.Glance))
        rule.onNodeWithTag(LooksTags.card("sunrise")).assertIsSelected()
        prefs = prefs.copy(calmStyle = CalmStyle.Beam); rule.waitForIdle()
        LookPresets.all.forEach { assertTrue(!LookPresets.matches(prefs, dock, it)) }
    }

    // --- the calibrators ------------------------------------------------------------------------------------------------------------------

    @Test fun CFG_27_w56_tryItRowsSitUnderTheirSliders() {
        val corners = cats().first { it.title == "Corners" }.items.map { it.title }
        assertEquals("the hot corner delay slider is followed by its Try it", corners.indexOf("Hot corner delay") + 1, corners.indexOf("Try it: Hot corner delay"))
        prefs = prefs.copy(hotCornersOn = false)
        assertTrue("with hot corners off there is nothing to try", cats().first { it.title == "Corners" }.items.none { it.title.startsWith("Try it") })
        val titles = all().map { it.title }
        assertEquals(titles.indexOf("Motion sensitivity") + 1, titles.indexOf("Try it: Motion sensitivity"))
        assertTrue(titles.indexOf("Try it: Controls hide after") > titles.indexOf("Controls hide after (seconds)"))
        for (t in listOf("Try it: Hot corner delay", "Try it: Controls hide after", "Try it: Motion sensitivity")) assertTrue(t, all().none { it.title == t } || titles.count { it == t } == 1)
    }

    private fun tryRow(title: String) = all().first { it.title == title } as SettingItem.Choice<*>

    private fun showRow(title: String) = rule.setContent { SphereTheme { Column(Modifier.fillMaxSize().background(HyleColors.Background).padding(16.dp).verticalScroll(rememberScrollState())) { tryRow(title).preview!!.invoke(this) } } }

    @Test fun CFG_27_w56_holdingInTheHotCornerAreaWritesTheDelayToTheSameSettingAsTheSlider() {
        prefs = prefs.copy(hotCornersOn = true, hotCornerDwellMs = 150)
        showRow("Try it: Hot corner delay")
        rule.onNodeWithTag(CalibratorTags.area("hotCorner")).performTouchInput { down(center); advanceEventTime(420); up() }
        rule.waitForIdle()
        assertTrue("${prefs.hotCornerDwellMs}", prefs.hotCornerDwellMs in 410..430 && prefs.hotCornerDwellMs % 10 == 0)
        val slider = all().first { it.title == "Hot corner delay" } as SettingItem.Slider
        assertEquals("the slider is the source of truth and shows it", prefs.hotCornerDwellMs.toFloat(), slider.amount, 0f)
        rule.onNodeWithTag(CalibratorTags.reset("hotCorner")).performClick(); rule.waitForIdle()
        assertEquals(LauncherPrefs().hotCornerDwellMs, prefs.hotCornerDwellMs)
    }

    @Test fun CFG_27_w56_twoTapsInTheControlsAreaWriteWholeSecondsAndResetGoesBackToTheDefault() {
        showRow("Try it: Controls hide after")
        rule.onNodeWithTag(CalibratorTags.area("controls")).performTouchInput { down(center); advanceEventTime(40); up() }
        rule.onNodeWithTag(CalibratorTags.area("controls")).performTouchInput { advanceEventTime(7_000); down(center); advanceEventTime(40); up() }
        rule.waitForIdle()
        assertTrue("${prefs.glanceControlsSeconds}", prefs.glanceControlsSeconds in 7..8)
        rule.onNodeWithTag(CalibratorTags.reset("controls")).performClick(); rule.waitForIdle()
        assertEquals(com.bos.sphere.core.data.GlanceControls.DEFAULT_SECONDS, prefs.glanceControlsSeconds)
    }

    @Test fun CFG_27_w56_theMotionAreaHasAResetThatRestoresTheDefaultSensitivity() {
        prefs = prefs.copy(motionSensitivity = 0.9f)
        showRow("Try it: Motion sensitivity")
        rule.onNodeWithTag(CalibratorTags.reset("motion")).performClick(); rule.waitForIdle()
        assertEquals(LauncherPrefs().motionSensitivity, prefs.motionSensitivity, 0f)
    }

    // --- screenshots of the real pages ----------------------------------------------------------------------------------------------------

    private fun save(name: String) {
        rule.mainClock.advanceTimeBy(1_200); rule.waitForIdle()
        val dir = File(System.getProperty("user.dir"), "build/screenshots").also { it.mkdirs() }
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(dir, "settings-w56-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(dir, "settings-w56-$name.png").length() > 0)
    }

    private fun page(name: String, title: String, light: Boolean) {
        HyleTheme.apply(if (light) ThemeVariant.Light else ThemeVariant.Dark, 0xFF8E7BFF)
        val c = buildSettingsCategories(model(prefs))
        rule.mainClock.autoAdvance = false
        rule.setContent { SphereTheme { SettingsSpace(visible = true, categories = c, initialCategory = c.indexOfFirst { it.title == title }, sounds = null, onClose = {}, modifier = Modifier.fillMaxSize()) } }
        save(name)
    }

    @Test fun lookPageDark() = page("look-dark", "Look", false)
    @Test fun lookPageLight() = page("look-light", "Look", true)
    @Test fun cornersPageWithTryIt() = page("corners-tryit", "Corners", false)
}
