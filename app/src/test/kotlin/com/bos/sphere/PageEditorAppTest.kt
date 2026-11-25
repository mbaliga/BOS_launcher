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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
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

    private val many = (10..49).map { "App$it" }

    private fun start(mode: HomePages = HomePages.Paginated, glance: Boolean = false, order: HomeOrder = HomeOrder.LeftToRight, manyApps: Boolean = false): LauncherStores {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta", *(if (manyApps) many.toTypedArray() else emptyArray()))
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
        // Wave 40: on a continuous home the same long press opens Ground Control itself (its root list), which now covers the home and takes the next touch like any screen.
        fun settingsOpen() = rule.onAllNodesWithContentDescription("Close settings", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        for (y in listOf(0.65f, 0.8f, 0.35f)) { // continuous puts items where the paginated home has space: try a few empty spots
            if (rule.onAllNodesWithTag("quick.groundcontrol", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() || has("Page carousel") || settingsOpen()) break
            rule.onRoot().performTouchInput { longClick(Offset(width * 0.5f, height * y)) }; settle(300)
        }
        if (has("Page carousel")) return // a long press on an empty spot of a continuous home enters edit mode directly
        if (!settingsOpen()) rule.onNodeWithTag("quick.groundcontrol", useUnmergedTree = true).performScrollTo().performClick().also { settle() }
        // wave 40: Ground Control opens on the root list (the sheet's row) or, from a long press on a continuous home, on the Home screen page itself
        SettingsTestNav.goTo(rule, "Home", "Edit home"); settle(300) // wave 57: the Home tab, Pages section
        rule.onNodeWithText("Edit home", useUnmergedTree = true).performClick(); settle()
        // wave 45: Edit home opens the single-page edit mode; its title opens the page editor these tests are about
        if (!has("Page carousel") && rule.onAllNodesWithTag("edit.title", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) { rule.onNodeWithTag("edit.title", useUnmergedTree = true).performClick(); settle() }
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
        assertTrue("the page 3 card says it is selected", has("Page 3 of 3, selected"))
        assertFalse(has("Page 1 of 3, selected"))
    }

    @Test fun HOM_12_pe45_addPageCardAppendsOneEmptyPageAndFocusesIt() {
        val stores = start(); openEditor(stores)
        rule.onNodeWithTag("pe.page.1").performClick(); settle(700); rule.onNodeWithTag("pe.page.2").performClick(); settle(700)
        rule.onNodeWithTag("page-editor-strip").performScrollToNode(androidx.compose.ui.test.hasTestTag("pe.add")); settle(300) // the card at the end of the strip scrolls into view
        rule.onNodeWithTag("pe.add").performClick(); settle(700)
        assertEquals(4, stores.layout.layout.value.pages.size)
        assertTrue(pageKeys(stores)[3].isEmpty())
        rule.onNodeWithTag("pe.page.3").assertIsSelected()
    }

    @Test fun HOM_12_pe46_deleteInTheCardsOverflowMenuAsksThenRemovesThePageAndUndoBringsItBack() {
        val stores = start(); openEditor(stores)
        val before = pageKeys(stores)
        rule.onNodeWithTag("pe.more.0").performClick(); settle(200); rule.onNodeWithTag("pe.menu.delete.0").performClick(); settle(300)
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
        assertTrue("side by side in a row above the live card (the focused card is a little larger)", p2.left > p1.left && kotlin.math.abs(p2.top.value - p1.top.value) < 10f)
        rule.onNodeWithTag("pe.page.1").performClick(); settle(700)
        rule.onNodeWithTag("pe.page.1").assertIsSelected()
        assertTrue(has("Page 2 of 3, selected"))
    }

    @Test fun HOM_12_pe48_theRightToLeftPageOrderMirrorsTheRow() {
        val stores = start(order = HomeOrder.RightToLeft); openEditor(stores)
        val p1 = rule.onNodeWithTag("pe.page.0").getUnclippedBoundsInRoot(); val p2 = rule.onNodeWithTag("pe.page.1").getUnclippedBoundsInRoot()
        assertTrue("page 1 is to the right of page 2 in the mirrored row", p1.left > p2.left)
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
        assertFalse("the Glance card has no move or delete actions", rule.onNodeWithTag("pe.glance").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsActions.CustomActions))
        rule.onNodeWithTag("pe.page.0").performClick(); settle(700)
        rule.onNodeWithTag("pe.page.0").assertIsSelected()
        assertTrue(has("Actions for page 1"))
    }

    // ---- wave 45 (pageEditorLayout) ----

    @Test fun HOM_12_pl10_moveLaterInTheCardsOverflowMenuReordersTheStoredPagesAndFocusesTheMovedPage() {
        val stores = start(); openEditor(stores)
        val before = pageKeys(stores)
        rule.onNodeWithTag("pe.more.0").performClick(); settle(200)
        rule.onNodeWithTag("pe.menu.later.0").performClick(); settle(700)
        assertEquals(listOf(before[1], before[0], before[2]), pageKeys(stores))
        rule.onNodeWithTag("pe.page.1").assertIsSelected()
    }

    @Test fun HOM_12_pl11_dragReorderOfACardMovesThePageInTheStore() {
        val stores = start(); openEditor(stores)
        val before = pageKeys(stores)
        val pitch = 72f * 2f // dp to px at the xhdpi-ish test density is not needed: the drag is by travelled px; two slots right
        val bounds = rule.onNodeWithTag("pe.page.0").getUnclippedBoundsInRoot()
        val slot = (rule.onNodeWithTag("pe.page.1").getUnclippedBoundsInRoot().left - bounds.left).value
        rule.onNodeWithTag("pe.page.0").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(900)
        rule.onNodeWithTag("pe.page.0").performTouchInput { moveBy(Offset(slot * density * 2f + 4f, 0f)) }
        rule.onNodeWithTag("pe.page.0").performTouchInput { up() }
        settle(700)
        assertEquals("page 1 went to the end", listOf(before[1], before[2], before[0]), pageKeys(stores))
        assertTrue(pitch > 0f)
    }

    @Test fun HOM_12_pl12_noRailNoHamburgerNoDuplicateDeleteOrAddInTheEditor() {
        val stores = start(); openEditor(stores)
        assertFalse(has("Move page 1 later")); assertFalse(has("Move page 1 earlier")); assertFalse(has("Delete page 1"))
        assertFalse(has("Page editor menu"))
        rule.onNodeWithText("Settings").performClick(); settle(300)
        assertTrue("the Settings tab names the page", rule.onAllNodesWithText("Page 1").fetchSemanticsNodes().isNotEmpty())
        assertFalse("no Delete page button in Settings", rule.onAllNodesWithText("Delete page").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithText("All pages").assertIsDisplayed()
    }

    // ---- wave 37 (editorDebt) ----

    /** Three pages that each fill a few columns of the Continuous surface (replaced while the editor is open, so entering it still finds empty space to long press). */
    private fun fillPages(stores: LauncherStores): List<List<String>> {
        val keys = many.map { AppSmokeHarness.appKey(it) }
        val groups = listOf(keys.subList(0, 14), keys.subList(14, 27), keys.subList(27, 40))
        stores.layout.replace(HomeLayout(pages = groups.mapIndexed { i, g -> HomePage(keys = g, id = 7L + i) }, seeded = true)); settle()
        return groups
    }

    private fun surfaceScroll(): Pair<Float, Float> {
        val n = rule.onNode(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange) and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("scrollhome")))
            .fetchSemanticsNode()
        val r = n.config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange]
        return r.value() to r.maxValue()
    }

    private fun scrollSurfaceBy(px: Float) {
        rule.onNode(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange) and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("scrollhome")))
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(px, 0f) }
        settle(300)
    }

    @Test fun HOM_12_ed10_continuousTapOnACardScrollsTheSurfaceToThatPagesFirstColumn() {
        val stores = start(HomePages.Continuous, manyApps = true); openEditor(stores); fillPages(stores)
        val (v0, max) = surfaceScroll()
        assertTrue("the three pages need a scroll", max > 100f)
        assertEquals(0f, v0, 1f)
        rule.onNodeWithTag("pe.page.1").performClick(); settle(700)
        val (v1, _) = surfaceScroll()
        assertTrue("page 2 starts to the right of the start: the surface moved ($v1)", v1 > 50f)
        rule.onNodeWithTag("pe.page.1").assertIsSelected()
        rule.onNodeWithTag("pe.page.2").performClick(); settle(700)
        val (v2, _) = surfaceScroll()
        assertTrue("page 3 is further on ($v2 after $v1)", v2 > v1)
        rule.onNodeWithTag("pe.page.0").performClick(); settle(700)
        assertEquals("page 1 is the start", 0f, surfaceScroll().first, 1f)
        rule.onNodeWithTag("pe.page.0").assertIsSelected()
    }

    @Test fun HOM_12_ed11_scrollingTheContinuousSurfaceMovesTheStripSelection() {
        val stores = start(HomePages.Continuous, manyApps = true); openEditor(stores); fillPages(stores)
        rule.onNodeWithTag("pe.page.0").assertIsSelected()
        val (_, max) = surfaceScroll()
        scrollSurfaceBy(max); settle(700)
        assertEquals("at the far end", max, surfaceScroll().first, 2f)
        rule.onNodeWithTag("pe.page.2").assertIsSelected()
        assertTrue("the strip says page 3 is selected", has("Page 3 of 3, selected"))
        scrollSurfaceBy(-max); settle(700)
        rule.onNodeWithTag("pe.page.0").assertIsSelected()
    }

    @Test fun HOM_12_ed12_theBottomAddPagePillIsGoneAndTheNewPageCardAppendsAfterTheLastPagePaginated() {
        val stores = start(); openEditor(stores)
        rule.onNodeWithTag("pe.page.0").assertIsSelected()
        assertFalse("W47: the four-pill bar is gone, the bar is the edit chrome's", rule.onAllNodesWithText("Add page").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("page-editor-strip").performScrollToNode(androidx.compose.ui.test.hasTestTag("pe.add")); settle(300)
        rule.onNodeWithTag("pe.add").performClick(); settle(700)
        assertEquals("one empty page, after the LAST page", 4, stores.layout.layout.value.pages.size)
        assertTrue(pageKeys(stores)[3].isEmpty())
        rule.onNodeWithTag("pe.page.3").assertIsSelected()
    }

    @Test fun HOM_12_ed13_theNewPageCardAppendsAfterTheLastPageNotTheSelectedOneContinuous() {
        val stores = start(HomePages.Continuous); openEditor(stores)
        rule.onNodeWithTag("pe.page.1").performClick(); settle(700)
        assertFalse(rule.onAllNodesWithText("Add page").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("page-editor-strip").performScrollToNode(androidx.compose.ui.test.hasTestTag("pe.add")); settle(300)
        rule.onNodeWithTag("pe.add").performClick(); settle(700)
        assertEquals(4, stores.layout.layout.value.pages.size)
        assertTrue("appended after the last page, not after the selected one", pageKeys(stores)[3].isEmpty())
        rule.onNodeWithTag("pe.page.3").assertIsSelected() // the editor's own selection, not the pager index
        assertTrue(has("Page 4 of 4, empty, selected"))
    }

    @Test fun HOM_12_ed14_theHamburgerIsGoneAndTheNewPageCardAndTheBarStillAddAndLeave() {
        val stores = start(); openEditor(stores)
        assertFalse("no hamburger menu any more", rule.onAllNodesWithTag("pe.menu").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("page-editor-strip").performScrollToNode(androidx.compose.ui.test.hasTestTag("pe.add")); settle(300) // the card at the end of the strip scrolls into view
        rule.onNodeWithTag("pe.add").performClick(); settle(700)
        assertEquals(4, stores.layout.layout.value.pages.size)
        rule.onNodeWithTag("pe.page.3").assertIsSelected()
        rule.onNodeWithText("Done").performClick(); settle(700)
        assertFalse("Done left the editor", has("Page carousel"))
    }

    @Test fun HOM_12_ed15_whileTheEditorIsOpenOnlyTheStripAnnouncesPageNOfM() {
        val stores = start()
        assertEquals("the home's own dots announce page 2 of 3", 1, rule.onAllNodesWithContentDescription("Page 2 of 3").fetchSemanticsNodes().size)
        openEditor(stores)
        assertEquals("the strip card is the only one", 1, rule.onAllNodesWithContentDescription("Page 2 of 3").fetchSemanticsNodes().size)
        rule.onNodeWithText("Done").performClick(); settle(900)
        assertEquals("the dots are announced again after Done", 1, rule.onAllNodesWithContentDescription("Page 2 of 3").fetchSemanticsNodes().size)
    }
}
