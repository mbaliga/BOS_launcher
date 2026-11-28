package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import com.bos.sphere.core.data.CalmStyle
import com.bos.sphere.core.data.CalmStyles
import com.bos.sphere.core.data.IndicatorColors
import com.bos.sphere.core.data.IndicatorMetric
import com.bos.sphere.core.data.IndicatorReading
import com.bos.sphere.core.data.LauncherPrefs
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
 * Wave 55 (calmStyles): the calm styles never interrupt a wallpaper. A known picture wallpaper is drawn with each style at each severity (healthy, low, very low, lost,
 * for Signal and Battery) at full intensity, the worst case, and the pixels are compared with the plain wallpaper: the centre 80 percent of the screen may change by less
 * than a mean overlay alpha of 0.04, no pixel by more than the style cap (0.35), and the changed pixels are a small share of the screen (an accent, never a fill).
 * Screenshots go to build/screenshots/calm55-*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class CalmStylesW55UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    @After fun reset() = ReducedMotionTesting.force(null)

    private val healthy = IndicatorReading(ReadingKind.Live, 1f, "100%")
    private val low = IndicatorReading(ReadingKind.Live, 0.4f, "40%")
    private val veryLow = IndicatorReading(ReadingKind.Live, 0.1f, "10%")
    private val lost = IndicatorReading(ReadingKind.Off, 0f, "Offline")
    private val batteryUrgent = IndicatorReading(ReadingKind.Live, 0.04f, "4%")

    private var styleSel by mutableStateOf(CalmStyle.None)
    private var readingSel by mutableStateOf(healthy)
    private var metricSel by mutableStateOf(IndicatorMetric.Signal)
    private var intensitySel by mutableStateOf(1f)
    private var rtlSel by mutableStateOf(false)
    private var saverSel by mutableStateOf<Boolean?>(null)
    private var started = false

    /** A deterministic picture: a four-colour diagonal gradient with a stripe pattern, bright and busy so any overlay shows. */
    private fun show(style: CalmStyle, reading: IndicatorReading = healthy, metric: IndicatorMetric = IndicatorMetric.Signal, intensity: Float = 1f, rtl: Boolean = false, saver: Boolean? = null) {
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { Snapshot.withMutableSnapshot { styleSel = style; readingSel = reading; metricSel = metric; intensitySel = intensity; rtlSel = rtl; saverSel = saver } }
        if (!started) {
            started = true
            rule.setContent {
                val p = LauncherPrefs()
                CompositionLocalProvider(
                    LocalLayoutDirection provides (if (rtlSel) LayoutDirection.Rtl else LayoutDirection.Ltr),
                    LocalBatterySaverOverride provides saverSel,
                ) {
                    Box(
                        Modifier.fillMaxSize().drawBehind {
                            drawRect(Brush.linearGradient(listOf(Color(0xFF1B6CA8), Color(0xFFE8A33D), Color(0xFF3FA66B), Color(0xFFB03A5B)), Offset.Zero, Offset(size.width, size.height)))
                            for (i in 0 until 40) drawRect(Color.White.copy(alpha = 0.18f), Offset(0f, i * size.height / 40f), Size(size.width, size.height / 120f))
                        },
                    ) {
                        IndicatorLayer(
                            readingSel, styleSel, metricSel, IndicatorColors.argb(readingSel, p.indicatorLow, p.indicatorMid, p.indicatorHigh, p.indicatorIdle, false),
                            intensitySel, 0.5f, Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(100)
    }

    private fun grab(name: String? = null): Bitmap {
        val view = rule.activity.findViewById<View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        if (name != null) File(outDir, "calm55-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    private class Diff(val centreMean: Float, val maxAlpha: Float, val changedShare: Float, val changed: Int)

    /** Per-pixel overlay alpha is bounded below by max channel difference / 255; the centre is the middle 80 percent in both directions. */
    private fun diff(plain: Bitmap, with: Bitmap): Diff {
        val w = plain.width; val h = plain.height
        var centreSum = 0.0; var centreN = 0; var max = 0f; var changed = 0
        for (y in 0 until h) for (x in 0 until w) {
            val a = plain.getPixel(x, y); val b = with.getPixel(x, y)
            val d = maxOf(kotlin.math.abs(((a shr 16) and 255) - ((b shr 16) and 255)), kotlin.math.abs(((a shr 8) and 255) - ((b shr 8) and 255)), kotlin.math.abs((a and 255) - (b and 255))) / 255f
            if (d > max) max = d
            if (d > 0.004f) changed++
            if (x >= w * 0.1f && x < w * 0.9f && y >= h * 0.1f && y < h * 0.9f) { centreSum += d; centreN++ }
        }
        return Diff((centreSum / centreN).toFloat(), max, changed / (w * h).toFloat(), changed)
    }

    private val ambient = CalmStyle.entries.filter { it.ambient }
    private val states = listOf(
        Triple("healthy", healthy, IndicatorMetric.Signal), Triple("low", low, IndicatorMetric.Signal), Triple("verylow", veryLow, IndicatorMetric.Signal), Triple("lost", lost, IndicatorMetric.Signal),
        Triple("batt-healthy", healthy, IndicatorMetric.Battery), Triple("batt-low", low, IndicatorMetric.Battery), Triple("batt-urgent", batteryUrgent, IndicatorMetric.Battery),
    )

    @Test fun PAL_24_w55_everyAmbientStyleAtEverySeverityLeavesTheWallpaperCentreAloneAndStaysUnderTheCap() {
        for (style in ambient) {
            for ((name, reading, metric) in states) {
                show(CalmStyle.None, reading, metric); val plain = grab()
                show(style, reading, metric); val shot = grab(if (name == "healthy" || name == "lost" || name == "batt-urgent") "${style.name}-$name" else null)
                val d = diff(plain, shot)
                assertTrue("$style $name changes something (an accent is drawn)", d.changed > 50)
                assertTrue("$style $name: centre 80 percent mean overlay ${d.centreMean} <= 0.04", d.centreMean <= 0.04f)
                assertTrue("$style $name: no pixel above the cap, was ${d.maxAlpha}", d.maxAlpha <= CalmStyles.MAX_ALPHA + 2.5f / 255f)
                assertTrue("$style $name: an accent, not a fill (${d.changedShare} of the screen)", d.changedShare < 0.12f)
            }
        }
    }

    @Test fun PAL_24_w55_theCentreOfTheScreenIsUntouchedByTheEdgeStyles() {
        for (style in listOf(CalmStyle.EdgeDots, CalmStyle.BottomGlow, CalmStyle.Beam, CalmStyle.CornerBloom)) {
            show(CalmStyle.None, lost); val plain = grab()
            show(style, lost); val d = diff(plain, grab())
            // the strictest reading of the owner rule: nothing at all inside the middle 60 percent
            val w = plain.width; val h = plain.height
            val s = grab(); var inner = 0
            for (y in (h * 0.2f).toInt() until (h * 0.8f).toInt()) for (x in (w * 0.2f).toInt() until (w * 0.8f).toInt()) if (plain.getPixel(x, y) != s.getPixel(x, y)) inner++
            assertEquals("$style paints nothing in the middle 60 percent", 0, inner)
            assertTrue(d.changed > 0)
        }
    }

    @Test fun PAL_24_w55_theStyleCapScalesWithTheIntensityAndZeroIsOff() {
        for (style in ambient) {
            show(CalmStyle.None, lost); val plain = grab()
            show(style, lost, intensity = 0.5f); val half = diff(plain, grab())
            assertTrue("$style at half intensity stays under half the cap (${half.maxAlpha})", half.maxAlpha <= CalmStyles.MAX_ALPHA * 0.5f + 2.5f / 255f)
            show(style, lost, intensity = 0f); assertEquals("$style at zero intensity draws nothing", 0, diff(plain, grab()).changed)
        }
    }

    @Test fun PAL_24_w55_offAndRegularDrawNothingAmbient() {
        show(CalmStyle.None, lost); val plain = grab()
        for (style in listOf(CalmStyle.None, CalmStyle.Regular)) { show(style, lost); assertEquals("$style paints no ambient pixel", 0, diff(plain, grab()).changed) }
    }

    @Test fun PAL_24_w55_theStylesExpressSeverityHealthyLowAndLostLookDifferentAndLostIsRed() {
        fun red(b: Bitmap): Int { var n = 0; for (y in 0 until b.height step 2) for (x in 0 until b.width step 2) { val c = b.getPixel(x, y); val r = (c shr 16) and 255; if (r > 200 && ((c shr 8) and 255) < 90 && (c and 255) < 120) n++ }; return n }
        for (style in ambient) {
            show(style, healthy); val a = grab()
            show(style, low); val b = grab()
            show(style, lost); val c = grab()
            assertFalse("$style healthy differs from low", a.sameAs(b)); assertFalse("$style low differs from lost", b.sameAs(c)); assertFalse(a.sameAs(c))
            // tinted red: the red overlay raises the red channel over the plain picture on the lit edge
            show(CalmStyle.None, lost); val plain = grab()
            var dr = 0L; var dg = 0L
            for (y in 0 until c.height) for (x in 0 until c.width) { val p = plain.getPixel(x, y); val q = c.getPixel(x, y); dr += ((q shr 16) and 255) - ((p shr 16) and 255); dg += ((q shr 8) and 255) - ((p shr 8) and 255) }
            assertTrue("$style lost raises red more than green (dr=$dr dg=$dg)", dr > dg)
        }
    }

    @Test fun PAL_24_w55_lowSignalAndLowBatteryUseTheSameThresholdsAsBefore() {
        // healthy at or above the healthy line is the quiet state; just below it the style gets stronger (more changed pixels or brighter)
        show(CalmStyle.BottomGlow, IndicatorReading(ReadingKind.Live, 0.8f, "")); val ok = grab()
        show(CalmStyle.BottomGlow, IndicatorReading(ReadingKind.Live, 0.3f, "")); val bad = grab()
        show(CalmStyle.None); val plain = grab()
        assertTrue("a low signal is more present than a good one", diff(plain, bad).maxAlpha >= diff(plain, ok).maxAlpha)
        show(CalmStyle.BottomGlow, IndicatorReading(ReadingKind.Live, 0.3f, "", charging = true), IndicatorMetric.Battery); val charging = grab()
        show(CalmStyle.BottomGlow, IndicatorReading(ReadingKind.Live, 0.3f, ""), IndicatorMetric.Battery); val notCharging = grab()
        assertTrue("charging counts as healthy: quieter than not charging", diff(plain, charging).maxAlpha < diff(plain, notCharging).maxAlpha)
    }

    @Test fun PAL_24_w55_reducedMotionAndBatterySaverDrawOneStillFrame() {
        for (style in ambient) {
            ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f))
            show(style, lost); val a = grab(); rule.mainClock.advanceTimeBy(1_900); val b = grab(); rule.mainClock.advanceTimeBy(3_000)
            assertTrue("$style is a single static frame under reduced motion", a.sameAs(b) && a.sameAs(grab()))
            assertTrue("$style still draws it", diff(run { show(CalmStyle.None, lost); grab() }, a).changed > 50)
            ReducedMotionTesting.force(null)
            show(style, lost, saver = true); val c = grab(); rule.mainClock.advanceTimeBy(1_900); assertTrue("$style is still in battery saver", c.sameAs(grab()))
        }
    }

    @Test fun PAL_24_w55_motionIsSlowAndAnimatedStylesMoveWhenMotionIsAllowed() {
        for (style in listOf(CalmStyle.EdgeDots, CalmStyle.CornerBloom)) {
            show(style, healthy); val a = grab(); rule.mainClock.advanceTimeBy(1_500); val b = grab()
            assertFalse("$style moves over time", a.sameAs(b))
        }
        // idle (airplane mode) is steady: no clock
        show(CalmStyle.EdgeDots, IndicatorReading(ReadingKind.Idle, 0f, "Airplane")); val a = grab(); rule.mainClock.advanceTimeBy(1_500); assertTrue(a.sameAs(grab()))
    }

    @Test fun PAL_24_w55_theBeamFollowsTheTrailingEdgeAndMirrorsInRightToLeft() {
        fun litColumns(b: Bitmap, plain: Bitmap, left: Boolean): Int {
            var n = 0
            val xs = if (left) 0 until b.width / 4 else b.width * 3 / 4 until b.width
            for (y in 0 until b.height) for (x in xs) if (b.getPixel(x, y) != plain.getPixel(x, y)) n++
            return n
        }
        show(CalmStyle.None, healthy); val plain = grab()
        show(CalmStyle.Beam, healthy); val ltr = grab("Beam-ltr")
        assertTrue(litColumns(ltr, plain, left = false) > 200); assertEquals(0, litColumns(ltr, plain, left = true))
        show(CalmStyle.None, healthy, rtl = true); val plainRtl = grab()
        show(CalmStyle.Beam, healthy, rtl = true); val rtl = grab("Beam-rtl")
        assertTrue(litColumns(rtl, plainRtl, left = true) > 200); assertEquals(0, litColumns(rtl, plainRtl, left = false))
    }

    @Test fun PAL_24_w55_theBeamLengthFollowsTheReading() {
        show(CalmStyle.None, healthy); val plain = grab()
        show(CalmStyle.Beam, IndicatorReading(ReadingKind.Live, 0.9f, "")); val full = diff(plain, grab()).changed
        show(CalmStyle.Beam, IndicatorReading(ReadingKind.Live, 0.3f, "")); val part = diff(plain, grab()).changed
        assertTrue("a weaker reading lights a shorter beam ($part vs $full)", part < full)
    }

    @Test fun PAL_24_w55_theDefaultForEveryNonSignalWallpaperDrawsNothingSoThePictureIsUntouched() {
        // the real wiring: the style the home draws for a wallpaper kind is CalmStyles.resolve; with nothing chosen every kind but the Signal wallpaper resolves to a style that draws nothing
        show(CalmStyle.None, lost); val plain = grab()
        for (k in com.bos.sphere.core.data.WallpaperKind.entries.filter { it != com.bos.sphere.core.data.WallpaperKind.Live }) {
            show(CalmStyles.resolve(null, k), lost); assertEquals("$k: nothing drawn by default", 0, diff(plain, grab()).changed)
        }
    }
}
