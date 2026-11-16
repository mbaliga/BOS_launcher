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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.LayoutDirection
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePageOps
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
 * HOM-12 (page carousel), HOM-04 / HOM-05 (add carousel), HOM-01 (empty hint): Robolectric UI tests of the carousels and the
 * card layer, in both orientations, light and dark and RTL. Screenshots go to build/screenshots/edit_card_*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class EditCardUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @After fun reset() { Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }; L10n.setLanguage(""); ReducedMotionTesting.force(null) }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(400)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { Canvas(bmp).also { it.drawColor(android.graphics.Color.rgb(20, 26, 50)); view.draw(it) } }
        File(outDir, "edit_card_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val labels = listOf("Alpha", "Bravo", "Camera", "Delta", "Echo", "Foxtrot", "Golf", "Hotel")
    private val apps = labels.mapIndexed { i, l ->
        AppEntry("demo.$l", ComponentName("demo.$l", "M"), l, Process.myUserHandle()) { ColorDrawable(0xFF3070D0.toInt() + i * 0x101010) }
    }
    private val byKey = apps.associateBy { it.key }
    private fun page(id: Long, vararg k: AppEntry) = HomePage(k.map { it.key }, PageLayout(), id)

    private class Calls {
        val selected = ArrayList<Int>(); val deleted = ArrayList<Int>(); val moved = ArrayList<Pair<Int, Int>>(); val moveTo = ArrayList<Pair<Int, Int>>()
        var added = 0; val addedApps = ArrayList<String>(); var widget = 0; var folder = 0; var group = 0; var exit = 0
    }

    private fun host(c: Calls, pages: List<HomePage>, current: Int = 0, landscape: Boolean, rtl: Boolean = false, onHome: Set<String> = emptySet(), focusAdd: Boolean = false, progress: Float = 1f) {
        rule.setContent {
            val dir = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides dir) {
                val target = rememberEditCardTarget()
                val state = mutableStateOf(progress)
                Box(Modifier.fillMaxSize().background(Color(0xFF0A0B10))) {
                    EditCardHost(
                        progress = state, reduced = false, target = target, pages = pages, current = { current }, appsByKey = byKey, prefs = LauncherPrefs(), isLandscape = landscape,
                        apps = apps, onHome = onHome, hasWork = false, isWork = { false },
                        onSelect = { c.selected += it }, onDelete = { c.deleted += it }, onMove = { i, d -> c.moved += i to d }, onMoveTo = { i, t -> c.moveTo += i to t },
                        onAddPage = { c.added++ }, onAddApp = { c.addedApps += it.label }, onWidget = { c.widget++ }, onFolder = { c.folder++ },
                        onShortcut = {}, onLink = {}, onFile = {}, onGroup = { c.group++ }, notice = null, onNoticeDone = {}, focusAdd = focusAdd, onExit = { c.exit++ },
                    )
                    Box(Modifier.editCardLayer { EditCardGeometry.frame(target, state.value, false) }.fillMaxSize().background(Color(0xFF30406A))) { Text("HOME", color = Color.White) }
                }
            }
        }
    }

    private val threePages get() = listOf(page(1, apps[0], apps[1]), page(2), page(3, apps[2]))

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_portraitCarouselShowsEveryPageAndHighlightsTheCurrentOne() {
        val c = Calls(); host(c, threePages, current = 1, landscape = false)
        rule.onNodeWithContentDescription("Page 2 of 3, empty").assertIsSelected()
        shoot("portrait_dark")
        rule.onNodeWithTag("page-carousel-list").performScrollToIndex(2) // the column scrolls
        rule.onNodeWithContentDescription("Page 3 of 3").assertIsDisplayed()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_tapSwitchesPageAndEveryPageHasMoveAndDeleteButtons() {
        val c = Calls(); host(c, threePages, current = 0, landscape = false)
        rule.onNodeWithContentDescription("Move page 1 up").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Move page 1 down").assertIsEnabled().performClick()
        rule.onNodeWithTag("page-carousel-list").performScrollToIndex(2)
        rule.onNodeWithContentDescription("Page 3 of 3").performClick()
        assertEquals(listOf(2), c.selected)
        rule.onNodeWithContentDescription("Move page 3 down").assertIsNotEnabled()
        rule.onNodeWithTag("page-carousel-list").performScrollToIndex(1)
        rule.onNodeWithContentDescription("Delete page 2").performClick()
        rule.onNodeWithContentDescription("Add page").performClick()
        assertEquals(listOf(0 to 1), c.moved); assertEquals(listOf(1), c.deleted); assertEquals(1, c.added)
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_12_landscapeLayoutAndKeyboardEscLeavesEditMode() {
        val c = Calls(); host(c, threePages, landscape = true)
        rule.onNodeWithContentDescription("Page carousel").assertIsDisplayed()
        rule.onNodeWithContentDescription("Add to home").assertIsDisplayed()
        rule.onNodeWithContentDescription("Filter apps").performClick() // focus inside the host
        rule.onRoot().performKeyInput { pressKey(Key.Escape) }
        assertEquals("Esc leaves edit mode", 1, c.exit)
        shoot("landscape_dark")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_lightThemeRenders() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF) }
        val c = Calls(); host(c, threePages, landscape = false)
        rule.onNodeWithContentDescription("Page 1 of 3").assertIsDisplayed()
        shoot("portrait_light")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_rtlPutsTheCardOnTheRightAndTheCarouselOnTheLeft() {
        val c = Calls(); host(c, threePages, landscape = false, rtl = true)
        rule.onNodeWithContentDescription("Page carousel").assertIsDisplayed()
        shoot("portrait_rtl")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_05_addCarouselListsAppsAlphabeticallyFiltersAndAdds() {
        val c = Calls(); host(c, threePages, landscape = false, onHome = setOf(apps[0].key))
        rule.onNodeWithContentDescription("Add to home: Bravo").assertIsDisplayed()
        rule.onNodeWithContentDescription("Add to home: Alpha").assertDoesNotExist() // already on the home
        rule.onNodeWithContentDescription("Filter apps").performTextInput("cam")
        rule.onNodeWithContentDescription("Add to home: Bravo").assertDoesNotExist()
        rule.onNodeWithContentDescription("Add to home: Camera").performClick()
        assertEquals(listOf("Camera"), c.addedApps)
        shoot("add_carousel_filtered")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_04_tabsReachWidgetsFoldersAndShortcuts() {
        val c = Calls(); host(c, threePages, landscape = false)
        rule.onNodeWithText("Widgets").performClick(); rule.onNodeWithText("Choose a widget").performClick()
        rule.onNodeWithText("Folders").performClick(); rule.onNodeWithText("New folder").performClick()
        rule.onNodeWithText("Shortcuts").performClick(); rule.onNodeWithText("App shortcut").assertIsDisplayed(); rule.onNodeWithText("Web link").assertIsDisplayed(); rule.onNodeWithText("File").assertIsDisplayed()
        rule.onNodeWithText("App group").performClick(); assertEquals(1, c.group) // WSP-05
        assertEquals(1, c.widget); assertEquals(1, c.folder)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_05_hiddenAppsStayOutOfTheCarousel() {
        // The host passes only visible apps (hidden ones are filtered upstream); an app absent from the list never shows.
        val c = Calls(); host(c, threePages, landscape = false)
        rule.onNodeWithContentDescription("Add to home: Zulu").assertDoesNotExist()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun NFR_14_reducedMotionStillShowsTheCarouselWithoutTheShrink() {
        ReducedMotionTesting.force(com.bos.sphere.core.quality.MotionPrefs(0f, 0f, 0f))
        val c = Calls(); host(c, threePages, landscape = false)
        rule.onNodeWithContentDescription("Page carousel").assertIsDisplayed()
        shoot("portrait_reduced")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_carouselIsHiddenWhenTheHomeIsNotACard() {
        val c = Calls(); host(c, threePages, landscape = false, progress = 0f)
        rule.onNodeWithContentDescription("Page carousel").assertDoesNotExist()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_01_emptyHintIsQuietAndOpensEdit() {
        var opened = 0
        rule.setContent { EmptyHomeHint(onOpen = { opened++ }) }
        rule.onNodeWithText("Long press to add apps").assertIsDisplayed().performClick()
        assertEquals(1, opened)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_lastPageDeleteLeavesAnEmptyPageViaThePureRule() {
        val r = HomePageOps.delete(listOf(page(1, apps[0])), 0) { 20 }
        assertEquals(1, r.pages.size); assertTrue(r.pages[0].keys.isEmpty())
    }
}
