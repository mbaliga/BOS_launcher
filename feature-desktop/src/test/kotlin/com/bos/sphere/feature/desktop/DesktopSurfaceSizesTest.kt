package com.bos.sphere.feature.desktop

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.design.SphereTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * EXT-05: the surface itself, composed in the window sizes a maximised, freeform and split window give, down to the compact
 * size class. Window size here is the Robolectric configuration; a real freeform or split window is not exercised.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesktopSurfaceSizesTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private fun render(name: String, expectedClass: SizeClass) {
        val controller = DesktopController.get(ApplicationProvider.getApplicationContext())
        rule.mainClock.autoAdvance = false
        rule.setContent { SphereTheme { DesktopSurface(controller, { 2 }, "res:1920x1080:monitor", "Monitor") } }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Monitor · ${expectedClass.name}").assertExists()
        rule.onNodeWithText("Add clock").assertExists()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(outDir, "$name.png").length() > 500)
    }

    @Test @Config(sdk = [35], qualifiers = "w1920dp-h1080dp-land-mdpi")
    fun EXT_05_maximisedIsTheExpandedClass() = render("50-desktop-maximised", SizeClass.Expanded)

    @Test @Config(sdk = [35], qualifiers = "w960dp-h600dp-land-mdpi")
    fun EXT_05_aFreeformWindowIsStillExpanded() = render("51-desktop-freeform", SizeClass.Expanded)

    @Test @Config(sdk = [35], qualifiers = "w700dp-h800dp-port-mdpi")
    fun EXT_05_aHalfSplitIsTheMediumClass() = render("52-desktop-split", SizeClass.Medium)

    @Test @Config(sdk = [35], qualifiers = "w360dp-h400dp-port-mdpi")
    fun EXT_05_theNarrowestPaneIsTheCompactClass() = render("53-desktop-compact", SizeClass.Compact)
}
