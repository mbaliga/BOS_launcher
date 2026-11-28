package com.bos.sphere.feature.dock

import com.bos.sphere.core.data.DockStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 39 (dockThemes): the dock style numbers and the stored setting. Pure JVM. */
class DockStyleTokensW39Test {
    private val wallpapers = listOf(0xFFFFFFFF.toInt(), 0xFFF5F5F5.toInt(), 0xFFFFF59D.toInt(), 0xFFB0BEC5.toInt(), 0xFF80CBC4.toInt(), 0xFFFFCC80.toInt(), 0xFF808080.toInt(), 0xFF101820.toInt())

    @Test fun DCK_04_style_01_aWhiteGlyphOnItsHaloReachesThreeToOneOnEveryWallpaperIncludingWhite() {
        wallpapers.forEach { w -> assertTrue("0x${Integer.toHexString(w)}: ${DockStyleTokens.glyphContrastOnHalo(w)}", DockStyleTokens.glyphContrastOnHalo(w) >= DockStyleTokens.MIN_CONTRAST) }
    }

    @Test fun DCK_04_style_01_aWhiteIconOutlineAtTheIconsEdgeReachesThreeToOneOnLightAndBusyWallpapers() {
        wallpapers.forEach { w -> assertTrue("0x${Integer.toHexString(w)}: ${DockStyleTokens.iconEdgeContrast(w)}", DockStyleTokens.iconEdgeContrast(w) >= DockStyleTokens.MIN_CONTRAST) }
        // and on pure white, with no halo at all, it would not (this is what the halo is for)
        assertTrue(DockStyleTokens.ratio(DockStyleTokens.GLYPH, 0xFFFFFFFF.toInt()) < 1.1)
    }

    @Test fun DCK_04_style_01_theHaloIsStrongestAtTheMiddleFadesToNothingAtTheRimAndStaysInsideTheSlot() {
        assertEquals(DockStyleTokens.HALO_ALPHA, DockStyleTokens.haloAlphaAt(0f), 0.0001f)
        assertEquals(0f, DockStyleTokens.haloAlphaAt(1f), 0.0001f)
        var last = 1f
        for (i in 0..20) { val a = DockStyleTokens.haloAlphaAt(i / 20f); assertTrue("monotonic at $i", a <= last + 1e-6f); last = a }
        assertTrue("the rim (${DockStyleTokens.ICON_DP * DockStyleTokens.HALO_RADIUS} dp) is inside the ${DockStyleTokens.SLOT_DP} dp slot, so the slot never cuts it", DockStyleTokens.ICON_DP * DockStyleTokens.HALO_RADIUS <= DockStyleTokens.SLOT_DP / 2)
    }

    @Test fun DCK_04_style_02_theShelfHasLightAndDarkGlassWithABrightBevelAndADarkerLip() {
        val d = DockStyleTokens.shelf(true); val l = DockStyleTokens.shelf(false)
        fun a(c: Int) = c ushr 24
        listOf(d, l).forEach { s ->
            assertTrue("the glass is translucent so the wallpaper shows through", a(s.floorBack) in 0x30..0xE0 && a(s.floorFront) in 0x20..0xE0)
            assertTrue("the floor is lighter at the back than at the front (it recedes)", a(s.floorBack) > a(s.floorFront))
            assertTrue("the bevel line along the back edge is bright white", (s.topLine and 0xFFFFFF) == 0xFFFFFF && a(s.topLine) >= 0xC0)
            assertTrue("the lip is darker than the floor's glass", DockStyleTokens.luminance(DockStyleTokens.blend(s.lipBottom, 0xFF808080.toInt(), a(s.lipBottom) / 255.0)) < DockStyleTokens.luminance(DockStyleTokens.blend(s.floorFront, 0xFF808080.toInt(), a(s.floorFront) / 255.0)))
            assertTrue("shadows are black", (s.shadow and 0xFFFFFF) == 0 && (s.iconShadow and 0xFFFFFF) == 0)
        }
        assertNotEquals("the light and dark glass differ", d.floorBack, l.floorBack)
        assertTrue("the light shelf reads lighter than the dark one over the same wallpaper", DockStyleTokens.luminance(DockStyleTokens.blend(l.floorBack, 0xFF607090.toInt(), (l.floorBack ushr 24) / 255.0)) > DockStyleTokens.luminance(DockStyleTokens.blend(d.floorBack, 0xFF607090.toInt(), (d.floorBack ushr 24) / 255.0)) - 0.0001)
    }

    @Test fun DCK_04_style_02_theShelfGeometryLeavesTheReflectionFloorAndTheRailNoTaller() {
        assertEquals("a slot gives the shelf side exactly the rail's dropped padding", 10f, DockStyleTokens.SLOT_EXTRA_DP)
        assertEquals(0f, DockStyleTokens.shelfSidePad(10f))
        assertEquals("the rail keeps its 72 dp thickness: 10 + 62 + 0", 72f, 10f + DockStyleTokens.SLOT_DP + DockStyleTokens.SLOT_EXTRA_DP + DockStyleTokens.shelfSidePad(10f))
        assertTrue("the reflection fits on the floor between the icon's foot and the lip", DockStyleTokens.REFLECT_DP > 4f && DockStyleTokens.REFLECT_DP <= DockStyleTokens.SHELF_DEPTH_DP - DockStyleTokens.LIP_DP)
        assertTrue("the back edge is narrower than the front", DockStyleTokens.BACK_INSET > 0.2f && DockStyleTokens.BACK_INSET < 0.7f)
    }

    @Test fun DCK_04_style_03_onlyTransparentHasNoSurfaceAndNothingChangesATouchTarget() {
        assertEquals(listOf(true, false, true), DockStyle.entries.map { DockStyleTokens.hasSurface(it) })
        assertEquals(52f, DockStyleTokens.SLOT_DP); assertTrue(DockStyleTokens.SLOT_DP >= 48f)
    }

    @Test fun DCK_04_style_05_theStyleIsStoredWithTheDockSettingsAndOlderFilesAreStandard() {
        val s = DockWorkspaceState(dock = DockSettings(style = DockStyle.ThreeD), workspaces = listOf(Workspace("default", "Home")))
        val text = WorkspaceJson.encode(s)
        assertTrue(text.contains("\"style\":\"3d\""))
        assertEquals(DockStyle.ThreeD, (WorkspaceJson.decode(text) as WorkspaceJson.Decoded.Ok).state.dock.style)
        assertEquals(DockStyle.Standard, (WorkspaceJson.decode(text.replace(",\"style\":\"3d\"", "")) as WorkspaceJson.Decoded.Ok).state.dock.style)
        assertEquals("an unknown value from a newer build is the default", DockStyle.Standard, (WorkspaceJson.decode(text.replace("\"3d\"", "\"holo\"")) as WorkspaceJson.Decoded.Ok).state.dock.style)
        assertEquals(DockStyle.Standard, DockSettings().style)
        assertEquals("the other dock settings are untouched", DockSettings(enabled = true, edge = DockEdge.Left, style = DockStyle.Transparent).copy(style = DockStyle.Standard), DockSettings(enabled = true, edge = DockEdge.Left))
    }
}
