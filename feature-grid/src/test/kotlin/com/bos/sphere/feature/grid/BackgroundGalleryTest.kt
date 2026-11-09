package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bos.sphere.core.data.BackgroundKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders contact sheets of the newer wallpapers to `feature-grid/build/screenshots/` (smoke test). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackgroundGalleryTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(500)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun sheet(name: String, kind: BackgroundKind, ids: List<String>, cols: Int) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Column(Modifier.fillMaxSize()) {
                ids.chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        row.forEach { id ->
                            Box(Modifier.weight(1f).fillMaxSize()) {
                                BackgroundLayer(kind, id, 0xFF0B0C10, 0xFF0B0C10, Modifier.fillMaxSize(), animate = false)
                            }
                        }
                    }
                }
            }
        }
        shoot(name)
    }

    private val newGradients = listOf("sunrise", "lagoon", "peach", "ultraviolet", "graphite-fade", "cherry",
        "forest-mist", "glacier", "dune", "midnight-teal", "candy", "ember-night")
    private val newTextures = listOf("noise", "stripes", "diagonal", "wavelines", "topo", "circuit", "bricks", "scales")
    private val newLives = listOf("lava", "snow", "matrix", "orbits", "plasma", "plasma")

    @Test @Config(sdk = [34], qualifiers = "w600dp-h800dp-port-xhdpi")
    fun gradients() = sheet("bg-gradients", BackgroundKind.Gradient, newGradients, 4)

    @Test @Config(sdk = [34], qualifiers = "w600dp-h800dp-port-xhdpi")
    fun textures() = sheet("bg-textures", BackgroundKind.Texture, newTextures, 4)

    @Test @Config(sdk = [34], qualifiers = "w600dp-h800dp-port-xhdpi")
    fun lives() = sheet("bg-lives", BackgroundKind.Live, newLives, 3)

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun horizonPicture() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PictureBackground(PictureSource.Horizon, 0.3f, Modifier.fillMaxSize()) }
        shoot("bg-horizon")
    }
}
