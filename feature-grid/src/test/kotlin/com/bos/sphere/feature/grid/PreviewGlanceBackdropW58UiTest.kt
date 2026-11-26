package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Bitmap
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.ClockStyle
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.SearchLayout
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.quality.MotionPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.bos.sphere.core.data.WallpaperKind
import java.io.File

/**
 * Wave 58 (studioPreview), CFG-73: the Glance topic of the floating Preview shows the Glance's own wallpaper even while the home behind Settings is on
 * a page with another wallpaper (the capture then holds the page's): the Glance backdrop is drawn in the sandbox under the real Glance surface.
 * With the same wallpaper on both, or with the home on the Glance, the exact capture is used as before. Screenshot: build/screenshots/w58-preview-glance*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class PreviewGlanceBackdropW58UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var prefs by mutableStateOf(LauncherPrefs(glanceWallpaperKind = WallpaperKind.Solid, glanceSolid = 0xFFD02020, pagesSameAsGlance = false, pagesWallpaperKind = WallpaperKind.Solid, pagesSolid = 0xFF2040D0))
    private var onGlance = false

    @Before fun setUp() { L10n.setLanguage("en"); PreviewStage.exposeSandboxSemantics = true; ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f)) }
    @After fun tearDown() { ReducedMotionTesting.force(null); PreviewStage.exposeSandboxSemantics = false }

    private fun show() {
        rule.mainClock.autoAdvance = true
        rule.setContent {
            val capture = rememberHomeCapture()
            val cats = androidx.compose.runtime.remember { listOf(SettingCategory("Home screen", Glyphs.Apps, listOf(SettingItem.Choice("Clock style", Glyphs.Clock, ClockStyle.entries, ClockStyle.BigDigital, { it.label }) {}))) }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                // the home is on a page: the captured wallpaper is the page's blue
                Box(Modifier.fillMaxSize().captureWallpaper(capture).background(Color(0xFF2040D0)))
                Box(Modifier.fillMaxSize().captureHome(capture))
                SettingsSpace(visible = true, categories = cats, initialCategory = 0, sounds = null, onClose = {}, modifier = Modifier.fillMaxSize(),
                    previewDock = { SettingsPreviewDock(capture, surfaces = PreviewSurfaces(prefs = { prefs }, homeOnGlance = { onGlance })) })
            }
        }
        rule.mainClock.advanceTimeBy(1_500); rule.waitForIdle()
        copy(); rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        rule.onNodeWithText("Clock style").performClick()
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
    }

    private fun copy(): Bitmap {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val latch = java.util.concurrent.CountDownLatch(1)
        val thread = android.os.HandlerThread("copy").also { it.start() }
        rule.runOnUiThread { android.view.PixelCopy.request(rule.activity.window, bmp, { latch.countDown() }, android.os.Handler(thread.looper)) }
        latch.await(10, java.util.concurrent.TimeUnit.SECONDS); thread.quit()
        return bmp
    }

    /** The picture inside the window, cropped from a real draw; also written to build/screenshots. */
    private fun window(name: String): Bitmap {
        val bmp = copy()
        val r = rule.onNodeWithTag(PreviewWindowTags.CONTENT).getUnclippedBoundsInRoot()
        val d = rule.activity.resources.displayMetrics.density
        val at = IntArray(2).also { rule.activity.findViewById<android.view.View>(android.R.id.content).getLocationInWindow(it) }
        val x = (r.left.value * d + at[0]).toInt().coerceIn(0, bmp.width - 1); val y = (r.top.value * d + at[1]).toInt().coerceIn(0, bmp.height - 1)
        val crop = Bitmap.createBitmap(bmp, x, y, ((r.right - r.left).value * d).toInt().coerceAtMost(bmp.width - x).coerceAtLeast(1), ((r.bottom - r.top).value * d).toInt().coerceAtMost(bmp.height - y).coerceAtLeast(1))
        val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        File(dir, "w58-preview-$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return crop
    }
    /** The share of sampled pixels near the picture's lower edge (below the clock) that are mostly red / mostly blue. */
    private fun redBlue(b: Bitmap): Pair<Float, Float> {
        var red = 0; var blue = 0; var n = 0
        for (i in 1..9) for (j in 1..3) {
            val p = b.getPixel(b.width * i / 10, (b.height * (0.62f + j * 0.08f)).toInt().coerceAtMost(b.height - 1)); n++
            val rr = (p shr 16) and 255; val bb = p and 255
            if (rr > bb + 60) red++ else if (bb > rr + 60) blue++
        }
        return red / n.toFloat() to blue / n.toFloat()
    }
    private fun backdropShown() = rule.onAllNodesWithTag(PreviewGlanceBackdrop.TAG, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun CFG_73_onAPageTheGlanceTopicDrawsTheGlancesOwnWallpaper() {
        show()
        assertTrue(backdropShown())
        val (red, blue) = redBlue(window("glance-on-page"))
        assertTrue("the Glance's red wallpaper fills the picture, not the page's blue (red $red, blue $blue)", red > 0.7f && blue < 0.1f)
    }

    @Test fun CFG_73_withTheSameWallpaperOrOnTheGlanceTheCaptureIsUsed() {
        prefs = prefs.copy(pagesSameAsGlance = true)
        show()
        assertTrue("the shared wallpaper is already the capture", !backdropShown())
        assertTrue(PreviewGlanceBackdrop.own(prefs.copy(pagesSameAsGlance = false)))
    }

    @Test fun CFG_73_onTheGlanceTheExactCaptureIsUsed() {
        onGlance = true
        show()
        assertTrue(!backdropShown())
    }
}
