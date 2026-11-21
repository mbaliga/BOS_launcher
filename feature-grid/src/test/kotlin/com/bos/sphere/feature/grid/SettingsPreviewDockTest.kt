package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

class DockGeometryTest {
    @Test fun CFG_18_dockKeepsTheHomeAspectAndFitsTheWindow() {
        val land = DockGeometry.size(891f, 411f, 1644, 822)
        assertEquals(2f, land.widthDp / land.heightDp, 0.01f)
        assertTrue(land.widthDp <= 170f)
        val port = DockGeometry.size(411f, 891f, 822, 1644)
        assertEquals(0.5f, port.widthDp / port.heightDp, 0.01f)
        assertTrue(maxOf(port.widthDp, port.heightDp) <= 170f)
    }

    @Test fun CFG_18_dockIsHiddenUntilTheHomeHasBeenDrawn() {
        assertEquals(DockGeometry.Box(0f, 0f), DockGeometry.size(400f, 800f, 0, 0))
        assertEquals(0f, DockGeometry.scale(0, 100f), 0f)
        assertEquals(0.25f, DockGeometry.scale(400, 100f), 0f)
    }

    @Test fun CFG_18_smallWindowsStillGetAUsableDock() {
        val b = DockGeometry.size(120f, 120f, 100, 100)
        assertTrue(b.widthDp >= 72f)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class SettingsPreviewDockRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Headless limit: Robolectric's software canvas cannot execute a recorded GraphicsLayer, so the pixels of the dock
     * are device-only. This proves the wiring: once the home has drawn (and recorded), the dock appears.
     */
    @Test fun CFG_18_dockAppearsOnceTheHomeHasDrawn() {
        lateinit var cap: HomeCapture
        rule.setContent {
            val capture = rememberHomeCapture()
            cap = capture
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.fillMaxSize().captureHome(capture).background(Color(0xFFE53935)))
                SettingsPreviewDock(capture)
            }
        }
        rule.waitForIdle()
        assertTrue("dock hidden before the first draw", cap.size.width == 0)
        rule.onAllNodesWithContentDescription("Live preview of your home").assertCountEquals(0)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        runCatching { rule.runOnUiThread { view.draw(Canvas(bmp)) } }   // the software canvas may refuse drawLayer after recording
        rule.waitForIdle()
        assertTrue("home size recorded: ${cap.size}", cap.size.width > 0 && cap.size.height > cap.size.width)
        rule.onNodeWithContentDescription("Live preview of your home").assertExists()
    }
}
