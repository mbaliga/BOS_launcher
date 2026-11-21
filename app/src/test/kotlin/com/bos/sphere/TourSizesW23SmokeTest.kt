package com.bos.sphere

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * NFR-19, STR-01, STR-14, PAL-18, HOM-23 (wave 23 sweep): the same short tour at three window sizes the other tours do not use: a tiny
 * round-ish window (240 x 320 dp), the 280 x 653 dp cover screen of a folded phone, and a 1280 x 800 dp tablet. Each step must keep the
 * activity alive with at most one surface open, so a zero or one page carousel, a clamp on an empty range or a division by a small size
 * would show up here as a crash. Robolectric draws no pixels of a real GPU: nothing here is a device check.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TourSizesW23SmokeTest : TourBase() {
    private fun tour() {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        AppSmokeHarness.configureFresh { it }
        launch()
        check("start", Surface.Home)
        key(KeyEvent.KEYCODE_N, ctrl = true); check("launchpad", Surface.Launchpad)
        back(); check("launchpad closed", Surface.Home)
        key(KeyEvent.KEYCODE_R, ctrl = true); check("recents", Surface.Recents)
        key(ESC); check("recents closed", Surface.Home)
        key(KeyEvent.KEYCODE_B); check("radar", Surface.Radar)
        key(KeyEvent.KEYCODE_R); key(KeyEvent.KEYCODE_A); check("radar typing", Surface.Radar)
        back(); check("radar closed", Surface.Home)
        key(KeyEvent.KEYCODE_COMMA, ctrl = true); check("settings", Surface.Settings)
        back(); check("settings closed", Surface.Home)
        longPressAt(0.5f, 0.3f); check("quick sheet")
        closeSheet(); settle(); check("quick sheet closed", Surface.Home)
        rotateAndCheck("home")
        assertFalse("no surface is left open", open().any { it != Surface.Home })
    }

    @Test @Config(sdk = [34], qualifiers = "w240dp-h320dp-hdpi") fun NFR_19_tourAtATinyWindow() = tour()
    @Test @Config(sdk = [34], qualifiers = "w280dp-h653dp-xhdpi") fun NFR_19_tourOnAFoldedCoverScreen() = tour()
    @Test @Config(sdk = [34], qualifiers = "w1280dp-h800dp-mdpi") fun NFR_19_tourOnALargeTablet() = tour()
}
