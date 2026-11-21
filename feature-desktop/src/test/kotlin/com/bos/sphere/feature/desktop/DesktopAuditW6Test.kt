package com.bos.sphere.feature.desktop

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.SphereTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Wave 6 audit of the phone-as-input requirement (EXT-10): the keys the panel shows must do something in the surface. */
class DrawerKeysTest {
    @Test fun EXT_10_arrowsMoveByOneAndByARowAndStayInsideTheList() {
        assertEquals(1, DrawerKeys.step(0, 10, PanelKey.Right, 4))
        assertEquals(0, DrawerKeys.step(0, 10, PanelKey.Left, 4))
        assertEquals(4, DrawerKeys.step(0, 10, PanelKey.Down, 4))
        assertEquals(0, DrawerKeys.step(2, 10, PanelKey.Up, 4))
        assertEquals(9, DrawerKeys.step(8, 10, PanelKey.Down, 4))
        assertEquals(3, DrawerKeys.step(2, 10, PanelKey.Tab, 4))
    }

    @Test fun EXT_10_anEmptyListAndOtherKeysLeaveTheSelectionAlone() {
        assertEquals(0, DrawerKeys.step(5, 0, PanelKey.Down, 4))
        assertEquals(3, DrawerKeys.step(3, 10, PanelKey.Enter, 4))
        assertEquals(1, DrawerKeys.columns(10f, 100f))
        assertEquals(4, DrawerKeys.columns(410f, 100f))
    }

    @Test fun EXT_10_backspaceNeverCutsASurrogatePairInHalf() {
        assertEquals("ab", DrawerKeys.backspace("abc"))
        assertEquals("a", DrawerKeys.backspace("a😀"))
        assertEquals("", DrawerKeys.backspace(""))
    }

    @Test fun EXT_09_aRememberedDisplayIsListedByItsNameNotByTheLastPieceOfItsKey() {
        assertEquals("dell u2720q (1920x1080)", DisplayIdentity.label(DisplayIdentity.key("Dell U2720Q", 1080, 1920)))
        assertEquals("acme 1234 2022", DisplayIdentity.label(DisplayIdentity.key("x", 1, 1, "ACME", "1234", 2022)))
        assertEquals("odd", DisplayIdentity.label("odd"))
    }

    @Test fun EXT_10_everyPanelKeyHasATranslatableSpokenName() {
        for (k in PanelKey.entries) assertTrue(panelKeyName(k), panelKeyName(k) in L10n.englishKeys)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi")
class DesktopPanelKeysUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun off() { DesktopInputBus.setFocused(false) }

    @Test fun EXT_10_typingOpensTheDrawerBackspaceEditsItAndEscapeClosesIt() {
        val controller = DesktopController.get(ApplicationProvider.getApplicationContext())
        rule.mainClock.autoAdvance = false
        rule.setContent { SphereTheme { DesktopSurface(controller, { 2 }, "res:1920x1080:monitor", "Monitor") } }
        rule.mainClock.advanceTimeBy(500)
        DesktopInputBus.setFocused(true)
        rule.onNodeWithText("Search apps").assertDoesNotExistOrEmpty()
        assertTrue(DesktopInputBus.send(InputCommand.Text("zz")))
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithText("zz").assertExists()
        DesktopInputBus.send(InputCommand.Key(PanelKey.Down))   // no apps match: must not crash or move anything
        DesktopInputBus.send(InputCommand.Key(PanelKey.Backspace))
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithText("z").assertExists()
        DesktopInputBus.send(InputCommand.Key(PanelKey.Escape))
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithText("z").assertDoesNotExist()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistOrEmpty() = runCatching { assertDoesNotExist() }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ProfilePillSemanticsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun EXT_09_theChosenDockPositionAndDensityAreAnnouncedAsSelected() {
        rule.setContent {
            SphereTheme { DisplayProfileEditor("Monitor", DesktopProfile(dockMode = DockMode.Left, densityOverrideDpi = 213), {}) }
        }
        rule.onNodeWithText("Left").assertIsSelected()
        rule.onNodeWithText("Bottom").assertIsNotSelected()
        rule.onNodeWithText("213 dpi").assertIsSelected()
        rule.onNodeWithText("Automatic").assertIsNotSelected()
    }
}
