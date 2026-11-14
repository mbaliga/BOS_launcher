package com.bos.sphere.feature.grid

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.design.rememberMotionPrefs
import com.bos.sphere.core.quality.Contrast
import com.bos.sphere.core.quality.HomeLegibility
import com.bos.sphere.core.quality.MotionPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** NFR-14 (reduced motion on the surfaces) and NFR-15 (scrim over the wallpaper) on the JVM. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
class ReducedMotionUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun grab(): android.graphics.Bitmap {
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        return bmp
    }

    private fun sampleApps() = (0 until 12).map { i ->
        com.bos.sphere.core.data.AppEntry("demo.r$i", android.content.ComponentName("demo.r$i", "M"), "${'A' + i}pp", android.os.Process.myUserHandle()) {
            android.graphics.drawable.ColorDrawable(0xFF3070D0.toInt() + i * 0x101010)
        }
    }

    @After fun reset() = ReducedMotionTesting.force(null)

    @Test fun NFR_14_composableReadsTheSystemAnimationScaleAndFollowsChanges() {
        val resolver = rule.activity.contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        var seen by mutableStateOf(MotionPrefs.Default)
        rule.setContent { seen = rememberMotionPrefs() }
        rule.waitForIdle()
        assertFalse(seen.reduced)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        resolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
        rule.waitForIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        rule.waitForIdle()
        assertTrue("reduced after the scale is set to 0", seen.reduced)
    }

    @Test fun NFR_14_settingsSpaceOpensAndClosesWithACrossFadeWhenMotionIsReduced() {
        ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f))
        var open by mutableStateOf(false)
        rule.setContent {
            SettingsSpace(
                visible = open,
                categories = listOf(SettingCategory("Reduced", Glyphs.Apps, listOf(SettingItem.Action("Probe row", Glyphs.Apps, "x") {}))),
                initialCategory = 0, sounds = null, onClose = {}, modifier = Modifier.fillMaxSize(),
            )
        }
        open = true
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithText("Reduced").assertIsDisplayed()
        open = false
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithText("Reduced").assertDoesNotExist()
    }

    @Test fun NFR_14_drawerPaintsAtFullSizeOnTheFirstFramesWhenMotionIsReduced() {
        ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f))
        var open by mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                WheelDrawer(
                    visible = open, anchor = com.bos.sphere.core.data.Corner.BottomRight, apps = sampleApps(),
                    grouping = com.bos.sphere.core.data.WheelGrouping.Letter, onGroupingChange = {}, recentKeys = emptyList(),
                    onLaunch = {}, onLongPressApp = {}, onSearch = {}, onDismiss = {}, modifier = Modifier.fillMaxSize(),
                )
            }
        }
        open = true
        // Step frame by frame until the first pixel shows: that is the earliest the surface is visible at all.
        var early = grab()
        var t = 0
        while (t < 400 && early.let { b -> IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }.none { px -> (px and 0xFFFFFF) != 0 } }) {
            rule.mainClock.advanceTimeBy(16); t += 16; early = grab()
        }
        rule.mainClock.advanceTimeBy(1000)
        val settled = grab()
        // With a cross-fade the geometry is final from the start: the picture only gets more opaque, never moves.
        // Compare lit pixels: every pixel lit at its first visible frame is also lit when settled (no part of it travelled away).
        val a = IntArray(early.width * early.height).also { early.getPixels(it, 0, early.width, 0, 0, early.width, early.height) }
        val b = IntArray(settled.width * settled.height).also { settled.getPixels(it, 0, settled.width, 0, 0, settled.width, settled.height) }
        assertEquals(a.size, b.size)
        val litEarly = a.indices.filter { (a[it] and 0xFFFFFF) != 0 }
        val stillLit = litEarly.count { (b[it] and 0xFFFFFF) != 0 }
        val litSettled = b.indices.count { (b[it] and 0xFFFFFF) != 0 }
        assertTrue("something is drawn already at its first visible frame (early ${litEarly.size}, settled $litSettled)", litEarly.isNotEmpty())
        assertTrue("lit at its first visible frame: ${litEarly.size}, still lit settled: $stillLit", stillLit >= litEarly.size * 95 / 100)
    }

    @Test fun NFR_15_scrimPaintsTheCompositeTheMathPromises() {
        val wallpaper = Color(0xFFFFE000)
        val plan = HomeLegibility.plan(listOf(Contrast.WHITE), listOf(wallpaper.toArgb()))
        assertTrue(plan.reachesAa)
        rule.mainClock.autoAdvance = false
        rule.setContent { Box(Modifier.fillMaxSize().background(wallpaper)) { HomeScrim(plan) } }
        rule.mainClock.advanceTimeBy(100)
        val px = grab().getPixel(10, 10)
        // Opaque result of the scrim over the wallpaper must give white text AA contrast (small rounding allowed).
        assertTrue("ratio ${Contrast.ratio(Contrast.WHITE, px or (0xFF shl 24))}", Contrast.ratio(Contrast.WHITE, px or (0xFF shl 24)) >= Contrast.AA_NORMAL - 0.05)
    }

    @Test fun NFR_15_noScrimIsDrawnWhenTheWallpaperAlreadyGivesAa() {
        val plan = HomeLegibility.plan(listOf(Contrast.WHITE), listOf(Contrast.BLACK))
        assertEquals(0.0, plan.alpha, 0.0)
        rule.mainClock.autoAdvance = false
        rule.setContent { Box(Modifier.fillMaxSize().background(Color.Black)) { HomeScrim(plan) } }
        rule.mainClock.advanceTimeBy(100)
        val px = grab().getPixel(5, 5)
        assertEquals(0, px and 0xFFFFFF)
    }

    @Test fun NFR_15_solidAndGradientBackgroundsAreSampledFromTheirRealColours() {
        assertEquals(listOf(0xFFF1EBDD.toInt()), WallpaperSamples.staticColors(com.bos.sphere.core.data.BackgroundKind.Solid, "ivory", 0, 0))
        val g = WallpaperSamples.staticColors(com.bos.sphere.core.data.BackgroundKind.Gradient, "candy", 0, 0)
        assertEquals(3, g.size)
        assertTrue(WallpaperSamples.staticColors(com.bos.sphere.core.data.BackgroundKind.Live, "aurora", 0, 0).isEmpty())
        // The brightest built-in solid needs a real scrim for light text; the darkest needs none.
        val ivory = HomeLegibility.plan(listOf(Contrast.WHITE), WallpaperSamples.staticColors(com.bos.sphere.core.data.BackgroundKind.Solid, "ivory", 0, 0))
        val ink = HomeLegibility.plan(listOf(Contrast.WHITE), WallpaperSamples.staticColors(com.bos.sphere.core.data.BackgroundKind.Solid, "ink", 0, 0))
        assertTrue(ivory.alpha > 0.4 && ivory.reachesAa)
        assertEquals(0.0, ink.alpha, 0.0)
    }
}
