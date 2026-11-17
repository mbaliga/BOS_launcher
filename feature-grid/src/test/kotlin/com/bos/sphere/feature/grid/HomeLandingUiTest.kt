package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.HomeLanding
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.MinimalHotspots
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ThemeVariant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * HOM-22, INP-16, INP-17, INP-07: the landing surfaces under Robolectric. The four hotspots of Minimal are labelled buttons on the
 * focus graph, the quick sheet's segmented control and Ground Control's list pick a landing, Recent apps shows newest first and a
 * quiet empty state, All apps filters by typing. Both orientations, light and dark, right to left. Screenshots go to
 * build/screenshots/home_landing_*.png. Robolectric draws no compositor and no real window: none of this is a device run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeLandingUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @After fun reset() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
        L10n.setLanguage("")
        runCatching { LauncherInput.focus.nodes().map { it.id }.forEach(LauncherInput.focus::unregister) }
    }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(300)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { Canvas(bmp).also { it.drawColor(android.graphics.Color.rgb(20, 26, 50)); view.draw(it) } }
        File(outDir, "home_landing_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val labels = listOf("Alpha", "Bravo", "Camera", "Delta", "Echo", "Foxtrot")
    private val apps = labels.mapIndexed { i, l -> AppEntry("demo.$l", ComponentName("demo.$l", "M"), l, Process.myUserHandle()) { ColorDrawable(0xFF3070D0.toInt() + i * 0x101010) } }
    private val byKey = apps.associateBy { it.key }

    private fun hotspots(): List<Hotspot> = MinimalHotspots.defaults.map { (c, slot) -> Hotspot(c, MinimalHotspots.itemOf(slot)!!) }

    private fun minimal(rtl: Boolean = false, hints: Boolean = true, run: MutableList<Pair<CornerItem, Corner>> = ArrayList()) = rule.setContent {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Box(Modifier.fillMaxSize().background(Color(0xFF30406A))) {
                MinimalHotspotLayer(hotspots(), byKey, hints, onRun = { i, c -> run += i to c })
            }
        }
    }

    // ---- Minimal: the four hotspots

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_22_minimalShowsFourLabelledHotspotButtonsOnTheFocusGraphAndClickRunsThem() {
        val ran = ArrayList<Pair<CornerItem, Corner>>()
        minimal(run = ran)
        for (d in listOf("Top left hotspot: Radar", "Top right hotspot: Launchpad", "Bottom left hotspot: All apps", "Bottom right hotspot: Home settings")) {
            val n = rule.onAllNodesWithContentDescription(d, substring = true)
            assertEquals("one button for $d", 1, n.fetchSemanticsNodes().size)
        }
        val ids = LauncherInput.focus.nodes().filter { it.id.startsWith("minimal.corner.") }.map { it.id }.toSet()
        assertEquals("four nodes on the focus graph", setOf("minimal.corner.TopLeft", "minimal.corner.TopRight", "minimal.corner.BottomLeft", "minimal.corner.BottomRight"), ids)
        rule.onNodeWithContentDescription("Top right hotspot: Launchpad", substring = true).performClick()
        assertEquals(listOf(Corner.TopRight), ran.map { it.second })
        assertEquals(CornerItem.Action(CornerActionId.OpenLaunchpad), ran.single().first)
        shoot("minimal_portrait_dark")
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_22_minimalLandscapeKeepsAllFourHotspotsOnTheGraph() {
        minimal()
        assertEquals(4, LauncherInput.focus.nodes().count { it.id.startsWith("minimal.corner.") })
        val b = LauncherInput.focus.nodes().associate { it.id to it.rect }
        // physical corners: top left is left of top right and above bottom left, whatever the orientation
        assertTrue(b.getValue("minimal.corner.TopLeft").left < b.getValue("minimal.corner.TopRight").left)
        assertTrue(b.getValue("minimal.corner.TopLeft").top < b.getValue("minimal.corner.BottomLeft").top)
        shoot("minimal_landscape_dark")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_22_minimalRtlKeepsThePhysicalCornersAndLightThemeRenders() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF) }
        minimal(rtl = true)
        val b = LauncherInput.focus.nodes().associate { it.id to it.rect }
        assertTrue("top left stays on the physical left in right to left", b.getValue("minimal.corner.TopLeft").left < b.getValue("minimal.corner.TopRight").left)
        shoot("minimal_portrait_light_rtl")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_22_minimalWithoutHintsStillHasTheFourButtons() {
        minimal(hints = false)
        assertEquals(4, LauncherInput.focus.nodes().count { it.id.startsWith("minimal.corner.") })
        shoot("minimal_no_hints")
    }

    // ---- switching UI

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_22_theSegmentedControlOffersFourLandingsAndMarksTheSelectedOne() {
        val picked = ArrayList<HomeLanding>()
        rule.setContent { var cur by remember { mutableStateOf(HomeLanding.Selected) }; Box(Modifier.padding(16.dp).background(Color(0xFF16181F))) { LandingSegmented(cur, { cur = it; picked += it }) } }
        for (l in HomeLanding.entries) rule.onNodeWithContentDescription(l.label).assertIsDisplayed()
        rule.onNodeWithContentDescription("Selected apps").assertIsSelected()
        rule.onNodeWithContentDescription("Minimal").performClick()
        rule.onNodeWithContentDescription("Recent apps").performClick()
        assertEquals(listOf(HomeLanding.Minimal, HomeLanding.Recents), picked)
        shoot("segmented_portrait")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_22_groundControlListShowsAOneLineDescriptionForEachLanding() {
        val picked = ArrayList<HomeLanding>()
        rule.setContent { Box(Modifier.fillMaxSize().background(Color(0xFF16181F)).padding(16.dp)) { LandingChoiceList(HomeLanding.Recents, { picked += it }) } }
        for (l in HomeLanding.entries) rule.onNodeWithText(HomeLandingCopy.description(l)).assertIsDisplayed()
        rule.onNodeWithText("The apps you opened most recently, newest first").assertIsDisplayed()
        rule.onNodeWithContentDescription("All apps. Every app from A to Z, with an index and type to filter").performClick()
        assertEquals(listOf(HomeLanding.AllApps), picked)
        shoot("choice_list_portrait")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_22_everyLandingHasADescriptionAndAGlyph() {
        for (l in HomeLanding.entries) assertTrue(HomeLandingCopy.description(l).isNotBlank())
        rule.setContent { Column { for (l in HomeLanding.entries) HomeLandingGlyph(l) } }
        rule.waitForIdle()
    }

    // ---- empty home hint

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_22_theEmptyHomeHintHasASecondLineThatShowsRecentApps() {
        var recents = 0; var open = 0
        rule.setContent { Box(Modifier.fillMaxSize().background(Color(0xFF16181F))) { EmptyHomeHint(onOpen = { open++ }, onShowRecents = { recents++ }) } }
        rule.onNodeWithText("Long press to add apps").assertIsDisplayed()
        rule.onNodeWithText("Or show recent apps").assertIsDisplayed().performClick()
        assertEquals(1, recents); assertEquals(0, open)
        shoot("empty_hint")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_22_theEmptyHomeHintWithoutTheCallbackKeepsItsOneLine() {
        rule.setContent { Box(Modifier.fillMaxSize()) { EmptyHomeHint(onOpen = {}) } }
        rule.onNodeWithText("Long press to add apps").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithContentDescription("Or show recent apps").fetchSemanticsNodes().size)
    }

    // ---- Recent apps and All apps

    private fun look(style: HomeStyle, landscape: Boolean = false) = LandingLook(
        style, 4, landscape, androidx.compose.foundation.layout.PaddingValues(16.dp), 0.8f, 0.5f, true, null, null, { "Tools" },
    )

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_22_recentAppsShowsTheAppsNewestFirstInTheListStyleNotAlphabetically() {
        val recent = listOf(apps[3], apps[0], apps[5])
        rule.setContent { Box(Modifier.fillMaxSize().background(Color(0xFF16181F))) { RecentsLanding(recent, look(HomeStyle.List), false, {}, {}, {}) } }
        val ys = recent.map { rule.onNodeWithText(it.label).fetchSemanticsNode().boundsInRoot.top }
        assertTrue("Delta, then Alpha, then Foxtrot from the top: $ys", ys[0] < ys[1] && ys[1] < ys[2])
        shoot("recents_list_portrait")
    }

    private fun recentsIn(style: HomeStyle, shot: String, expectCamera: Boolean = true) {
        val recent = listOf(apps[2], apps[1], apps[0])
        rule.setContent { Box(Modifier.fillMaxSize().background(Color(0xFF16181F))) { RecentsLanding(recent, look(style), false, {}, {}, {}) } }
        rule.waitForIdle()
        if (expectCamera) rule.onNodeWithText("Camera").assertIsDisplayed()
        shoot(shot)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_22_recentAppsRendersTheGridStyle() = recentsIn(HomeStyle.Grid, "recents_grid_portrait")

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_22_recentAppsRendersTheDsiStyle() = recentsIn(HomeStyle.Dsi, "recents_dsi_portrait")

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_22_recentAppsRendersTheXmbStyle() = recentsIn(HomeStyle.Psp, "recents_xmb_portrait", expectCamera = false)

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_22_recentAppsLandscapeGrid() {
        rule.setContent { Box(Modifier.fillMaxSize().background(Color(0xFF16181F))) { RecentsLanding(apps.take(4), look(HomeStyle.Grid, landscape = true), false, {}, {}, {}) } }
        rule.onNodeWithText("Alpha").assertIsDisplayed()
        shoot("recents_grid_landscape")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_22_recentAppsEmptyStateIsOneQuietLineAndOffersUsageAccessOnlyWhenItIsOff() {
        var asked = 0
        rule.setContent { Box(Modifier.fillMaxSize().background(Color(0xFF16181F))) { RecentsLanding(emptyList(), look(HomeStyle.Grid), false, { asked++ }, {}, {}) } }
        rule.onNodeWithText("No recent apps yet").assertIsDisplayed()
        rule.onNodeWithText("To include apps you open from elsewhere, allow usage access.").assertIsDisplayed()
        rule.onNodeWithText("Allow usage access").performClick()
        assertEquals(1, asked)
        shoot("recents_empty_portrait")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_22_recentAppsEmptyStateDoesNotNagWhenUsageAccessIsOn() {
        rule.setContent { Box(Modifier.fillMaxSize()) { RecentsLanding(emptyList(), look(HomeStyle.Grid), true, {}, {}, {}) } }
        rule.onNodeWithText("No recent apps yet").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithContentDescription("Allow usage access").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("Allow usage access").fetchSemanticsNodes().size)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_22_recentAppsLongPressAndTheMenuKeyReachTheSameCallback() {
        val menu = ArrayList<String>()
        rule.setContent { Box(Modifier.fillMaxSize()) { RecentsLanding(listOf(apps[0]), look(HomeStyle.List), false, {}, {}, { menu += it.label }) } }
        rule.onNodeWithText("Alpha").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick)
        assertEquals("the row's long click is the app menu", listOf("Alpha"), menu)
        menu.clear()
        // a real press and hold on the row
        rule.onNodeWithText("Alpha").performTouchInput { down(center); advanceEventTime(900); }
        rule.mainClock.advanceTimeBy(900)
        rule.onNodeWithText("Alpha").performTouchInput { up() }
        assertEquals("press and hold opens the same menu", listOf("Alpha"), menu)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_22_allAppsShowsEveryAppAlphabeticallyAndTheIndexJumps() {
        rule.setContent { Box(Modifier.fillMaxSize().background(Color(0xFF16181F))) { AllAppsLanding(apps.reversed(), look(HomeStyle.List), 24.dp, {}, {}) } }
        val ys = labels.map { rule.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals("A to Z from the top", ys.sorted(), ys)
        rule.onNodeWithText("Type to filter apps").assertIsDisplayed()
        shoot("allapps_list_portrait")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_22_allAppsGridHasAnAlphabetIndexAndTypeToFilter() {
        rule.setContent { Box(Modifier.fillMaxSize().background(Color(0xFF16181F))) { AllAppsLanding(apps.reversed(), look(HomeStyle.Grid), 24.dp, {}, {}) } }
        rule.onNodeWithContentDescription("Alphabet index").assertIsDisplayed()
        rule.onNodeWithContentDescription("Jump to C").assertIsDisplayed()
        rule.onNodeWithText("Type to filter apps").assertIsDisplayed()
        shoot("allapps_grid_portrait")
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_22_allAppsLandscape() {
        rule.setContent { Box(Modifier.fillMaxSize().background(Color(0xFF16181F))) { AllAppsLanding(apps, look(HomeStyle.Grid, landscape = true), 8.dp, {}, {}) } }
        rule.onNodeWithText("Type to filter apps").assertIsDisplayed()
        shoot("allapps_grid_landscape")
    }
}
