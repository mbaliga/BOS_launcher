package com.bos.sphere.feature.dock

import com.bos.sphere.core.launch.AppGroup
import com.bos.sphere.core.launch.LaunchRequest
import com.bos.sphere.core.launch.LaunchTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-02: the rail reads HomeState.dock as the single source, and a widget can be pinned to it. */
class DockSingleSourceTest {
    private val group = AppGroup("Dev", listOf(LaunchRequest(LaunchTarget("a", "a.Main", 1)), LaunchRequest(LaunchTarget("b", null, 2))), id = "g1")

    @Test
    fun HOM_02_everyPinKindRoundTripsThroughTheDockList() {
        val pins = listOf(
            DockItemRef.app("com.a/.Main#0"), DockItemRef.group("g1"), DockItemRef(ItemKind.Folder, "f1"),
            HomeItemPins.refOf("shortcut:com.a/x#0")!!, HomeItemPins.refOf("link:https://x.org")!!, HomeItemPins.refOf("widget:12")!!,
            DockItemRef(ItemKind.Shortcut, "liftoff.desktop"),
        )
        assertEquals(pins, HomeDockKeys.decodeAll(HomeDockKeys.encodeAll(pins)))
        // A pin never collides with the key of the item it points at, so the item can stay on its page.
        assertTrue(HomeDockKeys.encodeAll(pins).none { it == "widget:12" || it == "folder:f1" })
    }

    @Test
    fun HOM_02_aPlainKeyFromOlderMoveToDockStillShowsInTheRail() {
        assertEquals(DockItemRef.app("com.a/.Main#0"), HomeDockKeys.decode("com.a/.Main#0"))
        assertEquals(ItemKind.Folder, HomeDockKeys.decode("folder:f9")!!.kind)
        assertNull(HomeDockKeys.decode(""))
    }

    @Test
    fun HOM_02_theOldWorkspacePinsMigrateIntoTheDockListOnceAndNeverOverwriteIt() {
        val old = listOf(DockItemRef.app("a"), DockItemRef(ItemKind.Folder, "f1"))
        val migrated = HomeDockKeys.migrate(emptyList(), old)!!
        assertEquals(old, HomeDockKeys.decodeAll(migrated))
        // Once the list holds pins the old store is not read again, even if the user later emptied it of others.
        assertNull(HomeDockKeys.migrate(migrated, listOf(DockItemRef.app("zzz"))))
        assertNull(HomeDockKeys.migrate(emptyList(), emptyList()))
    }

    @Test
    fun HOM_02_aWorkspaceWithoutASavedDockKeepsItsLegacyPins() {
        val legacy = listOf(DockItemRef.app("a"))
        assertEquals(HomeDockKeys.encodeAll(legacy), HomeDockKeys.forWorkspace(null, legacy))
        assertEquals(listOf("pin:app:b"), HomeDockKeys.forWorkspace(listOf("pin:app:b"), legacy))
        assertEquals(listOf("pin:app:b"), HomeDockKeys.dockOf("""{"pages":"x","dock":["pin:app:b"]}"""))
        assertNull(HomeDockKeys.dockOf("""{"pages":"x"}"""))
        assertNull(HomeDockKeys.dockOf(null))
    }

    @Test
    fun HOM_02_withASinkSetPinsWritesTheSharedListAndTheWorkspaceOnlyMirrorsIt() {
        val m = WorkspaceManager(InMemoryWorkspaceStore())
        var shared = emptyList<String>()
        m.pinSink = { pins -> shared = HomeDockKeys.encodeAll(pins) }
        m.setPins(listOf(DockItemRef.app("a")))
        assertEquals(listOf("pin:app:a"), shared)
        assertTrue("the workspace list is not the store while a sink is set", m.current.active.dockPins.isEmpty())
        m.mirrorPins(HomeDockKeys.decodeAll(shared))
        assertEquals(listOf(DockItemRef.app("a")), m.current.active.dockPins)
    }

    @Test
    fun HOM_02_withoutASinkTheWorkspaceListStaysTheStore() {
        val m = WorkspaceManager(InMemoryWorkspaceStore())
        m.setPins(listOf(DockItemRef.app("a")))
        assertEquals(listOf(DockItemRef.app("a")), m.current.active.dockPins)
    }

    @Test
    fun HOM_02_aPinnedWidgetTakesItsSpanAlongTheRailAndIsBounded() {
        val ref = HomeItemPins.refOf("widget:3")!!
        val content = DockContentBuilder.build(
            pins = listOf(DockItemRef.app("a"), ref), labelOf = { "x" }, recentApps = emptyList(), recentLabelOf = { null },
            usageGranted = false, mode = DockMode.PinnedPlusRecent, sessionUsed = emptySet(), media = null,
            spanOf = { 3 },
        )
        assertEquals(1, content.pinned[0].span)
        assertEquals(3, content.pinned[1].span)
        assertEquals(3 * 52f + 2 * 8f, DockWidgetSlot.lengthDp(3), 0.001f)
        assertEquals(DockWidgetSlot.MAX_SPAN, DockWidgetSlot.slots(9))
        assertEquals(1, DockWidgetSlot.slots(0))
    }

    @Test
    fun HOM_02_aPinnedWidgetIsOneSlotThickSoTheRailKeepsItsThickness() {
        // Across the rail the widget is one slot, the same thickness the home reserves for the rail (HOM-19).
        assertEquals(DockFootprint.SLOT_DP, DockWidgetSlot.SLOT_DP, 0f)
        assertEquals(DockFootprint.thicknessDp(DockEdge.Bottom, false), DockFootprint.BOTTOM_MARGIN_DP + DockWidgetSlot.SLOT_DP + 2 * DockFootprint.PAD_DP, 0f)
    }

    @Test
    fun HOM_02_deletingAGroupDropsItsPinFromTheSharedList() {
        val m = WorkspaceManager(InMemoryWorkspaceStore())
        m.saveGroup(group)
        var shared: List<DockItemRef> = emptyList()
        m.pinSink = { shared = it }
        m.mirrorPins(listOf(DockItemRef.group("g1"), DockItemRef.app("a")))
        m.deleteGroup("g1")
        assertEquals(listOf(DockItemRef.app("a")), shared)
    }
}
