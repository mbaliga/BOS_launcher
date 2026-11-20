package com.bos.sphere

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.bos.sphere.core.data.AppsOnHome
import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherStores
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOM-01 (empty default home), HOM-12 (edit card with the page carousel) and HOM-04 / HOM-05 (add carousel) in the real app, under
 * Robolectric (see [AppSmokeHarness]). Not a device run: no compositor, no real touch pipeline.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class EditCardAppTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun freshProcess() { AppSmokeHarness.resetProcess() }

    @After fun closeApp() {
        if (scenario != null) { rule.waitForIdle(); assertFalse("a test left a global Compose snapshot write pending", Snapshot.current.hasPendingChanges()) }
        scenario?.close(); scenario = null
        AppSmokeHarness.resetProcess()
    }

    private fun settle(ms: Long = 1500) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); Snapshot.sendApplyNotifications(); rule.mainClock.advanceTimeBy(ms); org.robolectric.shadows.ShadowLooper.idleMainLooper(); rule.mainClock.advanceTimeBy(ms) }

    private fun startFresh(): LauncherStores {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        scenario = AppSmokeHarness.launch(); rule.waitForIdle()
        val stores = LauncherStores.get(AppSmokeHarness.app)
        settle()
        // Onboarding is not what is under test: finish it so the home is what the person sees.
        stores.prefs.update { it.copy(onboardingDone = true, glanceEnabled = false, homePages = com.bos.sphere.core.data.HomePages.Paginated) } // wave 28: Pages Paginated; wave 26: Glance off, so home is the first page; these tests are about the pages
        settle()
        return stores
    }

    private fun onHome(label: String) = rule.onAllNodesWithContentDescription(label, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun HOM_01_aFreshInstallStartsWithOneEmptyPageAndNoAppsPlaced() {
        val stores = startFresh()
        val l = stores.layout.layout.value
        assertEquals(1, l.pages.size); assertTrue(l.pages[0].keys.isEmpty()); assertTrue(l.seeded)
        assertEquals(AppsOnHome.PinnedOnly, stores.prefs.prefs.value.appsOnHome)
        // (Camera is also a corner shortcut, so the grid check uses apps that are not a corner action.)
        for (label in listOf("Alpha", "Bravo", "Delta")) assertFalse("$label is not placed on a fresh home", onHome(label))
        rule.onNodeWithText("Long press to add apps").assertIsDisplayed()
    }

    @Test fun HOM_01_anExistingSavedLayoutIsNeverTouched() {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        val key = AppSmokeHarness.appKey("Camera")
        val saved = HomeLayout(pages = listOf(HomePage(keys = listOf(key), id = 7L)), seeded = true)
        LauncherStores.get(AppSmokeHarness.app).layout.replace(saved)
        scenario = AppSmokeHarness.launch(); rule.waitForIdle(); settle()
        assertEquals(saved, LauncherStores.get(AppSmokeHarness.app).layout.layout.value)
        for (label in listOf("Alpha", "Bravo", "Delta")) assertFalse("$label was not added by the launcher", onHome(label))
    }

    // Wave 30 (pagesExplicit), replaces HOM_01_aReturningUserWithoutALayoutStillSeesTheirAppsFlow: nothing is placed for the user, not even for a user from before.
    @Test fun PGX_aReturningUserWithoutALayoutGetsNoAppsFlowedOntoThePages() {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        AppSmokeHarness.configure { it.copy(appsOnHome = AppsOnHome.Everything) } // onboarding done: a user from before this change
        scenario = AppSmokeHarness.launch(); rule.waitForIdle(); settle()
        for (label in listOf("Alpha", "Bravo", "Delta")) assertFalse("$label is not placed for the user", onHome(label))
        assertTrue(LauncherStores.get(AppSmokeHarness.app).layout.layout.value.pages.flatMap { it.keys }.isEmpty())
    }

    @Test fun HOM_12_theHintOpensEditModeWithTheCarouselsAndBackLeaves() {
        startFresh()
        rule.onNodeWithText("Long press to add apps").performClick(); settle()
        rule.onNodeWithContentDescription("Page carousel").assertIsDisplayed()
        rule.onNodeWithContentDescription("Add to home: Camera").assertIsDisplayed()
        // HOM-04: a tap adds the app to the first free cell of the current page.
        rule.onNodeWithContentDescription("Add to home: Camera").performClick(); settle()
        val keys = LauncherStores.get(AppSmokeHarness.app).layout.layout.value.pages.flatMap { it.keys }
        assertEquals(listOf(AppSmokeHarness.appKey("Camera")), keys)
        assertTrue("the placed app leaves the carousel", rule.onAllNodesWithContentDescription("Add to home: Camera").fetchSemanticsNodes().isEmpty())
        // W47: Back in the page editor is Cancel: something changed, so it asks first; Keep editing stays, Discard restores the snapshot and leaves.
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }; settle()
        assertTrue("Back asks because an app was placed", rule.onAllNodesWithText("Discard changes?").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithText("Keep editing").performClick(); settle()
        rule.onNodeWithContentDescription("Page carousel").assertIsDisplayed()
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }; settle()
        rule.onNodeWithText("Discard").performClick(); settle()
        assertTrue("the carousel is gone after Back and Discard", rule.onAllNodesWithContentDescription("Page carousel").fetchSemanticsNodes().isEmpty())
        assertTrue("the snapshot is back: nothing is placed", LauncherStores.get(AppSmokeHarness.app).layout.layout.value.pages.flatMap { it.keys }.isEmpty())
    }

    private fun pageKeys(stores: LauncherStores) = stores.layout.layout.value.pages.map { it.keys }
    private fun confirmDelete() { rule.onNode(androidx.compose.ui.test.hasText("Delete page") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isDialog()), useUnmergedTree = true).performClick(); settle(300) }


    @Test fun HOM_12_deletePageThenUndoRestoresItAndALaterEditIsNotUndoneByTheNotice() {
        val stores = startFresh()
        rule.onNodeWithText("Long press to add apps").performClick(); settle()
        val camera = AppSmokeHarness.appKey("Camera"); val alpha = AppSmokeHarness.appKey("Alpha")
        stores.layout.replace(HomeLayout(pages = listOf(HomePage(keys = listOf(camera), id = 7L), HomePage(keys = listOf(alpha), id = 8L)), seeded = true)); settle()
        assertEquals(listOf(listOf(camera), listOf(alpha)), pageKeys(stores))
        rule.onNodeWithTag("pe.page.1").performClick(); settle(700) // wave 36: the page editor's rail acts on the page being edited, so the card is tapped first
        rule.onNodeWithTag("pe.more.1").performClick(); settle(200); rule.onNodeWithTag("pe.menu.delete.1").performClick(); settle()
        rule.onNodeWithText("1 item will be removed from this page. It stays in the drawer.").assertIsDisplayed() // wave 30: it asks first
        confirmDelete()
        assertEquals("wave 30: the deleted page's items leave the home, none joins the previous page", listOf(listOf(camera)), pageKeys(stores))
        rule.onNodeWithText("Page deleted").assertIsDisplayed()
        rule.onNodeWithText("Undo", useUnmergedTree = true).performClick(); settle()
        assertEquals("one tap on the notice brought the whole page back", listOf(listOf(camera), listOf(alpha)), pageKeys(stores))
    }

    @Test fun HOM_12_theDeleteNoticeNeverUndoesAnEditMadeAfterTheDelete() {
        val stores = startFresh()
        rule.onNodeWithText("Long press to add apps").performClick(); settle()
        val camera = AppSmokeHarness.appKey("Camera"); val alpha = AppSmokeHarness.appKey("Alpha"); val bravo = AppSmokeHarness.appKey("Bravo")
        stores.layout.replace(HomeLayout(pages = listOf(HomePage(keys = listOf(camera), id = 7L), HomePage(keys = listOf(alpha), id = 8L)), seeded = true)); settle()
        rule.onNodeWithTag("pe.page.1").performClick(); settle(700) // wave 36: select the page, then the rail's Delete
        rule.onNodeWithTag("pe.more.1").performClick(); settle(200); rule.onNodeWithTag("pe.menu.delete.1").performClick(); settle(300); confirmDelete()
        rule.onNodeWithContentDescription("Add to home: Bravo").performClick(); settle(300) // the notice lasts six seconds
        val afterAdd = pageKeys(stores)
        assertTrue(bravo in afterAdd.flatten())
        rule.onNodeWithText("Page deleted").assertIsDisplayed()
        rule.onNodeWithText("Undo", useUnmergedTree = true).performClick(); settle(300)
        assertEquals("the notice only undoes the delete while nothing else changed", afterAdd, pageKeys(stores))
    }

    @Test fun HOM_12_deletingTheLastPageEmptiesItAndTheAppsStayInstalledAndInTheAddCarousel() {
        val stores = startFresh()
        rule.onNodeWithText("Long press to add apps").performClick(); settle()
        rule.onNodeWithContentDescription("Add to home: Camera").performClick(); settle()
        rule.onNodeWithTag("pe.more.0").performClick(); settle(200); rule.onNodeWithTag("pe.menu.delete.0").performClick(); settle(); confirmDelete()
        assertEquals(1, stores.layout.layout.value.pages.size)
        assertTrue(stores.layout.layout.value.pages[0].keys.isEmpty())
        rule.onNodeWithContentDescription("Add to home: Camera").assertIsDisplayed() // back in the carousel
    }

    @Test fun HOM_22_theHomeButtonToGlanceEndsAnOpenEditSession() {
        val stores = startFresh()
        stores.prefs.update { it.copy(glanceEnabled = true) }; settle() // Glance on: home still shows the pages until the Home button is pressed
        rule.onNodeWithText("Long press to add apps").performClick(); settle()
        rule.onNodeWithContentDescription("Page carousel").assertIsDisplayed()
        scenario!!.onActivity { androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)) }; settle()
        assertTrue("edit mode ended when home went back to Glance", rule.onAllNodesWithContentDescription("Page carousel").fetchSemanticsNodes().isEmpty())
        settle()
        assertTrue("and does not come back by itself", rule.onAllNodesWithContentDescription("Page carousel").fetchSemanticsNodes().isEmpty())
    }
}
