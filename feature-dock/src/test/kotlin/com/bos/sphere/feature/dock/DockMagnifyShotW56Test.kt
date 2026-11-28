package com.bos.sphere.feature.dock

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.capabilities.Tier
import com.bos.sphere.core.data.DockStyle
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.core.quality.MotionPrefs
import org.junit.After
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
 * Wave 56 (dockMagnify): screenshots of the 3D shelf at rest and magnified (light and dark, bottom and side docks, right to left, a long dock that
 * scrolls) and of the option cards. They are written to build/screenshots/w56-*.png and READ by a person against the reference picture; the
 * assertions only check that something was drawn.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h400dp-port-xhdpi")
class DockMagnifyShotW56Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private val tile = listOf(Color(0xFF4F8CFF), Color(0xFFFF7A59), Color(0xFF3DDC84), Color(0xFFE6B800), Color(0xFFB36BFF), Color(0xFF29C5D6), Color(0xFFE5484D), Color(0xFF8E8E93), Color(0xFF1FB6A6), Color(0xFFD9468F))

    @Before fun setUp() { L10n.setLanguage("en"); DockInput.dialogHook = {}; ReducedMotionTesting.force(MotionPrefs(1f, 1f, 1f)) }
    @After fun tearDown() { ReducedMotionTesting.force(null); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }

    private fun content(n: Int) = DockContent(
        pinned = (0 until n).map { DockItem(DockItemRef.app("app$it"), "App ${"ABCDEFGHIJKL"[it]}", it == 1 || it == 3) },
        recent = emptyList(), showEnableRow = false, showRecentSection = false, collapsed = false,
    )

    @Composable private fun Wall(light: Boolean, align: Alignment, rtl: Boolean = false, body: @Composable () -> Unit) {
        val brush = if (light) Brush.linearGradient(listOf(Color(0xFFFFF3C4), Color(0xFFBFE3F5), Color(0xFFF7D6E0))) else Brush.linearGradient(listOf(Color(0xFF3A5FA8), Color(0xFF6B4FB0), Color(0xFF1E2E5C)))
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Box(Modifier.fillMaxSize().background(brush).padding(16.dp), contentAlignment = align) { body() }
        }
    }

    @Composable private fun Rail(n: Int, light: Boolean, style: DockStyle, magnify: MagnifyStrength?, edge: DockEdge = DockEdge.Bottom) {
        DockRail(content(n), edge, "Home", expanded = true, focusRequest = 0, showNumbers = false, actions = DockActions(), graphBridge = null, style = style, dark = !light, magnify = magnify) { item, s ->
            GlossyTile(tile[item.ref.id.removePrefix("app").toInt() % tile.size], s)
        }
    }

    private fun shoot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(outDir, "$name.png").length() > 2000)
    }

    private fun theme(light: Boolean) = HyleTheme.apply(if (light) ThemeVariant.Light else ThemeVariant.Dark, 0xFF8E7BFF)

    private fun restAndMagnified(name: String, light: Boolean, n: Int, style: DockStyle, magnify: MagnifyStrength, edge: DockEdge = DockEdge.Bottom, rtl: Boolean = false, on: String = "App C", along: Offset = Offset(6f, 0f)) {
        theme(light)
        val align = when (edge) { DockEdge.Bottom -> Alignment.BottomCenter; DockEdge.Left -> Alignment.CenterStart; DockEdge.Right -> Alignment.CenterEnd }
        rule.setContent { Wall(light, align, rtl) { Rail(n, light, style, magnify, edge) } }
        shoot("$name-rest")
        rule.onNodeWithContentDescription(on).performTouchInput { down(center); moveBy(along) }
        rule.mainClock.advanceTimeBy(330)
        shoot("$name-magnified")
        rule.onNodeWithContentDescription(on).performTouchInput { up() }
    }

    @Test fun DCK_04_mag_shot_01_threeDDarkBottom() = restAndMagnified("w56-3d-dark", false, 5, DockStyle.ThreeD, MagnifyStrength.Medium)
    @Test fun DCK_04_mag_shot_01_threeDLightBottomLarge() = restAndMagnified("w56-3d-light", true, 5, DockStyle.ThreeD, MagnifyStrength.Large)
    @Test fun DCK_04_mag_shot_02_threeDDarkFourIconsSubtle() = restAndMagnified("w56-3d-dark-subtle", false, 4, DockStyle.ThreeD, MagnifyStrength.Subtle, on = "App B, used this session")
    @Test fun DCK_04_mag_shot_03_threeDLongDockThatScrolls() = restAndMagnified("w56-3d-dark-long", false, 10, DockStyle.ThreeD, MagnifyStrength.Medium, on = "App D, used this session")
    @Test fun DCK_04_mag_shot_04_standardWithMagnificationAndTooltip() = restAndMagnified("w56-standard-dark", false, 4, DockStyle.Standard, MagnifyStrength.Medium)
    @Test fun DCK_04_mag_shot_04_transparentWithMagnificationAndTooltipLight() = restAndMagnified("w56-transparent-light", true, 4, DockStyle.Transparent, MagnifyStrength.Medium)
    @Test fun DCK_04_mag_shot_05_threeDRightToLeft() = restAndMagnified("w56-3d-dark-rtl", false, 5, DockStyle.ThreeD, MagnifyStrength.Medium, rtl = true, along = Offset(-6f, 0f))

    @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    @Test fun DCK_04_mag_shot_06_threeDLeftSideDock() = restAndMagnified("w56-3d-dark-left", false, 5, DockStyle.ThreeD, MagnifyStrength.Medium, DockEdge.Left, along = Offset(0f, 6f))
    @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    @Test fun DCK_04_mag_shot_06_threeDRightSideDockLight() = restAndMagnified("w56-3d-light-right", true, 5, DockStyle.ThreeD, MagnifyStrength.Medium, DockEdge.Right, along = Offset(0f, 6f))

    private fun cards(name: String, light: Boolean, style: DockStyle) {
        theme(light)
        val m = WorkspaceManager(InMemoryWorkspaceStore()).also { it.setDock { d -> d.copy(style = style, magnify = true, magnifyStrength = MagnifyStrength.Large) } }
        rule.setContent { Box(Modifier.fillMaxSize().background(HyleColors.Background)) { Column(Modifier.padding(8.dp)) { DockOptionsBody(m, Tier.T2, DockOptionsScope()) } } }
        shoot(name)
    }
    @Config(sdk = [35], qualifiers = "w411dp-h1100dp-port-xhdpi")
    @Test fun DCK_04_mag_shot_07_optionCardsDark() = cards("w56-cards-dark", false, DockStyle.ThreeD)
    @Config(sdk = [35], qualifiers = "w411dp-h1100dp-port-xhdpi")
    @Test fun DCK_04_mag_shot_07_optionCardsLight() = cards("w56-cards-light", true, DockStyle.ThreeD)

    @Test fun DCK_04_mag_shot_08_theSettingsLivePreviewShowsTheShelfAndMagnifies() {
        theme(false)
        val m = WorkspaceManager(InMemoryWorkspaceStore()).also { it.setDock { d -> d.copy(style = DockStyle.ThreeD, magnify = true) } }
        m.setPins(listOf("a", "b", "c", "d").map { DockItemRef.app(it) })
        val apps = listOf("Alpha", "Bravo", "Chart", "Delta").mapIndexed { i, l -> PanelApp("abcd"[i].toString(), l, "p.$l", com.bos.sphere.core.launch.LaunchTarget("p.$l", null, 0)) }
        rule.setContent { Box(Modifier.fillMaxSize().background(HyleColors.Background)) { DockLivePreview(m, apps, landscape = false, modifier = Modifier.padding(top = 24.dp)) } }
        shoot("w56-preview-rest")
        rule.onNodeWithContentDescription("Dock preview. 3D, Bottom.").performTouchInput { down(Offset(width * 0.5f, height * 0.8f)); moveBy(Offset(6f, 0f)) }
        rule.mainClock.advanceTimeBy(330)
        shoot("w56-preview-magnified")
        rule.onNodeWithContentDescription("Dock preview. 3D, Bottom.").performTouchInput { up() }
    }
}
