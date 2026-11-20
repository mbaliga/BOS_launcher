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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.center
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
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
 * Wave 36 (pageEditor), redone in wave 45 (pageEditorLayout): the page editor's strip above the live card, tabs below, the overflow menu, reorder, the Glance and
 * New page cards and the drop seam under Robolectric, in both Pages modes, portrait and landscape, light and dark, RTL, reduced motion. Screenshots go to build/screenshots/page_editor_*.png. Not a device run.
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
        val reordered = ArrayList<Pair<Int, Int>>()
        var prefs = LauncherPrefs()
    }

    private lateinit var drag: AddCarouselDrag
    private var winW = 0f; private var winH = 0f
    private lateinit var layoutNow: PageEditorGeometry.Layout
    private lateinit var inputModes: androidx.compose.ui.input.InputModeManager

    private fun host(
        c: Calls, pages: List<HomePage>, current: Int = 0, continuous: Boolean = false, landscape: Boolean = false, rtl: Boolean = false, orderRtl: Boolean = false,
        glanceOn: Boolean = true, glanceFocus: Boolean = false, swing: PageEditorGeometry.Swing = PageEditorGeometry.REST, onHome: Set<String> = emptySet(),
        reduced: Boolean = false, fontScale: Float = 1f, topBar: (@androidx.compose.runtime.Composable () -> Unit)? = null,
    ) {
        drag = AddCarouselDrag()
        rule.setContent {
            val dir = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            val base = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(LocalLayoutDirection provides dir, androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density, fontScale)) {
              androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
               // The Robolectric window is smaller than the configuration's screen (system bars): size the layout to the window the host really fills, as the app does on a device.
               val cfg0 = androidx.compose.ui.platform.LocalConfiguration.current
               val cfg = remember(maxWidth, maxHeight) { android.content.res.Configuration(cfg0).apply { screenWidthDp = maxWidth.value.toInt(); screenHeightDp = maxHeight.value.toInt() } }
               winW = maxWidth.value; winH = maxHeight.value
               CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides cfg) {
                val layout = rememberPageEditorLayout(continuous, orderRtl)
                layoutNow = layout
                inputModes = androidx.compose.ui.platform.LocalInputModeManager.current
                val state = mutableStateOf(1f)
                Box(Modifier.fillMaxSize().background(Color(0xFF0A0B10))) {
                    PageEditorHost(
                        progress = state, reduced = reduced, layout = layout, continuous = continuous, pages = pages, current = current, glanceOn = glanceOn, glanceFocus = glanceFocus,
                        swing = { swing }, appsByKey = byKey, prefs = c.prefs, isLandscape = landscape, apps = apps, onHome = onHome, hasWork = false, isWork = { false }, drag = drag,
                        onSelectPage = { i, dx, dy -> c.selected += Triple(i, dx, dy) }, onSelectGlance = { dx, dy -> c.glance += dx to dy }, onAddPage = { dx, dy -> c.added += dx to dy },
                        onDelete = { c.deleted += it }, onMove = { i, d -> c.moved += i to d }, onReorder = { f, t -> c.reordered += f to t }, onAddApp = { c.addedApps += it.label }, onDropApp = { a, p -> c.dropped += a.label to p },
                        onWidget = { c.widget++ }, onFolder = { c.folder++ }, onShortcut = {}, onLink = {}, onFile = {}, onGroup = { c.group++ },
                        onPrefs = { f -> c.prefs = f(c.prefs) }, notice = null, onNoticeDone = {}, focusAdd = false, onExit = { c.exit++ },
                        topBar = topBar,
                    )
                    // The live page, in the focus slot (the real home does this with the same frame).
                    if (!glanceFocus) Box(Modifier.fillMaxSize().editCardLayer { EditCardGeometry.frame(layout.target, state.value, false) }.background(Color(0xFF30406A))) { Text("LIVE", color = Color.White) }
                }
               }
              }
            }
        }
    }

    private fun tag(t: String) = rule.onNodeWithTag(t)
    private fun pc(i: Int) = tag("pe.page.$i")
    private fun bounds(t: String) = tag(t).getUnclippedBoundsInRoot()
    private val dens get() = rule.density.density
    private fun cardDp() = layoutNow.card.let { (it.left / dens).dp to (it.top / dens).dp }

    /** The live card (from the layout the host used) is completely inside the window and below the strip, above the tabs. */
    private fun assertLiveInside(@Suppress("UNUSED_PARAMETER") w0: Float, @Suppress("UNUSED_PARAMETER") h0: Float) {
        val w = winW; val h = winH
        val c = layoutNow.card
        assertTrue("left", c.left >= -0.5f); assertTrue("top", c.top >= layoutNow.strip.bottom - 0.5f || layoutNow.side)
        assertTrue("right $w: ${c.right / dens}", c.right / dens <= w + 0.5f); assertTrue("bottom $h: ${c.bottom / dens}", c.bottom / dens <= h + 0.5f)
        // and the real node: the stand-in page's text is drawn inside the window
        val live = rule.onNodeWithText("LIVE").getUnclippedBoundsInRoot()
        assertTrue("the live page's text is inside the window", live.left >= 0.dp && live.right <= w.dp + 1.dp && live.top >= 0.dp && live.bottom <= h.dp + 1.dp)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe20_portraitHasTheStripAboveTheLiveCardAndTheTabsBelowIt() {
        val c = Calls(); host(c, fourPages, current = 1, topBar = { Text("TOPBAR") })
        pc(1).assertIsSelected(); pc(0).assertIsNotSelected()
        val l = layoutNow
        val strip = rule.onNodeWithContentDescription("Page carousel").getUnclippedBoundsInRoot()
        assertTrue("the top bar's 56 dp slot is above the strip", rule.onNodeWithText("TOPBAR").getUnclippedBoundsInRoot().bottom <= strip.top + 1.dp)
        assertEquals(56f, (l.topBar.height / dens), 0.5f)
        assertTrue("the strip is above the live card", strip.bottom <= (l.card.top / dens).dp + 1.dp)
        val apps = rule.onNodeWithText("Apps").getUnclippedBoundsInRoot()
        assertTrue("the tabs are below the live card", apps.top >= (l.card.bottom / dens).dp - 1.dp)
        assertTrue("in order glance, 1, 2, 3, 4, new page, left to right", bounds("pe.glance").left < bounds("pe.page.0").left && bounds("pe.page.0").left < bounds("pe.page.1").left && bounds("pe.page.1").left < bounds("pe.page.2").left)
        assertEquals("one row: same top", bounds("pe.page.0").top.value, bounds("pe.page.3").top.value, 0.5f)
        // the focused card is centred in the strip
        assertEquals(411f / 2, bounds("pe.page.1").left.value + bounds("pe.page.1").width.value / 2, 1.5f)
        rule.onNodeWithText("Apps").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertIsSelected()
        rule.onNodeWithContentDescription("Add to home: Camera").assertIsDisplayed()
        assertLiveInside(411f, 891f)
        shoot("paginated_portrait_dark")
    }

    @Test @Config(sdk = [35], qualifiers = "w320dp-h568dp-port-xhdpi")
    fun HOM_12_pe20b_theLiveCardIsWholeOnTheSmallestPhone() {
        host(Calls(), fourPages, current = 1); assertLiveInside(320f, 568f)
        assertTrue("the tab row is on screen", rule.onNodeWithText("Settings").getUnclippedBoundsInRoot().bottom <= 568.dp)
        shoot("paginated_portrait_320")
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-xhdpi")
    fun HOM_12_pe20c_theLiveCardIsWholeOnA360By640Phone() { host(Calls(), fourPages, current = 1); assertLiveInside(360f, 640f) }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe20d_theLiveCardIsWholeWithFontScaleOneThree() { host(Calls(), fourPages, current = 1, fontScale = 1.3f); assertLiveInside(411f, 891f) }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe21_tabsSwitchContentAndSettingsNamesThePageAndShowsTheWallpaperAsGlobal() {
        val c = Calls(); host(c, fourPages)
        rule.onNodeWithText("Widgets").performClick(); rule.onNodeWithText("Widgets").assertIsSelected(); rule.onNodeWithText("Apps").assertIsNotSelected()
        rule.onNodeWithText("Choose a widget").performClick(); assertEquals(1, c.widget)
        rule.onNodeWithText("Folders").performClick(); rule.onNodeWithText("New folder").performClick(); assertEquals(1, c.folder)
        rule.onNodeWithText("App group").performScrollTo().performClick(); assertEquals(1, c.group)
        rule.onNodeWithText("App shortcut").assertExists()
        rule.onNodeWithText("Settings").performClick()
        tag("pe.settings.name").assertIsDisplayed(); rule.onNodeWithText("Page 1").assertIsDisplayed()
        tag("pe.settings.global").assertIsDisplayed(); rule.onNodeWithText("All pages").assertIsDisplayed()
        rule.onNodeWithText("Applies to every page and the scroll, not only this one").assertIsDisplayed()
        rule.onAllNodesWithText("Applies to the pages and the scroll alike").assertCountEquals(0)
        assertTrue("the global heading comes before the wallpaper switch", bounds("pe.settings.global").bottom <= rule.onNodeWithText("Pages use the same wallpaper as Glance").getUnclippedBoundsInRoot().top)
        rule.onNodeWithText("Pages use the same wallpaper as Glance").performClick()
        assertTrue("the switch wrote the pref", !c.prefs.pagesSameAsGlance)
        shoot("settings_tab")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe21b_theSettingsTabNamesTheFocusedPage() {
        val c = Calls(); host(c, fourPages, current = 2)
        rule.onNodeWithText("Settings").performClick(); rule.onNodeWithText("Page 3").assertIsDisplayed(); rule.onNodeWithText("Page 3 of 4").assertIsDisplayed()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe22_tappingACardReportsWhereItWasFromTheLiveCardCentreForTheSwing() {
        val c = Calls(); host(c, fourPages, current = 1)
        pc(2).performClick()
        assertEquals(1, c.selected.size)
        val (p, dx, dy) = c.selected[0]
        assertEquals(2, p)
        assertTrue("the next card is right of the live card's centre by about one pitch", dx > 0f && dx < layoutNow.pitch * 1.5f)
        assertTrue("the strip is above the live card", dy < -100f)
        pc(0).performClick()
        assertTrue("an earlier card is left of it", c.selected[1].second < 0f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe22b_theNewPageCardAtTheEndHasItsLabelAndAddsOnePage() {
        val c = Calls(); host(c, fourPages, current = 3)
        tag("pe.add").assertIsDisplayed(); rule.onNodeWithText("New page").assertIsDisplayed()
        tag("pe.add").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assert(hasContentDescription("New page"))
        assertTrue("it is after the last page", bounds("pe.add").left > bounds("pe.page.3").left)
        tag("pe.add").performClick(); assertEquals(1, c.added.size); assertTrue(c.added[0].second < 0f)
        rule.onAllNodesWithContentDescription("Add page").assertCountEquals(0)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe23_theCardsOverflowMenuHoldsMoveEarlierMoveLaterAndDelete() {
        val c = Calls(); host(c, fourPages, current = 0)
        tag("pe.more.0").assert(hasContentDescription("Actions for page 1")).performClick(); rule.waitForIdle()
        tag("pe.menu.earlier.0").assertIsDisplayed().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
        tag("pe.menu.later.0").assertIsDisplayed().performClick()
        tag("pe.more.0").performClick(); rule.waitForIdle()
        tag("pe.menu.delete.0").performClick()
        assertEquals(listOf(0 to 1), c.moved); assertEquals(listOf(0), c.deleted)
        tag("pe.more.3").performClick(); rule.waitForIdle()
        tag("pe.menu.later.3").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
        shoot("overflow_menu")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe23b_theFloatingRailTheHamburgerAndTheSettingsDeleteAreGone() {
        val c = Calls(); host(c, fourPages, current = 1)
        rule.onAllNodesWithContentDescription("Move page 2 earlier").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Move page 2 later").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Delete page 2").assertCountEquals(0)
        rule.onAllNodesWithTag("pe.menu").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Page editor menu").assertCountEquals(0)
        rule.onNodeWithText("Settings").performClick()
        rule.onAllNodesWithText("Delete page 2").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Add page").assertCountEquals(0)
        assertEquals("one overflow menu per page, none on the Glance or the New page card", 4, rule.onAllNodes(hasTestTag("pe.more.0") or hasTestTag("pe.more.1") or hasTestTag("pe.more.2") or hasTestTag("pe.more.3")).fetchSemanticsNodes().size)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe23c_longPressAndDragReordersWithAGhostAndAnInsertionLine() {
        val c = Calls(); host(c, fourPages, current = 0)
        val pitch = layoutNow.pitch
        pc(1).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(900)
        pc(1).performTouchInput { moveBy(Offset(pitch * 1.1f, 0f)) }
        rule.mainClock.advanceTimeBy(50)
        tag("pe.ghost.card").assertIsDisplayed()
        tag("pe.insertion").assertIsDisplayed()
        shoot("reorder_drag")
        pc(1).performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(listOf(1 to 2), c.reordered)
        rule.onAllNodesWithTag("pe.ghost.card").assertCountEquals(0); rule.onAllNodesWithTag("pe.insertion").assertCountEquals(0)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe23d_aShortDragOfLessThanHalfASlotChangesNothing() {
        val c = Calls(); host(c, fourPages, current = 0)
        val pitch = layoutNow.pitch
        pc(1).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(900)
        pc(1).performTouchInput { moveBy(Offset(pitch * 0.3f, 0f)) }
        pc(1).performTouchInput { up() }
        rule.waitForIdle()
        assertTrue(c.reordered.isEmpty())
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    fun HOM_12_pe23e_shiftArrowReordersAndPlainArrowsMoveBetweenCards() {
        val c = Calls(); host(c, fourPages, current = 1)
        rule.runOnUiThread { inputModes.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) } // as a keyboard user: clickable cards take focus only in keyboard mode
        pc(1).requestFocus(); pc(1).assertIsFocused()
        pc(1).performKeyInput { keyDown(Key.ShiftLeft); pressKey(Key.DirectionRight); keyUp(Key.ShiftLeft) }
        assertEquals(listOf(1 to 1), c.moved)
        pc(1).performKeyInput { keyDown(Key.ShiftLeft); pressKey(Key.DirectionLeft); keyUp(Key.ShiftLeft) }
        assertEquals(listOf(1 to 1, 1 to -1), c.moved)
        pc(1).performKeyInput { pressKey(Key.DirectionRight) }
        pc(2).assertIsFocused()
        pc(2).performKeyInput { pressKey(Key.DirectionLeft) }
        pc(1).assertIsFocused()
        pc(1).performKeyInput { pressKey(Key.Enter) }
        assertEquals("Enter on a card is a tap on it", 1, c.selected.size); assertEquals(1, c.selected[0].first)
        assertEquals("plain arrows and Enter never reorder", 2, c.moved.size)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe23f_everyPageCardHasMoveEarlierMoveLaterAndDeleteAsAccessibilityActions() {
        val c = Calls(); host(c, fourPages, current = 1)
        fun actions(i: Int) = pc(i).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Move earlier", "Move later", "Delete page"), actions(1).map { it.label })
        assertEquals("the first page cannot move earlier", listOf("Move later", "Delete page"), actions(0).map { it.label })
        assertEquals("the last cannot move later", listOf("Move earlier", "Delete page"), actions(3).map { it.label })
        rule.runOnUiThread { actions(1)[0].action(); actions(1)[1].action(); actions(1)[2].action() }
        assertEquals(listOf(1 to -1, 1 to 1), c.moved); assertEquals(listOf(1), c.deleted)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe24_glanceIsTheFirstCardWithAHouseAndNoMenuAndNoDelete() {
        val c = Calls(); host(c, fourPages, current = 0)
        assertTrue("Glance is left of page 1", bounds("pe.glance").left < bounds("pe.page.0").left)
        tag("pe.glance").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assert(hasContentDescription("Glance"))
        assertTrue("no custom actions on the Glance card", !tag("pe.glance").fetchSemanticsNode().config.contains(SemanticsActions.CustomActions))
        rule.onAllNodesWithContentDescription("Actions for page 0").assertCountEquals(0)
        tag("pe.glance").performClick(); assertEquals(1, c.glance.size)
        rule.onAllNodesWithContentDescription("Add to home").assertCountEquals(1) // the content area
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe24b_withTheGlanceFocusedItsSettingsAreInTheSettingsTabAndNoPageIsSelected() {
        val c = Calls(); host(c, fourPages, current = 0, glanceFocus = true)
        tag("pe.glance").assertIsSelected(); tag("pe.glance").assert(hasContentDescription("Glance, selected"))
        pc(0).assertIsNotSelected()
        rule.onNodeWithText("Hotspots").assertIsDisplayed(); rule.onNodeWithText("Show music").assertIsDisplayed()
        rule.onNodeWithText("Top left").performClick()
        assertTrue("a hotspot toggled", com.bos.sphere.core.data.Corner.TopLeft !in c.prefs.glanceHotspots)
        shoot("glance_focus")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe25_noGlanceCardWhenGlanceIsOff() {
        val c = Calls(); host(c, fourPages, glanceOn = false)
        rule.onAllNodesWithTag("pe.glance").assertCountEquals(0)
        pc(0).assertIsSelected()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe26_dropSeamPlacesAnAppOnTheStripCardOrTheLiveCardAndIgnoresOtherPlaces() {
        val c = Calls(); host(c, fourPages, current = 1)
        val d = dens
        val o = rule.onRoot().fetchSemanticsNode().positionInWindow
        val b = bounds("pe.page.2")
        rule.runOnUiThread { drag.onDrop!!.invoke(apps[6], Offset((b.left.value + b.width.value / 2) * d + o.x, (b.top.value + 30) * d + o.y)) }
        assertEquals(listOf("Golf" to 2), c.dropped)
        // On the live card it goes to the current page.
        val l = layoutNow.card
        rule.runOnUiThread { drag.onDrop!!.invoke(apps[7], Offset((l.left + l.width / 2) + o.x, (l.top + l.height / 2) + o.y)) }
        assertEquals("Hotel" to 1, c.dropped[1])
        rule.runOnUiThread { drag.onDrop!!.invoke(apps[7], Offset(5f, 5f)) }
        assertEquals("nothing happens off the cards", 2, c.dropped.size)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe27_continuousPortraitHasTheSameLayoutAndTapReportsTheCard() {
        val c = Calls(); host(c, fourPages, current = 2, continuous = true)
        val l = layoutNow
        assertEquals(PageEditorGeometry.Axis.Horizontal, l.axis)
        assertTrue("the strip is above the live card in Continuous too", rule.onNodeWithContentDescription("Page carousel").getUnclippedBoundsInRoot().bottom <= (l.card.top / dens).dp + 1.dp)
        assertTrue("neighbours sit on the sides of the focus card", bounds("pe.page.1").left < bounds("pe.page.2").left && bounds("pe.page.3").left > bounds("pe.page.2").left)
        val tabs = rule.onNodeWithText("Apps").getUnclippedBoundsInRoot(); val widgets = rule.onNodeWithText("Widgets").getUnclippedBoundsInRoot()
        assertTrue("the tabs are below the live card, in one row", tabs.top >= (l.card.bottom / dens).dp - 1.dp && kotlin.math.abs(tabs.bottom.value - widgets.bottom.value) < 12f)
        pc(3).performClick()
        val (p, dx, dy) = c.selected.single()
        assertEquals(3, p); assertTrue("a card at the side reports a horizontal offset", dx > 0f && dy < 0f)
        assertLiveInside(411f, 891f)
        shoot("continuous_portrait_dark")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe28_rtlMirrorsTheRowSoPageOneIsOnTheRightAndTheOrderSettingDoesToo() {
        val c = Calls(); host(c, fourPages, current = 1, rtl = true)
        assertTrue("page 1 is right of page 2", bounds("pe.page.0").left > bounds("pe.page.1").left)
        shoot("paginated_rtl")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe28b_theRightToLeftOrderSettingMirrorsTheRowToo() {
        host(Calls(), fourPages, current = 1, orderRtl = true)
        assertTrue("page 1 is right of page 2", bounds("pe.page.0").left > bounds("pe.page.1").left)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe28c_continuousPageOrderRunsRightToLeftWithTheOrderSetting() {
        host(Calls(), fourPages, current = 1, continuous = true, orderRtl = true)
        assertTrue(bounds("pe.page.0").left > bounds("pe.page.1").left)
        shoot("continuous_rtl")
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_12_pe29_landscapeHasTheStripAsAColumnAtTheEndThenTheLiveCardThenTheTabs() {
        val c = Calls(); host(c, fourPages, current = 1, landscape = true)
        val l = layoutNow
        assertTrue(l.side)
        val strip = rule.onNodeWithContentDescription("Page carousel").getUnclippedBoundsInRoot()
        assertTrue("the strip is the last column", strip.left >= 891.dp - 100.dp)
        assertTrue("the cards stack top to bottom", bounds("pe.page.0").top < bounds("pe.page.1").top && bounds("pe.page.1").top < bounds("pe.page.2").top)
        assertTrue("the live card is between the tabs and the strip", (l.card.right / dens).dp <= strip.left + 1.dp && (l.tabs.right / dens).dp <= (l.card.left / dens).dp + 1.dp)
        val a = rule.onNodeWithText("Apps").getUnclippedBoundsInRoot(); val w = rule.onNodeWithText("Widgets").getUnclippedBoundsInRoot()
        assertTrue("the tabs are one row", kotlin.math.abs(a.bottom.value - w.bottom.value) < 12f)
        assertLiveInside(891f, 411f)
        shoot("paginated_landscape_dark")
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_12_pe29b_landscapeContinuousIsTheSame() {
        host(Calls(), fourPages, current = 1, continuous = true, landscape = true)
        assertTrue(layoutNow.side); assertTrue(bounds("pe.page.0").top < bounds("pe.page.1").top)
        assertLiveInside(891f, 411f)
        shoot("continuous_landscape_dark")
    }

    @Test @Config(sdk = [35], qualifiers = "w800dp-h1280dp-port-xhdpi")
    fun HOM_12_pe29c_aTabletPortraitUsesTheSideStripToo() {
        host(Calls(), fourPages, current = 1)
        assertTrue(layoutNow.side); assertTrue(bounds("pe.page.0").top < bounds("pe.page.1").top)
        assertLiveInside(800f, 1280f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe30_lightThemeRendersBothModes() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF) }
        host(Calls(), fourPages, current = 1); pc(1).assertIsSelected(); shoot("paginated_portrait_light")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe31_lightThemeContinuous() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF) }
        host(Calls(), fourPages, current = 1, continuous = true); pc(1).assertIsSelected(); shoot("continuous_portrait_light")
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_12_pe32_lightThemeLandscape() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF) }
        host(Calls(), fourPages, current = 1, landscape = true); pc(1).assertIsSelected(); shoot("paginated_landscape_light")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe33_fontScaleTwoKeepsTheTabsReachableAndTheLiveCardWhole() {
        val c = Calls(); host(c, fourPages, current = 1, fontScale = 2f)
        rule.onNodeWithText("Apps").assertIsDisplayed()
        rule.onNodeWithText("Settings").performClick(); rule.onNodeWithText("Settings").assertIsSelected()
        assertLiveInside(411f, 891f)
        shoot("font_scale_2")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe34_reducedMotionStillShowsTheEditor() {
        ReducedMotionTesting.force(com.bos.sphere.core.quality.MotionPrefs(0f, 0f, 0f))
        val c = Calls(); host(c, fourPages, current = 2, reduced = true)
        pc(2).assertIsSelected()
        pc(3).performClick(); assertEquals(3, c.selected.single().first)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe35_swingMidFrameDrawsTheGlanceCardInTheSlot() {
        val c = Calls(); host(c, fourPages, current = 1, glanceFocus = true, swing = PageEditorGeometry.swing(0.4f, 0f, -300f, false, 0.4f))
        rule.onNodeWithTag("page-editor-glance-focus").assertIsDisplayed()
        shoot("swing_mid_frame_glance")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe36_a11yTabsAreRealTabsCardsAreButtonsSaying_PageNOfM_selected() {
        val c = Calls(); host(c, fourPages, current = 1)
        for (t in listOf("Apps", "Widgets", "Folders", "Settings")) {
            rule.onNodeWithText(t).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            assertTrue(t, rule.onNodeWithText(t).getUnclippedBoundsInRoot().height >= 48.dp - 9.dp) // the text; the 48 dp target is the tab around it
            assertTrue("$t target", tag("pe.tab.${EditorTab.entries.first { it.label == t }.name}").getUnclippedBoundsInRoot().height >= 48.dp)
        }
        rule.onNodeWithContentDescription("Page 2 of 4, selected").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertIsSelected()
        rule.onNodeWithContentDescription("Page 1 of 4").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertIsNotSelected()
        rule.onNodeWithContentDescription("Page 3 of 4, empty").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        rule.onNodeWithContentDescription("Page carousel").assertExists()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe37_eightPagesScrollTheStripSoTheFocusCardStaysCentred() {
        val many = (1..8).map { page(it.toLong(), apps[it - 1]) }
        val c = Calls(); host(c, many, current = 6)
        rule.mainClock.advanceTimeBy(800)
        val b = bounds("pe.page.6")
        assertEquals("page 7 is in the middle of the strip", 411f / 2, b.left.value + b.width.value / 2, 2f)
        assertTrue("the first cards are scrolled out", rule.onAllNodesWithTag("pe.page.0").fetchSemanticsNodes().isEmpty() || bounds("pe.page.0").right <= 0.dp)
        assertLiveInside(411f, 891f)
        shoot("strip_scrolled_8_pages")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe38_theTopBarSlotIs56DpAndFilledWhenAnIntegrationDrawsInIt() {
        host(Calls(), fourPages, topBar = { Text("CHROME") })
        tag("page-editor-topbar-slot").assertIsDisplayed(); rule.onNodeWithText("CHROME").assertIsDisplayed()
        assertEquals(56f, tag("page-editor-topbar-slot").getUnclippedBoundsInRoot().height.value, 0.6f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_pe38b_withoutATopBarTheSlotIsEmpty() {
        host(Calls(), fourPages)
        rule.onAllNodesWithTag("page-editor-topbar-slot").assertCountEquals(0)
    }
}
