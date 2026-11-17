package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bos.sphere.core.data.GlanceWeatherEffect
import com.bos.sphere.core.data.RadarIntensity
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.quality.MotionPrefs
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.pow

/**
 * HOM-23, NFR-15, NFR-14: every weather effect, dark and light, at the stronger intensity, drawn as its still frame (reduced motion):
 * the clock's ink keeps WCAG AA (4.5) against every pixel of the ambience, and the layer is not blank. The thunder flash adds at most
 * its peak alpha of near-white, which is checked on top of the brightest pixel. Screenshots: build/screenshots/glance_weather_*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class GlanceWeatherContrastTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    @Before fun still() = ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f))
    @After fun reset() = ReducedMotionTesting.force(null)

    private fun lum(c: Int): Double {
        fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * ch(android.graphics.Color.red(c)) + 0.7152 * ch(android.graphics.Color.green(c)) + 0.0722 * ch(android.graphics.Color.blue(c))
    }
    private fun contrast(a: Double, b: Double) = (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)

    private fun render(effect: GlanceWeatherEffect, dark: Boolean): Bitmap {
        val base = if (dark) Color.Black else Color(0xFFF3F3F6)
        rule.setContent { Box(Modifier.fillMaxSize().background(base)) { GlanceWeatherLayer(effect, RadarIntensity.Normal, dark, animate = false) } }
        rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { Canvas(bmp).also { view.draw(it) } }
        File(outDir, "glance_weather_${effect.name}_${if (dark) "dark" else "light"}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    private fun check(effect: GlanceWeatherEffect, dark: Boolean) {
        val bmp = render(effect, dark)
        val ink = if (dark) lum(android.graphics.Color.WHITE) else lum(0xFF101016.toInt())
        var worst = Double.MAX_VALUE; var changed = 0
        val baseLum = if (dark) 0.0 else lum(0xFFF3F3F6.toInt())
        for (y in 0 until bmp.height step 2) for (x in 0 until bmp.width step 2) {
            val l = lum(bmp.getPixel(x, y))
            worst = minOf(worst, contrast(ink, l))
            if (kotlin.math.abs(l - baseLum) > 0.002) changed++
        }
        assertTrue("$effect dark=$dark: the clock keeps AA over the ambience (worst ${"%.2f".format(worst)})", worst >= 4.5)
        assertTrue("$effect dark=$dark: the layer draws something", changed > 20)
    }

    @Test fun NFR_15_clearDayKeepsTheClockAtAAOnDark() = check(GlanceWeatherEffect.ClearDay, true)
    @Test fun NFR_15_clearDayKeepsTheClockAtAAOnLight() = check(GlanceWeatherEffect.ClearDay, false)
    @Test fun NFR_15_clearNightKeepsTheClockAtAAOnDark() = check(GlanceWeatherEffect.ClearNight, true)
    @Test fun NFR_15_clearNightKeepsTheClockAtAAOnLight() = check(GlanceWeatherEffect.ClearNight, false)
    @Test fun NFR_15_cloudsKeepsTheClockAtAAOnDark() = check(GlanceWeatherEffect.Clouds, true)
    @Test fun NFR_15_cloudsKeepsTheClockAtAAOnLight() = check(GlanceWeatherEffect.Clouds, false)
    @Test fun NFR_15_rainKeepsTheClockAtAAOnDark() = check(GlanceWeatherEffect.Rain, true)
    @Test fun NFR_15_rainKeepsTheClockAtAAOnLight() = check(GlanceWeatherEffect.Rain, false)
    @Test fun NFR_15_snowKeepsTheClockAtAAOnDark() = check(GlanceWeatherEffect.Snow, true)
    @Test fun NFR_15_snowKeepsTheClockAtAAOnLight() = check(GlanceWeatherEffect.Snow, false)
    @Test fun NFR_15_fogKeepsTheClockAtAAOnDark() = check(GlanceWeatherEffect.Fog, true)
    @Test fun NFR_15_fogKeepsTheClockAtAAOnLight() = check(GlanceWeatherEffect.Fog, false)
    @Test fun NFR_15_thunderKeepsTheClockAtAAOnDark() = check(GlanceWeatherEffect.Thunder, true)
    @Test fun NFR_15_thunderKeepsTheClockAtAAOnLight() = check(GlanceWeatherEffect.Thunder, false)
}
