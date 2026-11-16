package com.bos.sphere.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bos.sphere.core.input.LauncherCommand
import com.bos.sphere.feature.dock.DockController
import com.bos.sphere.feature.dock.DockInput
import com.bos.sphere.feature.grid.LauncherInput
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * INP-01 and INP-05: the app-side pieces that connect the dock module to the launcher's input hub, first tested here now the
 * app module has a Robolectric setup. Nothing here is a device run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DockWiringTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @After fun reset() {
        DockInput.bridge = null
        DockInput.dialogHook = {}
        LauncherInput.focus.nodes().map { it.id }.forEach(LauncherInput.focus::unregister)
    }

    @Test @Config(sdk = [35])
    fun INP_01_theBridgeRegistersARailItemAsAFocusNodeOnTheHomeLayerAndForwardsFocusAndRemoval() {
        val b = DockInputWiring.bridge
        b.register("dock:a", 1f, 2f, 31f, 42f, FocusRequester())
        val node = LauncherInput.focus.nodes().single { it.id == "dock:a" }
        assertEquals(0, node.layer)
        assertEquals(com.bos.sphere.core.input.FocusKind.Item, node.kind)
        assertEquals(1f, node.rect.left, 0f); assertEquals(42f, node.rect.bottom, 0f)
        b.register("dock:a", 5f, 6f, 35f, 46f, FocusRequester()) // a moved item replaces its node
        assertEquals(1, LauncherInput.focus.nodes().count { it.id == "dock:a" })
        assertEquals(5f, LauncherInput.focus.nodes().single { it.id == "dock:a" }.rect.left, 0f)
        b.onFocus("dock:a", true)
        b.unregister("dock:a")
        assertTrue(LauncherInput.focus.nodes().none { it.id == "dock:a" })
    }

    @Test @Config(sdk = [35])
    fun INP_01_installPublishesTheBridgeAndHookWhileComposedAndRemovesThemWhenItLeaves() {
        var show by mutableStateOf(true)
        rule.setContent { if (show) DockInputWiring.Install() }
        rule.waitForIdle()
        assertNotNull(DockInput.bridge)
        assertTrue("the dialog hook is the app's, not the empty default", DockInput.dialogHook === DockInputWiring.dialogHook)
        show = false
        rule.waitForIdle()
        assertNull(DockInput.bridge)
    }

    @Test @Config(sdk = [35])
    fun INP_01_theDesktopSurfaceInstallsTheHookButNoBridgeAndTheHomeKeepsItsBridgeWhenTheDesktopLeaves() {
        var home by mutableStateOf(true); var desktop by mutableStateOf(true)
        rule.setContent {
            if (home) DockInputWiring.Install()
            if (desktop) DockInputWiring.Install(withBridge = false)
        }
        rule.waitForIdle()
        assertNotNull(DockInput.bridge)
        desktop = false; rule.waitForIdle()
        assertNotNull("the home still has its bridge", DockInput.bridge)
        home = false; rule.waitForIdle()
        assertNull(DockInput.bridge)
    }

    @Test @Config(sdk = [35])
    fun INP_01_aDesktopOnlyInstallLeavesTheRailOffThePhoneHomeGraph() {
        rule.setContent { DockInputWiring.Install(withBridge = false) }
        rule.waitForIdle()
        assertNull(DockInput.bridge)
    }

    @Test @Config(sdk = [35])
    fun INP_01_theDialogHookMarksTheDockDialogLayerAboveTheHomeWhileItIsComposed() {
        var open by mutableStateOf(true)
        rule.setContent { if (open) DockInputWiring.dialogHook() }
        rule.waitForIdle()
        assertTrue(LauncherInput.focus.topLayer >= DockInput.DIALOG_LAYER)
        open = false; rule.waitForIdle()
        assertTrue("the layer is released", LauncherInput.focus.topLayer < DockInput.DIALOG_LAYER)
    }

    // ---- INP-05: the home's "focus dock" branch

    @Test fun INP_05_focusDockWithAnOverlayOpenClosesItFirstThenHandsTheCommandToTheDock() {
        val order = mutableListOf<String>()
        val handled = FocusDockRouting.focusDock(LauncherCommand.FocusDock, overlayOpen = true, closeOverlays = { order += "close" }, dock = { order += "dock:${it.name}"; true })
        assertTrue(handled)
        assertEquals(listOf("close", "dock:FocusDock"), order)
    }

    @Test fun INP_05_focusDockWithNothingOpenLeavesEverythingAloneAndStillReachesTheDock() {
        val order = mutableListOf<String>()
        val handled = FocusDockRouting.focusDock(LauncherCommand.FocusDock, overlayOpen = false, closeOverlays = { order += "close" }, dock = { order += "dock"; false })
        assertFalse("the dock's answer is returned", handled)
        assertEquals(listOf("dock"), order)
    }

    @Test fun INP_05_theRealControllerKeepsTheFocusRequestPendingForTheRailThatAppearsAfterTheOverlayCloses() {
        val controller = DockController()
        var overlay = true
        FocusDockRouting.focusDock(LauncherCommand.FocusDock, overlayOpen = overlay, closeOverlays = { overlay = false }, dock = controller::handle)
        assertFalse(overlay)
        assertEquals("the request was counted", 1, controller.focusRequest)
    }
}
