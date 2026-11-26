package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.CornerSlot
import com.bos.sphere.core.data.CornerStyle
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.MusicLineWidth
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.ThemeVariant
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

/** Wave 59 (glanceTop), CFG-75 / CFG-76: the pure width and fade rules. */
class GlanceTopRulesW59Test {
    @Test fun CFG_76_fullWidthRunsEdgeToEdgeWhateverTheCorners() {
        for (tl in listOf(true, false)) for (tr in listOf(true, false)) {
            assertEquals("$tl $tr", 411f - 2 * FixedHomeChrome.EDGE_MARGIN_DP, FixedHomeChrome.titleWidthDp(411f, tl, tr, MusicLineWidth.FullWidth), 0.01f)
        }
        // Fit around corners keeps the old rule: a drawn top corner caps the line at 280 dp, none runs edge to edge
        assertEquals(280f, FixedHomeChrome.titleWidthDp(411f, true, false, MusicLineWidth.FitAroundCorners), 0.01f)
        assertEquals(411f - 24f, FixedHomeChrome.titleWidthDp(411f, false, false, MusicLineWidth.FitAroundCorners), 0.01f)
        assertEquals("the default is Fit around corners", 280f, FixedHomeChrome.titleWidthDp(411f, true, true), 0.01f)
        assertEquals("12 dp margins", 12f, FixedHomeChrome.EDGE_MARGIN_DP, 0f)
    }

    @Test fun CFG_75_onlyAClippedEdgeFades() {
        assertEquals("a line that fits fades nowhere", false to false, MarqueeFade.edges(scrolling = false, moved = false))
        assertEquals("the end is clipped as soon as the text overflows", false to true, MarqueeFade.edges(scrolling = true, moved = false))
        assertEquals("the start once the text moves", true to true, MarqueeFade.edges(scrolling = true, moved = true))
    }

    @Test fun CFG_77_arrowsMoveBetweenTheFourCornersAndStopAtTheEdges() {
        val m = HotspotsEditorModel
        val a = HotspotsEditorModel.Arrow.entries
        assertEquals(Corner.TopRight, m.move(Corner.TopLeft, HotspotsEditorModel.Arrow.Right))
        assertEquals(Corner.BottomLeft, m.move(Corner.TopLeft, HotspotsEditorModel.Arrow.Down))
        assertEquals(Corner.TopLeft, m.move(Corner.TopLeft, HotspotsEditorModel.Arrow.Left))
        assertEquals(Corner.TopLeft, m.move(Corner.TopLeft, HotspotsEditorModel.Arrow.Up))
        assertEquals(Corner.BottomLeft, m.move(Corner.BottomRight, HotspotsEditorModel.Arrow.Left))
        // from any corner the four arrows reach itself and its two neighbours, never the opposite corner
        for (c in Corner.entries) assertEquals(3, a.map { m.move(c, it) }.toSet().size)
    }

    @Test fun CFG_79_theOrientationInViewPicksTheRotationAndTheCornerSet() {
        val m = HotspotsEditorModel
        assertEquals("the device's own rotation for the orientation it is in", 3, m.viewRotation(true, true, 3))
        assertEquals("the natural landscape from portrait", 1, m.viewRotation(true, false, 0))
        assertEquals("portrait from landscape", 0, m.viewRotation(false, true, 1))
        val p = mapOf(Corner.TopLeft to CornerSlot.Single(CornerItem.Action(CornerActionId.Camera)))
        val l = mapOf(Corner.TopLeft to CornerSlot.Single(CornerItem.Action(CornerActionId.Flashlight)))
        assertEquals(l, m.slotsFor(viewLandscape = true, separate = true, portrait = p, landscape = l))
        assertEquals("one set when separate corners are off", p, m.slotsFor(viewLandscape = true, separate = false, portrait = p, landscape = l))
        assertTrue(m.editsLandscape(true, true)); assertFalse(m.editsLandscape(true, false)); assertFalse(m.editsLandscape(false, true))
        // hotspots stay physical: at rotation 1 the screen top left is the physical top right
        assertEquals(Corner.TopRight, m.physicalAt(Corner.TopLeft, 1, followDevice = true))
        assertEquals(Corner.TopLeft, m.physicalAt(Corner.TopLeft, 1, followDevice = false))
    }
}

