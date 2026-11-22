package com.bos.sphere

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.up
import com.bos.sphere.core.data.FreshHome
import com.bos.sphere.core.data.GradientSpec
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.Wallpaper
import com.bos.sphere.core.data.WallpaperKind
import com.bos.sphere.core.data.WallpaperSurface
import com.bos.sphere.core.data.withWallpaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * W55 (fixedBackdrop), the REAL home under Robolectric. Owner: "When I scroll between pages, the background also moves, it shouldn't."
 * The wallpaper is one fixed layer below the pager: mid-swipe from Glance to page 1 its bounds and drawn pixel columns are those at rest; with a different Pages
 * wallpaper the two cross-fade. Not a device run: the feel of the swipe and live wallpapers (GPU) need a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class FixedBackdropW55SmokeTest : TourBase() {
    private val stores get() = LauncherStores.get(AppSmokeHarness.app)
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private val stripes = Wallpaper(WallpaperKind.Gradient, gradient = GradientSpec(0xFFD81B60, 0xFF1A237E, 0))

    private fun start(split: Boolean) {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        AppSmokeHarness.configureFresh {
            var p = it.copy(glanceEnabled = true, homePages = HomePages.Paginated, pagesSameAsGlance = !split).withWallpaper(WallpaperSurface.Glance, stripes)
            if (split) p = p.withWallpaper(WallpaperSurface.Pages, Wallpaper(WallpaperKind.Solid, solid = 0xFF0000FF))
            p
        }
        AppSmokeHarness.app.getSharedPreferences("liftoff_misc", android.content.Context.MODE_PRIVATE).edit().putBoolean("defaultHomeSeeded", true).commit()
        stores.layout.replace(FreshHome.emptyLayout())
        launch()
    }

    private fun shot(name: String?): Bitmap {
        settle(400)
        var bmp: Bitmap? = null
        sc.onActivity { a ->
            val view = a.window.decorView
            val b = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            android.view.PixelCopy.request(a.window, b, { }, android.os.Handler(android.os.Looper.getMainLooper()))
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            bmp = b
        }
        if (name != null) File(outDir, "$name.png").outputStream().use { bmp!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp!!
    }

    /** The wallpaper at the very top edge between the corner hotspots: nothing but the backdrop is drawn there. */
    private fun row(b: Bitmap) = (b.width / 4 until b.width * 3 / 4 step 8).map { b.getPixel(it, 3) }

    @Test fun HOM_16_theHomeWallpaperDoesNotMoveWhenSwipingFromGlanceToPage() {
        start(split = false)
        val bounds = rule.onNodeWithTag("home.backdrop").getBoundsInRoot()
        val rest = row(shot("w55_glance_rest"))
        assertTrue("the stripes are distinct columns", rest.distinct().size > 10)
        rule.onNodeWithTag("home.pager").performTouchInput { down(Offset(width * 0.75f, height * 0.5f)); moveBy(Offset(-width * 0.3f, 0f)) }
        rule.mainClock.advanceTimeBy(100)
        assertEquals("the backdrop layer's bounds", bounds, rule.onNodeWithTag("home.backdrop").getBoundsInRoot())
        assertEquals("the wallpaper's pixel columns mid-swipe", rest, row(shot("w55_mid_swipe")))
        rule.onNodeWithTag("home.pager").performTouchInput { up() }
        settle()
        if (glanceShown()) { rule.onNodeWithTag("home.pager").performTouchInput { swipe(Offset(width * 0.75f, height * 0.5f), Offset(width * 0.25f, height * 0.5f), 300) }; settle() }
        assertTrue("now on the first page", !glanceShown())
        assertEquals("and on the page", rest, row(shot("w55_page")))
    }

    @Test fun HOM_16_aDifferentPagesWallpaperFadesInMidSwipeInsteadOfSliding() {
        start(split = true)
        val rest = row(shot(null))
        rule.onNodeWithTag("home.pager").performTouchInput { down(Offset(width * 0.75f, height * 0.5f)); moveBy(Offset(-width * 0.3f, 0f)) }
        rule.mainClock.advanceTimeBy(100)
        assertTrue("the Pages wallpaper layer is composed mid-swipe", tagShown("home.backdrop.pages"))
        val mid = shot("w55_crossfade_mid")
        assertTrue("the Glance stripes are still where they were, tinted by the fade", row(mid) != rest)
        rule.onNodeWithTag("home.pager").performTouchInput { up() }
        settle()
    }
}
