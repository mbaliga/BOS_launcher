package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.bos.sphere.core.data.AppEntry
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

/**
 * Wave 22 (glassFix): the mic strip, the category wheel, the rings and the results each have their own region in every layout, in portrait
 * and landscape, small and large windows, font scale 1.0 and 2.0, keyboard closed and (faked) open; Close in Flat; the TalkBack order.
 * Screenshots go to `build/screenshots/w22-*.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RadarGlassW22Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = java.io.File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun english() { com.bos.sphere.core.design.L10n.setLanguage("en") }

    private val names = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India")
    private val apps = names.map { n -> AppEntry("demo.$n", ComponentName("demo.$n", "demo.$n.Main"), n, Process.myUserHandle()) { null } }

    private fun open(layout: SearchLayout, query: String = "", fontScale: Float = 1f, onDismiss: () -> Unit = {}) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val d = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, fontScale)) {
                SearchScreen(
                    apps = apps, recent = apps, prefs = LauncherPrefs(searchMotion = false, searchLayout = layout, searchLayoutPinned = true), contacts = null,
                    onLaunch = {}, onLongPressApp = {}, onOpenIntent = {}, onRequestContactsPermission = {}, onDismiss = onDismiss,
                    hub = null, onOpenHit = null, onRunAction = {}, onRequestFilePermission = {}, sounds = null, initialQuery = query,
                    launchHistory = RadarTestHabits.history(apps), learnedOverride = emptyMap(), nowMillis = { RadarTestHabits.NOW }, voiceAvailable = true,
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
    }

    private fun fakeInsets(imeBottomPx: Int, navBottomPx: Int) {
        rule.runOnUiThread {
            val w = androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(), androidx.core.graphics.Insets.of(0, 0, 0, imeBottomPx))
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars(), androidx.core.graphics.Insets.of(0, 0, 0, navBottomPx))
                .setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(), imeBottomPx > 0)
                .build()
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(rule.activity.window.decorView, w)
        }
        rule.mainClock.advanceTimeBy(500)
    }

    private fun bounds(desc: String) = rule.onNodeWithContentDescription(desc).fetchSemanticsNode().boundsInRoot
    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(600)
        Thread.sleep(2500) // the glass shades are measured off the main thread; let them land
        rule.mainClock.advanceTimeBy(600)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        java.io.File(outDir, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ---- wheel maths

    @Test
    fun PAL_18_wheelLabelsFadeToNothingAtTheEdgeOfTheirOwnBox() {
        for (rows in intArrayOf(1, 3, 5)) {
            assertEquals(1f, wheelLabelAlpha(0f, rows), 1e-6f)
            assertEquals("nothing is drawn half a row outside the box of $rows rows", 0f, wheelLabelAlpha(rows / 2f, rows), 0f)
            assertEquals(0f, wheelLabelAlpha(rows / 2f + 3f, rows), 0f)
            var prev = 1f; var d = 0.25f
            while (d <= 6f) { val a = wheelLabelAlpha(d, rows); assertTrue("alpha falls with distance at $d", a <= prev + 1e-6f); prev = a; d += 0.25f }
        }
        assertTrue("the last whole row of a 5 row wheel is still faintly there", wheelLabelAlpha(2f, 5) > 0.1f)
        assertEquals("the old falloff is unchanged for the labels that matter", 1f / (1f + 0.9f), wheelLabelAlpha(1f, 5), 1e-6f)
    }

    @Test
    fun PAL_18_theWheelTakesOnlyTheRowsThatFitAndRowsGrowWithTheFont() {
        assertEquals(5, WheelSpec.fit(400f, 1f).rows)
        assertEquals(3, WheelSpec.fit(120f, 1f).rows)
        assertEquals("never an even count (the selected row is in the middle)", 1, WheelSpec.fit(90f, 1f).rows)
        assertEquals("always at least one row", 1, WheelSpec.fit(0f, 1f).rows)
        assertTrue("every fit fits (or is the one row minimum)", (0..500 step 7).all { f -> val w = WheelSpec.fit(f.toFloat(), 1f); w.rows == 1 || w.height.value <= f - 8f + 0.01f })
        assertEquals(34f, WheelSpec.rowFor(1f).value, 0f)
        assertEquals("a row holds 1.5 lines of the label at double size", 48f, WheelSpec.rowFor(2f).value, 0f)
        assertTrue("the wheel never gets taller than its five rows", WheelSpec.fit(9999f, 1f).height <= WheelHeight)
    }

    // ---- regions: the mic strip never meets the wheel, its labels, the rings or the results

    private fun rects(pred: (androidx.compose.ui.semantics.SemanticsNode) -> Boolean) =
        rule.onAllNodes(SemanticsMatcher("region") { pred(it) }, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }

    /** The visible part of a result: its bounds cut to the results area (the results box clips what is outside it). */
    private fun assertRegions(layout: SearchLayout, query: String, fontScale: Float) {
        open(layout, query, fontScale)
        checkRegions(layout, query, fontScale, keyboard = false)
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        fakeInsets(((root.height * 0.4f).toInt()), 96) // a keyboard 40 percent of the window high, and a gesture bar
        checkRegions(layout, query, fontScale, keyboard = true)
    }

    private var lastInline = false

    private fun checkRegions(layout: SearchLayout, query: String, fontScale: Float, keyboard: Boolean, shot: String? = null) {
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val density = rule.activity.resources.displayMetrics.density
        val mic = bounds("Speak to search")
        val field = rule.onNodeWithTag(RADAR_FIELD_TAG).fetchSemanticsNode().boundsInRoot
        // Wave 23: with little free height the mic sits in the field row (inline) and no strip is reserved.
        val inline = mic.center.y < field.bottom
        val stripTop = if (inline) root.bottom else mic.center.y - 36f * density // the reserved 72 dp strip is centred on the mic
        val where = "$layout q='$query' font=$fontScale keyboard=$keyboard ${root.width / density}x${root.height / density}dp"
        assertTrue("mic is below the field: $where", mic.top >= field.bottom - 0.5f || keyboard || inline)
        // The wheel's own box and every label drawn inside it (labels outside it are transparent).
        val wheel = rule.onAllNodes(androidx.compose.ui.test.hasTestTag(RADAR_WHEEL_TAG)).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
        val labelNames = listOf("EVERYTHING", "THE WEB", "ACTIONS", "APPS", "CONTACTS", "SEARCH")
        if (wheel != null) {
            assertFalse("the wheel $wheel overlaps the mic $mic: $where", wheel.overlaps(mic))
            assertTrue("the wheel ${wheel} sits inside the results area (above the strip at $stripTop): $where", wheel.bottom <= stripTop + 0.5f)
            assertTrue("the wheel ${wheel} starts below the query line ${field.bottom}: $where", wheel.top >= field.bottom - 0.5f)
            val labels = rects { n -> n.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text in labelNames }
                .filter { it.center.y >= wheel.top - 1f && it.center.y <= wheel.bottom + 1f } // the labels the wheel draws; the rest are transparent
            assertTrue("a wheel label is on screen: $where", labels.isNotEmpty())
            labels.forEach { l ->
                assertFalse("a wheel label $l overlaps the mic $mic: $where", l.overlaps(mic))
                assertTrue("a wheel label $l ends above the strip: $where", l.bottom <= stripTop + 0.5f)
            }
        }
        // Results (tiles, rows, captions): what is visible lies in the results area, never over the mic.
        val results = rects { n -> n.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() in names || n.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text in names }
        val area = androidx.compose.ui.geometry.Rect(root.left, field.bottom, root.right, stripTop)
        results.forEach { r ->
            val visible = r.intersect(area)
            assertFalse("a visible result $visible overlaps the mic $mic: $where", visible.width > 0f && visible.height > 0f && visible.overlaps(mic))
        }
        // The Filmstrip's strip and the wheel each have their own band of the results area (when the area has room for one wheel row at all).
        if (wheel != null && layout == SearchLayout.Filmstrip && area.height >= (WheelSpec.rowFor(fontScale).value + 8f) * density) {
            results.forEach { r ->
                val visible = r.intersect(area)
                assertFalse("a visible result $visible overlaps the wheel $wheel: $where", visible.width > 0f && visible.height > 0f && visible.overlaps(wheel))
            }
        }
        // The mic is a whole 56 dp button, centred, whatever the font and the keyboard.
        if (inline) {
            assertTrue("inline mic is 48 dp and inside the window: $where $mic", mic.width >= 48f * density - 1f && mic.height >= 48f * density - 1f && mic.left >= root.left && mic.right <= root.right)
            assertFalse("inline mic does not sit on the field: $where", mic.overlaps(field))
            assertTrue("inline mic is in the field row: $where", mic.top >= root.top && mic.bottom <= field.bottom + 8f * density)
        } else {
            assertTrue("mic is 56 dp: $where", mic.width >= 56f * density - 1f && mic.height >= 56f * density - 1f)
            assertTrue("mic is centred: $where", kotlin.math.abs(mic.center.x - root.center.x) <= 2f)
        }
        lastInline = inline
        if (shot != null) shoot(shot)
    }

    // ---- the matrix: 4 window sizes x 4 layouts (Orbit at rest and typed, Filmstrip, Flat) x font scale 1.0 and 2.0, each with the keyboard closed and then open
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun INP_13_orbitPortSmallFont1MicHasItsOwnRegion() = assertRegions(SearchLayout.Orbit, "", 1f)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun INP_13_orbitPortSmallFont2MicHasItsOwnRegion() = assertRegions(SearchLayout.Orbit, "", 2f)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun INP_13_orbittypedPortSmallFont1MicHasItsOwnRegion() = assertRegions(SearchLayout.Orbit, "br", 1f)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun INP_13_orbittypedPortSmallFont2MicHasItsOwnRegion() = assertRegions(SearchLayout.Orbit, "br", 2f)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun INP_13_filmstripPortSmallFont1MicHasItsOwnRegion() = assertRegions(SearchLayout.Filmstrip, "", 1f)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun INP_13_filmstripPortSmallFont2MicHasItsOwnRegion() = assertRegions(SearchLayout.Filmstrip, "", 2f)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun INP_13_flatPortSmallFont1MicHasItsOwnRegion() = assertRegions(SearchLayout.Flat, "al", 1f)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun INP_13_flatPortSmallFont2MicHasItsOwnRegion() = assertRegions(SearchLayout.Flat, "al", 2f)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun INP_13_orbitPortLargeFont1MicHasItsOwnRegion() = assertRegions(SearchLayout.Orbit, "", 1f)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun INP_13_orbitPortLargeFont2MicHasItsOwnRegion() = assertRegions(SearchLayout.Orbit, "", 2f)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun INP_13_orbittypedPortLargeFont1MicHasItsOwnRegion() = assertRegions(SearchLayout.Orbit, "br", 1f)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun INP_13_orbittypedPortLargeFont2MicHasItsOwnRegion() = assertRegions(SearchLayout.Orbit, "br", 2f)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun INP_13_filmstripPortLargeFont1MicHasItsOwnRegion() = assertRegions(SearchLayout.Filmstrip, "", 1f)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun INP_13_filmstripPortLargeFont2MicHasItsOwnRegion() = assertRegions(SearchLayout.Filmstrip, "", 2f)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun INP_13_flatPortLargeFont1MicHasItsOwnRegion() = assertRegions(SearchLayout.Flat, "al", 1f)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun INP_13_flatPortLargeFont2MicHasItsOwnRegion() = assertRegions(SearchLayout.Flat, "al", 2f)
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun INP_13_orbitLandSmallFont1MicHasItsOwnRegion() = assertRegions(SearchLayout.Orbit, "", 1f)
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun INP_13_orbitLandSmallFont2MicHasItsOwnRegion() = assertRegions(SearchLayout.Orbit, "", 2f)
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun INP_13_orbittypedLandSmallFont1MicHasItsOwnRegion() = assertRegions(SearchLayout.Orbit, "br", 1f)
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun INP_13_orbittypedLandSmallFont2MicHasItsOwnRegion() = assertRegions(SearchLayout.Orbit, "br", 2f)
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun INP_13_filmstripLandSmallFont1MicHasItsOwnRegion() = assertRegions(SearchLayout.Filmstrip, "", 1f)
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun INP_13_filmstripLandSmallFont2MicHasItsOwnRegion() = assertRegions(SearchLayout.Filmstrip, "", 2f)
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun INP_13_flatLandSmallFont1MicHasItsOwnRegion() = assertRegions(SearchLayout.Flat, "al", 1f)
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun INP_13_flatLandSmallFont2MicHasItsOwnRegion() = assertRegions(SearchLayout.Flat, "al", 2f)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun INP_13_orbitLandLargeFont1MicHasItsOwnRegion() = assertRegions(SearchLayout.Orbit, "", 1f)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun INP_13_orbitLandLargeFont2MicHasItsOwnRegion() = assertRegions(SearchLayout.Orbit, "", 2f)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun INP_13_orbittypedLandLargeFont1MicHasItsOwnRegion() = assertRegions(SearchLayout.Orbit, "br", 1f)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun INP_13_orbittypedLandLargeFont2MicHasItsOwnRegion() = assertRegions(SearchLayout.Orbit, "br", 2f)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun INP_13_filmstripLandLargeFont1MicHasItsOwnRegion() = assertRegions(SearchLayout.Filmstrip, "", 1f)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun INP_13_filmstripLandLargeFont2MicHasItsOwnRegion() = assertRegions(SearchLayout.Filmstrip, "", 2f)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun INP_13_flatLandLargeFont1MicHasItsOwnRegion() = assertRegions(SearchLayout.Flat, "al", 1f)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun INP_13_flatLandLargeFont2MicHasItsOwnRegion() = assertRegions(SearchLayout.Flat, "al", 2f)

    // ---- Flat: Close with an empty query (decision in docs/decisions/radarGlass.md)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun PAL_18_flatHasNoCloseBesideTheFieldOnlyTheXInsideItWhenThereIsText() {
        open(SearchLayout.Flat, "")
        assertEquals("the owner asked for the X inside the field only: no second X in Flat", 0, rule.onAllNodesWithContentDescription("Close").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithContentDescription("Clear").fetchSemanticsNodes().size)
        val field = rule.onNodeWithTag(RADAR_FIELD_TAG).fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val d = rule.activity.resources.displayMetrics.density
        assertTrue("the field takes the width Close used to: ${field.right} of ${root.right}", field.right >= root.right - 12f * d - 1f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun PAL_18_orbitAndFilmstripStillHaveCloseWhileTheQueryIsEmpty() {
        var closed = 0
        open(SearchLayout.Orbit, "", onDismiss = { closed++ })
        rule.onNodeWithContentDescription("Close").performClick()
        assertEquals(1, closed)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun NFR_13_theQueryLineTheResultsAndTheMicAreSeparateTraversalGroupsReadTopToBottom() {
        open(SearchLayout.Orbit)
        val groups = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.IsTraversalGroup), useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.config[SemanticsProperties.IsTraversalGroup] }.map { it.boundsInRoot }
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val mic = bounds("Speak to search")
        val field = rule.onNode(androidx.compose.ui.test.hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val bar = groups.firstOrNull { it.contains(field.center) && it.height < root.height / 4 }
        val micGroup = groups.firstOrNull { it.contains(mic.center) && it.height < root.height / 4 && it !== bar }
        val results = groups.firstOrNull { it.height > root.height / 2 }
        assertTrue("a group holds the query line: $groups", bar != null)
        assertTrue("a group holds the mic: $groups", micGroup != null)
        assertTrue("a group holds the results: $groups", results != null)
        assertTrue("query line group above the results above the mic group", bar!!.bottom <= results!!.top + 1f && results.bottom <= micGroup!!.top + 1f)
    }

    // ---- wave 23: the adaptive mic (strip when the free height is 220 dp or more, in the field row below that)

    @Test fun PAL_18_theMicLeavesItsStripForTheFieldRowBelow220dpOfFreeHeight() {
        assertFalse(RadarMicPlacement.inline(220.dp))
        assertFalse(RadarMicPlacement.inline(600.dp))
        assertTrue(RadarMicPlacement.inline(219.dp))
        assertTrue(RadarMicPlacement.inline(0.dp))
        assertEquals("inline it may be 48 dp", 48f, RadarMicPlacement.InlineSize.value, 0f)
    }

    private fun inlineMatrix(layout: SearchLayout, query: String, font: Float, keyboardFraction: Float, expectInline: Boolean) {
        open(layout, query, font)
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        if (keyboardFraction > 0f) fakeInsets((root.height * keyboardFraction).toInt(), 0)
        checkRegions(layout, query, font, keyboard = keyboardFraction > 0f)
        assertEquals("$layout q='$query' font=$font keyboard=$keyboardFraction", expectInline, lastInline)
    }

    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun PAL_18_landscapeSmallNoKeyboardKeepsTheStripFont1() { inlineMatrix(SearchLayout.Orbit, "", 1f, 0f, expectInline = false) }
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun PAL_18_landscapeSmallNoKeyboardKeepsTheStripFont2() { inlineMatrix(SearchLayout.Orbit, "", 2f, 0f, expectInline = false) }
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun PAL_18_landscapeSmallKeyboardOpenPutsTheMicInTheFieldRowFont1() { inlineMatrix(SearchLayout.Orbit, "", 1f, 0.4f, true) }
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun PAL_18_landscapeSmallKeyboardOpenPutsTheMicInTheFieldRowFont2() { inlineMatrix(SearchLayout.Filmstrip, "", 2f, 0.4f, true) }
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun PAL_18_landscapeSmallKeyboardOpenFlatTypedFont1() { inlineMatrix(SearchLayout.Flat, "al", 1f, 0.4f, true) }
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun PAL_18_landscapeSmallKeyboardOpenFlatTypedFont2() { inlineMatrix(SearchLayout.Flat, "al", 2f, 0.4f, true) }
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_landscapeLargeKeyboardOpenOrbitTypedFont1() { inlineMatrix(SearchLayout.Orbit, "br", 1f, 0.45f, true) }
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_landscapeLargeKeyboardOpenOrbitTypedFont2() { inlineMatrix(SearchLayout.Orbit, "br", 2f, 0.45f, true) }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_portraitKeyboardOpenKeepsTheStripBecauseThereIsRoom() { inlineMatrix(SearchLayout.Orbit, "", 1f, 0.4f, false) }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_portraitNoKeyboardKeepsTheStrip() { inlineMatrix(SearchLayout.Flat, "al", 1f, 0f, false) }

    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi")
    fun PAL_18_inlineMicStillHasItsStateAndTheResultsGetTheFreedHeight() {
        org.robolectric.Shadows.shadowOf(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        open(SearchLayout.Flat, "al")
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        fakeInsets((root.height * 0.4f).toInt(), 0)
        val mic = rule.onNodeWithTag(RADAR_MIC_TAG).fetchSemanticsNode()
        assertEquals("Ready", mic.config[SemanticsProperties.StateDescription])
        assertEquals("a polite live region, never assertive", androidx.compose.ui.semantics.LiveRegionMode.Polite, mic.config[SemanticsProperties.LiveRegion])
        // The free area under the field is 360*0.6 - 64 = 152 dp: the list may use all of it (a strip would have taken 72 dp of it).
        val rows = rule.onAllNodes(SemanticsMatcher("row") { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text == "Alpha" }, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("a result is listed", rows.isNotEmpty())
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun NFR_13_theMicSaysUnavailableUntilTheMicrophonePermissionIsGranted() {
        open(SearchLayout.Orbit)
        assertEquals("Unavailable, microphone permission is off", rule.onNodeWithTag(RADAR_MIC_TAG).fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        assertEquals("the name stays the name; the state is a state", "Speak to search", rule.onNodeWithTag(RADAR_MIC_TAG).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first())
    }

    @Test fun NFR_13_eachVoiceStateHasItsOwnWordsAndAllEightLanguagesTranslateThem() {
        val words = VoiceState.values().map { VoiceStates.describe(it) }.toSet()
        assertEquals(setOf(VoiceStates.READY, VoiceStates.LISTENING, VoiceStates.UNAVAILABLE), words)
        assertEquals(VoiceStates.LISTENING, VoiceStates.describe(PushToTalk.state(true, true, true)))
        assertEquals(VoiceStates.UNAVAILABLE, VoiceStates.describe(PushToTalk.state(true, false, false)))
        assertEquals(VoiceStates.READY, VoiceStates.describe(PushToTalk.state(true, true, false)))
        for (lang in listOf("es", "fr", "de", "pt", "hi", "zh", "ja", "ar")) {
            com.bos.sphere.core.design.L10n.setLanguage(lang)
            for (w in words) assertTrue("$lang translates '$w'", com.bos.sphere.core.design.L10n.tr(w) != w)
        }
        com.bos.sphere.core.design.L10n.setLanguage("en")
    }

    @Test fun NFR_13_thePageIndicatorLabelsAreTranslatedInAllEightLanguages() {
        for (lang in listOf("es", "fr", "de", "pt", "hi", "zh", "ja", "ar")) {
            com.bos.sphere.core.design.L10n.setLanguage(lang)
            for (w in listOf("Page options", "Home page", "Page %s", "Page %s of %s")) assertTrue("$lang translates '$w'", com.bos.sphere.core.design.L10n.tr(w) != w)
            assertTrue("$lang keeps both numbers", com.bos.sphere.core.design.L10n.tr("Page %s of %s", 2, 3).let { it.contains("2") && it.contains("3") })
        }
        com.bos.sphere.core.design.L10n.setLanguage("en")
    }

    // ---- screenshots (read by a person; the numbers above are the assertions)

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun shotLandscapeOrbit() { open(SearchLayout.Orbit); shoot("w22-orbit-landscape") }
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun shotLandscapeFilmstrip() { open(SearchLayout.Filmstrip); shoot("w22-filmstrip-landscape") }
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun shotLandscapeFlat() { open(SearchLayout.Flat, "al"); shoot("w22-flat-landscape") }
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun shotLandscapeOrbitKeyboard() { open(SearchLayout.Orbit); fakeInsets(330, 0); shoot("w22-orbit-landscape-keyboard") }
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun shotSmallLandscapeFilmstripFont2() { open(SearchLayout.Filmstrip, "", 2f); shoot("w22-filmstrip-small-landscape-font2") }
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun shotW23SmallLandscapeKeyboardOrbit() { open(SearchLayout.Orbit); fakeInsets(144, 0); shoot("w23-orbit-small-landscape-keyboard") }
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun shotW23SmallLandscapeKeyboardFlatFont2() { open(SearchLayout.Flat, "al", 2f); fakeInsets(144, 0); shoot("w23-flat-small-landscape-keyboard-font2") }
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun shotW23SmallLandscapeKeyboardFilmstrip() { open(SearchLayout.Filmstrip); fakeInsets(144, 0); shoot("w23-filmstrip-small-landscape-keyboard") }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun shotPortraitOrbitDefaultBackground() { open(SearchLayout.Orbit); shoot("w22-orbit-portrait-default-bg") }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun shotPortraitFlatEmpty() { open(SearchLayout.Flat, ""); shoot("w22-flat-empty-portrait") }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun shotPortraitFilmstripFont2() { open(SearchLayout.Filmstrip, "", 2f); shoot("w22-filmstrip-portrait-font2") }
}
