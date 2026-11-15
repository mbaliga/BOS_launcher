package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.catalog.HomeState
import com.bos.sphere.core.catalog.ItemDef
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind
import com.bos.sphere.core.catalog.Folder
import com.bos.sphere.core.data.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOM-01 (non-app items are rows among the apps on List, XMB and DSi), HOM-19 (the first rows stay inside
 * the frame's app area at many window sizes) and HOM-21 (quick sheet layout rules).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeLayoutW3UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun app(label: String) = AppEntry("p.$label", ComponentName("p.$label", "p.$label.Main"), label, Process.myUserHandle()) { ColorDrawable(0xFF3366CC.toInt()) }
    private val apps = listOf("Alpha", "Beta", "Camera", "Delta", "Echo", "Maps").map(::app)
    private val folderKey = ItemKeys.folder("f1")
    private val linkKey = ItemKeys.link("https://example.org")

    private fun extras(onTap: (String) -> Unit = {}): HomeExtras {
        val s = HomeState(
            folders = mapOf("f1" to Folder("f1", "Games", listOf(apps[0].key, apps[1].key))),
            items = mapOf(linkKey to ItemDef(linkKey, ItemKind.Link, "Example site", "https://example.org")),
        )
        return HomeExtras(s, { k -> apps.firstOrNull { it.key == k } }, { null }, { _, m -> Box(m) }, onTap = onTap)
    }

    private val topLevel get() = listOf(apps[0].key, apps[1].key, folderKey, apps[2].key, apps[3].key, linkKey, apps[4].key, apps[5].key)
    private val extraKeys get() = listOf(folderKey, linkKey)

    @Test @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_01_listShowsFolderAndLinkAmongTheAppRowsAndTapsThem() {
        val taps = mutableListOf<String>()
        rule.setContent {
            InputModeProvider {
                ListHome(apps, {}, {}, extras = extras { taps += it }, extraKeys = extraKeys, contentPadding = PaddingValues(top = 60.dp))
            }
        }
        rule.onNodeWithText("Games").assertIsDisplayed()
        rule.onNodeWithText("Example site").assertIsDisplayed()
        // "Games" sorts between Echo? No: between Delta and Maps by name; here it must sit in G, after Echo.
        val ys = listOf("Echo", "Games", "Maps").map { rule.onNodeWithText(it).getUnclippedBoundsInRoot().top.value }
        assertTrue("A to Z order $ys", ys[0] < ys[1] && ys[1] < ys[2])
        rule.onNodeWithText("Games").performClick()
        assertEquals(listOf(folderKey), taps)
    }

    @Test @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_01_dsiAndContinuousDrawExtrasAsCellsInPageOrder() {
        rule.setContent {
            InputModeProvider {
                ContinuousHome(
                    apps = apps, crossCount = 3, landscape = false, style = ContinuousStyle.Dsi, onLaunch = {}, onLongPress = {},
                    extras = extras(), extraKeys = extraKeys, topLevel = topLevel,
                )
            }
        }
        rule.onNodeWithText("Games").assertIsDisplayed()
        rule.onNodeWithText("Example site").assertIsDisplayed()
        // Page order: Alpha, Beta, Games, Camera ... so the folder cell sits after Beta and before Camera in reading order.
        fun x(t: String) = rule.onNodeWithText(t).getUnclippedBoundsInRoot()
        val beta = x("Beta"); val games = x("Games"); val camera = x("Camera")
        assertTrue("row-major order", (beta.top == games.top && beta.left < games.left) || beta.top < games.top)
        assertTrue("row-major order", (games.top == camera.top && games.left < camera.left) || games.top < camera.top)
    }

    @Test @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_01_xmbShowsAnExtraRowInTheColumnOfTheAppAfterIt() {
        rule.setContent {
            InputModeProvider {
                XmbHome(apps, emptyList(), { "Tools" }, {}, {}, extras = extras(), extraKeys = extraKeys, topLevel = topLevel)
            }
        }
        rule.onNodeWithText("Games").assertIsDisplayed()
    }

    @Test
    fun HOM_01_xmbPlacementUsesTheFollowingApp() {
        val cats = listOf(XmbCategory("Tools", false, apps.take(3)), XmbCategory("Fun", false, apps.drop(3)))
        val out = XmbMath.withExtras(cats, listOf(folderKey, linkKey), topLevel)
        // folder is followed by Camera (Tools), link by Echo (Fun).
        assertEquals(listOf(folderKey), out[0].extraBefore[apps[2].key])
        assertEquals(listOf(linkKey), out[1].extraBefore[apps[4].key])
        assertEquals(apps.size + 2, out.sumOf { it.rows.size })
        // Recent is never a home for an extra.
        val withRecent = listOf(XmbCategory("Recent", true, apps.take(2))) + cats
        val o2 = XmbMath.withExtras(withRecent, listOf(folderKey), listOf(folderKey))
        assertTrue(o2[0].extraTail.isEmpty() && o2[0].extraBefore.isEmpty())
        assertEquals(listOf(folderKey), o2[1].extraTail)
    }

    // ---- HOM-19: many window sizes -------------------------------------------------------

    private fun check(kind: String, w: Int, h: Int) {
        val area = HomeFrame.appArea(w.toFloat(), h.toFloat())
        val pad = PaddingValues(start = area.left.dp, end = (w - area.right).dp, top = area.top.dp, bottom = (h - area.bottom).dp)
        rule.setContent {
            InputModeProvider {
                Box(Modifier.fillMaxSize()) {
                    if (kind == "list") ListHome(apps, {}, {}, contentPadding = pad, extras = extras(), extraKeys = extraKeys)
                    else if (kind == "xmb") XmbHome(apps, emptyList(), { "Tools" }, {}, {}, contentPadding = pad, extras = extras(), extraKeys = extraKeys, topLevel = topLevel)
                    else ContinuousHome(apps, 3, w > h, ContinuousStyle.Dsi, {}, {}, contentPadding = pad, extras = extras(), extraKeys = extraKeys, topLevel = topLevel)
                }
            }
        }
        val b = rule.onNodeWithText("Alpha").getUnclippedBoundsInRoot()
        assertTrue("$kind $w x $h: left ${b.left.value} >= ${area.left}", b.left.value >= area.left - 1f)
        assertTrue("$kind $w x $h: top ${b.top.value} >= ${area.top}", b.top.value >= area.top - 1f)
        assertTrue("$kind $w x $h: right ${b.right.value} <= ${area.right}", b.right.value <= area.right + 1f)
        // The folder row/cell is among the first rows and also inside the area.
        if (kind != "list") { // on the A to Z list the folder sorts under G, which a short window may not show
            val f = rule.onNodeWithText("Games").getUnclippedBoundsInRoot()
            assertTrue("$kind $w x $h: folder left", f.left.value >= area.left - 1f)
            assertTrue("$kind $w x $h: folder top", f.top.value >= area.top - 1f)
        }
    }

    // HOM-19: the first rows of List, XMB and DSi stay inside the frame's app area at many window sizes and both orientations.
    @Test @Config(qualifiers = "w320dp-h320dp-mdpi") fun HOM_19_list320x320() = check("list", 320, 320)
    @Test @Config(qualifiers = "w360dp-h640dp-mdpi") fun HOM_19_list360x640() = check("list", 360, 640)
    @Test @Config(qualifiers = "w640dp-h360dp-mdpi") fun HOM_19_list640x360() = check("list", 640, 360)
    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi") fun HOM_19_list800x1280() = check("list", 800, 1280)
    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi") fun HOM_19_list1280x800() = check("list", 1280, 800)
    @Test @Config(qualifiers = "w360dp-h640dp-mdpi") fun HOM_19_xmb360x640() = check("xmb", 360, 640)
    @Test @Config(qualifiers = "w640dp-h360dp-mdpi") fun HOM_19_xmb640x360() = check("xmb", 640, 360)
    @Test @Config(qualifiers = "w411dp-h891dp-mdpi") fun HOM_19_xmb411x891() = check("xmb", 411, 891)
    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi") fun HOM_19_xmb800x1280() = check("xmb", 800, 1280)
    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi") fun HOM_19_xmb1280x800() = check("xmb", 1280, 800)
    @Test @Config(qualifiers = "w360dp-h640dp-mdpi") fun HOM_19_dsi360x640() = check("dsi", 360, 640)
    @Test @Config(qualifiers = "w640dp-h360dp-mdpi") fun HOM_19_dsi640x360() = check("dsi", 640, 360)
    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi") fun HOM_19_dsi800x1280() = check("dsi", 800, 1280)
    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi") fun HOM_19_dsi1280x800() = check("dsi", 1280, 800)

    private val sizes = listOf(320 to 320, 360 to 640, 640 to 360, 411 to 891, 891 to 411, 800 to 1280, 1280 to 800, 1024 to 600)

    @Test
    fun HOM_19_noBandStripIsTakenForItemsAnyMoreOnlyWidgetsReserveSpace() {
        for ((w, h) in sizes) {
            val area = HomeFrame.appArea(w.toFloat(), h.toFloat())
            val r = ScrollExtrasLayout.compute(w.toFloat(), h.toFloat(), widgetCount = 0, itemCount = 0)
            assertEquals(area.left, r.tiles.left, 0.01f); assertEquals(area.top, r.tiles.top, 0.01f)
            assertEquals(area.right, r.tiles.right, 0.01f); assertEquals(area.bottom, r.tiles.bottom, 0.01f)
        }
    }

    // ---- HOM-21 ----------------------------------------------------------------------------

    @Test
    fun HOM_21_quickSheetTilesWrapOnNarrowWindowsAndLargeFonts() {
        assertEquals(4, QuickSheetLayout.tileColumns(411f, 1f))
        assertEquals(4, QuickSheetLayout.tileColumns(800f, 1f))
        assertEquals(2, QuickSheetLayout.tileColumns(320f, 1f))
        assertEquals(2, QuickSheetLayout.tileColumns(411f, 1.5f))
        assertEquals(1, QuickSheetLayout.tileColumns(200f, 2f))
        for (w in 200..1400 step 20) for (f in listOf(0.85f, 1f, 1.3f, 2f)) {
            val c = QuickSheetLayout.tileColumns(w.toFloat(), f)
            assertTrue(c in 1..4)
            val inner = w - 2 * QuickSheetLayout.SIDE_PADDING_DP
            val tile = (inner - (c - 1) * QuickSheetLayout.TILE_GAP_DP) / c
            // Each tile is as wide as a label needs, unless even a single column is narrower than that.
            assertTrue("w=$w f=$f cols=$c tile=$tile", c == 1 || tile >= QuickSheetLayout.MIN_TILE_DP * f.coerceIn(0.8f, 2f) - 0.01f)
        }
        assertEquals(listOf(listOf(1, 2), listOf(3, 4)), QuickSheetLayout.rows(listOf(1, 2, 3, 4), 2))
    }

    @Test
    fun HOM_21_quickSheetScrollsWhenTheWindowIsShorterThanItsContent() {
        assertTrue(QuickSheetLayout.needsScroll(700f, 360f))
        assertTrue(!QuickSheetLayout.needsScroll(300f, 891f))
        assertTrue(QuickSheetLayout.maxContentHeightDp(100f) >= 120f)
    }
}
