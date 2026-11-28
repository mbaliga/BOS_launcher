package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.CornerSlot
import com.bos.sphere.core.data.CornerStyle
import com.bos.sphere.core.data.DefaultCorners
import com.bos.sphere.core.data.GlanceHotspots
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.quality.MotionPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Wave 59 (glanceTop), CFG-77 to CFG-79: the Hotspots editor. A tap on a corner of the miniature turns it on or off (and the stored set follows), a long
 * press or Edit opens the corner editor for that corner, the selected corner pulses, every corner is a labelled toggle with an Edit action, the keys move,
 * toggle and edit, landscape and "Separate corners for landscape" edit the orientation in view, and right to left keeps the physical corners.
 * Screenshots: build/screenshots/w59_editor_*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class HotspotsEditorW59UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @After fun reset() { ReducedMotionTesting.force(null); L10n.setLanguage("") }

    private val prefs = mutableStateOf(LauncherPrefs())
    private val edits = ArrayList<Pair<Corner, Boolean>>()
    private val landscape = DefaultCorners + (Corner.TopLeft to CornerSlot.Single(CornerItem.Action(CornerActionId.Flashlight)))

    private fun editor(deviceLandscape: Boolean = false, rotation: Int = 0, rtl: Boolean = false, portrait: Map<Corner, CornerSlot> = DefaultCorners, keyboard: Boolean = false) {
        rule.setContent {
            if (keyboard) { val im = androidx.compose.ui.platform.LocalInputModeManager.current; androidx.compose.runtime.SideEffect { im.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) } }
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).padding(16.dp)) {
                    HotspotsEditorFor(
                        prefs = prefs.value, update = { f -> Snapshot.withMutableSnapshot { prefs.value = f(prefs.value) } },
                        portraitSlots = portrait, landscapeSlots = landscape, deviceLandscape = deviceLandscape, deviceRotation = rotation,
                        appsByKey = emptyMap(), onEdit = { c, l -> edits += c to l },
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(400)
    }

    private fun corner(c: Corner) = rule.onNodeWithTag(HotspotsEditorTags.corner(c))
    private fun text(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(200)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { Canvas(bmp).also { it.drawColor(android.graphics.Color.BLACK); view.draw(it) } }
        File(outDir, "w59_editor_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun CFG_77_aTapTurnsACornerOffAndOnAndTheStoredSetFollows() {
        editor()
        shoot("portrait_all_on")
        corner(Corner.TopRight).performClick(); rule.mainClock.advanceTimeBy(200)
        assertEquals(GlanceHotspots.ALL - Corner.TopRight, prefs.value.glanceHotspots)
        corner(Corner.TopRight).assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        assertEquals("the row below names the selected corner", "Top right", text(HotspotsEditorTags.ROW_NAME))
        assertEquals("Music", text(HotspotsEditorTags.ROW_ITEM))
        shoot("portrait_top_right_off")
        corner(Corner.TopRight).performClick(); rule.mainClock.advanceTimeBy(200)
        assertEquals(GlanceHotspots.ALL, prefs.value.glanceHotspots)
        // the row's switch toggles the selected corner too
        rule.onNodeWithTag(HotspotsEditorTags.ROW_SWITCH).performClick(); rule.mainClock.advanceTimeBy(200)
        assertEquals(GlanceHotspots.ALL - Corner.TopRight, prefs.value.glanceHotspots)
    }

    @Test fun CFG_77_aLongPressOrEditOpensTheCornerEditorForThatCorner() {
        editor()
        corner(Corner.BottomLeft).performTouchInput { longClick() }; rule.mainClock.advanceTimeBy(800)
        assertEquals(listOf(Corner.BottomLeft to false), edits)
        assertEquals("a long press does not toggle", GlanceHotspots.ALL, prefs.value.glanceHotspots)
        corner(Corner.BottomLeft).assertIsSelected()
        rule.onNodeWithTag(HotspotsEditorTags.EDIT).performClick(); rule.mainClock.advanceTimeBy(200)
        assertEquals(Corner.BottomLeft to false, edits.last())
        // the custom accessibility action does the same
        val actions = corner(Corner.TopLeft).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Edit"), actions.map { it.label })
        rule.runOnUiThread { actions[0].action() }
        assertEquals(Corner.TopLeft to false, edits.last())
    }

    @Test fun CFG_77_theSelectedCornerPulsesAndIsStillWithReducedMotion() {
        rule.mainClock.autoAdvance = false
        editor()
        corner(Corner.TopLeft).assertIsSelected(); corner(Corner.BottomRight).assertIsNotSelected()
        fun pulse() = rule.onNodeWithTag(HotspotsEditorTags.PULSE, useUnmergedTree = true).fetchSemanticsNode().config[HotspotPulse]
        val a = pulse(); rule.mainClock.advanceTimeBy(350); val b = pulse()
        assertNotEquals("the ring pulses", a, b, 0.0001f)
    }

    @Test fun CFG_77_reducedMotionHoldsThePulseStill() {
        ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f))
        rule.mainClock.autoAdvance = false
        editor()
        fun pulse() = rule.onNodeWithTag(HotspotsEditorTags.PULSE, useUnmergedTree = true).fetchSemanticsNode().config[HotspotPulse]
        val a = pulse(); rule.mainClock.advanceTimeBy(350)
        assertEquals(1f, a, 0f); assertEquals(1f, pulse(), 0f)
    }

    /**
     * Wave 60 (testFlake, CFG-67): a regression for the app suite's rare "Compose did not get idle" flake. The selected corner's pulse is a real,
     * infinite transition with full motion; composing it, then disposing the editor (as a test that forgets to force reduced motion or stop the
     * clock would leave it), must cancel that transition with the composition, so the host settles promptly on the very next [waitForIdle] rather
     * than spin forever polling a leaked animation (the symptom the flake showed on an unrelated, later test in the same JVM fork).
     */
    @Test fun CFG_77_wave60_disposingTheEditorCancelsItsPulseSoTheHostStaysIdle() {
        var showEditor by mutableStateOf(true)
        rule.setContent {
            if (showEditor) {
                Box(Modifier.fillMaxSize()) {
                    HotspotsEditorFor(
                        prefs = prefs.value, update = { f -> Snapshot.withMutableSnapshot { prefs.value = f(prefs.value) } },
                        portraitSlots = DefaultCorners, landscapeSlots = landscape, deviceLandscape = false, deviceRotation = 0,
                        appsByKey = emptyMap(), onEdit = { _, _ -> },
                    )
                }
            } else Text("idle")
        }
        // The selected corner (Top left, by default) pulses with real motion: not forced here, on purpose.
        rule.mainClock.advanceTimeBy(500)
        corner(Corner.TopLeft).assertIsSelected()
        showEditor = false // leaves the editor's composition: its infinite pulse coroutine must be cancelled with it
        val start = System.currentTimeMillis()
        rule.waitForIdle() // a leaked pulse would keep requesting frames forever and this would hang (the flake's exact symptom)
        assertTrue("waitForIdle returned promptly once the pulse's composition left", System.currentTimeMillis() - start < 10_000)
        rule.onNodeWithText("idle", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun CFG_78_eachCornerIsALabelledToggleWithItsItemAndState() {
        editor(portrait = DefaultCorners + (Corner.BottomRight to CornerSlot.Empty))
        for ((c, item) in listOf(Corner.TopLeft to "Camera", Corner.TopRight to "Music", Corner.BottomRight to "Nothing", Corner.BottomLeft to "All apps")) {
            corner(c).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("${c.label} hotspot, $item")))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "On"))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Switch))
                .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
        }
        corner(Corner.TopLeft).performClick(); rule.mainClock.advanceTimeBy(200)
        corner(Corner.TopLeft).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Off"))
        // the miniature's own corner layer is a picture: it adds no buttons of its own
        assertEquals(0, rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Camera"))).fetchSemanticsNodes().size)
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun CFG_78_arrowsMoveEnterTogglesAndEEdits() {
        editor(keyboard = true)
        corner(Corner.TopLeft).performSemanticsAction(SemanticsActions.RequestFocus); rule.mainClock.advanceTimeBy(100)
        corner(Corner.TopLeft).performKeyInput { pressKey(Key.DirectionRight) }; rule.mainClock.advanceTimeBy(100)
        corner(Corner.TopRight).assertIsSelected()
        corner(Corner.TopRight).performKeyInput { pressKey(Key.DirectionDown) }; rule.mainClock.advanceTimeBy(100)
        corner(Corner.BottomRight).assertIsSelected()
        corner(Corner.BottomRight).performKeyInput { pressKey(Key.Enter) }; rule.mainClock.advanceTimeBy(100)
        assertEquals(GlanceHotspots.ALL - Corner.BottomRight, prefs.value.glanceHotspots)
        corner(Corner.BottomRight).performKeyInput { pressKey(Key.E) }; rule.mainClock.advanceTimeBy(100)
        assertEquals(listOf(Corner.BottomRight to false), edits)
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun CFG_79_inLandscapeTheMiniatureIsLandscapeAndTheCornersStayPhysical() {
        editor(deviceLandscape = true, rotation = 1)
        val b = rule.onNodeWithTag(HotspotsEditorTags.MINIATURE).fetchSemanticsNode().boundsInRoot
        assertTrue("a landscape miniature (${b.width} x ${b.height})", b.width > b.height)
        // at rotation 1 the screen top left is the physical top right (Music)
        corner(Corner.TopLeft).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Top left hotspot, Music")))
        corner(Corner.TopLeft).performClick(); rule.mainClock.advanceTimeBy(200)
        assertEquals("the physical corner under it turns off", GlanceHotspots.ALL - Corner.TopRight, prefs.value.glanceHotspots)
        shoot("landscape")
    }

    @Test fun CFG_79_withSeparateCornersTheEditorEditsTheOrientationPickedInIt() {
        Snapshot.withMutableSnapshot { prefs.value = prefs.value.copy(cornersPerOrientation = true) }
        editor()
        rule.onNodeWithTag(HotspotsEditorTags.ORIENTATION).fetchSemanticsNode()
        assertEquals("portrait first (the device's)", "Camera", text(HotspotsEditorTags.ROW_ITEM))
        rule.onNodeWithTag(oneUiSegmentedTag(1)).performClick(); rule.mainClock.advanceTimeBy(300)
        val b = rule.onNodeWithTag(HotspotsEditorTags.MINIATURE).fetchSemanticsNode().boundsInRoot
        assertTrue("the miniature turns to landscape", b.width > b.height)
        // landscape's own set, at the natural landscape rotation (1): the screen top left is the physical top right
        corner(Corner.TopRight).performClick(); rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithTag(HotspotsEditorTags.EDIT).performClick(); rule.mainClock.advanceTimeBy(200)
        assertEquals("an edit goes to the landscape set", Corner.BottomRight to true, edits.last())
        corner(Corner.TopLeft).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Top left hotspot, Music")))
        shoot("separate_landscape")
        // without separate corners there is no orientation control
    }

    @Test fun CFG_79_noOrientationControlWithOneSetOfCorners() {
        editor()
        assertEquals(0, rule.onAllNodes(androidx.compose.ui.test.hasTestTag(HotspotsEditorTags.ORIENTATION)).fetchSemanticsNodes().size)
    }

    @Test fun CFG_78_rightToLeftKeepsThePhysicalCornersAndReadsNaturally() {
        L10n.setLanguage("ar")
        editor(rtl = true)
        val mini = rule.onNodeWithTag(HotspotsEditorTags.MINIATURE).fetchSemanticsNode().boundsInRoot
        val tl = corner(Corner.TopLeft).fetchSemanticsNode().boundsInRoot
        assertTrue("the top-left corner is drawn at the left in right to left too", tl.left - mini.left < 4f)
        val desc = corner(Corner.TopLeft).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()
        assertTrue("the name is the translated one ($desc)", desc.startsWith(L10n.tr("%s hotspot", L10n.tr("Top left"))))
        corner(Corner.TopLeft).performClick(); rule.mainClock.advanceTimeBy(200)
        assertEquals(GlanceHotspots.ALL - Corner.TopLeft, prefs.value.glanceHotspots)
        shoot("rtl")
    }

    @Test fun CFG_77_theMiniatureUsesTheChosenCornerStyle() {
        Snapshot.withMutableSnapshot { prefs.value = prefs.value.copy(cornerStyle = CornerStyle.Quarter, glanceHotspots = GlanceHotspots.ALL - Corner.BottomRight) }
        editor()
        shoot("quarter_style")
        corner(Corner.BottomRight).assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
    }
}

/** Wave 59 (glanceTop), CFG-76 / CFG-77: the floating Preview follows the editor's corner and draws the music line's width. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
class PreviewSyncW59UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun reset() { PreviewStage.exposeSandboxSemantics = false; ReducedMotionTesting.force(null) }

    @Test fun CFG_77_theEditorsSelectedCornerIsWhatThePreviewsCornersTopicPulses() {
        val focus = PreviewFocusState()
        val prefs = mutableStateOf(LauncherPrefs())
        rule.setContent {
            CompositionLocalProvider(LocalPreviewFocus provides focus) {
                HotspotsEditorFor(prefs.value, { f -> Snapshot.withMutableSnapshot { prefs.value = f(prefs.value) } }, DefaultCorners, DefaultCorners, false, 0, emptyMap(), { _, _ -> })
            }
        }
        rule.onNodeWithTag(HotspotsEditorTags.corner(Corner.BottomRight)).performClick(); rule.waitForIdle()
        assertEquals(PreviewSubject(PreviewTopic.Corners, Corner.BottomRight), focus.subject)
        assertEquals("the Hotspots row maps to the Corners topic", PreviewTopic.Corners, PreviewTopics.ROWS["Hotspots"])
    }

    @Test fun CFG_76_thePreviewDrawsTheMusicLineAtTheChosenWidthWithASampleTitle() {
        ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f))
        PreviewStage.exposeSandboxSemantics = true
        val width = mutableStateOf(MusicLineWidthHolder.Fit)
        rule.setContent {
            val capture = rememberHomeCapture()
            capture.size = androidx.compose.ui.unit.IntSize(822, 1782)
            val p = LauncherPrefs(musicLineWidth = width.value.v)
            Box(Modifier.fillMaxSize()) {
                PreviewStageContent(capture, PreviewSubject(PreviewTopic.Glance, music = true), PreviewSurfaces(prefs = { p }, topDrawn = { true to true }, mediaPlaying = { false }), p)
            }
        }
        rule.waitForIdle()
        fun w() = rule.onNodeWithTag("glance.title.static", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width
        val fit = w()
        Snapshot.withMutableSnapshot { width.value = MusicLineWidthHolder.Full }; rule.waitForIdle()
        assertTrue("Full width draws a wider line in the Preview ($fit -> ${w()})", w() > fit * 1.2f)
        rule.onNodeWithTag(PreviewStageTags.SAMPLE, useUnmergedTree = true).fetchSemanticsNode()
    }
}

private enum class MusicLineWidthHolder(val v: com.bos.sphere.core.data.MusicLineWidth) { Fit(com.bos.sphere.core.data.MusicLineWidth.FitAroundCorners), Full(com.bos.sphere.core.data.MusicLineWidth.FullWidth) }
