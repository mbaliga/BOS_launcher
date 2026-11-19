package com.bos.sphere

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ActivityScenario
import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.HomeOrder
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.design.ReducedMotionTesting
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
 * Wave 36 (pageEditor): the page editor in the real app under Robolectric: tabs, selecting a card (end state of the swing), Add page, delete,
 * Continuous, the right-to-left page order, reduced motion. Not a device run: no real touch pipeline, no compositor, no swing feel.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class PageEditorAppTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun freshProcess() { AppSmokeHarness.resetProcess() }

    @After fun closeApp() {
        if (scenario != null) { rule.waitForIdle(); assertFalse("a test left a global Compose snapshot write pending", Snapshot.current.hasPendingChanges()) }
        scenario?.close(); scenario = null
        ReducedMotionTesting.force(null)
        AppSmokeHarness.resetProcess()
    }

    private fun settle(ms: Long = 1500) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); Snapshot.sendApplyNotifications(); rule.mainClock.advanceTimeBy(ms); org.robolectric.shadows.ShadowLooper.idleMainLooper(); rule.mainClock.advanceTimeBy(ms) }

    private fun start(mode: HomePages = HomePages.Paginated, glance: Boolean = false, order: HomeOrder = HomeOrder.LeftToRight): LauncherStores {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        scenario = AppSmokeHarness.launch(); rule.waitForIdle()
        val stores = LauncherStores.get(AppSmokeHarness.app)
        settle()
        stores.prefs.update { it.copy(onboardingDone = true, glanceEnabled = glance, homePages = mode, homeOrder = order) }
        settle()
        val a = AppSmokeHarness.appKey("Alpha"); val b = AppSmokeHarness.appKey("Bravo"); val c = AppSmokeHarness.appKey("Camera")
        stores.layout.replace(HomeLayout(pages = listOf(HomePage(keys = listOf(a), id = 7L), HomePage(keys = listOf(b), id = 8L), HomePage(keys = listOf(c), id = 9L)), seeded = true)); settle()
        return stores
    }

    private fun openEditor(@Suppress("UNUSED_PARAMETER") stores: LauncherStores) {
        // W34: the Home sheet no longer has an Edit home row; the way in is the long press on empty home space (the quick sheet), then Ground Control > Home screen > Edit home.
        for (y in listOf(0.35f, 0.6f, 0.8f)) { // continuous puts items where the paginated home has space: try a few empty spots
            if (rule.onAllNodesWithTag("quick.groundcontrol", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() || has("Page carousel")) break
            rule.onRoot().performTouchInput { longClick(Offset(width * 0.5f, height * y)) }; settle(300)
        }
        if (has("Page carousel")) return // a long press on an empty spot of a continuous home enters edit mode directly
        rule.onNodeWithTag("quick.groundcontrol", useUnmergedTree = true).performScrollTo().performClick(); settle()
        rule.onNodeWithText("Edit home", useUnmergedTree = true).performScrollTo().performClick(); settle()
    }

    private fun pageKeys(stores: LauncherStores) = stores.layout.layout.value.pages.map { it.keys }
    private fun has(label: String) = rule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()

    @Test fun HOM_12_pe44_theEditorShowsThePageStripAndTheTabsAndATapOnACardMakesThatPageTheOneEdited() {
        val stores = start(); openEditor(stores)
        rule.onNodeWithContentDescription("Page carousel").assertIsDisplayed()
        rule.onNodeWithText("Apps").assertIsSelected()
        rule.onNodeWithTag("pe.page.0").assertIsSelected()
        rule.onNodeWithTag("pe.page.1").performClick(); settle(700); rule.onNodeWithTag("pe.page.2").performClick(); settle(700) // the swing is about 350 ms; this is its end state
        rule.onNodeWithTag("pe.page.2").assertIsSelected()
        assertTrue("the rail now acts on page 3", has("Delete page 3"))
        assertFalse(has("Delete page 1"))
    }

    @Test fun HOM_12_pe45_addPageCardAppendsOneEmptyPageAndFocusesIt() {
        val stores = start(); openEditor(stores)
        rule.onNodeWithTag("pe.page.1").performClick(); settle(700); rule.onNodeWithTag("pe.page.2").performClick(); settle(700)
        rule.onNodeWithTag("pe.add").performClick(); settle(700)
        assertEquals(4, stores.layout.layout.value.pages.size)
        assertTrue(pageKeys(stores)[3].isEmpty())
        rule.onNodeWithTag("pe.page.3").assertIsSelected()
    }

    @Test fun HOM_12_pe46_deleteOnTheRailAsksThenRemovesThePageAndUndoBringsItBack() {
        val stores = start(); openEditor(stores)
        val before = pageKeys(stores)
        rule.onNodeWithContentDescription("Delete page 1").performClick(); settle(300)
        rule.onNodeWithText("1 item will be removed from this page. It stays in the drawer.").assertIsDisplayed()
        rule.onNode(androidx.compose.ui.test.hasText("Delete page") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isDialog()), useUnmergedTree = true).performClick(); settle(300)
        assertEquals(2, stores.layout.layout.value.pages.size)
        rule.onNodeWithText("Undo", useUnmergedTree = true).performClick(); settle(300)
        assertEquals(before, pageKeys(stores))
    }

    @Test fun HOM_12_pe47_continuousPutsTheCardsInARowAndATapSelectsThePage() {
        val stores = start(HomePages.Continuous); openEditor(stores)
        val p1 = rule.onNodeWithTag("pe.page.0").getUnclippedBoundsInRoot()
        val p2 = rule.onNodeWithTag("pe.page.1").getUnclippedBoundsInRoot()
        assertTrue("side by side along the top", p2.left > p1.left && kotlin.math.abs(p2.top.value - p1.top.value) < 1f)
        rule.onNodeWithTag("pe.page.1").performClick(); settle(700)
        rule.onNodeWithTag("pe.page.1").assertIsSelected()
        assertTrue(has("Delete page 2"))
    }

    @Test fun HOM_12_pe48_theRightToLeftPageOrderMirrorsTheStripToTheLeft() {
        val stores = start(order = HomeOrder.RightToLeft); openEditor(stores)
        val strip = rule.onNodeWithContentDescription("Page carousel").getUnclippedBoundsInRoot()
        assertTrue("strip on the left half", strip.right.value < 360f * 0.75f)
    }

    @Test fun HOM_12_pe49_reducedMotionSelectsWithoutTheSwing() {
        ReducedMotionTesting.force(com.bos.sphere.core.quality.MotionPrefs(0f, 0f, 0f))
        val stores = start(); openEditor(stores)
        rule.onNodeWithTag("pe.page.1").performClick(); settle(300); rule.onNodeWithTag("pe.page.2").performClick(); settle(300)
        rule.onNodeWithTag("pe.page.2").assertIsSelected()
    }

    @Test fun HOM_12_pe50_glanceIsTheFirstCardAndItsSettingsReplaceThePageSettings() {
        val stores = start(glance = true); openEditor(stores)
        rule.onNodeWithTag("pe.glance").performClick(); settle(700)
        rule.onNodeWithTag("pe.glance").assertIsSelected()
        rule.onNodeWithText("Hotspots").assertIsDisplayed()
        assertFalse("no page actions on the Glance", has("Delete page 1"))
        rule.onNodeWithTag("pe.page.0").performClick(); settle(700)
        rule.onNodeWithTag("pe.page.0").assertIsSelected()
        assertTrue(has("Delete page 1"))
    }
}
