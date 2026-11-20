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

    @Test fun DCK_04_style_02_theSlabHasLightAndDarkVariantsWithAVisibleTopHighlightAndBevel() {
        val d = DockStyleTokens.slab(true); val l = DockStyleTokens.slab(false)
        assertTrue("dark face is dark", DockStyleTokens.luminance(d.faceTop) < 0.12); assertTrue("light face is light", DockStyleTokens.luminance(l.faceTop) > 0.7)
        listOf(d, l).forEach { s ->
            assertTrue("face gets darker towards the bottom", DockStyleTokens.luminance(s.faceBottom) < DockStyleTokens.luminance(s.faceTop))
            assertTrue("highlight is translucent white", (s.highlight ushr 24) in 0x40..0xFF && (s.highlight and 0xFFFFFF) == 0xFFFFFF)
            assertTrue("bevel dark edge is dark", (s.bevelDark and 0xFFFFFF) == 0 && (s.bevelDark ushr 24) >= 0x44)
            assertTrue("contact shadow and icon shadow are black", (s.contactShadow and 0xFFFFFF) == 0 && (s.iconShadow and 0xFFFFFF) == 0)
        }
        assertNotEquals(d.faceTop, l.faceTop)
    }

    @Test fun DCK_04_style_02_iconsOnTheSlabKeepContrastWithItsFace() {
        // the rail's own glyphs (home glyph, ring) use the theme ink: bright on the dark slab, dark on the light slab
        assertTrue(DockStyleTokens.ratio(0xFFE7E9F0.toInt(), DockStyleTokens.slab(true).faceBottom) >= 7.0)
        assertTrue(DockStyleTokens.ratio(0xFF0E1018.toInt(), DockStyleTokens.slab(false).faceBottom) >= 7.0)
    }

    @Test fun DCK_04_style_02_theContactShadowFallsDownForTheBottomDockAndInwardForASideDock() {
        assertEquals(0f to 1f, DockStyleTokens.shadowDirection(DockEdge.Bottom))
        assertTrue(DockStyleTokens.shadowDirection(DockEdge.Left).first > 0f); assertTrue(DockStyleTokens.shadowDirection(DockEdge.Right).first < 0f)
        val total = (0..<DockStyleTokens.CONTACT_LAYERS).sumOf { DockStyleTokens.contactLayerAlpha(it, 0.4f).toDouble() }
        assertTrue("layers add up to a soft shadow, not a black slab: $total", total in 0.2..0.6)
    }

    @Test fun DCK_04_style_03_onlyTransparentHasNoSurfaceAndNothingChangesATouchTarget() {
        assertEquals(listOf(true, false, true), DockStyle.entries.map { DockStyleTokens.hasSurface(it) })
        assertEquals(52f, DockStyleTokens.SLOT_DP); assertTrue(DockStyleTokens.SLOT_DP >= 48f)
    }

    @Test fun DCK_04_style_04_theMenuOffersDockStyleOnlyWhenTheHostCanStoreIt() {
        val actions = DockActions()
        assertFalse(DockMenuModel.dock(actions, DockEdge.Bottom, {}, {}).any { it.label == "Dock style" })
        assertTrue(DockMenuModel.dock(actions, DockEdge.Bottom, {}, {}, onStyle = {}).any { it.label == "Dock style" })
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
