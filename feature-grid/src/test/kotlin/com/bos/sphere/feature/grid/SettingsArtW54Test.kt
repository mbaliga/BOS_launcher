package com.bos.sphere.feature.grid

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.bos.sphere.core.catalog.UsageRange
import com.bos.sphere.core.data.AlertEffect
import com.bos.sphere.core.data.CalmEffect
import com.bos.sphere.core.data.ClockFormat
import com.bos.sphere.core.data.ClockSize
import com.bos.sphere.core.data.ClockStyle
import com.bos.sphere.core.data.CornerIconMode
import com.bos.sphere.core.data.CornerStyle
import com.bos.sphere.core.data.DensityPreset
import com.bos.sphere.core.data.HomeOrder
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.HotspotPreset
import com.bos.sphere.core.data.IconShape
import com.bos.sphere.core.data.IconTint
import com.bos.sphere.core.data.IndicatorMetric
import com.bos.sphere.core.data.LeftPage
import com.bos.sphere.core.data.PageMode
import com.bos.sphere.core.data.PageShape
import com.bos.sphere.core.data.PagingAxis
import com.bos.sphere.core.data.PuckActions
import com.bos.sphere.core.data.RadarBackground
import com.bos.sphere.core.data.RadarIntensity
import com.bos.sphere.core.data.RightSide
import com.bos.sphere.core.data.ScrollAxis
import com.bos.sphere.core.data.SearchLayout
import com.bos.sphere.core.data.SettingsBackdrop
import com.bos.sphere.core.data.ThemeMode
import com.bos.sphere.core.data.WallpaperKind
import com.bos.sphere.core.data.WebEngine
import com.bos.sphere.core.data.WheelGrouping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Wave 54 (settingsVisual), CFG-23: every value of every choice that is shown as cards has an illustration, it draws something, in the dark and
 * in the light ink, and no two values of one setting draw the same picture (except the search engines, which are told apart by name, never by logo).
 * The lists below are the enums' own `entries`: a new enum value is covered the moment it exists, and without a drawing in its provider the build
 * fails (an exhaustive `when`) and so does this test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SettingsArtW54Test {
    private val w = 160; private val h = 128

    private fun render(art: VisualArt, ink: VisualInk): IntArray {
        val bmp = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(bmp), Size(w.toFloat(), h.toFloat())) { art(ink) }
        val px = IntArray(w * h)
        bmp.asAndroidBitmap().getPixels(px, 0, w, 0, 0, w, h)
        return px
    }

    private fun <T> check(name: String, expected: List<T>, set: ArtSet<T>, distinct: Boolean = true) {
        for (v in expected) assertTrue("$name: $v has no illustration slot", v in set.values)
        assertEquals("$name: the set covers exactly the values", expected.toSet(), set.values.toSet())
        for (ink in listOf(VisualInk.Dark, VisualInk.Light)) {
            val shots = expected.map { v ->
                val px = render(set.art(v), ink)
                val drawn = px.count { (it ushr 24) != 0 }
                assertTrue("$name: $v draws something (drawn pixels $drawn)", drawn > 40)
                px.toList()
            }
            if (distinct) assertEquals("$name: every value draws its own picture", expected.size, shots.toSet().size)
        }
    }

    @Test fun CFG_23_everyHomeChoiceHasAnIllustrationPerValue() {
        check("Pages", HomePages.entries, SettingsArtHome.pages)
        check("Order", HomeOrder.entries, SettingsArtHome.order)
        check("Right side", RightSide.entries, SettingsArtHome.rightSide)
        check("Style", HomeStyle.entries, SettingsArtHome.homeStyle)
        check("Layout", PageShape.entries, SettingsArtHome.pageShape)
        check("Page turning", PagingAxis.entries, SettingsArtHome.paging)
        check("Scroll direction", ScrollAxis.entries, SettingsArtHome.scrollAxis)
        check("Default density", DensityPreset.entries, SettingsArtHome.density)
        check("Hotspots", HotspotPreset.entries, SettingsArtHome.hotspots)
        check("Corner style", CornerStyle.entries, SettingsArtHome.cornerStyle)
        check("Icon type", CornerIconMode.entries, SettingsArtHome.cornerIcon)
        check("Left page", LeftPage.entries, SettingsArtHome.leftPage)
        check("Radar layout", SearchLayout.entries, SettingsArtHome.searchLayout)
        check("Radar background", RadarBackground.entries, SettingsArtHome.radarBackground)
        check("Wheel groups", WheelGrouping.entries, SettingsArtHome.wheelGrouping)
        check("Drawer pages", PageMode.entries, SettingsArtHome.pageMode)
        check("Ring segments", PuckActions.SEGMENT_CHOICES, SettingsArtHome.ringSegments)
    }

    @Test fun CFG_23_everyLookAndGlanceChoiceHasAnIllustrationPerValue() {
        check("Clock style", ClockStyle.entries, SettingsArtLook.clockStyle)
        check("Clock format", ClockFormat.entries.filter { it != ClockFormat.System }, SettingsArtLook.clockFormat.let { set -> ArtSet(ClockFormat.entries.filter { it != ClockFormat.System }) { set.art(it) } })
        check("Clock size", ClockSize.entries, SettingsArtLook.clockSize)
        check("Clock colour", listOf(false, true), SettingsArtLook.clockColour)
        check("Light / dark", ThemeMode.entries, SettingsArtLook.themeMode)
        check("Icon shape", IconShape.entries, SettingsArtLook.iconShape)
        check("Icon colour", IconTint.entries, SettingsArtLook.iconTint)
        check("Settings backdrop", SettingsBackdrop.entries, SettingsArtLook.backdrop)
        check("Calm effect", listOf<CalmEffect?>(null) + CalmEffect.entries, SettingsArtLook.calm)
        check("Alert effect", listOf<AlertEffect?>(null) + AlertEffect.entries, SettingsArtLook.alert)
        check("Show", IndicatorMetric.entries, SettingsArtLook.metric)
        check("Web engine", WebEngine.entries, SettingsArtLook.webEngine, distinct = false)
        check("Wallpaper type", WallpaperKind.entries, SettingsArtLook.wallpaperKind)
        check("Radar intensity", RadarIntensity.entries, SettingsArtLook.radarIntensity)
        check("Usage range", UsageRange.entries, SettingsArtLook.usageRange)
    }

    @Test fun CFG_23_theSameDrawingServesTheDarkAndTheLightInk() {
        // the strong strokes follow the ink: a clock drawn in the dark ink is not the same picture as in the light one
        val d = render(SettingsArtLook.clockStyle.art(ClockStyle.BigDigital), VisualInk.Dark)
        val l = render(SettingsArtLook.clockStyle.art(ClockStyle.BigDigital), VisualInk.Light)
        assertTrue(!d.contentEquals(l))
    }

    @Test fun CFG_23_theClockFormatSetCoversTheSystemValueToo() {
        // System (follow the device) is not offered as a card (the row shows the format it resolves to), but a drawing exists for it
        assertTrue(ClockFormat.System in SettingsArtLook.clockFormat.values)
    }
}
