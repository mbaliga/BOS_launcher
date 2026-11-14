package com.bos.sphere.feature.grid

import android.view.KeyEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** PAL-01: the activity's key dispatch reaches the home's type-to-search hook only when no surface is open and no shortcut claims the key. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TypeToSearchHookTest {
    @After fun reset() { LauncherInput.typeHook = null; LauncherInput.overlayOpen = false }

    private fun key(code: Int, meta: Int = 0) = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta)

    @Test fun PAL_01_aPrintableKeyOnHomeIsHandedToTheHook() {
        val seen = ArrayList<Int>()
        LauncherInput.typeHook = { e -> seen += e.unicodeChar; true }
        LauncherInput.overlayOpen = false
        assertTrue(LauncherInput.onKeyEvent(key(KeyEvent.KEYCODE_G)))
        assertEquals(listOf('g'.code), seen)
    }

    @Test fun PAL_01_noHookCallWhileASurfaceIsOpen() {
        var calls = 0
        LauncherInput.typeHook = { calls++; true }
        LauncherInput.overlayOpen = true
        assertFalse(LauncherInput.onKeyEvent(key(KeyEvent.KEYCODE_G)))
        assertEquals(0, calls)
    }
}
