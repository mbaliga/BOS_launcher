package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.ClockFormat
import com.bos.sphere.core.data.ClockSize
import com.bos.sphere.core.data.ClockStyle
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.ThemeVariant
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Wave 60 (polish60), CFG-80: the halo [GlanceInk.shadow] adds behind Glance text (the music line, the clock digits) so both read over a
 * wallpaper colour the app cannot sample from (a photo or the system's own live wallpaper; see `WallpaperSurfaces.kt`). This is pure math
 * on the engineered values, mirroring `core.quality.Contrast`'s WCAG formula (feature-grid does not depend on core-quality, so the few
 * lines are repeated here, as `GlanceTopFadeW59PixelTest` already repeats its own luminance helper).
 */
class GlanceLegibilityW60Test {
    private fun channel(c: Int): Double { val s = c / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
    private fun luminance(rgb: Triple<Int, Int, Int>) = 0.2126 * channel(rgb.first) + 0.7152 * channel(rgb.second) + 0.0722 * channel(rgb.third)
    private fun ratio(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Double {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
    private fun blend(fg: Triple<Int, Int, Int>, bg: Triple<Int, Int, Int>, alpha: Double) = Triple(
        (fg.first * alpha + bg.first * (1 - alpha)).roundToInt(),
        (fg.second * alpha + bg.second * (1 - alpha)).roundToInt(),
        (fg.third * alpha + bg.third * (1 - alpha)).roundToInt(),
    )
    private fun rgb(c: Color) = Triple((c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt())

    /**
     * CFG-80: a pixel right next to a glyph is, at the halo's own alpha, mostly the halo colour over whatever the wallpaper is. That blend must
     * still read [Contrast.AA_LARGE] (3:1) against the ink the glyph is drawn in, for a wallpaper as bright as literal white and as dark as
     * literal black, in both themes, for both the clock's ink and the music line's (`GlanceInk.secondary`).
     */
    @Test fun CFG_80_theGlanceTextHaloClearsAaLargeOverLiteralWhiteAndBlackInBothThemes() {
        val white = Triple(255, 255, 255); val black = Triple(0, 0, 0)
        for (dark in listOf(true, false)) {
            val shadow = GlanceInk.shadow(dark)
            val halo = rgb(shadow.color)
            val alpha = shadow.color.alpha.toDouble()
            for ((label, ink) in listOf("clock" to GlanceInk.clock(dark), "music line" to GlanceInk.secondary(dark))) {
                val inkRgb = rgb(ink)
                for ((wname, wallpaper) in listOf("white" to white, "black" to black)) {
                    val behindText = blend(halo, wallpaper, alpha)
                    val r = ratio(inkRgb, behindText)
                    assertTrue("dark=$dark $label over $wname wallpaper: ratio $r", r >= 3.0)
                }
            }
        }
    }

    /** The halo is the opposite tone of the ink, not a copy of it (a same-tone halo would do nothing over the same-tone wallpaper). */
    @Test fun CFG_80_theHaloIsTheOppositeToneOfTheInkAndCentred() {
        assertTrue("dark theme: a black halo behind the light ink", GlanceInk.shadow(true).color == Color.Black.copy(alpha = 0.85f))
        assertTrue("light theme: a white halo behind the dark ink", GlanceInk.shadow(false).color == Color.White.copy(alpha = 0.85f))
        assertTrue("centred: no offset, so every edge of a scrolling line reads the same", GlanceInk.shadow(true).offset == androidx.compose.ui.geometry.Offset.Zero)
    }
}

/**
 * Wave 60 (polish60), CFG-80: screenshots of the music line and the clock digits over a literal white and a literal black background, in
 * both themes — the two wallpaper extremes a real photo or live wallpaper can present, which the app cannot sample a colour from (see
 * `WallpaperSurfaces.kt`'s `WallpaperKind.System, WallpaperKind.Picture -> Unit` comment). Screenshots: `build/screenshots/w60_*.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
class GlanceLegibilityW60PixelTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    @After fun reset() { Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) } }

    private val media = MediaInfo("Daft Punk", "Discovery", "pkg.music", playing = true, canPrev = true, canNext = true)

    private fun bitmap(): Bitmap {
        val win = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(win.width, win.height, Bitmap.Config.ARGB_8888)
        val latch = java.util.concurrent.CountDownLatch(1)
        var status = -1
        val thread = android.os.HandlerThread("copy").also { it.start() }
        rule.runOnUiThread { android.view.PixelCopy.request(rule.activity.window, bmp, { status = it; latch.countDown() }, android.os.Handler(thread.looper)) }
        assertTrue("window copy finished", latch.await(10, java.util.concurrent.TimeUnit.SECONDS))
        thread.quit()
        org.junit.Assert.assertEquals(android.view.PixelCopy.SUCCESS, status)
        val content = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val o = IntArray(2).also { content.getLocationInWindow(it) }
        return Bitmap.createBitmap(bmp, o[0], o[1], content.width, content.height)
    }

    private fun lum(c: Int): Double { val r = (c shr 16 and 255) / 255.0; val g = (c shr 8 and 255) / 255.0; val b = (c and 255) / 255.0; return 0.2126 * r + 0.7152 * g + 0.0722 * b }

    /** Mean |luminance delta| between a square of [img] centred at the node's left edge (where a glyph starts) and a flat reference of [bg]. */
    private fun haloPresent(img: Bitmap, r: Rect, bg: Double): Boolean {
        var found = false
        val x0 = r.left.toInt().coerceAtLeast(0); val y0 = r.top.toInt().coerceAtLeast(0)
        val x1 = r.right.toInt().coerceAtMost(img.width - 1); val y1 = r.bottom.toInt().coerceAtMost(img.height - 1)
        for (x in x0 until x1) for (y in y0 until y1) if (Math.abs(lum(img.getPixel(x, y)) - bg) > 0.15) found = true
        return found
    }

    private fun shoot(name: String, white: Boolean, dark: Boolean, tag: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        Snapshot.withMutableSnapshot { HyleTheme.apply(if (dark) ThemeVariant.Dark else ThemeVariant.Light, 0xFF8E7BFF) }
        rule.mainClock.autoAdvance = false
        rule.setContent { Box(Modifier.fillMaxSize().background(if (white) Color.White else Color.Black)) { content() } }
        rule.mainClock.advanceTimeBy(100)
        val node = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val img = bitmap()
        File(outDir, "$name.png").outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("$name: the halo should change pixels under the text away from flat $white", haloPresent(img, node.boundsInRoot, if (white) 1.0 else 0.0))
    }

    @Test fun CFG_80_theMusicLineHaloShowsOverWhiteInTheDarkTheme() = shoot("w60_line_white_dark", white = true, dark = true, tag = "glance.title.static") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { NowPlayingLine(media, GlanceInk.secondary(true), marquee = false, onActivate = {}, maxWidth = 280.dp, dark = true) }
    }
    @Test fun CFG_80_theMusicLineHaloShowsOverBlackInTheLightTheme() = shoot("w60_line_black_light", white = false, dark = false, tag = "glance.title.static") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { NowPlayingLine(media, GlanceInk.secondary(false), marquee = false, onActivate = {}, maxWidth = 280.dp, dark = false) }
    }
    @Test fun CFG_80_theClockHaloShowsOverWhiteInTheDarkTheme() = shoot("w60_clock_white_dark", white = true, dark = true, tag = "glance.clock.hours") {
        GlanceWallpaperClock(ClockStyle.BigDigital, ClockFormat.Hour24, ClockSize.Large, showDate = false, useAccent = false, dark = true, accent = Color(0xFF8E7BFF), onTap = {})
    }
    @Test fun CFG_80_theClockHaloShowsOverBlackInTheLightTheme() = shoot("w60_clock_black_light", white = false, dark = false, tag = "glance.clock.hours") {
        GlanceWallpaperClock(ClockStyle.BigDigital, ClockFormat.Hour24, ClockSize.Large, showDate = false, useAccent = false, dark = false, accent = Color(0xFF8E7BFF), onTap = {})
    }
}
