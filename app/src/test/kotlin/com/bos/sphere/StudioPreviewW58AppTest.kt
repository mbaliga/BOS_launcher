package com.bos.sphere

import android.content.ComponentName
import android.graphics.Bitmap
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.dock.MagnifyStrength
import com.bos.sphere.feature.dock.WorkspaceRepository
import com.bos.sphere.feature.grid.PreviewStage
import com.bos.sphere.feature.grid.PreviewWindowTags
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.feature.grid.SettingsPreviewDock
import com.bos.sphere.feature.grid.SettingsSpace
import com.bos.sphere.feature.grid.captureHome
import com.bos.sphere.feature.grid.captureWallpaper
import com.bos.sphere.feature.grid.rememberHomeCapture
import com.bos.sphere.ui.CommsOptions
import com.bos.sphere.ui.LiftoffStudio
import com.bos.sphere.ui.PREVIEW_WIDGETS_TAG
import com.bos.sphere.ui.rememberPreviewSurfaces
import com.bos.sphere.ui.studioSettingItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Wave 58 (studioPreview): the app side. CFG-73: the floating Preview's side panel draws the Widgets section as sample cards when it is on, and the
 * dock topic draws the magnification as a sample bulge (the real rail with a still sample pointer), both through the app's own renderers.
 * CFG-74: the "Open Liftoff Studio" row reads "Not published yet" while its one address constant is the placeholder.
 * Screenshots: app/build/screenshots/w58-*.png (read by hand).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class StudioPreviewW58AppTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun setUp() { L10n.setLanguage("en"); PreviewStage.exposeSandboxSemantics = true }
    @After fun tearDown() {
        PreviewStage.exposeSandboxSemantics = false
        CommsOptions.setWidgetsSection(false)
        runCatching { WorkspaceRepository.get(rule.activity).setDock { it.copy(magnify = false, magnifyStrength = MagnifyStrength.Medium) } }
    }

    private val apps = listOf("Maps", "Mail", "Music", "Camera", "Clock").map { n -> AppEntry("demo.$n", ComponentName("demo.$n", "demo.$n.Main"), n, Process.myUserHandle()) { null } }

    private fun show(category: String) {
        val cats = listOf(
            SettingCategory("Side panel", com.bos.sphere.feature.grid.Glyphs.Bell, listOf(SettingItem.Toggle("Show pinned at the top", com.bos.sphere.feature.grid.Glyphs.Bell, true) {})),
            SettingCategory("Desktop & dock", com.bos.sphere.feature.grid.Glyphs.Apps, listOf(SettingItem.Toggle("Dock", com.bos.sphere.feature.grid.Glyphs.Apps, true) {})),
        )
        rule.mainClock.autoAdvance = true
        rule.setContent {
            val capture = rememberHomeCapture()
            val surfaces = rememberPreviewSurfaces(LauncherPrefs(), apps, apps.associateBy { it.key }, emptyList(), 6, false, homeOnGlance = { false })
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().captureWallpaper(capture).background(Brush.linearGradient(listOf(Color(0xFF2B4C8C), Color(0xFF7A3E9D)))))
                Box(Modifier.fillMaxSize().captureHome(capture))
                SettingsSpace(visible = true, categories = cats, initialCategory = cats.indexOfFirst { it.title == category }, sounds = null, onClose = {}, modifier = Modifier.fillMaxSize(),
                    previewDock = { SettingsPreviewDock(capture, surfaces = surfaces) })
            }
        }
        rule.mainClock.advanceTimeBy(1_500); rule.waitForIdle()
        pixelCopy(); rule.waitForIdle()
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
    }

    private fun pixelCopy(): Bitmap {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val latch = java.util.concurrent.CountDownLatch(1)
        val thread = android.os.HandlerThread("copy").also { it.start() }
        rule.runOnUiThread { android.view.PixelCopy.request(rule.activity.window, bmp, { latch.countDown() }, android.os.Handler(thread.looper)) }
        latch.await(10, java.util.concurrent.TimeUnit.SECONDS); thread.quit()
        return bmp
    }

    private fun shot(name: String) {
        rule.onNodeWithTag(PreviewWindowTags.RESIZE).performTouchInput { down(center); moveBy(Offset(80f, 600f)); up() }
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        val bmp = pixelCopy()
        val r = rule.onNodeWithTag(PreviewWindowTags.CONTENT).getUnclippedBoundsInRoot()
        val d = rule.activity.resources.displayMetrics.density
        val at = IntArray(2).also { rule.activity.findViewById<android.view.View>(android.R.id.content).getLocationInWindow(it) }
        val x = (r.left.value * d + at[0]).toInt().coerceIn(0, bmp.width - 1); val y = (r.top.value * d + at[1]).toInt().coerceIn(0, bmp.height - 1)
        val crop = Bitmap.createBitmap(bmp, x, y, ((r.right - r.left).value * d).toInt().coerceAtMost(bmp.width - x).coerceAtLeast(1), ((r.bottom - r.top).value * d).toInt().coerceAtMost(bmp.height - y).coerceAtLeast(1))
        val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        File(dir, "w58-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(dir, "w58-${name}_window.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun CFG_73_theSidePanelPreviewShowsTheWidgetsSectionAsSampleCards() {
        CommsOptions.setWidgetsSection(true)
        show("Side panel")
        assertTrue(rule.onAllNodesWithTag(PREVIEW_WIDGETS_TAG, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        shot("preview-sidepanel-widgets")
    }

    @Test fun CFG_73_theSidePanelPreviewHasNoWidgetCardsWithTheSectionOff() {
        show("Side panel")
        assertTrue(rule.onAllNodesWithTag(PREVIEW_WIDGETS_TAG, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test fun CFG_73_theDockPreviewDrawsTheMagnificationAsASampleBulge() {
        WorkspaceRepository.get(rule.activity).setDock { it.copy(enabled = true, magnify = true, magnifyStrength = MagnifyStrength.Large) }
        show("Desktop & dock")
        val widths = listOf("Maps", "Mail", "Music", "Camera").map { n ->
            rule.onAllNodes(hasContentDescription(n), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot.width
        }
        assertTrue("one tile is magnified well past the others: $widths", widths.max() > widths.min() * 1.4f)
        shot("preview-dock-bulge")
    }

    @Test fun CFG_74_theStudioRowSaysNotPublishedYetWhileItsAddressIsThePlaceholder() {
        assertEquals(LiftoffStudio.PLACEHOLDER_URL, LiftoffStudio.URL)
        assertFalse(LiftoffStudio.PUBLISHED)
        assertEquals("Not published yet", studioSettingItem().value)
        assertTrue(LiftoffStudio.isPublished("https://studio.example.org/liftoff/studio/"))
        assertEquals("a published address reads as the editor again", "Edit your setup in a browser", studioSettingItem(published = true).value)
        // one constant: no other source file spells the address
        val src = File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.filter { it.readText().contains("example.com/liftoff") }.map { it.name }.toList()
        assertEquals(listOf("StudioPane.kt"), src)
    }
}
