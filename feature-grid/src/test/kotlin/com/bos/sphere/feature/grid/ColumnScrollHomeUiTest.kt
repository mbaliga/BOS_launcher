package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.catalog.ColumnFill
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HomeOrder
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.ThemeVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** HOM-01, HOM-07, HOM-23 (wave 26): the SCROLL home renders column by column in portrait and landscape, mirrored by the order and untouched by the language direction. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColumnScrollHomeUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private fun app(i: Int) = AppEntry("com.app$i", ComponentName("com.app$i", "com.app$i.Main"), "App $i", Process.myUserHandle()) { ColorDrawable(Color.HSVToColor(floatArrayOf((i * 37f) % 360f, 0.6f, 0.9f))) }
    private val apps = List(30) { app(it) }

    private var layout: ColumnFill.Layout? = null

    private fun show(order: HomeOrder, languageRtl: Boolean = false, n: Int = 30, top: Int = 48, bottom: Int = 72) {
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (languageRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0B1020))) {
                    ColumnScrollHome(
                        keys = apps.take(n).map { it.key }, appsByKey = apps.associateBy { it.key }, extras = null, order = order,
                        topBand = top.dp, bottomBand = bottom.dp, onLaunch = {}, onAppLongPress = {}, onLayout = { layout = it },
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun bounds(i: Int) = rule.onNodeWithTag("scroll.cell.${apps[i].key}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(400)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        Canvas(out).also { it.drawColor(0xFF0B1020.toInt()); it.drawBitmap(bmp, 0f, 0f, null) }
        File(outDir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_01_portraitThirtyIconsFillTheMiddleColumnByColumnFromTheLeft() {
        show(HomeOrder.LeftToRight)
        val l = layout!!
        assertTrue("several rows fit the tall window: ${l.rows}", l.rows >= 6)
        val first = bounds(0); val second = bounds(1); val next = bounds(l.rows)
        assertEquals("the second icon is below the first in the same column", first.left, second.left, 1f); assertTrue(second.top > first.top)
        assertTrue("after the last row the next column starts at the top, to the right", next.left > first.left && Math.abs(next.top - first.top) < 1f)
        assertTrue("nothing under the top band", first.top >= 48f * 2f - 1f) // xhdpi = 2 px per dp
        shoot("w26-scroll-portrait-30")
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_01_landscapeThirtyIconsUseFewerRowsAndMoreColumns() {
        show(HomeOrder.LeftToRight, top = 36, bottom = 36)
        val l = layout!!
        assertTrue("landscape has fewer rows: ${l.rows}", l.rows in 2..5)
        assertTrue("and more columns than portrait would need", l.columns >= 30 / 5)
        shoot("w26-scroll-landscape-30")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_rightToLeftOrderStartsAtTheRightEdgeAndFillsColumnsRightToLeft() {
        show(HomeOrder.RightToLeft)
        val l = layout!!
        val first = bounds(0); val next = bounds(l.rows)
        assertTrue("the first column is at the right", first.right > 411f * 2f * 0.7f)
        assertTrue("the next column is to the LEFT of the first", next.right < first.left + 1f)
        shoot("w26-scroll-rtl-order")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_theLanguageDirectionDoesNotChangeTheScrollLayout() {
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
        var languageRtl by androidx.compose.runtime.mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (languageRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Box(Modifier.fillMaxSize()) { ColumnScrollHome(keys = apps.map { it.key }, appsByKey = apps.associateBy { it.key }, extras = null, order = HomeOrder.LeftToRight, topBand = 48.dp, bottomBand = 72.dp, onLaunch = {}, onAppLongPress = {}, onLayout = { layout = it }) }
            }
        }
        rule.waitForIdle()
        val ltrLayout = layout; val a = bounds(0) to bounds(7)
        rule.runOnUiThread { languageRtl = true }; rule.waitForIdle()
        assertEquals("the same layout under a right-to-left language", ltrLayout, layout)
        assertEquals(a.first, bounds(0)); assertEquals(a.second, bounds(7))
    }
}
