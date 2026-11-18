package com.bos.sphere

import android.view.KeyEvent
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.RightSide
import com.bos.sphere.feature.dock.DockItemRef
import com.bos.sphere.feature.dock.WorkspaceRepository
import com.bos.sphere.feature.grid.LauncherInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Wave 26 (audit of wave 25, uninstall): after the system's uninstall dialog is confirmed the only signal Liftoff gets is the launcher-apps
 * `onPackageRemoved` callback. The drawer, the dock, Radar and the home layout must all drop the app by themselves (HOM-10, HOM-22).
 * Robolectric's launcher-apps shadow plays the system; not a device run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class PackageRemovedW26SmokeTest : TourBase() {
    private val alpha get() = AppSmokeHarness.appKey("Alpha")
    private fun drawerHasAlpha() = LauncherInput.focus.nodes().any { it.id == "drawer.$alpha" }
    private fun dockHasAlpha() = LauncherInput.focus.nodes().any { it.id.startsWith("dock.") && it.id.contains(alpha) } || tagShown("dock.item.$alpha")

    @Test fun UNI_05_aRemovedPackageLeavesTheDrawerTheDockRadarAndTheHomeLayout() {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        AppSmokeHarness.configure { it.copy(rightSide = RightSide.DockRail) }
        WorkspaceRepository.get(AppSmokeHarness.app).setDock { it.copy(enabled = true) }
        WorkspaceRepository.get(AppSmokeHarness.app).setPins(listOf(DockItemRef.app(alpha), DockItemRef.app(AppSmokeHarness.appKey("Bravo"))))
        launch()
        val stores = LauncherStores.get(AppSmokeHarness.app)
        val homeBefore = alpha in stores.layout.layout.value.toString()

        key(KeyEvent.KEYCODE_H, ctrl = true)
        assertTrue("the dock draws Alpha before the uninstall: ${LauncherInput.focus.nodes().map { it.id }}", dockHasAlpha())
        key(KeyEvent.KEYCODE_D, ctrl = true); check("drawer", Surface.Drawer)
        assertTrue("the drawer lists Alpha before the uninstall", drawerHasAlpha())

        AppSmokeHarness.removeApp("Alpha")
        waitFor("the drawer drops the removed app", 8000) { !drawerHasAlpha() }
        assertTrue("another app stays", LauncherInput.focus.nodes().any { it.id == "drawer.${AppSmokeHarness.appKey("Bravo")}" })
        back()

        // the home layout forgets it as well
        val homeAfter = alpha in stores.layout.layout.value.toString()
        assertFalse("the home layout still holds the removed app (before: $homeBefore)", homeAfter)

        // Radar no longer finds it
        key(KeyEvent.KEYCODE_B); check("radar", Surface.Radar)
        key(KeyEvent.KEYCODE_A); key(KeyEvent.KEYCODE_L); key(KeyEvent.KEYCODE_P)
        assertFalse("Radar still offers the removed app", textShown("Alpha"))
        back()

        // the dock keeps the pin for the HOM-10 grace period but marks it unavailable (dimmed, not launchable): the app's own label is gone from the rail
        key(KeyEvent.KEYCODE_H, ctrl = true)
        waitFor("the dock marks the removed app as unavailable", 20000) { descShown("App unavailable", substring = true) || textShown("App unavailable") }
        assertFalse("the dock still offers the removed app by its name", descShown("Alpha", substring = true))
    }
}
