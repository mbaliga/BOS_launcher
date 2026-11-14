package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bos.sphere.core.data.BackgroundKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every texture must render at every density with default and custom tints without throwing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextureStressTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun renderAll() {
        rule.mainClock.autoAdvance = false
        val ids = BackgroundPresets.textures.map { it.id } + "custom" + "unknown-id"
        val tint = androidx.compose.runtime.mutableLongStateOf(0xFF0A0B10)
        rule.setContent {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                ids.forEach { id ->
                    Box(Modifier.weight(1f).fillMaxSize()) { BackgroundLayer(BackgroundKind.Texture, id, tint.longValue, tint.longValue, Modifier.fillMaxSize(), animate = false) }
                }
            }
        }
        for (t in listOf(0xFF0A0B10, 0xFF2A1B5E, 0xFFFFFFFF, 0x00000000)) {
            rule.runOnUiThread { tint.longValue = t }
            rule.mainClock.advanceTimeBy(200)
        }
    }

    @Test @Config(sdk = [34], qualifiers = "w360dp-h800dp-port-mdpi")
    fun mdpi() = renderAll()

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun xhdpi() = renderAll()

    @Test @Config(sdk = [34], qualifiers = "w360dp-h780dp-port-xxhdpi")
    fun xxhdpi() = renderAll()

    @Test @Config(sdk = [34], qualifiers = "w360dp-h780dp-port-560dpi")
    fun xxxhdpi() = renderAll()
}
