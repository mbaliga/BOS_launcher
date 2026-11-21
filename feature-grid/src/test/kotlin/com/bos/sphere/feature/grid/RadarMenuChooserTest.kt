package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.input.MenuAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * PAL-08: the real [SearchScreen] is driven from a result's action list (opened with the keyboard) into Radar's chooser, and
 * choosing a row runs its action. The displays are fed through the test seam (no second display exists here, so this is not a
 * device run of "Open on display").
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RadarMenuChooserTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun english() { com.bos.sphere.core.design.L10n.setLanguage("en") }

    private val mail = AppEntry("demo.mail", ComponentName("demo.mail", "demo.mail.Main"), "Mail", Process.myUserHandle()) { null }

    private val ranActions = mutableListOf<MenuAction>()
    private val asked = mutableListOf<MenuAction>()
    private val chosen = mutableListOf<String>()
    private var launched = 0

    private fun open() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SearchScreen(
                apps = listOf(mail), recent = emptyList(), prefs = LauncherPrefs(searchMotion = false), contacts = null,
                onLaunch = { launched++ }, onLongPressApp = {}, onOpenIntent = {}, onRequestContactsPermission = {}, onDismiss = {},
                hub = null, onOpenHit = null, onRunAction = {}, onRequestFilePermission = {}, sounds = null, initialQuery = "Mail",
                onRunAppAction = { _, a -> ranActions += a; false },
                onAppActionChoices = { _, a ->
                    asked += a
                    when (a) {
                        MenuAction.OpenOnDisplay -> listOf(RadarChoice("Open on Desk") { chosen += "desk" }, RadarChoice("Open on TV") { chosen += "tv" })
                        MenuAction.AddToWorkspace -> listOf(RadarChoice("Work") { chosen += "work" }, RadarChoice("Play (added)") { chosen += "play" })
                        else -> null
                    }
                },
                otherDisplaysOverride = 2,
            )
        }
        rule.mainClock.advanceTimeBy(1000)
        // Down selects the first result, Tab opens its action list.
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.mainClock.advanceTimeBy(200)
        rule.onRoot().performKeyInput { pressKey(Key.Tab) }
        rule.mainClock.advanceTimeBy(300)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_08_openOnDisplayWithSeveralDisplaysOpensTheChooserAndAChoiceRuns() {
        open()
        rule.onNodeWithContentDescription("Open on another display").performClick()
        rule.mainClock.advanceTimeBy(300)
        assertEquals("the host is asked first, then the chooser opens", listOf(MenuAction.OpenOnDisplay), ranActions)
        assertEquals(listOf(MenuAction.OpenOnDisplay), asked)
        rule.onNodeWithTag("radar.picker").assertExists()
        assertTrue("nothing runs before a row is chosen", chosen.isEmpty())
        rule.onNodeWithTag("radar.picker.1").performClick()
        rule.mainClock.advanceTimeBy(300)
        assertEquals(listOf("tv"), chosen)
        rule.onNodeWithTag("radar.picker").assertDoesNotExist()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_08_addToWorkspaceOpensTheChooserAndChoosingRunsTheAction() {
        open()
        rule.onNodeWithContentDescription("Add to workspace").performClick()
        rule.mainClock.advanceTimeBy(300)
        assertEquals(listOf(MenuAction.AddToWorkspace), asked)
        rule.onNodeWithTag("radar.picker").assertExists()
        rule.onNodeWithTag("radar.picker.0").performClick()
        rule.mainClock.advanceTimeBy(300)
        assertEquals(listOf("work"), chosen)
        rule.onNodeWithTag("radar.picker").assertDoesNotExist()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_08_cancelClosesTheChooserWithoutRunningAnything() {
        open()
        rule.onNodeWithContentDescription("Add to workspace").performClick()
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithTag("radar.picker.cancel").performClick()
        rule.mainClock.advanceTimeBy(300)
        assertTrue(chosen.isEmpty())
        rule.onNodeWithTag("radar.picker").assertDoesNotExist()
    }
}
