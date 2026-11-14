package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.bos.sphere.core.data.MediaInfo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaStripArtworkTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test @Config(sdk = [35])
    fun STR_06_stripShowsArtworkWhenThePlayerGivesIt() {
        val art = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        rule.setContent { MediaStrip(MediaInfo("T", "A", "p", true, true, true, artwork = art), {}, {}, {}, {}) }
        rule.onNodeWithTag("media-artwork", useUnmergedTree = true).assertExists()
    }

    @Test @Config(sdk = [35])
    fun STR_06_stripShowsAPlaceholderWithoutArtwork() {
        rule.setContent { MediaStrip(MediaInfo("T", "A", "p", true, true, true), {}, {}, {}, {}) }
        rule.onNodeWithTag("media-artwork-none", useUnmergedTree = true).assertExists()
    }
}
