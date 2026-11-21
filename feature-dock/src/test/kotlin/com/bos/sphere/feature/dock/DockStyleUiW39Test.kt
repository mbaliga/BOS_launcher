package com.bos.sphere.feature.dock

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsSelected
import com.bos.sphere.core.data.DockStyle
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ThemeVariant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Wave 39 (dockThemes): each dock style as drawn by the real [DockRail]: its nodes, the 48 dp targets, the active dot, press, long press, the
 * accessibility state, the pixels that make a style what it is, and screenshots (dark and light, portrait and landscape).
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
class DockStyleUiW39Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private val ran = mutableListOf<String>()

    @Before fun setUp() { L10n.setLanguage("en"); DockInput.dialogHook = {}; ran.clear() }
    @After fun tearDown() { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }

    private fun content() = DockContent(
        pinned = listOf(DockItem(DockItemRef.app("a"), "Mail", true), DockItem(DockItemRef.app("b"), "Maps"), DockItem(DockItemRef.app("c"), "Notes", true)),
        recent = listOf(DockItem(DockItemRef.app("e"), "Music")),
        showEnableRow = false, showRecentSection = true, collapsed = false,
    )
    private val tile = listOf(Color(0xFF4F8CFF), Color(0xFFFF7A59), Color(0xFF3DDC84), Color(0xFFE6B800), Color(0xFFB36BFF), Color(0xFF29C5D6))
    private fun actions() = DockActions(
        onLaunch = { i, _ -> ran += "launch ${i.label}" }, onOpenPanel = { ran += "panel" },
        onSetStyle = { ran += "style ${it.id}" },
    )

    @Composable private fun Wallpaper(light: Boolean, flat: Color? = null, content: @Composable () -> Unit) {
        val brush = if (flat != null) Brush.linearGradient(listOf(flat, flat))
        else if (light) Brush.linearGradient(listOf(Color(0xFFFFF3C4), Color(0xFFBFE3F5), Color(0xFFF7D6E0)))
        else Brush.linearGradient(listOf(Color(0xFF1B2A55), Color(0xFF3B1E54), Color(0xFF0E1226)))
        Box(Modifier.fillMaxSize().background(brush), content = { content() })
    }

    @Composable private fun Rail(style: DockStyle, edge: DockEdge = DockEdge.Bottom, light: Boolean = false, actions: DockActions = actions(), icon: ((DockItem) -> Color)? = null) {
        val align = if (edge == DockEdge.Bottom) Alignment.BottomCenter else Alignment.CenterStart
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = align) {
            DockRail(content(), edge, "Home", expanded = true, focusRequest = 0, showNumbers = false, actions = actions, graphBridge = null, style = style, dark = !light) { item, s ->
                Box(Modifier.size(s).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(icon?.invoke(item) ?: tile[item.ref.id.hashCode().mod(tile.size)]))
            }
        }
    }

    private fun theme(light: Boolean) = HyleTheme.apply(if (light) ThemeVariant.Light else ThemeVariant.Dark, 0xFF8E7BFF)

    private fun capture(): Bitmap {
        rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        return bmp
    }

    private fun shoot(name: String): Bitmap = capture().also { b -> File(outDir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }

    private fun px(dp: Float) = rule.activity.resources.displayMetrics.density * dp

    // ---------------------------------------------------------------------------------------------------------------------------------------

    private fun stateOf() = rule.onNodeWithContentDescription("Dock").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    private fun stateIs(s: DockStyle) {
        rule.setContent { Wallpaper(false) { Rail(s) } }
        assertEquals("Dock style: ${s.label}", stateOf())
    }
    @Test fun DCK_04_style_10_standardDockNodeSaysItsStyle() = stateIs(DockStyle.Standard)
    @Test fun DCK_04_style_10_transparentDockNodeSaysItsStyle() = stateIs(DockStyle.Transparent)
    @Test fun DCK_04_style_10_threeDDockNodeSaysItsStyle() = stateIs(DockStyle.ThreeD)

    private fun nodesAndTargets(style: DockStyle) {
        rule.setContent { Wallpaper(false) { Rail(style) } }
        rule.waitForIdle()
        // one node per item, never a second one for the 3D reflection
        listOf("Mail, used this session", "Maps", "Notes, used this session", "Music").forEach { rule.onAllNodesWithContentDescription(it).assertCountEquals(1) }
        rule.onNodeWithContentDescription("Workspace Home").assertExists()
        // touch targets are 52 dp whatever the style (48 dp is the minimum)
        listOf("Maps", "Music").forEach { d ->
            val b = rule.onNodeWithContentDescription(d).getBoundsInRoot()
            assertTrue("$style $d width ${b.width}", b.width >= 48.dp); assertTrue("$style $d height ${b.height}", b.height >= 48.dp)
        }
        val home = rule.onNodeWithContentDescription("Workspace Home").getBoundsInRoot(); assertTrue(home.width >= 40.dp)
    }
    @Test fun DCK_04_style_11_standardKeepsItsNodesAndTargets() = nodesAndTargets(DockStyle.Standard)
    @Test fun DCK_04_style_11_transparentKeepsItsNodesAndTargets() = nodesAndTargets(DockStyle.Transparent)
    @Test fun DCK_04_style_11_threeDKeepsItsNodesAndTargetsAndItsReflectionIsNotANode() = nodesAndTargets(DockStyle.ThreeD)

    private fun dotsAndPress(style: DockStyle) {
        rule.setContent { Wallpaper(false) { Rail(style) } }
        rule.onAllNodesWithTag("dock.activeDot", useUnmergedTree = true).assertCountEquals(2) // Mail and Notes are the active ones, in every style
        rule.onNodeWithContentDescription("Maps").performClick()
        assertEquals(listOf("launch Maps"), ran)
    }
    @Test fun DCK_04_style_12_standardKeepsTheActiveDotAndPress() = dotsAndPress(DockStyle.Standard)
    @Test fun DCK_04_style_12_transparentKeepsTheActiveDotAndPress() = dotsAndPress(DockStyle.Transparent)
    @Test fun DCK_04_style_12_threeDKeepsTheActiveDotAndPress() = dotsAndPress(DockStyle.ThreeD)

    private fun longPress(style: DockStyle) {
        rule.setContent { Wallpaper(false) { Rail(style) } }
        rule.onNodeWithContentDescription("Maps").performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag(DockMenuModel.TAG_ITEM_MENU).assertExists() // the icon's own menu
    }
    @Test fun DCK_04_style_13_standardLongPressOnAnIconStillOpensItsMenu() = longPress(DockStyle.Standard)
    @Test fun DCK_04_style_13_transparentLongPressOnAnIconStillOpensItsMenu() = longPress(DockStyle.Transparent)
    @Test fun DCK_04_style_13_threeDLongPressOnAnIconStillOpensItsMenu() = longPress(DockStyle.ThreeD)

    @Test fun DCK_04_style_14_theDockMenuHasDockStyleAndItOpensTheThreeChoicesAndStoresTheChosenOne() {
        rule.setContent { Wallpaper(false) { Rail(DockStyle.Standard) } }
        rule.onNodeWithContentDescription("Dock").performTouchInput { longClick(androidx.compose.ui.geometry.Offset(width - 4f, height / 2f)) }
        rule.waitForIdle()
        rule.onNodeWithText("Dock style").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("dock.styleDialog").assertExists()
        DockStyle.entries.forEach { rule.onNodeWithTag(DockStyleUi.rowTag(it)).assertExists() }
        rule.onNodeWithTag(DockStyleUi.rowTag(DockStyle.Standard)).assertIsSelected()
        rule.onNodeWithTag(DockStyleUi.rowTag(DockStyle.Transparent)).performClick(); rule.waitForIdle()
        assertEquals(listOf("style transparent"), ran)
        rule.onNodeWithTag("dock.styleDialog").assertDoesNotExist()
    }

    @Test fun DCK_04_style_15_theChooserHasOneRadioRowPerStyleWithAPreviewAndTheCurrentOneSelected() {
        var chosen = DockStyle.Transparent
        rule.setContent { Wallpaper(false) { Box(Modifier.padding(16.dp)) { DockStyleChooser(chosen, { chosen = it }) } } }
        DockStyle.entries.forEach { s ->
            rule.onNodeWithTag(DockStyleUi.rowTag(s)).assertExists()
            rule.onNodeWithTag(DockStyleUi.previewTag(s), useUnmergedTree = true).assertExists()
            val role = rule.onNodeWithTag(DockStyleUi.rowTag(s)).fetchSemanticsNode().config[SemanticsProperties.Role]
            assertEquals(androidx.compose.ui.semantics.Role.RadioButton, role)
            assertTrue(rule.onNodeWithTag(DockStyleUi.rowTag(s)).getBoundsInRoot().height >= 48.dp)
        }
        rule.onNodeWithTag(DockStyleUi.rowTag(DockStyle.Transparent)).assertIsSelected()
        rule.onNodeWithTag(DockStyleUi.rowTag(DockStyle.ThreeD)).performClick()
        assertEquals(DockStyle.ThreeD, chosen)
    }

    // --- pixels: what makes each style what it is -------------------------------------------------------------------------------------------

    private val flat = Color(0xFFE8DFC0)
    private fun lum(c: Int) = DockStyleTokens.luminance(c)

    /** The pixel 3 dp inside the dock's top edge, centred: in the dock's own padding, where there is no icon. */
    private fun padPixel(b: Bitmap): Int {
        val r = rule.onNodeWithContentDescription("Dock").getBoundsInRoot()
        val d = rule.activity.resources.displayMetrics.density
        return b.getPixel(((r.left.value + r.right.value) / 2 * d).toInt(), ((r.top.value + 3f) * d).toInt())
    }

    private fun pillPixel(s: DockStyle): Int { rule.setContent { Wallpaper(true, flat) { Rail(s, light = true) } }; return padPixel(capture()) }
    @Test fun DCK_04_style_20_transparentDrawsNoPanelAtAllNotEvenABorder() = assertEquals("the wallpaper itself", flat.toArgb(), pillPixel(DockStyle.Transparent))
    @Test fun DCK_04_style_20_standardHasItsPill() = assertNotEquals(flat.toArgb(), pillPixel(DockStyle.Standard))
    @Test fun DCK_04_style_20_threeDHasItsSlab() = assertNotEquals(flat.toArgb(), pillPixel(DockStyle.ThreeD))

    private fun Color.toArgb() = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

    @Test fun DCK_04_style_21_transparentGivesAWhiteIconAHaloOnAWhiteWallpaperSoItIsStillSeparatedFromIt() {
        theme(true)
        rule.setContent { Wallpaper(true, Color.White) { Rail(DockStyle.Transparent, light = true, icon = { Color.White }) } }
        val b = capture()
        val r = rule.onNodeWithContentDescription("Maps").getBoundsInRoot()
        val d = rule.activity.resources.displayMetrics.density
        // 6 dp from the slot edge is the icon's edge (40 dp icon in a 52 dp slot); sample 1.5 dp outside it, level with its middle
        val sample = b.getPixel(((r.left.value + 4.5f) * d).toInt(), ((r.top.value + r.height.value / 2) * d).toInt())
        val inside = b.getPixel(((r.left.value + r.width.value / 2) * d).toInt(), ((r.top.value + r.height.value / 2) * d).toInt())
        assertEquals("the icon itself is white", 0xFFFFFFFF.toInt(), inside)
        assertTrue("the wallpaper next to the icon is darkened by the halo: ${Integer.toHexString(sample)}", lum(sample) < 0.6)
        assertTrue("so a white icon outline reads against it (${DockStyleTokens.ratio(inside, sample)})", DockStyleTokens.ratio(inside, sample) >= 2.0)
    }

    @Test fun DCK_04_style_22_threeDHasAHighlightAGradientFaceAndAContactShadowBeneathTheSlab() {
        theme(true)
        rule.setContent { Wallpaper(true, flat) { Rail(DockStyle.ThreeD, light = true) } }
        val b = capture()
        val r = rule.onNodeWithContentDescription("Dock").getBoundsInRoot()
        val d = rule.activity.resources.displayMetrics.density
        val cx = ((r.left.value + r.right.value) / 2 * d).toInt()
        val top = b.getPixel(cx, ((r.top.value + 2f) * d).toInt())
        val mid = b.getPixel(cx, ((r.top.value + 6f) * d).toInt())
        val bottomFace = b.getPixel(cx, ((r.bottom.value - 3f) * d).toInt())
        assertNotEquals("the face is a gradient, not one flat colour", mid, bottomFace)
        assertTrue("the bottom of the face is darker than the top (${Integer.toHexString(mid)} vs ${Integer.toHexString(bottomFace)})", lum(bottomFace) < lum(mid))
        assertTrue("a highlight edge differs from the face below it", top != mid)
        // beneath the slab (inside the screen's bottom padding) the wallpaper is darkened by the contact shadow
        val below = b.getPixel(cx, ((r.bottom.value + 2f) * d).toInt())
        assertTrue("contact shadow beneath: ${Integer.toHexString(below)} darker than ${Integer.toHexString(flat.toArgb())}", lum(below) < lum(flat.toArgb()) - 0.01)
    }

    private fun slabFace(light: Boolean): Int {
        theme(light)
        rule.setContent { Wallpaper(light, flat) { Rail(DockStyle.ThreeD, light = light) } }
        val b = capture()
        val r = rule.onNodeWithContentDescription("Dock").getBoundsInRoot(); val d = rule.activity.resources.displayMetrics.density
        return b.getPixel(((r.left.value + r.right.value) / 2 * d).toInt(), ((r.top.value + 7f) * d).toInt())
    }
    @Test fun DCK_04_style_23_threeDDarkSlabIsDark() = assertTrue(lum(slabFace(false)) < 0.2)
    @Test fun DCK_04_style_23_threeDLightSlabIsLight() = assertTrue(lum(slabFace(true)) > 0.5)

    private fun rtl(s: DockStyle) {
        rule.setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { Wallpaper(false) { Rail(s) } } }
        val home = rule.onNodeWithContentDescription("Workspace Home").getBoundsInRoot(); val maps = rule.onNodeWithContentDescription("Maps").getBoundsInRoot()
        assertTrue("$s: home glyph at the start (right) in RTL", home.left > maps.left)
    }
    @Test fun DCK_04_style_24_standardRightToLeft() = rtl(DockStyle.Standard)
    @Test fun DCK_04_style_24_transparentRightToLeft() = rtl(DockStyle.Transparent)
    @Test fun DCK_04_style_24_threeDRightToLeft() = rtl(DockStyle.ThreeD)

    private fun bigFont(s: DockStyle) {
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(2f, fontScale = 2f)) { Wallpaper(false) { Rail(s) } } }
        val b = rule.onNodeWithContentDescription("Maps").getBoundsInRoot()
        assertTrue("$s ${b.width}", b.width >= 48.dp); assertTrue(b.height >= 48.dp)
    }
    @Test fun DCK_04_style_25_standardLargeFontScale() = bigFont(DockStyle.Standard)
    @Test fun DCK_04_style_25_transparentLargeFontScale() = bigFont(DockStyle.Transparent)
    @Test fun DCK_04_style_25_threeDLargeFontScale() = bigFont(DockStyle.ThreeD)

    // --- screenshots (read by a person; the assertions only check they were drawn) -------------------------------------------------------

    private fun photo(name: String, style: DockStyle, light: Boolean, edge: DockEdge) {
        theme(light)
        rule.setContent { Wallpaper(light) { Rail(style, edge, light) } }
        val b = shoot(name)
        assertTrue(File(outDir, "$name.png").length() > 2000); assertEquals(edge == DockEdge.Bottom, b.height > b.width)
    }

    @Test fun DCK_04_style_30_screenshotStandardDarkPortrait() = photo("w39-dock-standard-dark-portrait", DockStyle.Standard, false, DockEdge.Bottom)
    @Test fun DCK_04_style_30_screenshotStandardLightPortrait() = photo("w39-dock-standard-light-portrait", DockStyle.Standard, true, DockEdge.Bottom)
    @Test fun DCK_04_style_30_screenshotTransparentDarkPortrait() = photo("w39-dock-transparent-dark-portrait", DockStyle.Transparent, false, DockEdge.Bottom)
    @Test fun DCK_04_style_30_screenshotTransparentLightPortrait() = photo("w39-dock-transparent-light-portrait", DockStyle.Transparent, true, DockEdge.Bottom)
    @Test fun DCK_04_style_30_screenshotThreeDDarkPortrait() = photo("w39-dock-3d-dark-portrait", DockStyle.ThreeD, false, DockEdge.Bottom)
    @Test fun DCK_04_style_30_screenshotThreeDLightPortrait() = photo("w39-dock-3d-light-portrait", DockStyle.ThreeD, true, DockEdge.Bottom)

    @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi") @Test fun DCK_04_style_31_screenshotStandardDarkLandscape() = photo("w39-dock-standard-dark-landscape", DockStyle.Standard, false, DockEdge.Left)
    @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi") @Test fun DCK_04_style_31_screenshotStandardLightLandscape() = photo("w39-dock-standard-light-landscape", DockStyle.Standard, true, DockEdge.Left)
    @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi") @Test fun DCK_04_style_31_screenshotTransparentDarkLandscape() = photo("w39-dock-transparent-dark-landscape", DockStyle.Transparent, false, DockEdge.Left)
    @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi") @Test fun DCK_04_style_31_screenshotTransparentLightLandscape() = photo("w39-dock-transparent-light-landscape", DockStyle.Transparent, true, DockEdge.Left)
    @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi") @Test fun DCK_04_style_31_screenshotThreeDDarkLandscape() = photo("w39-dock-3d-dark-landscape", DockStyle.ThreeD, false, DockEdge.Left)
    @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi") @Test fun DCK_04_style_31_screenshotThreeDLightLandscape() = photo("w39-dock-3d-light-landscape", DockStyle.ThreeD, true, DockEdge.Left)

    @Test fun DCK_04_style_32_screenshotTheChooser() {
        theme(false)
        rule.setContent { Wallpaper(false) { Box(Modifier.padding(16.dp)) { DockStyleChooser(DockStyle.ThreeD, {}) } } }
        shoot("w39-dock-style-chooser-dark")
    }
}
