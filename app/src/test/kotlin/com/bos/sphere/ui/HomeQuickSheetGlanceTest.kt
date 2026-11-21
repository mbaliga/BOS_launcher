package com.bos.sphere.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.bos.sphere.core.data.HomeOrder
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOM-01, HOM-22, HOM-23 (waves 26, 28, 34): the long-press Home sheet. Wave 34 (homeSheet): it keeps the Pages, Order, Glance content and Hotspots
 * choices and the Wallpaper and Ground Control rows; the Glance tile, Background, Time / Weather / Battery tiles, Add page, Edit home, Files and
 * "Widgets, folders and icons" are gone from it (they are in Ground Control, Home screen).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class HomeQuickSheetGlanceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private class Seen { var prefs = LauncherPrefs(); var wallpaper = 0; var ground = 0; var delete = 0 }

    private fun sheet(start: LauncherPrefs, seen: Seen, page: Int? = null, pages: Int = 1) = rule.setContent {
        var p by remember { mutableStateOf(start) }
        HomeQuickSheetBody(p, { f -> p = f(p); seen.prefs = p }, page, pages, { seen.delete++ }, { seen.wallpaper++ }, { seen.ground++ })
    }

    private fun gone(vararg texts: String) { for (t in texts) assertTrue("$t is not in the sheet", rule.onAllNodesWithText(t).fetchSemanticsNodes().isEmpty()) }

    @Test fun HOM_22_w34_theSheetNoLongerHasTheGlanceTileBackgroundTimeWeatherBatteryAddPageEditHomeFilesOrItemPicker() {
        val seen = Seen(); sheet(LauncherPrefs(homePages = HomePages.Paginated), seen)
        gone("Calm home screen (Glance)", "Background", "Weather", "Battery %", "Add page", "Edit home", "Files, usage and timeline", "Widgets, folders and icons")
        assertTrue(rule.onAllNodes(hasTestTag("quick.glance") or hasTestTag("quick.addpage") or hasTestTag("quick.edit")).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("Home").assertIsDisplayed()
    }

    @Test fun HOM_21_w37_theTitleUsesTheSharedPopupTitleTokenOf28spLightCentred() {
        sheet(LauncherPrefs(), Seen())
        val out = ArrayList<androidx.compose.ui.text.TextLayoutResult>()
        rule.onNodeWithTag("quick.sheet").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(out)
        val st = out[0].layoutInput.style
        assertEquals(com.bos.sphere.core.design.PopupTokens.TitleSize, st.fontSize); assertEquals(28f, st.fontSize.value, 0f)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Light, st.fontWeight)
        assertEquals(androidx.compose.ui.text.style.TextAlign.Center, st.textAlign)
    }

    @Test fun HOM_01_w28_theQuickSheetHasThreePagesChipsOffPaginatedContinuousAndTheOrder() {
        val seen = Seen(); sheet(LauncherPrefs(), seen)
        rule.onNodeWithText("PAGES").assertExists()
        assertEquals("exactly three Pages chips", 3, rule.onAllNodes(hasTestTag("quick.pages.Off") or hasTestTag("quick.pages.Paginated") or hasTestTag("quick.pages.Continuous")).fetchSemanticsNodes().size)
        rule.onNodeWithTag("quick.pages.Continuous").performClick()
        assertEquals(HomePages.Continuous, seen.prefs.homePages)
        rule.onNodeWithTag("quick.pages.Paginated").performClick()
        assertEquals(HomePages.Paginated, seen.prefs.homePages)
        rule.onNodeWithTag("quick.pages.Off").performClick()
        assertEquals(HomePages.Off, seen.prefs.homePages)
        rule.onNodeWithText("Right to left").performClick()
        assertEquals(HomeOrder.RightToLeft, seen.prefs.homeOrder)
        rule.onNodeWithText("Left to right").performClick()
        assertEquals(HomeOrder.LeftToRight, seen.prefs.homeOrder)
    }

    @Test fun HOM_23_theQuickSheetHasHotspotsChipsButNoGlanceContentChips() {
        val seen = Seen(); sheet(LauncherPrefs(), seen)
        gone("GLANCE CONTENT")
        for (t in listOf("Time", "Time + Date", "Date + Weather", "Time + Date + Weather")) gone(t)
        rule.onNodeWithText("HOTSPOTS").performScrollTo().assertExists()
        for (t in listOf("Four", "Two (bottom)", "One", "None")) rule.onNodeWithText(t).performScrollTo().assertExists()
        rule.onNodeWithText("Two (bottom)").performScrollTo().performClick()
        assertEquals(setOf(com.bos.sphere.core.data.Corner.BottomLeft, com.bos.sphere.core.data.Corner.BottomRight), seen.prefs.glanceHotspots)
    }

    @Test fun GFX_38_theHotspotsChipsStayWithGlanceOffBecauseTheHotspotsAreOnEveryPage() {
        val seen = Seen(); sheet(LauncherPrefs(glanceEnabled = false), seen)
        gone("GLANCE CONTENT")
        rule.onNodeWithText("HOTSPOTS").performScrollTo().assertExists()
        rule.onNodeWithText("PAGES").assertExists()
    }

    @Test fun HOM_16_w34_theTwoBottomRowsAreWallpaperAndGroundControl() {
        val seen = Seen(); sheet(LauncherPrefs(), seen)
        rule.onNodeWithTag("quick.wallpaper").performScrollTo().performClick(); assertEquals(1, seen.wallpaper)
        rule.onNodeWithTag("quick.groundcontrol").performScrollTo().performClick(); assertEquals(1, seen.ground)
    }

    @Test fun HOM_22_w34_onAPageThePageToolsStayAndDeletePageWorksButThereIsNoAddPage() {
        val seen = Seen(); sheet(LauncherPrefs(homePages = HomePages.Paginated), seen, page = 0, pages = 2)
        rule.onNodeWithText("THIS PAGE").performScrollTo().assertExists()
        rule.onNodeWithText("TRANSITION").performScrollTo().assertExists()
        gone("Add page")
        rule.onNodeWithText("Delete page").performScrollTo().performClick(); assertEquals(1, seen.delete)
    }
}
