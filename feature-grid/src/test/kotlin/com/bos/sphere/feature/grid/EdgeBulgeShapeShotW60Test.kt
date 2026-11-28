package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.design.EdgeBulgeShape
import com.bos.sphere.core.design.NotchShape
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Wave 60 (owner: "trace the shape and make sure it's exactly right", then the exact "Music Controls.svg" / "Notification bar.svg"
 * source): screenshots of the real, Compose-drawn shapes (not the Python reconstruction used to design them), read and judged rather
 * than only asserted on bounds. Screenshots: `build/screenshots/w60_shape_*.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
class EdgeBulgeShapeShotW60Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private fun bitmap(): Bitmap {
        val win = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(win.width, win.height, Bitmap.Config.ARGB_8888)
        val latch = java.util.concurrent.CountDownLatch(1)
        var status = -1
        val thread = android.os.HandlerThread("copy").also { it.start() }
        rule.runOnUiThread { android.view.PixelCopy.request(rule.activity.window, bmp, { status = it; latch.countDown() }, android.os.Handler(thread.looper)) }
        org.junit.Assert.assertTrue("window copy finished", latch.await(10, java.util.concurrent.TimeUnit.SECONDS))
        thread.quit()
        org.junit.Assert.assertEquals(android.view.PixelCopy.SUCCESS, status)
        val content = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val o = IntArray(2).also { content.getLocationInWindow(it) }
        return Bitmap.createBitmap(bmp, o[0], o[1], content.width, content.height)
    }

    private fun shoot(name: String, content: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { Box(Modifier.fillMaxSize().background(Color(0xFF2A1414))) { content() } }
        rule.mainClock.advanceTimeBy(100)
        val img = bitmap()
        File(outDir, "$name.png").outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun W60_theTopNotchShapeAtARealisticCutoutHeight() = shoot("w60_shape_notch_tall_cutout") {
        Box(Modifier.width(238.dp).height(80.dp).align(Alignment.TopCenter).background(Color(0xFFB83A3A), NotchShape(28f, 14f)))
    }

    @Test fun W60_theWaitingIndicatorShapeAtTheMinimumBadgeHeight() = shoot("w60_shape_waiting_min_height") {
        Box(Modifier.width(48.dp).height(36.dp).align(Alignment.CenterStart).background(Color(0xFFB83A3A), EdgeBulgeShape(flare = 10f, corner = 6f)))
    }

    @Test fun W60_theWaitingIndicatorShapeAtARealisticBadgeHeight() = shoot("w60_shape_waiting_tall") {
        Box(Modifier.width(48.dp).height(90.dp).align(Alignment.CenterStart).background(Color(0xFFB83A3A), EdgeBulgeShape(flare = 10f, corner = 6f)))
    }
}
