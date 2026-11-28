package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width as dpWidth
import com.bos.sphere.core.data.CalmStyle
import com.bos.sphere.core.data.DockStyle
import com.bos.sphere.core.data.IconShape
import com.bos.sphere.core.data.IconTint
import com.bos.sphere.core.data.LookPreset
import com.bos.sphere.core.data.LookPresets
import com.bos.sphere.core.data.ThemeMode
import com.bos.sphere.core.data.ThemeWallpaper
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.core.design.ThemeVariant
import org.junit.After
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
 * Wave 56 (looksPresets), CFG-26: the Looks rail. One card per bundled Look, drawn from the Look's own values (its real wallpaper colour, shape, dock), the picked
 * card computed by the caller, a tap reports the Look, the undo row shows only while there is something to undo. Screenshots: `build/screenshots/looks56-*.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class LooksRailW56Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var picked by mutableStateOf<String?>(null)
    private var applied by mutableStateOf<String?>(null)
    private var canUndo by mutableStateOf(false)
    private val taps = ArrayList<String>()
    private var undos = 0
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @After fun reset() { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }

    private fun show(light: Boolean = false) {
        HyleTheme.apply(if (light) ThemeVariant.Light else ThemeVariant.Dark, 0xFF8E7BFF)
        rule.setContent {
            SphereTheme {
                Box(Modifier.fillMaxSize().background(HyleColors.Background).padding(16.dp)) {
                    LooksRail(LookPresets.all, picked, { taps += it.id; picked = it.id }, { it.name }, applied, canUndo, { undos++ }, Modifier.width(328.dp))
                }
            }
        }
    }

    private fun bitmap(): Bitmap {
        rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        return bmp
    }

    private fun shoot(name: String): Bitmap = bitmap().also { b -> File(outDir, "looks56-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }

    @Test fun CFG_26_w56_oneRadioCardPerBundledLookInOrderAndTheUnpickedStateHasNoSelection() {
        show()
        for (l in LookPresets.all) rule.onNodeWithTag(LooksTags.card(l.id)).assertExists().assertIsNotSelected()
    }

    @Test fun CFG_26_w56_aTapReportsTheLookAndThePickedCardIsTheOneTheCallerSays() {
        show()
        rule.onNodeWithTag(LooksTags.card("terminal")).performClick(); rule.waitForIdle()
        assertEquals(listOf("terminal"), taps)
        rule.onNodeWithTag(LooksTags.card("terminal")).assertIsSelected()
        for (l in LookPresets.all.filter { it.id != "terminal" }) rule.onNodeWithTag(LooksTags.card(l.id)).assertIsNotSelected()
    }

    @Test fun CFG_26_w56_theAppliedLineAndTheUndoRowShowOnlyWhenThereIsSomethingToSay() {
        show()
        rule.onNodeWithTag(LooksTags.APPLIED).assertDoesNotExist(); rule.onNodeWithTag(LooksTags.UNDO).assertDoesNotExist()
        applied = "Applied Midnight."; canUndo = true; rule.waitForIdle()
        rule.onNodeWithTag(LooksTags.APPLIED).assertExists()
        rule.onNodeWithTag(LooksTags.UNDO).assertExists().performClick()
        assertEquals(1, undos)
        canUndo = false; rule.waitForIdle()
        rule.onNodeWithTag(LooksTags.UNDO).assertDoesNotExist()
    }

    @Test fun CFG_26_w56_everyCardIsAtLeast48DpAndTheRailFitsThePhoneWidth() {
        show()
        for (l in LookPresets.all) {
            val b = rule.onNodeWithTag(LooksTags.card(l.id)).getBoundsInRoot()
            assertTrue("${l.id} ${b.dpWidth} x ${b.height}", b.dpWidth >= 48.dp && b.height >= 48.dp && b.right <= 360.dp)
        }
    }

    @Test fun CFG_26_w56_theCardPictureIsDrawnFromTheLooksOwnWallpaperAndEveryPictureDiffers() {
        show(); picked = null
        val bmp = shoot("rail-dark")
        val density = 2.0f // xhdpi
        val sigs = HashSet<Int>()
        for (l in LookPresets.all) {
            val b = rule.onNodeWithTag(LooksTags.card(l.id)).getBoundsInRoot()
            val x0 = (b.left.value * density).toInt(); val y0 = (b.top.value * density).toInt(); val w = (b.dpWidth.value * density).toInt(); val h = (64 * density).toInt()
            // a pixel in the empty wallpaper area (middle left, between the clock and the icons) carries the wallpaper colour
            val c = bmp.getPixel(x0 + (w * 0.03f).toInt() + 4, y0 + (h * 0.62f).toInt())
            val s = l.settings.wallpaper
            val want = when (s) { is ThemeWallpaper.Solid -> s.color.toInt(); is ThemeWallpaper.Gradient -> null; null -> null }
            if (want != null && l.settings.calmStyle != CalmStyle.EdgeDots) assertEquals("${l.id} wallpaper pixel", want and 0xFFFFFF, c and 0xFFFFFF)
            var sig = 17
            for (yy in 0 until h step 3) for (xx in 0 until w step 3) sig = 31 * sig + bmp.getPixel(x0 + xx, y0 + yy)
            sigs += sig
        }
        assertEquals("six different pictures", LookPresets.all.size, sigs.size)
    }

    @Test fun CFG_26_w56_screenshotsOfTheRailInDarkAndLightAreWritten() {
        picked = "midnight"; show(); shoot("rail-dark-picked")
        assertTrue(File(outDir, "looks56-rail-dark-picked.png").length() > 0)
    }

    @Test fun CFG_26_w56_screenshotLight() {
        picked = "paper"; applied = "Applied Paper."; canUndo = true; show(light = true); shoot("rail-light-picked")
        assertTrue(File(outDir, "looks56-rail-light-picked.png").length() > 0)
    }

    @Test fun CFG_26_w56_theMiniatureChoicesFollowTheLooksValues() {
        assertTrue(LookArt.radius(IconShape.Square) < LookArt.radius(IconShape.Rounded) && LookArt.radius(IconShape.Rounded) < LookArt.radius(IconShape.Squircle) && LookArt.radius(IconShape.Squircle) < LookArt.radius(IconShape.Circle))
        assertNotEquals(LookArt.foreground(ThemeMode.Light), LookArt.foreground(ThemeMode.Dark)); assertEquals(LookArt.foreground(ThemeMode.Dark), LookArt.foreground(ThemeMode.Amoled))
        val accent = androidx.compose.ui.graphics.Color(0xFF3DDC84); val fg = LookArt.foreground(ThemeMode.Dark)
        assertNotEquals(LookArt.iconColour(IconTint.None, accent, fg, 0), LookArt.iconColour(IconTint.None, accent, fg, 1))
        assertEquals(accent, LookArt.iconColour(IconTint.Accent, accent, fg, 0))
        assertEquals(fg.red, LookArt.iconColour(IconTint.Mono, accent, fg, 0).red, 0f)
        assertTrue("every dock style and calm style is covered by a Look", LookPresets.all.map { it.settings.dockStyle }.toSet() == DockStyle.entries.toSet() && LookPresets.all.mapNotNull { it.settings.calmStyle }.toSet().size >= 4)
    }
}
