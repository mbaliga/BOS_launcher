package com.bos.sphere.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.LaunchpadTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** CFG-18: the preview card of the settings rail draws pixels (light, dark and both), headless. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class LaunchpadThemePreviewTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun luminance(theme: LaunchpadTheme): Pair<Double, Int> {
        rule.setContent { Box(Modifier.width(120.dp).height(96.dp)) { LaunchpadThemePreview(theme) } }
        rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        var sum = 0.0; var n = 0; val seen = HashSet<Int>()
        for (y in 0 until (96 * 2).coerceAtMost(bmp.height) step 4) for (x in 0 until (120 * 2).coerceAtMost(bmp.width) step 4) {
            val p = bmp.getPixel(x, y); seen += p
            sum += 0.299 * ((p shr 16) and 255) + 0.587 * ((p shr 8) and 255) + 0.114 * (p and 255); n++
        }
        return (sum / n) to seen.size
    }

    @Test fun CFG_18_darkCardIsDarkAndNotBlank() { val (l, c) = luminance(LaunchpadTheme.Dark); assertTrue("lum $l", l < 80); assertTrue("colours $c", c > 2) }
    @Test fun CFG_18_lightCardIsLightAndNotBlank() { val (l, c) = luminance(LaunchpadTheme.Light); assertTrue("lum $l", l > 150); assertTrue("colours $c", c > 2) }
    @Test fun CFG_18_systemCardShowsBothLooks() { val (l, c) = luminance(LaunchpadTheme.System); assertTrue("lum $l", l in 60.0..200.0); assertTrue("colours $c", c > 3) }
}
