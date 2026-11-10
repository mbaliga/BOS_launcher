package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.MediaInfo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the media strip (playing, paused, long title, no skip) to `build/screenshots/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaStripScreenshotTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(300)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, 0f, out.height.toFloat(), intArrayOf(0xFF1B2A55.toInt(), 0xFF3B1E54.toInt(), 0xFF0E1226.toInt()), null, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
        c.drawBitmap(bmp, 0f, 0f, null)
        File(outDir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h300dp-port-xhdpi")
    fun strip() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MediaStrip(MediaInfo("Midnight City", "M83", "demo", playing = true, canPrev = true, canNext = true), {}, {}, {}, {})
                MediaStrip(MediaInfo("Paused Song", "Some Artist", "demo", playing = false, canPrev = true, canNext = true), {}, {}, {}, {})
                MediaStrip(MediaInfo("A Really Long Track Title That Must Be Cut Off With An Ellipsis", "An Equally Long Artist Name Feat. Others", "demo", playing = true, canPrev = true, canNext = true), {}, {}, {}, {})
                MediaStrip(MediaInfo("Podcast Episode", "", "demo", playing = true, canPrev = false, canNext = false), {}, {}, {}, {})
            }
        }
        shoot("30-media-strip")
    }
}
