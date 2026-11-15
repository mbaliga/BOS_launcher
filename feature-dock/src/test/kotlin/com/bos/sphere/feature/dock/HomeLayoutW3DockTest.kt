package com.bos.sphere.feature.dock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-02 (non-app home items in the rail) and HOM-19 (the rail never overlaps the app area). */
class HomeLayoutW3DockTest {
    @Test
    fun HOM_02_everyNonAppHomeKindMapsToADockPinAndBack() {
        val keys = listOf("folder:f1", "shortcut:com.a/compose#0", "link:https://x.org", "link:content://docs/1")
        for (k in keys) {
            val ref = HomeItemPins.refOf(k)!!
            assertEquals(k, HomeItemPins.homeKeyOf(ref))
            // The pin survives the rail's own storage encoding.
            assertEquals(ref, DockItemRef.decode(ref.encode()))
        }
        assertEquals(ItemKind.Folder, HomeItemPins.refOf("folder:f1")!!.kind)
        assertNull(HomeItemPins.refOf("com.a/.Main#0"))
        // HOM-02 (wave 4): a widget can be pinned to the rail too, in a slot that takes its span.
        assertEquals(ItemKind.Widget, HomeItemPins.refOf("widget:4")!!.kind)
        // The Desktop item and groups are not home items.
        assertNull(HomeItemPins.homeKeyOf(DockItemRef(ItemKind.Shortcut, "liftoff.desktop")))
        assertNull(HomeItemPins.homeKeyOf(DockItemRef.group("g")))
    }

    @Test
    fun HOM_02_toggleAddsAndRemovesOnePin() {
        val pins = listOf(DockItemRef.app("a"))
        val on = HomeItemPins.toggle(pins, "folder:f1")
        assertTrue(HomeItemPins.isPinned(on, "folder:f1"))
        assertEquals(2, on.size)
        val off = HomeItemPins.toggle(on, "folder:f1")
        assertFalse(HomeItemPins.isPinned(off, "folder:f1"))
        assertEquals(pins, off)
        assertEquals(pins, HomeItemPins.toggle(pins, "com.a/.Main#0"))
    }

    @Test
    fun HOM_02_aPinnedFolderShowsInTheRailContentWithItsName() {
        val pins = listOf(DockItemRef.app("a"), HomeItemPins.refOf("folder:f1")!!, HomeItemPins.refOf("link:https://x.org")!!)
        val content = DockContentBuilder.build(
            pins = pins,
            labelOf = { r -> when (r.kind) { ItemKind.App -> "App A"; else -> HomeItemPins.homeKeyOf(r)?.let { if (it == "folder:f1") "Games" else "Site" } } },
            recentApps = emptyList(), recentLabelOf = { null }, usageGranted = true, mode = DockMode.PinnedPlusRecent, sessionUsed = emptySet(), media = null,
        )
        assertEquals(listOf("App A", "Games", "Site"), content.pinned.map { it.label })
        // A folder deleted from home has no label: it is skipped, its pin kept in storage.
        val gone = DockContentBuilder.build(pins, { r -> if (r.kind == ItemKind.App) "App A" else null }, emptyList(), { null }, true, DockMode.PinnedPlusRecent, emptySet(), null)
        assertEquals(listOf("App A"), gone.pinned.map { it.label })
    }

    @Test
    fun HOM_19_railNeverOverlapsTheAppAreaAtManyWindowSizes() {
        val windows = listOf(320 to 320, 360 to 640, 411 to 891, 640 to 360, 891 to 411, 800 to 1280, 1280 to 800, 1024 to 600, 1920 to 1080)
        for ((w, h) in windows) {
            val band = minOf(w, h) * 0.148f // HomeFrame.FRACTION
            for (edge in DockEdge.entries) for (collapsed in listOf(false, true)) {
                val r = DockFootprint.reserve(true, edge, collapsed, band)
                val thickness = DockFootprint.thicknessDp(edge, collapsed)
                // Band plus the reserve always covers the rail's reach, on its own edge only.
                val own = when (edge) { DockEdge.Bottom -> r.bottom; DockEdge.Left -> r.start; DockEdge.Right -> r.end }
                assertTrue("$w x $h $edge collapsed=$collapsed", band + own >= thickness - 0.001f)
                assertTrue(own >= 0f)
                val other = r.bottom + r.start + r.end - own
                assertEquals(0f, other, 0.001f)
            }
            assertFalse(DockFootprint.reserve(false, DockEdge.Bottom, false, band).any)
        }
        // On a roomy window the rail fits inside the band and takes nothing from the app area.
        assertFalse(DockFootprint.reserve(true, DockEdge.Bottom, false, 1080 * 0.148f).any)
        // On a phone it does not, so the app area gives up the difference.
        assertTrue(DockFootprint.reserve(true, DockEdge.Bottom, false, 360 * 0.148f).bottom > 0f)
    }
}
