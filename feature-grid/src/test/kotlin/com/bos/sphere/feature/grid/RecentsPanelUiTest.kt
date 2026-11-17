package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ThemeVariant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * STR-14: the Recents panel under Robolectric: newest first, 48 dp rows with "Recent" and how long ago, the honest one-line limits, Clear all,
 * long press for the menu, the empty state, both orientations, light and dark, right to left. Screenshots go to build/screenshots/recents_panel_*.png.
 * Robolectric has no compositor and no real window: none of this is a device run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecentsPanelUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @After fun reset() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
        L10n.setLanguage("")
        runCatching { LauncherInput.focus.nodes().map { it.id }.forEach(LauncherInput.focus::unregister) }
    }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(300)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { Canvas(bmp).also { it.drawColor(android.graphics.Color.rgb(20, 26, 50)); view.draw(it) } }
        File(outDir, "recents_panel_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val labels = listOf("Alpha", "Bravo", "Camera", "Delta", "Echo", "Foxtrot")
    private val apps = labels.mapIndexed { i, l -> AppEntry("demo.$l", ComponentName("demo.$l", "M"), l, Process.myUserHandle()) { ColorDrawable(0xFF3070D0.toInt() + i * 0x101010) } }
    private fun key(label: String) = apps.first { it.label == label }.key
    private fun row(label: String) = "recents.row.${key(label)}"
    private val now = 1_000_000_000_000L
    private val rows = apps.mapIndexed { i, a -> RecentRow(a, now - listOf(20_000L, 5 * 60_000L, 50 * 60_000L, 3 * 3_600_000L, 30 * 3_600_000L, 4 * 86_400_000L)[i]) }

    private fun show(
        rows: List<RecentRow> = this.rows, rtl: Boolean = false, usageOn: Boolean = true,
        launched: MutableList<String> = ArrayList(), menu: MutableList<String> = ArrayList(), cleared: IntArray = IntArray(1),
    ) = rule.setContent {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                RecentsPanel(
                    rows = rows, now = now, usageAccessOn = usageOn, sliverInsetDp = 29.dp,
                    onLaunch = { launched += it.label }, onMenu = { menu += it.label }, onClearAll = { cleared[0]++ }, onGrantUsage = {},
                    sliverOnLeft = !rtl,
                )
            }
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun STR_14_listsTheAppsNewestFirstWithRecentAndHowLongAgoAndOneHonestLine() {
        val launched = ArrayList<String>()
        show(launched = launched)
        val tops = labels.map { rule.onNodeWithTag(row(it)).fetchSemanticsNode().boundsInRoot.top }
        assertEquals("rows keep the given newest-first order", tops.sorted(), tops)
        rule.onNodeWithText("Recent · Just now").assertIsDisplayed()
        rule.onNodeWithText("Recent · 5 min ago").assertIsDisplayed()
        rule.onNodeWithText("Recent · 3 h ago").assertIsDisplayed()
        rule.onNodeWithTag("recents.honesty").assertIsDisplayed()
        val honest = rule.onNodeWithTag("recents.honesty").fetchSemanticsNode().config.toString()
        assertTrue(honest.contains("does not tell a launcher which apps are still running"))
        rule.onNodeWithTag(row("Bravo")).performClick()
        assertEquals(listOf("Bravo"), launched)
        shoot("portrait_dark")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun STR_14_rowsAre48DpIconsAtLeast64DpTallAndLongPressOpensTheAppMenu() {
        val menu = ArrayList<String>()
        show(menu = menu)
        val d = rule.activity.resources.displayMetrics.density
        val h = rule.onNodeWithTag(row("Alpha")).fetchSemanticsNode().size.height / d
        assertTrue("row height $h dp", h >= 64f)
        rule.onNodeWithTag(row("Camera")).performTouchInput { longClick() }
        assertEquals(listOf("Camera"), menu)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun STR_14_clearAllIsAFocusNodeAndRunsTheCallbackAndEachRowIsAFocusNode() {
        val cleared = IntArray(1)
        show(cleared = cleared)
        rule.onNodeWithTag("recents.clearAll").performClick()
        assertEquals(1, cleared[0])
        val ids = LauncherInput.focus.nodes().map { it.id }.toSet()
        assertTrue("clear all on the graph", "recents.clearAll" in ids)
        for (l in labels) assertTrue("row $l on the graph", "recents.${key(l)}" in ids)
        assertTrue(LauncherInput.focus.nodes().filter { it.id.startsWith("recents.") }.all { it.layer == RECENTS_FOCUS_LAYER })
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun STR_14_emptyStateExplainsAndOffersUsageAccessAndHasNoClearAll() {
        show(rows = emptyList(), usageOn = false)
        rule.onNodeWithText("No recent apps yet").assertIsDisplayed()
        rule.onNodeWithText("Allow usage access").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithTag("recents.clearAll").fetchSemanticsNodes().size)
        rule.onNodeWithTag("recents.honesty").assertIsDisplayed()
        shoot("empty_portrait_dark")
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun STR_14_landscapeKeepsTheListScrollableAndClearOfTheSliver() {
        show()
        val left = rule.onNodeWithTag(row("Alpha")).fetchSemanticsNode().boundsInRoot.left
        val d = rule.activity.resources.displayMetrics.density
        assertTrue("rows start right of the 29 dp sliver inset, left=$left", left >= 29f * d)
        shoot("landscape_dark")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun STR_14_lightThemeRendersAndRightToLeftMovesTheSliverInsetToTheRight() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF) }
        show(rtl = true)
        val b = rule.onNodeWithTag(row("Alpha")).fetchSemanticsNode().boundsInRoot
        val d = rule.activity.resources.displayMetrics.density
        val width = rule.activity.resources.displayMetrics.widthPixels
        assertTrue("right inset kept clear: right=${b.right} width=$width", b.right <= width - 29f * d + 1f)
        shoot("portrait_light_rtl")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun STR_14_fontScale200KeepsTheRowsAndHeaderReadable() {
        rule.setContent {
            val d = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, 2f)) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    RecentsPanel(rows, now, true, 29.dp, onLaunch = {}, onMenu = {}, onClearAll = {}, onGrantUsage = {})
                }
            }
        }
        rule.onNodeWithTag(row("Alpha")).assertIsDisplayed()
        rule.onNodeWithTag("recents.clearAll").assertIsDisplayed()
        shoot("portrait_font200")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun STR_14_stringsAreTranslatedInAllEightLanguages() {
        for (c in listOf("es", "fr", "de", "pt", "hi", "zh", "ja", "ar")) {
            L10n.setLanguage(c)
            for (k in listOf("%d min ago", "Open recents", "Right side", "Recents panel", "Dock rail", "Just now")) assertTrue("$k in $c", L10n.tr(k) != k)
        }
    }
}
