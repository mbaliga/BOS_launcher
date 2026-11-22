@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.LayoutDirection
import com.bos.sphere.core.data.ClockFormat
import com.bos.sphere.core.data.ClockPlacement
import com.bos.sphere.core.data.ClockPlacement.clockPos
import com.bos.sphere.core.data.ClockPlacement.withClockPos
import com.bos.sphere.core.data.ClockSize
import com.bos.sphere.core.data.ClockStyle
import com.bos.sphere.core.data.HomeSurface
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.SurfaceScroll
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.design.ThemeVariant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Wave 50 (glanceClockEdit, HOM-23): long press on the clock edits it in place, long press on empty space is the config options. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GlanceClockEditW50Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @After fun reset() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
        ReducedMotionTesting.force(null)
        L10n.setLanguage("")
        runCatching { LauncherInput.focus.nodes().map { it.id }.forEach(LauncherInput.focus::unregister) }
    }

    private val cur = mutableStateOf(LauncherPrefs(clockStyle = ClockStyle.BigDigital))
    private val editing = mutableStateOf(false)
    private var taps = 0; private var awake = 0; private var emptyLong = 0; private var edits = 0

    private fun show(prefs: LauncherPrefs = LauncherPrefs(clockStyle = ClockStyle.BigDigital), dir: LayoutDirection = LayoutDirection.Ltr) {
        Snapshot.withMutableSnapshot { cur.value = prefs }
        val spec = HomeGestureSpec(HomeSurface(SurfaceScroll.Horizontal, paged = true, hasDensity = false, leftPageOn = false), emptySet(), {}, onEmptyLongPress = { emptyLong++ })
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides dir) {
                Box(Modifier.fillMaxSize()) {
                    GlanceSurface(
                        cur.value, true, Modifier.fillMaxSize(), indicator = false, onAwake = { awake++ }, gestures = spec, dark = true, onClockTap = { taps++ },
                        onPrefsEdit = { f -> cur.value = f(cur.value); edits++ }, onEditingChange = { editing.value = it },
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun clockBounds() = rule.onNodeWithTag("glance.clock", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun panel() = rule.onAllNodesWithTag(GlanceClockEditTags.PANEL).fetchSemanticsNodes().size
    private fun longPressClock() { val c = clockBounds().center; rule.onNodeWithTag("glance.surface").performTouchInput { longClick(Offset(c.x, c.y)) }; rule.waitForIdle() }
    private fun drag(from: Offset, dx: Float, dy: Float) {
        rule.onNodeWithTag("glance.surface").performTouchInput {
            down(from); advanceEventTime(30)
            for (i in 1..8) { moveTo(Offset(from.x + dx * i / 8f, from.y + dy * i / 8f)); advanceEventTime(16) }
            up()
        }
        rule.waitForIdle()
    }
    private fun portraitPos() = cur.value.clockPos(false)

    @Test fun HOM_23_longPressOnTheClockOpensTheEditModeNotTheMenu() {
        show(); assertEquals(0, panel())
        longPressClock()
        assertEquals("the edit panel is up", 1, panel()); assertTrue(editing.value)
        assertEquals("not the launcher config options", 0, emptyLong); assertEquals("not the clock app", 0, taps)
        rule.onNodeWithTag(GlanceClockEditTags.DONE).assertExists(); rule.onNodeWithTag(GlanceClockEditTags.CANCEL).assertExists(); rule.onNodeWithTag(GlanceClockEditTags.RESET).assertExists()
    }

    @Test fun HOM_23_longPressOnEmptySpaceOpensTheLauncherConfigOptions() {
        show()
        val r = rule.onNodeWithTag("glance.surface").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("glance.surface").performTouchInput { longClick(Offset(r.width * 0.5f, r.height * 0.85f)) }; rule.waitForIdle()
        assertEquals(1, emptyLong); assertEquals("no clock edit", 0, panel()); assertFalse(editing.value)
    }

    @Test fun HOM_23_aTapOnTheClockStillOpensTheClockApp() {
        show(); val c = clockBounds().center
        rule.onNodeWithTag("glance.surface").performTouchInput { click(Offset(c.x, c.y)) }; rule.waitForIdle()
        assertEquals(1, taps); assertEquals(0, panel())
    }

    @Test fun HOM_23_dragMovesTheClockAnywhereAndTheFractionsAreStored() {
        show(); val before = clockBounds(); longPressClock()
        val c = before.center
        drag(Offset(c.x, c.y), -150f, 400f)
        val after = clockBounds()
        assertTrue("moved left ${before.left} -> ${after.left}", after.left < before.left - 100f); assertTrue("moved down ${before.top} -> ${after.top}", after.top > before.top + 100f)
        val p = portraitPos()
        assertTrue("stored x ${p.x}", p.x < 0.5f); assertTrue("stored y ${p.y}", p.y > ClockPlacement.DEFAULT_Y)
        assertEquals("the other orientation is untouched", ClockPlacement.Default, cur.value.clockPos(true))
        assertTrue("the drag is not a tap and does not wake", taps == 0 && awake == 0)
    }

    @Test fun HOM_23_aDragNeverLeavesTheVisibleArea() {
        show(); longPressClock(); val c = clockBounds().center
        drag(Offset(c.x, c.y), 5000f, 5000f)
        val root = rule.onNodeWithTag("glance.surface").fetchSemanticsNode().boundsInRoot; val b = clockBounds()
        assertTrue("inside: $b of $root", b.right <= root.right + 1f && b.bottom <= root.bottom + 1f && b.left >= -1f && b.top >= -1f)
    }

    @Test fun HOM_23_aDragThatStartsOffTheClockDoesNothing() {
        show(); longPressClock(); val before = portraitPos(); val root = rule.onNodeWithTag("glance.surface").fetchSemanticsNode().boundsInRoot
        drag(Offset(root.width * 0.5f, root.height * 0.55f), 200f, 100f)
        assertEquals(before, portraitPos())
    }

    @Test fun HOM_23_editModeDoesNotWakeNorOpenTheClockApp() {
        show(); longPressClock(); val c = clockBounds().center; val root = rule.onNodeWithTag("glance.surface").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("glance.surface").performTouchInput { click(Offset(c.x, c.y)); click(Offset(root.width * 0.5f, root.height * 0.55f)) }; rule.waitForIdle()
        assertEquals(0, taps); assertEquals(0, awake); assertEquals(0, emptyLong)
    }

    @Test fun HOM_23_optionsApplyLiveWhileEditing() {
        show(); longPressClock()
        rule.onNodeWithContentDescription("Small").performScrollTo().performClick(); assertEquals(ClockSize.Small, cur.value.glanceClockSize)
        rule.onNodeWithTag(GlanceClockEditTags.DATE).performScrollTo().performClick(); assertTrue(cur.value.glanceClockDate)
        rule.onNodeWithContentDescription("Accent").performScrollTo().performClick(); assertTrue(cur.value.glanceClockAccent)
        rule.onNodeWithContentDescription("24-hour").performScrollTo().performClick(); assertEquals(ClockFormat.Hour24, cur.value.clockFormat)
        rule.onNodeWithTag(GlanceClockEditTags.STYLE).performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag(OneUiDialogTags.option(2)).performClick(); rule.waitForIdle()
        assertEquals(ClockStyle.AnalogClassic, cur.value.clockStyle)
        assertEquals("the clock shows the new style at once", 1, rule.onAllNodesWithTag("glance.clock.analogClassic", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals("the format row is for the digital styles only", 0, rule.onAllNodesWithTag("oneui.segment.2", useUnmergedTree = true).fetchSemanticsNodes().filter { false }.size)
        assertTrue("still editing", panel() == 1)
    }

    @Test fun HOM_23_cancelRestoresThePreviousPositionAndSettings() {
        val start = LauncherPrefs(clockStyle = ClockStyle.BigDigital).withClockPos(false, ClockPlacement.Pos(0.4f, 0.3f))
        show(start); longPressClock(); val c = clockBounds().center
        drag(Offset(c.x, c.y), 120f, 200f)
        rule.onNodeWithContentDescription("Small").performScrollTo().performClick(); rule.onNodeWithTag(GlanceClockEditTags.DATE).performScrollTo().performClick()
        assertNotEquals(start, cur.value)
        rule.onNodeWithTag(GlanceClockEditTags.CANCEL).performClick(); rule.waitForIdle()
        assertEquals(start, cur.value); assertEquals(0, panel()); assertFalse(editing.value)
    }

    @Test fun HOM_23_backIsCancel() {
        show(); longPressClock(); val c = clockBounds().center
        drag(Offset(c.x, c.y), 0f, 300f); assertTrue(portraitPos().y > ClockPlacement.DEFAULT_Y)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle()
        assertEquals(ClockPlacement.Default, portraitPos()); assertEquals(0, panel())
    }

    @Test fun HOM_23_doneKeepsTheChanges() {
        show(); longPressClock(); val c = clockBounds().center
        drag(Offset(c.x, c.y), 0f, 300f); rule.onNodeWithContentDescription("Small").performScrollTo().performClick()
        val edited = cur.value
        rule.onNodeWithTag(GlanceClockEditTags.DONE).performClick(); rule.waitForIdle()
        assertEquals(edited, cur.value); assertEquals(0, panel()); assertTrue(portraitPos().y > ClockPlacement.DEFAULT_Y)
        // and a tap works again
        val b = clockBounds().center
        rule.onNodeWithTag("glance.surface").performTouchInput { click(Offset(b.x, b.y)) }; rule.waitForIdle(); assertEquals(1, taps)
    }

    @Test fun HOM_23_resetPositionBringsTheFactoryPlaceBack() {
        show(LauncherPrefs(clockStyle = ClockStyle.BigDigital).withClockPos(false, ClockPlacement.Pos(0.3f, 0.6f)))
        longPressClock(); rule.onNodeWithTag(GlanceClockEditTags.RESET).performClick(); rule.waitForIdle()
        assertEquals(ClockPlacement.Default, portraitPos()); assertEquals("still editing", 1, panel())
    }

    @Test fun HOM_23_aStoredPositionPlacesTheClockAndTheFactoryPlaceIsUnchanged() {
        show(); val factory = clockBounds()
        val root = rule.onNodeWithTag("glance.surface").fetchSemanticsNode().boundsInRoot
        assertEquals("centred", (root.left + root.right) / 2f, (factory.left + factory.right) / 2f, 2f)
        Snapshot.withMutableSnapshot { cur.value = cur.value.withClockPos(false, ClockPlacement.Pos(0.25f, 0.5f)) }; rule.waitForIdle()
        val moved = clockBounds()
        assertTrue("x $moved vs $factory", moved.center.x < factory.center.x - 20f); assertTrue("y $moved vs $factory", moved.top > factory.top + 20f)
    }

    @Test fun HOM_23_talkBackCanEditAndMoveAndResizeTheClockWithoutDragging() {
        show()
        val actions = rule.onNodeWithTag("glance.clock", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Edit clock", "Move clock up", "Move clock down", "Move clock left", "Move clock right", "Larger clock", "Smaller clock"), actions.map { it.label })
        fun run(label: String) { rule.runOnUiThread { rule.onNodeWithTag("glance.clock", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }.action() }; rule.waitForIdle() }
        val y0 = portraitPos().y; val x0 = portraitPos().x
        run("Move clock down"); assertEquals(y0 + ClockPlacement.STEP, portraitPos().y, 1e-3f)
        run("Move clock left"); assertEquals(x0 - ClockPlacement.STEP, portraitPos().x, 1e-3f)
        run("Move clock up"); run("Move clock right"); assertEquals(y0, portraitPos().y, 1e-3f); assertEquals(x0, portraitPos().x, 1e-3f)
        run("Smaller clock"); assertEquals(ClockSize.Medium, cur.value.glanceClockSize); run("Smaller clock"); run("Smaller clock"); assertEquals(ClockSize.Small, cur.value.glanceClockSize)
        run("Larger clock"); assertEquals(ClockSize.Medium, cur.value.glanceClockSize)
        assertEquals("moving by action needs no edit mode", 0, panel())
        run("Edit clock"); assertEquals(1, panel())
    }

    @Test fun HOM_23_arrowKeysMoveTheClockWhileEditing() {
        show(); val x0 = portraitPos().x; val y0 = portraitPos().y
        fun key(code: Int) = rule.runOnUiThread { LauncherInput.onKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code)) }.also { rule.waitForIdle() }
        key(android.view.KeyEvent.KEYCODE_DPAD_DOWN); assertEquals("arrows do nothing outside edit mode", y0, portraitPos().y, 1e-4f)
        longPressClock()
        key(android.view.KeyEvent.KEYCODE_DPAD_DOWN); assertEquals(y0 + ClockPlacement.STEP, portraitPos().y, 1e-3f)
        key(android.view.KeyEvent.KEYCODE_DPAD_RIGHT); assertEquals(x0 + ClockPlacement.STEP, portraitPos().x, 1e-3f)
        key(android.view.KeyEvent.KEYCODE_ESCAPE); assertEquals("Esc is Cancel", 0, panel()); assertEquals(ClockPlacement.Default, portraitPos())
    }

    @Test fun HOM_23_theMenuKeyOnTheFocusedClockEntersTheEditMode() {
        show()
        rule.onNodeWithTag("glance.clock", useUnmergedTree = true).requestFocus(); rule.waitForIdle()
        rule.onNodeWithTag("glance.clock", useUnmergedTree = true).performKeyInput { keyDown(androidx.compose.ui.input.key.Key.Menu); keyUp(androidx.compose.ui.input.key.Key.Menu) }; rule.waitForIdle()
        assertEquals(1, panel())
    }

    @Test fun HOM_23_rightToLeftKeepsTheClockPlaceAndStoresTheSamePhysicalFractions() {
        show(dir = LayoutDirection.Rtl)
        val root = rule.onNodeWithTag("glance.surface").fetchSemanticsNode().boundsInRoot; val f = clockBounds()
        assertEquals("the clock is not mirrored", (root.left + root.right) / 2f, (f.left + f.right) / 2f, 2f)
        longPressClock(); assertEquals(1, panel())
        val c = clockBounds().center; drag(Offset(c.x, c.y), 150f, 0f)
        assertTrue("dragging right moves right in right-to-left too", portraitPos().x > 0.5f && clockBounds().center.x > f.center.x + 100f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi") fun HOM_23_theEditModeAndItsPanelAreVisibleInAScreenshot() {
        show(LauncherPrefs(clockStyle = ClockStyle.BigDigital, glanceClockDate = true)); longPressClock()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { android.graphics.Canvas(bmp).also { it.drawColor(android.graphics.Color.BLACK); view.draw(it) } }
        File(outDir, "w50_clock_edit_portrait.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // the edit frame is drawn 10 dp outside the clock: pixels just outside the clock's left edge carry the accent
        val b = clockBounds(); val d = view.resources.displayMetrics.density
        val x = (b.left - 10 * d).toInt().coerceAtLeast(0); val y = b.center.y.toInt()
        var accent = 0
        for (dx in -3..3) { val px = bmp.getPixel((x + dx).coerceIn(0, bmp.width - 1), y); if (android.graphics.Color.blue(px) > 150 && android.graphics.Color.red(px) > 80) accent++ }
        assertTrue("the edit frame outline is drawn", accent > 0)
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land") fun HOM_23_landscapeStoresItsOwnPlaceAndPutsThePanelBesideTheClock() {
        show(); longPressClock(); val c = clockBounds().center
        drag(Offset(c.x, c.y), 120f, 60f)
        assertEquals("portrait untouched", ClockPlacement.Default, cur.value.clockPos(false)); assertNotEquals(ClockPlacement.Default, cur.value.clockPos(true))
        val p = rule.onNodeWithTag(GlanceClockEditTags.PANEL).fetchSemanticsNode().boundsInRoot
        val b = clockBounds()
        assertTrue("the panel is beside the clock: $p vs $b", p.right <= b.left + 1f || p.left >= b.right - 1f)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { android.graphics.Canvas(bmp).also { it.drawColor(android.graphics.Color.BLACK); view.draw(it) } }
        File(outDir, "w50_clock_edit_landscape.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun HOM_23_theStyleOffHasNothingToEdit() {
        show(LauncherPrefs(clockStyle = ClockStyle.Off))
        val root = rule.onNodeWithTag("glance.surface").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("glance.surface").performTouchInput { longClick(Offset(root.width * 0.5f, root.height * 0.2f)) }; rule.waitForIdle()
        assertEquals("with no clock the long press is the config options", 1, emptyLong); assertEquals(0, panel())
    }
}
