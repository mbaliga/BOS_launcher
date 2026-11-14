package com.bos.sphere.ui

import com.bos.sphere.core.input.DefaultShortcuts
import com.bos.sphere.core.input.KeyChord
import com.bos.sphere.core.input.LauncherCommand
import com.bos.sphere.core.input.ShortcutRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RevealMathTest {
    @Test fun STR_01_partialDragPeeksAndSpringsBack() {
        // 20% of the way, slow: closed again.
        val f = RevealMath.fraction(0f, 200f, 1000f)
        assertEquals(0.2f, f, 1e-4f)
        assertFalse(RevealMath.settle(f, 100f, wasOpen = false))
        // Exactly at the threshold still springs back; just past it opens.
        assertFalse(RevealMath.settle(RevealMath.OPEN_AT, 0f, wasOpen = false))
        assertTrue(RevealMath.settle(RevealMath.OPEN_AT + 0.01f, 0f, wasOpen = false))
    }

    @Test fun STR_01_flingDecidesWhateverTheDistance() {
        assertTrue(RevealMath.settle(0.05f, 2000f, wasOpen = false))
        assertFalse(RevealMath.settle(0.9f, -2000f, wasOpen = true))
    }

    @Test fun STR_01_openStaysOpenUntilPulledBackFar() {
        assertTrue(RevealMath.settle(0.8f, 0f, wasOpen = true))
        assertFalse(RevealMath.settle(0.5f, 0f, wasOpen = true))
    }

    @Test fun STR_01_fractionIsClampedAndFollowsFinger() {
        assertEquals(1f, RevealMath.fraction(0f, 5000f, 1000f), 0f)
        assertEquals(0f, RevealMath.fraction(1f, -5000f, 1000f), 0f)
        assertEquals(0.5f, RevealMath.fraction(1f, -500f, 1000f), 1e-4f)
        assertEquals(0.3f, RevealMath.fraction(0.3f, 100f, 0f), 0f)
    }

    @Test fun STR_01_onlyTheLeftEdgeStartsTheReveal() {
        assertTrue(RevealMath.startsInEdge(3f, 28f))
        assertFalse(RevealMath.startsInEdge(200f, 28f))
    }

    @Test fun STR_01_landscapeKeepsHomePartlyVisiblePortraitTakesTheScreen() {
        val land = RevealLayout.forWindow(900f, 400f)
        assertTrue(land.landscape); assertTrue(land.homeStripFraction > 0f); assertTrue(land.slideFraction < 1f)
        assertEquals(1f, land.slideFraction + land.homeStripFraction, 1e-4f)
        val port = RevealLayout.forWindow(400f, 900f)
        assertFalse(port.landscape); assertEquals(1f, port.slideFraction, 0f); assertEquals(0f, port.homeStripFraction, 0f)
    }

    @Test fun STR_01_shortcutOpensLaunchpadAndFollowsRebinding() {
        val reg = ShortcutRegistry()
        val chord = reg.binding(LauncherCommand.OpenLaunchpad)!!
        assertTrue(LaunchpadShortcuts.opensLaunchpad(reg, chord))
        val mine = KeyChord(keyCode = 41, alt = true)
        assertEquals(ShortcutRegistry.Rebind.Ok, reg.rebind(LauncherCommand.OpenLaunchpad, mine))
        assertTrue(LaunchpadShortcuts.opensLaunchpad(reg, mine))
        assertFalse(LaunchpadShortcuts.opensLaunchpad(reg, chord))
        assertTrue(DefaultShortcuts.all.any { it.command == LauncherCommand.OpenLaunchpad && it.default != null })
    }
}
