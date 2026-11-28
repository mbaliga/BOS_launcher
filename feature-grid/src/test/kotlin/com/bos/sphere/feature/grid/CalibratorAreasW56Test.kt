package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.core.design.ThemeVariant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Wave 56 (looksPresets), CFG-27: the "Try it" areas. The measured time comes from the touch events' own times (the test advances the event clock, no sleeping), a shake
 * from injected accelerometer samples; each writes through the callback, shows what was measured and what the setting is now, and Reset to default calls back.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CalibratorAreasW56Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var setTo by mutableStateOf("150 ms")
    private val held = ArrayList<Long>()
    private var resets = 0
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @After fun reset() { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }

    private fun host(content: @androidx.compose.runtime.Composable () -> Unit) = rule.setContent {
        SphereTheme { Box(Modifier.fillMaxSize().background(HyleColors.Background).padding(16.dp)) { content() } }
    }

    private fun shoot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "calib56-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun hold() = host { HoldCalibrator("hot", "Press and hold here", setTo, { "$it ms" }, { held += it; setTo = "${it} ms" }, { resets++; setTo = "150 ms" }) }

    @Test fun CFG_27_w56_aHoldIsMeasuredFromTheTouchEventTimesAndReported() {
        hold()
        rule.onNodeWithTag(CalibratorTags.area("hot")).performTouchInput { down(center); advanceEventTime(300); up() }
        rule.waitForIdle()
        assertEquals(1, held.size); assertTrue("${held[0]}", held[0] in 295..305)
        rule.onNodeWithText("Measured ${held[0]} ms").assertExists()
        rule.onNodeWithText("Set to ${held[0]} ms").assertExists()
        shoot("hold")
    }

    @Test fun CFG_27_w56_aCancelledHoldMeasuresNothingAndAShortTapIsAShortHold() {
        hold()
        rule.onNodeWithTag(CalibratorTags.area("hot")).performTouchInput { down(center); advanceEventTime(200); cancel() }
        rule.waitForIdle(); assertTrue(held.isEmpty())
        rule.onNodeWithTag(CalibratorTags.area("hot")).performTouchInput { down(center); advanceEventTime(30); up() }
        rule.waitForIdle(); assertEquals(1, held.size); assertTrue(held[0] < 100)
    }

    @Test fun CFG_27_w56_resetToDefaultCallsBackAndClearsTheMeasuredLine() {
        hold()
        rule.onNodeWithTag(CalibratorTags.area("hot")).performTouchInput { down(center); advanceEventTime(400); up() }
        rule.waitForIdle()
        rule.onNodeWithTag(CalibratorTags.reset("hot")).performClick(); rule.waitForIdle()
        assertEquals(1, resets); rule.onNodeWithText("Set to 150 ms").assertExists()
        rule.onNodeWithText("Measured", substring = true).assertDoesNotExist()
    }

    @Test fun CFG_27_w56_twoTapsMeasureTheWaitBetweenThem() {
        val waited = ArrayList<Long>()
        host { IntervalCalibrator("ctl", "Tap here to start", "Tap again when the controls should hide", "4 s", { "%.1f s".format(it / 1000.0) }, { waited += it }, {}) }
        rule.onNodeWithText("Tap here to start").assertExists()
        rule.onNodeWithTag(CalibratorTags.area("ctl")).performTouchInput { down(center); advanceEventTime(50); up() }
        rule.waitForIdle()
        rule.onNodeWithText("Tap again when the controls should hide").assertExists()
        assertTrue(waited.isEmpty())
        rule.onNodeWithTag(CalibratorTags.area("ctl")).performTouchInput { advanceEventTime(5_000); down(center); advanceEventTime(50); up() }
        rule.waitForIdle()
        assertEquals(1, waited.size); assertTrue("${waited[0]}", waited[0] in 5_000..5_300)
        rule.onNodeWithText("Tap here to start").assertExists()
    }

    private class FakeAccel : AccelSource {
        var listener: ((Long, Float, Float, Float) -> Unit)? = null; var stopped = false
        override fun start(onSample: (tMs: Long, x: Float, y: Float, z: Float) -> Unit): () -> Unit { listener = onSample; return { stopped = true; listener = null } }
    }

    @Test fun CFG_27_w56_aShakeFromInjectedSamplesIsMeasuredAndTheSensorStopsWhenTheAreaLeaves() {
        val fake = FakeAccel(); val peaks = ArrayList<Float>(); var show by mutableStateOf(true)
        host { if (show) ShakeCalibrator("shake", "Shake your phone", "No motion sensor on this device", "50%", fake, { "%.1f m/s²".format(it) }, { peaks += it }, {}) }
        rule.onNodeWithText("Shake your phone").assertExists()
        rule.runOnUiThread {
            var t = 0L
            repeat(30) { fake.listener!!(t, 0f, 0f, 9.81f); t += 20 }
            repeat(8) { i -> fake.listener!!(t, if (i % 2 == 0) 14f else -14f, 0f, 9.81f); t += 40 }
            repeat(40) { fake.listener!!(t, 0f, 0f, 9.81f); t += 20 }
        }
        rule.waitForIdle()
        assertEquals(1, peaks.size); assertTrue("${peaks[0]}", peaks[0] > 10f)
        rule.onNodeWithText("Measured", substring = true).assertExists()
        shoot("shake")
        show = false; rule.waitForIdle()
        assertTrue("the accelerometer is released with the area", fake.stopped)
    }

    @Test fun CFG_27_w56_withoutAnAccelerometerTheShakeAreaSaysSo() {
        host { ShakeCalibrator("shake", "Shake your phone", "No motion sensor on this device", "50%", null, { "" }, {}, {}) }
        rule.onNodeWithText("No motion sensor on this device").assertExists()
        assertNotNull(rule.onNodeWithTag(CalibratorTags.area("shake")))
    }
}
