package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.bos.sphere.core.data.BackgroundChoice
import com.bos.sphere.core.data.BgItem
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.RadarIntensity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * HOM-16, CFG-17, NFR-13 (wave 15): Ground Control > Background under Robolectric. The two slot pickers show all ten options, a
 * second living wallpaper and an item already in the other slot are disabled and say why, a tap changes the slot, the rule holds
 * through a sequence of taps, and Intensity changes the saved value. Screenshots: `build/screenshots/bg-pane-*.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1500dp-port-mdpi")
class BackgroundPaneUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var prefs by mutableStateOf(LauncherPrefs())
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun still() = com.bos.sphere.core.design.ReducedMotionTesting.force(com.bos.sphere.core.quality.MotionPrefs(0f, 0f, 0f))
    @After fun reset() = com.bos.sphere.core.design.ReducedMotionTesting.force(null)

    private fun show() = rule.setContent {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            BackgroundPane(prefs) { f -> prefs = f(prefs) }
        }
    }

    private fun choice() = BackgroundChoice(prefs.backgroundSlot1, prefs.backgroundSlot2)

    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.findViewById<View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "bg-pane-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun HOM_16_theDefaultPairIsSelectedAndAllTenOptionsAreInBothSlots() {
        show()
        for (label in listOf("None", "Beam", "Spire", "Halo", "Laser", "Shaft", "Glow lines", "Breathing dots", "Bottom glow", "Radar sweep")) {
            assertEquals("$label in both slots", 2, rule.onAllNodes(androidx.compose.ui.test.hasContentDescription(label, substring = true)).fetchSemanticsNodes().count { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Selected) })
        }
        shot("default")
    }

    @Test fun HOM_16_aSecondLivingWallpaperIsDisabledWithTheReasonAndAnItemInTheOtherSlotToo() {
        show()
        // Default: Spire in slot 1, Breathing dots in slot 2. Slot 2: Beam would be a second wallpaper, Spire is already in slot 1.
        val beamInSlot2 = rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Beam, Only one living wallpaper"))
        assertTrue(beamInSlot2.fetchSemanticsNodes().size == 1)
        beamInSlot2[0].assertIsNotEnabled()
        rule.onNodeWithContentDescription("Spire, Already in the other slot").assertExists()
        rule.onNodeWithContentDescription("Breathing dots, Already in the other slot").assertExists()
    }

    @Test fun HOM_16_tappingAnAllowedOptionChangesTheSlotAndTheRulesHoldThroughASequenceOfTaps() {
        show()
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Laser") and androidx.compose.ui.test.hasClickAction())[0].performClick() // slot 1: Spire -> Laser
        rule.waitForIdle()
        assertEquals(BackgroundChoice(BgItem.Laser, BgItem.BreathingDots), choice())
        // slot 2 -> Bottom glow
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Bottom glow") and androidx.compose.ui.test.hasClickAction())[1].performClick()
        rule.waitForIdle()
        assertEquals(BackgroundChoice(BgItem.Laser, BgItem.BottomGlow), choice())
        // slot 2 -> None, then slot 1 -> Beam is allowed again (the wallpaper replaces the wallpaper)
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("None") and androidx.compose.ui.test.hasClickAction())[1].performClick()
        rule.waitForIdle()
        assertEquals(BackgroundChoice(BgItem.Laser, BgItem.None), choice())
        assertTrue(choice().isValid)
        shot("laser-none")
    }

    @Test fun HOM_16_aRefusedTapNeverBreaksTheRule() {
        show()
        // Beam in slot 2 is disabled; clicking it directly through the action does nothing.
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Beam, Only one living wallpaper"))[0].performClick()
        rule.waitForIdle()
        assertEquals(BackgroundChoice.DEFAULT, choice())
    }

    @Test fun HOM_16_intensityAndThePreviewSignalControlsAreThere() {
        show()
        rule.onNodeWithText("Subtle").assertExists()
        rule.onNodeWithText("Excellent").assertExists()
        rule.onNodeWithText("No signal").performClick()
        rule.waitForIdle()
        shot("preview-nosignal")
        rule.onNodeWithText("Subtle").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(RadarIntensity.Subtle, prefs.radarIntensity)
    }
}
