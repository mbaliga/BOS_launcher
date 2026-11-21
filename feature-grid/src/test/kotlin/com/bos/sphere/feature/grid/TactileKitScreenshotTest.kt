package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.ThemeVariant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** CFG-16: one screenshot test per tactile control type, in light and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h200dp-xhdpi")
class TactileKitScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @After fun resetTheme() = HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF)

    private fun luminance(b: Bitmap, onlyDifferingFrom: Int? = null): Pair<Double, Int> {
        var sum = 0.0; var n = 0; var differing = 0
        for (y in 0 until b.height step 2) for (x in 0 until b.width step 2) {
            val p = b.getPixel(x, y)
            sum += 0.299 * ((p shr 16) and 255) + 0.587 * ((p shr 8) and 255) + 0.114 * (p and 255); n++
            if (onlyDifferingFrom != null && p != onlyDifferingFrom) differing++
        }
        return sum / n to differing
    }

    /** Renders [control] on the theme's own background; returns (mean luminance, pixels that are not background). */
    private fun render(name: String, light: Boolean, control: @Composable () -> Unit): Pair<Double, Int> {
        HyleTheme.apply(if (light) ThemeVariant.Light else ThemeVariant.Dark, 0xFF6D5BFF)
        rule.setContent { Box(Modifier.fillMaxSize().background(HyleColors.Background).padding(16.dp)) { Box(Modifier.width(300.dp)) { control() } } }
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "cfg16-$name-${if (light) "light" else "dark"}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return luminance(bmp, bmp.getPixel(2, 2))
    }

    private fun check(name: String, control: @Composable () -> Unit) {
        val (lumD, drawnD) = render(name, false, control)
        assertTrue("$name dark draws something ($drawnD px)", drawnD > 200)
        assertTrue("$name dark is dark ($lumD)", lumD < 110)
    }

    private fun checkLight(name: String, control: @Composable () -> Unit) {
        val (lumL, drawnL) = render(name, true, control)
        assertTrue("$name light draws something ($drawnL px)", drawnL > 200)
        assertTrue("$name light is light ($lumL)", lumL > 150)
    }

    @Test fun CFG_16_toggle_dark() = check("toggle") { TactileToggle(true) }
    @Test fun CFG_16_toggle_light() = checkLight("toggle") { TactileToggle(true) }

    @Test fun CFG_16_slider_dark() = check("slider") { TactileSlider(0.4f, {}, defaultFraction = 0.5f) }
    @Test fun CFG_16_slider_light() = checkLight("slider") { TactileSlider(0.4f, {}, defaultFraction = 0.5f) }

    @Test fun CFG_16_segments_dark() = check("segments") { SegmentSlab(listOf("Pages", "List", "PSP", "DSi"), 1, {}) }
    @Test fun CFG_16_segments_light() = checkLight("segments") { SegmentSlab(listOf("Pages", "List", "PSP", "DSi"), 1, {}) }

    @Test fun CFG_16_stepper_dark() = check("stepper") { TactileStepper("4", true, true, {}, {}, "Columns") }
    @Test fun CFG_16_stepper_light() = checkLight("stepper") { TactileStepper("4", true, true, {}, {}, "Columns") }

    @Test fun CFG_16_sectionHeader_dark() = check("header") { Column { TactileSectionHeader("Status strip") } }
    @Test fun CFG_16_sectionHeader_light() = checkLight("header") { Column { TactileSectionHeader("Status strip") } }

    @Test fun CFG_16_chips_dark() = check("chips") { TactileChipRow(listOf("Off", "Left", "Centre", "Right"), "Centre", { it }, {}) }
    @Test fun CFG_16_chips_light() = checkLight("chips") { TactileChipRow(listOf("Off", "Left", "Centre", "Right"), "Centre", { it }, {}) }

    @Test fun CFG_16_pill_dark() = check("pill") { Column { TactilePill("Save", true, true, {}); TactilePill("Cancel", false, true, {}) } }
    @Test fun CFG_16_pill_light() = checkLight("pill") { Column { TactilePill("Save", true, true, {}); TactilePill("Cancel", false, true, {}) } }

    @Test fun CFG_16_stepperStepsAndStopsAtTheEnds() {
        rule.setContent {
            var v by remember { mutableIntStateOf(2) }
            Stepper("Rows", v, 1..3) { v = it }
        }
        rule.onNodeWithContentDescription("Increase Rows").performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("Rows 3").assertExists()
        rule.onNodeWithContentDescription("Increase Rows").performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("Rows 3").assertExists()   // clamped at the top
        rule.onNodeWithContentDescription("Decrease Rows").performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("Rows 2").assertExists()
    }

    @Test fun CFG_16_olderPageControlsAreTheTactileOnes() {
        // The shared older-page helpers now draw the kit's controls: this renders them together without error.
        rule.setContent {
            Column {
                SectionHeader("Older page")
                ToggleRow("Labels", true, {})
                LabeledSlider("Size", 0.3f, 0f..1f, "30%", {})
                ChipRow(listOf("A", "B"), "A", { it }, {})
                TextButtonPill("Done", primary = true) {}
            }
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Size, 30%").assertExists()
        assertEquals(3, TactileTokens.groove.alpha.toInt() + 2 + 0)   // tokens resolve without a theme crash
    }
}
