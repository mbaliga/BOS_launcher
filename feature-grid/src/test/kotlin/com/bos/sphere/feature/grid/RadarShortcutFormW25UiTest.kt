package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HitKind
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.SearchCategory
import com.bos.sphere.core.data.SearchHit
import com.bos.sphere.core.data.SearchHub
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
 * Wave 25 (shortcutForm, owner: "some shortcuts appear as circles around the radar circles, others as pills below the icon"): ONE form. A result is an app tile
 * (icon circle and name), an app's shortcuts are one row of 40 dp circles under it (48 dp slots), and every action that is not tied to an app sits on the strip, as the
 * same circles, never on the rings. The query of the owner's screenshot: "fylz" with an app that has four shortcuts and a "Deep results" action.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RadarShortcutFormW25UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = java.io.File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private val originalIcons = ShortcutIcons.source
    @Before fun english() {
        com.bos.sphere.core.design.L10n.setLanguage("en")
        ShortcutIcons.clear()
        // Tools and Activity carry their own icon, the others have none and fall back to the app's icon (never a gap).
        ShortcutIcons.source = ShortcutIconSource { _, _, id, _, _ -> if (id == "tools") android.graphics.drawable.ColorDrawable(0xFFE0A030.toInt()) else if (id == "activity") android.graphics.drawable.ColorDrawable(0xFF4FA3E0.toInt()) else null }
    }
    @org.junit.After fun restoreIcons() { ShortcutIcons.source = originalIcons; ShortcutIcons.clear() }

    private fun app(pkg: String, label: String) = AppEntry(pkg, ComponentName(pkg, "$pkg.Main"), label, Process.myUserHandle()) { android.graphics.drawable.ColorDrawable(0xFF2E7D5B.toInt()) }
    private val fylz = app("demo.fylz", "Fylz")
    private val apps = listOf(fylz, app("demo.fylzer", "Fylzer"), app("demo.filez", "Filez"), app("demo.files", "Files by Fylz"))
    private fun shortcut(id: String, title: String, score: Float) = SearchHit(
        id = "shortcut:demo.fylz:0:$id", category = SearchCategory.Actions, title = title, subtitle = "Fylz", kind = HitKind.Action, score = score,
        extras = mapOf("package" to "demo.fylz", "shortcutId" to id, "user" to "0"),
    )
    private val shortcuts = listOf(shortcut("tools", "Tools", 0.9f), shortcut("activity", "Activity", 0.8f), shortcut("settings", "Settings", 0.7f), shortcut("recent", "Recent files", 0.6f))
    private val deep = SearchHit("act:deep", SearchCategory.Actions, "Deep results", kind = HitKind.Action, extras = mapOf("command" to "OpenSettings"))
    private val settingsPage = SearchHit("act:page", SearchCategory.Actions, "Open Fylz settings page", kind = HitKind.Action, extras = mapOf("command" to "OpenSettings"))
    private val fakeHub = object : SearchHub {
        override fun search(query: String, prefs: LauncherPrefs) = kotlinx.coroutines.flow.flowOf(mapOf(SearchCategory.Actions to shortcuts + deep + settingsPage))
        override fun hasFileAccess() = true
        override fun deepSources() = listOf("demo.fylz")
    }
    private var launched = 0

    private fun open(layout: SearchLayout, query: String = "fylz", fontScale: Float = 1f) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val d = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, fontScale)) {
                SearchScreen(
                    apps = apps, recent = apps, prefs = LauncherPrefs(searchMotion = false, searchLayout = layout, searchLayoutPinned = true), contacts = null,
                    onLaunch = { launched++ }, onLongPressApp = {}, onOpenIntent = {}, onRequestContactsPermission = {}, onDismiss = {},
                    hub = fakeHub, onOpenHit = { launched++ }, onRunAction = {}, onRequestFilePermission = {}, sounds = null, initialQuery = query,
                    launchHistory = RadarTestHabits.history(apps), learnedOverride = emptyMap(), nowMillis = { RadarTestHabits.NOW }, voiceAvailable = true,
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
    }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(900)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        java.io.File(outDir, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Every node that takes a click (unmerged tree, so the circles are separate nodes), except the ones the size of the screen. */
    private fun interactive(): List<Pair<String, Rect>> {
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        return rule.onAllNodes(SemanticsMatcher("clickable") { it.config.contains(SemanticsActions.OnClick) }, useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.boundsInRoot.width < root.width * 0.9f || it.boundsInRoot.height < root.height * 0.5f }
            // the category wheel (its labels fade out above and below the visible rows) is not part of this change; it has its own bounds tests
            .filterNot { n -> generateSequence(n) { it.parent }.any { it.config.getOrNull(SemanticsProperties.TestTag) == RADAR_WHEEL_TAG } }
            .map { (it.config.getOrNull(SemanticsProperties.TestTag) ?: it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() ?: it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text ?: it.config.map { c -> c.key.name }.toString()) to it.boundsInRoot }
    }

    private fun assertNoOverlap(where: String) {
        val nodes = interactive()
        val bad = ArrayList<String>()
        for (i in nodes.indices) for (j in i + 1 until nodes.size) {
            val a = nodes[i].second; val b = nodes[j].second
            val w = minOf(a.right, b.right) - maxOf(a.left, b.left); val h = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
            // one pixel of slack for rounding; real overlap is whole dp
            if (nodes[i].first == RADAR_MIC_TAG || nodes[j].first == RADAR_MIC_TAG) continue // wave 29: the mic is an overlay; the list scrolls under it and the visible part of a row stays tappable
            if (w > 1.5f && h > 1.5f) bad += "${nodes[i].first} $a x ${nodes[j].first} $b"
        }
        assertTrue("two interactive nodes overlap ($where): $bad", bad.isEmpty())
    }

    private fun circles(prefix: String) = rule.onAllNodes(SemanticsMatcher("tagged $prefix") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true }, useUnmergedTree = true).fetchSemanticsNodes()

    private fun checkLayout(layout: SearchLayout, font: Float, shot: String? = null) {
        open(layout, "fylz", font)
        val density = rule.activity.resources.displayMetrics.density
        val r = rule.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().first().boundsInRoot
        val where = "$layout ${r.width / density}x${r.height / density}dp font=$font"
        val row = circles("nested.demo.fylz")
        assertTrue("Fylz's shortcut row is drawn ($where)", row.isNotEmpty())
        row.forEach {
            assertEquals("a slot is 48 dp wide ($where)", 48f * density, it.boundsInRoot.width, 1f)
            assertEquals("a slot is 48 dp high ($where)", 48f * density, it.boundsInRoot.height, 1f)
            assertTrue("a shortcut circle stays inside the display ($where)", it.boundsInRoot.left >= -0.5f && it.boundsInRoot.right <= r.right + 0.5f && it.boundsInRoot.top >= -0.5f && it.boundsInRoot.bottom <= r.bottom + 0.5f)
        }
        assertNoOverlap(where)
        if (shot != null) shoot(shot)
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_03_orbitShortcutRowDoesNotOverlap_891x411_font1() = checkLayout(SearchLayout.Orbit, 1f, "w25-shortcutForm-orbit-fylz-landscape")
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_03_orbitShortcutRowDoesNotOverlap_891x411_font2() = checkLayout(SearchLayout.Orbit, 2f)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_03_flatShortcutRowDoesNotOverlap_360x640_font1() = checkLayout(SearchLayout.Flat, 1f)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_03_flatShortcutRowDoesNotOverlap_360x640_font2() = checkLayout(SearchLayout.Flat, 2f)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_03_flatShortcutRowDoesNotOverlap_411x891_font1() = checkLayout(SearchLayout.Flat, 1f, "w25-shortcutForm-flat-fylz")
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_03_flatShortcutRowDoesNotOverlap_411x891_font2() = checkLayout(SearchLayout.Flat, 2f)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_03_flatShortcutRowDoesNotOverlap_891x411_font1() = checkLayout(SearchLayout.Flat, 1f)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_03_flatShortcutRowDoesNotOverlap_891x411_font2() = checkLayout(SearchLayout.Flat, 2f)

    /** The Filmstrip (it is the idle layout; the typed one is the list): a row of four shortcuts is part of the tile's height and overlaps nothing, at both font scales. */
    private fun checkFilmstrip(font: Float) {
        rule.mainClock.autoAdvance = false
        val tile = Tile.App(fylz, shortcuts)
        val other = Tile.App(apps[1], emptyList())
        rule.setContent {
            val d = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, font)) {
                Box(Modifier.fillMaxSize()) {
                    Filmstrip(listOf(tile, other), motion = false, query = "", expanded = null, onOpenIntent = {}, footnote = null, actions = TileActions({}, {}, {}), fit = StripFit.Full)
                }
            }
        }
        rule.mainClock.advanceTimeBy(600)
        assertTrue(circles("nested.demo.fylz").isNotEmpty())
        assertNoOverlap("Filmstrip font=$font")
        // the row is below the tile it belongs to
        val t = rule.onAllNodes(SemanticsMatcher("tile") { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() == "Fylz" }, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        circles("nested.demo.fylz").forEach { assertTrue("a circle sits under its tile: ${it.boundsInRoot} vs $t", it.boundsInRoot.top >= t.bottom - 1.5f) }
    }
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_03_filmstripShortcutRowDoesNotOverlap_360x640_font1() = checkFilmstrip(1f)
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun PAL_03_filmstripShortcutRowDoesNotOverlap_411x891_font2() = checkFilmstrip(2f)
    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi") fun PAL_03_filmstripShortcutRowDoesNotOverlap_891x411_font2() = checkFilmstrip(2f)

    // ---- no action circle on the rings; the strip holds them in the same form


    @Test fun PAL_18_theRingResultsOfTheDataModelAreOnlyAppsPeopleAndFiles() {
        val d = SearchData(
            query = "fylz", apps = apps, contacts = emptyList(), askContacts = false, web = emptyList(), webFirst = false,
            hits = mapOf(SearchCategory.Actions to shortcuts + deep + settingsPage, SearchCategory.Files to listOf(SearchHit("f1", SearchCategory.Files, "Fylz notes.txt", kind = HitKind.Document))),
            askFiles = false, deepHint = false,
        )
        val ring = d.ringTiles(null)
        assertTrue("only apps and files: $ring", ring.all { it is Tile.App || (it is Tile.Hit && it.hit.category == SearchCategory.Files) })
        assertTrue("the four shortcuts are inside the app, not tiles", (ring.first { it is Tile.App && it.app.key == fylz.key } as Tile.App).shortcuts.size == 4)
        assertEquals(listOf("Deep results", "Open Fylz settings page"), d.stripTiles().map { it.caption() })
        assertTrue(d.stripTiles().all(::isStripTile) && ring.none(::isStripTile))
    }

    @Test fun PAL_03_aRowShowsThreeCirclesAndAPlusNOnlyWhenTheyDoNotAllFit() {
        fun r(total: Int, w: Float) = ShortcutForm.row(total, w).let { it.shown to it.more }
        assertEquals(0 to 0, r(0, 200f))
        assertEquals(3 to 0, r(3, 144f))
        assertEquals(2 to 2, r(4, 144f)) // a tile three slots wide: two circles and "+2"
        assertEquals(3 to 1, r(4, 400f)) // room enough: three circles and "+1"
        assertEquals(0 to 4, r(4, 48f))
        assertEquals(144f, ShortcutForm.smallRowWidth(9), 0f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun PAL_03_aCircleRunsItsShortcutAndThePlusNCircleOpensThePreviewListingAll() {
        open(SearchLayout.Flat)
        rule.onNodeWithTag("nested.${fylz.key}.${shortcuts[0].id}").performClick()
        assertEquals(1, launched)
        rule.onNodeWithTag("nested.${fylz.key}.more").performClick()
        rule.mainClock.advanceTimeBy(400)
        shortcuts.forEach { rule.onNodeWithTag("preview.shortcut.${it.id}").assertExists() }
    }


    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performSemanticsAction(a: androidx.compose.ui.semantics.SemanticsPropertyKey<androidx.compose.ui.semantics.AccessibilityAction<() -> Boolean>>) {
        rule.runOnUiThread { fetchSemanticsNode().config[a].action?.invoke() }
    }
}
