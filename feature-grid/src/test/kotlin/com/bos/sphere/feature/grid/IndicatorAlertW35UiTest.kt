package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.bos.sphere.core.data.AlertEffect
import com.bos.sphere.core.data.CalmEffect
import com.bos.sphere.core.data.IndicatorColors
import com.bos.sphere.core.data.IndicatorMetric
import com.bos.sphere.core.data.IndicatorReading
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.RadarBackground
import com.bos.sphere.core.data.ReadingKind
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.quality.MotionPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Wave 35 (signalEffects): the four effects on the real [IndicatorLayer]. Calm effects light while the value is healthy; alert effects are invisible then,
 * grow as the value falls and are urgent (red, full, pulsing; one static frame under reduced motion) when it is lost. Pixels are measured on the drawn bitmap;
 * screenshots go to build/screenshots/signal35-*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class IndicatorAlertW35UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    @After fun reset() = ReducedMotionTesting.force(null)

    private val healthy = IndicatorReading(ReadingKind.Live, 1f, "100%")
    private val low = IndicatorReading(ReadingKind.Live, 0.4f, "40%")
    private val lost = IndicatorReading(ReadingKind.Off, 0f, "Offline")
    private var reading by mutableStateOf(healthy)

    private var calmSel by mutableStateOf<CalmEffect?>(null)
    private var alertSel by mutableStateOf<AlertEffect?>(null)
    private var metricSel by mutableStateOf(IndicatorMetric.Signal)
    private var intensitySel by mutableStateOf(0.6f)
    private var started = false

    /** The first call composes the layer (setContent is once per test); later calls change the selections through snapshot state, like the settings rows do. */
    private fun show(calm: CalmEffect?, alert: AlertEffect?, metric: IndicatorMetric = IndicatorMetric.Signal, intensity: Float = 0.6f) {
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { Snapshot.withMutableSnapshot { calmSel = calm; alertSel = alert; metricSel = metric; intensitySel = intensity; reading = healthy } }
        if (!started) {
            started = true
            rule.setContent {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    val p = LauncherPrefs()
                    IndicatorLayer(
                        reading, calmSel, alertSel, metricSel, IndicatorColors.argb(reading, p.indicatorLow, p.indicatorMid, p.indicatorHigh, p.indicatorIdle, false),
                        intensitySel, 0.5f, Modifier.fillMaxSize(),
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(100)
    }

    private fun set(r: IndicatorReading) { rule.runOnIdle { Snapshot.withMutableSnapshot { reading = r } }; rule.mainClock.advanceTimeBy(100) }

    private fun grab(name: String? = null): Bitmap {
        val view = rule.activity.findViewById<View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        if (name != null) File(outDir, "signal35-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    private fun lit(b: Bitmap): Int { var n = 0; for (y in 0 until b.height) for (x in 0 until b.width) { val c = b.getPixel(x, y); if ((c and 0xFFFFFF) != 0) n++ }; return n }
    private fun redDominant(b: Bitmap): Int { var n = 0; for (y in 0 until b.height) for (x in 0 until b.width) { val c = b.getPixel(x, y); val r = (c shr 16) and 255; val g = (c shr 8) and 255; val bl = c and 255; if (r > 90 && r > g * 2 && r > bl * 2) n++ }; return n }
    private fun greenDominant(b: Bitmap): Int { var n = 0; for (y in 0 until b.height) for (x in 0 until b.width) { val c = b.getPixel(x, y); val r = (c shr 16) and 255; val g = (c shr 8) and 255; if (g > 40 && g > r) n++ }; return n }
    private fun maxRed(b: Bitmap): Int { var m = 0; for (y in 0 until b.height) for (x in 0 until b.width) m = maxOf(m, (b.getPixel(x, y) shr 16) and 255); return m }
    private fun same(a: Bitmap, b: Bitmap) = a.sameAs(b)

    @Test fun PAL_24_w35_theTwoCalmEffectsDrawWhileHealthyInTheRampColour() {
        for (calm in CalmEffect.entries) {
            show(calm, null)
            val b = grab("calm-${calm.name}")
            assertTrue("$calm lights dots while healthy", lit(b) > 200)
            assertTrue("$calm is green when healthy", greenDominant(b) > 30)
        }
    }

    @Test fun PAL_24_w35_alertEffectsAreInvisibleWhileHealthy() {
        for (alert in AlertEffect.entries) {
            show(null, alert)
            assertEquals("$alert draws nothing at a healthy value", 0, lit(grab()))
        }
    }

    @Test fun PAL_24_w35_vignetteAndComicLinesAppearWhenLowAndGrowWhenLost() {
        for (alert in AlertEffect.entries) {
            show(null, alert)
            set(low); val lowShot = grab("${alert.name}-low")
            set(lost); val lostShot = grab("${alert.name}-lost")
            assertTrue("$alert shows when low (${lit(lowShot)})", lit(lowShot) > 100)
            assertTrue("$alert is stronger when lost (${lit(lostShot)} vs ${lit(lowShot)})", lit(lostShot) > lit(lowShot))
            assertTrue("$alert lost is red", redDominant(lostShot) > 100)
            set(healthy); assertEquals("$alert goes away again when the value recovers", 0, lit(grab()))
        }
    }

    @Test fun PAL_24_w35_theAlertGrowsProgressivelyAsTheValueFalls() {
        show(null, AlertEffect.ComicLines)
        val counts = listOf(0.7f, 0.5f, 0.3f, 0.1f).map { v -> set(IndicatorReading(ReadingKind.Live, v, "")); lit(grab()) }
        for (i in 1 until counts.size) assertTrue("comic lines at lower values cover more: $counts", counts[i] > counts[i - 1])
    }

    @Test fun PAL_24_w35_batteryAlertFollowsBatteryAndChargingIsHealthy() {
        show(null, AlertEffect.Vignette, IndicatorMetric.Battery)
        set(IndicatorReading(ReadingKind.Live, 0.3f, "30%", charging = true)); assertEquals("charging at 30 percent is healthy", 0, lit(grab()))
        set(IndicatorReading(ReadingKind.Live, 0.3f, "30%")); assertTrue(lit(grab()) > 100)
        set(IndicatorReading(ReadingKind.Live, 0.04f, "4%")); val urgent = grab("Vignette-battery-urgent")
        assertTrue("4 percent and not charging is urgent: red", redDominant(urgent) > 200)
        set(IndicatorReading(ReadingKind.Live, 0.04f, "4%", charging = true)); assertEquals("plugged in at 4 percent is calm again", 0, lit(grab()))
    }

    @Test fun PAL_24_w35_nothingRunsWhenHealthyAndUrgentPulsesOnceMotionIsAllowed() {
        show(null, AlertEffect.Vignette)
        val a = grab(); rule.mainClock.advanceTimeBy(700); val b = grab()
        assertTrue("healthy alert: no change over time, nothing drawn", same(a, b) && lit(a) == 0)
        set(lost); val u0 = grab(); rule.mainClock.advanceTimeBy(600); val u1 = grab()
        assertFalse("urgent pulses (about 1.2 s)", same(u0, u1))
        rule.mainClock.advanceTimeBy(600); // a full period later, near the first brightness again
        assertTrue(redDominant(grab()) > 200)
    }

    @Test fun PAL_24_w35_underReducedMotionUrgentIsOneStaticFullStrengthFrame() {
        ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f))
        for (alert in AlertEffect.entries) {
            show(null, alert); set(lost)
            val a = grab(); rule.mainClock.advanceTimeBy(700); val b = grab(); rule.mainClock.advanceTimeBy(1_900)
            assertTrue("$alert urgent is static under reduced motion", same(a, b) && same(a, grab()))
            assertTrue("$alert urgent is clearly visible and red (${redDominant(a)})", redDominant(a) > 300)
        }
    }

    @Test fun PAL_24_w35_comicLinesWhenLostAreDenseAndBoldComparedWithLow() {
        show(null, AlertEffect.ComicLines)
        set(low); val a = lit(grab())
        set(lost); val b = lit(grab())
        assertTrue("the full ring is much denser: $b vs $a", b > a * 2)
    }

    @Test fun PAL_24_w35_theTwoGroupsCanBeOnTogether() {
        show(CalmEffect.Bloom, AlertEffect.Vignette)
        set(lost) // offline: calm dots stay dark (Off paints nothing), the alert shows
        assertTrue(redDominant(grab("both-lost")) > 200)
        set(healthy)
        assertTrue("healthy: only the calm effect", lit(grab()) > 200 && redDominant(grab()) < 20)
    }

    @Test fun PAL_24_w35_zeroIntensityDrawsNothingEvenWhenLost() {
        show(CalmEffect.AnimatedDots, AlertEffect.Vignette, intensity = 0f)
        set(lost); assertEquals(0, lit(grab()))
    }

    private var radarMode by mutableStateOf(RadarBackground.Dimmed)
    private var previewLevel by mutableStateOf(1f)

    @Test fun PAL_24_w35_theAlertIsAboveRadarWithTrueBlackWhichComposesNoSignalBackground() {
        rule.mainClock.autoAdvance = false
        reading = lost
        val apps = List(8) { i -> com.bos.sphere.core.data.AppEntry("demo.$i", android.content.ComponentName("demo.$i", "demo.$i.Main"), "Clau0$i", android.os.Process.myUserHandle()) { null } }
        rule.setContent {
            SearchScreen(
                apps = apps, recent = apps, prefs = LauncherPrefs(searchMotion = false, searchLayoutPinned = true, radarBackground = radarMode), contacts = null,
                onLaunch = {}, onLongPressApp = {}, onOpenIntent = {}, onRequestContactsPermission = {}, onDismiss = {},
                hub = null, onOpenHit = null, onRunAction = {}, onRequestFilePermission = {}, sounds = null,
                launchHistory = RadarTestHabits.history(apps), learnedOverride = emptyMap(), nowMillis = { RadarTestHabits.NOW }, voiceAvailable = true,
                alertLayer = { IndicatorLayer(reading, null, AlertEffect.Vignette, IndicatorMetric.Signal, 0L, 0.6f, 0.5f, Modifier.fillMaxSize()) },
            )
        }
        for (mode in RadarBackground.entries) {
            rule.runOnIdle { Snapshot.withMutableSnapshot { radarMode = mode } }
            rule.mainClock.advanceTimeBy(1_500)
            val b = grab("radar-${mode.name}-lost")
            assertTrue("$mode: the urgent vignette is drawn over Radar (${redDominant(b)})", redDominant(b) > 300)
        }
        rule.runOnIdle { Snapshot.withMutableSnapshot { reading = healthy } }
        rule.mainClock.advanceTimeBy(300)
        assertTrue("healthy: no red over Radar", redDominant(grab()) < 50)
    }

    @Test fun PAL_24_w35_theSettingsPreviewRespondsToItsSimulatedLevelAndNeverTouchesRealState() {
        rule.mainClock.autoAdvance = false
        val prefs = LauncherPrefs(indicatorCalm = null, indicatorAlert = AlertEffect.Vignette)
        rule.setContent { Box(Modifier.fillMaxSize().background(Color.Black)) { androidx.compose.runtime.key(previewLevel) { IndicatorPreview(prefs, initialLevel = previewLevel) } } }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag(INDICATOR_PREVIEW_TAG).assertExists()
        val healthyShot = grab("preview-healthy")
        rule.runOnIdle { Snapshot.withMutableSnapshot { previewLevel = 0f } }
        rule.mainClock.advanceTimeBy(100)
        val lostShot = grab("preview-lost")
        assertTrue("the simulated lost level shows the red alert, the healthy one does not", redDominant(lostShot) > redDominant(healthyShot) + 200)
    }
}