/**
 * Wave 59 (glanceTop), CFG-75: the marquee's edge fade paints no colour. The line is drawn over a white and over a patterned background, in the dark
 * and the light theme; at the clipped ends the picture must be the background itself (no dark block: the mean luminance there is at least the background's,
 * minus a rounding margin). Without the alpha mask (the old gradient to black, or to a pale grey in light) these fail. Screenshots: build/screenshots/w59_*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
class GlanceTopFadeW59PixelTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    @After fun reset() { androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) } }

    private val media = MediaInfo("Lose Yourself to Dance (Drumless Edition) (feat. Pharrell Williams) and a long tail", "Daft Punk", "pkg.music", playing = true, canPrev = true, canNext = true)
    private val showLine = mutableIntStateOf(1)

    /** The microscope-like busy light pattern: pale stripes and dots over white. */
    private fun Modifier.pattern(white: Boolean) = if (white) background(Color.White) else drawBehind {
        drawRect(Color(0xFFF4F1E8))
        var x = 0f; var i = 0
        while (x < size.width) { drawRect(if (i % 2 == 0) Color(0xFFD9E6F5) else Color(0xFFF7D9C4), Offset(x, 0f), Size(18f, size.height)); x += 18f; i++ }
        var y = 6f; while (y < size.height) { var xx = 4f; while (xx < size.width) { drawCircle(Color(0xFF9AA7B8), 3f, Offset(xx, y)); xx += 23f }; y += 17f }
    }

    private fun compose(white: Boolean, dark: Boolean) {
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { HyleTheme.apply(if (dark) ThemeVariant.Dark else ThemeVariant.Light, 0xFF8E7BFF) }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.fillMaxSize().pattern(white)) {
                Box(Modifier.fillMaxWidth().height(60.dp).padding(top = 10.dp).testTag("w59.slot"), contentAlignment = Alignment.TopCenter) {
                    if (showLine.intValue == 1) NowPlayingLine(media, GlanceInk.secondary(dark), marquee = true, onActivate = {}, maxWidth = 387.dp)
                }
            }
        }
        rule.mainClock.advanceTimeBy(2600) // past the marquee's start delay: the text moves, both ends are clipped
    }

    /** What is really drawn: a window copy with PixelCopy (the hardware path that honours the offscreen layer), cut to the content view. */
    private fun bitmap(): Bitmap {
        val win = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(win.width, win.height, Bitmap.Config.ARGB_8888)
        val latch = java.util.concurrent.CountDownLatch(1)
        var status = -1
        val thread = android.os.HandlerThread("copy").also { it.start() }
        rule.runOnUiThread { android.view.PixelCopy.request(rule.activity.window, bmp, { status = it; latch.countDown() }, android.os.Handler(thread.looper)) }
        assertTrue("window copy finished", latch.await(10, java.util.concurrent.TimeUnit.SECONDS))
        thread.quit()
        assertEquals(android.view.PixelCopy.SUCCESS, status)
        val content = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val o = IntArray(2).also { content.getLocationInWindow(it) }
        return Bitmap.createBitmap(bmp, o[0], o[1], content.width, content.height)
    }

    private fun lum(c: Int): Double { val r = (c shr 16 and 255) / 255.0; val g = (c shr 8 and 255) / 255.0; val b = (c and 255) / 255.0; return 0.2126 * r + 0.7152 * g + 0.0722 * b }

    /** Mean luminance of [cols] columns at x from [x0] over the text's rows, and the mean channel difference against [bg]. */
    private fun ends(img: Bitmap, bg: Bitmap, x0: Int, cols: IntRange, r: Rect): Pair<Double, Double> {
        var l = 0.0; var lb = 0.0; var d = 0.0; var n = 0
        for (dx in cols) for (y in r.top.toInt() until r.bottom.toInt()) {
            val x = x0 + dx; val a = img.getPixel(x, y); val b = bg.getPixel(x, y)
            l += lum(a); lb += lum(b)
            d += (Math.abs((a shr 16 and 255) - (b shr 16 and 255)) + Math.abs((a shr 8 and 255) - (b shr 8 and 255)) + Math.abs((a and 255) - (b and 255))) / (3 * 255.0)
            n++
        }
        return (l - lb) / n to d / n
    }

    private fun check(white: Boolean, dark: Boolean) {
        compose(white, dark)
        val node = rule.onNodeWithTag("glance.title.marquee", useUnmergedTree = true).fetchSemanticsNode()
        val r = node.boundsInRoot
        val withLine = bitmap()
        val name = "w59_line_${if (white) "white" else "pattern"}_${if (dark) "dark" else "light"}"
        File(outDir, "$name.png").outputStream().use { withLine.compress(Bitmap.CompressFormat.PNG, 100, it) }
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { showLine.intValue = 0 }
        rule.mainClock.advanceTimeBy(100)
        val bg = bitmap()
        val left = r.left.toInt(); val right = r.right.toInt() - 3
        for ((edge, x0) in listOf("start" to left, "end" to right)) {
            val (dl, diff) = ends(withLine, bg, x0, 0..2, r)
            assertTrue("$name $edge: no dark block (luminance ${"%.3f".format(dl)} below the background)", dl >= -0.04)
            assertTrue("$name $edge: no colour painted (mean difference ${"%.3f".format(diff)})", diff < 0.06)
        }
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { showLine.intValue = 1 }
    }

    @Test fun CFG_75_theFadeShowsAWhiteWallpaperThroughInTheDarkTheme() = check(white = true, dark = true)
    @Test fun CFG_75_theFadeShowsAWhiteWallpaperThroughInTheLightTheme() = check(white = true, dark = false)
    @Test fun CFG_75_theFadeShowsAPatternedWallpaperThroughInTheDarkTheme() = check(white = false, dark = true)
    @Test fun CFG_75_theFadeShowsAPatternedWallpaperThroughInTheLightTheme() = check(white = false, dark = false)
}

