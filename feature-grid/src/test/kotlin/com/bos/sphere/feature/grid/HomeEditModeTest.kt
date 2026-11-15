package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithText
import com.bos.sphere.core.catalog.AppMark
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.data.HomeLayoutMath
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** HOM-12 edit mode on the live grid, HOM-06 pause chip and HOM-10 / HOM-06 marks on icons. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeEditModeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val labels = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot")
    private val apps = labels.mapIndexed { i, n ->
        AppEntry("demo.e$i", ComponentName("demo.e$i", "demo.e$i.Main"), n, Process.myUserHandle()) { ColorDrawable(0xFF3355AA.toInt() + i * 0x101010) }
    }
    private val byKey = apps.associateBy { it.key }

    private class Probe {
        var changes = 0
        var lastPages: List<HomePage> = emptyList()
        val removed = mutableListOf<String>()
        var longPresses = 0
    }

    @Composable
    private fun Scene(edit: Boolean, probe: Probe) {
        val prefs = LauncherPrefs()
        val state = rememberGridHomeState()
        val pages = HomeLayoutMath.visualPages(com.bos.sphere.core.data.HomeLayout(), apps.map { it.key }, capacityOf = { it.capacity(false, prefs) })
        Box(Modifier.fillMaxSize()) {
            GridHome(
                pages = pages, appsByKey = byKey, prefs = prefs, isLandscape = false, state = state, suggestion = null,
                gesturesBound = setOf(HomeGesture.PinchIn), capacityOf = { it.capacity(false, prefs) },
                onLaunch = {}, onAppLongPress = { probe.longPresses++ },
                onPagesChange = { probe.changes++; probe.lastPages = it },
                onEmptyLongPress = {}, onGesture = {}, onClockTap = {}, onTileLongPress = { _, _ -> },
                editMode = edit, onRemoveItem = { probe.removed += it },
            )
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_editModeDrawsARemoveHandleOnEveryAppAndTapRemovesIt() {
        rule.mainClock.autoAdvance = false
        val probe = Probe()
        rule.setContent { Scene(true, probe) }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithContentDescription("Remove from home: Charlie", useUnmergedTree = true).assertExists().performClick()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(listOf(apps[2].key), probe.removed)
        assertEquals(0, probe.longPresses) // the handle took the touch: no menu opened
        labels.forEach { rule.onNodeWithContentDescription("Remove from home: $it", useUnmergedTree = true).assertExists() }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_outsideEditModeThereAreNoHandles() {
        rule.mainClock.autoAdvance = false
        rule.setContent { Scene(false, Probe()) }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithContentDescription("Remove from home: Alpha", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_inEditModeAnIconIsDraggedAtOnceWithoutALongPress() {
        rule.mainClock.autoAdvance = false
        val probe = Probe()
        rule.setContent { Scene(true, probe) }
        rule.mainClock.advanceTimeBy(500)
        val from = rule.onNodeWithContentDescription("Alpha").fetchSemanticsNode().boundsInRoot.center
        val to = rule.onNodeWithContentDescription("Foxtrot").fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput {
            down(from)
            advanceEventTime(30)
            moveTo(Offset(from.x + 40f, from.y + 40f))
            advanceEventTime(30)
            moveTo(to)
            advanceEventTime(30)
            up()
        }
        rule.mainClock.advanceTimeBy(300)
        assertTrue("a drag ends in a new arrangement", probe.changes >= 1)
        val first = probe.lastPages.first().keys
        assertNotEquals(apps[0].key, first.first())
        assertEquals(apps.map { it.key }.toSet(), probe.lastPages.flatMap { it.keys }.toSet()) // nothing lost
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_outsideEditModeTheSameQuickDragRearrangesNothing() {
        rule.mainClock.autoAdvance = false
        val probe = Probe()
        rule.setContent { Scene(false, probe) }
        rule.mainClock.advanceTimeBy(500)
        val from = rule.onNodeWithContentDescription("Alpha").fetchSemanticsNode().boundsInRoot.center
        val to = rule.onNodeWithContentDescription("Foxtrot").fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput {
            down(from); advanceEventTime(30); moveTo(Offset(from.x + 40f, from.y + 40f)); advanceEventTime(30); moveTo(to); advanceEventTime(30); up()
        }
        rule.mainClock.advanceTimeBy(300)
        assertEquals(0, probe.changes) // only a long press picks an item up outside edit mode
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_06_pauseChipSaysResumeWhilePausedAndTogglesOnTap() {
        // a plain scene without endless animations: the clock may run on its own
        var toggles = 0
        val paused = mutableStateOf<Boolean?>(true)
        rule.setContent { WorkPauseChip(paused.value, onToggle = { toggles++ }) }
        rule.onNodeWithText("Resume work apps").performClick()
        assertEquals(1, toggles)
        rule.runOnUiThread { paused.value = false }
        rule.waitForIdle()
        rule.onNodeWithText("Pause work apps").assertExists()
        rule.runOnUiThread { paused.value = null }
        rule.waitForIdle()
        rule.onNodeWithText("Pause work apps").assertDoesNotExist()
        rule.onNodeWithText("Resume work apps").assertDoesNotExist()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_10_archivedAndPausedMarksAreDescribed() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            androidx.compose.foundation.layout.Column {
                AppMarkGlyph(AppMark.Archived)
                AppMarkGlyph(AppMark.ProfilePaused)
                AppMarkGlyph(AppMark.Suspended)
                AppMarkGlyph(AppMark.None)
            }
        }
        rule.onNodeWithContentDescription("Archived").assertExists()
        rule.onNodeWithContentDescription("Paused").assertExists()
        rule.onNodeWithContentDescription("Suspended").assertExists()
    }
}
