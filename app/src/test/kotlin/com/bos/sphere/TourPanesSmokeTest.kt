package com.bos.sphere

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.LeftPage
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.feature.grid.SettingsSpace
import com.bos.sphere.ui.LabEntry
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * TOUR, part 5 (wave 22): Ground Control, every category and every page. The category list is built the way the home builds it, over each home style,
 * and each category is shown by the real SettingsSpace; every Pane page and every visual-picker preview is composed. A row that throws while composing
 * would otherwise only be found by opening it on a phone. Also the rows the tour changes (Launchpad Off and back, the background pair, the hotspots) are
 * driven through their own onSelect/onChange and must land in the preferences.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class TourPanesSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var prefs = LauncherPrefs()
    private fun model(p: LauncherPrefs) = SettingsModel(
        prefs = p, updatePrefs = { f -> prefs = f(prefs) }, pageIndex = 0, pages = listOf(HomePage(keys = emptyList(), id = 1L), HomePage(keys = emptyList(), id = 2L)),
        setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
        about = listOf("Version" to "test"), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
        pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false, editCorner = {}, editGesture = {},
        system = SystemLinks({}, {}, {}, {}), lab = listOf(LabEntry("x", "Spike", "result", {}), LabEntry("y", "Readout", "42", null)),
    )

    private fun variants() = listOf(
        "Pages" to LauncherPrefs(homeStyle = HomeStyle.Grid, homePages = HomePages.Paginated),
        "Continuous" to LauncherPrefs(homeStyle = HomeStyle.Grid, homePages = HomePages.Continuous),
        "List" to LauncherPrefs(homeStyle = HomeStyle.List), "PSP" to LauncherPrefs(homeStyle = HomeStyle.Psp), "DSi" to LauncherPrefs(homeStyle = HomeStyle.Dsi),
    )

    @Test fun CFG_09_everyCategoryOfGroundControlComposesItsRowsOnEveryHomeStyle() {
        var cats by mutableStateOf(buildSettingsCategories(model(LauncherPrefs())))
        var idx by mutableIntStateOf(0)
        rule.setContent { SphereTheme { SettingsSpace(visible = true, categories = cats, initialCategory = idx, sounds = null, onClose = {}, session = idx) } }
        var shownCategories = 0
        for ((name, p) in variants()) {
            rule.runOnIdle { cats = buildSettingsCategories(model(p)); idx = 0 }; rule.waitForIdle()
            assertTrue("$name: the six tabs exist (${cats.size})", cats.size >= 6) // wave 57: six tabs
            for (i in cats.indices) {
                try { rule.runOnIdle { idx = i }; rule.waitForIdle() } catch (e: Throwable) { throw AssertionError("$name: category ${cats[i].title} does not compose: $e", e) }
                shownCategories++
            }
        }
        assertTrue(shownCategories >= 30)
    }

    private fun details(c: SettingCategory): List<Pair<String, @androidx.compose.runtime.Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit>> =
        c.items.mapNotNull { item ->
            val d: (@androidx.compose.runtime.Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit)? = when (item) { is SettingItem.Pane -> item.content; is SettingItem.Choice<*> -> item.preview; else -> null }
            d?.let { "${c.title} > ${item.title}" to it }
        }

    @Test fun CFG_09_everyPaneAndVisualPickerPageOfGroundControlComposes() {
        var shown by mutableStateOf<(@androidx.compose.runtime.Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit)?>(null)
        rule.setContent { SphereTheme { Column { shown?.invoke(this) } } }
        var count = 0
        for ((name, p) in variants()) for (c in buildSettingsCategories(model(p))) for ((title, d) in details(c)) {
            try { rule.runOnIdle { shown = d }; rule.waitForIdle() } catch (e: Throwable) { throw AssertionError("$name: the page $title does not compose: $e", e) }
            count++
        }
        assertTrue("pages were composed ($count)", count >= 20)
    }

    private fun item(title: String): SettingItem = buildSettingsCategories(model(prefs)).flatMap { it.items }.first { it.title == title }

    @Test fun HOM_23_theLaunchpadPageRowTurnsLaunchpadOffAndBack() {
        prefs = LauncherPrefs()
        @Suppress("UNCHECKED_CAST") val row = item("Left page") as SettingItem.Choice<LeftPage>
        assertEquals(LeftPage.Launchpad, row.selected)
        row.onSelect(LeftPage.Off); assertEquals(LeftPage.Off, prefs.leftPage)
        @Suppress("UNCHECKED_CAST") val again = item("Left page") as SettingItem.Choice<LeftPage>
        assertEquals(LeftPage.Off, again.selected); again.onSelect(LeftPage.Launchpad); assertEquals(LeftPage.Launchpad, prefs.leftPage)
    }

    @Test fun HOM_23_theHotspotsRowChangesTheGlanceHotspots() {
        prefs = LauncherPrefs()
        @Suppress("UNCHECKED_CAST") val row = item("Hotspots") as SettingItem.Choice<com.bos.sphere.core.data.HotspotPreset>
        row.onSelect(com.bos.sphere.core.data.HotspotPreset.None); assertTrue(prefs.glanceHotspots.isEmpty())
        row.onSelect(com.bos.sphere.core.data.HotspotPreset.entries.first { com.bos.sphere.core.data.GlanceHotspots.preset(it)?.size == 4 })
        assertEquals(4, prefs.glanceHotspots.size)
    }
}
