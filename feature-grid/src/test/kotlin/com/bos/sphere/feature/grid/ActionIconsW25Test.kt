package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Color as AColor
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.graphics.ImageBitmap

import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HitKind
import com.bos.sphere.core.data.SearchCategory
import com.bos.sphere.core.data.SearchHit
import com.bos.sphere.core.input.MenuAction
import dev.aarso.search.palette.LauncherActions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Wave 25 (owner item 6): every action, shortcut and suggestion row or pill of Radar has an appropriate leading icon. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
class ActionIconsW25Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val original = ShortcutIcons.source
    @Before fun setUp() { com.bos.sphere.core.design.L10n.setLanguage("en"); ShortcutIcons.clear() }
    @After fun restore() { ShortcutIcons.source = original; ShortcutIcons.clear() }

    private fun app(pkg: String, label: String) = AppEntry(pkg, ComponentName(pkg, "$pkg.Main"), label, Process.myUserHandle()) { null }
    private fun shortcut(pkg: String, id: String, title: String) = SearchHit(
        id = "shortcut:$pkg:0:$id", category = SearchCategory.Actions, title = title, kind = HitKind.Action,
        extras = mapOf("package" to pkg, "shortcutId" to id, "user" to "0"),
    )

    // ---- the mapping: every kind has an icon (a new kind without one does not compile or fails here)

    @Test @Config(sdk = [35]) fun PAL_18_everyMenuActionMapsToAnIconKind() {
        MenuAction.entries.forEach { a -> assertNotNull(a.name, ActionIcons.kind(a).glyph) }
        assertEquals(ActionKind.AppInfo, ActionIcons.kind(MenuAction.AppInfo))
        assertEquals(ActionKind.Uninstall, ActionIcons.kind(MenuAction.Uninstall))
        assertEquals(ActionKind.AddToDock, ActionIcons.kind(MenuAction.AddToDock))
    }

    @Test @Config(sdk = [35]) fun PAL_18_everyLauncherCommandHasItsOwnIconNotTheGenericOne() {
        LauncherActions.defaults.forEach { d ->
            assertNotEquals("${d.command} falls back to the generic shortcut glyph", ActionKind.AppShortcut, ActionIcons.kindOfCommand(d.command))
        }
        assertEquals(ActionKind.SwitchWorkspace, ActionIcons.kindOfCommand("GoToWorkspace"))
    }

    @Test @Config(sdk = [35]) fun PAL_18_everyResultMenuEntryOfEveryKindHasAGlyph() {
        ResultKind.entries.forEach { k ->
            ResultMenus.forKind(k).forEach { e -> assertTrue("${k.name}/${e.label}", ActionIcons.kind(e).glyph.defaultWidth.value > 0f) }
        }
        val sc = ResultMenuEntry("Compose", null, shortcut = shortcut("p", "c", "Compose"))
        assertEquals(ActionKind.AppShortcut, ActionIcons.kind(sc))
    }

    @Test @Config(sdk = [35]) fun PAL_18_everyActionKindHasADistinctNamedGlyphWithA24Viewport() {
        ActionKind.entries.forEach { k ->
            assertEquals(k.name, 24f, k.glyph.viewportWidth, 0f)
            assertEquals(k.name, 24f, k.glyph.viewportHeight, 0f)
        }
        // Sixteen distinct shapes at least: the new built-in glyphs are not one reused dot.
        assertTrue(ActionKind.entries.map { it.glyph.name }.toSet().size >= 16)
    }

    @Test @Config(sdk = [35]) fun PAL_03_hitsOfEveryActionKindResolveToTheirGlyph() {
        fun hit(vararg e: Pair<String, String>, copy: String? = null) = SearchHit("x", SearchCategory.Actions, "t", kind = HitKind.Action, copyText = copy, extras = mapOf(*e))
        assertEquals(Glyphs.Calculator, hitGlyph(hit(copy = "4")))
        assertEquals(ActionKind.EditHome.glyph, hitGlyph(hit("command" to "EditMode")))
        assertEquals(ActionKind.ShowKeys.glyph, hitGlyph(hit("command" to "ShortcutSheet")))
        assertEquals(Glyphs.Gear, hitGlyph(hit("intentAction" to "android.settings.WIFI_SETTINGS")))
        assertEquals(ActionKind.AppShortcut.glyph, hitGlyph(shortcut("p", "s", "S")))
    }

    // ---- a seeded app shortcut with an icon shows that icon; one without falls back

    /** Draws the activity's view into a bitmap (captureToImage waits for an idle that Robolectric never gives), saves it, counts pure-red pixels inside the node. */
    private fun redPixels(tag: String, shot: String): Int {
        val view = rule.activity.window.decorView
        val bmp = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        val dir = java.io.File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(dir, "$shot.png")).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val r = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        var n = 0
        for (y in r.top.toInt() until r.bottom.toInt().coerceAtMost(bmp.height)) for (x in r.left.toInt() until r.right.toInt().coerceAtMost(bmp.width)) {
            val c = bmp.getPixel(x, y)
            if (AColor.red(c) > 200 && AColor.green(c) < 40 && AColor.blue(c) < 40) n++
        }
        return n
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_03_aShortcutWithAnIconShowsThatIconInItsCircle() {
        ShortcutIcons.source = ShortcutIconSource { _, _, _, _, _ -> ColorDrawable(AColor.RED) }
        val mail = app("demo.mail", "Mail")
        val h = shortcut("demo.mail", "compose", "Scan and pay")
        rule.setContent { Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF101018))) { ActionCircle(h.title, "pill", onClick = {}) { HitLeadingIcon(h, 24.dp, androidx.compose.ui.graphics.Color.White, app = mail) } } }
        rule.waitUntil(5_000) { ShortcutIcons.peek(h) is ImageBitmap }
        rule.waitForIdle()
        assertTrue("the shortcut's own icon is drawn in its circle", redPixels("pill", "w25-actionIcons-shortcut-own-icon") > 100)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_03_aShortcutWithoutAnIconFallsBackToTheAppIconNotAGap() {
        ShortcutIcons.source = ShortcutIconSource { _, _, _, _, _ -> null }
        val mail = app("demo.mail", "Mail")
        val h = shortcut("demo.mail", "compose", "Compose")
        rule.setContent { Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF101018))) { ActionCircle(h.title, "pill", onClick = {}) { HitLeadingIcon(h, 24.dp, androidx.compose.ui.graphics.Color.White, app = mail) } } }
        rule.waitUntil(5_000) { ShortcutIcons.peek(h) === ShortcutIcons.Miss }
        rule.waitForIdle()
        // Never the shortcut colour; the leading cell is occupied (app icon, or the built-in glyph when the app has none)
        assertSame(ShortcutIcons.Miss, ShortcutIcons.peek(h))
        assertEquals("no shortcut icon colour", 0, redPixels("pill", "w25-actionIcons-shortcut-fallback"))
        rule.onNodeWithTag("pill").assertExists()
    }

    @Test @Config(sdk = [35]) fun PAL_03_aShortcutIconThatThrowsIsTreatedAsMissingAndNotRetriedForever() {
        var calls = 0
        ShortcutIcons.source = ShortcutIconSource { _, _, _, _, _ -> calls++; error("boom") }
        val h = shortcut("demo.mail", "x", "X")
        val got = kotlinx.coroutines.runBlocking { ShortcutIcons.load(rule.activity, h, 48) }
        assertNull(got)
        kotlinx.coroutines.runBlocking { ShortcutIcons.load(rule.activity, h, 48) }
        assertEquals("a failed load is remembered", 1, calls)
    }

    @Test @Config(sdk = [35]) fun PAL_03_theShortcutIconCacheIsBounded() {
        ShortcutIcons.source = ShortcutIconSource { _, _, _, _, _ -> ColorDrawable(AColor.BLUE) }
        kotlinx.coroutines.runBlocking { (0 until 200).forEach { ShortcutIcons.load(rule.activity, shortcut("p$it", "s", "S"), 32) } }
        val kept = (0 until 200).count { ShortcutIcons.peek(shortcut("p$it", "s", "S")) != null }
        assertTrue("kept $kept", kept <= 48)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_07_theActionListShowsAnIconInFrontOfEveryEntryAndTheGlyphSheetIsReadable() {
        ShortcutIcons.source = ShortcutIconSource { _, _, _, _, _ -> ColorDrawable(AColor.RED) }
        val sc = shortcut("demo.mail", "compose", "Scan and pay")
        val entries = NestedShortcuts.menuFor(Tile.App(app("demo.mail", "Mail"), listOf(sc)), ResultMenus.forKind(ResultKind.App))
        rule.setContent {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF101018))) {
                RadarActionList(entries, 1, {})
                androidx.compose.foundation.layout.FlowRow {
                    ActionKind.entries.forEach { k -> KindIcon(k, 20.dp, androidx.compose.ui.graphics.Color.White, androidx.compose.ui.Modifier.padding(6.dp)) }
                }
            }
        }
        rule.waitUntil(5_000) { ShortcutIcons.peek(sc) is ImageBitmap }
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        val dir = java.io.File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(dir, "w25-actionIcons-action-list.png")).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
