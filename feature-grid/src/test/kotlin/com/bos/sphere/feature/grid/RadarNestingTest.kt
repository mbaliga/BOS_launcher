package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HitKind
import com.bos.sphere.core.data.SearchCategory
import com.bos.sphere.core.data.SearchHit
import com.bos.sphere.core.input.MenuAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** PAL-03: an app's own shortcuts sit inside its tile or row in Orbit, Filmstrip and Flat, and are reachable from the keyboard action list. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RadarNestingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @org.junit.Before fun english() { com.bos.sphere.core.design.L10n.setLanguage("en") }

    private fun app(pkg: String, label: String) = AppEntry(pkg, ComponentName(pkg, "$pkg.Main"), label, Process.myUserHandle()) { null }
    private fun shortcut(pkg: String, id: String, title: String, score: Float = 0.5f, user: Int = 0) = SearchHit(
        id = "shortcut:$pkg:$user:$id", category = SearchCategory.Actions, title = title, subtitle = "App", kind = HitKind.Action, score = score,
        extras = mapOf("package" to pkg, "shortcutId" to id, "user" to user.toString()),
    )

    private val mail = app("demo.mail", "Mail")
    private val maps = app("demo.maps", "Maps")

    private fun data(apps: List<AppEntry>, vararg hits: SearchHit) = SearchData(
        query = "ma", apps = apps, contacts = emptyList(), askContacts = false, web = emptyList(), webFirst = false,
        hits = mapOf(SearchCategory.Actions to hits.toList()), askFiles = false, deepHint = false,
    )

    @Test @Config(sdk = [35]) fun PAL_03_shortcutsOfAMatchedAppAreNestedInItsTileInsteadOfSeparateRows() {
        val compose = shortcut("demo.mail", "compose", "Compose", 0.9f)
        val inbox = shortcut("demo.mail", "inbox", "Inbox", 0.4f)
        val d = data(listOf(mail, maps), compose, inbox)
        val tiles = d.tiles(SearchCategory.Apps)
        assertEquals("only the two apps are rows", 2, tiles.size)
        val mailTile = tiles.filterIsInstance<Tile.App>().first { it.app.key == mail.key }
        assertEquals("best match first", listOf("compose", "inbox"), mailTile.shortcuts.map { it.extras["shortcutId"] })
        assertTrue(tiles.filterIsInstance<Tile.App>().first { it.app.key == maps.key }.shortcuts.isEmpty())
        assertEquals("no separate Actions rows", 0, d.count(SearchCategory.Actions))
        assertTrue(d.tiles(SearchCategory.Actions).isEmpty())
        assertTrue("the mixed list has no standalone shortcut row either", d.tiles(null).none { it is Tile.Hit })
        assertTrue(flatTiles(d, null).none { it is Tile.Hit })
    }

    @Test @Config(sdk = [35]) fun PAL_03_aShortcutWhoseAppDidNotMatchStaysAnActionsRow() {
        val orphan = shortcut("demo.other", "x", "Other thing")
        val d = data(listOf(mail), orphan, shortcut("demo.mail", "compose", "Compose"))
        assertEquals(listOf(orphan.id), d.tiles(SearchCategory.Actions).filterIsInstance<Tile.Hit>().map { it.hit.id })
        assertEquals(1, d.count(SearchCategory.Actions))
    }

    @Test @Config(sdk = [35]) fun PAL_03_shortcutsBeyondTheCapStayRowsSoNoneIsLost() {
        val many = (1..6).map { shortcut("demo.mail", "s$it", "S$it", 1f - it / 10f) }
        val d = data(listOf(mail), *many.toTypedArray())
        val nested = (d.tiles(SearchCategory.Apps).single() as Tile.App).shortcuts
        val rows = d.tiles(SearchCategory.Actions).filterIsInstance<Tile.Hit>().map { it.hit }
        assertEquals(NestedShortcuts.MAX, nested.size)
        assertEquals(many.map { it.id }.toSet(), (nested + rows).map { it.id }.toSet())
    }

    @Test @Config(sdk = [35]) fun PAL_03_plainActionHitsAreNeverNested() {
        val action = SearchHit("act:1", SearchCategory.Actions, "Open launcher settings", kind = HitKind.Action, extras = mapOf("command" to "OpenSettings"))
        val d = data(listOf(mail), action)
        assertEquals(1, d.tiles(SearchCategory.Actions).size)
        assertTrue((d.tiles(SearchCategory.Apps).single() as Tile.App).shortcuts.isEmpty())
    }

    @Test @Config(sdk = [35]) fun PAL_03_theKeyboardActionListOfANestedAppRunsItsShortcutsRightAfterOpen() {
        val compose = shortcut("demo.mail", "compose", "Compose")
        val tile = Tile.App(mail, listOf(compose))
        val menu = NestedShortcuts.menuFor(tile, ResultMenus.forKind(ResultKind.App))
        val open = menu.indexOfFirst { it.action == MenuAction.Open }
        assertEquals(compose, menu[open + 1].shortcut)
        assertEquals("Compose", menu[open + 1].label)
        assertEquals(ResultMenus.forKind(ResultKind.App).size + 1, menu.size)
        // An app without shortcuts, and any other kind of result, keep the plain list.
        assertEquals(ResultMenus.forKind(ResultKind.App), NestedShortcuts.menuFor(Tile.App(mail), ResultMenus.forKind(ResultKind.App)))
        assertFalse(menu.filter { it.shortcut == null }.any { it.label == "Compose" })
    }

    // ---- the three layouts draw them inside the tile or row

    private fun tag(t: Tile.App, h: SearchHit) = "nested.${t.app.key}.${h.id}"

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_03_filmstripDrawsTheShortcutsInsideTheAppTileAndTapRunsOne() {
        rule.mainClock.autoAdvance = false
        val compose = shortcut("demo.mail", "compose", "Compose")
        val tile = Tile.App(mail, listOf(compose))
        val tapped = mutableListOf<Tile>()
        rule.setContent {
            Filmstrip(listOf(tile), motion = false, query = "", expanded = null, onOpenIntent = {}, footnote = null, actions = TileActions({ tapped += it }, {}, {}))
        }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag(tag(tile, compose)).assertExistsAndTap()
        assertEquals(listOf<Tile>(Tile.Hit(compose)), tapped)
    }


    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_03_flatListDrawsEveryShortcutInTheAppRow() {
        val compose = shortcut("demo.mail", "compose", "Compose", 0.9f)
        val inbox = shortcut("demo.mail", "inbox", "Inbox", 0.4f)
        val d = data(listOf(mail), compose, inbox)
        val tapped = mutableListOf<Tile>()
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                FlatResults(d, scope = null, suggestions = emptyList(), onPickSuggestion = {}, onTileTap = { tapped += it }, onLongPressApp = {}, onOpenIntent = {}, onQueryChange = {}, onTouch = {})
            }
        }
        rule.waitForIdle()
        val tile = d.tiles(SearchCategory.Apps).single() as Tile.App
        rule.onNodeWithTag(tag(tile, compose)).assertExistsAndTap()
        rule.onNodeWithTag(tag(tile, inbox)).assertExistsAndTap()
        assertEquals(listOf<Tile>(Tile.Hit(compose), Tile.Hit(inbox)), tapped)
    }

    @Test @Config(sdk = [35]) fun PAL_08_theChooserDialogRendersAndARowRunsItsChoiceAndCancelDismisses() {
        val ran = mutableListOf<String>(); var dismissed = 0; val picked = mutableListOf<String>()
        val choices = listOf(RadarChoice("Open on Desk") { ran += "desk" }, RadarChoice("Open on TV") { ran += "tv" })
        rule.setContent { RadarChoicePicker("Open on which display?", choices, onDismiss = { dismissed++ }, onPick = { picked += it.label; it.run() }) }
        rule.waitForIdle()
        rule.onNodeWithTag("radar.picker").assertExists()
        rule.onNodeWithTag("radar.picker.1").performClick()
        assertEquals(listOf("TV".let { "Open on $it" }), picked)
        assertEquals(listOf("tv"), ran)
        rule.onNodeWithTag("radar.picker.cancel").performClick()
        assertEquals(1, dismissed)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsAndTap() { assertExists(); performClick() }
}
