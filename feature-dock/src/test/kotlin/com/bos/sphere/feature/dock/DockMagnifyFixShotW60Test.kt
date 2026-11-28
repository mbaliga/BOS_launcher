package com.bos.sphere.feature.dock

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.DockStyle
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
 * Wave 60 (dockMagnifyFix): screenshots of a crowded dock (8 items), fully magnified with the finger at EACH end of the row — the owner's exact
 * complaint ("the leftmost and rightmost icons are visibly cropped/bleeding outside the dock's rounded-rect background at both ends"). Written to
 * build/screenshots/w60-*.png and READ (the Read tool) against that complaint for Standard, Transparent and 3D; the assertion only checks that
 * something was drawn, the judgement is visual.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w650dp-h400dp-land-xhdpi") // "land" (not "port": with w > h, Robolectric otherwise swaps them to stay portrait) so the
// screen is actually 650 dp wide — wide enough that 8 icons do not scroll at rest, the owner's exact complaint (a dock that "clearly isn't
// overflowing" still getting cropped once magnified), not the separately-covered case of a dock that already scrolls.
class DockMagnifyFixShotW60Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private val tile = listOf(Color(0xFF4F8CFF), Color(0xFFFF7A59), Color(0xFF3DDC84), Color(0xFFE6B800), Color(0xFFB36BFF), Color(0xFF29C5D6), Color(0xFFE5484D), Color(0xFF8E8E93))

    @Before fun setUp() { L10n.setLanguage("en"); DockInput.dialogHook = {}; ReducedMotionTesting.force(MotionPrefs(1f, 1f, 1f)) }
    @After fun tearDown() { ReducedMotionTesting.force(null); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }

    private fun content(n: Int) = DockContent(
        pinned = (0 until n).map { DockItem(DockItemRef.app("app$it"), "App ${"ABCDEFGHIJKL"[it]}", false) },
        recent = emptyList(), showEnableRow = false, showRecentSection = false, collapsed = false,
    )

    @Composable private fun Wall(body: @Composable () -> Unit) {
        val brush = Brush.linearGradient(listOf(Color(0xFF3A5FA8), Color(0xFF6B4FB0), Color(0xFF1E2E5C)))
        Box(Modifier.fillMaxSize().background(brush).padding(16.dp), contentAlignment = Alignment.BottomCenter) { body() }
    }

    @Composable private fun Rail(n: Int, style: DockStyle) {
        DockRail(content(n), DockEdge.Bottom, "Home", expanded = true, focusRequest = 0, showNumbers = false, actions = DockActions(), graphBridge = null, style = style, dark = true, magnify = MagnifyStrength.Large) { item, s ->
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

    private fun atEachEnd(name: String, style: DockStyle, n: Int = 8) {
        HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF)
        rule.setContent { Wall { Rail(n, style) } }
        rule.onNodeWithContentDescription("App A").performTouchInput { down(center); moveBy(Offset(-20f, 0f)) } // the very first icon, finger towards its outer edge
        rule.mainClock.advanceTimeBy(350)
        shoot("$name-left-end")
        rule.onNodeWithContentDescription("App A").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(700)
        val last = "App ${"ABCDEFGHIJKL"[n - 1]}"
        rule.onNodeWithContentDescription(last).performTouchInput { down(center); moveBy(Offset(20f, 0f)) } // the very last icon, finger towards its outer edge
        rule.mainClock.advanceTimeBy(350)
        shoot("$name-right-end")
        rule.onNodeWithContentDescription(last).performTouchInput { up() }
    }

    @Test fun DCK_04_fix_shot_01_standardEightIconsFullyMagnifiedAtEachEnd() = atEachEnd("w60-standard", DockStyle.Standard)
    @Test fun DCK_04_fix_shot_02_transparentEightIconsFullyMagnifiedAtEachEnd() = atEachEnd("w60-transparent", DockStyle.Transparent)
    @Test fun DCK_04_fix_shot_03_threeDEightIconsFullyMagnifiedAtEachEnd() = atEachEnd("w60-3d", DockStyle.ThreeD)
}
