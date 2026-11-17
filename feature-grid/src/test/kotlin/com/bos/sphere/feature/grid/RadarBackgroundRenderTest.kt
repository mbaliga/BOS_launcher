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
 * PAL-24, NFR-15: the Radar background drawn for real (Robolectric native graphics) in every signal state, portrait and
 * landscape, dark and light. Screenshots go to `build/screenshots/radar-bg-*.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RadarBackgroundRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private val cfg = mutableStateOf(RadarBackdropConfig(baseFill = true))
    private val sig = mutableStateOf<SignalState?>(null)
    private val still = mutableStateOf(true)
    private val clock = RadarClock()

    private fun set(config: RadarBackdropConfig? = null, signal: SignalState? = sig.value, isStill: Boolean = still.value, timeMs: Long? = null) {
        rule.runOnIdle {
            Snapshot.withMutableSnapshot {
                config?.let { cfg.value = it }
                sig.value = signal; still.value = isStill
                if (timeMs != null) clock.ms.longValue = timeMs
            }
        }
        rule.waitForIdle()
    }

    private fun show() = rule.setContent { Box(Modifier.fillMaxSize()) { RadarBackground(cfg.value, sig, clock, still.value) } }

    private fun capture(name: String? = null): Bitmap {
        rule.waitForIdle()
        val view = rule.activity.findViewById<View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        if (name != null) File(outDir, "radar-bg-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
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

    private val states = listOf("nosignal" to SignalState.NoSignal, "weak" to SignalState.forced(1), "fair" to SignalState.forced(2), "good" to SignalState.forced(3), "excellent" to SignalState.forced(4))

    // -------------------------------------------------------------------------------- the dot grid

    private fun dotsOnly(dark: Boolean) = RadarBackdropConfig(sweep = false, viewId = SignalViews.OFF, comic = false, dark = dark, baseFill = true)

    private fun dotsAreBrightInTheMiddleAndInvisibleAtTheEdges(tag: String, dark: Boolean) {
        show(); set(dotsOnly(dark), SignalState.forced(3))
        val b = capture("dots-$tag")
        val w = b.width; val h = b.height
        val centre = mean(b, w * 35 / 100, h * 40 / 100, w * 65 / 100, h * 60 / 100)
        val edge = mean(b, 0, 0, w, h / 20)
        val base = lum(if (dark) 0xFF000000.toInt() else 0xFFF3F3F6.toInt())
        // Dark: dots add light to black (centre brighter). Light: dots add ink to white (centre darker).
        if (dark) { assertTrue("centre $centre vs edge $edge", centre > edge + 0.0008); assertEquals(base, edge, 0.002) }
        else { assertTrue("centre $centre vs edge $edge", centre < edge - 0.0008); assertEquals(base, edge, 0.01) }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun PAL_24_dotGridPortraitDark() = dotsAreBrightInTheMiddleAndInvisibleAtTheEdges("port-dark", true)

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-mdpi")
    fun PAL_24_dotGridLandscapeDark() = dotsAreBrightInTheMiddleAndInvisibleAtTheEdges("land-dark", true)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun PAL_24_dotGridPortraitLight() = dotsAreBrightInTheMiddleAndInvisibleAtTheEdges("port-light", false)

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-mdpi")
    fun PAL_24_dotGridLandscapeLight() = dotsAreBrightInTheMiddleAndInvisibleAtTheEdges("land-light", false)

    // -------------------------------------------------------------------------------- every signal state

    private fun everyState(tag: String, dark: Boolean) {
        show()
        val glowOnly = RadarBackdropConfig(sweep = true, viewId = SignalViews.GLOW, comic = false, dark = dark, baseFill = true)
        val blue = LinkedHashMap<String, Int>()
        for ((name, s) in states) {
            set(glowOnly, s)
            val b = capture("$name-$tag")
            blue[name] = bluePixels(b)
        }
        assertEquals("no pulse without signal", 0, blue["nosignal"])
        assertTrue("weak shows something ${blue}", blue["weak"]!! > 0)
        assertTrue("levels add light ${blue}", blue["weak"]!! < blue["fair"]!! && blue["fair"]!! < blue["good"]!! && blue["good"]!! < blue["excellent"]!!)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun PAL_24_everySignalStatePortraitDark() = everyState("port-dark", true)

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-mdpi")
    fun PAL_24_everySignalStateLandscapeDark() = everyState("land-dark", true)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun PAL_24_everySignalStatePortraitLight() = everyState("port-light", false)

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-mdpi")
    fun PAL_24_everySignalStateLandscapeLight() = everyState("land-light", false)

    // -------------------------------------------------------------------------------- comic lines

    private fun comicLinesAppearOnlyWithoutSignalAndOnlyAtTheEdges(dark: Boolean, tag: String) {
        show()
        val on = RadarBackdropConfig(sweep = false, viewId = SignalViews.OFF, comic = true, dark = dark, baseFill = true)
        val off = on.copy(comic = false)
        set(off, SignalState.NoSignal); val without = capture()
        set(on, SignalState.NoSignal); val with = capture("comic-$tag")
        val d = diffPixels(without, with)
        assertTrue("comic lines drawn (${d.size} pixels)", d.size > 300)
        val cx = with.width / 2.0; val cy = with.height / 2.0
        // Every changed pixel lies in the outer part of the screen (normalised radius at least 0.5): the middle stays calm.
        val inner = d.count { (x, y) -> Math.hypot((x - cx) / cx, (y - cy) / cy) < 0.5 }
        assertEquals("comic pixels inside the calm middle", 0, inner)
        // With signal they are gone again.
        set(on, SignalState.forced(3)); val signalBack = capture()
        assertEquals("no comic lines with signal", 0, diffPixels(without, signalBack).size)
        // Weak signal is still signal.
        set(on, SignalState.forced(1)); assertEquals(0, diffPixels(without, capture()).size)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun PAL_24_comicLinesOnlyAtNoSignalPortrait() = comicLinesAppearOnlyWithoutSignalAndOnlyAtTheEdges(true, "port")

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-mdpi")
    fun PAL_24_comicLinesOnlyAtNoSignalLandscape() = comicLinesAppearOnlyWithoutSignalAndOnlyAtTheEdges(true, "land")

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun PAL_24_comicLinesOnlyAtNoSignalLight() = comicLinesAppearOnlyWithoutSignalAndOnlyAtTheEdges(false, "port-light")

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun PAL_24_comicSettingOffKeepsTheEdgesClearAtNoSignal() {
        show()
        val off = RadarBackdropConfig(sweep = false, viewId = SignalViews.OFF, comic = false, baseFill = true)
        set(off, SignalState.NoSignal); val a = capture()
        set(off.copy(comic = true), SignalState.NoSignal); val b = capture()
        assertTrue(diffPixels(a, b).isNotEmpty())
        set(off, SignalState.NoSignal); assertEquals(0, diffPixels(a, capture()).size)
    }

    // -------------------------------------------------------------------------------- motion

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun NFR_14_reducedMotionDrawsOneStaticCompositionWhateverTheClock() {
        show()
        val all = RadarBackdropConfig(baseFill = true)
        set(all, SignalState.forced(4), isStill = true, timeMs = 100); val a = capture("still-a")
        set(timeMs = 4300); val b = capture("still-b")
        assertEquals("a still frame does not depend on the clock", 0, diffPixels(a, b).size)
        set(all, SignalState.forced(4), isStill = false, timeMs = 100); val m1 = capture()
        set(timeMs = 3100); val m2 = capture("moving")
        assertTrue("moving frames differ (sweep, breath, pulses)", diffPixels(m1, m2).size > 500)
        // A still no-signal frame keeps its comic lines (one static composition each).
        set(all, SignalState.NoSignal, isStill = true, timeMs = 0); val n1 = capture()
        set(timeMs = 7777); assertEquals(0, diffPixels(n1, capture()).size)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun PAL_24_sweepOffRemovesTheScopeAndKeepsTheDots() {
        show()
        val on = RadarBackdropConfig(sweep = true, viewId = SignalViews.OFF, comic = false, baseFill = true)
        set(on, SignalState.forced(3), isStill = false, timeMs = 1000); val withSweep = capture("sweep-on")
        set(on.copy(sweep = false)); val without = capture("sweep-off")
        assertNotEquals(0, diffPixels(withSweep, without).size)
        val dots = capture(); assertTrue(mean(dots, 0, dots.height * 2 / 5, dots.width, dots.height * 3 / 5) > 0.0)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun PAL_24_intensitySubtleIsFainterThanNormal() {
        show()
        val normal = RadarBackdropConfig(viewId = SignalViews.GLOW, comic = false, baseFill = true, intensity = RadarIntensity.Normal)
        set(normal, SignalState.forced(4)); val n = capture()
        set(normal.copy(intensity = RadarIntensity.Subtle)); val s = capture()
        assertTrue(mean(s, 0, 0, s.width, s.height) < mean(n, 0, 0, n.width, n.height))
    }

    // -------------------------------------------------------------------------------- legibility (NFR-15)

    private fun legibility(dark: Boolean, tag: String) {
        show()
        val text = if (dark) 0xFFE7E9F0.toInt() else 0xFF1A1A22.toInt()
        var worst = 99.0
        for ((name, s) in states) for (t in listOf(0L, 1500L, 2500L, 3600L, 4700L)) {
            set(RadarBackdropConfig(dark = dark, baseFill = true), s, isStill = false, timeMs = t)
            val b = capture()
            // The worst pixel for the text: the brightest on a dark Radar, the darkest on a light one.
            var extreme = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            var at = 0 to 0
            for (y in 0 until b.height) for (x in 0 until b.width) {
                val c = b.getPixel(x, y)
                if (dark) { if (lum(c) > lum(extreme)) { extreme = c; at = x to y } } else if (lum(c) < lum(extreme)) { extreme = c; at = x to y }
            }
            val r = Contrast.ratio(text, extreme)
            worst = minOf(worst, r)
            assertTrue("$tag $name t=$t: contrast $r at $at colour ${Integer.toHexString(extreme)}", r >= Contrast.AA_NORMAL)
        }
        println("NFR-15 radar background $tag worst-case contrast ${"%.2f".format(worst)}")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun NFR_15_radarBackgroundKeepsResultTextAtAaDark() = legibility(true, "dark")

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun NFR_15_radarBackgroundKeepsResultTextAtAaLight() = legibility(false, "light")

    // -------------------------------------------------------------------------------- TalkBack

    // The host runs the shared frame clock; with animations off it does not, so the test clock can go idle.
    @org.junit.Before fun still_() = com.bos.sphere.core.design.ReducedMotionTesting.force(com.bos.sphere.core.quality.MotionPrefs(0f, 0f, 0f))
    @org.junit.After fun reset_() = com.bos.sphere.core.design.ReducedMotionTesting.force(null)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun NFR_13_signalIsDescribedOnceAsAPoliteLiveRegionAndTheBackgroundIsDecoration() {
        rule.setContent { Box(Modifier.fillMaxSize()) { RadarBackgroundHost(LauncherPrefs(radarSignalForce = SignalForce.Good)) } }
        rule.onNodeWithContentDescription("Signal: good, Wi-Fi").assertExists()
        val tree = rule.onRoot().printToString()
        assertEquals("described once", 1, Regex("Signal: good").findAll(tree).count())
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun PAL_24_liveSignalComesFromTheInjectedSourceAndReadsInTheDescription() {
        rule.setContent {
            CompositionLocalProvider(LocalSignalSource provides FixedSignalSource(SignalState(2, SignalKind.Cellular, true))) {
                Box(Modifier.fillMaxSize()) { RadarBackgroundHost(LauncherPrefs()) }
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Signal: fair, Mobile data").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
    fun PAL_24_noSignalIsDescribedInWords() {
        rule.setContent { Box(Modifier.fillMaxSize()) { RadarBackgroundHost(LauncherPrefs(radarSignalForce = SignalForce.NoSignal)) } }
        rule.onNodeWithContentDescription("No signal").assertExists()
    }
}
