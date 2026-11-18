package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.WheelGrouping
import androidx.compose.ui.test.onAllNodesWithContentDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * HOM-01, INP-18, CFG-15 (wave 29, drawerRevert): the restored wheel disc (hub with its search button and the ring actions, section bands, app spokes)
 * renders in portrait and landscape from the left and the right corner. The PNGs in build/screenshots are what the status note judges; the assertions
 * pin that the ring actions and the hub's search button are really on the disc.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WheelDiscW29ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private val tints = longArrayOf(0xFFE1306C, 0xFF2F6BFF, 0xFF25C04A, 0xFFFF9A3C, 0xFF8E7BFF, 0xFF3A9BFF)
    private val apps = (0 until 60).map { i ->
        val label = "${('A' + (i % 22))}app$i"
        AppEntry("demo.w$i", ComponentName("demo.w$i", "M"), label, Process.myUserHandle()) { AdaptiveIconDrawable(ColorDrawable(tints[i % tints.size].toInt()), ColorDrawable(tints[(i + 2) % tints.size].toInt())) }
    }
    private val actions = listOf("Settings" to Glyphs.Dial, "Flashlight" to Glyphs.Up, "Do not disturb" to Glyphs.Bell).map { (l, g) -> PuckAction(l, g) {} }

    private fun shoot(name: String, anchor: Corner) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                WheelDrawer(
                    visible = true, anchor = anchor, apps = apps, grouping = WheelGrouping.Letter, onGroupingChange = {}, recentKeys = emptyList(),
                    onLaunch = {}, onLongPressApp = {}, onSearch = {}, onDismiss = {}, actions = actions, modifier = Modifier.fillMaxSize(),
                )
            }
        }
        rule.mainClock.advanceTimeBy(2_000)
        assertTrue("the hub's search button is on the disc", rule.onAllNodesWithContentDescription("Search apps").fetchSemanticsNodes().size == 1)
        for (a in actions) assertEquals("the ring action ${a.label} is on the disc", 1, rule.onAllNodesWithContentDescription(a.label).fetchSemanticsNodes().size)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_01_w29_discPortraitRightHanded() = shoot("w29-disc-portrait-right", Corner.BottomRight)

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_01_w29_discPortraitLeftHanded() = shoot("w29-disc-portrait-left", Corner.BottomLeft)

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_01_w29_discLandscapeRightHanded() = shoot("w29-disc-landscape-right", Corner.BottomRight)

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_01_w29_discLandscapeLeftHanded() = shoot("w29-disc-landscape-left", Corner.BottomLeft)
}
