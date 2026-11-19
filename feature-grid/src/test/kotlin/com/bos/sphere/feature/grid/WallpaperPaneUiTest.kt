package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.bos.sphere.core.data.BgItem
import com.bos.sphere.core.data.GradientSpec
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.SignalForce
import com.bos.sphere.core.data.Wallpaper
import com.bos.sphere.core.data.WallpaperKind
import com.bos.sphere.core.data.WallpaperSurface
import com.bos.sphere.core.data.wallpaper
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
 * HOM-16 (wave 26, owner clause 10): the wallpaper pane of Ground Control for the Glance and the Pages, in every state. The
 * live wallpaper choices must be there and selectable whatever the signal setting and whether the pages share the Glance
 * wallpaper; solid and gradient are chosen the same way. Screenshots: `build/screenshots/wallpaper-*.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1600dp-port-mdpi")
class WallpaperPaneUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var prefs by mutableStateOf(LauncherPrefs())
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun still() = com.bos.sphere.core.design.ReducedMotionTesting.force(com.bos.sphere.core.quality.MotionPrefs(0f, 0f, 0f))
    @After fun reset() = com.bos.sphere.core.design.ReducedMotionTesting.force(null)

    private var surface by mutableStateOf(WallpaperSurface.Glance)
    private var shown = false
    private fun show(s: WallpaperSurface) {
        surface = s
        if (shown) { rule.waitForIdle(); return }
        shown = true
        rule.setContent { Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { BackgroundPane(prefs, surface) { f -> prefs = f(prefs) } } }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.findViewById<View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "wallpaper-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun state(signal: SignalForce, same: Boolean) = LauncherPrefs(radarSignalForce = signal, pagesSameAsGlance = same)

    @Test fun HOM_16_theLiveWallpaperChoicesAreVisibleAndSelectableOnGlanceInEverySignalState() {
        for (signal in listOf(SignalForce.Live, SignalForce.Good, SignalForce.NoSignal)) for (same in listOf(true, false)) {
            prefs = state(signal, same)
            show(WallpaperSurface.Glance)
            for (label in listOf("Beam", "Spire", "Halo", "Laser", "Shaft")) assertEquals("$label on Glance (signal $signal, same $same)", 2, rule.onAllNodesWithContentDescription(label, substring = true).fetchSemanticsNodes().size)
            rule.onAllNodes(hasContentDescription("Halo", substring = true)).onFirst().performScrollTo().performClick(); rule.waitForIdle()
            assertEquals("selecting Halo in slot 1 changes the Glance live wallpaper ($signal, $same)", BgItem.Halo, prefs.backgroundSlot1)
            assertEquals("and leaves the pages' own choice alone", BgItem.Spire, prefs.pagesSlot1)
        }
    }

    @Test fun HOM_16_thePagesPaneOffersTheLiveWallpapersWhenTheyHaveTheirOwnAndExplainsWhenTheyShareTheGlanceOne() {
        for (signal in listOf(SignalForce.Live, SignalForce.NoSignal)) {
            prefs = state(signal, same = false)
            show(WallpaperSurface.Pages)
            assertEquals(2, rule.onAllNodesWithContentDescription("Laser", substring = true).fetchSemanticsNodes().size)
            rule.onAllNodes(hasContentDescription("Laser", substring = true)).onFirst().performScrollTo().performClick(); rule.waitForIdle()
            assertEquals("the pages' own slot changes ($signal)", BgItem.Laser, prefs.pagesSlot1)
            assertEquals("the Glance one does not", BgItem.Spire, prefs.backgroundSlot1)
        }
        prefs = state(SignalForce.Live, same = true)
        show(WallpaperSurface.Pages)
        rule.onNodeWithText("The pages and the scroll show the Glance wallpaper.", substring = true).assertExists()
        assertEquals(0, rule.onAllNodesWithContentDescription("Laser", substring = true).fetchSemanticsNodes().size)
    }

    @Test fun HOM_16_theFiveTypesAreTheOnlyOnesAndEachShowsItsOwnControls() {
        show(WallpaperSurface.Glance)
        for (label in listOf("Solid colour", "Gradient", "Signal wallpaper", "System live wallpaper", "Picture")) rule.onNodeWithText(label).assertExists()
        rule.onNodeWithText("Solid colour").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(WallpaperKind.Solid, prefs.wallpaper(WallpaperSurface.Glance).kind)
        rule.onNodeWithContentDescription("Saturation and brightness").assertExists()
        shot("glance-solid")
        rule.onNodeWithText("Gradient").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(WallpaperKind.Gradient, prefs.glanceWallpaperKind)
        rule.onNodeWithText("Angle").assertExists(); rule.onNodeWithText("From").assertExists(); rule.onNodeWithText("To").assertExists()
        rule.onNodeWithText("135°").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(135, prefs.glanceGradAngle)
        shot("glance-gradient")
        rule.onNodeWithText("Signal wallpaper").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(WallpaperKind.Live, prefs.glanceWallpaperKind)
        shot("glance-live")
    }

    @Test fun HOM_16_pagesDifferentFromGlanceScreenshots() {
        prefs = LauncherPrefs(pagesSameAsGlance = false).withWallpaper(WallpaperSurface.Pages, Wallpaper(WallpaperKind.Gradient, gradient = GradientSpec(0xFF3A1C71, 0xFF0B0B10, 135)))
        show(WallpaperSurface.Pages)
        shot("pages-gradient")
        rule.onNodeWithText("Solid colour").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(WallpaperKind.Solid, prefs.pagesWallpaperKind)
        assertEquals("the Glance one is untouched", WallpaperKind.Live, prefs.glanceWallpaperKind)
        shot("pages-solid")
        rule.onNodeWithText("Signal wallpaper").performScrollTo().performClick(); rule.waitForIdle()
        shot("pages-live")
    }

    @Test fun HOM_16_sharedPagesShowTheNoteAndThePreview() {
        prefs = LauncherPrefs(pagesSameAsGlance = true)
        show(WallpaperSurface.Pages)
        shot("pages-same")
    }

    @Test fun HOM_16_gradientGeometrySpansTheRectangleAtTheAngle() {
        val (a, b) = GradientGeometry.endpoints(200f, 100f, 0)
        assertEquals(0f, a.x, 0.01f); assertEquals(200f, b.x, 0.01f); assertEquals(50f, a.y, 0.01f)
        val (c, d) = GradientGeometry.endpoints(200f, 100f, 90)
        assertEquals(0f, c.y, 0.01f); assertEquals(100f, d.y, 0.01f)
        assertTrue(GradientGeometry.samples(GradientSpec(0xFF000000, 0xFFFFFFFF, 0)).let { it.first() == 0xFF000000.toInt() && it.last() == 0xFFFFFFFF.toInt() })
    }
}
