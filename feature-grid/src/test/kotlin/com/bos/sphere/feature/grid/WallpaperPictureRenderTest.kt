package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.BackgroundChoice
import com.bos.sphere.core.data.BgItem
import com.bos.sphere.core.data.GifMode
import com.bos.sphere.core.data.GradientSpec
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.PictureFit
import com.bos.sphere.core.data.PictureSpec
import com.bos.sphere.core.data.RadarBackground
import com.bos.sphere.core.data.SignalForce
import com.bos.sphere.core.data.Wallpaper
import com.bos.sphere.core.data.WallpaperKind
import com.bos.sphere.core.data.WallpaperSurface
import com.bos.sphere.core.data.wallpaper
import com.bos.sphere.core.data.withWallpaper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Wave 34 (wallpaper): drawing. Fit and Fill geometry, the dim, the missing-file fallback, the frame player by the real delays in each mode (fake frames: red, green, blue),
 * the pause while the app is not visible, the system live wallpaper layer draws nothing opaque, and the spider-sense alert over EVERY wallpaper kind and on Radar (Dimmed and
 * True black). Screenshots: build/screenshots/wallpaper34-render-*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h720dp-xhdpi")
class WallpaperPictureRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private var prefs by mutableStateOf(LauncherPrefs())

    @Before fun setUp() { com.bos.sphere.core.design.L10n.setLanguage("en"); PictureFiles.dir(ctx).deleteRecursively() }
    @After fun tearDown() { com.bos.sphere.core.design.ReducedMotionTesting.force(null); PictureFiles.dir(ctx).deleteRecursively() }

    private fun still() = com.bos.sphere.core.design.ReducedMotionTesting.force(com.bos.sphere.core.quality.MotionPrefs(0f, 0f, 0f))
    private var hosted: (@androidx.compose.runtime.Composable () -> Unit) by mutableStateOf({})
    private var hostStarted = false
    /** setContent can be called once per test: later calls swap the content of the one host. */
    private fun setHost(content: @androidx.compose.runtime.Composable () -> Unit) {
        if (!hostStarted) { hostStarted = true; hosted = content; rule.setContent { hosted() } } else { rule.runOnUiThread { hosted = content }; }
        rule.waitForIdle()
    }
    private fun solidBitmap(c: Int, w: Int = 20, h: Int = 10) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.eraseColor(c) }.asImageBitmap()
    private fun shot(name: String, b: Bitmap) { File(outDir, "wallpaper34-render-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    private fun bitmap(): Bitmap {
        rule.waitForIdle()
        val view = rule.activity.findViewById<View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        return bmp
    }
    /** A picture loads on the IO dispatcher (real threads), so under load the first frames can be empty: wait, bounded, until [ok]. */
    private fun await(ok: (Bitmap) -> Boolean = { Color.alpha(it.getPixel(it.width / 2, it.height / 2)) != 0 }): Bitmap {
        var b = bitmap(); var n = 0
        while (!ok(b) && n++ < 200) { Thread.sleep(25); rule.mainClock.advanceTimeBy(50); b = bitmap() }
        return b
    }
    private fun file(name: String) = File(PictureFiles.dir(ctx).also { it.mkdirs() }, name).also { it.writeBytes(byteArrayOf(1)) }

    private class FakeLoader(val still: ImageBitmap? = null, val set: FrameSet? = null) : PictureLoader {
        override suspend fun still(file: File, video: Boolean, screenW: Int, screenH: Int) = still
        override suspend fun info(file: File) = set?.let { com.bos.sphere.core.data.AnimationInfo(it.frames.size, it.delaysMs, it.width, it.height) }
        override suspend fun frames(file: File, info: com.bos.sphere.core.data.AnimationInfo, screenW: Int, screenH: Int) = set
        override suspend fun stream(file: File): StreamAnim? = null
    }

    @Test fun HOM_16_fillCropsAndFitLetterboxes() {
        val fill = PictureLayout.place(200, 100, 100f, 200f, PictureFit.Fill)
        assertEquals(2f, fill.scale, 0.001f); assertEquals(-150f, fill.dx, 0.01f); assertEquals(0f, fill.dy, 0.01f) // 2:1 picture on a tall screen: cropped left and right
        val fit = PictureLayout.place(200, 100, 100f, 200f, PictureFit.Fit)
        assertEquals(0.5f, fit.scale, 0.001f); assertEquals(0f, fit.dx, 0.01f); assertEquals(75f, fit.dy, 0.01f) // all of it, bars above and below
        assertEquals(1f, PictureLayout.place(0, 0, 100f, 100f, PictureFit.Fill).scale, 0f)
    }

    @Test fun HOM_16_aStillPictureFillsTheScreenOrIsFittedWithBlackBars() {
        still()
        file("glance-a.jpg")
        val img = solidBitmap(Color.RED, 20, 10)
        for (fit in PictureFit.entries) {
            val w = Wallpaper(WallpaperKind.Picture, picture = PictureSpec("glance-a.jpg", fit))
            setHost { CompositionLocalProvider(LocalPictureLoader provides FakeLoader(still = img)) { PictureWallpaper(w) } }
            rule.waitForIdle(); rule.mainClock.advanceTimeBy(500)
            val b = await(); shot("still-$fit", b)
            assertEquals("centre is the picture", Color.RED, b.getPixel(b.width / 2, b.height / 2))
            if (fit == PictureFit.Fill) assertEquals("Fill covers the corner", Color.RED, b.getPixel(2, 2)) else assertEquals("Fit leaves a black bar", Color.BLACK, b.getPixel(2, 2))
        }
    }

    @Test fun HOM_16_theDimIsABlackLayerOverThePicture() {
        still(); file("glance-a.jpg")
        val w = Wallpaper(WallpaperKind.Picture, picture = PictureSpec("glance-a.jpg", dim = 50))
        setHost { CompositionLocalProvider(LocalPictureLoader provides FakeLoader(still = solidBitmap(Color.WHITE))) { PictureWallpaper(w) } }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(500)
        val c = await().let { it.getPixel(it.width / 2, it.height / 2) }
        assertTrue("half dimmed white: ${Color.red(c)}", Color.red(c) in 120..136)
    }

    @Test fun HOM_16_aMissingFileFallsBackToTheSolidColourAndDoesNotCrash() {
        still()
        val w = Wallpaper(WallpaperKind.Picture, solid = 0xFF336699, picture = PictureSpec("glance-gone.jpg", dim = 25))
        setHost { PictureWallpaper(w) }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(500)
        assertEquals(0xFF336699.toInt(), await { it.getPixel(50, 50) != 0 }.getPixel(50, 50))
    }

    @Test fun HOM_16_aCorruptFileThatCannotBeDecodedShowsTheBlackBaseNotACrash() {
        still(); file("glance-bad.jpg")
        val w = Wallpaper(WallpaperKind.Picture, picture = PictureSpec("glance-bad.jpg"))
        setHost { CompositionLocalProvider(LocalPictureLoader provides FakeLoader(still = null)) { PictureWallpaper(w) } }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(500)
        assertEquals(Color.BLACK, await { it.getPixel(50, 50) != 0 }.getPixel(50, 50))
    }

    private val red = Color.RED; private val green = Color.GREEN; private val blue = Color.BLUE
    private fun frames() = FrameSet(intArrayOf(100, 100, 100), listOf(solidBitmap(red), solidBitmap(green), solidBitmap(blue)), 20, 10)

    private fun playerColours(mode: GifMode, times: List<Long>, active: Boolean = true): List<Int> {
        rule.mainClock.autoAdvance = false
        setHost { Box(Modifier.fillMaxSize()) { FramePlayer(frames(), mode, active, PictureFit.Fill) } }
        rule.mainClock.advanceTimeByFrame()
        val out = ArrayList<Int>()
        var t = 0L
        for (target in times) { rule.mainClock.advanceTimeBy(target - t); t = target; rule.waitForIdle(); val b = bitmap(); out += b.getPixel(b.width / 2, b.height / 2) }
        return out
    }

    @Test fun NFR_14_theFramePlayerPlaysOnceAndStopsOnTheLastFrame() {
        assertEquals(listOf(red, green, blue, blue, blue), playerColours(GifMode.Once, listOf(40, 140, 240, 400, 2000)))
    }

    @Test fun NFR_14_theFramePlayerLoopsContinuously() {
        assertEquals(listOf(red, green, blue, red, green), playerColours(GifMode.LoopContinuous, listOf(40, 140, 240, 340, 440)))
    }

    @Test fun NFR_14_theFramePlayerBouncesForwardThenBack() {
        // frames 0 1 2 1 | 0 ...: red green blue, then back: green, red (the test clock steps in 16 ms frames; later cycles drift in it, so one cycle and the wrap are sampled; the exact clock is pinned in core-data's WallpaperPictureTest)
        assertEquals(listOf(red, green, blue, green, red), playerColours(GifMode.LoopBounce, listOf(40, 140, 240, 340, 440)))
    }

    @Test fun NFR_14_theFramePlayerStaysOnItsFrameWhileTheAppIsNotVisible() {
        assertEquals(listOf(red, red, red), playerColours(GifMode.LoopContinuous, listOf(40, 340, 1000), active = false))
    }

    @Test fun HOM_16_theSystemWallpaperLayerDrawsNothingOpaque() {
        still()
        setHost { SystemWallpaperLayer(Wallpaper(WallpaperKind.System)) }
        rule.waitForIdle()
        val b = bitmap()
        for ((x, y) in listOf(2 to 2, b.width / 2 to b.height / 2, b.width - 3 to b.height - 3)) assertEquals("pixel $x,$y is transparent", 0, Color.alpha(b.getPixel(x, y)))
        setHost { SystemWallpaperLayer(Wallpaper(WallpaperKind.System, systemDim = 50)) }
        rule.waitForIdle()
        val d = bitmap(); assertTrue("the dim is half-transparent black: ${Color.alpha(d.getPixel(50, 50))}", Color.alpha(d.getPixel(50, 50)) in 124..132)
        assertEquals(0, Color.red(d.getPixel(50, 50)))
    }

    // ---- the spider-sense alert ---------------------------------------------------------------------------------------------------------------------------------------------

    private fun surfaceBitmap(p: LauncherPrefs): Bitmap {
        setHost { CompositionLocalProvider(LocalPictureLoader provides FakeLoader(still = solidBitmap(0xFF304050.toInt()))) { HomeSurfaceBackground(WallpaperSurface.Glance, p, Modifier.fillMaxSize(), announce = false, scrim = false) } }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1500)
        return if (p.wallpaper(WallpaperSurface.Glance).kind == WallpaperKind.Picture) await() else bitmap()
    }
    private fun differing(a: Bitmap, b: Bitmap): Int { var n = 0; for (y in 0 until a.height step 2) for (x in 0 until a.width step 2) if (a.getPixel(x, y) != b.getPixel(x, y)) n++; return n }

    private fun kinds(): List<Pair<String, Wallpaper>> {
        file("glance-a.jpg")
        return listOf(
            "solid" to Wallpaper(WallpaperKind.Solid, solid = 0xFF204060),
            "gradient" to Wallpaper(WallpaperKind.Gradient, gradient = GradientSpec(0xFF3A1C71, 0xFF0B0B10, 90)),
            "picture" to Wallpaper(WallpaperKind.Picture, picture = PictureSpec("glance-a.jpg")),
            "system" to Wallpaper(WallpaperKind.System),
            "signal-empty" to Wallpaper(WallpaperKind.Live, live = BackgroundChoice(BgItem.None, BgItem.None)),
            "signal" to Wallpaper(WallpaperKind.Live),
        )
    }

    // W35: the alert is the user-selected Alert effect (IndicatorLayer), covered by IndicatorAlertW35UiTest; the wallpaper no longer draws comic lines itself.

    private fun radar(p: LauncherPrefs): Bitmap {
        setHost { Box(Modifier.fillMaxSize().androidxBlack()) { CompositionLocalProvider(LocalPictureLoader provides FakeLoader(still = solidBitmap(0xFF304050.toInt()))) { RadarBackgroundLayer(p, { null }) } } }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1500)
        return if (p.wallpaper(WallpaperSurface.Glance).kind == WallpaperKind.Picture && p.radarBackground == RadarBackground.Dimmed) await { Color.red(it.getPixel(it.width / 2, it.height / 2)) > 0 || Color.blue(it.getPixel(it.width / 2, it.height / 2)) > 0 } else bitmap()
    }
    private fun Modifier.androidxBlack() = this.then(Modifier.background(androidx.compose.ui.graphics.Color.Black))

    @Test fun NFR_14_radarDrawsAnAnimatedPictureAsItsStillAndNeverStartsAPlayerThere() {
        still(); file("glance-v.mp4")
        var created = 0
        val engines = VideoEngineFactory { created++; object : VideoEngine { override fun start(file: File, surface: android.view.Surface, loop: Boolean, onSize: (Int, Int) -> Unit) {}; override fun release() {} } }
        val w = Wallpaper(WallpaperKind.Picture, picture = PictureSpec("glance-v.mp4", animated = true, video = true, gif = GifMode.LoopContinuous, powerOk = true))
        val p = LauncherPrefs().withWallpaper(WallpaperSurface.Glance, w)
        // reduced motion is off here on purpose: the only thing keeping the player away is that Radar asks for the still
        com.bos.sphere.core.design.ReducedMotionTesting.force(null)
        setHost { CompositionLocalProvider(LocalVideoEngineFactory provides engines, LocalPictureLoader provides FakeLoader(still = solidBitmap(Color.MAGENTA))) { RadarBackgroundLayer(p, { null }) } }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000)
        assertEquals("no player on Radar", 0, created)
    }
}
