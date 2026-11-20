package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.bos.sphere.core.catalog.HomeState
import com.bos.sphere.core.catalog.ItemDef
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind
import com.bos.sphere.core.catalog.WidgetSpan
import com.bos.sphere.core.catalog.WidgetStack
import com.bos.sphere.core.catalog.WidgetStacks
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOM-12 (wave 24): the remove handle of a widget and of a widget stack on the live grid in edit mode. The hosted widget is a
 * stand-in that takes every pointer event that reaches it, as an AppWidgetHostView does; the handle must still win its own touch.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetEditHandleW24Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val app = AppEntry("demo.a", ComponentName("demo.a", "demo.a.Main"), "Alpha", Process.myUserHandle()) { ColorDrawable(0xFF3355AA.toInt()) }
    private val clock = ItemKeys.widget(7)
    private val weather = ItemKeys.widget(8)
    private val notes = ItemKeys.widget(9)
    private val stack = WidgetStacks.stackKey("s1")

    private class Probe { val removed = mutableListOf<String>(); var widgetTouches = 0; var longPresses = 0 }

    private val state = HomeState(
        items = mapOf(
            clock to ItemDef(clock, ItemKind.Widget, "Clock", "demo/.Clock"),
            weather to ItemDef(weather, ItemKind.Widget, "Weather", "demo/.Weather"),
            notes to ItemDef(notes, ItemKind.Widget, "Notes", "demo/.Notes"),
            stack to ItemDef(stack, ItemKind.Widget, "Weather", WidgetStacks.encode(WidgetStack(listOf(weather, notes), 0))),
        ),
        widgets = mapOf(clock to WidgetSpan(2, 2), stack to WidgetSpan(2, 2)),
    )

    @Composable
    private fun Scene(edit: Boolean, probe: Probe) {
        val prefs = LauncherPrefs()
        val extras = HomeExtras(
            state, { if (it == app.key) app else null }, { null },
            widget = { key, m ->
                // The hosted view: consumes every event that starts on it (taps, drags, long presses).
                Box(m.background(Color(0xFF224466)).testTag("hosted.$key").pointerInput(Unit) {
                    awaitPointerEventScope { while (true) { val e = awaitPointerEvent(); e.changes.forEach { it.consume() }; probe.widgetTouches++ } }
                })
            },
            onLongPress = { probe.longPresses++ },
        )
        val pages = listOf(HomePage(keys = listOf(clock, stack, app.key), id = 1L))
        Box(Modifier.fillMaxSize()) {
            GridHome(
                pages = pages, appsByKey = mapOf(app.key to app), prefs = prefs, isLandscape = false, state = rememberGridHomeState(),
                gesturesBound = setOf(HomeGesture.PinchIn), capacityOf = { it.capacity(false, prefs) },
                onLaunch = {}, onAppLongPress = {}, onPagesChange = {}, onEmptyLongPress = {}, onGesture = {}, onClockTap = {},
                onTileLongPress = { _, _ -> }, extras = extras, editMode = edit, onRemoveItem = { probe.removed += it },
            )
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_aWidgetInEditModeHasARemoveHandleThatWinsOverTheHostedView() {
        rule.mainClock.autoAdvance = false
        val probe = Probe()
        rule.setContent { Scene(true, probe) }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithContentDescription("Remove from home: Clock", useUnmergedTree = true).assertExists().performClick()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(listOf(clock), probe.removed)
        assertEquals("the handle took the touch, not the hosted view", 0, probe.widgetTouches)
        assertEquals("no menu opened", 0, probe.longPresses)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_aTapOnTheWidgetBodyInEditModeRemovesNothing() {
        rule.mainClock.autoAdvance = false
        val probe = Probe()
        rule.setContent { Scene(true, probe) }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag("hosted.$clock", useUnmergedTree = true).performTouchInput { click(center) }
        rule.mainClock.advanceTimeBy(100)
        assertTrue("the body belongs to the widget", probe.widgetTouches > 0)
        assertTrue(probe.removed.isEmpty())
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_14_aStackInEditModeHasOneRemoveHandleForTheWholeStack() {
        rule.mainClock.autoAdvance = false
        val probe = Probe()
        rule.setContent { Scene(true, probe) }
        rule.mainClock.advanceTimeBy(500)
        assertEquals(1, rule.onAllNodesWithContentDescription("Remove from home: Weather", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithContentDescription("Remove from home: Notes", useUnmergedTree = true).fetchSemanticsNodes().size)
        rule.onNodeWithContentDescription("Remove from home: Weather", useUnmergedTree = true).performClick()
        rule.mainClock.advanceTimeBy(100)
        assertEquals("the stack key goes to the host, which removes every member", listOf(stack), probe.removed)
        // the app beside them keeps its handle too
        rule.onNodeWithContentDescription("Remove from home: Alpha", useUnmergedTree = true).assertExists()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_outsideEditModeWidgetsAndStacksHaveNoHandle() {
        rule.mainClock.autoAdvance = false
        rule.setContent { Scene(false, Probe()) }
        rule.mainClock.advanceTimeBy(500)
        assertEquals(0, rule.onAllNodesWithContentDescription("Remove from home", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size)
        rule.onNodeWithTag("hosted.$clock", useUnmergedTree = true).assertExists()
    }
}
