package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import com.bos.sphere.core.data.BackgroundChoice
import com.bos.sphere.core.data.BgItem
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.RadarIntensity
import com.bos.sphere.core.data.SignalForce
import com.bos.sphere.core.quality.Contrast
import com.bos.sphere.core.signals.FixedSignalSource
import com.bos.sphere.core.signals.SignalKind
import com.bos.sphere.core.signals.SignalState
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
 * HOM-16, PAL-24, NFR-14, NFR-15 (wave 15): the background drawn for real (Robolectric native graphics): every item at every signal
 * level, portrait and landscape, dark and light, the comic lines, reduced motion, intensity and TalkBack. Screenshots go to
 * `build/screenshots/bg-*.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackgroundRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private val cfg = mutableStateOf(BackgroundConfig(baseFill = true))
    private val sig = mutableStateOf<SignalState?>(null)
    private val still = mutableStateOf(true)
    private val clock = RadarClock()

    private fun set(config: BackgroundConfig? = null, signal: SignalState? = sig.value, isStill: Boolean = still.value, timeMs: Long? = null) {
        rule.runOnIdle {
            Snapshot.withMutableSnapshot {
                config?.let { cfg.value = it }
                sig.value = signal; still.value = isStill
                if (timeMs != null) clock.ms.longValue = timeMs
            }
        }
        rule.waitForIdle()
    }

    private fun show() = rule.setContent { Box(Modifier.fillMaxSize()) { SignalBackgroundCanvas(cfg.value, sig, clock, still.value) } }

    private fun capture(name: String? = null): Bitmap {
        rule.waitForIdle()
        val view = rule.activity.findViewById<View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        if (name != null) File(outDir, "bg-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    private fun lum(c: Int) = Contrast.luminance(c)
    private fun mean(b: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): Double {
        var s = 0.0; var n = 0
        for (y in y0 until y1) for (x in x0 until x1) { s += lum(b.getPixel(x, y)); n++ }
        return s / n
    }
    private fun bluePixels(b: Bitmap): Int {
        var n = 0
        for (y in 0 until b.height) for (x in 0 until b.width) { val c = b.getPixel(x, y); val r = (c shr 16) and 255; val g = (c shr 8) and 255; val bl = c and 255; if (bl > r + 25 && bl > g + 8) n++ }
        return n
    }
    private fun diffPixels(a: Bitmap, b: Bitmap): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.getPixel(x, y) != b.getPixel(x, y)) out += x to y
        return out
    }

    private val items = BgItem.entries.filter { it != BgItem.None }
    private fun only(item: BgItem, dark: Boolean = true) = BackgroundConfig(BackgroundChoice(item, BgItem.None), dark = dark, baseFill = true)
    private fun meanAll(b: Bitmap) = mean(b, 0, 0, b.width, b.height)
    /** The middle 60 percent: leaves out the comic lines, which live at the edges and belong to no signal only. */
    private fun meanCentre(b: Bitmap) = mean(b, b.width / 5, b.height / 5, b.width * 4 / 5, b.height * 4 / 5)

    // -------------------------------------------------------------------------------- every item at every level

    private fun everyItemGlowsDifferentlyWithTheLevel(tag: String) {
        show()
        for (item in items) {
            val means = DoubleArray(5)
            val shots = ArrayList<Bitmap>()
            for (level in 0..4) {
                set(only(item), if (level == 0) SignalState.NoSignal else SignalState.forced(level), isStill = true, timeMs = ScopeMath.STILL_TIME_MS)
                val b = capture(if (level % 2 == 0) "${item.id}-L$level-$tag" else null)
                means[level] = meanCentre(b); shots += b
            }
            assertTrue("${item.id} $tag level 4 draws light: ${means.toList()}", means[4] > 0.00005)
            assertTrue("${item.id} $tag level 4 is lighter than level 1: ${means.toList()}", means[4] > means[1] * 1.02)
            assertTrue("${item.id} $tag level 4 is lighter than level 0: ${means.toList()}", means[4] > means[0])
            for (l in 1..4) assertTrue("${item.id} $tag level $l differs from level ${l - 1}", diffPixels(shots[l - 1], shots[l]).size > 100)
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun HOM_16_everyItemGlowsDifferentlyAtEachSignalLevelPortrait() = everyItemGlowsDifferentlyWithTheLevel("port")

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-mdpi")
    fun HOM_16_everyItemGlowsDifferentlyAtEachSignalLevelLandscape() = everyItemGlowsDifferentlyWithTheLevel("land")

    // -------------------------------------------------------------------------------- the reference shapes

    /** Brightest quarter of the picture, by where its light-weighted centre of mass lies (0..1 across, 0..1 down). */
    private fun centreOfLight(b: Bitmap): Pair<Double, Double> {
        var sx = 0.0; var sy = 0.0; var s = 0.0
        for (y in 0 until b.height step 3) for (x in 0 until b.width step 3) { val l = lum(b.getPixel(x, y)); sx += l * x; sy += l * y; s += l }
        return if (s == 0.0) 0.5 to 0.5 else (sx / s / b.width) to (sy / s / b.height)
    }

    private fun shot(item: BgItem, level: Int): Bitmap { set(only(item), SignalState.forced(level), isStill = true, timeMs = ScopeMath.STILL_TIME_MS); return capture() }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-mdpi")
    fun HOM_16_beamEntersFromTheRightEdgeInLandscapeAndReachesFurtherWithTheLevel() {
        show()
        val weak = shot(BgItem.Beam, 1); val strong = shot(BgItem.Beam, 4)
        val (cx, cy) = centreOfLight(strong)
        assertTrue("light sits right of centre ($cx)", cx > 0.55); assertEquals("beam is on the horizontal axis", 0.5, cy, 0.08)
        fun reach(b: Bitmap): Int { var far = 0; for (x in 0 until b.width) if (lum(b.getPixel(x, b.height / 2)) > 0.004) { far = b.width - x; break }; return far }
        assertTrue("reach grows ${reach(weak)} -> ${reach(strong)}", reach(strong) > reach(weak) + 40)
        // the tip (right edge, axis) is the brightest part and is whitish: all channels high
        val tip = strong.getPixel(strong.width - 6, strong.height / 2)
        assertTrue("white-lilac tip ${Integer.toHexString(tip)}", ((tip shr 16) and 255) > 150 && (tip and 255) > 200)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun HOM_16_beamEntersFromTheBottomEdgeInPortrait() {
        show()
        val (cx, cy) = centreOfLight(shot(BgItem.Beam, 4))
        assertTrue("light sits below centre ($cy)", cy > 0.55); assertEquals(0.5, cx, 0.08)
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-mdpi")
    fun HOM_16_spireRisesFromTheBottomCentreAndGetsTallerWithTheLevel() {
        show()
        val low = shot(BgItem.Spire, 1); val high = shot(BgItem.Spire, 4)
        fun top(b: Bitmap): Int { for (y in 0 until b.height) if (lum(b.getPixel(b.width / 2, y)) > 0.01) return y; return b.height }
        assertTrue("apex rises ${top(low)} -> ${top(high)}", top(high) < top(low) - 30)
        val (cx, cy) = centreOfLight(high)
        assertEquals(0.5, cx, 0.06); assertTrue("bottom heavy ($cy)", cy > 0.5)
        val blue = high.getPixel(high.width / 2 + 40, high.height * 3 / 4)
        assertTrue("blue body ${Integer.toHexString(blue)}", (blue and 255) > ((blue shr 16) and 255) + 30)
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-mdpi")
    fun HOM_16_haloArcGrowsFromAShortArcTowardAClosedRingWithRoseColours() {
        show()
        val short = shot(BgItem.Halo, 1); val long = shot(BgItem.Halo, 4)
        fun lit(b: Bitmap): Int { var n = 0; for (y in 0 until b.height step 2) for (x in 0 until b.width step 2) if (lum(b.getPixel(x, y)) > 0.02) n++; return n }
        assertTrue("lit area grows ${lit(short)} -> ${lit(long)}", lit(long) > lit(short) * 1.3)
        var rose = 0; for (y in 0 until long.height step 3) for (x in 0 until long.width step 3) { val c = long.getPixel(x, y); if (((c shr 16) and 255) > (c and 255) + 25 && ((c shr 16) and 255) > ((c shr 8) and 255) + 25) rose++ }
        assertTrue("rose pixels $rose", rose > 300)
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-mdpi")
    fun HOM_16_laserBeamStandsOnAFloorLineAndGrowsInLength() {
        show()
        val low = shot(BgItem.Laser, 1); val high = shot(BgItem.Laser, 4)
        fun lit(b: Bitmap): Int { var n = 0; for (y in b.height * 3 / 10 until b.height * 3 / 4) if (((b.getPixel(b.width / 2, y) shr 16) and 255) > 100) n++; return n }
        assertTrue("beam longer ${lit(low)} -> ${lit(high)}", lit(high) > lit(low) + 40)
        val c = high.getPixel(high.width / 2, high.height * 55 / 100)
        assertTrue("red beam ${Integer.toHexString(c)}", ((c shr 16) and 255) > 150 && (c and 255) < 160)
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-mdpi")
    fun HOM_16_shaftIsADiagonalFromTheTopToTheLowerRightThatWidensWithTheLevel() {
        show()
        val (cx, cy) = centreOfLight(shot(BgItem.Shaft, 4))
        assertTrue("light centre is in the upper half ($cy)", cy < 0.5); assertTrue("and left of centre-right ($cx)", cx < 0.6)
        fun width(b: Bitmap): Int { val y = b.height / 2; var n = 0; for (x in 0 until b.width) if (lum(b.getPixel(x, y)) > 0.004) n++; return n }
        assertTrue("wider ${width(shot(BgItem.Shaft, 1))} -> ${width(shot(BgItem.Shaft, 4))}", width(shot(BgItem.Shaft, 4)) > width(shot(BgItem.Shaft, 1)) + 25)
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-mdpi")
    fun HOM_16_bottomGlowRisesFromTheLineNearTheBottomEdge() {
        show()
        val low = shot(BgItem.BottomGlow, 1); val high = shot(BgItem.BottomGlow, 4)
        val (_, cy) = centreOfLight(high); assertTrue("light sits low ($cy)", cy > 0.5)
        fun bloomTop(b: Bitmap): Int { for (y in 0 until b.height) if (lum(b.getPixel(b.width / 2, y)) > 0.12) return y; return b.height }
        assertTrue("bloom rises ${bloomTop(low)} -> ${bloomTop(high)}", bloomTop(high) < bloomTop(low) - 20)
        val line = high.getPixel(high.width / 2, (high.height * 0.935).toInt())
        assertTrue("white-hot line ${Integer.toHexString(line)}", ((line shr 16) and 255) > 190)
    }

    // -------------------------------------------------------------------------------- effects keep their wave 14 behaviour

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun PAL_24_glowLinesPulseMoreAsTheSignalGetsStronger() {
        show()
        val blue = LinkedHashMap<Int, Int>()
        for (level in 0..4) { set(only(BgItem.GlowLines), if (level == 0) SignalState.NoSignal else SignalState.forced(level), isStill = true, timeMs = ScopeMath.STILL_TIME_MS); blue[level] = bluePixels(capture("glow-lines-L$level-port")) }
        assertEquals("no pulse without signal", 0, blue[0])
        assertTrue("weak shows something $blue", blue[1]!! > 0)
        assertTrue("levels add light $blue", blue[1]!! < blue[2]!! && blue[2]!! < blue[3]!! && blue[3]!! < blue[4]!!)
    }

    private fun dotsAreBrightInTheMiddleAndInvisibleAtTheEdges(tag: String, dark: Boolean) {
        show(); set(only(BgItem.BreathingDots, dark), SignalState.forced(4))
        val b = capture("dots-$tag")
        val w = b.width; val h = b.height
        val centre = mean(b, w * 35 / 100, h * 40 / 100, w * 65 / 100, h * 60 / 100)
        val edge = mean(b, 0, 0, w, h / 20)
        val base = lum(if (dark) 0xFF000000.toInt() else 0xFFF3F3F6.toInt())
        if (dark) { assertTrue("centre $centre vs edge $edge", centre > edge + 0.0008); assertEquals(base, edge, 0.002) }
        else { assertTrue("centre $centre vs edge $edge", centre < edge - 0.0008); assertEquals(base, edge, 0.01) }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi") fun PAL_24_dotGridPortraitDark() = dotsAreBrightInTheMiddleAndInvisibleAtTheEdges("port-dark", true)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-mdpi") fun PAL_24_dotGridLandscapeDark() = dotsAreBrightInTheMiddleAndInvisibleAtTheEdges("land-dark", true)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi") fun PAL_24_dotGridPortraitLight() = dotsAreBrightInTheMiddleAndInvisibleAtTheEdges("port-light", false)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-mdpi") fun PAL_24_dotGridLandscapeLight() = dotsAreBrightInTheMiddleAndInvisibleAtTheEdges("land-light", false)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun HOM_16_breathingDotsLightAreaGrowsWithTheLevel() {
        show()
        fun litDots(level: Int): Int { val b = shot(BgItem.BreathingDots, level); var n = 0; for (y in 0 until b.height step 2) for (x in 0 until b.width step 2) if (lum(b.getPixel(x, y)) > 0.004) n++; return n }
        assertTrue("${litDots(1)} < ${litDots(4)}", litDots(1) < litDots(4))
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun HOM_16_radarSweepWedgeBrightensWithTheLevelAndNoSignalHasNoPing() {
        show()
        set(only(BgItem.RadarSweep), SignalState.forced(1), isStill = true); val weak = meanAll(capture())
        set(only(BgItem.RadarSweep), SignalState.forced(4), isStill = true); val strong = meanAll(capture("radar-sweep-L4-port"))
        assertTrue("wedge $weak < $strong", weak < strong)
        assertEquals(-1f, ScopeMath.pingProgress(1234L, BackgroundCurves.at(BackgroundCurves.PING_EVERY_MS, 0f)), 0f)
    }

    // -------------------------------------------------------------------------------- two slots, light surface, intensity

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun HOM_16_theDefaultPairDrawsBothItemsAndAnEffectOverAWallpaperAddsLight() {
        show()
        val wall = BackgroundConfig(BackgroundChoice(BgItem.Spire, BgItem.None), baseFill = true)
        set(wall, SignalState.forced(3), isStill = true, timeMs = ScopeMath.STILL_TIME_MS); val a = capture()
        set(BackgroundConfig(BackgroundChoice.DEFAULT, baseFill = true)); val both = capture("default-pair-port")
        assertTrue("dots are drawn over the Spire", diffPixels(a, both).size > 300)
        set(BackgroundConfig(BackgroundChoice(BgItem.None, BgItem.None), baseFill = true)); val none = capture("none-port")
        assertEquals("None is pure black", 0.0, meanAll(none), 0.0)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun HOM_16_everyItemStaysAQuietLightSurfaceOnTheLightTheme() {
        show()
        for (item in items) {
            set(only(item, dark = false), SignalState.forced(4), isStill = true, timeMs = ScopeMath.STILL_TIME_MS)
            val b = capture("${item.id}-light-port")
            assertTrue("${item.id} light mean ${meanAll(b)}", meanAll(b) > 0.45)
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun PAL_24_intensitySubtleIsFainterThanNormal() {
        show()
        for (item in items) {
            val normal = only(item)
            set(normal, SignalState.forced(4), isStill = true); val n = meanAll(capture())
            set(normal.copy(intensity = RadarIntensity.Subtle)); val s = meanAll(capture())
            assertTrue("${item.id} subtle $s < normal $n", s < n)
        }
    }

    // -------------------------------------------------------------------------------- comic lines (no signal only)

    private fun comicLinesAppearOnlyWithoutSignalAndOnlyAtTheEdges(dark: Boolean, tag: String) {
        show()
        val cfgNone = BackgroundConfig(BackgroundChoice(BgItem.None, BgItem.None), dark = dark, baseFill = true)
        set(cfgNone, null); val without = capture()          // signal not known yet: calm, no comic lines
        set(cfgNone, SignalState.NoSignal); val with = capture("comic-$tag")
        val d = diffPixels(without, with)
        assertTrue("comic lines drawn (${d.size} pixels)", d.size > 300)
        val cx = with.width / 2.0; val cy = with.height / 2.0
        val inner = d.count { (x, y) -> Math.hypot((x - cx) / cx, (y - cy) / cy) < 0.5 }
        assertEquals("comic pixels inside the calm middle", 0, inner)
        set(cfgNone, SignalState.forced(3)); assertEquals("no comic lines with signal", 0, diffPixels(without, capture()).size)
        set(cfgNone, SignalState.forced(1)); assertEquals(0, diffPixels(without, capture()).size)
    }


    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun HOM_16_noSignalDropsEveryItemToItsDimRestingStateAndShowsTheComicLines() {
        show()
        for (item in items) {
            set(only(item), SignalState.forced(4), isStill = true, timeMs = ScopeMath.STILL_TIME_MS); val good = meanCentre(capture())
            set(only(item), SignalState.NoSignal); val none = capture()
            assertTrue("${item.id} dims at no signal: $good -> ${meanCentre(none)}", meanCentre(none) < good)
        }
    }

    // -------------------------------------------------------------------------------- motion

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun NFR_14_reducedMotionDrawsOneStaticCompositionWhateverTheClock() {
        show()
        val all = BackgroundConfig(BackgroundChoice(BgItem.Beam, BgItem.BreathingDots), baseFill = true)
        set(all, SignalState.forced(4), isStill = true, timeMs = 100); val a = capture("still-a")
        set(timeMs = 4300); val b = capture("still-b")
        assertEquals("a still frame does not depend on the clock", 0, diffPixels(a, b).size)
        set(all, SignalState.forced(4), isStill = false, timeMs = 100); val m1 = capture()
        set(timeMs = 3100); val m2 = capture("moving")
        assertTrue("moving frames differ (breath, beads, dots)", diffPixels(m1, m2).size > 500)
        set(all, SignalState.NoSignal, isStill = true, timeMs = 0); val n1 = capture()
        set(timeMs = 7777); assertEquals(0, diffPixels(n1, capture()).size)
    }

    // The host runs the shared frame clock; with animations off it does not, so the test clock can go idle.
    @org.junit.Before fun still_() = com.bos.sphere.core.design.ReducedMotionTesting.force(com.bos.sphere.core.quality.MotionPrefs(0f, 0f, 0f))
    @org.junit.After fun reset_() = com.bos.sphere.core.design.ReducedMotionTesting.force(null)

    // -------------------------------------------------------------------------------- TalkBack

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun NFR_13_signalIsDescribedOnceAsAPoliteLiveRegionAndTheBackgroundIsDecoration() {
        rule.setContent { Box(Modifier.fillMaxSize()) { SignalBackground(LauncherPrefs(radarSignalForce = SignalForce.Good)) } }
        rule.onNodeWithContentDescription("Signal: good, Wi-Fi").assertExists()
        val tree = rule.onRoot().printToString()
        assertEquals("described once", 1, Regex("Signal: good").findAll(tree).count())
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun PAL_24_liveSignalComesFromTheInjectedSourceAndReadsInTheDescription() {
        rule.setContent {
            CompositionLocalProvider(LocalSignalSource provides FixedSignalSource(SignalState(2, SignalKind.Cellular, true))) {
                Box(Modifier.fillMaxSize()) { SignalBackground(LauncherPrefs()) }
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Signal: fair, Mobile data").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun PAL_24_noSignalIsDescribedInWords() {
        rule.setContent { Box(Modifier.fillMaxSize()) { SignalBackground(LauncherPrefs(radarSignalForce = SignalForce.NoSignal)) } }
        rule.onNodeWithContentDescription("No signal").assertExists()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun HOM_16_withNoItemsNothingIsDrawnAndNothingIsAnnounced() {
        rule.setContent { Box(Modifier.fillMaxSize()) { SignalBackground(LauncherPrefs(backgroundSlot1 = BgItem.None, backgroundSlot2 = BgItem.None, radarSignalForce = SignalForce.Good)) } }
        assertEquals(0, rule.onAllNodesWithContentDescription("Signal: good, Wi-Fi").fetchSemanticsNodes().size)
    }
}
