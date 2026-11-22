package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.DefaultCorners
import com.bos.sphere.core.data.GlanceHotspots
import com.bos.sphere.core.data.IndicatorReading
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.ReadingKind
import com.bos.sphere.core.design.L10n
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
import java.io.File

/** Wave 55 (calmStyles): the regular battery and signal indicators in the Glance chrome: content, and position in portrait, landscape and right-to-left, with and without top hotspots and the music line. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RegularIndicatorsW55UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    @After fun reset() { ReducedMotionTesting.force(null); L10n.setLanguage("") }

    private val media = MediaInfo("Someday Out of The Blue", "Artist", "pkg.music", playing = true, canPrev = true, canNext = true)
    private val signal3 = IndicatorReading(ReadingKind.Live, 0.75f, "75%")
    private val battery87 = IndicatorReading(ReadingKind.Live, 0.87f, "87%", charging = true)
    private fun hotspots(topOn: Boolean) = DefaultCorners.filterKeys { it in GlanceHotspots.ALL }.map { (c, slot) -> Hotspot(c, GlanceHotspots.itemOf(slot)!!) }
        .filter { topOn || (it.screen != com.bos.sphere.core.data.Corner.TopLeft && it.screen != com.bos.sphere.core.data.Corner.TopRight) }

    private fun host(m: MediaInfo? = media, topHotspots: Boolean = true, rtl: Boolean = false, signal: IndicatorReading = signal3, battery: IndicatorReading = battery87) = rule.setContent {
        CompositionLocalProvider(LocalLayoutDirection provides (if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
            Box(Modifier.fillMaxSize().background(Color(0xFF203050))) {
                FixedHomeChrome(
                    m, true, 4, {}, {}, {}, dark = true,
                    corners = { MinimalHotspotLayer(hotspots(topHotspots), emptyMap(), showHints = false, onRun = { _, _ -> }, showIcons = true) },
                    topLeftHotspot = topHotspots, topRightHotspot = topHotspots, regular = signal to battery,
                )
            }
        }
    }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(300)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { Canvas(bmp).also { it.drawColor(android.graphics.Color.BLACK); view.draw(it) } }
        File(outDir, "calm55-regular-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun checkPlacement(windowW: Float, windowH: Float, musicShown: Boolean, topHotspots: Boolean) {
        val r = rule.onNodeWithTag("regular.indicator").getUnclippedBoundsInRoot()
        assertTrue("inside the window", r.left >= 0.dp && r.right <= windowW.dp && r.top >= 0.dp && r.bottom <= windowH.dp)
        assertEquals("centred", windowW / 2f, (r.left.value + r.right.value) / 2f, 1.5f)
        assertTrue("small: no wider than ${RegularIndicatorGeometry.MAX_WIDTH_DP}", (r.right - r.left).value <= RegularIndicatorGeometry.MAX_WIDTH_DP)
        assertEquals(RegularIndicatorGeometry.HEIGHT_DP, (r.bottom - r.top).value, 0.6f)
        assertEquals("sits where the geometry says", RegularIndicatorGeometry.topDp(musicShown), r.top.value, 1f)
        if (topHotspots) {
            val sq = FixedHomeChrome.HOTSPOT_TOUCH_DP
            assertTrue("clear of the top-left hotspot square", r.left.value >= sq || r.top.value >= sq)
            assertTrue("clear of the top-right hotspot square", r.right.value <= windowW - sq || r.top.value >= sq)
        }
        if (musicShown) {
            val t = rule.onNodeWithTag("glance.title.slot").getUnclippedBoundsInRoot()
            assertTrue("under the music line, not over it (title bottom ${t.bottom}, row top ${r.top})", r.top.value >= t.bottom.value - 0.5f)
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_24_w55_portraitWithHotspotsAndMusicTheRowSitsUnderTheMusicLineBetweenTheTopHotspots() {
        host(); rule.mainClock.advanceTimeBy(300); checkPlacement(411f, 891f, musicShown = true, topHotspots = true); shoot("portrait-music")
        assertTrue(RegularIndicatorGeometry.clearsHotspots(411f))
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_24_w55_portraitWithoutMusicTheRowTakesTheMusicLinesPlace() {
        host(m = null); rule.mainClock.advanceTimeBy(300); checkPlacement(411f, 891f, musicShown = false, topHotspots = true); shoot("portrait-nomusic")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_24_w55_withNoTopHotspotsTheMusicLineRunsEdgeToEdgeAndTheRowStaysUnderIt() {
        host(topHotspots = false); rule.mainClock.advanceTimeBy(300); checkPlacement(411f, 891f, musicShown = true, topHotspots = false)
        val line = rule.onNodeWithTag("glance.title.slot").getUnclippedBoundsInRoot()
        assertEquals("the title slot is still where it was (the row is not inside it)", 40f, (line.bottom - line.top).value, 0.1f)
        shoot("portrait-no-top-hotspots")
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun PAL_24_w55_landscapeKeepsTheRowCentredUnderTheMusicLine() {
        host(); rule.mainClock.advanceTimeBy(300); checkPlacement(891f, 411f, musicShown = true, topHotspots = true); shoot("landscape")
        assertTrue(RegularIndicatorGeometry.clearsHotspots(891f))
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_24_w55_rightToLeftMirrorsTheOrderButKeepsTheCentre() {
        host(rtl = true); rule.mainClock.advanceTimeBy(300); checkPlacement(411f, 891f, musicShown = true, topHotspots = true)
        val sig = rule.onNodeWithTag("regular.signal").getUnclippedBoundsInRoot(); val bat = rule.onNodeWithTag("regular.battery").getUnclippedBoundsInRoot()
        assertTrue("right-to-left: the signal (first) is on the right of the battery", sig.left.value > bat.left.value)
        shoot("rtl")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_24_w55_leftToRightHasTheSignalFirst() {
        host(); rule.mainClock.advanceTimeBy(300)
        val sig = rule.onNodeWithTag("regular.signal").getUnclippedBoundsInRoot(); val bat = rule.onNodeWithTag("regular.battery").getUnclippedBoundsInRoot()
        assertTrue(sig.right.value <= bat.left.value)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_24_w55_theContentIsTheSignalBarsAndTheBatteryWithItsPercentageAndChargingBolt() {
        host(); rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithTag("regular.signal").assertContentDescriptionEquals("Signal: 3 of 4 bars")
        rule.onNodeWithTag("regular.battery").assertContentDescriptionEquals("Battery 87 percent, charging")
        rule.onNodeWithTag("regular.battery").assertExists()
        assertTrue("the percentage text is drawn", rule.onAllNodesWithText("87%").fetchSemanticsNodes().isNotEmpty())
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_24_w55_noConnectivityAndAirplaneModeAreSaidAndNotDrawnAsBars() {
        host(signal = IndicatorReading(ReadingKind.Off, 0f, "Offline"), battery = IndicatorReading(ReadingKind.Live, 0.12f, "12%")); rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithTag("regular.signal").assertContentDescriptionEquals("No signal")
        rule.onNodeWithTag("regular.battery").assertContentDescriptionEquals("Battery 12 percent")
        shoot("lost-low")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_24_w55_airplaneModeSaysSo() {
        host(signal = IndicatorReading(ReadingKind.Idle, 0f, "Airplane")); rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithTag("regular.signal").assertContentDescriptionEquals("Airplane mode")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_24_w55_translatedDescriptionsKeepTheirNumbers() {
        L10n.setLanguage("de")
        host(); rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithTag("regular.signal").assertContentDescriptionEquals("Signal: 3 von 4 Balken")
        rule.onNodeWithTag("regular.battery").assertContentDescriptionEquals("Akku 87 Prozent, wird geladen")
    }
}
