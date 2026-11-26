package com.bos.sphere

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.CornerSlot
import com.bos.sphere.core.data.DefaultCorners
import com.bos.sphere.core.data.GlanceHotspots
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.MediaNow
import com.bos.sphere.core.data.MusicLineWidth
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.core.quality.MotionPrefs
import com.bos.sphere.feature.grid.HotspotsEditorTags
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.feature.grid.SettingsSpace
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
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
 * Wave 59 (glanceTop), CFG-76 / CFG-77: Settings. The Hotspots editor is the first block of Home > Corners (the four corner rows, the Hotspots presets
 * and the four hotspot switches are gone), a tap on a corner writes the stored set, Edit opens the corner editor for that corner and set, and "Music line
 * width" is a pair of drawn cards in Home > Glance.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class GlanceTopW59SettingsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val stores get() = LauncherStores.get(AppSmokeHarness.app)
    private val edits = ArrayList<Pair<Corner, Boolean>>()

    @Before fun fresh() { AppSmokeHarness.resetProcess() }
    @After fun done() { AppSmokeHarness.resetProcess() }

    private fun model(p: LauncherPrefs) = SettingsModel(
        prefs = p, updatePrefs = { f -> stores.prefs.update(f) }, pageIndex = 0, pages = listOf(HomePage(keys = emptyList(), id = 1L)),
        setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = DefaultCorners, gestures = emptyMap(), setGesture = { _, _ -> },
        about = listOf("Version" to "test"), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
        pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false, editCorner = { edits += it to false }, editGesture = {},
        system = SystemLinks({}, {}, {}, {}), portraitCorners = DefaultCorners, landscapeCorners = DefaultCorners,
        editCornerIn = { c, l -> edits += c to l },
    )

    private fun home() = buildSettingsCategories(model(LauncherPrefs())).first { com.bos.sphere.feature.grid.SettingsTabs.key(it.title) == com.bos.sphere.feature.grid.SettingsTabs.HOME }

    @Test fun CFG_77_theHotspotsEditorIsTheFirstBlockOfCornersAndTheOldRowsAreGone() {
        val c = home()
        val titles = c.items.map { it.title }
        val i = titles.indexOf("Hotspots")
        assertTrue("the editor is on the Home tab", i >= 0)
        assertEquals("it opens the Corners section", "Corners", c.headers[i])
        assertEquals("Corner style", titles[i + 1])
        for (gone in Corner.entries.map { "${it.label} hotspot" } + Corner.entries.map { it.label }) assertFalse("$gone is gone", gone in titles)
        assertEquals("the Hotspots presets are gone: one editor row", 1, titles.count { it == "Hotspots" })
        // Music line width sits under Show music in Glance, as drawn cards
        val mw = titles.indexOf("Music line width")
        assertEquals("Show music", titles[mw - 1])
        val row = c.items[mw] as SettingItem.Choice<*>
        assertEquals(MusicLineWidth.entries, row.options); assertTrue("drawn cards", row.preview != null)
    }

    @Test fun CFG_76_musicLineWidthPersistsAndIsDimmedWithoutTheMusicLine() {
        @Suppress("UNCHECKED_CAST") val row = home().items.first { it.title == "Music line width" } as SettingItem.Choice<MusicLineWidth>
        assertEquals(MusicLineWidth.FitAroundCorners, row.selected)
        row.onSelect(MusicLineWidth.FullWidth)
        assertEquals(MusicLineWidth.FullWidth, stores.prefs.prefs.value.musicLineWidth)
        assertEquals("FullWidth", AppSmokeHarness.app.getSharedPreferences("launcher_prefs", 0).getString("musicLineWidth", null))
        val off = buildSettingsCategories(model(LauncherPrefs(showMusic = false))).flatMap { it.items }.first { it.title == "Music line width" }
        assertEquals("Show music is off", off.meta.disabledReason)
    }

    @Test fun CFG_77_inSettingsATapTogglesAndPersistsAndEditOpensTheCornerEditor() {
        var cats by mutableStateOf(buildSettingsCategories(model(stores.prefs.prefs.value)))
        val home = cats.indexOfFirst { com.bos.sphere.feature.grid.SettingsTabs.key(it.title) == com.bos.sphere.feature.grid.SettingsTabs.HOME }
        rule.setContent { SphereTheme { SettingsSpace(visible = true, categories = cats, initialCategory = home, sounds = null, onClose = {}) } }
        rule.mainClock.advanceTimeBy(800)
        SettingsTestNav.goTo(rule, "Home", "Hotspots") // through Settings' own search: it scrolls the Home list to the editor
        rule.onNodeWithTag(HotspotsEditorTags.corner(Corner.TopLeft)).performClick(); rule.mainClock.advanceTimeBy(300)
        assertEquals(GlanceHotspots.ALL - Corner.TopLeft, stores.prefs.prefs.value.glanceHotspots)
        assertEquals("TopRight,BottomRight,BottomLeft", AppSmokeHarness.app.getSharedPreferences("launcher_prefs", 0).getString("glanceHotspots", null))
        rule.runOnIdle { cats = buildSettingsCategories(model(stores.prefs.prefs.value)) }; rule.mainClock.advanceTimeBy(300)
        // the Edit chip sits below the fold of this window: its click action, as a screen reader would
        rule.onNodeWithTag(HotspotsEditorTags.EDIT).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); rule.mainClock.advanceTimeBy(300)
        assertEquals(listOf(Corner.TopLeft to false), edits)
        shoot("settings_corners")
    }

    private fun shoot(name: String) {
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { Canvas(bmp).also { it.drawColor(android.graphics.Color.BLACK); view.draw(it) } }
        File(File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }, "w59_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/**
 * Wave 59 (glanceTop), CFG-75 / CFG-76 / CFG-77 in the real app: the Home sheet's Hotspots tile opens the editor as a full page (a tap writes the set, a
 * long press opens the corner editor), and the music line on the home ignores a top corner that is on but holds nothing; Full width always runs it edge to edge.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class GlanceTopW59SmokeTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun freshProcess() { AppSmokeHarness.resetProcess() }
    @After fun closeApp() {
        if (scenario != null) { rule.waitForIdle(); assertFalse(Snapshot.current.hasPendingChanges()) }
        scenario?.close(); scenario = null
        MediaNow.publishForTest(null)
        ReducedMotionTesting.force(null)
        AppSmokeHarness.resetProcess()
    }

    private fun start(edit: (LauncherPrefs) -> LauncherPrefs = { it }) {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        AppSmokeHarness.configureFresh(edit)
        scenario = AppSmokeHarness.launch(); rule.waitForIdle()
    }
    private fun settle(ms: Long = 1200) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); Snapshot.sendApplyNotifications(); rule.mainClock.advanceTimeBy(ms); org.robolectric.shadows.ShadowLooper.idleMainLooper(); rule.mainClock.advanceTimeBy(ms) }
    private val stores get() = LauncherStores.get(AppSmokeHarness.app)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun CFG_77_theHomeSheetTileOpensTheEditorAsAPageThatTogglesAndEdits() {
        start(); settle()
        rule.onRoot().performTouchInput { longClick(Offset(width * 0.5f, height * 0.75f)) }; settle(300)
        assertTrue("the quick sheet is open", has("quick.sheet"))
        rule.onNodeWithTag("quick.hotspots").performScrollTo().performClick(); settle(400)
        assertTrue("the Hotspots page is open", has("hotspots.page")); assertFalse("the sheet closed", has("quick.sheet"))
        rule.onNodeWithTag(HotspotsEditorTags.corner(Corner.TopRight)).performClick(); settle(300)
        assertEquals(GlanceHotspots.ALL - Corner.TopRight, stores.prefs.prefs.value.glanceHotspots)
        rule.onNodeWithTag(HotspotsEditorTags.corner(Corner.BottomLeft)).performTouchInput { longClick() }; settle(400)
        assertTrue("the corner editor for that corner opened", rule.onAllNodesWithText(L10n.tr("%s corner", L10n.tr(Corner.BottomLeft.label))).fetchSemanticsNodes().isNotEmpty())
        // the miniature adds no hotspots to the home's focus graph
        assertEquals(3, com.bos.sphere.feature.grid.LauncherInput.focus.nodes().count { it.id.startsWith("corner.") })
    }

    private val title = MediaInfo("Lose Yourself to Dance (Drumless Edition) (feat. Pharrell Williams) and a long tail", "Daft Punk", "pkg.music", playing = true, canPrev = true, canNext = true)
    private fun lineWidthDp(): Float {
        val n = rule.onNodeWithTag("glance.title.static", useUnmergedTree = true).fetchSemanticsNode()
        return n.boundsInRoot.width / AppSmokeHarness.app.resources.displayMetrics.density
    }

    @Test fun CFG_75_onTheHomeAnEnabledButEmptyTopCornerLeavesTheLineFullWidth() {
        ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f)) // the ellipsized line fills its room; no marquee
        start(); settle()
        MediaNow.publishForTest(title); settle()
        val fit = lineWidthDp()
        assertTrue("the factory top corners draw, so the line keeps clear ($fit dp)", fit <= 280f - 48f + 1f)
        stores.shortcuts.setCorner(Corner.TopLeft, CornerSlot.Empty); stores.shortcuts.setCorner(Corner.TopRight, CornerSlot.Empty); settle()
        assertEquals("both top corners are still on", GlanceHotspots.ALL, stores.prefs.prefs.value.glanceHotspots)
        val wide = lineWidthDp()
        assertTrue("nothing drawn at the top: edge to edge ($wide dp)", wide > 360f - 72f - 2f)
    }

    @Test fun CFG_76_fullWidthRunsTheLineEdgeToEdgeUnderDrawnCorners() {
        ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f))
        start { it.copy(musicLineWidth = MusicLineWidth.FullWidth) }; settle()
        MediaNow.publishForTest(title); settle()
        assertEquals(CornerSlot.Single(CornerItem.Action(CornerActionId.Camera)), stores.shortcuts.corners.value[Corner.TopLeft])
        assertTrue("Full width: edge to edge with drawn corners (${lineWidthDp()} dp)", lineWidthDp() > 360f - 72f - 2f)
    }
}
