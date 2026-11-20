package com.bos.sphere

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.LauncherStores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOM-01 / HOM-22 (wave 28, homeUnify, second review): the edit flow from every starting point (Glance with Pages Off, an empty Continuous scroll)
 * and where "Add page" is offered, through the real UI under Robolectric. Not a device run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class HomeUnifyReview2FlowTest : TourBase() {
    private val stores get() = LauncherStores.get(AppSmokeHarness.app)
    private fun key(i: Int) = AppSmokeHarness.appKey("App %02d".format(i))
    private fun pageKeys() = stores.layout.layout.value.pages.flatMap { it.keys }

    private fun start(pages: HomePages, glance: Boolean = true, layout: List<HomePage>? = null, edit: (LauncherPrefs) -> LauncherPrefs = { it }) {
        AppSmokeHarness.seedApps(*Array(12) { "App %02d".format(it) })
        AppSmokeHarness.configureFresh { edit(it.copy(glanceEnabled = glance, homePages = pages)) }
        AppSmokeHarness.app.getSharedPreferences("liftoff_misc", android.content.Context.MODE_PRIVATE).edit().putBoolean("defaultHomeSeeded", true).commit() // a real home: no first-run seed
        if (layout != null) stores.layout.replace(HomeLayout(pages = layout, seeded = true))
        launch()
    }

    private fun forward() {
        rule.onNodeWithTag("home.pager").performTouchInput { val y = height * 0.5f; swipe(Offset(width * 0.75f, y), Offset(width * 0.25f, y), 300) }; settle()
    }

    @Test fun HOM_01_w28_editHomeFromGlanceWithPagesOffTurnsPagesOnAndOpensTheFirstPageInEditModeThenAnAppCanBeAdded() {
        start(HomePages.Off)
        assertTrue(glanceShown()); longPressAt(0.5f, 0.3f); assertTrue(quickSheetShown())
        quickGoTo("Edit home")
        assertEquals("Edit home turns Pages on", HomePages.Paginated, stores.prefs.prefs.value.homePages)
        assertTrue("and opens the first page in edit mode", editShown()); assertFalse(glanceShown())
        rule.onNodeWithContentDescription("Add to home: App 04").performClick(); settle()
        assertTrue("the picked app is on the first page", key(4) in pageKeys())
    }

    @Test fun HOM_01_w28_anEmptyContinuousScrollSaysHowToAddAndEditModeAddsAnAppToTheScroll() {
        start(HomePages.Continuous, layout = listOf(HomePage(keys = emptyList(), id = 1L))) { it.copy(appsOnHome = com.bos.sphere.core.data.AppsOnHome.PinnedOnly) }
        forward(); assertFalse(glanceShown()); assertTrue(tagShown("scrollhome"))
        assertTrue("an empty scroll tells the user how to start", textShown("Long press to add apps"))
        rule.onNodeWithText("Long press to add apps").performClick(); settle()
        assertTrue("it opens edit mode", editShown())
        val offered = rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Add to home: ", substring = true), useUnmergedTree = true).fetchSemanticsNodes().map { it.config.toString() }
        assertTrue("the add carousel offers apps in edit mode of the scroll: $offered", offered.isNotEmpty())
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Add to home: ", substring = true), useUnmergedTree = true).onFirst().performClick(); settle()
        assertTrue("the app is stored on the scroll surface's page", pageKeys().size == 1)
    }

    @Test fun HOM_22_w28_addPageIsOfferedOnlyWhenPagesIsPaginatedSoThePagesSettingIsTheOnlySwitch() {
        start(HomePages.Off)
        longPressAt(0.5f, 0.3f); assertTrue(quickSheetShown())
        assertFalse("the quick sheet has no Add page any more (wave 34)", tagShown("quick.addpage"))
        openGroundControlHome()
        assertFalse("with Pages Off Ground Control has no Add page either (the Pages chips turn pages on)", textShown("Add page"))
        back(); back(); assertFalse(settingsShown()) // wave 40: the page goes back to the root list first
        stores.prefs.update { it.copy(homePages = HomePages.Paginated) }; settle()
        backwardToGlance()
        longPressAt(0.5f, 0.3f); assertTrue(quickSheetShown())
        assertFalse(tagShown("quick.addpage"))
        openGroundControlHome()
        assertTrue("with Paginated Ground Control, Home screen offers Add page", textShown("Add page"))
    }

    private fun backwardToGlance() { if (!glanceShown()) { rule.onNodeWithTag("home.pager").performTouchInput { val y = height * 0.5f; swipe(Offset(width * 0.25f, y), Offset(width * 0.75f, y), 300) }; settle() } }
}
