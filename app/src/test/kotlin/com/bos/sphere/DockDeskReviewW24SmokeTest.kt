package com.bos.sphere

import android.view.KeyEvent
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.bos.sphere.core.data.RightSide
import com.bos.sphere.feature.dock.DockItemRef
import com.bos.sphere.feature.dock.WorkspaceRepository
import com.bos.sphere.feature.grid.LauncherInput
import org.junit.After
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
 * Wave 24 second review (dockDesk): the two ways into the rail ("Focus the dock", Ctrl+H, and "Open recents" with Right side set to
 * Dock rail, Ctrl+R) must close what hides the rail, the same way. Before the review Ctrl+R left the drawer open over the rail it
 * was asked to reveal, and Ctrl+H left the Recents panel open (the rail steps aside for it), so neither focused anything visible.
 * The real [MainActivity] under Robolectric; synthesized keys, not a device run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class DockDeskReviewW24SmokeTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun freshProcess() { AppSmokeHarness.resetProcess() }

    @After fun closeApp() {
        if (scenario != null) { rule.waitForIdle(); assertFalse("a test left a global Compose snapshot write pending", Snapshot.current.hasPendingChanges()) }
        scenario?.close(); scenario = null
        AppSmokeHarness.resetProcess()
    }

    private fun start(rightSide: RightSide): ActivityScenario<MainActivity> {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        AppSmokeHarness.configure { it.copy(rightSide = rightSide) }
        WorkspaceRepository.get(AppSmokeHarness.app).setDock { it.copy(enabled = true) }
        WorkspaceRepository.get(AppSmokeHarness.app).setPins(listOf(DockItemRef.app(AppSmokeHarness.appKey("Alpha"))))
        return AppSmokeHarness.launch().also { scenario = it; rule.waitForIdle() }
    }

    private fun key(sc: ActivityScenario<*>, code: Int, ctrl: Boolean = false): Boolean = AppSmokeHarness.press(sc, code, ctrl = ctrl).also { rule.waitForIdle() }
    private fun drawerOpen() = LauncherInput.focus.nodes().any { it.id == "puck.back" }
    private fun railFocused() = LauncherInput.focus.currentId?.startsWith("dock.") == true

    @Test fun STR_14_withRightSideDockRailOpenRecentsFromTheDrawerClosesItAndTheRailTakesFocus() {
        val sc = start(RightSide.DockRail)
        key(sc, KeyEvent.KEYCODE_D, ctrl = true)
        assertTrue(drawerOpen())
        assertTrue(key(sc, KeyEvent.KEYCODE_R, ctrl = true))
        assertFalse("the drawer hid the rail Ctrl+R was asked to reveal", drawerOpen())
        assertTrue("the rail takes focus", railFocused())
    }

    @Test fun INP_05_focusDockWithTheRecentsPanelOpenClosesItAndTheRailTakesFocus() {
        val sc = start(RightSide.RecentsPanel)
        assertTrue(key(sc, KeyEvent.KEYCODE_R, ctrl = true)) // opens the Recents panel; the rail steps aside for it
        assertTrue(key(sc, KeyEvent.KEYCODE_H, ctrl = true))
        assertTrue("the Recents panel hid the rail, so Ctrl+H focused nothing", railFocused())
    }
}
