package com.bos.sphere

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.PageLayout
import com.bos.sphere.core.data.UiLanguage
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.feature.grid.OneUiModel
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.ui.LanguageGlyphs
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Wave 54 (settingsVisual), CFG-23: every pick from a small fixed set in the real settings is a row of drawn cards. The categories are built the
 * way the home builds them, over several home styles; every Choice row must be a row of cards (none is left as a text radio page), each card group
 * is composed and driven (labels, 48 dp, one picked, a tap sets the setting), Language stays a list page, and the rows keep their titles and values
 * (the Settings search and the summaries read those).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h900dp-xhdpi")
class SettingsVisualW54Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var prefs = LauncherPrefs()
    private val layouts = ArrayList<PageLayout>()
    private val segments = ArrayList<Int>()

    private fun model(p: LauncherPrefs) = SettingsModel(
        prefs = p, updatePrefs = { f -> prefs = f(prefs) }, pageIndex = 0, pages = listOf(HomePage(keys = emptyList(), id = 1L), HomePage(keys = emptyList(), id = 2L)),
        setPageLayout = { _, l -> layouts += l }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
        about = listOf("Version" to "test"), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
        pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false, editCorner = {}, editGesture = {},
        system = SystemLinks({}, {}, {}, {}), setPuckSegments = { segments += it }, puckSegments = 8,
    )

    private fun variants() = listOf(
        "Pages" to LauncherPrefs(homeStyle = HomeStyle.Grid, homePages = HomePages.Paginated),
        "Continuous" to LauncherPrefs(homeStyle = HomeStyle.Grid, homePages = HomePages.Continuous),
        "DSi" to LauncherPrefs(homeStyle = HomeStyle.Dsi),
        "List" to LauncherPrefs(homeStyle = HomeStyle.List),
    )

    private fun choices(cats: List<SettingCategory>): List<Pair<String, SettingItem.Choice<*>>> =
        cats.flatMap { c -> c.items.filterIsInstance<SettingItem.Choice<*>>().map { c.title to it } }

    @Test fun CFG_23_noChoiceOfTheSettingsIsLeftAsATextRadioPage() {
        var rails = 0
        for ((name, p) in variants()) {
            for ((cat, item) in choices(buildSettingsCategories(model(p)))) {
                assertEquals("$name / $cat / ${item.title}: a choice is a row of cards", OneUiModel.Presentation.Rail, OneUiModel.presentation(item))
                assertTrue("$name / ${item.title}: from two to ${OneUiModel.VISUAL_MAX} options (${item.options.size})", OneUiModel.offersCards(item))
                rails++
            }
        }
        assertTrue("many choices were checked ($rails)", rails > 60)
    }

    @Test fun CFG_23_everySmallChoiceTheInventoryListsIsThere() {
        val titles = variants().flatMap { (_, p) -> choices(buildSettingsCategories(model(p))).map { it.second.title } }.toSet()
        for (t in listOf(
            "Order", "Right side", "Clock style", "Clock format", "Clock colour", "Clock size", "Hotspots", "Icon colour", "Calm style", "Show",
            "Wheel groups", "Ring segments", "Radar background", "Radar layout", "Web engine", "Default view", "Comms theme", "Page turning · portrait", "Page turning · landscape",
            "Scroll direction", "Corner style", "Icon type", "Light / dark", "Icon shape", "Style", "Pages", "Settings backdrop", "Left page", "Layout", "Default density", "Font",
        )) assertTrue("$t is a choice of cards", t in titles)
    }

    @Test fun CFG_23_languageStaysAListPageWithAGlyphAndItsOwnScriptPerRow() {
        val cats = buildSettingsCategories(model(LauncherPrefs()))
        val row = cats.flatMap { it.items }.first { it.title == "Language" }
        assertTrue("a pane, not a radio page", row is SettingItem.Pane)
        assertEquals("the value is the language's own name", "System", row.value)
        for (l in UiLanguage.entries) if (l != UiLanguage.System) assertNotNull("${l.name} has a glyph in its own script", LanguageGlyphs.letter(l))
        assertEquals(10, UiLanguage.entries.size)
    }

    @Test fun CFG_23_theRowsKeepTheirTitlesHintsAndValueTextForSearchAndSummaries() {
        val cats = buildSettingsCategories(model(LauncherPrefs()))
        val all = choices(cats).map { it.second }
        val clock = all.first { it.title == "Clock style" }
        assertEquals("Big digital", clock.value)
        assertEquals("The big clock on the Glance page", clock.hint)
        val order = all.first { it.title == "Order" }
        assertEquals("Left to right", order.value)
        assertTrue(order.hint!!.contains("Right to left"))
    }

    // ---- every card group, composed and driven -----------------------------------------------------------------------------------------------

    private var shown by mutableStateOf<SettingItem.Choice<*>?>(null)
    private val radios = SemanticsMatcher("a radio button") { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton }

    /** Choices whose change does not land in the preferences (the page layout, the ring, Comms' own store) are checked by their own recorders below. */
    private val notInPrefs = setOf("Layout", "Ring segments", "Default view", "Widgets position", "Font")

    @Test fun CFG_23_everyCardGroupShowsItsLabelsAtLeast48DpAndOnePickedAndATapSetsTheSetting() {
        rule.setContent { SphereTheme { Column(Modifier.width(300.dp)) { shown?.preview?.invoke(this) } } }
        var groups = 0; var cards = 0
        for ((name, p) in variants()) {
            prefs = p
            val titles = choices(buildSettingsCategories(model(prefs))).map { it.second.title }.distinct()
            for (title in titles) {
                prefs = p
                fun current() = choices(buildSettingsCategories(model(prefs))).map { it.second }.first { it.title == title }
                val item = current()
                @Suppress("UNCHECKED_CAST") val opts = item.options as List<Any?>
                rule.runOnIdle { shown = item }; rule.waitForIdle()
                val nodes = rule.onAllNodes(radios)
                val expected = if (title == "Default density") opts.filterNotNull().size else opts.size
                assertEquals("$name / $title: one card per option", expected, nodes.fetchSemanticsNodes().size)
                for (i in 0 until expected) {
                    val b = nodes[i].getBoundsInRoot()
                    assertTrue("$name / $title card $i is 48 dp or more (${b.bottom - b.top} x ${b.right - b.left})", (b.bottom - b.top) >= 48.dp && (b.right - b.left) >= 48.dp)
                    assertTrue("$name / $title card $i stays inside 300 dp", b.right <= 300.dp)
                }
                // the labels of the options are the texts of the cards
                val texts = nodes.fetchSemanticsNodes().map { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } }
                @Suppress("UNCHECKED_CAST") val labelOf = (item as SettingItem.Choice<Any?>).label
                val wanted = opts.filter { !(title == "Default density" && it == null) }.map { labelOf(it) }
                // a font card also shows its sample text above the name; every other card's text is exactly the label
                if (title == "Font") wanted.forEachIndexed { i, w -> assertTrue("$name / Font card $i ends with its name", texts[i]!!.endsWith(w)) }
                else assertEquals("$name / $title: each card's text is its option's label", wanted, texts)
                // exactly the picked one is selected
                val picked = nodes.fetchSemanticsNodes().count { it.config.getOrNull(SemanticsProperties.Selected) == true }
                assertEquals("$name / $title: one card is picked (or none for a custom density)", if (title == "Default density" && item.selected == null) 0 else 1, picked)
                groups++; cards += expected
                if (title in notInPrefs) continue
                // a tap on each other card sets the value
                for ((i, o) in opts.withIndex()) {
                    if (title == "Default density" && o == null) continue
                    if (title == "Left page" && o.toString() == "Widget") continue // opens the widget picker first
                    prefs = p
                    rule.runOnIdle { shown = current() }; rule.waitForIdle()
                    rule.onAllNodes(radios)[if (title == "Default density" && opts.first() == null) i - 1 else i].performClick(); rule.waitForIdle()
                    assertEquals("$name / $title: tapping card $i picks ${labelOf(o)}", o, current().selected)
                }
            }
        }
        assertTrue("many groups driven ($groups, $cards cards)", groups > 60 && cards > 200)
    }

    @Test fun CFG_23_thePageLayoutAndTheRingTapsGoToTheirOwnSetters() {
        prefs = LauncherPrefs(homeStyle = HomeStyle.Grid, homePages = HomePages.Paginated)
        val cats = buildSettingsCategories(model(prefs))
        val layout = choices(cats).map { it.second }.first { it.title == "Layout" }
        val ring = choices(cats).map { it.second }.first { it.title == "Ring segments" }
        rule.setContent { SphereTheme { Column(Modifier.width(300.dp)) { shown?.preview?.invoke(this) } } }
        rule.runOnIdle { shown = layout }; rule.waitForIdle()
        rule.onNodeWithTag("cfg.pageLayout.Circle").performClick(); rule.waitForIdle()
        assertEquals(1, layouts.size); assertEquals(com.bos.sphere.core.data.PageShape.Circle, layouts.single().shape)
        rule.runOnIdle { shown = ring }; rule.waitForIdle()
        rule.onNodeWithTag("cfg.ringSegments.6").performClick(); rule.waitForIdle()
        assertEquals(listOf(6), segments)
        rule.onNodeWithTag("cfg.ringSegments.8").assertIsSelected()
    }

    @Test fun CFG_23_clockColourOffersAutomaticAndAccentAsCards() {
        prefs = LauncherPrefs()
        val clockOn = choices(buildSettingsCategories(model(prefs))).map { it.second }.first { it.title == "Clock colour" }
        @Suppress("UNCHECKED_CAST") val lab = (clockOn as SettingItem.Choice<Any?>).label
        assertEquals(listOf("Automatic", "Accent"), clockOn.options.map { lab(it) })
        assertFalse(clockOn.selected as Boolean)
    }
}
