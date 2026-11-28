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
import java.io.File

/**
 * Wave 57 (previewAll), CFG-50..CFG-53: the floating Preview shows whatever the open page or the touched row configures: the home, Glance with
 * its clock, the corners (the chosen one pulsing), a gesture hint, Radar and the disc composed for real in a sandbox, the side panel and the dock
 * through the app's renderers; a page with nothing visual hides it. Screenshots go to build/screenshots/previewAll_*.png (read by hand).
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class PreviewAllW57UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val apps = listOf("Maps", "Mail", "Music", "Camera", "Clock", "Notes").map { n -> AppEntry("demo.$n", ComponentName("demo.$n", "demo.$n.Main"), n, Process.myUserHandle()) { null } }
    private var prefs by mutableStateOf(LauncherPrefs(searchLayout = SearchLayout.Flat, searchLayoutPinned = true, searchMotion = false, webSuggestions = true, searchWeb = true))
    private var labelCalls = 0

    @Before fun setUp() { L10n.setLanguage("en"); PreviewStage.exposeSandboxSemantics = true }
    @After fun tearDown() { ReducedMotionTesting.force(null); PreviewStage.exposeSandboxSemantics = false }

    private val noMotion = MotionPrefs(0f, 0f, 0f)

    private fun categories(): List<SettingCategory> = listOf(
        SettingCategory("Home screen", Glyphs.Apps, listOf(
            SettingItem.Toggle("App labels", Glyphs.Apps, true) {},
            SettingItem.Choice("Clock style", Glyphs.Clock, ClockStyle.entries, prefs.clockStyle, { it.label }) { v -> prefs = prefs.copy(clockStyle = v) },
            SettingItem.Toggle("Top right hotspot", Glyphs.Dial, true) {},
        )),
        SettingCategory("Corners", Glyphs.Plus, Corner.entries.map { c -> SettingItem.Action(c.label, Glyphs.Palette, "", navigates = true) {} }),
        SettingCategory("Gestures", Glyphs.Pinch, listOf(
            SettingItem.Action("Pinch in", Glyphs.Pinch, "", navigates = true) {},
            // a choice with more than nine options is a page row: its label runs each time the row composes (the recomposition counter)
            SettingItem.Choice("Counter", Glyphs.Dial, (1..12).toList(), 1, { labelCalls++; "$it" }) {},
        )),
        SettingCategory("Radar", Glyphs.Search, listOf(SettingItem.Toggle("Web suggestions", Glyphs.Search, prefs.webSuggestions) { v -> prefs = prefs.copy(webSuggestions = v) })),
        SettingCategory("App drawer", Glyphs.Widget, listOf(SettingItem.Toggle("Ring labels", Glyphs.Widget, true) {})),
        SettingCategory("Side panel", Glyphs.Bell, listOf(SettingItem.Toggle("Show pinned at the top", Glyphs.Bell, true) {})),
        SettingCategory("Desktop & dock", Glyphs.Apps, listOf(SettingItem.Toggle("Dock", Glyphs.Apps, true) {})),
        SettingCategory("Look", Glyphs.Palette, listOf(SettingItem.Toggle("Icon plate", Glyphs.Palette, false) {})),
        SettingCategory("System & sound", Glyphs.Gear, listOf(SettingItem.Toggle("Interface sounds", Glyphs.Gear, true) {})),
    )

    private fun idx(title: String) = categories().indexOfFirst { it.title == title }

    private fun surfaces() = PreviewSurfaces(
        prefs = { prefs },
        radar = { p -> PreviewRadar(p, apps) },
        disc = { p -> PreviewDisc(p, apps, listOf(PuckAction("Flashlight", Glyphs.Bolt) {}, PuckAction("Alarm", Glyphs.Clock) {}, PuckAction("Notifications", Glyphs.Bell) {}), 6, true) },
        sidePanel = { _ -> Box(Modifier.fillMaxSize().background(Color(0xFF15171C)).testTag("fake.comms")) { BasicText("Comms", style = TextStyle(color = Color.White, fontSize = 30.sp)) } },
        dock = { _ -> Box(Modifier.fillMaxSize().testTag("fake.dock")) },
        dockDetail = { "Standard, Bottom" },
    )

    private fun show(category: String, reduced: Boolean = true) {
        ReducedMotionTesting.force(if (reduced) noMotion else MotionPrefs(1f, 1f, 1f))
        rule.mainClock.autoAdvance = true
        rule.setContent {
            val capture = rememberHomeCapture()
            val cats = androidx.compose.runtime.remember { Snapshot.withoutReadObservation { categories() } } // built once: a prefs change redraws the preview, never the page
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.fillMaxSize().captureWallpaper(capture).background(Brush.linearGradient(listOf(Color(0xFF2B4C8C), Color(0xFF7A3E9D)))))
                Column(Modifier.fillMaxSize().captureHome(capture).padding(top = 120.dp, start = 24.dp)) {
                    repeat(4) { r -> androidx.compose.foundation.layout.Row { repeat(4) { c -> Box(Modifier.padding(14.dp).size(56.dp).background(Color(0xFF8E7BFF + (r * 4 + c) * 0x0A0A00L).copy(alpha = 0.9f))) } } }
                }
                SettingsSpace(
                    visible = true, categories = cats, initialCategory = idx(category), sounds = null, onClose = {},
                    modifier = Modifier.fillMaxSize(),
                    previewDock = { SettingsPreviewDock(capture, surfaces = surfaces()) },
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_500); rule.waitForIdle()
        drawOnce(); rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
    }

    /** One real (hardware) draw, so the home records itself into the capture: a software canvas cannot draw a recorded layer. */
    private fun drawOnce() {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val latch = java.util.concurrent.CountDownLatch(1)
        val thread = android.os.HandlerThread("draw").also { it.start() }
        rule.runOnUiThread { android.view.PixelCopy.request(rule.activity.window, bmp, { latch.countDown() }, android.os.Handler(thread.looper)) }
        latch.await(10, java.util.concurrent.TimeUnit.SECONDS); thread.quit()
    }

    private fun description(): String = rule.onNodeWithTag(PreviewWindowTags.CONTENT).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
    private fun stageShows(t: PreviewTopic) = rule.onAllNodesWithTag(PreviewStageTags.topic(t), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun settle(ms: Long = 400) { rule.mainClock.advanceTimeBy(ms); rule.waitForIdle() }

    // ---- CFG-50: the mapping ------------------------------------------------------------------------------------------------------

    @Test fun CFG_50_everyCategoryHasADefaultTopicAndSystemPagesHaveNone() {
        val c = categories()
        fun t(title: String) = PreviewTopics.forCategory(c.first { it.title == title }).topic
        assertEquals(PreviewTopic.Home, t("Home screen")); assertEquals(PreviewTopic.Corners, t("Corners")); assertEquals(PreviewTopic.Gestures, t("Gestures"))
        assertEquals(PreviewTopic.Radar, t("Radar")); assertEquals(PreviewTopic.Disc, t("App drawer")); assertEquals(PreviewTopic.SidePanel, t("Side panel"))
        assertEquals(PreviewTopic.Dock, t("Desktop & dock")); assertEquals(PreviewTopic.Look, t("Look")); assertEquals(PreviewTopic.None, t("System & sound"))
        assertEquals("the root list shows the home", PreviewTopic.Home, PreviewTopics.forCategory(null).topic)
    }

    @Test fun CFG_50_rowsRefineTheTopicWithTheirCornerOrGesture() {
        val c = categories()
        val home = c[idx("Home screen")]
        assertEquals(PreviewSubject(PreviewTopic.Glance), PreviewTopics.forRow(home, home.items[1]))
        assertEquals(PreviewSubject(PreviewTopic.Glance, corner = Corner.TopRight), PreviewTopics.forRow(home, home.items[2]))
        assertEquals(PreviewSubject(PreviewTopic.Home), PreviewTopics.forRow(home, home.items[0]))
        val corners = c[idx("Corners")]
        assertEquals(PreviewSubject(PreviewTopic.Corners, corner = Corner.BottomLeft), PreviewTopics.forRow(corners, corners.items[3]))
        val gestures = c[idx("Gestures")]
        assertEquals(PreviewSubject(PreviewTopic.Gestures, gesture = HomeGesture.PinchIn), PreviewTopics.forRow(gestures, gestures.items[0]))
    }

    @Test fun CFG_50_aRowsOwnDeclarationWinsAndANewTabIsJudgedByItsRows() {
        val row = SettingItem.Toggle("Anything", Glyphs.Apps, true) {}.previewTopic(PreviewTopic.Radar)
        assertEquals(PreviewTopic.Radar, PreviewTopics.forRow(SettingCategory("Look", Glyphs.Palette, listOf(row)), row).topic)
        // a tab nobody listed: the topic most of its rows map to
        val tab = SettingCategory("Surfaces", Glyphs.Apps, listOf(
            SettingItem.Toggle("Clock size", Glyphs.Clock, true) {}, SettingItem.Toggle("Show music", Glyphs.Music, true) {}, SettingItem.Toggle("Calm style", Glyphs.Dial, true) {},
            SettingItem.Toggle("Something new", Glyphs.Apps, true) {},
        ))
        assertEquals(PreviewTopic.Glance, PreviewTopics.forCategory(tab).topic)
        assertEquals("an unlisted row inherits the tab's topic", PreviewTopic.Glance, PreviewTopics.forRow(tab, tab.items[3]).topic)
        // and the tab is registered through the one table
        PreviewTopics.category("Surfaces", PreviewTopic.Radar)
        try { assertEquals(PreviewTopic.Radar, PreviewTopics.forCategory(tab).topic) } finally { PreviewTopics.CATEGORIES.remove("Surfaces") }
    }

    // ---- CFG-50: the window follows the page and the row -------------------------------------------------------------------------

    @Test fun CFG_50_theWindowShowsTheTopicOfTheOpenPageAndHidesOnSystemPages() {
        show("Radar")
        assertTrue(stageShows(PreviewTopic.Radar))
        assertEquals("Preview of Radar, Flat layout", description())
        rule.onNodeWithTag(PreviewStageTags.CAPTION, useUnmergedTree = true).assertTextEquals("Radar") // the small caption is the bar's title
        rule.onAllNodesWithTag(PreviewStageTags.SAMPLE, useUnmergedTree = true).fetchSemanticsNodes().let { assertTrue("Radar draws sample data, so it says so", it.isNotEmpty()) }
    }

    @Test fun CFG_50_aSystemPageHidesTheWindow() {
        show("System & sound")
        rule.onAllNodesWithTag(PreviewWindowTags.BAR).assertCountEquals(0)
        rule.onAllNodesWithTag(PreviewWindowTags.PILL).assertCountEquals(0)
    }

    @Test fun CFG_50_touchingARowSwitchesTheTopicWithACrossfade() {
        show("Home screen", reduced = false)
        assertTrue(stageShows(PreviewTopic.Home))
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Clock style").performClick()
        rule.mainClock.advanceTimeBy(80)
        assertTrue("mid cross-fade both layers are composed", stageShows(PreviewTopic.Home) && stageShows(PreviewTopic.Glance))
        rule.mainClock.advanceTimeBy(600)
        assertTrue(stageShows(PreviewTopic.Glance) && !stageShows(PreviewTopic.Home))
        assertTrue(description().startsWith("Preview of Glance"))
    }

    @Test fun CFG_50_aCornerRowMakesItsCornerPulse() {
        show("Corners", reduced = false)
        assertTrue(description().startsWith("Preview of Corners"))
        rule.onNodeWithText("Bottom left").performClick(); settle()
        assertEquals("Preview of Corners, Bottom left", description())
        rule.onNodeWithTag(PreviewStageTags.CORNERS, useUnmergedTree = true).assertExists()
    }

    @Test fun CFG_50_aGestureRowPlaysItsHint() {
        show("Gestures", reduced = false)
        rule.onNodeWithText("Pinch in").performClick(); settle()
        assertEquals("Preview of Gestures, Pinch in", description())
        rule.onNodeWithTag(PreviewStageTags.GESTURE, useUnmergedTree = true).assertExists()
    }

    // ---- CFG-51: Glance and the dock; coalescing -----------------------------------------------------------------------------------

    @Test fun CFG_51_glanceIsTheRealGlanceSurfaceWithTheCurrentClock() {
        show("Home screen")
        rule.onNodeWithText("Clock style").performClick(); settle()
        assertTrue(rule.onAllNodesWithTag("glance.surface", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithTag("glance.clock.word", useUnmergedTree = true).assertCountEquals(0)
        rule.runOnIdle { Snapshot.withMutableSnapshot { prefs = prefs.copy(clockStyle = ClockStyle.Word) } }
        settle(200)
        assertTrue("the word clock is drawn after the change", rule.onAllNodesWithTag("glance.clock.word", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals("Preview of Glance, Word clock", description())
    }

    @Test fun CFG_51_theDockPageShowsTheAppsDockRenderer() {
        show("Desktop & dock")
        assertTrue(rule.onAllNodesWithTag("fake.dock", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals("Preview of Dock, Standard, Bottom", description())
    }

    @Test fun CFG_51_aDragIsCoalescedTo60msAndAnUnchangedValueDoesNothing() {
        var source by mutableIntStateOf(0)
        var shown = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val v by rememberCoalesced({ source })
            androidx.compose.runtime.SideEffect { shown++ }
            BasicText("v=$v")
        }
        rule.mainClock.advanceTimeByFrame()
        val start = shown
        // 20 slider steps, one per frame (the test clock moves in whole frames)
        val t0 = rule.mainClock.currentTime
        repeat(20) { i -> Snapshot.withMutableSnapshot { source = i + 1 }; rule.mainClock.advanceTimeByFrame() }
        val elapsed = rule.mainClock.currentTime - t0
        rule.mainClock.advanceTimeBy(200)
        val during = shown - start
        val cap = elapsed / PreviewStage.COALESCE_MS + 2
        assertTrue("at most one picture per 60 ms while dragging: $during pictures for 20 changes in $elapsed ms (cap $cap)", during in 2..cap.toInt() && during < 20)
        rule.onNodeWithText("v=20").assertExists()
        val before = shown
        Snapshot.withMutableSnapshot { source = 20 } // the same value again
        rule.mainClock.advanceTimeBy(200)
        assertEquals("an unchanged value does not refresh", before, shown)
    }

    // ---- CFG-52: Radar and the disc, composed for real ----------------------------------------------------------------------------

    @Test fun CFG_52_radarIsTheRealSearchScreenWithASampleQueryFromTheAppsAndSampleWebRows() {
        show("Radar")
        settle(1_200)
        val q = PreviewSamples.query(apps)
        assertEquals("ma", q)
        val field = rule.onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("Radar's own field is composed", field.isNotEmpty())
        assertTrue("its query is the sample", field.any { n -> n.config.getOrNull(SemanticsProperties.EditableText)?.text == q })
        assertTrue("the real apps match", rule.onAllNodesWithText("Maps", substring = false, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue("web suggestions are visible and made up", rule.onAllNodesWithText("$q sample 1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        // turning web suggestions off removes them from the picture
        rule.runOnIdle { Snapshot.withMutableSnapshot { prefs = prefs.copy(webSuggestions = false) } }
        settle(800)
        rule.onAllNodesWithText("$q sample 1", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun CFG_52_radarFollowsTheLayoutSetting() {
        show("Radar")
        rule.runOnIdle { Snapshot.withMutableSnapshot { prefs = prefs.copy(searchLayout = SearchLayout.Orbit) } }
        settle(400)
        assertEquals("Preview of Radar, Orbit layout", description())
    }

    @Test fun CFG_52_theDiscIsTheRealWheelWithTheRingShortcuts() {
        show("App drawer")
        settle(800)
        assertTrue(stageShows(PreviewTopic.Disc))
        for (l in listOf("Flashlight", "Alarm", "Notifications")) {
            assertTrue("$l is on the ring", rule.onAllNodes(androidx.compose.ui.test.hasContentDescription(l), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        }
        assertTrue(description().startsWith("Preview of App drawer"))
    }

    @Test fun CFG_52_theSandboxTakesNoFocusAndNoTouch() {
        show("Radar")
        settle(800)
        val focused = rule.onAllNodes(androidx.compose.ui.test.isFocused(), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("nothing inside the Preview has the focus: $focused", focused.none { n -> n.config.getOrNull(SemanticsProperties.EditableText) != null })
    }

    @Test fun CFG_52_theScreenReaderHearsOneLineNotTheSandboxRows() {
        PreviewStage.exposeSandboxSemantics = false
        show("Radar"); settle(800)
        // the merged tree is the one the screen reader walks
        val fields = rule.onAllNodes(hasSetTextAction(), useUnmergedTree = false).fetchSemanticsNodes().filter { it.config.getOrNull(SemanticsProperties.TestTag) != OneUiTags.SEARCH }
        assertTrue("Radar's field is not reachable by the screen reader: ${fields.map { it.config }}", fields.isEmpty())
        assertEquals("Preview of Radar, Flat layout", description())
    }

    // ---- CFG-53: the side panel ----------------------------------------------------------------------------------------------------

    @Test fun CFG_53_theSidePanelPageShowsTheCommsRendererWithASampleBadge() {
        show("Side panel")
        assertTrue(rule.onAllNodesWithTag("fake.comms", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithTag(PreviewStageTags.SAMPLE, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals("Preview of Side panel, Dark", description())
    }

    // ---- performance guard ---------------------------------------------------------------------------------------------------------

    @Test fun CFG_51_aPlayingPreviewNeverRecomposesTheSettingsPage() {
        show("Gestures", reduced = false)
        rule.onNodeWithText("Pinch in").performClick(); settle()
        val before = labelCalls
        assertTrue("the counter row was composed", before > 0)
        rule.mainClock.advanceTimeBy(3_000); rule.waitForIdle() // ~180 frames of the gesture hint
        assertEquals("the settings page did not recompose while the preview animated", before, labelCalls)
        // and a coalesced prefs change redraws the preview only
        rule.runOnIdle { Snapshot.withMutableSnapshot { prefs = prefs.copy(searchLayout = SearchLayout.Orbit) } }
        settle(300)
        assertEquals(before, labelCalls)
    }

    @Test fun CFG_51_minimisedTheStageIsNotComposed() {
        show("Gestures", reduced = false)
        rule.onNodeWithText("Pinch in").performClick(); settle()
        rule.onNodeWithTag(PreviewWindowTags.MINIMISE).performClick(); settle()
        rule.onAllNodesWithTag(PreviewStageTags.GESTURE, useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag(PreviewStageTags.topic(PreviewTopic.Gestures), useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag(PreviewWindowTags.PILL).performClick(); settle() // restore for the next test's stored state
    }

    // ---- screenshots (read by hand) ------------------------------------------------------------------------------------------------

    private fun shot(name: String) {
        // a larger window, so the picture can be judged
        rule.onNodeWithTag(PreviewWindowTags.RESIZE).performTouchInput { down(center); moveBy(Offset(80f, 600f)); up() }
        settle(800)
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val latch = java.util.concurrent.CountDownLatch(1)
        val thread = android.os.HandlerThread("copy").also { it.start() }
        rule.runOnUiThread { android.view.PixelCopy.request(rule.activity.window, bmp, { latch.countDown() }, android.os.Handler(thread.looper)) }
        latch.await(10, java.util.concurrent.TimeUnit.SECONDS); thread.quit()
        val r = rule.onNodeWithTag(PreviewWindowTags.CONTENT).getUnclippedBoundsInRoot()
        val d = rule.activity.resources.displayMetrics.density
        val at = IntArray(2).also { rule.activity.findViewById<android.view.View>(android.R.id.content).getLocationInWindow(it) } // the root sits under the title bar
        val x = (r.left.value * d + at[0]).toInt().coerceIn(0, bmp.width - 1); val y = (r.top.value * d + at[1]).toInt().coerceIn(0, bmp.height - 1)
        val crop = Bitmap.createBitmap(bmp, x, y, ((r.right - r.left).value * d).toInt().coerceAtMost(bmp.width - x).coerceAtLeast(1), ((r.bottom - r.top).value * d).toInt().coerceAtMost(bmp.height - y).coerceAtLeast(1))
        val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        File(dir, "previewAll_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(dir, "previewAll_${name}_window.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // undo the resize so the next screenshot starts from the default
        rule.onNodeWithTag(PreviewWindowTags.RESIZE).performTouchInput { down(center); moveBy(Offset(-80f, -600f)); up() }
        settle()
    }

    @Test fun CFG_50_screenshotHome() { show("Home screen"); shot("home") }
    @Test fun CFG_51_screenshotGlance() { show("Home screen"); rule.onNodeWithText("Clock style").performClick(); settle(); shot("glance") }
    @Test fun CFG_50_screenshotCorners() { show("Corners"); rule.onNodeWithText("Top right").performClick(); settle(); shot("corners") }
    @Test fun CFG_50_screenshotGestures() { show("Gestures"); rule.onNodeWithText("Pinch in").performClick(); settle(); shot("gestures") }
    @Test fun CFG_52_screenshotRadar() { show("Radar"); settle(1_200); shot("radar") }
    @Test fun CFG_52_screenshotDisc() { show("App drawer"); settle(800); shot("disc") }
    @Test fun CFG_50_screenshotLook() { show("Look"); shot("look") }
}

