package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.quality.MotionPrefs
import com.bos.sphere.core.design.ReducedMotionTesting
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** HOM-16: every scrolling home style reaches the wallpaper through [WallpaperScrollEffect]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperScrollUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val apps = (0 until 40).map { i ->
        AppEntry("demo.w$i", ComponentName("demo.w$i", "demo.w$i.Main"), "App $i", Process.myUserHandle()) { ColorDrawable(0xFF3355AA.toInt()) }
    }
    private val sent = mutableListOf<Pair<Float, Int>>()

    @After fun reset() { ReducedMotionTesting.force(null) }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_16_effectForwardsPositionDisabledSendsNothingVerticalSendsCentred() {
        val pos = mutableFloatStateOf(0.4f)
        val enabled = androidx.compose.runtime.mutableStateOf(true)
        val across = androidx.compose.runtime.mutableStateOf(true)
        rule.setContent {
            CompositionLocalProvider(LocalWallpaperForward provides { p, n -> sent += p to n }) {
                WallpaperScrollEffect(enabled.value, pos.floatValue, 4, across.value)
            }
        }
        rule.waitForIdle()
        assertEquals(listOf(0.4f to 4), sent)
        across.value = false; rule.waitForIdle()
        assertEquals(0f to 1, sent.last()) // centred offset, one page
        sent.clear(); enabled.value = false; pos.floatValue = 2f; rule.waitForIdle()
        assertTrue(sent.isEmpty())
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_16_animationsOffSnapsTheForwardedPositionToThePage() {
        ReducedMotionTesting.force(MotionPrefs(0f, 0f))
        val pos = mutableFloatStateOf(1.4f)
        rule.setContent {
            CompositionLocalProvider(LocalWallpaperForward provides { p, n -> sent += p to n }) { WallpaperScrollEffect(true, pos.floatValue, 4) }
        }
        rule.waitForIdle()
        assertEquals(1f to 4, sent.last())
        pos.floatValue = 1.6f; rule.waitForIdle()
        assertEquals(2f to 4, sent.last())
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_16_horizontalContinuousGridForwardsItsProgress() {
        rule.setContent {
            CompositionLocalProvider(LocalWallpaperForward provides { p, n -> sent += p to n }) {
                ContinuousHome(apps = apps, crossCount = 3, landscape = true, style = ContinuousStyle.Plain, onLaunch = {}, onLongPress = {}, wallpaperScroll = true)
            }
        }
        rule.waitForIdle()
        assertEquals("at the start of a horizontal grid", 0f to com.bos.sphere.core.catalog.WallpaperScroll.GRID_PAGES, sent.last())
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_16_verticalContinuousGridSendsCentred() {
        rule.setContent {
            CompositionLocalProvider(LocalWallpaperForward provides { p, n -> sent += p to n }) {
                ContinuousHome(apps = apps, crossCount = 3, landscape = false, style = ContinuousStyle.Dsi, onLaunch = {}, onLongPress = {}, wallpaperScroll = true)
            }
        }
        rule.waitForIdle()
        assertEquals(0f to 1, sent.last())
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_16_continuousGridWithoutTheOptionForwardsNothing() {
        rule.setContent {
            CompositionLocalProvider(LocalWallpaperForward provides { p, n -> sent += p to n }) {
                ContinuousHome(apps = apps, crossCount = 3, landscape = true, style = ContinuousStyle.Plain, onLaunch = {}, onLongPress = {})
            }
        }
        rule.waitForIdle()
        assertTrue(sent.isEmpty())
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_16_xmbForwardsTheCategoryBarPosition() {
        rule.setContent {
            CompositionLocalProvider(LocalWallpaperForward provides { p, n -> sent += p to n }) {
                XmbHome(apps = apps, recent = emptyList(), categoryOf = { if (it.label.length % 2 == 0) "A" else "B" }, onLaunch = {}, onLongPress = {}, wallpaperScroll = true)
            }
        }
        rule.waitForIdle()
        assertTrue("forwards at the first category", sent.isNotEmpty())
        assertEquals(0f, sent.last().first, 0f)
        assertTrue("one page per category", sent.last().second >= 2)
    }
}
