package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bos.sphere.core.data.GradientSpec
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.Wallpaper
import com.bos.sphere.core.data.WallpaperKind
import com.bos.sphere.core.data.WallpaperSurface
import com.bos.sphere.core.data.withWallpaper
import org.junit.After
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
 * HOM-16 (wave 26): the one seam [HomeSurfaceBackground] draws the right wallpaper on each surface. The pages with their own
 * gradient show it (screenshot `build/screenshots/wallpaper-surface-pages-gradient.png`), the Glance keeps its own live one, and
 * pages that share the Glance wallpaper draw the same pixels as the Glance.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h720dp-xhdpi")
class WallpaperSurfaceRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var prefs by mutableStateOf(LauncherPrefs())
    private var surface by mutableStateOf(WallpaperSurface.Pages)
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun still() = com.bos.sphere.core.design.ReducedMotionTesting.force(com.bos.sphere.core.quality.MotionPrefs(0f, 0f, 0f))
    @After fun reset() = com.bos.sphere.core.design.ReducedMotionTesting.force(null)

    private fun bitmap(): Bitmap {
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000); rule.waitForIdle()
        val view = rule.activity.findViewById<View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        return bmp
    }

    private fun show() = rule.setContent { HomeSurfaceBackground(surface, prefs, Modifier.fillMaxSize(), announce = false, scrim = false) }

    @Test fun HOM_16_thePagesShowTheirOwnGradientAndTheGlanceKeepsItsLiveWallpaper() {
        prefs = LauncherPrefs().withWallpaper(WallpaperSurface.Pages, Wallpaper(WallpaperKind.Gradient, gradient = GradientSpec(0xFFD81B60, 0xFF1A237E, 90))).copy(pagesSameAsGlance = false)
        show()
        val pages = bitmap()
        File(outDir, "wallpaper-surface-pages-gradient.png").outputStream().use { pages.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val top = pages.getPixel(pages.width / 2, 2); val bottom = pages.getPixel(pages.width / 2, pages.height - 3)
        assertTrue("top is the From colour (pink): r ${android.graphics.Color.red(top)}", android.graphics.Color.red(top) > 190 && android.graphics.Color.blue(top) < 140)
        assertTrue("bottom is the To colour (indigo): b ${android.graphics.Color.blue(bottom)}", android.graphics.Color.blue(bottom) > 100 && android.graphics.Color.red(bottom) < 60)
        rule.runOnUiThread { surface = WallpaperSurface.Glance }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(2000)
        val glance = bitmap()
        assertTrue("the Glance is not the pages' gradient", glance.getPixel(glance.width / 2, 2) != top)
    }

    @Test fun HOM_16_pagesThatShareTheGlanceWallpaperDrawExactlyTheGlancePixels() {
        prefs = LauncherPrefs(glanceWallpaperKind = WallpaperKind.Solid, glanceSolid = 0xFF204060)
        surface = WallpaperSurface.Glance
        show()
        val glance = bitmap()
        rule.runOnUiThread { surface = WallpaperSurface.Pages }
        val pages = bitmap()
        assertEquals(glance.getPixel(10, 10), pages.getPixel(10, 10))
        assertEquals(0xFF204060.toInt(), pages.getPixel(100, 300))
    }

    @Test fun HOM_16_aSolidColourFillsTheSurfaceAndEditingItDoesNotNeedANewKind() {
        prefs = LauncherPrefs(glanceWallpaperKind = WallpaperKind.Solid, glanceSolid = 0xFF336699)
        surface = WallpaperSurface.Glance
        show()
        assertEquals(0xFF336699.toInt(), bitmap().getPixel(50, 50))
        rule.runOnUiThread { prefs = prefs.copy(glanceSolid = 0xFF993366) }
        assertEquals(0xFF993366.toInt(), bitmap().getPixel(50, 50))
    }
}
