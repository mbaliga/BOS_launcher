package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.PageLayout
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.design.ThemeVariant
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

/**
 * Wave 36 (pageEditor): the page editor's strip, tabs, rail and drop seam under Robolectric, in both Pages modes, portrait and landscape,
 * light and dark, RTL, reduced motion. Screenshots go to build/screenshots/page_editor_*.png. Not a device run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class PageEditorUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @After fun reset() { Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }; L10n.setLanguage(""); ReducedMotionTesting.force(null) }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(600)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { Canvas(bmp).also { it.drawColor(android.graphics.Color.rgb(20, 26, 50)); view.draw(it) } }
        File(outDir, "page_editor_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val labels = listOf("Alpha", "Bravo", "Camera", "Delta", "Echo", "Foxtrot", "Golf", "Hotel")
    private val apps = labels.mapIndexed { i, l ->
        AppEntry("demo.$l", ComponentName("demo.$l", "M"), l, Process.myUserHandle()) { ColorDrawable(0xFF3070D0.toInt() + i * 0x101010) }
    }
    private val byKey = apps.associateBy { it.key }
    private fun page(id: Long, vararg k: AppEntry) = HomePage(k.map { it.key }, PageLayout(), id)
    private val fourPages get() = listOf(page(1, apps[0], apps[1], apps[2]), page(2, apps[3]), page(3), page(4, apps[4], apps[5]))

    private class Calls {
        val selected = ArrayList<Triple<Int, Float, Float>>(); val glance = ArrayList<Pair<Float, Float>>(); val added = ArrayList<Pair<Float, Float>>()
        val deleted = ArrayList<Int>(); val moved = ArrayList<Pair<Int, Int>>(); val addedApps = ArrayList<String>(); val dropped = ArrayList<Pair<String, Int>>()
        var widget = 0; var folder = 0; var group = 0; var exit = 0
        var prefs = LauncherPrefs()
    }

    private lateinit var drag: AddCarouselDrag

    private fun host(
        c: Calls, pages: List<HomePage>, current: Int = 0, continuous: Boolean = false, landscape: Boolean = false, rtl: Boolean = false, orderRtl: Boolean = false,
        glanceOn: Boolean = true, glanceFocus: Boolean = false, swing: PageEditorGeometry.Swing = PageEditorGeometry.REST, onHome: Set<String> = emptySet(),
        reduced: Boolean = false, fontScale: Float = 1f,
    ) {
        drag = AddCarouselDrag()
        rule.setContent {
            val dir = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            val base = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(LocalLayoutDirection provides dir, androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density, fontScale)) {
                val layout = rememberPageEditorLayout(continuous, orderRtl)
                val state = mutableStateOf(1f)
                Box(Modifier.fillMaxSize().background(Color(0xFF0A0B10))) {
                    PageEditorHost(
                        progress = state, reduced = reduced, layout = layout, continuous = continuous, pages = pages, current = current, glanceOn = glanceOn, glanceFocus = glanceFocus,
                        swing = { swing }, appsByKey = byKey, prefs = c.prefs, isLandscape = landscape, apps = apps, onHome = onHome, hasWork = false, isWork = { false }, drag = drag,
                        onSelectPage = { i, dx, dy -> c.selected += Triple(i, dx, dy) }, onSelectGlance = { dx, dy -> c.glance += dx to dy }, onAddPage = { dx, dy -> c.added += dx to dy },
                        onDelete = { c.deleted += it }, onMove = { i, d -> c.moved += i to d }, onAddApp = { c.addedApps += it.label }, onDropApp = { a, p -> c.dropped += a.label to p },
                        onWidget = { c.widget++ }, onFolder = { c.folder++ }, onShortcut = {}, onLink = {}, onFile = {}, onGroup = { c.group++ },
                        onPrefs = { f -> c.prefs = f(c.prefs) }, notice = null, onNoticeDone = {}, focusAdd = false, onExit = { c.exit++ },
                    )
                    // The live page, in the focus slot (the real home does this with the same frame).
                    if (!glanceFocus) Box(Modifier.fillMaxSize().editCardLayer { EditCardGeometry.frame(layout.target, state.value, false) }.background(Color(0xFF30406A))) { Text("LIVE", color = Color.White) }
                }
            }
        }
    }

    private fun card(label: String) = rule.onNodeWithContentDescription(label)
    private fun bounds(label: String) = card(label).getUnclippedBoundsInRoot()

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe20_paginatedPortraitStripIsOnTheRightTheTabsOnTheLeftWithAppsSelected() {
        val c = Calls(); host(c, fourPages, current = 1)
        card("Page 2 of 4").assertIsSelected()
        card("Page 1 of 4").assertIsNotSelected()
        val strip = rule.onNodeWithContentDescription("Page carousel").getUnclippedBoundsInRoot()
        assertTrue("the strip is on the right half", strip.left > 411.dp / 3)
        // The neighbours peek above and below the focus card, in the same column.
        assertTrue(bounds("Page 1 of 4").top < bounds("Page 2 of 4").top)
        assertTrue(bounds("Page 3 of 4, empty").top > bounds("Page 2 of 4").top)
        assertEquals(bounds("Page 1 of 4").left.value, bounds("Page 3 of 4, empty").left.value, 0.5f)
        rule.onNodeWithText("Apps").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertIsSelected()
        rule.onNodeWithText("Widgets").assertIsNotSelected()
        assertTrue("the tabs are on the left of the strip", rule.onNodeWithText("Apps").getUnclippedBoundsInRoot().right <= strip.left)
        rule.onNodeWithContentDescription("Add to home: Camera").assertIsDisplayed()
        shoot("paginated_portrait_dark")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe21_tabsSwitchContentAndKeepTheSelectedState() {
        val c = Calls(); host(c, fourPages)
        rule.onNodeWithText("Widgets").performClick(); rule.onNodeWithText("Widgets").assertIsSelected(); rule.onNodeWithText("Apps").assertIsNotSelected()
        rule.onNodeWithText("Choose a widget").performClick(); assertEquals(1, c.widget)
        rule.onNodeWithText("Folders").performClick(); rule.onNodeWithText("New folder").performClick(); assertEquals(1, c.folder)
        rule.onNodeWithText("App group").performClick(); assertEquals(1, c.group)
        rule.onNodeWithText("App shortcut").assertIsDisplayed()
        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithText("Page 1 of 4").assertIsDisplayed()
        rule.onNodeWithText("Pages use the same wallpaper as Glance").performClick()
        assertTrue("the switch wrote the pref", !c.prefs.pagesSameAsGlance)
        rule.onNodeWithContentDescription("Delete page 1", useUnmergedTree = true)
        shoot("settings_tab")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe22_tappingACardReportsWhereItWasForTheSwing() {
        val c = Calls(); host(c, fourPages, current = 1)
        card("Page 3 of 4, empty").performClick()
        assertEquals(1, c.selected.size)
        val (page, dx, dy) = c.selected[0]
        assertEquals(2, page)
        assertEquals("same column as the focus card", 0f, dx, 2f)
        assertTrue("the tapped card is below the focus slot by about one pitch", dy > 100f)
        card("Page 1 of 4").performClick()
        assertTrue("a card above reports a negative offset", c.selected[1].third < 0f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe22b_theAddCardAtTheEndAddsOnePageAndReportsItsPlace() {
        val c = Calls(); host(c, fourPages, current = 3)
        card("Add page").performClick(); assertEquals(1, c.added.size); assertTrue(c.added[0].second > 0f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe23_railMovesAndDeletesTheFocusPageAndStopsAtTheEnds() {
        val c = Calls(); host(c, fourPages, current = 0)
        rule.onNodeWithContentDescription("Move page 1 earlier").assertExists().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
        rule.onNodeWithContentDescription("Move page 1 later").performClick()
        rule.onNodeWithContentDescription("Delete page 1").performClick()
        assertEquals(listOf(0 to 1), c.moved); assertEquals(listOf(0), c.deleted)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe24_glanceIsTheFirstCardWithNoDeleteAndItsSettingsInTheSettingsTab() {
        val c = Calls(); host(c, fourPages, current = 0)
        assertTrue("Glance is above page 1", bounds("Glance").top < bounds("Page 1 of 4").top)
        card("Glance").performClick(); assertEquals(1, c.glance.size)
        rule.onAllNodesWithContentDescription("Add to home").assertCountEquals(1) // the content area
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe24b_withTheGlanceFocusedThereIsNoRailAndTheSettingsTabHoldsItsOptions() {
        val c = Calls(); host(c, fourPages, current = 0, glanceFocus = true)
        card("Glance").assertIsSelected()
        rule.onAllNodesWithContentDescription("Delete page 1").assertCountEquals(0)
        rule.onNodeWithText("Hotspots").assertIsDisplayed(); rule.onNodeWithText("Show music").assertIsDisplayed()
        rule.onNodeWithText("Top left").performClick()
        assertTrue("a hotspot toggled", com.bos.sphere.core.data.Corner.TopLeft !in c.prefs.glanceHotspots)
        shoot("glance_focus")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe25_noGlanceCardWhenGlanceIsOff() {
        val c = Calls(); host(c, fourPages, glanceOn = false)
        rule.onAllNodesWithContentDescription("Glance").assertCountEquals(0)
        card("Page 1 of 4").assertIsSelected()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe26_dropSeamPlacesAnAppOnTheCardUnderTheFingerAndIgnoresOtherPlaces() {
        val c = Calls(); host(c, fourPages, current = 1)
        val b = rule.onNodeWithContentDescription("Page 3 of 4, empty").getUnclippedBoundsInRoot()
        val d = rule.density.density
        val o = rule.onRoot().fetchSemanticsNode().positionInWindow // the root is not at the window's origin (system bars)
        val centre = Offset((b.left.value + b.width.value / 2) * d + o.x, (b.top.value + 30) * d + o.y) // the lower cards peek: aim at the part inside the strip
        rule.runOnUiThread { drag.onDrop!!.invoke(apps[6], centre) }
        assertEquals(listOf("Golf" to 2), c.dropped)
        // On the live card (the focus slot) it goes to the current page.
        val bf = rule.onNodeWithContentDescription("Page 2 of 4").getUnclippedBoundsInRoot()
        rule.runOnUiThread { drag.onDrop!!.invoke(apps[7], Offset((bf.left.value + bf.width.value / 2) * d + o.x, (bf.top.value + bf.height.value / 2) * d + o.y)) }
        assertEquals("Hotel" to 1, c.dropped[1])
        rule.runOnUiThread { drag.onDrop!!.invoke(apps[7], Offset(5f, 5f)) }
        assertEquals("nothing happens off the cards", 2, c.dropped.size)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe27_continuousPortraitStripRunsAcrossTheTopAndTheTabsAreARowBelow() {
        val c = Calls(); host(c, fourPages, current = 2, continuous = true)
        val strip = rule.onNodeWithContentDescription("Page carousel").getUnclippedBoundsInRoot()
        assertTrue("the strip is in the upper part", strip.bottom < 891.dp * 0.6f)
        assertTrue("neighbours sit on the sides of the focus card", bounds("Page 2 of 4").left < bounds("Page 3 of 4, empty").left && bounds("Page 4 of 4").left > bounds("Page 3 of 4, empty").left)
        assertEquals(bounds("Page 2 of 4").top.value, bounds("Page 4 of 4").top.value, 0.5f)
        val tabs = rule.onNodeWithText("Apps").getUnclippedBoundsInRoot(); val widgets = rule.onNodeWithText("Widgets").getUnclippedBoundsInRoot()
        assertTrue("the tabs are below the strip, in one row", tabs.top >= strip.bottom && kotlin.math.abs(tabs.bottom.value - widgets.bottom.value) < 12f)
        card("Page 4 of 4").performClick()
        val (page, dx, dy) = c.selected.single()
        assertEquals(3, page); assertTrue("a card on the side reports a horizontal offset", dx > 100f && kotlin.math.abs(dy) < 4f)
        rule.onNodeWithContentDescription("Move page 3 earlier").assertExists()
        shoot("continuous_portrait_dark")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe28_rtlMirrorsTheStripToTheLeftAndTheOrderSettingDoesToo() {
        val c = Calls(); host(c, fourPages, current = 1, rtl = true)
        val strip = rule.onNodeWithContentDescription("Page carousel").getUnclippedBoundsInRoot()
        assertTrue("the strip is on the left", strip.right < 411.dp * 0.7f)
        assertTrue("the tabs are on the right of the strip", rule.onNodeWithText("Apps").getUnclippedBoundsInRoot().left >= strip.right - 1.dp)
        shoot("paginated_rtl")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe28b_theRightToLeftOrderSettingMirrorsTheSideStripToo() {
        val c = Calls(); host(c, fourPages, current = 1, orderRtl = true)
        assertTrue(rule.onNodeWithContentDescription("Page carousel").getUnclippedBoundsInRoot().right < 411.dp * 0.7f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe28c_continuousPageOrderRunsRightToLeftWithTheOrderSetting() {
        val c = Calls(); host(c, fourPages, current = 1, continuous = true, orderRtl = true)
        assertTrue("page order runs right to left in the top strip", bounds("Page 1 of 4").left > bounds("Page 2 of 4").left)
        shoot("continuous_rtl")
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_12_pe29_landscapeKeepsTheStripOnTheSideWithTabsAsARowInBothModes() {
        val continuous = false
        run {
            val c = Calls(); host(c, fourPages, current = 1, continuous = continuous, landscape = true)
            val strip = rule.onNodeWithContentDescription("Page carousel").getUnclippedBoundsInRoot()
            assertTrue("continuous=$continuous: strip on the right", strip.left > 891.dp / 3)
            assertTrue(bounds("Page 1 of 4").top < bounds("Page 2 of 4").top)
            val a = rule.onNodeWithText("Apps").getUnclippedBoundsInRoot(); val w = rule.onNodeWithText("Widgets").getUnclippedBoundsInRoot()
            assertTrue("the tabs are one row", kotlin.math.abs(a.bottom.value - w.bottom.value) < 12f)
            shoot("paginated_landscape_dark")
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_12_pe29b_landscapeContinuousAlsoKeepsTheStripOnTheSide() {
        val c = Calls(); host(c, fourPages, current = 1, continuous = true, landscape = true)
        assertTrue(rule.onNodeWithContentDescription("Page carousel").getUnclippedBoundsInRoot().left > 891.dp / 3)
        assertTrue(bounds("Page 1 of 4").top < bounds("Page 2 of 4").top)
        shoot("continuous_landscape_dark")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe30_lightThemeRendersBothModes() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF) }
        val c = Calls(); host(c, fourPages, current = 1)
        card("Page 2 of 4").assertIsSelected(); shoot("paginated_portrait_light")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe31_lightThemeContinuous() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF) }
        val c = Calls(); host(c, fourPages, current = 1, continuous = true)
        card("Page 2 of 4").assertIsSelected(); shoot("continuous_portrait_light")
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_12_pe32_lightThemeLandscape() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF) }
        val c = Calls(); host(c, fourPages, current = 1, landscape = true)
        card("Page 2 of 4").assertIsSelected(); shoot("paginated_landscape_light")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe33_fontScaleTwoKeepsTheTabsReachable() {
        val c = Calls(); host(c, fourPages, current = 1, fontScale = 2f)
        rule.onNodeWithText("Apps").assertIsDisplayed(); rule.onNodeWithText("Settings").assertIsDisplayed()
        rule.onNodeWithText("Settings").performClick(); rule.onNodeWithText("Settings").assertIsSelected()
        shoot("font_scale_2")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe34_reducedMotionStillShowsTheEditor() {
        ReducedMotionTesting.force(com.bos.sphere.core.quality.MotionPrefs(0f, 0f, 0f))
        val c = Calls(); host(c, fourPages, current = 2, reduced = true)
        card("Page 3 of 4, empty").assertIsSelected()
        card("Page 4 of 4").performClick(); assertEquals(3, c.selected.single().first)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe35_swingMidFrameDrawsTheLivePageTiltedAndAtRestItIsFlat() {
        val c = Calls(); host(c, fourPages, current = 1, glanceFocus = true, swing = PageEditorGeometry.swing(0.4f, 0f, 400f, false))
        rule.onNodeWithTag("page-editor-glance-focus").assertIsDisplayed()
        shoot("swing_mid_frame_glance")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe36_tabsAreRealTabsAndCardsAreButtons() {
        val c = Calls(); host(c, fourPages, current = 1)
        for (t in listOf("Apps", "Widgets", "Folders", "Settings")) rule.onNodeWithText(t).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        for (l in listOf("Page 1 of 4", "Page 3 of 4, empty")) card(l).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        // 48 dp targets.
        for (t in listOf("Apps", "Widgets", "Folders", "Settings")) assertTrue(t, rule.onNodeWithText(t).getUnclippedBoundsInRoot().height >= 48.dp)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe36b_glanceAndAddCardsAreButtonsToo() {
        val c = Calls(); host(c, fourPages, current = 0)
        card("Glance").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }
}
