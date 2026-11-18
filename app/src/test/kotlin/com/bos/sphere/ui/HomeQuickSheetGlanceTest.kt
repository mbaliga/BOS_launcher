package com.bos.sphere.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextContains
import org.junit.Assert.assertFalse
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bos.sphere.core.data.HomeOrder
import com.bos.sphere.core.data.HomeScroll
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
 * HOM-22, HOM-23 (wave 26): the long-press quick sheet has no landing selector. It carries the "Calm home screen (Glance)" On / Off tile, the
 * "Pages after Glance" Scroll / Paginated and Left to right / Right to left choices, and an "Add page" and an "Edit home pages" action on Glance.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class HomeQuickSheetGlanceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private class Seen { var prefs = LauncherPrefs(); var edit = 0; var add = 0 }

    private fun sheet(start: LauncherPrefs, seen: Seen, page: Int? = null) = rule.setContent {
        var p by remember { mutableStateOf(start) }
        HomeQuickSheet(
            prefs = p, update = { f -> p = f(p); seen.prefs = p }, page = page, pageCount = 1, onAddPage = { seen.add++ }, onDeletePage = {},
            onEdit = { seen.edit++ }, onWallpaper = {}, onGroundControl = {}, onDismiss = {},
        )
    }

    @Test fun HOM_22_theQuickSheetHasNoLandingSelectorAndOffersTheGlanceSwitchInItsPlace() {
        val seen = Seen(); sheet(LauncherPrefs(), seen)
        assertTrue(rule.onAllNodesWithText("HOME LANDING").fetchSemanticsNodes().isEmpty())
        for (old in listOf("Recent apps", "All apps", "Selected apps")) assertTrue("$old landing is gone", rule.onAllNodesWithContentDescription(old).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("Calm home screen (Glance)").assertIsDisplayed()
        rule.onNodeWithTag("quick.glance").assertTextContains("On")
    }

    @Test fun HOM_22_theGlanceTileSwitchesItOffAndOnAndTheSettingIsWritten() {
        val seen = Seen(); sheet(LauncherPrefs(), seen)
        rule.onNodeWithTag("quick.glance").performClick()
        assertFalse(seen.prefs.glanceEnabled)
        rule.onNodeWithTag("quick.glance").assertTextContains("Off")
        rule.onNodeWithTag("quick.glance").performClick()
        assertTrue(seen.prefs.glanceEnabled)
    }

    @Test fun HOM_01_theQuickSheetChoosesScrollOrPaginatedAndTheOrder() {
        val seen = Seen(); sheet(LauncherPrefs(), seen)
        rule.onNodeWithText("PAGES AFTER GLANCE").assertExists()
        rule.onNodeWithText("Scroll").performClick()
        assertEquals(HomeScroll.Continuous, seen.prefs.homeScroll); assertTrue("the choice counts as made", seen.prefs.pagesModeChosen)
        rule.onNodeWithText("Paginated").performClick()
        assertEquals(HomeScroll.Paged, seen.prefs.homeScroll)
        rule.onNodeWithText("Right to left").performClick()
        assertEquals(HomeOrder.RightToLeft, seen.prefs.homeOrder)
        rule.onNodeWithText("Left to right").performClick()
        assertEquals(HomeOrder.LeftToRight, seen.prefs.homeOrder)
    }

    @Test fun HOM_01_fromGlanceTheSheetHasAClearAddPageActionAndEditHomePages() {
        val seen = Seen(); sheet(LauncherPrefs(), seen)
        rule.onNodeWithTag("quick.addpage").performScrollTo().assertExists().performClick()
        assertEquals(1, seen.add)
        rule.onNodeWithText("Edit home pages").performScrollTo().performClick()
        assertEquals(1, seen.edit)
    }

    @Test fun HOM_23_theGlanceQuickSheetOffersContentHotspotsBackgroundAndEditHomePages() {
        val seen = Seen(); sheet(LauncherPrefs(), seen)
        rule.onNodeWithText("GLANCE CONTENT").assertIsDisplayed()
        rule.onNodeWithText("HOTSPOTS").performScrollTo().assertExists()
        rule.onNodeWithText("Background").performScrollTo().assertExists()
        rule.onNodeWithText("Edit home pages").performScrollTo().assertExists()
        rule.onNodeWithText("Date + Weather").performClick()
        assertEquals(setOf(com.bos.sphere.core.data.GlanceItem.Date, com.bos.sphere.core.data.GlanceItem.Weather, com.bos.sphere.core.data.GlanceItem.NowPlaying), seen.prefs.glanceContent)
        rule.onNodeWithText("Two (bottom)").performScrollTo().performClick()
        assertEquals(setOf(com.bos.sphere.core.data.Corner.BottomLeft, com.bos.sphere.core.data.Corner.BottomRight), seen.prefs.glanceHotspots)
    }

    @Test fun HOM_23_withGlanceOffTheGlanceContentRowsAreHidden() {
        val seen = Seen(); sheet(LauncherPrefs(glanceEnabled = false), seen)
        assertTrue(rule.onAllNodesWithText("GLANCE CONTENT").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("Calm home screen (Glance)").assertIsDisplayed(); rule.onNodeWithTag("quick.glance").assertTextContains("Off")
    }
}
