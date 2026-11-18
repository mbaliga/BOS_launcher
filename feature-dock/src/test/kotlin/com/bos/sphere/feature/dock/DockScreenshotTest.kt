package com.bos.sphere.feature.dock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the dock in portrait (bottom rail) and landscape (side rail) to `build/screenshots/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DockScreenshotTest {
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
        assertTrue(File(outDir, "$name.png").length() > 1000)
    }

    private fun item(id: String, label: String, used: Boolean = false) = DockItem(DockItemRef.app(id), label, used)
    private val colors = listOf(Color(0xFF4F8CFF), Color(0xFFFF7A59), Color(0xFF3DDC84), Color(0xFFE6B800), Color(0xFFB36BFF), Color(0xFF29C5D6), Color(0xFFFF5C8A))
    private val full = DockContent(
        pinned = listOf(item("a", "Mail", true), item("b", "Maps"), item("c", "Notes", true), item("d", "Camera")),
        recent = listOf(item("e", "Music"), item("f", "Files")),
        showEnableRow = false, showRecentSection = true,
        collapsed = false,
    )

    @androidx.compose.runtime.Composable
    private fun Rail(content: DockContent, edge: DockEdge, align: Alignment) {
        Box(Modifier.fillMaxSize().padding(16.dp)) {
            DockRail(content, edge, "Home", expanded = true, focusRequest = 0, showNumbers = true, actions = DockActions(), modifier = Modifier.align(align)) { item, size ->
                Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(colors[item.ref.id.hashCode().mod(colors.size)]))
            }
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h800dp-port-xhdpi")
    fun DCK_portraitBottomRail() {
        rule.mainClock.autoAdvance = false
        rule.setContent { Rail(full, DockEdge.Bottom, Alignment.BottomCenter) }
        rule.onNodeWithContentDescription("Mail, used this session").assertExists()
        shoot("40-dock-portrait")
    }

    /** W25 (owner item 3): no media control and no media title anywhere in the dock; they live only in the Glance notch. */
    @Test @Config(sdk = [35], qualifiers = "w411dp-h800dp-port-xhdpi")
    fun DCK_08_theBottomDockHasNoMediaControlOrTitle() = noMedia(DockEdge.Bottom)

    @Test @Config(sdk = [35], qualifiers = "w800dp-h411dp-land-xhdpi")
    fun DCK_08_theSideDockHasNoMediaControlOrTitle() = noMedia(DockEdge.Left)

    private fun noMedia(edge: DockEdge) {
        rule.setContent { Rail(full, edge, Alignment.CenterStart) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Mail, used this session").assertExists()
        for (d in listOf("Previous track", "Next track", "Pause", "Play", "Previous", "Next", "Now playing")) {
            assertTrue("$edge has no '$d'", rule.onAllNodes(androidx.compose.ui.test.hasContentDescription(d, substring = true)).fetchSemanticsNodes().isEmpty())
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w800dp-h411dp-land-xhdpi")
    fun DCK_landscapeSideRail() {
        rule.mainClock.autoAdvance = false
        rule.setContent { Rail(full, DockEdge.Left, Alignment.CenterStart) }
        shoot("41-dock-landscape")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h400dp-port-xhdpi")
    fun DCK_06_enableRowAndCollapsedBand() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                DockRail(full.copy(recent = emptyList(), showRecentSection = false, showEnableRow = true), DockEdge.Bottom, "Home", true, 0, false, DockActions(), Modifier.align(Alignment.TopCenter)) { _, size -> Box(Modifier.size(size).background(Color.Gray)) }
                DockRail(full.copy(collapsed = true), DockEdge.Bottom, "Home", false, 0, false, DockActions(), Modifier.align(Alignment.BottomCenter)) { _, size -> Box(Modifier.size(size)) }
            }
        }
        rule.onNodeWithContentDescription("Dock, collapsed").assertExists()
        shoot("42-dock-enable-and-collapsed")
    }
}
