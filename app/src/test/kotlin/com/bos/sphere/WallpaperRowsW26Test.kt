package com.bos.sphere

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.bos.sphere.core.data.BgItem
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.SignalForce
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.ui.LabEntry
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOM-16 (wave 26, owner clause 10, "Live wallpaper selection is not showing in the landing currently"): the live wallpaper
 * choices were only reachable through a "Background" row buried in Look > Theme, so they never showed where the home screen is
 * configured. Now Ground Control > Home screen lists "Glance wallpaper", the "Pages use the same wallpaper as Glance" switch and,
 * with it off, "Pages wallpaper", in every state; the live wallpaper rows are in each pane. These tests fail with the old
 * layout (no such rows in the Home screen category).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class WallpaperRowsW26Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var prefs by mutableStateOf(LauncherPrefs())

    @Before fun still() = com.bos.sphere.core.design.ReducedMotionTesting.force(com.bos.sphere.core.quality.MotionPrefs(0f, 0f, 0f))
    @After fun reset() = com.bos.sphere.core.design.ReducedMotionTesting.force(null)

    private fun model(p: LauncherPrefs, pages: Int) = SettingsModel(
        prefs = p, updatePrefs = { f -> prefs = f(prefs) }, pageIndex = 0, pages = List(pages) { HomePage(keys = emptyList(), id = it + 1L) },
        setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
        requestHealth = {}, about = listOf("Version" to "test"), healthAvailable = false, appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
        pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false, editCorner = {}, editGesture = {},
        system = SystemLinks({}, {}, {}, {}), lab = listOf(LabEntry("x", "Spike", "result", {})),
    )

    private fun homeItems(p: LauncherPrefs, pages: Int) = buildSettingsCategories(model(p, pages)).first { it.title == "Home screen" }.items

    private val states = buildList {
        for (glance in listOf(true, false)) for (signal in listOf(SignalForce.Live, SignalForce.NoSignal)) for (same in listOf(true, false)) for (pages in listOf(0, 1, 3)) {
            add(Triple(LauncherPrefs(glanceEnabled = glance, radarSignalForce = signal, pagesSameAsGlance = same, homeStyle = HomeStyle.Grid, homePages = if (pages == 3) HomePages.Continuous else HomePages.Paginated), pages, "$glance/$signal/same=$same/pages=$pages"))
        }
    }

    @Test fun HOM_16_theWallpaperRowsAreInTheHomeScreenCategoryInEveryState() {
        for ((p, pages, name) in states) {
            val items = homeItems(p, pages)
            assertNotNull("$name: Glance wallpaper row", items.firstOrNull { it.title == "Glance wallpaper" && it is SettingItem.Pane })
            val toggle = items.firstOrNull { it.title == "Pages use the same wallpaper as Glance" } as? SettingItem.Toggle
            assertNotNull("$name: the switch", toggle); assertEquals(name, p.pagesSameAsGlance, toggle!!.checked)
            assertEquals("$name: the Pages row exists exactly when the switch is off", !p.pagesSameAsGlance, items.any { it.title == "Pages wallpaper" })
            assertTrue("$name: the signal wallpaper is named in the summary", (items.first { it.title == "Glance wallpaper" } as SettingItem.Pane).value.contains("Signal wallpaper"))
        }
    }

    @Test fun HOM_16_theLookCategoryNoLongerHidesTheOnlyBackgroundRow() {
        val look = buildSettingsCategories(model(LauncherPrefs(), 1)).flatMap { it.items }
        assertFalse("one place for wallpapers: no second Background row", look.any { it.title == "Background" })
    }

    @Test fun HOM_16_theSwitchTurnsThePagesRowOnAndOff() {
        prefs = LauncherPrefs()
        val t = homeItems(prefs, 2).first { it.title == "Pages use the same wallpaper as Glance" } as SettingItem.Toggle
        t.onChange(false)
        assertFalse(prefs.pagesSameAsGlance)
        assertTrue(homeItems(prefs, 2).any { it.title == "Pages wallpaper" })
    }

    @Test fun HOM_16_openingEachPaneShowsTheLiveWallpapersAndSelectingOneChangesThePrefs() {
        var pane by mutableStateOf<(@androidx.compose.runtime.Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit)?>(null)
        rule.setContent { SphereTheme { Column(androidx.compose.ui.Modifier.verticalScroll(rememberScrollState())) { pane?.invoke(this) } } }
        for ((surfaceTitle, pick) in listOf("Glance wallpaper" to BgItem.Halo, "Pages wallpaper" to BgItem.Laser)) for (signal in listOf(SignalForce.Live, SignalForce.NoSignal)) {
            prefs = LauncherPrefs(radarSignalForce = signal, pagesSameAsGlance = false)
            val row = homeItems(prefs, 2).first { it.title == surfaceTitle } as SettingItem.Pane
            rule.runOnIdle { pane = row.content }; rule.waitForIdle()
            for (label in listOf("Beam", "Spire", "Halo", "Laser", "Shaft")) assertEquals("$surfaceTitle/$signal: $label", 2, rule.onAllNodesWithContentDescription(label, substring = true).fetchSemanticsNodes().size)
            rule.onAllNodes(hasContentDescription(pick.label, substring = true)).onFirst().performScrollTo().performClick(); rule.waitForIdle()
            if (surfaceTitle.startsWith("Glance")) assertEquals(pick, prefs.backgroundSlot1) else assertEquals(pick, prefs.pagesSlot1)
        }
    }
}
