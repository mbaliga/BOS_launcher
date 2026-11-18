package com.bos.sphere.feature.dock

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** HOM-10 (wave 24): a dock pin whose app is gone for now keeps its slot through the grace period, labelled, dimmed and not launched. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DockUnavailableW24Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val installed = mapOf("a" to "Alpha", "c" to "Camera")
    private fun build(unavailable: Set<String>): DockContent = DockUnavailable.mark(
        DockContentBuilder.build(
            pins = listOf(DockItemRef.app("a"), DockItemRef.app("gone"), DockItemRef.app("c"), DockItemRef.app("removed")),
            labelOf = { ref -> DockUnavailable.appLabel(ref.id, { installed[it] }, unavailable, "App unavailable") },
            recentApps = emptyList(), recentLabelOf = { null }, usageGranted = false, mode = DockMode.PinnedPlusRecent, sessionUsed = emptySet(),
        ),
    ) { it in installed }

    @Test @Config(sdk = [35]) fun HOM_10_anUnavailablePinKeepsItsSlotInOrderAndAPinPastTheGracePeriodHasNone() {
        val c = build(unavailable = setOf("gone"))
        assertEquals(listOf("a", "gone", "c"), c.pinned.map { it.ref.id })
        assertEquals("App unavailable", c.pinned[1].label)
        assertEquals(listOf(false, true, false), c.pinned.map { it.unavailable })
        assertFalse(DockUnavailable.launchable(c.pinned[1]))
        assertTrue(DockUnavailable.launchable(c.pinned[0]))
        assertNull(DockUnavailable.appLabel("removed", { installed[it] }, setOf("gone"), "App unavailable"))
    }

    @Test @Config(sdk = [35]) fun HOM_10_aPackageThatComesBackIsAvailableAgain() {
        val back = DockUnavailable.mark(build(setOf("gone"))) { true }
        assertTrue(back.pinned.none { it.unavailable })
    }

    @Test @Config(sdk = [35], qualifiers = "w800dp-h400dp-land-mdpi")
    fun HOM_10_theRailDrawsAnUnavailablePinDimmedUnderItsLabel() {
        val launched = mutableListOf<String>()
        val content = build(unavailable = setOf("gone"))
        rule.setContent {
            DockRail(
                content, DockEdge.Bottom, "Home", expanded = true, focusRequest = 0, showNumbers = false,
                actions = DockActions(onLaunch = { item, _ -> if (DockUnavailable.launchable(item)) launched += item.ref.id }), graphBridge = null,
            ) { _, _ -> Box(Modifier.size(40.dp).background(Color.White)) }
        }
        rule.waitForIdle()
        val dim = rule.onNodeWithContentDescription("App unavailable").assertExists()
        val bright = rule.onNodeWithContentDescription("Alpha").assertExists()
        // A window copy (PixelCopy; captureToImage times out under Robolectric native graphics) of what is really drawn.
        val view = rule.activity.window.decorView
        val bmp = android.graphics.Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
        val latch = java.util.concurrent.CountDownLatch(1); var status = -1
        val thread = android.os.HandlerThread("copy").also { it.start() }
        rule.runOnUiThread { android.view.PixelCopy.request(rule.activity.window, bmp, { status = it; latch.countDown() }, android.os.Handler(thread.looper)) }
        assertTrue(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)); thread.quit()
        assertEquals(android.view.PixelCopy.SUCCESS, status)
        java.io.File("build/w24-shots").apply { mkdirs() }.resolve("dock-unavailable.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val d = rule.activity.resources.displayMetrics.density
        val origin = IntArray(2).also { rule.activity.findViewById<android.view.View>(android.R.id.content).getLocationInWindow(it) }
        fun luma(node: androidx.compose.ui.test.SemanticsNodeInteraction): Float {
            val b = node.getUnclippedBoundsInRoot()
            val px = bmp.getPixel((((b.left + b.right) / 2).value * d + origin[0]).toInt(), (((b.top + b.bottom) / 2).value * d + origin[1]).toInt())
            return 0.2126f * android.graphics.Color.red(px) + 0.7152f * android.graphics.Color.green(px) + 0.0722f * android.graphics.Color.blue(px)
        }
        val lumaDim = luma(dim); val lumaBright = luma(bright)
        assertTrue("unavailable is dimmed: $lumaDim vs $lumaBright", lumaDim < lumaBright * 0.7f)
        dim.performClick(); bright.performClick()
        assertEquals("only the available pin launches", listOf("a"), launched)
    }
}
