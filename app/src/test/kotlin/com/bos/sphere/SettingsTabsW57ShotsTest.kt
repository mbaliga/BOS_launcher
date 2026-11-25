package com.bos.sphere

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingsSpace
import com.bos.sphere.ui.HiddenAppsAccess
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
import org.junit.After
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
 * Wave 57 (settingsTabs): screenshots of the six real tabs (dark), one in light, and the landscape rail, written to build/screenshots/w57-*.png
 * and read by eye against the owner's text (Liftoff's own text tabs, flat rows under grey section headers, drawn cards).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class SettingsTabsW57ShotsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun setUp() { L10n.setLanguage("en"); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
    @After fun tearDown() { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }

    private fun cats(): List<SettingCategory> = buildSettingsCategories(
        SettingsModel(
            prefs = LauncherPrefs(homePages = HomePages.Paginated), updatePrefs = {}, pageIndex = 0,
            pages = listOf(HomePage(keys = emptyList(), id = 1L), HomePage(keys = emptyList(), id = 2L), HomePage(keys = emptyList(), id = 3L)),
            setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
            about = listOf("Version" to "test"), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
            pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = true, editCorner = {}, editGesture = {},
            system = SystemLinks({}, {}, {}, {}), lab = emptyList(),
            hiddenApps = HiddenAppsAccess(count = 2, hiddenKeys = kotlinx.coroutines.flow.MutableStateFlow(emptySet()), allApps = emptyList(), setHidden = { _, _ -> }),
        ),
    )

    private fun show(tab: Int) {
        val c = cats()
        rule.setContent { SphereTheme { SettingsSpace(visible = true, categories = c, initialCategory = tab, sounds = null, onClose = {}, modifier = Modifier.fillMaxSize()) } }
        rule.mainClock.advanceTimeBy(900); rule.waitForIdle()
    }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(outDir, "$name.png").length() > 2000)
    }

    @Test fun CFG_40_screenshotHomeTabDark() { show(0); shoot("w57-tab-home-dark") }
    @Test fun CFG_40_screenshotLookTabDark() { show(1); shoot("w57-tab-look-dark") }
    @Test fun CFG_40_screenshotAppsTabDark() { show(2); shoot("w57-tab-apps-dark") }
    @Test fun CFG_40_screenshotRadarTabDark() { show(3); shoot("w57-tab-radar-dark") }
    @Test fun CFG_40_screenshotSidePanelTabDark() { show(4); shoot("w57-tab-sidepanel-dark") }
    @Test fun CFG_40_screenshotSystemTabDark() { show(5); shoot("w57-tab-system-dark") }
    @Test fun CFG_40_screenshotHomeTabLight() { HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF); show(0); shoot("w57-tab-home-light") }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun CFG_41_screenshotLandscapeRailDark() { show(1); shoot("w57-landscape-rail-dark") }

    @Test fun CFG_41_screenshotHomeTabScrolledDark() {
        show(0)
        repeat(2) { rule.onNodeWithTag("oneui.tabList.Home").performTouchInput { swipeUp() }; rule.mainClock.advanceTimeBy(400) }
        shoot("w57-tab-home-scrolled-dark")
    }

    @Test fun CFG_42_accessAndPrivacyListsEveryGrantInOnePage() {
        val c = cats()
        val row = c.first { it.title == "System" }.items.first { it.title == "Access and privacy" } as com.bos.sphere.feature.grid.SettingItem.Pane
        val ids = row.meta.entries.map { it.id }.toSet()
        for (k in com.bos.sphere.core.design.GrantKind.entries) assertTrue("$k is listed", com.bos.sphere.ui.SettingsRowMeta.PaneIds.access(k) in ids)
        assertTrue(com.bos.sphere.ui.SettingsRowMeta.PaneIds.ACCESS_DND in ids && com.bos.sphere.ui.SettingsRowMeta.PaneIds.ACCESS_DEFAULT_HOME in ids)
        rule.setContent { SphereTheme { androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(com.bos.sphere.core.design.HyleColors.Background)) { row.content(this) } } }
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        for (t in listOf("Do Not Disturb access", "Set as default home")) rule.onNodeWithText(t, substring = true).assertExists()
        shoot("w57-access-privacy-dark")
    }

    @Test fun CFG_43_studioPageSaysItIsAPlaceholderAndShowsItsAddress() {
        assertTrue("the address is the reserved placeholder host until Studio is published", com.bos.sphere.ui.LiftoffStudio.URL.startsWith("https://example.com/"))
        rule.setContent { SphereTheme { androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(com.bos.sphere.core.design.HyleColors.Background)) { com.bos.sphere.ui.StudioPane() } } }
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        rule.onNodeWithTag("studio.placeholder").assertExists()
        rule.onNodeWithTag("studio.url").assertExists()
        rule.onNodeWithText("Open in the browser").assertExists()
        assertEquals(android.content.Intent.ACTION_VIEW, com.bos.sphere.ui.LiftoffStudio.intent().action)
        val backup = File("../feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/BackupPane.kt").readText()
        assertTrue("Export, import and restore offers Import a Studio file", backup.contains("flashId = \"${com.bos.sphere.ui.SettingsRowMeta.PaneIds.BACKUP_IMPORT_STUDIO}\""))
    }
}
