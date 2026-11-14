package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.compose.ui.geometry.Offset
import com.bos.sphere.core.catalog.Box
import com.bos.sphere.core.catalog.HomeEdit
import com.bos.sphere.core.catalog.HomeState
import com.bos.sphere.core.catalog.IconPackMap
import com.bos.sphere.core.catalog.IconPlate
import com.bos.sphere.core.catalog.IconSpec
import com.bos.sphere.core.catalog.ItemDef
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind
import com.bos.sphere.core.catalog.WidgetBand
import com.bos.sphere.core.catalog.WidgetSpan
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.PageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class HomeItemsWiringTest {
    private val cap: (PageLayout) -> Int = { 12 }
    private val w = ItemKeys.widget(4)

    private fun app(pkg: String, icon: () -> android.graphics.drawable.Drawable?) =
        AppEntry(pkg, ComponentName(pkg, "$pkg.Main"), pkg, Process.myUserHandle(), iconLoader = icon)

    private fun extras(state: HomeState) = HomeExtras(state, { null }, { null }, { _, _ -> })

    // ---- HOM-08: the band never overlaps tiles, on every window size and orientation
    @Test
    fun HOM_08_scrollBandNeverOverlapsTilesItemsOrWidgets() {
        for ((wd, ht) in listOf(360 to 640, 640 to 360, 411 to 891, 1280 to 800, 800 to 1280, 320 to 320)) {
            for (widgets in 0..3) for (items in 0..2) {
                val r = ScrollExtrasLayout.compute(wd.toFloat(), ht.toFloat(), widgets, items)
                val boxes = listOfNotNull(r.tiles, r.items) + r.widgets
                for (i in boxes.indices) for (j in i + 1 until boxes.size) {
                    assertFalse("overlap at $wd x $ht w=$widgets i=$items", boxes[i].intersects(boxes[j]))
                }
                val area = HomeFrame.appArea(wd.toFloat(), ht.toFloat())
                val outer = Box(area.left, area.top, area.right, area.bottom)
                boxes.forEach { assertTrue(outer.contains(it)) }
                assertEquals(widgets, r.widgets.size)
                assertEquals(items > 0, r.items != null)
            }
        }
    }

    @Test
    fun HOM_08_bandSitsBelowInPortraitAndAtTheEndInLandscape() {
        assertEquals(WidgetBand.Edge.Bottom, ScrollExtrasLayout.compute(360f, 640f, 1, 0).edge)
        assertEquals(WidgetBand.Edge.End, ScrollExtrasLayout.compute(640f, 360f, 1, 0).edge)
        assertEquals(0f, ScrollExtrasLayout.compute(360f, 640f, 0, 0).thickness, 0f)
    }

    @Test
    fun HOM_08_padForAddsExactlyTheBandThickness() {
        val base = androidx.compose.foundation.layout.PaddingValues(10.dp(), 20.dp(), 10.dp(), 30.dp())
        val r = ScrollExtrasLayout.compute(360f, 640f, 1, 0)
        val p = ScrollExtrasLayout.padFor(base, 360f, 640f, 1, 0)
        assertEquals(30f + r.thickness, p.calculateBottomPadding().value, 0.01f)
        assertEquals(base, ScrollExtrasLayout.padFor(base, 360f, 640f, 0, 0))
    }

    private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())

    // ---- HOM-01: the band keys come from the surface contract's plan
    @Test
    fun HOM_01_bandKeysComeFromThePlanForEveryScrollingStyle() {
        var s = HomeState(pages = listOf(HomePage(listOf("a", "b"), id = 1)))
        s = HomeEdit.add(s, ItemDef(w, ItemKind.Widget, "W"), 0, cap, WidgetSpan(2, 2))
        s = HomeEdit.add(s, ItemDef(ItemKeys.link("https://x.org"), ItemKind.Link, "x"), 0, cap)
        s = HomeEdit.createFolder(s, listOf("a", "b"), "f", "F", cap)
        val e = extras(s)
        val top = s.topLevelKeys()
        for (style in listOf(HomeStyle.List, HomeStyle.Psp, HomeStyle.Dsi, HomeStyle.Grid)) {
            val (items, widgets) = bandKeys(e, top, style, 400, 800)
            assertEquals(style.name, listOf(w), widgets)
            assertEquals(style.name, setOf(ItemKeys.folder("f"), ItemKeys.link("https://x.org")), items.toSet())
        }
    }

    // ---- HOM-08: the grid page packs spans
    private fun grid(cols: Int, rows: Int) =
        PageGeometry.Grid(GridGeometry.compute(400f, 600f, cols, rows, 0.5f), 1f, false)

    @Test
    fun HOM_08_gridPageWithAWidgetPacksAndHitTestsBySpan() {
        val s = HomeState(widgets = mapOf(w to WidgetSpan(2, 2)), items = mapOf(w to ItemDef(w, ItemKind.Widget, "W")))
        val e = extras(s)
        val page = HomePage(listOf("a", w, "b"), id = 1)
        val g = grid(4, 6)
        assertNotNull(spanPacked(page, g, e))
        // widget placed at col 1,row 0 spans cells 1,2,5,6
        assertEquals(w, keyAtSlot(page, g, 6, e))
        assertEquals(w, keyAtSlot(page, g, 2, e))
        assertEquals("a", keyAtSlot(page, g, 0, e))
        assertNull(keyAtSlot(page, g, 7, e) .takeIf { it != "b" && it != null })
        // a page without a spanning widget keeps plain slot order
        assertNull(spanPacked(HomePage(listOf("a", "b"), id = 1), g, e))
        assertEquals("b", keyAtSlot(HomePage(listOf("a", "b"), id = 1), g, 1, e))
    }

    @Test
    fun HOM_08_dropIndexAccountsForTheWidgetBlock() {
        val s = HomeState(widgets = mapOf(w to WidgetSpan(2, 2)))
        val e = extras(s)
        val g = grid(4, 6)
        val rest = listOf("a", w, "b")
        // Far bottom-right cell: after everything.
        val idx = dropIndex(g, rest, "x", e, 399f, 599f)
        assertEquals(rest.size, idx)
        // Top-left cell: before everything.
        assertEquals(0, dropIndex(g, rest, "x", e, 1f, 1f))
    }

    // ---- HOM-04: drag onto an item
    @Test
    fun HOM_04_dropOntoTargetNeedsTheCentreOfAnotherItem() {
        val s = HomeState(pages = listOf(HomePage(listOf("a", "b", "c"), id = 1)))
        val e = extras(s)
        val g = grid(3, 3)
        val page = HomePage(listOf("a", "b", "c"), id = 1)
        val c = g.center(1)
        assertEquals("b", dropOntoTarget(e, page, g, Offset(c.x, c.y), "a"))
        assertNull(dropOntoTarget(e, page, g, Offset(c.x + g.iconPx * 0.45f, c.y), "a"))
        assertNull(dropOntoTarget(e, page, g, Offset(c.x, c.y), "b"))
        assertNull(dropOntoTarget(null, page, g, Offset(c.x, c.y), "a"))
    }

    // ---- HOM-03: icon sources (override > pack > themed > own icon)
    @Test
    fun HOM_03_themedIconIsMonochromeInThemeColoursOnItsOwnTile() {
        val (fg, bg) = IconPlate.themed(0xFF8E7BFF.toInt(), true)
        val mono = ColorDrawable(Color.WHITE)
        val bmp = IconSources.rasterizeThemed(mono, 96, fg, bg)
        // The whole tile is covered by the monochrome layer, so its pixels are the foreground colour.
        assertEquals(fg, bmp.getPixel(48, 48))
        val bgOnly = IconSources.rasterizeThemed(ColorDrawable(Color.TRANSPARENT), 96, fg, bg)
        assertEquals(bg, bgOnly.getPixel(2, 2))
        assertNotEquals(fg, bg)
    }

    @Test
    fun HOM_03_themedSourceUsesTheMonochromeLayerOfAnAdaptiveIcon() {
        val adaptive = AdaptiveIconDrawable(ColorDrawable(Color.RED), ColorDrawable(Color.GREEN), ColorDrawable(Color.WHITE))
        val a = app("com.t") { adaptive }
        val themed = IconSources(themed = true, dark = true)
        val plain = IconSources()
        val tBmp = themed.rasterize(a, 96, themed.cacheKey(a))!!
        val pBmp = plain.rasterize(a, 96, plain.cacheKey(a))!!
        val (fg, _) = IconPlate.themed(themed.accentArgb, true)
        assertEquals(fg, tBmp.getPixel(48, 48))
        assertNotEquals(tBmp.getPixel(48, 48), pBmp.getPixel(48, 48))
        assertNotEquals(themed.cacheKey(a), plain.cacheKey(a))
    }

    @Test
    fun HOM_03_overrideBeatsPackBeatsOwnIconAndChangesTheCacheKey() {
        val a = app("com.a") { ColorDrawable(Color.BLUE) }
        val pack = IconPackMap.parse("pk", """<resources><item component="ComponentInfo{com.a/com.a.Main}" drawable="from_pack"/></resources>""")
        val drawables = mapOf("pk/from_pack" to Color.RED, "pk/mine" to Color.GREEN)
        val load: (String, String) -> android.graphics.drawable.Drawable? = { p, n -> drawables["$p/$n"]?.let { ColorDrawable(it) } }
        val none = IconSources(drawable = load)
        val withPack = IconSources(pack = pack, drawable = load)
        val withOverride = IconSources(overrides = mapOf(a.key to IconSpec.encode("pk", "mine")), pack = pack, drawable = load)
        assertEquals(Color.RED, withPack.rasterize(a, 64, withPack.cacheKey(a))!!.getPixel(32, 32))
        assertEquals(Color.GREEN, withOverride.rasterize(a, 64, withOverride.cacheKey(a))!!.getPixel(32, 32))
        assertEquals(setOf(none.cacheKey(a), withPack.cacheKey(a), withOverride.cacheKey(a)).size, 3)
    }

    @Test
    fun HOM_03_aMissingPackDrawableFallsBackToTheAppsOwnIcon() {
        val a = app("com.a") { ColorDrawable(Color.BLUE) }
        val pack = IconPackMap.parse("pk", """<resources><item component="ComponentInfo{com.a/com.a.Main}" drawable="gone"/></resources>""")
        val s = IconSources(pack = pack, drawable = { _, _ -> null })
        assertNotNull(s.rasterize(a, 64, s.cacheKey(a)))
    }
}
