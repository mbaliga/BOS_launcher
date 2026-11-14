package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** PAL-13: the empty state lists the prefixes. Renders to `build/screenshots/` and checks that a tap starts the query with its prefix. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RadarPaletteScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h200dp-port-xhdpi")
    fun PAL_13_emptyStateListsThePrefixes() {
        var picked = ""
        rule.setContent {
            Column(Modifier.fillMaxSize().padding(16.dp)) { PrefixHints(ScopePrefixes.DEFAULT, onPick = { picked = it }) }
        }
        rule.onNodeWithText("Actions", substring = true).assertExists()
        rule.onNodeWithText("web", substring = true, ignoreCase = true).performClick()
        assertEquals("w ", picked)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), Paint().apply { color = 0xFF14172B.toInt() })
        rule.runOnUiThread { view.draw(c) }
        File(outDir, "31-radar-prefix-hints.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
