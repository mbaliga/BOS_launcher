package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.HomeStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** CFG-18: preview rails inside the real settings screen must render, not come out blank. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsRailTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private fun categories() = listOf(
        SettingCategory(
            "Home", Glyphs.Apps,
            listOf(
                SettingItem.Choice("Style", Glyphs.Apps, HomeStyle.entries, HomeStyle.Grid, { it.name }, preview = {
                    VisualChoice(HomeStyle.entries, HomeStyle.Grid, {}, { it.name }, previewHeight = 120.dp) { HomeStylePreview(it) }
                }) {},
                SettingItem.Toggle("Labels", Glyphs.Apps, true) {},
            ),
        ),
    )

    private fun shoot(name: String): Bitmap {
        rule.mainClock.advanceTimeBy(1_500)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    /** Number of distinct, clearly coloured pixels (saturated or bright) in the bitmap: a blank rail has none. */
    private fun colourful(b: Bitmap): Int {
        var n = 0
        for (y in 0 until b.height step 3) for (x in 0 until b.width step 3) {
            val p = b.getPixel(x, y)
            val r = (p shr 16) and 255; val g = (p shr 8) and 255; val bl = p and 255
            if (maxOf(r, g, bl) - minOf(r, g, bl) > 70) n++
        }
        return n
    }

    private fun run(initial: Int = 0): Bitmap {
        rule.mainClock.autoAdvance = false
        rule.setContent { SettingsSpace(visible = true, categories = categories(), initialCategory = initial, sounds = null, onClose = {}, modifier = Modifier.fillMaxSize()) }
        return shoot("cfg18-settings-rail-" + System.nanoTime() % 1000)
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun CFG_18_railRendersInsideTheSettingsScreenPortrait() {
        val b = run()
        assertTrue("rail cards are coloured, got ${colourful(b)} coloured samples", colourful(b) > 150)
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun CFG_18_railRendersInsideTheSettingsScreenLandscape() {
        val b = run()
        assertTrue("rail cards are coloured, got ${colourful(b)} coloured samples", colourful(b) > 150)
    }
}
