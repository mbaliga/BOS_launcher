package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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

/** HOM-12 and HOM-09 chrome: the edit bar (undo depth, disabled redo, buttons) and badge dots. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeEditScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(300)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { Canvas(bmp).also { it.drawColor(AColor.rgb(20, 26, 50)); view.draw(it) } }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun editBarUndoRedoAndDone() {
        rule.mainClock.autoAdvance = false
        var undos = 0; var done = 0
        rule.setContent {
            HomeEditBar(undoDepth = 3, canUndo = true, canRedo = false, onUndo = { undos++ }, onRedo = {}, onAddPage = {}, onDone = { done++ }, modifier = Modifier.fillMaxSize())
        }
        rule.onNodeWithText("Undo (3)").assertIsEnabled().performClick()
        rule.onNodeWithText("Redo").assertIsNotEnabled()
        rule.onNodeWithText("Done").performClick()
        assertEquals(1, undos); assertEquals(1, done)
        shoot("home_edit_bar")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h200dp-port-xhdpi")
    fun badgeDotsPlainAndCounted() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BadgeDot("")
                BadgeDot("7")
                BadgeDot("99+")
            }
        }
        rule.onNodeWithText("99+").assertIsEnabled()
        shoot("home_badge_dots")
    }
}
