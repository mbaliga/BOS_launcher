package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import com.bos.sphere.core.input.KeyChord
import com.bos.sphere.core.input.LauncherCommand
import com.bos.sphere.core.input.ShortcutPersistence
import com.bos.sphere.core.input.ShortcutRegistry
import com.bos.sphere.core.input.ShortcutStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * CFG-01: the rebind screen (the shortcut sheet) is drawn, a rebind is driven with real key events, and the registry and the
 * saved slot are checked. Nothing here is a device run.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutRebindUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun english() { com.bos.sphere.core.design.L10n.setLanguage("en") }

    private class Slot : ShortcutStorage { var t: String? = null; override fun read() = t; override fun write(text: String?) { t = text } }

    private val registry = ShortcutRegistry()
    private val slot = Slot()
    private var saves = 0

    private fun open() {
        rule.mainClock.autoAdvance = false
        rule.setContent { ShortcutSheet(registry, onDismiss = {}, reload = { ShortcutPersistence.load(registry, slot) }, save = { saves++; ShortcutPersistence.save(registry, slot) }) }
        rule.mainClock.advanceTimeBy(800)
    }

    private fun capture(command: LauncherCommand, key: Key, ctrl: Boolean = false, alt: Boolean = false) {
        rule.onNodeWithTag("shortcut.change.${command.name}").performClick()
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("Press the new keys", substring = true).performKeyInput {
            fun press() = pressKey(key)
            when {
                ctrl && alt -> withKeyDown(Key.CtrlLeft) { withKeyDown(Key.AltLeft) { press() } }
                ctrl -> withKeyDown(Key.CtrlLeft) { press() }
                alt -> withKeyDown(Key.AltLeft) { press() }
                else -> press()
            }
        }
        rule.mainClock.advanceTimeBy(300)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun CFG_01_everyRegisteredCommandIsListedWithItsChord() {
        open()
        for (c in LauncherCommand.entries) rule.onNodeWithTag("shortcut.binding.${c.name}").assertExists()
        rule.onNodeWithTag("shortcut.binding.OpenDrawer").assertTextContains("Ctrl + D")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun CFG_01_changeCapturesTheNextChordAndTheRegistryAndSlotFollow() {
        open()
        capture(LauncherCommand.OpenDrawer, Key.J, ctrl = true)
        assertEquals(KeyChord(38, ctrl = true), registry.binding(LauncherCommand.OpenDrawer))
        rule.onNodeWithTag("shortcut.binding.OpenDrawer").assertTextContains("Ctrl + J")
        assertTrue(saves >= 1)
        val second = ShortcutRegistry(); ShortcutPersistence.load(second, slot)
        assertEquals(KeyChord(38, ctrl = true), second.binding(LauncherCommand.OpenDrawer))
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun CFG_01_aClashOffersSwapOrCancelAndNothingIsStolenFirst() {
        open()
        capture(LauncherCommand.OpenDrawer, Key.Slash, ctrl = true) // Radar's chord
        assertEquals(KeyChord(32, ctrl = true), registry.binding(LauncherCommand.OpenDrawer))
        assertEquals(KeyChord(76, ctrl = true), registry.binding(LauncherCommand.OpenPalette))
        assertEquals(0, saves)
        rule.onNodeWithTag("shortcut.swap").assertExists()
        rule.onNodeWithTag("shortcut.cancel").performClick()
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithTag("shortcut.swap").assertDoesNotExist()
        assertEquals(KeyChord(32, ctrl = true), registry.binding(LauncherCommand.OpenDrawer))
        assertEquals(KeyChord(76, ctrl = true), registry.binding(LauncherCommand.OpenPalette))
        // now the same clash again, and swap
        capture(LauncherCommand.OpenDrawer, Key.Slash, ctrl = true)
        rule.onNodeWithTag("shortcut.swap").performClick()
        rule.mainClock.advanceTimeBy(200)
        assertEquals(KeyChord(76, ctrl = true), registry.binding(LauncherCommand.OpenDrawer))
        assertEquals(KeyChord(32, ctrl = true), registry.binding(LauncherCommand.OpenPalette))
        val second = ShortcutRegistry(); ShortcutPersistence.load(second, slot)
        assertEquals(registry.all(), second.all())
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun CFG_01_aSystemChordIsRefusedAndNothingChangesOrSaves() {
        open()
        capture(LauncherCommand.OpenDrawer, Key.Tab, alt = true)
        assertEquals(KeyChord(32, ctrl = true), registry.binding(LauncherCommand.OpenDrawer))
        assertEquals(0, saves)
        assertNull(slot.t)
        rule.onNodeWithText("The system keeps this key combination", substring = true).assertExists()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun CFG_01_resetOneAndResetAllRestoreDefaultsAndClearTheSlot() {
        open()
        capture(LauncherCommand.OpenDrawer, Key.J, ctrl = true)
        capture(LauncherCommand.EditMode, Key.K, ctrl = true)
        assertNotNull(slot.t)
        rule.onNodeWithTag("shortcut.reset.OpenDrawer").performClick()
        rule.mainClock.advanceTimeBy(200)
        assertEquals(KeyChord(32, ctrl = true), registry.binding(LauncherCommand.OpenDrawer))
        assertEquals(KeyChord(39, ctrl = true), registry.binding(LauncherCommand.EditMode))
        rule.onNodeWithTag("shortcut.resetAll").performClick()
        rule.mainClock.advanceTimeBy(200)
        assertEquals(ShortcutRegistry().all(), registry.all())
        assertNull("no overrides leave the exported slot empty", slot.t)
    }
}
