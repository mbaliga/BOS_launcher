package com.bos.sphere.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bos.sphere.core.data.HomeLanding
import com.bos.sphere.core.data.LauncherPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** HOM-22: the long-press quick sheet carries the four-way landing control and an "Edit home pages" entry on every landing but Selected apps. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class HomeLandingQuickSheetTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private class Seen { var prefs = LauncherPrefs(); var edit = 0 }

    private fun sheet(start: HomeLanding, seen: Seen) = rule.setContent {
        var p by remember { mutableStateOf(LauncherPrefs(homeLanding = start)) }
        HomeQuickSheet(
            prefs = p, update = { f -> p = f(p); seen.prefs = p }, page = null, pageCount = 1, onAddPage = {}, onDeletePage = {},
            onEdit = { seen.edit++ }, onWallpaper = {}, onGroundControl = {}, onDismiss = {},
            landing = p.homeLanding, onLanding = { l -> p = p.copy(homeLanding = l); seen.prefs = p },
        )
    }

    @Test fun HOM_22_theQuickSheetHasAFourWaySegmentedControlThatSwitchesTheLanding() {
        val seen = Seen(); sheet(HomeLanding.Selected, seen)
        rule.onNodeWithText("HOME LANDING").assertIsDisplayed()
        for (l in HomeLanding.entries) rule.onNodeWithContentDescription(l.label).assertIsDisplayed()
        rule.onNodeWithContentDescription("Selected apps").assertIsSelected()
        rule.onNodeWithContentDescription("Recent apps").performClick()
        assertEquals(HomeLanding.Recents, seen.prefs.homeLanding)
        rule.onNodeWithContentDescription("Recent apps").assertIsSelected()
        rule.onNodeWithContentDescription("Glance").performClick() // wave 16: the fourth landing is labelled Glance
        assertEquals(HomeLanding.Minimal, seen.prefs.homeLanding)
    }

    @Test fun HOM_22_editHomePagesIsOfferedOnEveryLandingButSelectedApps() {
        val seen = Seen(); sheet(HomeLanding.Minimal, seen)
        // wave 16: the Glance quick sheet is longer (content and hotspot rows), so the entry is scrolled to before it is checked
        rule.onNodeWithText("Edit home pages").performScrollTo().performClick()
        assertEquals(1, seen.edit)
    }

    @Test fun HOM_23_theGlanceQuickSheetOffersContentHotspotsBackgroundLandingAndEditHomePages() {
        val seen = Seen(); sheet(HomeLanding.Glance, seen)
        rule.onNodeWithText("GLANCE CONTENT").assertIsDisplayed()
        rule.onNodeWithText("HOTSPOTS").performScrollTo().assertExists()
        rule.onNodeWithText("Background").performScrollTo().assertExists()
        rule.onNodeWithText("HOME LANDING").assertExists()
        rule.onNodeWithText("Edit home pages").performScrollTo().assertExists()
        rule.onNodeWithText("Date + Weather").performClick()
        assertEquals(setOf(com.bos.sphere.core.data.GlanceItem.Date, com.bos.sphere.core.data.GlanceItem.Weather, com.bos.sphere.core.data.GlanceItem.NowPlaying), seen.prefs.glanceContent)
        rule.onNodeWithText("Two (bottom)").performScrollTo().performClick()
        assertEquals(setOf(com.bos.sphere.core.data.Corner.BottomLeft, com.bos.sphere.core.data.Corner.BottomRight), seen.prefs.glanceHotspots)
    }

    @Test fun HOM_22_selectedAppsKeepsItsOwnEditHomeEntry() {
        val seen = Seen(); sheet(HomeLanding.Selected, seen)
        assertTrue(rule.onAllNodesWithText("Edit home pages").fetchSemanticsNodes().isEmpty())
    }
}
