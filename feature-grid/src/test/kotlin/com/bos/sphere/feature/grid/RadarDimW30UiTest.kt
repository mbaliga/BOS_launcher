package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Process
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.BackgroundChoice
import com.bos.sphere.core.data.BgItem
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.RadarBackground
import com.bos.sphere.core.data.SearchLayout
import com.bos.sphere.core.data.SignalForce
import com.bos.sphere.core.quality.Contrast
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
 * Wave 30 (radarDim, owner build 59: "The wallpapers must dim or use a true black for the radar background, otherwise radar becomes a poor experience"): Radar's own background,
 * Dimmed wallpaper (default) or True black, on the real SearchScreen. The label contrast is measured on the drawn pixels of every background item at every signal level.
 * Screenshots go to build/screenshots/radardim-*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RadarDimW30UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    @Before fun english() { com.bos.sphere.core.design.L10n.setLanguage("en") }

    private val names = List(20) { "Clau%02d".format(it) }
    private val apps = names.map { n -> AppEntry("demo.$n", ComponentName("demo.$n", "demo.$n.Main"), n, Process.myUserHandle()) { null } }

    private var prefs by mutableStateOf(LauncherPrefs(searchMotion = false, searchLayoutPinned = true))
    private var query by mutableStateOf("")
    private var probe by mutableStateOf(false)
    private var probeZone by mutableStateOf<Rect?>(null)
    private var epoch by mutableStateOf(0)

    private fun open() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            if (probe) {
                // The background layer alone on its black base: nothing but the background's own pixels.
                Box(Modifier.fillMaxSize().background(Color.Black)) { RadarBackgroundLayer(prefs, { probeZone }) }
            } else androidx.compose.runtime.key(epoch, query, prefs.searchLayout) {
                SearchScreen(
                    apps = apps, recent = apps, prefs = prefs, contacts = null,
                    onLaunch = {}, onLongPressApp = {}, onOpenIntent = {}, onRequestContactsPermission = {}, onDismiss = {},
                    hub = null, onOpenHit = null, onRunAction = {}, onRequestFilePermission = {}, sounds = null, initialQuery = query,
                    launchHistory = RadarTestHabits.history(apps), learnedOverride = emptyMap(), nowMillis = { RadarTestHabits.NOW }, voiceAvailable = true,
                )
            }
        }
        settle()
    }

    private fun settle() { rule.mainClock.advanceTimeBy(1_500) }
    private fun set(block: () -> Unit) { rule.runOnIdle { Snapshot.withMutableSnapshot(block) }; settle() }

    private fun capture(name: String? = null): Bitmap {
        rule.mainClock.advanceTimeBy(900)
        val view = rule.activity.findViewById<View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        if (name != null) File(outDir, "radardim-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    private fun wheelBox(): Rect = rule.onNodeWithTag(RADAR_WHEEL_TAG).fetchSemanticsNode().boundsInRoot
    private fun rowBox(): Rect = rule.onNodeWithTag(RADAR_WHEEL_ROW_TAG).fetchSemanticsNode().boundsInRoot
    private fun searchBox(): Rect = rule.onNodeWithText("SEARCH", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun labelBoxes() = listOf(wheelBox(), searchBox())
    private fun px(b: Bitmap, r: Rect, f: (Int) -> Unit) {
        for (y in r.top.toInt().coerceAtLeast(0) until r.bottom.toInt().coerceAtMost(b.height)) for (x in r.left.toInt().coerceAtLeast(0) until r.right.toInt().coerceAtMost(b.width)) f(b.getPixel(x, y))
    }
    private fun lum(c: Int) = Contrast.luminance(c)
    private fun maxLum(b: Bitmap, r: Rect): Double { var m = 0.0; px(b, r) { m = maxOf(m, lum(it)) }; return m }
    private fun maxChroma(b: Bitmap, r: Rect): Int { var m = 0; px(b, r) { val rr = (it shr 16) and 255; val g = (it shr 8) and 255; val bl = it and 255; m = maxOf(m, maxOf(rr, g, bl) - minOf(rr, g, bl)) }; return m }
    private fun maxChannel(b: Bitmap, r: Rect): Int { var m = 0; px(b, r) { m = maxOf(m, maxOf((it shr 16) and 255, (it shr 8) and 255, it and 255)) }; return m }

    private fun chan(s: Double) = if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    private fun lumD(r: Double, g: Double, bl: Double) = 0.2126 * chan(r) + 0.7152 * chan(g) + 0.0722 * chan(bl)

    /** The lowest contrast of white text at [alpha] over any background pixel in [r] (exact, not rounded to 8 bits: the drawn text is blended in float). */
    private fun worstContrast(b: Bitmap, r: Rect, alpha: Float): Double {
        var worst = Double.MAX_VALUE
        px(b, r) { c ->
            val rr = ((c shr 16) and 255) / 255.0; val g = ((c shr 8) and 255) / 255.0; val bl = (c and 255) / 255.0
            val bg = lumD(rr, g, bl)
            val text = lumD(alpha + (1 - alpha) * rr, alpha + (1 - alpha) * g, alpha + (1 - alpha) * bl)
            worst = minOf(worst, (maxOf(bg, text) + 0.05) / (minOf(bg, text) + 0.05))
        }
        return worst
    }

    private fun typed() { set { query = "jdk" } }
    private fun layout(l: SearchLayout) { set { prefs = prefs.copy(searchLayout = l) } }
    private fun mode(m: RadarBackground) { set { prefs = prefs.copy(radarBackground = m) } }

    // ------------------------------------------------------------------------------------------------ the setting

    @Test fun PAL_24_w30_radarOpensDimmedByDefault() {
        open()
        assertEquals(RadarBackground.Dimmed, prefs.radarBackground)
        rule.onNodeWithTag(RADAR_DIM_TAG).assertExists()
    }

    @Test fun PAL_24_w30_trueBlackComposesNoBackgroundAndEveryWheelPixelIsBlackOrWhiteText() {
        open()
        for (force in listOf(SignalForce.NoSignal, SignalForce.Excellent)) {
            set { prefs = LauncherPrefs(searchMotion = false, searchLayoutPinned = true, radarBackground = RadarBackground.TrueBlack, radarSignalForce = force) }
            rule.onAllNodesDim().assertEmpty()
            val b = capture()
            for (r in listOf(wheelBox(), searchBox(), rowBox())) assertTrue("true black, $force: no colour in the wheel row (chroma ${maxChroma(b, r)})", maxChroma(b, r) <= 2)
            // A pixel of the row that holds no glyph is pure black.
            assertEquals(0xFF000000.toInt(), b.getPixel(rowBox().left.toInt() + 2, rowBox().top.toInt() + 2))
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertEmpty() = assertEquals(0, fetchSemanticsNodes().size)
    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesDim() = onAllNodes(hasTestTag(RADAR_DIM_TAG))

    @Test fun PAL_24_w30_switchingTheSettingSwapsTheBackgroundOnTheRunningScreen() {
        open()
        rule.onNodeWithTag(RADAR_DIM_TAG).assertExists()
        mode(RadarBackground.TrueBlack); rule.onAllNodesDim().assertEmpty()
        mode(RadarBackground.Dimmed); rule.onNodeWithTag(RADAR_DIM_TAG).assertExists()
    }

    private fun everyLayoutAndQuery(m: RadarBackground) {
        // Wave 43: no web rows here (searchWeb = false): this test is about the wheel's background, and the Play Store row (a coloured ring tile) now exists for every query.
        set { prefs = LauncherPrefs(searchMotion = false, searchLayoutPinned = true, radarBackground = m, radarSignalForce = SignalForce.Excellent, searchWeb = false) }
        open()
        for (l in SearchLayout.entries) for (q in listOf("", "jdk")) {
            set { query = q; prefs = prefs.copy(searchLayout = l) }
            if (m == RadarBackground.TrueBlack) rule.onAllNodesDim().assertEmpty() else rule.onNodeWithTag(RADAR_DIM_TAG).assertExists()
            val b = capture()
            val bright = hasTestTag(RADAR_WHEEL_ROW_TAG)
            if (rule.onAllNodes(bright).fetchSemanticsNodes().isNotEmpty()) {
                val box = rowBox()
                val where = "$m $l query='$q' ${b.width}x${b.height}"
                if (m == RadarBackground.TrueBlack) assertTrue("$where chroma", maxChroma(b, box) <= 2)
                else {
                    // Dimmed: the brightest pixel in the wheel row stays under the cap (text glyphs included, which are white, so measure beside them: the left strip).
                    val strip = Rect(box.left, box.top, box.left + 40f, box.bottom)
                    assertTrue("$where: the wallpaper at the row's left edge is at most a quarter bright (${maxChannel(b, strip)})", maxChannel(b, strip) <= 255 * RadarDim.brightestAnywhere + 2)
                }
            }
        }
    }

    @Test fun PAL_24_w30_trueBlackAppliesToEveryLayout_portrait() = everyLayoutAndQuery(RadarBackground.TrueBlack)
    @Test fun PAL_24_w30_dimmedAppliesToEveryLayout_portrait() = everyLayoutAndQuery(RadarBackground.Dimmed)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_24_w30_trueBlackAppliesToEveryLayout_landscape() = everyLayoutAndQuery(RadarBackground.TrueBlack)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_24_w30_dimmedAppliesToEveryLayout_landscape() = everyLayoutAndQuery(RadarBackground.Dimmed)

    // ------------------------------------------------------------------------------------------------ the measured contrast

    private fun measureEveryItemAndLevel(tag: String) {
        set { prefs = LauncherPrefs(searchMotion = false, searchLayoutPinned = true, searchLayout = SearchLayout.Orbit) }
        open()
        val zone = RadarDim.wheelZone(rowBox(), rule.activity.resources.displayMetrics.density)
        val boxes = labelBoxes()
        for (b in boxes) assertTrue("the label box $b is inside the capped plateau of $zone", RadarDim.fullyCapped(zone, b))
        set { probeZone = zone; probe = true }
        val items = BgItem.entries.filter { it != BgItem.None }
        val report = StringBuilder()
        for (item in items) for (level in 0..4) {
            val force = SignalForce.entries.first { it.level == level }
            set { prefs = prefs.copy(backgroundSlot1 = item, backgroundSlot2 = BgItem.None, radarSignalForce = force, radarBackground = RadarBackground.Dimmed) }
            val bmp = capture(if (item == BgItem.Spire && (level == 4 || level == 0)) "probe-spire-L$level-$tag" else null)
            for (b in boxes) {
                val sel = worstContrast(bmp, b, RadarDim.SELECTED_LABEL_ALPHA)
                val nb = worstContrast(bmp, b, RadarDim.NEIGHBOUR_LABEL_ALPHA)
                val top = maxLum(bmp, b)
                report.append("${item.id} L$level $tag max-lum=${"%.4f".format(top)} sel=${"%.2f".format(sel)} neighbour=${"%.2f".format(nb)}\n")
                assertTrue("${item.id} level $level $tag: selected label contrast $sel < 4.5", sel >= RadarDim.SELECTED_MIN_CONTRAST)
                assertTrue("${item.id} level $level $tag: neighbour label contrast $nb < 3", nb >= RadarDim.NEIGHBOUR_MIN_CONTRAST)
                assertTrue("${item.id} level $level $tag: brightest pixel behind the wheel labels $top", top <= 0.0075)
            }
        }
        File(outDir, "radardim-measured-$tag.txt").writeText(report.toString())
    }

    @Test fun PAL_24_w30_everyWallpaperItemAtEverySignalLevelKeepsTheWheelLabelsReadable_portrait() = measureEveryItemAndLevel("portrait")
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_24_w30_everyWallpaperItemAtEverySignalLevelKeepsTheWheelLabelsReadable_landscape() = measureEveryItemAndLevel("landscape")

    @Test fun PAL_24_w30_dimmedWallpaperIsNeverBrighterThanAQuarterOfTheUndimmedOne() {
        set { prefs = LauncherPrefs(searchMotion = false, searchLayoutPinned = true, radarSignalForce = SignalForce.Excellent) }
        open()
        set { probeZone = null; probe = true }
        for (item in BgItem.entries.filter { it != BgItem.None }) {
            set { prefs = prefs.copy(backgroundSlot1 = item, backgroundSlot2 = BgItem.None) }
            val b = capture()
            val w = Rect(0f, 0f, b.width.toFloat(), b.height.toFloat())
            assertTrue("${item.id}: brightest channel ${maxChannel(b, w)}", maxChannel(b, w) <= 255 * 0.25 + 2)
        }
    }

    // ------------------------------------------------------------------------------------------------ screenshots (READ them)

    private fun shot(tag: String) {
        set { prefs = LauncherPrefs(searchMotion = false, searchLayoutPinned = true, searchLayout = SearchLayout.Orbit, radarSignalForce = SignalForce.Excellent, radarBackground = RadarBackground.Dimmed) }
        open()
        for (m in RadarBackground.entries) for (q in listOf("", "jdk")) {
            set { query = q; prefs = prefs.copy(radarBackground = m) }
            if (q.isNotEmpty()) runCatching { rule.onNodeWithText("FILES", useUnmergedTree = true).performClick(); settle() }
            capture("${m.name}-${if (q.isEmpty()) "empty" else "jdk"}-$tag")
        }
    }

    @Test fun PAL_24_w30_screenshots_portrait() = shot("portrait")
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_24_w30_screenshots_landscape() = shot("landscape")
}
