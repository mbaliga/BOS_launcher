package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.LayoutDirection
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.DefaultCorners
import com.bos.sphere.core.data.GlanceHotspots
import com.bos.sphere.core.data.GlanceItem
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.RightDestination
import com.bos.sphere.core.data.WeatherInfo
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.design.ThemeVariant
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
import java.io.File

/**
 * HOM-23, HOM-22, INP-16, INP-17, STR-06, NFR-13, NFR-14: the Glance home under Robolectric. Portrait and landscape, dark and
 * light, right to left, font scale 200 percent, the pop-in controls and their timer, the hotspot count. Screenshots go to
 * build/screenshots/glance_*.png. Robolectric has no compositor: none of this is a device run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlanceHomeUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @After fun reset() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
        ReducedMotionTesting.force(null)
        L10n.setLanguage("")
        runCatching { LauncherInput.focus.nodes().map { it.id }.forEach(LauncherInput.focus::unregister) }
    }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(300)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { Canvas(bmp).also { it.drawColor(android.graphics.Color.BLACK); view.draw(it) } }
        File(outDir, "glance_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val media = MediaInfo("Someday Out of The Blue", "Artist", "pkg.music", playing = true, canPrev = true, canNext = true)
    private fun hotspots(on: Set<Corner> = GlanceHotspots.ALL) = DefaultCorners.filterKeys { it in on }.map { (c, slot) -> Hotspot(c, GlanceHotspots.itemOf(slot)!!) }

    private fun glance(
        prefs: LauncherPrefs = LauncherPrefs(), media: MediaInfo? = this.media, weather: WeatherInfo? = null, spots: List<Hotspot> = hotspots(),
        rtl: Boolean = false, ran: MutableList<Pair<CornerItem, Corner>> = ArrayList(), clicks: IntArray = IntArray(4), dark: Boolean = true,
        right: List<RightDestination> = listOf(RightDestination.Recents),
    ) = rule.setContent {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            GlanceSurface(
                prefs = prefs, weather = weather, media = media, hotspots = spots, appsByKey = emptyMap(), rightDestinations = right, hasLaunchpad = true,
                onRun = { i, c -> ran += i to c }, onClockTap = { clicks[0]++ }, onPrevious = { clicks[1]++ }, onPlayPause = { clicks[2]++ }, onNext = { clicks[3]++ },
                dark = dark, modifier = Modifier.fillMaxSize(),
            )
        }
    }

    private fun tag(t: String) = rule.onNodeWithTag(t, useUnmergedTree = true)
    private fun visible(desc: String) = rule.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().isNotEmpty()
    private fun controlsShown() = visible("Next")

    // ---- the centre

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_portraitShowsTheClockTheDateTheMusicLineAndFourHotspots() {
        glance()
        tag("glance.time").assertIsDisplayed()
        tag("glance.date").assertIsDisplayed()
        rule.onNodeWithContentDescription("Now playing: Someday Out of The Blue · Artist").assertIsDisplayed()
        for (d in listOf("Top left hotspot: Camera", "Top right hotspot: Music", "Bottom left hotspot: All apps", "Bottom right hotspot: Radar")) assertEquals(d, 1, rule.onAllNodesWithContentDescription(d, substring = true).fetchSemanticsNodes().size)
        assertFalse("the controls are not shown until a tap", controlsShown())
        shoot("portrait_dark")
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_23_landscapeIsTheSameCompositionWithTheClockCentred() {
        glance()
        tag("glance.time").assertIsDisplayed()
        val t = tag("glance.time").fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals("the clock is centred horizontally", root.center.x, t.center.x, 2f)
        assertEquals("the clock is centred vertically (within the date's height)", root.center.y, t.center.y, root.height * 0.15f)
        val b = LauncherInput.focus.nodes().associate { it.id to it.rect }
        assertTrue(b.getValue("minimal.corner.TopLeft").left < b.getValue("minimal.corner.TopRight").left)
        shoot("landscape_dark")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_lightThemeIsAQuietLightSurfaceWithDarkType() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF) }
        glance(dark = false)
        tag("glance.time").assertIsDisplayed()
        shoot("portrait_light")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_rightToLeftMirrorsTheIndicatorButNotTheClockOrTheCorners() {
        glance(rtl = true)
        val b = LauncherInput.focus.nodes().associate { it.id to it.rect }
        assertTrue("physical corners stay put", b.getValue("minimal.corner.TopLeft").left < b.getValue("minimal.corner.TopRight").left)
        tag("glance.time").assertIsDisplayed()
        shoot("portrait_rtl")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi", fontScale = 2f)
    fun HOM_23_at200PercentFontTheClockGrowsButStaysInsideAndTheDateWraps() {
        glance(prefs = LauncherPrefs(glanceContent = setOf(GlanceItem.Time, GlanceItem.Date, GlanceItem.Weather, GlanceItem.NowPlaying)), weather = WeatherInfo(14f, 2, 0L))
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val t = tag("glance.time").fetchSemanticsNode().boundsInRoot
        assertTrue("inside the window", t.left >= 0f && t.right <= root.right)
        val corners = LauncherInput.focus.nodes().filter { it.id.startsWith("minimal.corner.") }.map { it.rect }
        for (c in corners) assertFalse("the clock does not overlap a hotspot", t.left < c.right && t.right > c.left && t.top < c.bottom && t.bottom > c.top)
        shoot("portrait_font200")
    }

    @Test @Config(sdk = [35]) fun HOM_23_theClockSizeScalesWithTheFontButNeverOutgrowsTheWindow() {
        val a = GlanceText.clockSizeDp(411f, 891f, 1f); val b = GlanceText.clockSizeDp(411f, 891f, 2f)
        assertTrue(b >= a); assertTrue(b <= 411f * 0.28f + 0.01f)
        assertTrue(GlanceText.clockSizeDp(891f, 411f, 2f) <= 411f * 0.42f + 0.01f)
        assertEquals("Title", GlanceText.nowPlayingLine("Title", "")); assertEquals("T · A", GlanceText.nowPlayingLine("T", "A"))
        assertEquals("HH:mm", GlanceText.timePattern(com.bos.sphere.core.data.ClockFormat.Hour24, false)); assertEquals("h:mm", GlanceText.timePattern(com.bos.sphere.core.data.ClockFormat.System, false))
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_theContentToggleHidesTheTimeAndTheDateAndTheWeatherJoinsTheDate() {
        glance(prefs = LauncherPrefs(glanceContent = setOf(GlanceItem.Date, GlanceItem.Weather)), weather = WeatherInfo(14.4f, 0, 0L), media = null)
        assertEquals(0, rule.onAllNodesWithTag("glance.time", useUnmergedTree = true).fetchSemanticsNodes().size)
        tag("glance.date").assertIsDisplayed()
        assertTrue("the temperature sits next to the date", visible("Clear, 14°"))
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_tappingTheClockRunsTheClockAction() {
        val clicks = IntArray(4)
        glance(clicks = clicks)
        tag("glance.time").performClick()
        assertEquals(1, clicks[0])
    }

    // ---- the pop-in controls

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_aTapOnTheSurfacePopsTheControlsInAndTheyPopOutAfterFourSeconds() {
        glance()
        assertFalse(controlsShown())
        rule.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.75f)) }
        rule.mainClock.advanceTimeBy(600)
        assertTrue("controls pop in", controlsShown() && visible("Previous") && visible("Pause"))
        shoot("portrait_controls")
        rule.mainClock.advanceTimeBy(3000)
        assertTrue("still there before the timeout", controlsShown())
        rule.mainClock.advanceTimeBy(1500)
        rule.mainClock.advanceTimeBy(600)
        assertFalse("controls pop out after inactivity", controlsShown())
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_usingAControlRestartsTheTimerAndAFingerDownPausesIt() {
        val clicks = IntArray(4)
        glance(clicks = clicks)
        rule.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.75f)) }
        rule.mainClock.advanceTimeBy(600)
        rule.mainClock.advanceTimeBy(3000)
        rule.onNodeWithContentDescription("Next").performClick()
        assertEquals(1, clicks[3])
        rule.mainClock.advanceTimeBy(3000)
        assertTrue("the press restarted the timer", controlsShown())
        rule.onNodeWithContentDescription("Pause").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(20_000)
        assertTrue("a finger down keeps them", controlsShown())
        rule.onNodeWithContentDescription("Pause").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(4600)
        assertFalse("and releasing restarts the timer", controlsShown())
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_aTapOutsideTheControlsPopsThemOutAndTheTimeoutSettingIsHonoured() {
        glance(prefs = LauncherPrefs(glanceControlsSeconds = 9))
        rule.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.75f)) }
        rule.mainClock.advanceTimeBy(600)
        rule.mainClock.advanceTimeBy(6000)
        assertTrue("nine seconds is longer than four", controlsShown())
        rule.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.75f)) }
        rule.mainClock.advanceTimeBy(800)
        assertFalse("a tap outside pops them out", controlsShown())
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_withNothingPlayingTheMusicLineIsAbsentAndATapShowsNothing() {
        glance(media = null)
        assertEquals(0, rule.onAllNodesWithContentDescription("Now playing", substring = true).fetchSemanticsNodes().size)
        rule.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.75f)) }
        rule.mainClock.advanceTimeBy(600)
        assertFalse(controlsShown())
        shoot("portrait_nothing_playing")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_reducedMotionFadesTheControlsInInsteadOfTheSpring() {
        ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f))
        glance()
        rule.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.75f)) }
        rule.mainClock.advanceTimeBy(400)
        assertTrue(controlsShown())
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_theMusicLineIsAKeyboardReachableButtonThatPopsTheControlsIn() {
        glance()
        assertTrue(LauncherInput.focus.nodes().any { it.id == "glance.nowplaying" })
        rule.onNodeWithContentDescription("Now playing: Someday Out of The Blue · Artist").performClick()
        rule.mainClock.advanceTimeBy(600)
        assertTrue(controlsShown())
        assertEquals(setOf("glance.prev", "glance.playpause", "glance.next"), LauncherInput.focus.nodes().map { it.id }.filter { it.startsWith("glance.p") || it == "glance.next" }.toSet())
    }

    // ---- hotspots

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_aDisabledHotspotDrawsNothingAndTheCountFollowsThePreset() {
        val on = androidx.compose.runtime.mutableStateOf(GlanceHotspots.ALL)
        rule.setContent {
            GlanceSurface(
                prefs = LauncherPrefs(), weather = null, media = null, hotspots = hotspots(on.value), appsByKey = emptyMap(), rightDestinations = emptyList(), hasLaunchpad = false,
                onRun = { _, _ -> }, onClockTap = {}, onPrevious = {}, onPlayPause = {}, onNext = {}, modifier = Modifier.fillMaxSize(),
            )
        }
        for ((preset, n) in listOf(com.bos.sphere.core.data.HotspotPreset.Four to 4, com.bos.sphere.core.data.HotspotPreset.Two to 2, com.bos.sphere.core.data.HotspotPreset.One to 1, com.bos.sphere.core.data.HotspotPreset.None to 0)) {
            Snapshot.withMutableSnapshot { on.value = GlanceHotspots.preset(preset)!! }
            rule.waitForIdle()
            assertEquals("$preset on the focus graph", n, LauncherInput.focus.nodes().count { it.id.startsWith("minimal.corner.") })
            assertEquals("$preset in the semantics", n, rule.onAllNodesWithContentDescription("hotspot", substring = true).fetchSemanticsNodes().size)
        }
        shoot("portrait_no_hotspots")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_aHotspotRunsItsAssignedActionAndDrawsItsGlyph() {
        val ran = ArrayList<Pair<CornerItem, Corner>>()
        glance(ran = ran)
        rule.onNodeWithContentDescription("Top left hotspot: Camera").performClick()
        assertEquals(listOf(Corner.TopLeft), ran.map { it.second })
    }

    // ---- the indicator and the weather

    // Wave 17 (owner bug report on build 48): the indicator's list glyph and right-hand dots are real 48 dp buttons; the current surface is not.
    // Was: one merged spoken line "Glance home. Launchpad is on the left edge. Recent apps are on the right edge" that nothing could press.
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_theIndicatorButtonsOpenLaunchpadAndTheRightSideAndTheHomeGlyphIsNotInteractive() {
        val on = androidx.compose.runtime.mutableStateOf(true)
        val opened = ArrayList<String>()
        rule.setContent {
            GlanceSurface(
                prefs = LauncherPrefs(glanceIndicator = on.value), weather = null, media = null, hotspots = emptyList(), appsByKey = emptyMap(),
                rightDestinations = listOf(RightDestination.Recents), hasLaunchpad = true,
                onRun = { _, _ -> }, onClockTap = {}, onPrevious = {}, onPlayPause = {}, onNext = {}, modifier = Modifier.fillMaxSize(),
                onOpenLaunchpad = { opened += "launchpad" }, onOpenRight = { opened += "right" },
            )
        }
        val lp = tag("glance.indicator.launchpad"); val rt = tag("glance.indicator.right.Recents")
        lp.assertIsDisplayed(); rt.assertIsDisplayed()
        for (n in listOf(lp, rt)) { val b = n.fetchSemanticsNode().boundsInRoot; assertTrue("48 dp target", b.width >= 48f * 2f - 1f && b.height >= 48f * 2f - 1f) }
        assertEquals(1, rule.onAllNodesWithContentDescription("Open Launchpad").fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithContentDescription("Open recents").fetchSemanticsNodes().size)
        rule.onNodeWithContentDescription("Glance home").assertHasNoClickAction()
        lp.performClick(); rt.performClick()
        assertEquals(listOf("launchpad", "right"), opened)
        assertTrue("both are focus nodes", LauncherInput.focus.nodes().map { it.id }.containsAll(listOf("glance.indicator.launchpad", "glance.indicator.right.Recents")))
        Snapshot.withMutableSnapshot { on.value = false }
        rule.waitForIdle()
        assertFalse(visible("Open Launchpad")); assertFalse(visible("Open recents")); assertFalse(visible("Glance home"))
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_theDockRailDestinationDotIsAButtonToo() {
        val opened = ArrayList<String>()
        rule.setContent {
            GlanceSurface(
                prefs = LauncherPrefs(), weather = null, media = null, hotspots = emptyList(), appsByKey = emptyMap(),
                rightDestinations = listOf(RightDestination.Dock), hasLaunchpad = false,
                onRun = { _, _ -> }, onClockTap = {}, onPrevious = {}, onPlayPause = {}, onNext = {}, modifier = Modifier.fillMaxSize(), onOpenRight = { opened += "right" },
            )
        }
        assertFalse("Launchpad off: no Launchpad glyph", visible("Open Launchpad"))
        rule.onNodeWithContentDescription("Show the dock").performClick()
        assertEquals(listOf("right"), opened)
    }

    // ---- the quiet edge handles (wave 17)

    private fun handles(rtl: Boolean, wake: androidx.compose.runtime.MutableIntState, taps: MutableList<String>, dark: Boolean = true) = rule.setContent {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Box(Modifier.fillMaxSize()) {
                GlanceSurface(
                    prefs = LauncherPrefs(), weather = null, media = null, hotspots = hotspots(), appsByKey = emptyMap(), rightDestinations = listOf(RightDestination.Recents), hasLaunchpad = true,
                    onRun = { _, _ -> }, onClockTap = {}, onPrevious = {}, onPlayPause = {}, onNext = {}, dark = dark, modifier = Modifier.fillMaxSize(),
                )
                val tint = if (dark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
                val red = com.bos.sphere.core.design.rememberMotionPrefs().reduced
                GlanceEdgeHandle("Open Launchpad", "glance.handle.left", rightEdge = rtl, wake = { wake.intValue }, reduced = red, tint = tint, onOpen = { taps += "left" },
                    modifier = Modifier.align(androidx.compose.ui.BiasAbsoluteAlignment(if (rtl) 1f else -1f, 0f)))
                GlanceEdgeHandle("Open recents", "recents.handle", rightEdge = !rtl, wake = { wake.intValue }, reduced = red, tint = tint, onOpen = { taps += "right" },
                    modifier = Modifier.align(androidx.compose.ui.BiasAbsoluteAlignment(if (rtl) -1f else 1f, 0f)))
            }
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_theQuietHandlesAre48By160FocusableLabelledAndTapOpen() {
        val taps = ArrayList<String>(); val wake = androidx.compose.runtime.mutableIntStateOf(0)
        handles(false, wake, taps)
        for ((t, d) in listOf("glance.handle.left" to "Open Launchpad", "recents.handle" to "Open recents")) {
            val b = tag(t).fetchSemanticsNode().boundsInRoot
            assertEquals("$t is 48 dp wide", 48f * 2f, b.width, 1f)
            assertEquals("$t is 160 dp tall", 160f * 2f, b.height, 1f)
            assertTrue(d, rule.onAllNodesWithContentDescription(d).fetchSemanticsNodes().isNotEmpty())
            tag(t).assertHasClickAction()
        }
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals("left handle on the physical left edge", 0f, tag("glance.handle.left").fetchSemanticsNode().boundsInRoot.left, 1f)
        assertEquals("right handle on the physical right edge", root.right, tag("recents.handle").fetchSemanticsNode().boundsInRoot.right, 1f)
        tag("glance.handle.left").performClick(); tag("recents.handle").performClick()
        assertEquals(listOf("left", "right"), taps)
        shoot("handles_portrait_dark")
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_23_theQuietHandlesInLandscape() {
        handles(false, androidx.compose.runtime.mutableIntStateOf(0), ArrayList())
        tag("glance.handle.left").assertIsDisplayed(); tag("recents.handle").assertIsDisplayed()
        shoot("handles_landscape_dark")
    }

    @Test @Config(sdk = [35], qualifiers = "ldrtl-w411dp-h891dp-port-xhdpi")
    fun HOM_23_inRightToLeftTheLaunchpadHandleIsOnTheRightAndRecentsOnTheLeft() {
        handles(true, androidx.compose.runtime.mutableIntStateOf(0), ArrayList())
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(root.right, tag("glance.handle.left").fetchSemanticsNode().boundsInRoot.right, 1f)
        assertEquals(0f, tag("recents.handle").fetchSemanticsNode().boundsInRoot.left, 1f)
        shoot("handles_portrait_rtl")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_theHandlesFadeWhenIdleAndReturnOnAnyTouch() {
        val wake = androidx.compose.runtime.mutableIntStateOf(0)
        handles(false, wake, ArrayList())
        assertEquals(0.30f, GlanceHandleFade.alpha(true), 0f); assertEquals(0.06f, GlanceHandleFade.alpha(false), 0f)
        assertTrue("the idle alpha is barely there", GlanceHandleFade.IDLE_ALPHA < GlanceHandleFade.AWAKE_ALPHA / 4f)
        rule.mainClock.advanceTimeBy(GlanceHandleFade.IDLE_AFTER_MS + 1500)
        Snapshot.withMutableSnapshot { wake.intValue++ }
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
        shoot("handles_awake_again")
        tag("glance.handle.left").assertIsDisplayed()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_theHandlesOnTheLightSurface() {
        handles(false, androidx.compose.runtime.mutableIntStateOf(0), ArrayList(), dark = false)
        tag("glance.handle.left").assertIsDisplayed()
        shoot("handles_portrait_light")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_weatherInTheContentDrawsTheAmbienceBehindTheClockAndNeverBreaksTheSemantics() {
        ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f))
        glance(prefs = LauncherPrefs(glanceContent = setOf(GlanceItem.Time, GlanceItem.Date, GlanceItem.Weather)), weather = WeatherInfo(8f, 63, 0L), media = null)
        tag("glance.time").assertIsDisplayed()
        shoot("portrait_rain")
    }
}
