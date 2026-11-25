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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.NotificationProvider
import com.bos.sphere.core.data.PageLayout
import com.bos.sphere.core.data.PageShape
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.OneUiModel
import com.bos.sphere.feature.grid.PreviewStage
import com.bos.sphere.feature.grid.PreviewStageTags
import com.bos.sphere.feature.grid.PreviewTopic
import com.bos.sphere.feature.grid.PreviewTopics
import com.bos.sphere.feature.grid.PreviewWindowTags
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.feature.grid.SettingsPreviewDock
import com.bos.sphere.feature.grid.SettingsSpace
import com.bos.sphere.feature.grid.captureHome
import com.bos.sphere.feature.grid.captureWallpaper
import com.bos.sphere.feature.grid.rememberHomeCapture
import com.bos.sphere.ui.CommsPreviewSamples
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
import com.bos.sphere.ui.rememberPreviewSurfaces
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
 * Wave 57 (previewAll), CFG-50..CFG-54 on the real settings and the real renderers: every row that changes a visual preference maps to a Preview
 * topic (the parity test, walking the built settings), the side panel is the real Comms column with sample rows and never a real notification,
 * the dock is the real rail with its settings. Screenshots: app/build/screenshots/previewAll_*.png (read by hand).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class PreviewAllW57AppTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun setUp() { L10n.setLanguage("en"); PreviewStage.exposeSandboxSemantics = true }
    @After fun tearDown() { PreviewStage.exposeSandboxSemantics = false; NotificationProvider.publishForTest(emptyList()) }

    private var prefs = LauncherPrefs() // a plain field: rows write it from their callbacks, outside any Compose host
    private val touched = ArrayList<String>()

    private fun model(p: LauncherPrefs) = SettingsModel(
        prefs = p, updatePrefs = { f -> prefs = f(prefs) }, pageIndex = 0,
        pages = (1..2).map { HomePage(keys = emptyList(), id = it.toLong(), layout = PageLayout(shape = PageShape.Grid)) },
        setPageLayout = { _, _ -> touched += "layout" }, addPageAfter = { touched += "addPage" }, deletePage = { touched += "deletePage" }, setClockTile = { _, _ -> touched += "clock" },
        corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> touched += "gesture" },
        about = listOf("Version" to "test"), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
        pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = true, editCorner = { touched += "corner" }, editGesture = {},
        system = SystemLinks({}, {}, {}, {}), lab = emptyList(),
    )

    /**
     * Rows that change a preference yet have nothing to draw in the Preview: they change sounds, behaviour, Settings itself (which is in view), or
     * text only. Each is kept here with its reason; a new row that changes a preference must map to a topic or be added here on purpose.
     */
    private val notVisual = mapOf(
        "Interface sounds" to "sound only", "Volume" to "sound only", "Haptics" to "touch feedback only", "Haptic strength" to "touch feedback only",
        "Restore open screens" to "behaviour after a restart",
        "Settings backdrop" to "changes Settings itself, which is in view", "Backdrop dimming" to "changes Settings itself, which is in view",
    )

    private fun variants() = listOf(
        LauncherPrefs(homeStyle = HomeStyle.Grid, homePages = HomePages.Paginated),
        LauncherPrefs(homeStyle = HomeStyle.Grid, homePages = HomePages.Continuous, hotCornersOn = true, glanceEnabled = true, motionEnabled = true),
        LauncherPrefs(homeStyle = HomeStyle.List),
    )

    @Test fun CFG_54_everySettingThatChangesAVisualPreferenceMapsToAPreviewTopic() {
        val offenders = LinkedHashSet<String>()
        var checked = 0
        for (v in variants()) {
            prefs = v
            val cats = buildSettingsCategories(model(v))
            for (c in cats) for (item in c.items) {
                // every control row counts as changing a preference: calling the setters would also write the stores some rows own (Comms size,
                // pinned, groups), which are process-wide and leak into the tests that run after this one
                if (item !is SettingItem.Toggle && item !is SettingItem.Choice<*> && item !is SettingItem.Slider && item !is SettingItem.Stepper) continue
                checked++
                val topic = PreviewTopics.forRow(c, item).topic
                if (topic == PreviewTopic.None && item.title !in notVisual) offenders += "${OneUiModel.key(c.title) ?: c.title} > ${item.title}"
            }
        }
        assertTrue("walked the settings ($checked control rows)", checked > 60)
        assertTrue("rows that change a preference but show nothing in the Preview (map them in PreviewTopics or allow-list them with a reason): $offenders", offenders.isEmpty())
    }

    @Test fun CFG_54_theAllowListHoldsOnlyRowsThatResolveToNone() {
        val all = variants().flatMap { v -> buildSettingsCategories(model(v)).flatMap { c -> c.items.map { c to it } } }
        for ((c, i) in all) if (i.title in notVisual) assertEquals("${i.title} is allow-listed, so it must not have a topic", PreviewTopic.None, PreviewTopics.forRow(c, i).topic)
    }

    @Test fun CFG_54_everyCategoryOfTheRealSettingsHasATopicAndOnlySystemPagesHaveNone() {
        val cats = buildSettingsCategories(model(LauncherPrefs(homeStyle = HomeStyle.Grid, homePages = HomePages.Paginated)))
        val topics = cats.associate { (OneUiModel.key(it.title) ?: it.title) to PreviewTopics.forCategory(it).topic }
        assertEquals(PreviewTopic.Home, topics["Home screen"]); assertEquals(PreviewTopic.Look, topics["Look"]); assertEquals(PreviewTopic.Corners, topics["Corners"])
        assertEquals(PreviewTopic.Gestures, topics["Gestures"]); assertEquals(PreviewTopic.Disc, topics["App drawer"]); assertEquals(PreviewTopic.Radar, topics["Radar"])
        assertEquals(PreviewTopic.SidePanel, topics["Side panel"]); assertEquals(PreviewTopic.None, topics["System & sound"])
        for ((k, t) in topics) if (k in setOf("System & sound", "Backup", "About", "Lab", "Capabilities", "Hidden apps")) assertEquals(k, PreviewTopic.None, t)
    }

    // ---- the real renderers ----------------------------------------------------------------------------------------------------------

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

    private fun description(): String = rule.onNodeWithTag(PreviewWindowTags.CONTENT).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()

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
        File(dir, "previewAll_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(dir, "previewAll_${name}_window.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun CFG_53_theSidePanelIsTheRealCommsColumnWithSampleRowsNeverARealNotification() {
        // a real notification is in the feed: it must not appear in the Preview (screenshots of Settings must not carry private content)
        NotificationProvider.publishForTest(listOf(HubNotification(id = "x", key = "real:1", source = "Messages", title = "Private Person", summary = "the real secret text", timestamp = System.currentTimeMillis(), packageName = "com.real.chat", appLabel = "Chat")))
        show("Side panel")
        assertTrue("the sample message is shown", rule.onAllNodesWithText(L10n.tr("Sample message"), substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue("never the real one", rule.onAllNodesWithText("the real secret text", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue("never the real one", rule.onAllNodesWithText("Private Person", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodes(androidx.compose.ui.test.hasTestTag(PreviewStageTags.SAMPLE), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals("Preview of Side panel, Dark", description())
        assertTrue(CommsPreviewSamples.items(0L).all { it.key.startsWith(CommsPreviewSamples.KEY_PREFIX) })
        shot("sidepanel")
    }

    @Test fun CFG_51_theDockIsTheRealRailWithItsSettings() {
        show("Desktop & dock")
        assertTrue(rule.onAllNodes(androidx.compose.ui.test.hasTestTag(PreviewStageTags.topic(PreviewTopic.Dock)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue("the rail's sample tiles are the user's apps", rule.onAllNodes(hasContentDescription("Maps", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(description(), description().startsWith("Preview of Dock, "))
        shot("dock")
    }
}
