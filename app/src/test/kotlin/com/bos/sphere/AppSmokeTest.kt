package com.bos.sphere

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.bos.sphere.core.catalog.Folder
import com.bos.sphere.core.catalog.HomeExtrasStore
import com.bos.sphere.core.catalog.HomeState
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.data.AppsOnHome
import com.bos.sphere.core.data.CrashLoopGuard
import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.OpenSurface
import com.bos.sphere.core.data.OpenSurfaceStore
import com.bos.sphere.feature.dock.DockItemRef
import com.bos.sphere.feature.dock.WorkspaceRepository
import com.bos.sphere.feature.grid.LauncherInput
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Whole-app smoke tests (INP-05, INP-01, NFR-19, NFR-11, HOM-01, PAL-01): the real [MainActivity] starts under Robolectric and
 * the composed [HomeScreen] is driven the way a person drives it, with keys and Back and new intents. See [AppSmokeHarness] for
 * what is real and what is faked. None of this is a device run: Robolectric has no compositor, no real IME, no system Back
 * animation and no real launcher-apps service.
 *
 * MainActivity starts headless. The one thing that blocked it was `Activity.getDisplay()` throwing from `onStart` (the Robolectric
 * activity context is not tied to a display); `onStart` now reads the display id defensively and falls back to the default display.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class AppSmokeTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun freshProcess() { AppSmokeHarness.resetProcess() }

    @After fun closeApp() {
        if (scenario != null) { rule.waitForIdle(); assertFalse("a test left a global Compose snapshot write pending", Snapshot.current.hasPendingChanges()) }
        scenario?.close(); scenario = null
        AppSmokeHarness.resetProcess()
    }

    private fun start(restoreSurfaces: Boolean = false, apps: AppsOnHome = AppsOnHome.Everything, dock: Boolean = true): ActivityScenario<MainActivity> {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        AppSmokeHarness.configure { it.copy(appsOnHome = apps, restoreOpenSurfaces = restoreSurfaces) }
        // Wave 30 (pagesExplicit): nothing is placed for the user, so a test that wants apps on the page places them (as the user's own, recorded edit).
        LauncherStores.get(AppSmokeHarness.app).layout.replace(com.bos.sphere.core.data.HomeLayout(pages = listOf(com.bos.sphere.core.data.HomePage(listOf("Alpha", "Bravo", "Camera", "Delta").map { AppSmokeHarness.appKey(it) }, id = 1L)), seeded = true))
        LauncherStores.get(AppSmokeHarness.app).layout.markExplicit()
        if (dock) {
            WorkspaceRepository.get(AppSmokeHarness.app).setDock { it.copy(enabled = true) }
            WorkspaceRepository.get(AppSmokeHarness.app).setPins(listOf(DockItemRef.app(AppSmokeHarness.appKey("Alpha"))))
        }
        return AppSmokeHarness.launch().also { scenario = it; rule.waitForIdle() }
    }

    /** Radar is the surface with the "Speak to search" button. */
    private fun radarOpen() = rule.onAllNodesWithContentDescription("Speak to search").fetchSemanticsNodes().isNotEmpty()

    private fun radarField() = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().firstOrNull()
        ?.config?.let { c -> if (c.contains(SemanticsProperties.EditableText)) c[SemanticsProperties.EditableText].text else null }

    private fun drawerOpen() = LauncherInput.focus.nodes().any { it.id == "puck.back" }

    private fun key(sc: ActivityScenario<*>, code: Int, ctrl: Boolean = false): Boolean =
        AppSmokeHarness.press(sc, code, ctrl = ctrl).also { rule.waitForIdle() }

    /** Advances the hand-driven clock and lets the main looper (snapshot apply, posted work) run. */
    private fun settle(ms: Long = 1500) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); Snapshot.sendApplyNotifications(); rule.mainClock.advanceTimeBy(ms); org.robolectric.shadows.ShadowLooper.idleMainLooper(); rule.mainClock.advanceTimeBy(ms) }

    private fun back(sc: ActivityScenario<*>, manual: Boolean = false) { AppSmokeHarness.back(sc); if (manual) rule.mainClock.advanceTimeBy(1500) else rule.waitForIdle() }

    // ---- start and home

    @Test fun HOM_01_theAppStartsAndTheHomeShowsTheSeededAppsAndTheDock() {
        start()
        for (label in listOf("Camera", "Bravo", "Delta")) assertTrue("$label on the page grid", rule.onAllNodesWithContentDescription(label, substring = true).fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithContentDescription("Dock").assertIsDisplayed()
        assertTrue("the pinned app is on the rail", LauncherInput.focus.nodes().any { it.id.startsWith("dock.app:") })
        assertFalse(radarOpen()); assertFalse(drawerOpen())
    }

    /** Wave 15 (HOM-16): a fresh install draws the default pair behind the home and the signal level is announced once. */
    @Test fun HOM_16_aFreshHomeHasTheDefaultBackgroundPairAndAnnouncesTheSignalOnce() {
        AppSmokeHarness.configure { it.copy(radarSignalForce = com.bos.sphere.core.data.SignalForce.Good) }
        start()
        val p = LauncherStores.get(AppSmokeHarness.app).prefs.prefs.value
        assertEquals(com.bos.sphere.core.data.BackgroundChoice.DEFAULT, com.bos.sphere.core.data.BackgroundChoice(p.backgroundSlot1, p.backgroundSlot2))
        assertEquals(1, rule.onAllNodesWithContentDescription("Signal: good, Wi-Fi").fetchSemanticsNodes().size)
    }

    @Test fun HOM_16_withBothSlotsNoneTheHomeDrawsAndAnnouncesNoBackground() {
        AppSmokeHarness.configure { it.copy(backgroundSlot1 = com.bos.sphere.core.data.BgItem.None, backgroundSlot2 = com.bos.sphere.core.data.BgItem.None, radarSignalForce = com.bos.sphere.core.data.SignalForce.Good) }
        start()
        assertEquals(0, rule.onAllNodesWithContentDescription("Signal: good, Wi-Fi").fetchSemanticsNodes().size)
        rule.onNodeWithContentDescription("Dock").assertIsDisplayed()
    }

    @Test fun HOM_01_thePageGridShowsAppsAndAFolderAndTheFolderOpens() {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        AppSmokeHarness.configure { it.copy(appsOnHome = AppsOnHome.PinnedOnly) }
        val a = AppSmokeHarness.appKey("Alpha"); val b = AppSmokeHarness.appKey("Bravo"); val c = AppSmokeHarness.appKey("Camera"); val d = AppSmokeHarness.appKey("Delta")
        val folder = Folder("f1", "Work", listOf(a, b))
        HomeExtrasStore.get(AppSmokeHarness.app).set(HomeState(folders = mapOf("f1" to folder)))
        LauncherStores.get(AppSmokeHarness.app).layout.replace(HomeLayout(pages = listOf(HomePage(keys = listOf(ItemKeys.folder("f1"), c, d))), seeded = true))
        scenario = AppSmokeHarness.launch(); rule.waitForIdle()
        assertTrue("the folder is a cell", rule.onAllNodesWithContentDescription("Work", substring = true).fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("Work").fetchSemanticsNodes().isNotEmpty())
        assertTrue("an app beside the folder", rule.onAllNodesWithContentDescription("Camera", substring = true).fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("Camera").fetchSemanticsNodes().isNotEmpty())
        // a folder member is not a cell of its own while the folder is closed
        assertTrue(rule.onAllNodesWithContentDescription("Bravo", substring = true).fetchSemanticsNodes().isEmpty())
    }

    // ---- PAL-01

    @Test fun PAL_01_aPrintableKeyOnTheHomeOpensRadarWithThatCharacter() {
        val sc = start()
        assertFalse(radarOpen())
        assertTrue(key(sc, KeyEvent.KEYCODE_X))
        assertTrue("Radar is open", radarOpen())
        assertEquals("x", radarField())
    }

    @Test fun PAL_01_aPrintableKeyDoesNotRestartRadarWhileItIsOpenAndTheDrawerKeepsItsKeys() {
        val sc = start()
        key(sc, KeyEvent.KEYCODE_X)
        assertEquals("x", radarField())
        back(sc)
        assertFalse(radarOpen())
        key(sc, KeyEvent.KEYCODE_D, ctrl = true)
        assertTrue(drawerOpen())
        key(sc, KeyEvent.KEYCODE_Y)
        assertFalse("a typed key with the drawer open does not open Radar", radarOpen())
        assertTrue(drawerOpen())
    }

    // ---- INP-05, INP-01

    @Test fun INP_05_focusDockClosesAnOpenDrawerAndTheRailTakesFocus() {
        val sc = start()
        key(sc, KeyEvent.KEYCODE_D, ctrl = true)
        assertTrue(drawerOpen())
        assertTrue(key(sc, KeyEvent.KEYCODE_H, ctrl = true))
        assertFalse("the overlay was closed", drawerOpen())
        assertTrue("the rail has focus", LauncherInput.focus.currentId?.startsWith("dock.") == true)
    }

    @Test fun INP_05_focusDockClosesRadarAndTheRailTakesFocus() {
        val sc = start()
        key(sc, KeyEvent.KEYCODE_SLASH, ctrl = true)
        assertTrue(radarOpen())
        assertTrue(key(sc, KeyEvent.KEYCODE_H, ctrl = true))
        assertFalse(radarOpen())
        assertTrue("the rail has focus", LauncherInput.focus.currentId?.startsWith("dock.") == true)
    }

    @Test fun INP_05_withNoOverlayOpenFocusDockFocusesTheRailAndNothingIsClosed() {
        val sc = start()
        assertTrue(key(sc, KeyEvent.KEYCODE_H, ctrl = true))
        assertTrue(LauncherInput.focus.currentId?.startsWith("dock.") == true)
        assertFalse(radarOpen()); assertFalse(drawerOpen())
    }

    @Test fun INP_01_theComposedHomeRegistersItsRailAndCornersOnTheFocusGraphAndTheDrawerRaisesALayer() {
        val sc = start()
        val ids = LauncherInput.focus.nodes().map { it.id }
        assertTrue("rail item on the graph", ids.any { it.startsWith("dock.app:") })
        assertTrue("corners on the graph", ids.any { it.startsWith("corner.") })
        assertTrue(LauncherInput.focus.nodes().filter { it.id.startsWith("dock.") || it.id.startsWith("corner.") }.all { it.layer == 0 })
        key(sc, KeyEvent.KEYCODE_D, ctrl = true)
        assertTrue("the drawer's items sit above the home", LauncherInput.focus.nodes().filter { it.id == "puck.back" }.let { n -> n.isNotEmpty() && n.all { it.layer == 3 } })
        assertTrue(LauncherInput.focus.anyLayerActive)
    }

    // ---- NFR-19

    @Test fun NFR_19_backClosesRadarThenTheDrawerThenDoesNothingOnTheHome() {
        val sc = start()
        assertFalse("nothing intercepts Back on the plain home", AppSmokeHarness.hasEnabledBackCallback(sc))
        key(sc, KeyEvent.KEYCODE_SLASH, ctrl = true)
        assertTrue(radarOpen())
        assertTrue("Radar takes Back", AppSmokeHarness.hasEnabledBackCallback(sc))
        back(sc)
        assertFalse("Back closed Radar", radarOpen())
        key(sc, KeyEvent.KEYCODE_D, ctrl = true)
        assertTrue(drawerOpen())
        assertTrue("the drawer takes Back", AppSmokeHarness.hasEnabledBackCallback(sc))
        back(sc)
        assertFalse("Back closed the drawer", drawerOpen())
        assertFalse("no handler left on the home", AppSmokeHarness.hasEnabledBackCallback(sc))
        // Back on the plain home is left to the system: the activity registers nothing for it and overrides no legacy
        // onBackPressed. (Sending it here would reach Robolectric's own emulation of the system fallback, which finishes the
        // activity; what a real device does with a Back on a home app is a device step.)
        assertTrue("no legacy back override", MainActivity::class.java.declaredMethods.none { it.name == "onBackPressed" })
        assertFalse(radarOpen()); assertFalse(drawerOpen())
        rule.onNodeWithContentDescription("Dock").assertIsDisplayed()
    }

    // ---- NFR-11

    private fun openDrawerThenRestart(sc: ActivityScenario<MainActivity>) {
        key(sc, KeyEvent.KEYCODE_D, ctrl = true)
        assertTrue(drawerOpen())
        rule.waitForIdle()
        assertEquals("the open drawer is recorded", OpenSurface.Drawer, OpenSurfaceStore.get(AppSmokeHarness.app).last())
        sc.close(); scenario = null
        AppSmokeHarness.restartProcess()
    }

    @Test fun NFR_11_theDrawerComesBackAfterARestartWhenTheSettingIsOn() {
        openDrawerThenRestart(start(restoreSurfaces = true))
        scenario = AppSmokeHarness.launch(); rule.waitForIdle()
        assertTrue("the drawer is open again", drawerOpen())
    }

    @Test fun NFR_11_theDrawerDoesNotComeBackAfterARestartWhenTheSettingIsOff() {
        openDrawerThenRestart(start(restoreSurfaces = false))
        scenario = AppSmokeHarness.launch(); rule.waitForIdle()
        assertFalse(drawerOpen())
        rule.onNodeWithContentDescription("Dock").assertIsDisplayed()
    }

    @Test fun NFR_11_radarComesBackAfterARestartWhenTheSettingIsOn() {
        val sc = start(restoreSurfaces = true)
        key(sc, KeyEvent.KEYCODE_SLASH, ctrl = true)
        assertTrue(radarOpen())
        assertEquals(OpenSurface.Search, OpenSurfaceStore.get(AppSmokeHarness.app).last())
        sc.close(); scenario = null
        AppSmokeHarness.restartProcess()
        scenario = AppSmokeHarness.launch(); rule.waitForIdle()
        assertTrue(radarOpen())
    }

    @Test fun NFR_11_aRestartInSafeModeNeverRestoresAndKeepsTheRecord() {
        val sc = start(restoreSurfaces = true)
        key(sc, KeyEvent.KEYCODE_D, ctrl = true)
        assertEquals(OpenSurface.Drawer, OpenSurfaceStore.get(AppSmokeHarness.app).last())
        AppSmokeHarness.closeLikeAKill(sc); scenario = null
        // Wave 19 (was: any rapid start): the previous run crashed within the window, and the run before it was a start with no stable run: safe mode.
        // Two counted crashes are needed, so the first crash is recorded by an earlier start.
        AppSmokeHarness.resetProcess()
        AppSmokeHarness.previousProcessExit(android.app.ApplicationExitInfo.REASON_CRASH)
        scenario = AppSmokeHarness.launch(); rule.waitForIdle()
        AppSmokeHarness.closeLikeAKill(scenario!!); scenario = null
        AppSmokeHarness.resetProcess()
        AppSmokeHarness.previousProcessExit(android.app.ApplicationExitInfo.REASON_CRASH)
        scenario = AppSmokeHarness.launch(); rule.waitForIdle()
        assertTrue("two counted crashes put this start in safe mode", CrashLoopGuard.safeMode)
        assertFalse("safe mode never restores a surface", drawerOpen())
        assertEquals("the record is kept for the next normal start", OpenSurface.Drawer, OpenSurfaceStore.get(AppSmokeHarness.app).last())
    }

    @Test fun NFR_11_aConfigurationChangeInTheSameProcessDoesNotReopenAClosedSurface() {
        val sc = start(restoreSurfaces = true)
        key(sc, KeyEvent.KEYCODE_D, ctrl = true)
        back(sc)
        assertFalse(drawerOpen())
        sc.recreate(); rule.waitForIdle()
        assertFalse("restore applies once per process", drawerOpen())
    }

    @Test fun NFR_11_aRecreateOfTheActivityInTheSameProcessIsNotTakenForACrashLoop() {
        val sc = start()
        assertFalse(CrashLoopGuard.safeMode)
        // a change the manifest does not handle (system locale, for one) recreates the activity within the 30 second window
        sc.recreate(); rule.waitForIdle()
        assertFalse("a recreate is not a rapid restart after a crash", CrashLoopGuard.safeMode)
        rule.onNodeWithContentDescription("Dock").assertIsDisplayed()
    }

    // ---- new intents

    private fun newIntent(sc: ActivityScenario<MainActivity>, intent: Intent) {
        sc.onActivity { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, intent) }
        rule.waitForIdle()
    }

    @Test fun NFR_19_aHomeIntentWhileTheDrawerIsOpenClosesIt() {
        val sc = start()
        key(sc, KeyEvent.KEYCODE_D, ctrl = true)
        assertTrue(drawerOpen())
        newIntent(sc, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        assertFalse(drawerOpen())
    }

    @Test fun NFR_19_aHomeIntentWhileRadarIsOpenClosesIt() {
        val sc = start()
        key(sc, KeyEvent.KEYCODE_SLASH, ctrl = true)
        assertTrue(radarOpen())
        newIntent(sc, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        assertFalse(radarOpen())
    }

    @Test fun HOM_01_aFolderOpenIntentWhileTheDrawerIsOpenClosesItAndOpensTheFolder() {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        AppSmokeHarness.configure { it.copy(appsOnHome = AppsOnHome.PinnedOnly) }
        val a = AppSmokeHarness.appKey("Alpha"); val b = AppSmokeHarness.appKey("Bravo"); val c = AppSmokeHarness.appKey("Camera")
        HomeExtrasStore.get(AppSmokeHarness.app).set(HomeState(folders = mapOf("f1" to Folder("f1", "Work", listOf(a, b)))))
        LauncherStores.get(AppSmokeHarness.app).layout.replace(HomeLayout(pages = listOf(HomePage(keys = listOf(ItemKeys.folder("f1"), c))), seeded = true))
        val sc = AppSmokeHarness.launch().also { scenario = it }; rule.waitForIdle()
        key(sc, KeyEvent.KEYCODE_D, ctrl = true)
        assertTrue(drawerOpen())
        // The folder sheet is a Dialog with a text field; Robolectric's Compose test never reports idle while a dialog's text field
        // exists (the same happens for a bare Dialog + BasicTextField outside the launcher), so the clock is driven by hand here.
        rule.mainClock.autoAdvance = false
        sc.onActivity { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, Intent(AppSmokeHarness.app, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_FOLDER, "f1")) }
        settle()
        assertFalse("the drawer closed", drawerOpen())
        assertNull("the open request was taken by the home", HomeOpenRequests.folder.value)
        // A node query would wait for idle, so the dialog is read through Robolectric's window shadow instead.
        val sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        assertNotNull("the folder sheet is showing", sheet)
        assertTrue(sheet.isShowing)
        (sheet as androidx.activity.ComponentDialog).onBackPressedDispatcher.onBackPressed()
        settle()
        assertFalse("Back closes the folder sheet", sheet.isShowing)
        rule.mainClock.autoAdvance = true
    }

    // ---- rotation

    @Test fun NFR_19_rotationKeepsTheOpenSurfaceAndTheHome() {
        val sc = start()
        key(sc, KeyEvent.KEYCODE_D, ctrl = true)
        assertTrue(drawerOpen())
        var before: MainActivity? = null
        sc.onActivity { before = it }
        RuntimeEnvironment.setQualifiers("+w720dp-h360dp-land"); rule.waitForIdle()
        var after: MainActivity? = null
        sc.onActivity { after = it }
        assertTrue("MainActivity declares the change, so it is not recreated", before === after)
        assertTrue("the drawer survived the rotation", drawerOpen())
        RuntimeEnvironment.setQualifiers("+w360dp-h720dp-port"); rule.waitForIdle()
        back(sc)
        assertFalse(drawerOpen())
    }

    // ---- HOM-22: Glance on / off (wave 26: there is no landing selector any more)

    private fun setGlance(on: Boolean) {
        LauncherStores.get(AppSmokeHarness.app).prefs.update { it.copy(glanceEnabled = on) }; settle()
        // turning Glance on while the pages show keeps the pages; the Home button is what returns to Glance
        if (on) { newIntent(scenario!!, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)); settle() }
    }
    private fun showsLabel(label: String) = rule.onAllNodesWithContentDescription(label, substring = true).fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
    private fun graphIds() = LauncherInput.focus.nodes().map { it.id }

    @Test fun HOM_22_turningGlanceOnShowsTheCalmScreenAndOffBringsTheFirstPageBackWithNothingLost() {
        start()
        assertTrue("Glance off: the first page shows the seeded apps", showsLabel("Camera"))
        setGlance(true)
        assertTrue("Glance on: the calm home shows", rule.onAllNodesWithTag("glance.surface", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertFalse("no page icons on Glance", showsLabel("Delta"))
        setGlance(false)
        assertTrue("the original page grid is back, nothing was lost", showsLabel("Camera"))
    }

    // Wave 16 (HOM-23): the Glance home. The hotspot ids are unchanged; the clock is a focus node too, and the dots are gone.
    @Test fun HOM_22_glanceShowsOnlyTheClockAndTheFourHotspotsOnTheGraph() {
        start()
        assertTrue(graphIds().any { it.startsWith("dock.app:") })
        setGlance(true)
        val ids = graphIds()
        assertTrue("the hotspots and the indicator buttons are on the focus graph: $ids", ids.containsAll(listOf("glance.indicator.launchpad", "corner.TopLeft", "corner.TopRight", "corner.BottomLeft", "corner.BottomRight")))
        assertFalse("no dock", ids.any { it.startsWith("dock.") })
        for (l in listOf("Alpha", "Bravo", "Delta")) assertFalse("no tile for $l", showsLabel(l)) // wave 16: Camera is the top left hotspot's own action, so its name is on screen
        for (d in listOf("Camera", "Music", "All apps", "Radar")) rule.onNodeWithContentDescription(d, substring = true).assertIsDisplayed() // wave 38: the corner layer labels each hotspot with its action
        rule.onAllNodesWithContentDescription("Dock").fetchSemanticsNodes().let { assertTrue("the dock is not drawn", it.isEmpty()) }
        setGlance(false)
        assertTrue("dock and apps are back", graphIds().any { it.startsWith("dock.app:") } && showsLabel("Camera"))
    }

    @Test fun HOM_22_aHotspotRunsItsAssignedAction() {
        val sc = start()
        setGlance(true)
        // wave 16: the Glance hotspots are the corner assignments as they are (the owner's reference: camera top left, music top right, apps bottom left, search bottom right)
        rule.onNodeWithContentDescription("Radar", substring = true).performClick(); settle()
        assertTrue("the bottom right hotspot is Radar", radarOpen())
        back(sc, manual = true)
        rule.onNodeWithContentDescription("All apps", substring = true).performClick(); settle()
        assertTrue("the bottom left hotspot opens all apps (the drawer or the puck, by style)", LauncherInput.focus.anyLayerActive)
    }

    @Test fun HOM_22_groundControlHomeHasTheGlanceSwitchAddPageAndOrderAndNoLandingRow() {
        val sc = start()
        setGlance(true)
        rule.onNodeWithContentDescription("Camera", substring = true).assertIsDisplayed()
        AppSmokeHarness.press(sc, KeyEvent.KEYCODE_COMMA, ctrl = true); settle()
        rule.onNodeWithContentDescription("Home screen").performClick(); settle()
        assertTrue("the Home category has the Glance switch", rule.onAllNodesWithText("Calm home screen (Glance)").fetchSemanticsNodes().isNotEmpty())
        assertTrue("and an Add page action", rule.onAllNodesWithText("Add page").fetchSemanticsNodes().isNotEmpty())
        assertTrue("and the order", rule.onAllNodesWithText("Order").fetchSemanticsNodes().isNotEmpty())
        assertTrue("no landing row any more", rule.onAllNodesWithText("HOME LANDING").fetchSemanticsNodes().isEmpty() && rule.onAllNodesWithText("Home landing").fetchSemanticsNodes().isEmpty())
    }

    @Test fun HOM_22_theOldCycleLandingShortcutDoesNothingAnyMore() {
        val sc = start()
        val before = LauncherStores.get(AppSmokeHarness.app).prefs.prefs.value
        repeat(3) { AppSmokeHarness.press(sc, KeyEvent.KEYCODE_L, ctrl = true, shift = true); settle() }
        assertEquals(before.glanceEnabled, LauncherStores.get(AppSmokeHarness.app).prefs.prefs.value.glanceEnabled)
    }

    // ---- snapshot hygiene

    @Test fun theSuiteNeverLeavesGlobalSnapshotWritesPending() {
        val sc = start()
        key(sc, KeyEvent.KEYCODE_X)
        back(sc)
        // a write made outside a Compose host goes through a snapshot, never straight at global state
        val probe = mutableStateOf(0)
        Snapshot.withMutableSnapshot { probe.value = 1 }
        rule.waitForIdle()
        assertFalse("no global write is waiting to be applied", Snapshot.current.hasPendingChanges())
    }
}
