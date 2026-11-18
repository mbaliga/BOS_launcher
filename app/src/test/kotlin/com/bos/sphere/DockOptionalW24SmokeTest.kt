package com.bos.sphere

import android.view.KeyEvent
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.core.app.ActivityScenario
import com.bos.sphere.core.data.ChromeSettings
import com.bos.sphere.core.data.HomeShown
import com.bos.sphere.core.data.RightSide
import com.bos.sphere.core.input.LauncherCommand
import com.bos.sphere.feature.dock.DockItemRef
import com.bos.sphere.feature.dock.WorkspaceRepository
import com.bos.sphere.feature.grid.LauncherInput
import com.bos.sphere.ui.FocusDockRouting
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

/**
 * Wave 24, the dock stays optional (docs/decisions/dockOptional.md): "Focus the dock" (Ctrl+H) used to close Radar or the drawer for
 * a rail that never came when the dock was off or the landing draws none (the default Glance landing). It now leaves the overlay
 * open and says why. The real [MainActivity] under Robolectric; keys go through the activity's dispatch. Not a device run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class DockOptionalW24SmokeTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun freshProcess() { AppSmokeHarness.resetProcess() }

    @After fun closeApp() {
        if (scenario != null) { rule.waitForIdle(); assertFalse("a test left a global Compose snapshot write pending", Snapshot.current.hasPendingChanges()) }
        scenario?.close(); scenario = null
        AppSmokeHarness.resetProcess()
    }

    private fun start(dockOn: Boolean, glance: Boolean = false, rightSide: RightSide = RightSide.RecentsPanel): ActivityScenario<MainActivity> {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        if (glance) AppSmokeHarness.configureFresh { it.copy(rightSide = rightSide) } else AppSmokeHarness.configure { it.copy(rightSide = rightSide) }
        WorkspaceRepository.get(AppSmokeHarness.app).setDock { it.copy(enabled = dockOn) }
        WorkspaceRepository.get(AppSmokeHarness.app).setPins(listOf(DockItemRef.app(AppSmokeHarness.appKey("Alpha"))))
        return AppSmokeHarness.launch().also { scenario = it; rule.waitForIdle() }
    }

    private fun key(sc: ActivityScenario<*>, code: Int, ctrl: Boolean = false): Boolean = AppSmokeHarness.press(sc, code, ctrl = ctrl).also { rule.waitForIdle() }
    private fun radarOpen() = rule.onAllNodesWithContentDescription("Speak to search").fetchSemanticsNodes().isNotEmpty()
    private fun drawerOpen() = LauncherInput.focus.nodes().any { it.id.startsWith("drawer.") }

    // ---- the rule

    @Test fun INP_05_noDockIsOffOnlyWhenTheLandingWouldDrawARailAndTheSettingIsOff() {
        val c = ChromeSettings()
        assertNull(FocusDockRouting.noDock(HomeShown.Pages, c, dockEnabled = true))
        assertEquals(FocusDockRouting.NoDock.Off, FocusDockRouting.noDock(HomeShown.Pages, c, dockEnabled = false))
        assertEquals(FocusDockRouting.NoDock.Landing, FocusDockRouting.noDock(HomeShown.Glance, c, dockEnabled = true))
        assertNull("Glance with the dock rail on its right side always shows the rail", FocusDockRouting.noDock(HomeShown.Glance, c.copy(rightSide = RightSide.DockRail), dockEnabled = false))
    }

    @Test fun INP_05_withNoDockToComeFocusDockClosesNothingAndReportsWhy() {
        val order = mutableListOf<String>()
        val handled = FocusDockRouting.focusDock(
            LauncherCommand.FocusDock, overlayOpen = true, closeOverlays = { order += "close" }, dock = { order += "dock"; true },
            noDock = FocusDockRouting.NoDock.Off, onNoDock = { order += "why:${it.name}" },
        )
        assertTrue(handled)
        assertEquals(listOf("why:Off"), order)
    }

    // ---- the composed home

    @Test fun INP_05_withTheDockOffFocusDockLeavesTheDrawerOpenAndSaysTheDockIsOff() {
        val sc = start(dockOn = false)
        key(sc, KeyEvent.KEYCODE_D, ctrl = true)
        assertTrue(drawerOpen())
        assertTrue(key(sc, KeyEvent.KEYCODE_H, ctrl = true))
        assertTrue("the drawer is not closed for a dock that is off", drawerOpen())
        assertEquals(FocusDockRouting.NoDock.Off.message, ShadowToast.getTextOfLatestToast())
        assertTrue("no rail took focus", LauncherInput.focus.nodes().none { it.id.startsWith("dock.") })
    }

    @Test fun INP_05_withTheDockOffFocusDockLeavesRadarOpen() {
        val sc = start(dockOn = false)
        key(sc, KeyEvent.KEYCODE_SLASH, ctrl = true)
        assertTrue(radarOpen())
        assertTrue(key(sc, KeyEvent.KEYCODE_H, ctrl = true))
        assertTrue("Radar stays open", radarOpen())
    }

    @Test fun INP_05_onTheDefaultGlanceLandingFocusDockLeavesRadarOpenAndSaysTheLandingHasNoDock() {
        val sc = start(dockOn = true, glance = true)
        key(sc, KeyEvent.KEYCODE_SLASH, ctrl = true)
        assertTrue(radarOpen())
        assertTrue(key(sc, KeyEvent.KEYCODE_H, ctrl = true))
        assertTrue("Radar stays open", radarOpen())
        assertEquals(FocusDockRouting.NoDock.Landing.message, ShadowToast.getTextOfLatestToast())
    }

    @Test fun INP_05_withTheDockOnFocusDockStillClosesTheDrawerAndTheRailTakesFocus() {
        val sc = start(dockOn = true)
        key(sc, KeyEvent.KEYCODE_D, ctrl = true)
        assertTrue(drawerOpen())
        assertTrue(key(sc, KeyEvent.KEYCODE_H, ctrl = true))
        assertFalse(drawerOpen())
        assertTrue(LauncherInput.focus.currentId?.startsWith("dock.") == true)
    }

    @Test fun STR_14_withTheRightSideOnDockRailAndTheDockOffOpenRecentsSaysTheDockIsOffInsteadOfNothing() {
        val sc = start(dockOn = false, rightSide = RightSide.DockRail)
        assertTrue(key(sc, KeyEvent.KEYCODE_R, ctrl = true))
        assertEquals(FocusDockRouting.NoDock.Off.message, ShadowToast.getTextOfLatestToast())
        assertTrue(LauncherInput.focus.nodes().none { it.id.startsWith("dock.") })
    }

    @Test fun STR_14_onGlanceWithTheRightSideOnDockRailOpenRecentsStillRevealsTheRailWithTheDockOff() {
        val sc = start(dockOn = false, glance = true, rightSide = RightSide.DockRail)
        assertTrue(key(sc, KeyEvent.KEYCODE_R, ctrl = true))
        assertNull("no dock-off toast: Glance draws the rail for its right side", ShadowToast.getTextOfLatestToast())
        assertTrue("the rail is on the graph", LauncherInput.focus.nodes().any { it.id.startsWith("dock.") })
    }
}
