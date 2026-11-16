package com.bos.sphere

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
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
        stores.prefs.update { it.copy(onboardingDone = true) }
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
}
