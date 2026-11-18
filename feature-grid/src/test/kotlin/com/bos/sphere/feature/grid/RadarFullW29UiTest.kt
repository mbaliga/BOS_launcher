package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
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
 * Wave 29 (radarFull, owner: "the rotating rings and icons are cropped in portrait and landscape" and "an error with the touch area that works"): the Orbit's layer is the
 * whole display area, nothing is cut by a straight line inside the screen, and every visible part of a tile takes the tap. Real SearchScreen, real pointer injection.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RadarFullW29UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = java.io.File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    @Before fun english() { com.bos.sphere.core.design.L10n.setLanguage("en") }

    private val names = List(44) { "Clau%02d".format(it) }
    private val apps = names.map { n -> AppEntry("demo.$n", ComponentName("demo.$n", "demo.$n.Main"), n, Process.myUserHandle()) { null } }
    private var dismissed = 0
    private var launched = 0

    private fun open(query: String, motion: Boolean, layout: SearchLayout = SearchLayout.Orbit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SearchScreen(
                apps = apps, recent = apps, prefs = LauncherPrefs(searchMotion = motion, searchLayout = layout, searchLayoutPinned = true), contacts = null,
                onLaunch = { launched++ }, onLongPressApp = { launched++ }, onOpenIntent = { launched++ }, onRequestContactsPermission = {}, onDismiss = { dismissed++ },
                hub = null, onOpenHit = null, onRunAction = {}, onRequestFilePermission = {}, sounds = null, initialQuery = query,
                launchHistory = RadarTestHabits.history(apps), learnedOverride = emptyMap(), nowMillis = { RadarTestHabits.NOW }, voiceAvailable = true,
            )
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

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(900)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        java.io.File(outDir, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun inOrbit(n: SemanticsNode): Boolean { var p: SemanticsNode? = n; while (p != null) { if (p.config.contains(androidx.compose.ui.semantics.SemanticsProperties.TestTag) && p.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] == RADAR_ORBIT_TAG) return true; p = p.parent }; return false }

    /** Everything that takes a touch and is not a tile of the rings: the field, the Close/Clear button, the mic, the strip circles, the wheel's labels and the "SEARCH" word. */
    private fun overlayRects(): List<Rect> = rule.onAllNodes(SemanticsMatcher("an overlay control") { n ->
        !inOrbit(n) && (n.config.contains(SemanticsActions.OnClick) || n.config.contains(SemanticsActions.SetText) || n.config.contains(SemanticsActions.OnLongClick))
    }).fetchSemanticsNodes().map { it.boundsInRoot } + rule.onNodeWithTag(RADAR_FIELD_TAG).fetchSemanticsNode().boundsInRoot +
        rule.onAllNodes(hasTestTag(RADAR_WHEEL_TAG)).fetchSemanticsNodes().map { it.boundsInRoot }

    /** The tiles' own rectangles in the screen's pixels, not clipped to the screen (a tile half off the edge keeps its real centre). */
    private fun tiles() = rule.onAllNodes(hasTestTag(RADAR_ORBIT_TILE_TAG)).fetchSemanticsNodes().map { n -> Rect(n.positionInRoot, androidx.compose.ui.geometry.Size(n.size.width.toFloat(), n.size.height.toFloat())) }

    private fun checkAll(query: String, keyboard: Float) {
        open(query, motion = true)
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        if (keyboard > 0f) fakeInsets((root.height * keyboard).toInt())
        rule.mainClock.advanceTimeBy(2_500)
        val d = rule.activity.resources.displayMetrics.density
        val imeTop = root.bottom - root.height * keyboard
        val where = "${root.width / d}x${root.height / d}dp keyboard=$keyboard query='$query'"
        // (a) the Orbit's layer is the whole display area: from the top edge to the keyboard (or the bottom edge), edge to edge; no smaller rectangle clips it.
        val orbit = rule.onNodeWithTag(RADAR_ORBIT_TAG).fetchSemanticsNode().boundsInRoot
        assertEquals("the Orbit layer starts at the top edge: $where", root.top, orbit.top, 1f)
        assertEquals("the Orbit layer starts at the left edge: $where", root.left, orbit.left, 1f)
        assertEquals("the Orbit layer ends at the right edge: $where", root.right, orbit.right, 1f)
        assertEquals("the Orbit layer ends at the keyboard (bottom edge when it is down): $where", imeTop, orbit.bottom, 2f)
        val overlays = overlayRects()
        // The rings creep while the clock runs, so the tiles' rectangles are read again right before every tap.
        fun freshTiles() = tiles()
        val faceR = 27f * d
        fun face(b: Rect) = Offset(b.center.x, b.top + faceR)
        val problems = ArrayList<String>()
        // (b) 40 points across the display area: a tile under the point takes the tap, an overlay control keeps its own, and empty space does the documented thing.
        for (iy in 0 until 5) for (ix in 0 until 8) {
            val p = Offset(orbit.left + orbit.width * (ix + 0.5f) / 8f, orbit.top + orbit.height * (iy + 0.5f) / 5f)
            if (overlays.any { it.contains(p) }) continue
            val now = freshTiles()
            val onFace = now.any { (face(it) - p).getDistance() <= faceR - 6f * d }
            val onTileBody = now.any { it.contains(p) }
            val near = now.any { (face(it) - p).getDistance() <= faceR + 6f * d } // a tile creeping under the point while the tap lands is not a verdict either way
            val l0 = launched; val d0 = dismissed
            rule.onAllNodes(isRoot()).onFirst().performTouchInput { click(p) }
            rule.mainClock.advanceTimeBy(50)
            when {
                onFace -> if (launched != l0 + 1) problems += "tile face at ${p} did not take the tap"
                onTileBody || near -> Unit // the caption and the gaps beside a face: not asserted (the face is the control)
                query.isEmpty() -> if (dismissed != d0 + 1 || launched != l0) problems += "empty space at ${p} did not close Radar"
                else -> if (dismissed != d0 || launched != l0) problems += "empty space at ${p} did something else than hiding the keyboard"
            }
        }
        // (c) every tile whose face is partly visible (under the field, the mic, beside the wheel) takes the tap on its visible part.
        var checked = 0
        for (idx in freshTiles().indices) {
            val offsets = listOf(Offset(0f, 0f), Offset(-14f, 0f), Offset(14f, 0f), Offset(0f, -14f), Offset(0f, 14f))
            for (o in offsets) {
                val b = freshTiles().getOrNull(idx) ?: continue
                val p = face(b) + o * d
                if (p.x < orbit.left + 2f || p.x > orbit.right - 2f || p.y < orbit.top + 2f || p.y > orbit.bottom - 2f) continue
                if (overlays.any { it.contains(p) }) continue
                val l0 = launched
                rule.onAllNodes(isRoot()).onFirst().performTouchInput { click(p) }
                rule.mainClock.advanceTimeBy(50)
                if (launched != l0 + 1) problems += "a visible part of a tile at $p (tile $b) is dead; others=${freshTiles().filter { it.contains(p) }} overlays=${overlayRects().filter { it.contains(p) }} all=${overlayRects()} wheel=${rule.onAllNodes(hasTestTag(RADAR_WHEEL_TAG)).fetchSemanticsNodes().map { it.boundsInRoot }} launchedDelta=${launched - l0} dismissedNow=$dismissed"
                checked++
            }
        }
        assertTrue("some tiles were checked ($checked): $where", checked > 0)
        assertTrue("$where: ${problems.take(8)}", problems.isEmpty())
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_w29_orbitLayerIsTheWholeDisplayAndEveryPointHasAnOwner_360x640_keyboardClosed_queryEmpty() = checkAll("", 0.0f)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_w29_orbitLayerIsTheWholeDisplayAndEveryPointHasAnOwner_360x640_keyboardClosed_queryTyped() = checkAll("clau", 0.0f)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_w29_orbitLayerIsTheWholeDisplayAndEveryPointHasAnOwner_360x640_keyboard40_queryEmpty() = checkAll("", 0.4f)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_w29_orbitLayerIsTheWholeDisplayAndEveryPointHasAnOwner_360x640_keyboard40_queryTyped() = checkAll("clau", 0.4f)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_w29_orbitLayerIsTheWholeDisplayAndEveryPointHasAnOwner_411x891_keyboardClosed_queryEmpty() = checkAll("", 0.0f)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_w29_orbitLayerIsTheWholeDisplayAndEveryPointHasAnOwner_411x891_keyboardClosed_queryTyped() = checkAll("clau", 0.0f)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_w29_orbitLayerIsTheWholeDisplayAndEveryPointHasAnOwner_411x891_keyboard40_queryEmpty() = checkAll("", 0.4f)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_w29_orbitLayerIsTheWholeDisplayAndEveryPointHasAnOwner_411x891_keyboard40_queryTyped() = checkAll("clau", 0.4f)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_w29_orbitLayerIsTheWholeDisplayAndEveryPointHasAnOwner_891x411_keyboardClosed_queryEmpty() = checkAll("", 0.0f)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_w29_orbitLayerIsTheWholeDisplayAndEveryPointHasAnOwner_891x411_keyboardClosed_queryTyped() = checkAll("clau", 0.0f)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_w29_orbitLayerIsTheWholeDisplayAndEveryPointHasAnOwner_891x411_keyboard40_queryEmpty() = checkAll("", 0.4f)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_w29_orbitLayerIsTheWholeDisplayAndEveryPointHasAnOwner_891x411_keyboard40_queryTyped() = checkAll("clau", 0.4f)
    @Test @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi") fun PAL_18_w29_orbitLayerIsTheWholeDisplayAndEveryPointHasAnOwner_1280x800_keyboardClosed_queryEmpty() = checkAll("", 0.0f)
    @Test @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi") fun PAL_18_w29_orbitLayerIsTheWholeDisplayAndEveryPointHasAnOwner_1280x800_keyboardClosed_queryTyped() = checkAll("clau", 0.0f)
    @Test @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi") fun PAL_18_w29_orbitLayerIsTheWholeDisplayAndEveryPointHasAnOwner_1280x800_keyboard40_queryEmpty() = checkAll("", 0.4f)
    @Test @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi") fun PAL_18_w29_orbitLayerIsTheWholeDisplayAndEveryPointHasAnOwner_1280x800_keyboard40_queryTyped() = checkAll("clau", 0.4f)

    /** With the rings turning, tiles cross the band under the field and the band above the mic: they are drawn there (not cut at a line) and take taps. */
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun PAL_18_w29_tilesRevolveThroughTheBandsUnderTheFieldAndAboveTheMic() {
        open("", motion = true)
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val d = rule.activity.resources.displayMetrics.density
        val field = rule.onNodeWithTag(RADAR_FIELD_TAG).fetchSemanticsNode().boundsInRoot
        val mic = rule.onNodeWithTag(RADAR_MIC_TAG).fetchSemanticsNode().boundsInRoot
        var under = false; var aboveMic = false; var beyondMic = false
        repeat(8) {
            rule.mainClock.advanceTimeBy(2_000)
            tiles().forEach { t ->
                if (t.top < field.bottom && t.bottom > field.top && t.bottom > 0f) under = true
                if (t.bottom > mic.top - 10f * d && t.top < mic.top) aboveMic = true
                if (t.top >= mic.top && t.top < root.bottom) beyondMic = true
            }
        }
        assertTrue("a tile passes under the field", under)
        assertTrue("a tile passes just above the mic", aboveMic)
        assertTrue("a tile passes the mic's top line and keeps going down to the bottom edge", beyondMic)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_w29_portraitEmptyShot() { open("", true); shoot("w29-radar-portrait-empty") }
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_18_w29_portraitTypedShot() { open("clau", true); shoot("w29-radar-portrait-typed") }
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_w29_landscapeEmptyShot() { open("", true); shoot("w29-radar-landscape-empty") }
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_18_w29_landscapeTypedShot() { open("clau", true); shoot("w29-radar-landscape-typed") }

    /** The typed lists scroll under the field and above the mic: Filmstrip and Flat have no clipped box, the list fills the area. */
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun PAL_18_w29_theFlatListFillsTheAreaAndScrollsUnderTheField() {
        open("clau", false, SearchLayout.Flat)
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val list = rule.onAllNodes(SemanticsMatcher("the lazy list") { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange) }).fetchSemanticsNodes().first().boundsInRoot
        assertEquals("the list starts at the top edge", root.top, list.top, 1f)
        assertTrue("the list reaches the mic's strip and below", list.bottom >= root.bottom - 1f)
    }
}
