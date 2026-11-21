package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.design.smokyBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Radar's app popups have no box: a radial smoke, dark in the middle and transparent at the edge. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmokyBackdropTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test @Config(sdk = [35]) fun POP_01_theBackdropIsDarkAtTheCentreAndTransparentAtTheEdgesAndCorners() {
        rule.setContent { Box(Modifier.fillMaxSize().background(Color.White)) { Box(Modifier.size(200.dp, 300.dp).testTag("s").smokyBackdrop()) } }
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        val r = rule.onNodeWithTag("s").fetchSemanticsNode().boundsInWindow
        val x0 = r.left.toInt(); val y0 = r.top.toInt(); val w = r.width.toInt(); val h = r.height.toInt()
        val px = object { operator fun get(x: Int, y: Int) = Color(bmp.getPixel(x0 + x.coerceIn(0, w - 1), y0 + y.coerceIn(0, h - 1))) }
        val centre = px[w / 2, h / 2]
        // over white: a dark centre has a low red channel; the faded edge is the white page again
        assertTrue("centre is dark (${centre.red})", centre.red < 0.2f)
        assertTrue("left edge is clear", px[0, h / 2].red > 0.97f)
        assertTrue("top edge is clear", px[w / 2, 0].red > 0.97f)
        assertEquals("corner is clear", 1f, px[0, 0].red, 0.01f)
        assertTrue("it fades smoothly outwards", px[w / 2, h / 2].red < px[w / 4, h / 2].red && px[w / 4, h / 2].red < px[w / 16, h / 2].red)
    }
}
