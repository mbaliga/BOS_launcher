package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.SearchLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Wave 25 (owner item 5): in the running Radar nothing is drawn partly outside the display area or under the keyboard or the mic, all the recents
 * the region can hold are placed at once, and no touch is swallowed. Windows: 360x640, 411x891, 320x480, 640x360, 891x411, 1280x800, 280x653;
 * font scale 1.0 and 2.0; keyboard closed and 40 percent open (landscape also 60 percent).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RadarBoundsW25UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = java.io.File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    @Before fun english() { com.bos.sphere.core.design.L10n.setLanguage("en") }

    private val names = List(44) { "App%02d".format(it) }
    private val apps = names.map { n -> AppEntry("demo.$n", ComponentName("demo.$n", "demo.$n.Main"), n, Process.myUserHandle()) { null } }
    private var dismissed = 0
    private var launched = 0

    private fun open(layout: SearchLayout, query: String = "", fontScale: Float = 1f, motion: Boolean = false, dark: Boolean = true) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val d = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, fontScale)) {
                SearchScreen(
                    apps = apps, recent = apps, prefs = LauncherPrefs(searchMotion = motion, searchLayout = layout, searchLayoutPinned = true), contacts = null,
                    onLaunch = { launched++ }, onLongPressApp = { launched++ }, onOpenIntent = { launched++ }, onRequestContactsPermission = {}, onDismiss = { dismissed++ },
                    hub = null, onOpenHit = null, onRunAction = {}, onRequestFilePermission = {}, sounds = null, initialQuery = query,
                    launchHistory = RadarTestHabits.history(apps), learnedOverride = emptyMap(), nowMillis = { RadarTestHabits.NOW }, voiceAvailable = true,
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
    }

    private fun fakeInsets(imeBottomPx: Int) {
        rule.runOnUiThread {
            val w = androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(), androidx.core.graphics.Insets.of(0, 0, 0, imeBottomPx))
                .setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(), imeBottomPx > 0)
                .build()
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(rule.activity.window.decorView, w)
        }
        rule.mainClock.advanceTimeBy(500)
    }

    private fun root() = rule.onRoot().fetchSemanticsNode().boundsInRoot
    private fun tiles() = rule.onAllNodes(hasTestTag("orbitTile")).fetchSemanticsNodes().map { it.boundsInRoot }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(900)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        java.io.File(outDir, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The Orbit's tiles all lie inside the region between the field, the mic bar and the keyboard; returns how many are placed. */
    private fun assertOrbitInside(font: Float, keyboard: Float, motion: Boolean = false, shot: String? = null): Int {
        open(SearchLayout.Orbit, "", font, motion)
        val r = root()
        val px = rule.activity.resources.displayMetrics.density
        if (keyboard > 0f) fakeInsets((r.height * keyboard).toInt())
        val field = rule.onNodeWithTag(RADAR_FIELD_TAG).fetchSemanticsNode().boundsInRoot
        val mic = rule.onAllNodes(hasTestTag(RADAR_MIC_TAG)).fetchSemanticsNodes().first().boundsInRoot
        val inline = mic.center.y < field.bottom
        val imeTop = r.bottom - r.height * keyboard
        val bottom = if (inline) imeTop else minOf(imeTop, mic.top)
        val area = Rect(r.left, field.bottom, r.right, bottom)
        val where = "${r.width / px}x${r.height / px}dp font=$font keyboard=$keyboard motion=$motion area=$area"
        val ts = tiles()
        ts.forEach { t ->
            assertTrue("a tile $t is outside the display area (left/right) : $where", t.left >= r.left - 0.5f && t.right <= r.right + 0.5f)
            assertTrue("a tile $t is above the field or under the keyboard or the mic: $where", t.top >= area.top - 0.5f && t.bottom <= area.bottom + 0.5f)
        }
        if (shot != null) shoot(shot)
        return ts.size
    }
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_360x640_font1_keyboard0() { assertOrbitInside(1f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_360x640_font1_keyboard40() { assertOrbitInside(1f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_360x640_font2_keyboard0() { assertOrbitInside(2f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_360x640_font2_keyboard40() { assertOrbitInside(2f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_411x891_font1_keyboard0() { assertOrbitInside(1f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_411x891_font1_keyboard40() { assertOrbitInside(1f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_411x891_font2_keyboard0() { assertOrbitInside(2f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_411x891_font2_keyboard40() { assertOrbitInside(2f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w320dp-h480dp-port-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_320x480_font1_keyboard0() { assertOrbitInside(1f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w320dp-h480dp-port-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_320x480_font1_keyboard40() { assertOrbitInside(1f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w320dp-h480dp-port-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_320x480_font2_keyboard0() { assertOrbitInside(2f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w320dp-h480dp-port-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_320x480_font2_keyboard40() { assertOrbitInside(2f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_640x360_font1_keyboard0() { assertOrbitInside(1f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_640x360_font1_keyboard40() { assertOrbitInside(1f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_640x360_font1_keyboard60() { assertOrbitInside(1f, 0.6f) }
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_640x360_font2_keyboard0() { assertOrbitInside(2f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_640x360_font2_keyboard40() { assertOrbitInside(2f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_640x360_font2_keyboard60() { assertOrbitInside(2f, 0.6f) }
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_891x411_font1_keyboard0() { assertOrbitInside(1f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_891x411_font1_keyboard40() { assertOrbitInside(1f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_891x411_font1_keyboard60() { assertOrbitInside(1f, 0.6f) }
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_891x411_font2_keyboard0() { assertOrbitInside(2f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_891x411_font2_keyboard40() { assertOrbitInside(2f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_891x411_font2_keyboard60() { assertOrbitInside(2f, 0.6f) }
    @Test @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_1280x800_font1_keyboard0() { assertOrbitInside(1f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_1280x800_font1_keyboard40() { assertOrbitInside(1f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_1280x800_font1_keyboard60() { assertOrbitInside(1f, 0.6f) }
    @Test @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_1280x800_font2_keyboard0() { assertOrbitInside(2f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_1280x800_font2_keyboard40() { assertOrbitInside(2f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_1280x800_font2_keyboard60() { assertOrbitInside(2f, 0.6f) }
    @Test @Config(sdk = [35], qualifiers = "w280dp-h653dp-port-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_280x653_font1_keyboard0() { assertOrbitInside(1f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w280dp-h653dp-port-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_280x653_font1_keyboard40() { assertOrbitInside(1f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w280dp-h653dp-port-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_280x653_font2_keyboard0() { assertOrbitInside(2f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w280dp-h653dp-port-night-xhdpi") fun PAL_18_orbitTilesStayInsideTheDisplayArea_280x653_font2_keyboard40() { assertOrbitInside(2f, 0.4f) }

    /** Everything the region can hold is placed at once: all 44 recents on the large windows, a quarter or more on the small ones, never none while there is room for one row. */
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_20_allFortyFourRecentsAreOnScreenAtOnceOnAPhone() { val n = assertOrbitInside(1f, 0f, shot = "w25-orbit-portrait-dark"); assertTrue("placed $n", n >= 9) }
    @Test @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi") fun PAL_20_allFortyFourRecentsAreOnScreenAtOnceOnATabletEvenWithTheKeyboardUp() { assertTrue(assertOrbitInside(1f, 0.4f) >= 12) }
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_20_aSmallPhoneShowsItsFirstRingsAtOnce() { assertTrue(assertOrbitInside(1f, 0f, shot = "w25-orbit-small-portrait") >= 4) }
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_landscapeUsesTheWholeFreeHeightAboveTheKeyboard() {
        val n = assertOrbitInside(1f, 0.4f, shot = "w25-orbit-landscape-keyboard")
        assertTrue("tiles are placed in the free height above the keyboard ($n)", n >= 8)
        val r = root(); val free = r.height * 0.6f
        assertTrue("the lowest tile reaches the bottom third of the free height", tiles().maxOf { it.bottom } > free * 0.75f)
    }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_movingRingsStayInsideTheDisplayAreaToo() {
        open(SearchLayout.Orbit, "", 1f, motion = true)
        repeat(6) { rule.mainClock.advanceTimeBy(1_500)
            val r = root()
            tiles().forEach { t -> assertTrue("a drifting tile $t left the display", t.left >= r.left - 0.5f && t.right <= r.right + 0.5f && t.top >= 0f && t.bottom <= r.bottom + 0.5f) } }
    }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-notnight-xhdpi") fun PAL_18_lightThemeRadarIsTheSameBlackOrbit() { assertOrbitInside(1f, 0f, shot = "w25-orbit-light-theme") }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_filmstripPortraitShot() { open(SearchLayout.Filmstrip); shoot("w25-filmstrip-portrait") }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_flatPortraitShot() { open(SearchLayout.Flat, "App0"); shoot("w25-flat-typed-portrait") }
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun PAL_18_filmstripLandscapeKeyboardShot() { open(SearchLayout.Filmstrip); fakeInsets(144); shoot("w25-filmstrip-landscape-keyboard") }
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun PAL_18_flatLandscapeKeyboardShot() { open(SearchLayout.Flat); fakeInsets(144); shoot("w25-flat-landscape-keyboard") }
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_orbitLandscapeNoKeyboardShot() { open(SearchLayout.Orbit); shoot("w25-orbit-landscape") }

    /** The Filmstrip never draws a tile or caption below the region (it shrinks instead): every filmstrip tile lies inside the free height. */
    private fun assertFilmstripInside(font: Float, keyboard: Float) {
        open(SearchLayout.Filmstrip, "", font)
        val r = root()
        if (keyboard > 0f) fakeInsets((r.height * keyboard).toInt())
        val imeTop = r.bottom - r.height * keyboard
        val where = "font=$font keyboard=$keyboard"
        val nodes = rule.onAllNodes(SemanticsMatcher("a strip tile") { n -> n.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() in names }).fetchSemanticsNodes()
        nodes.forEach { n ->
            val b = n.boundsInRoot
            // only tiles in the visible width count (the strip scrolls sideways); each must lie fully in the free height above the keyboard
            if (b.left >= 0f && b.right <= r.right) assertTrue("a strip tile $b hangs under the keyboard ($imeTop): $where", b.bottom <= imeTop + 0.5f && b.top >= 0f)
        }
    }
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_360x640_font1_keyboard0() { assertFilmstripInside(1f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_360x640_font1_keyboard40() { assertFilmstripInside(1f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_360x640_font2_keyboard0() { assertFilmstripInside(2f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_360x640_font2_keyboard40() { assertFilmstripInside(2f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_411x891_font1_keyboard0() { assertFilmstripInside(1f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_411x891_font1_keyboard40() { assertFilmstripInside(1f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_411x891_font2_keyboard0() { assertFilmstripInside(2f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_411x891_font2_keyboard40() { assertFilmstripInside(2f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w320dp-h480dp-port-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_320x480_font1_keyboard0() { assertFilmstripInside(1f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w320dp-h480dp-port-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_320x480_font1_keyboard40() { assertFilmstripInside(1f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w320dp-h480dp-port-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_320x480_font2_keyboard0() { assertFilmstripInside(2f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w320dp-h480dp-port-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_320x480_font2_keyboard40() { assertFilmstripInside(2f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_640x360_font1_keyboard0() { assertFilmstripInside(1f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_640x360_font1_keyboard40() { assertFilmstripInside(1f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_640x360_font2_keyboard0() { assertFilmstripInside(2f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_640x360_font2_keyboard40() { assertFilmstripInside(2f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_891x411_font1_keyboard0() { assertFilmstripInside(1f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_891x411_font1_keyboard40() { assertFilmstripInside(1f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_891x411_font2_keyboard0() { assertFilmstripInside(2f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_891x411_font2_keyboard40() { assertFilmstripInside(2f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_1280x800_font1_keyboard0() { assertFilmstripInside(1f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_1280x800_font1_keyboard40() { assertFilmstripInside(1f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_1280x800_font2_keyboard0() { assertFilmstripInside(2f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_1280x800_font2_keyboard40() { assertFilmstripInside(2f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w280dp-h653dp-port-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_280x653_font1_keyboard0() { assertFilmstripInside(1f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w280dp-h653dp-port-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_280x653_font1_keyboard40() { assertFilmstripInside(1f, 0.4f) }
    @Test @Config(sdk = [35], qualifiers = "w280dp-h653dp-port-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_280x653_font2_keyboard0() { assertFilmstripInside(2f, 0.0f) }
    @Test @Config(sdk = [35], qualifiers = "w280dp-h653dp-port-night-xhdpi") fun PAL_18_filmstripStaysAboveTheKeyboard_280x653_font2_keyboard40() { assertFilmstripInside(2f, 0.4f) }

    // ---- (3) no dead touch area: every point of the screen either hits a control or result, or has the documented empty-space action (closing Radar)

    private fun activeRects(): List<Rect> = rule.onAllNodes(SemanticsMatcher("takes a touch") { n ->
        n.config.contains(SemanticsActions.OnClick) || n.config.contains(SemanticsActions.SetText) || n.config.contains(SemanticsActions.OnLongClick)
    }).fetchSemanticsNodes().map { it.boundsInRoot } + rule.onNodeWithTag(RADAR_FIELD_TAG).fetchSemanticsNode().boundsInRoot + // the whole field puts the cursor in the text
        rule.onAllNodes(hasTestTag(RADAR_WHEEL_TAG)).fetchSemanticsNodes().map { it.boundsInRoot } // the wheel: labels select, its blank space puts the cursor in the field, a drag turns it

    private fun assertNoDeadTouch(layout: SearchLayout, font: Float, keyboard: Float, step: Float = 36f) {
        open(layout, "", font)
        val r = root()
        if (keyboard > 0f) fakeInsets((r.height * keyboard).toInt())
        val density = rule.activity.resources.displayMetrics.density
        val active = activeRects()
        var empty = 0
        val swallowed = ArrayList<String>()
        var y = step * density / 2f
        while (y < r.bottom) {
            var x = step * density / 2f
            while (x < r.right) {
                val hit = active.any { it.contains(Offset(x, y)) }
                val before = dismissed; val launchedBefore = launched
                rule.onAllNodes(isRoot()).onFirst().performTouchInput { click(Offset(x, y)) }
                rule.mainClock.advanceTimeBy(120)
                if (!hit) {
                    empty++
                    if (before + 1 != dismissed && launched == launchedBefore) swallowed += "(${(x / density).toInt()},${(y / density).toInt()})"
                }
                x += step * density
            }
            y += step * density
        }
        assertTrue("the grid found empty space to test ($empty)", empty > 0)
        assertTrue("taps on empty space swallowed at dp $swallowed ($layout font=$font keyboard=$keyboard window ${r.width / density}x${r.height / density}) field=${rule.onNodeWithTag(RADAR_FIELD_TAG).fetchSemanticsNode().boundsInRoot} rootNodes=${rule.onAllNodes(isRoot()).fetchSemanticsNodes().map { it.boundsInRoot }} active=${active.map { it.toString() }.take(6)}", swallowed.isEmpty())
    }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_noDeadTouchInOrbitPortrait() = assertNoDeadTouch(SearchLayout.Orbit, 1f, 0f)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_noDeadTouchInOrbitPortraitKeyboardOpen() = assertNoDeadTouch(SearchLayout.Orbit, 1f, 0.4f)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_noDeadTouchInOrbitLandscape() = assertNoDeadTouch(SearchLayout.Orbit, 1f, 0f)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_noDeadTouchInOrbitLandscapeKeyboardOpen() = assertNoDeadTouch(SearchLayout.Orbit, 1f, 0.4f)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_noDeadTouchInFilmstripPortrait() = assertNoDeadTouch(SearchLayout.Filmstrip, 1f, 0f)
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun PAL_18_noDeadTouchInFilmstripLandscapeKeyboardOpen() = assertNoDeadTouch(SearchLayout.Filmstrip, 1f, 0.4f)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_noDeadTouchInFlatPortrait() = assertNoDeadTouch(SearchLayout.Flat, 1f, 0f)
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun PAL_18_noDeadTouchInFlatLandscapeKeyboardOpen() = assertNoDeadTouch(SearchLayout.Flat, 1f, 0.4f)

    /** The strip beside the mic (the red ring in the owner's screenshot) used to swallow taps; it now closes Radar, and only the mic itself is active there. */
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun PAL_18_theStripBesideTheMicClosesRadarAndOnlyTheMicIsActiveInTheBottomBar() {
        open(SearchLayout.Orbit)
        val r = root(); val density = rule.activity.resources.displayMetrics.density
        val mic = rule.onNodeWithTag(RADAR_MIC_TAG).fetchSemanticsNode().boundsInRoot
        assertEquals("the mic is 56 dp", 56f * density, mic.width, 1f)
        val bar = activeRects().filter { it.top >= mic.top - 4f * density && it.height < 200f * density && it.top > r.height * 0.8f }
        assertTrue("only the mic is a control in the bottom bar: $bar", bar.all { it.width <= 56f * density + 1f })
        val before = dismissed
        rule.onAllNodes(isRoot()).onFirst().performTouchInput { click(Offset(mic.left - 40f * density, mic.center.y)) }
        rule.mainClock.advanceTimeBy(120)
        assertEquals("a tap in the strip beside the mic closes Radar", before + 1, dismissed)
    }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_orbitPortraitKeyboardShot() { assertOrbitInside(1f, 0.4f, shot = "w25-orbit-portrait-keyboard") }

    /** With a query typed, a tap on empty space neither closes Radar nor clears the query; it puts the keyboard away. */
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun PAL_18_aTapOnEmptySpaceWithAQueryKeepsRadarOpenAndTheQuery() {
        open(SearchLayout.Orbit, "App0")
        val density = rule.activity.resources.displayMetrics.density
        val mic = rule.onNodeWithTag(RADAR_MIC_TAG).fetchSemanticsNode().boundsInRoot
        rule.onAllNodes(isRoot()).onFirst().performTouchInput { click(Offset(mic.left - 60f * density, mic.center.y)) }
        rule.mainClock.advanceTimeBy(200)
        assertEquals("Radar stays open", 0, dismissed)
        assertTrue("the query is still there", rule.onAllNodes(SemanticsMatcher("query") { n -> n.config.getOrNull(SemanticsProperties.EditableText)?.text == "App0" }).fetchSemanticsNodes().isNotEmpty())
    }
}
