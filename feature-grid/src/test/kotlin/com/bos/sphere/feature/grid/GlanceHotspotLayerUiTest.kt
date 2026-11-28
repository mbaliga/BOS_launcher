package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.MinimalHotspots
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
 * HOM-22, INP-16, INP-17, INP-07 (wave 26: the landing selector is gone, the hotspot layer of Glance stays): the four hotspots are labelled buttons on the
 * focus graph. Both orientations, light and dark, right to left. Screenshots go to
 * build/screenshots/home_landing_*.png. Robolectric draws no compositor and no real window: none of this is a device run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlanceHotspotLayerUiTest {
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
        File(outDir, "glance_hotspots_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val labels = listOf("Alpha", "Bravo", "Camera", "Delta", "Echo", "Foxtrot")
    private val apps = labels.mapIndexed { i, l -> AppEntry("demo.$l", ComponentName("demo.$l", "M"), l, Process.myUserHandle()) { ColorDrawable(0xFF3070D0.toInt() + i * 0x101010) } }
    private val byKey = apps.associateBy { it.key }

    private fun hotspots(): List<Hotspot> = MinimalHotspots.defaults.map { (c, slot) -> Hotspot(c, MinimalHotspots.itemOf(slot)!!) }

    private fun minimal(rtl: Boolean = false, hints: Boolean = true, run: MutableList<Pair<CornerItem, Corner>> = ArrayList()) = rule.setContent {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Box(Modifier.fillMaxSize().background(Color(0xFF30406A))) {
                MinimalHotspotLayer(hotspots(), byKey, hints, onRun = { i, c -> run += i to c })
            }
        }
    }

    // ---- Minimal: the four hotspots

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_22_minimalShowsFourLabelledHotspotButtonsOnTheFocusGraphAndClickRunsThem() {
        val ran = ArrayList<Pair<CornerItem, Corner>>()
        minimal(run = ran)
        for (d in listOf("Top left hotspot: Radar", "Top right hotspot: Comms", "Bottom left hotspot: All apps", "Bottom right hotspot: Home settings")) {
            val n = rule.onAllNodesWithContentDescription(d, substring = true)
            assertEquals("one button for $d", 1, n.fetchSemanticsNodes().size)
        }
        val ids = LauncherInput.focus.nodes().filter { it.id.startsWith("minimal.corner.") }.map { it.id }.toSet()
        assertEquals("four nodes on the focus graph", setOf("minimal.corner.TopLeft", "minimal.corner.TopRight", "minimal.corner.BottomLeft", "minimal.corner.BottomRight"), ids)
        rule.onNodeWithContentDescription("Top right hotspot: Comms", substring = true).performClick()
        assertEquals(listOf(Corner.TopRight), ran.map { it.second })
        assertEquals(CornerItem.Action(CornerActionId.OpenLaunchpad), ran.single().first)
        shoot("minimal_portrait_dark")
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_22_minimalLandscapeKeepsAllFourHotspotsOnTheGraph() {
        minimal()
        assertEquals(4, LauncherInput.focus.nodes().count { it.id.startsWith("minimal.corner.") })
        val b = LauncherInput.focus.nodes().associate { it.id to it.rect }
        // physical corners: top left is left of top right and above bottom left, whatever the orientation
        assertTrue(b.getValue("minimal.corner.TopLeft").left < b.getValue("minimal.corner.TopRight").left)
        assertTrue(b.getValue("minimal.corner.TopLeft").top < b.getValue("minimal.corner.BottomLeft").top)
        shoot("minimal_landscape_dark")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_22_minimalRtlKeepsThePhysicalCornersAndLightThemeRenders() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF) }
        minimal(rtl = true)
        val b = LauncherInput.focus.nodes().associate { it.id to it.rect }
        assertTrue("top left stays on the physical left in right to left", b.getValue("minimal.corner.TopLeft").left < b.getValue("minimal.corner.TopRight").left)
        shoot("minimal_portrait_light_rtl")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_22_minimalWithoutHintsStillHasTheFourButtons() {
        minimal(hints = false)
        assertEquals(4, LauncherInput.focus.nodes().count { it.id.startsWith("minimal.corner.") })
        shoot("minimal_no_hints")
    }

    // ---- switching UI

}
