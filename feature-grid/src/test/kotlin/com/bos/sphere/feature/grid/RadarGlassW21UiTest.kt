package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.BackgroundChoice
import com.bos.sphere.core.data.BgItem
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.SearchLayout
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
import kotlin.math.abs

/**
 * Wave 21 (owner: "For the Radar, keep the mic icon for voice input in the centre at the bottom. Don't make the field and button that
 * kind of grey. Make it glass. Put the X inside the field."): the mic is at the bottom centre in every layout and orientation and clear of
 * the results, the Clear X is inside the field (only with text, never together with Close), every target is 48 dp, right-to-left mirrors.
 * Screenshots go to `build/screenshots/w21-*.png`. (Text contrast on the glass is RadarGlassContrastW21Test.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RadarGlassW21UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun english() { com.bos.sphere.core.design.L10n.setLanguage("en") }

    private val names = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India")
    private val apps = names.map { n -> AppEntry("demo.$n", ComponentName("demo.$n", "demo.$n.Main"), n, Process.myUserHandle()) { null } }
    private val px = 2f // xhdpi

    private fun open(layout: SearchLayout, query: String = "", voice: Boolean = true, bg: BackgroundChoice = BackgroundChoice.DEFAULT, onDismiss: () -> Unit = {}, rtl: Boolean = false) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides if (rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr) {
            SearchScreen(
                apps = apps, recent = apps, prefs = LauncherPrefs(searchMotion = false, searchLayout = layout, searchLayoutPinned = true, backgroundSlot1 = bg.slot1, backgroundSlot2 = bg.slot2), contacts = null,
                onLaunch = {}, onLongPressApp = {}, onOpenIntent = {}, onRequestContactsPermission = {}, onDismiss = onDismiss,
                hub = null, onOpenHit = null, onRunAction = {}, onRequestFilePermission = {}, sounds = null, initialQuery = query,
                launchHistory = RadarTestHabits.history(apps), learnedOverride = emptyMap(), nowMillis = { RadarTestHabits.NOW }, voiceAvailable = voice,
            )
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
    }

    private fun bounds(desc: String) = rule.onNodeWithContentDescription(desc).fetchSemanticsNode().boundsInRoot
    private fun count(desc: String) = rule.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().size
    private fun field() = rule.onNodeWithTag(RADAR_FIELD_TAG).fetchSemanticsNode().boundsInRoot
    private fun root() = rule.onRoot().fetchSemanticsNode().boundsInRoot
    private fun inside(inner: Rect, outer: Rect) = inner.left >= outer.left - 0.5f && inner.right <= outer.right + 0.5f && inner.top >= outer.top - 0.5f && inner.bottom <= outer.bottom + 0.5f

    private fun resultBounds(): List<Rect> = rule.onAllNodes(SemanticsMatcher("a result") { n ->
        n.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() in names
    }).fetchSemanticsNodes().map { it.boundsInRoot } + rule.onAllNodes(SemanticsMatcher("a result row") { n ->
        n.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text in names
    }).fetchSemanticsNodes().map { it.boundsInRoot }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(600)
        Thread.sleep(2500) // the glass shades are measured off the main thread; let them land
        rule.mainClock.advanceTimeBy(600)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ---- (1) the mic is at the bottom centre

    private fun assertMicAtBottomCentre(layout: SearchLayout, query: String, rtl: Boolean = false) {
        open(layout, query, rtl = rtl)
        val m = bounds("Speak to search")
        val r = root()
        assertTrue("mic is at least 56 dp: ${m.width} x ${m.height}", m.width >= 56 * px - 1 && m.height >= 56 * px - 1)
        assertTrue("mic is centred: centre ${m.center.x} of ${r.center.x}", abs(m.center.x - r.center.x) <= 2f)
        assertTrue("mic is at the bottom: bottom ${m.bottom} of ${r.bottom}", m.bottom <= r.bottom && m.bottom >= r.bottom - 24 * px && m.top > r.height * 0.7f)
        // Never over a result or the field.
        resultBounds().forEach { b -> assertFalse("a result $b overlaps the mic $m", b.overlaps(m)) }
        assertFalse("the field overlaps the mic", field().overlaps(m))
        // The mic is no longer in the field row.
        assertTrue("mic is below the field", m.top > field().bottom)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun INP_13_orbitMicIsAtTheBottomCentrePortraitDark() = assertMicAtBottomCentre(SearchLayout.Orbit, "")
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun INP_13_orbitMicIsAtTheBottomCentreLandscapeDark() = assertMicAtBottomCentre(SearchLayout.Orbit, "")
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-notnight-xhdpi") fun INP_13_orbitMicIsAtTheBottomCentrePortraitLight() = assertMicAtBottomCentre(SearchLayout.Orbit, "")
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun INP_13_orbitTypedMicIsAtTheBottomCentre() = assertMicAtBottomCentre(SearchLayout.Orbit, "br")
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun INP_13_filmstripMicIsAtTheBottomCentrePortrait() = assertMicAtBottomCentre(SearchLayout.Filmstrip, "")
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun INP_13_filmstripMicIsAtTheBottomCentreLandscape() = assertMicAtBottomCentre(SearchLayout.Filmstrip, "")
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun INP_13_flatMicIsAtTheBottomCentrePortrait() = assertMicAtBottomCentre(SearchLayout.Flat, "al")
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun INP_13_flatMicIsAtTheBottomCentreLandscape() = assertMicAtBottomCentre(SearchLayout.Flat, "al")
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun INP_13_micStaysCentredInRightToLeft() = assertMicAtBottomCentre(SearchLayout.Orbit, "", rtl = true)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun INP_13_noMicAndNoReservedSpaceWhenThereIsNoOnDeviceRecogniser() {
        open(SearchLayout.Flat, "al", voice = false)
        assertEquals(0, count("Speak to search"))
        // The list may use the bottom of the screen again.
        val lastRow = resultBounds().maxOf { it.bottom }
        assertTrue(lastRow > 0f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun INP_13_micIsLabelledAndFocusable() {
        open(SearchLayout.Orbit)
        rule.onNodeWithContentDescription("Speak to search").assertExists()
        val n = rule.onNodeWithContentDescription("Speak to search").fetchSemanticsNode()
        assertTrue("focusable", n.config.getOrNull(SemanticsProperties.Focused) != null)
        assertEquals(androidx.compose.ui.semantics.Role.Button, n.config.getOrNull(SemanticsProperties.Role))
    }

    // ---- (3) the X is inside the field

    private fun assertClearInsideField(layout: SearchLayout, rtl: Boolean) {
        open(layout, "al", rtl = rtl)
        val f = field()
        val c = bounds("Clear")
        assertTrue("the Clear X $c is inside the field $f", inside(c, f))
        assertTrue("Clear is a 48 dp target: ${c.width} x ${c.height}", c.width >= 48 * px - 1 && c.height >= 48 * px - 1)
        if (rtl) assertTrue("RTL: Clear at the left (trailing) end", c.center.x < f.center.x) else assertTrue("Clear at the right (trailing) end", c.center.x > f.center.x)
        assertEquals("Close and Clear are never both visible", 0, count("Close"))
        // The input text starts before the X on the reading side.
        val t = rule.onNode(androidx.compose.ui.test.hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        if (rtl) assertTrue(t.left >= c.right - 1f) else assertTrue(t.right <= c.left + 1f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_clearIsInsideTheFieldInOrbit() = assertClearInsideField(SearchLayout.Orbit, false)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_clearIsInsideTheFieldInFilmstrip() = assertClearInsideField(SearchLayout.Filmstrip, false)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_clearIsInsideTheFieldInFlat() = assertClearInsideField(SearchLayout.Flat, false)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_clearIsInsideTheFieldInLandscape() = assertClearInsideField(SearchLayout.Flat, false)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_clearMirrorsInRightToLeft() = assertClearInsideField(SearchLayout.Orbit, true)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-notnight-xhdpi") fun PAL_18_clearIsInsideTheFieldInLightTheme() = assertClearInsideField(SearchLayout.Orbit, false)

    private fun assertCloseOutside(layout: SearchLayout) {
        var closed = 0
        open(layout, "", onDismiss = { closed++ })
        assertEquals("no Clear without text", 0, count("Clear"))
        val c = bounds("Close"); val f = field()
        assertFalse("Close is outside the field", c.overlaps(f))
        assertTrue("Close is 48 dp: ${c.width} x ${c.height}", c.width >= 96f - 1 && c.height >= 96f - 1)
        assertTrue("a circle", abs(c.width - c.height) < 1f)
        rule.onNodeWithContentDescription("Close").performClick()
        assertEquals(1, closed)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_orbitWithNoTextHasNoClearAndCloseIsAGlassCircleOutsideTheField() = assertCloseOutside(SearchLayout.Orbit)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_filmstripWithNoTextHasNoClearAndCloseIsAGlassCircleOutsideTheField() = assertCloseOutside(SearchLayout.Filmstrip)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun PAL_18_clearEmptiesTheFieldAndCloseTakesItsPlace() {
        open(SearchLayout.Orbit, "bra")
        rule.onNodeWithContentDescription("Clear").performClick()
        rule.mainClock.advanceTimeBy(500)
        assertEquals(0, count("Clear"))
        assertEquals(1, count("Close"))
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun PAL_18_theScopeMenuSitsAtTheLeadingEndInsideTheFieldInFlat() {
        open(SearchLayout.Flat, "al")
        val s = bounds("Everything"); val f = field()
        assertTrue("the scope menu $s is inside the field $f", inside(s, f))
        assertTrue("at the leading (left) end", s.center.x < f.center.x)
        assertTrue("a 48 dp target: ${s.height}", s.height >= 48 * px - 1)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun PAL_18_theScopeMenuMirrorsInRightToLeft() {
        open(SearchLayout.Flat, "al", rtl = true)
        val s = bounds("Everything"); val f = field()
        assertTrue(inside(s, f)); assertTrue("at the leading (right) end", s.center.x > f.center.x)
    }

    // ---- glass chrome

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun NFR_13_everyTargetOfTheTopAndBottomBarsIs48dpOrMore() {
        open(SearchLayout.Flat, "al")
        for (d in listOf("Clear", "Speak to search", "Everything")) {
            val b = bounds(d)
            assertTrue("$d is ${b.width} x ${b.height} px", b.width >= 96f - 1 && b.height >= 96f - 1)
        }
    }

    // ---- keyboard open (fake IME and gesture-bar insets)

    private fun fakeInsets(imeBottomPx: Int, navBottomPx: Int) {
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            val w = androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(), androidx.core.graphics.Insets.of(0, 0, 0, imeBottomPx))
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars(), androidx.core.graphics.Insets.of(0, 0, 0, navBottomPx))
                .setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(), imeBottomPx > 0)
                .build()
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(view, w)
        }
        rule.mainClock.advanceTimeBy(500)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun INP_13_theMicRidesAboveTheKeyboardAndTheGestureBarAndStaysCentred() {
        open(SearchLayout.Orbit, "")
        val before = bounds("Speak to search"); val r0 = root()
        val ime = 600
        fakeInsets(ime, 48)
        val m = bounds("Speak to search"); val r = root()
        assertTrue("the mic moved up above the keyboard: before ${before.bottom}, now ${m.bottom}, keyboard top ${r0.bottom - ime}", m.bottom <= r0.bottom - ime + 0.5f && m.bottom > before.bottom - ime - 2f)
        assertTrue("still centred", abs(m.center.x - r.center.x) <= 2f)
        assertTrue("still 56 dp", m.width >= 56 * px - 1)
        resultBounds().forEach { b -> assertFalse("a result $b overlaps the mic $m", b.overlaps(m)) }
    }

    // ---- screenshots (read by a person; the numbers above are the assertions)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun shotOrbitPortrait() { open(SearchLayout.Orbit); shoot("w21-orbit-portrait-dark") }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun shotOrbitTypedPortrait() { open(SearchLayout.Orbit, "bra"); shoot("w21-orbit-typed-portrait-dark") }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun shotFlatTypedPortrait() { open(SearchLayout.Flat, "al"); shoot("w21-flat-typed-portrait-dark") }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun shotFilmstripPortrait() { open(SearchLayout.Filmstrip); shoot("w21-filmstrip-portrait-dark") }
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun shotOrbitLandscape() { open(SearchLayout.Orbit); shoot("w21-orbit-landscape-dark") }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-notnight-xhdpi") fun shotOrbitPortraitLight() { open(SearchLayout.Orbit, "bra"); shoot("w21-orbit-typed-portrait-light") }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun shotFlatRtl() { open(SearchLayout.Flat, "al", rtl = true); shoot("w21-flat-typed-rtl") }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun shotBeamBackground() { open(SearchLayout.Orbit, "", bg = BackgroundChoice(BgItem.Beam, BgItem.GlowLines)); shoot("w21-orbit-beam-portrait-dark") }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun shotSpireBackground() { open(SearchLayout.Orbit, "", bg = BackgroundChoice(BgItem.Spire, BgItem.BreathingDots)); shoot("w21-orbit-spire-portrait-dark") }
}
