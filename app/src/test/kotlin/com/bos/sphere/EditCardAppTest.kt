package com.bos.sphere

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
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
        stores.prefs.update { it.copy(onboardingDone = true, homeLanding = com.bos.sphere.core.data.HomeLanding.Selected) } // wave 16: the default landing is Glance; these tests are about the Selected apps home
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

    @Test fun HOM_01_aReturningUserWithoutALayoutStillSeesTheirAppsFlow() {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        AppSmokeHarness.configure { it.copy(appsOnHome = AppsOnHome.Everything) } // onboarding done: a user from before this change
        scenario = AppSmokeHarness.launch(); rule.waitForIdle(); settle()
        assertTrue(onHome("Camera"))
        assertTrue(rule.onAllNodesWithContentDescription("Long press to add apps").fetchSemanticsNodes().isEmpty())
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
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }; settle()
        assertTrue("the carousel is gone after Back", rule.onAllNodesWithContentDescription("Page carousel").fetchSemanticsNodes().isEmpty())
    }

    private fun pageKeys(stores: LauncherStores) = stores.layout.layout.value.pages.map { it.keys }

    @Test fun HOM_12_deletePageThenUndoRestoresItAndALaterEditIsNotUndoneByTheNotice() {
        val stores = startFresh()
        rule.onNodeWithText("Long press to add apps").performClick(); settle()
        val camera = AppSmokeHarness.appKey("Camera"); val alpha = AppSmokeHarness.appKey("Alpha")
        stores.layout.replace(HomeLayout(pages = listOf(HomePage(keys = listOf(camera), id = 7L), HomePage(keys = listOf(alpha), id = 8L)), seeded = true)); settle()
        assertEquals(listOf(listOf(camera), listOf(alpha)), pageKeys(stores))
        rule.onNodeWithContentDescription("Delete page 2").performScrollTo().performClick(); settle()
        assertEquals("the deleted page's items joined the previous page", listOf(listOf(camera, alpha)), pageKeys(stores))
        rule.onNodeWithText("Page deleted").assertIsDisplayed()
        rule.onNodeWithText("Undo", useUnmergedTree = true).performClick(); settle()
        assertEquals("one tap on the notice brought the whole page back", listOf(listOf(camera), listOf(alpha)), pageKeys(stores))
    }

    @Test fun HOM_12_theDeleteNoticeNeverUndoesAnEditMadeAfterTheDelete() {
        val stores = startFresh()
        rule.onNodeWithText("Long press to add apps").performClick(); settle()
        val camera = AppSmokeHarness.appKey("Camera"); val alpha = AppSmokeHarness.appKey("Alpha"); val bravo = AppSmokeHarness.appKey("Bravo")
        stores.layout.replace(HomeLayout(pages = listOf(HomePage(keys = listOf(camera), id = 7L), HomePage(keys = listOf(alpha), id = 8L)), seeded = true)); settle()
        rule.onNodeWithContentDescription("Delete page 2").performScrollTo().performClick(); settle(300)
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
        rule.onNodeWithContentDescription("Delete page 1").performClick(); settle()
        assertEquals(1, stores.layout.layout.value.pages.size)
        assertTrue(stores.layout.layout.value.pages[0].keys.isEmpty())
        rule.onNodeWithContentDescription("Add to home: Camera").assertIsDisplayed() // back in the carousel
    }

    @Test fun HOM_22_leavingSelectedAppsEndsAnOpenEditSession() {
        val stores = startFresh()
        rule.onNodeWithText("Long press to add apps").performClick(); settle()
        rule.onNodeWithContentDescription("Page carousel").assertIsDisplayed()
        stores.prefs.update { it.copy(homeLanding = com.bos.sphere.core.data.HomeLanding.Minimal) }; settle()
        assertTrue("edit mode ended with the landing switch", rule.onAllNodesWithContentDescription("Page carousel").fetchSemanticsNodes().isEmpty())
        stores.prefs.update { it.copy(homeLanding = com.bos.sphere.core.data.HomeLanding.Selected) }; settle()
        assertTrue("and does not come back by itself", rule.onAllNodesWithContentDescription("Page carousel").fetchSemanticsNodes().isEmpty())
    }
}