/** Wave 59 (glanceTop), CFG-75 / CFG-76: the fixed chrome's line width and the corners' touch over the line's ends. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
class GlanceTopChromeW59UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private val media = MediaInfo("Lose Yourself to Dance (Drumless Edition) (feat. Pharrell Williams) and a long tail", "Daft Punk", "pkg.music", playing = true, canPrev = true, canNext = true)
    private var runs = 0

    private fun chrome(width: MusicLineWidth, topDrawn: Boolean) {
        rule.mainClock.autoAdvance = false
        val placements = listOf(
            CornerPlacement(Corner.TopLeft, Corner.TopLeft, CornerSlot.Single(CornerItem.Action(CornerActionId.Camera))),
            CornerPlacement(Corner.TopRight, Corner.TopRight, CornerSlot.Single(CornerItem.Action(CornerActionId.Music))),
        )
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFFEDEAE0))) {
                FixedHomeChrome(
                    media = media, showMusic = true, controlsSeconds = 4, onPrevious = {}, onPlayPause = {}, onNext = {},
                    corners = { CornerLayer(placements, CornerStyle.Glyph, emptyMap(), { _, _ -> runs++ }, {}, { _, _, _ -> }, { _, _, _ -> }, { _, _ -> }) },
                    dark = false, topLeftHotspot = topDrawn, topRightHotspot = topDrawn, musicWidth = width,
                )
            }
        }
        rule.mainClock.advanceTimeBy(2600)
    }

    private fun d() = rule.activity.resources.displayMetrics.density
    /** A window copy (PixelCopy honours the offscreen fade layer; a software draw does not). */
    private fun windowCopy(): Bitmap {
        val win = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(win.width, win.height, Bitmap.Config.ARGB_8888)
        val latch = java.util.concurrent.CountDownLatch(1)
        val thread = android.os.HandlerThread("copy").also { it.start() }
        rule.runOnUiThread { android.view.PixelCopy.request(rule.activity.window, bmp, { latch.countDown() }, android.os.Handler(thread.looper)) }
        latch.await(10, java.util.concurrent.TimeUnit.SECONDS); thread.quit()
        return bmp
    }
    private fun textWidthDp() = rule.onNodeWithTag("glance.title.marquee", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width / d()

    @Test fun CFG_76_fullWidthRunsTheLineEdgeToEdgeAndTheCornerTouchWinsOverItsEnds() {
        chrome(MusicLineWidth.FullWidth, topDrawn = true)
        // 411 dp less the 12 dp margins, the 12 dp padding each side, the 16 dp note and its 8 dp gap
        assertTrue("the text runs nearly the whole width (${textWidthDp()} dp)", textWidthDp() > 411f - 24f - 24f - 24f - 2f)
        File(outDir, "w59_chrome_fullwidth.png").outputStream().use { windowCopy().compress(Bitmap.CompressFormat.PNG, 100, it) }
        // a tap on the top-left corner glyph (over the line's faded start) runs the corner, it does not pop the music controls in
        rule.onNodeWithContentDescription("Camera").performTouchInput { click(center) }
        rule.mainClock.advanceTimeBy(600)
        assertEquals(1, runs)
        assertTrue("the controls notch stayed away", rule.onAllNodesWithTag("glance.notch", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() ||
            rule.onNodeWithTag("glance.notch", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.bottom <= 1f)
    }

    @Test fun CFG_75_fitAroundCornersKeepsClearOfDrawnCornersOnly() {
        chrome(MusicLineWidth.FitAroundCorners, topDrawn = true)
        assertTrue("drawn top corners: capped at 280 dp (${textWidthDp()})", textWidthDp() <= 280f - 48f + 1f)
    }

    @Test fun CFG_75_withNothingDrawnAtTheTopTheLineRunsEdgeToEdge() {
        chrome(MusicLineWidth.FitAroundCorners, topDrawn = false)
        assertTrue("no drawn top corner: edge to edge (${textWidthDp()})", textWidthDp() > 411f - 72f - 2f)
    }
}
