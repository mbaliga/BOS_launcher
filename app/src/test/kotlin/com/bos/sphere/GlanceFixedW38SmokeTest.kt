package com.bos.sphere

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.up
import com.bos.sphere.core.data.FreshHome
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.MediaNow
import com.bos.sphere.feature.grid.LauncherInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Wave 38 (glanceFixed), the REAL home under Robolectric: the four hotspots and the music line are fixed chrome above the pager, so they stay at the same
 * screen position on Glance, in the middle of a swipe and on a page; the music line shows on a page; the Glance page has no clock, date or weather; and
 * the settings no longer list Glance content. Not a device run: the swipe feel and the hotspot against pager gesture conflicts need a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class GlanceFixedW38SmokeTest : TourBase() {
    private val stores get() = LauncherStores.get(AppSmokeHarness.app)
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private val track = MediaInfo("Someday Out of The Blue", "Artist", "pkg.music", playing = true, canPrev = true, canNext = true)

    private fun start() {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        AppSmokeHarness.configureFresh { it.copy(glanceEnabled = true, homePages = HomePages.Paginated) }
        AppSmokeHarness.app.getSharedPreferences("liftoff_misc", android.content.Context.MODE_PRIVATE).edit().putBoolean("defaultHomeSeeded", true).commit()
        stores.layout.replace(FreshHome.emptyLayout())
        launch()
    }

    private fun rects() = LauncherInput.focus.nodes().filter { it.id.startsWith("corner.") || it.id == "glance.clock" }.associate { it.id to it.rect }
    private fun swipe(forward: Boolean) { rule.onNodeWithTag("home.pager").performTouchInput { val y = height * 0.5f; val a = width * 0.75f; val b = width * 0.25f; swipe(Offset(if (forward) a else b, y), Offset(if (forward) b else a, y), 300) }; settle() }

    private fun shoot(name: String) {
        settle(400)
        var bmp: Bitmap? = null
        sc.onActivity { a ->
            val view = a.window.decorView
            val b = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            android.view.PixelCopy.request(a.window, b, { }, android.os.Handler(android.os.Looper.getMainLooper()))
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            bmp = b
        }
        File(outDir, "$name.png").outputStream().use { bmp!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun GFX_38_theFourHotspotsStayAtTheSameScreenPositionOnGlanceMidSwipeAndOnTheNextPage() {
        start()
        assertTrue(glanceShown())
        val onGlance = rects(); assertEquals("four hotspots and the clock", 5, onGlance.size)
        rule.onNodeWithTag("home.pager").performTouchInput { down(Offset(width * 0.75f, height * 0.5f)); moveBy(Offset(-width * 0.3f, 0f)) }
        rule.mainClock.advanceTimeBy(100)
        val mid = rects()
        rule.onNodeWithTag("home.pager").performTouchInput { up() }
        settle()
        if (glanceShown()) swipe(true)
        assertFalse("now on the first page", glanceShown())
        val onPage = rects()
        for ((id, r) in onGlance) { assertEquals("$id mid-swipe", r, mid.getValue(id)); assertEquals("$id on the page", r, onPage.getValue(id)) }
        shoot("w38_page_dark")
    }

    @Test fun GFX_38_theMusicLineShowsOnAPageWhenMediaIsActive() {
        start()
        MediaNow.publishForTest(track); settle()
        onNodeShown("Now playing: Someday Out of The Blue · Artist")
        swipe(true)
        assertFalse(glanceShown())
        onNodeShown("Now playing: Someday Out of The Blue · Artist")
        MediaNow.publishForTest(null); settle()
        assertFalse(descShown("Now playing", substring = true))
    }

    private fun onNodeShown(desc: String) { rule.onNodeWithContentDescription(desc).assertIsDisplayed() }

    @Test fun GFX_38_theClockIsOnGlanceAndOnAPageAndThereIsNoDateOrWeatherAnywhere() {
        start()
        MediaNow.publishForTest(track); settle()
        assertTrue(glanceShown())
        assertTrue("the default face is analog", tagShown("glance.clock.analog"))
        onNodeShown("Now playing: Someday Out of The Blue · Artist")
        for (t in listOf("glance.time", "glance.date", "glance.weather")) assertFalse(t, tagShown(t))
        shoot("w38_glance_dark")
        swipe(true)
        assertFalse(glanceShown())
        assertTrue("the clock is on the page too", tagShown("glance.clock.analog"))
        onNodeShown("Now playing: Someday Out of The Blue · Artist")
        for (t in listOf("glance.time", "glance.date", "glance.weather")) assertFalse(t, tagShown(t))
        val clock = rule.onNodeWithTag("glance.clock.analog", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val music = rule.onNodeWithContentDescription("Now playing: Someday Out of The Blue · Artist").fetchSemanticsNode().boundsInRoot
        assertTrue("the music line is under the clock", music.top >= clock.bottom - 1f)
        shoot("w38_page_clock_music_dark")
    }

    @Test fun GFX_38_theChosenCornerStyleAppliesToTheHotspotsOnEveryPageLive() {
        start()
        val glyph = rects().getValue("corner.BottomLeft")
        stores.prefs.update { it.copy(cornerStyle = com.bos.sphere.core.data.CornerStyle.Bubble) }; settle()
        val bubble = rects().getValue("corner.BottomLeft")
        assertTrue("the bubble style draws a different corner node than Holo: $glyph vs $bubble", glyph != bubble)
        swipe(true)
        assertEquals("the same bubble on the page", bubble, rects().getValue("corner.BottomLeft"))
        stores.prefs.update { it.copy(cornerStyle = com.bos.sphere.core.data.CornerStyle.Glyph) }; settle()
        assertEquals("back to Holo, live", glyph, rects().getValue("corner.BottomLeft"))
    }

    @Test fun GFX_38_theClockSettingIsAnalogOrDigitalWithTwelveOrTwentyFourHourForDigital() {
        val a = com.bos.sphere.ui.glanceSettings(LauncherPrefs()) { }
        assertTrue("Clock" in a.map { it.title }); assertFalse("Clock format" in a.map { it.title })
        val d = com.bos.sphere.ui.glanceSettings(LauncherPrefs(clockStyle = com.bos.sphere.core.data.ClockStyle.Digital)) { }
        assertTrue("Clock format" in d.map { it.title })
    }

    @Test fun GFX_38_theMusicToggleHidesTheLineOnEveryPage() {
        start()
        MediaNow.publishForTest(track); settle()
        stores.prefs.update { it.copy(showMusic = false) }; settle()
        assertFalse(descShown("Now playing", substring = true))
    }

    @Test fun GFX_38_theHomeSettingsListShowMusicAndNoGlanceContent() {
        val titles = com.bos.sphere.ui.glanceSettings(LauncherPrefs()) { }.map { it.title }
        assertTrue("Show music" in titles)
        for (gone in listOf("Glance content", "Time", "Date", "Weather", "Battery", "Weather effect")) assertFalse(gone, gone in titles)
        assertTrue("Hotspots" in titles)
    }
}
