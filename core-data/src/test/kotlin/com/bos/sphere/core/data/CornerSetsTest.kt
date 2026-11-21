package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CornerSetsTest {
    @Test fun INP_15_each_kind_of_assignment_round_trips_through_its_slot() {
        val app = CornerSets.slotFor(CornerAssignKind.App, appKey = "com.x/.Main#0")
        val act = CornerSets.slotFor(CornerAssignKind.LauncherAction, action = CornerActionId.Search)
        val grp = CornerSets.slotFor(CornerAssignKind.AppGroup, group = "Work", members = listOf(CornerItem.App("a/b#0")))
        val sc = CornerSets.slotFor(CornerAssignKind.Shortcut, appKey = "pkg:id")
        val none = CornerSets.slotFor(CornerAssignKind.Nothing)
        assertEquals(CornerAssignKind.App, CornerSets.kindOf(app))
        assertEquals(CornerAssignKind.LauncherAction, CornerSets.kindOf(act))
        assertEquals(CornerAssignKind.AppGroup, CornerSets.kindOf(grp))
        assertEquals(CornerAssignKind.Shortcut, CornerSets.kindOf(sc))
        assertEquals(CornerAssignKind.Nothing, CornerSets.kindOf(none))
        assertEquals(5, CornerAssignKind.Pickable.size)
    }

    @Test fun INP_15_the_assignments_survive_the_stored_form() {
        for (slot in listOf(
            CornerSets.slotFor(CornerAssignKind.App, appKey = "com.x/.Main#0"),
            CornerSets.slotFor(CornerAssignKind.AppGroup, group = "Work", members = listOf(CornerItem.App("a/b#0"), CornerItem.Action(CornerActionId.Music))),
            CornerSets.slotFor(CornerAssignKind.Shortcut, appKey = "pkg:id"),
            CornerSets.slotFor(CornerAssignKind.LauncherAction, action = CornerActionId.AllApps),
        )) assertEquals(slot, ShortcutCodec.decodeSlot(ShortcutCodec.encodeSlot(slot)))
    }

    @Test fun INP_15_landscape_set_is_used_only_when_asked_for() {
        val portrait = mapOf(Corner.TopLeft to CornerSlot.Single(CornerItem.Action(CornerActionId.Camera)))
        val landscape = mapOf(Corner.TopLeft to CornerSlot.Single(CornerItem.Action(CornerActionId.Music)))
        assertEquals(portrait, CornerSets.select(portrait, landscape, perOrientation = false, isLandscape = true))
        assertEquals(portrait, CornerSets.select(portrait, landscape, perOrientation = true, isLandscape = false))
        assertEquals(landscape, CornerSets.select(portrait, landscape, perOrientation = true, isLandscape = true))
        assertTrue(CornerSets.writesLandscape(true, true))
        assertFalse(CornerSets.writesLandscape(false, true))
    }

    @Test fun INP_12_keyboard_on_open_is_set_per_orientation_and_off_by_default() {
        val p = LauncherPrefs()
        assertFalse(p.keyboardOnOpen(false)); assertFalse(p.keyboardOnOpen(true))
        val q = p.copy(keyboardOnOpenLandscape = true)
        assertFalse(q.keyboardOnOpen(false)); assertTrue(q.keyboardOnOpen(true))
    }

    @Test fun INP_16_hot_corner_defaults_are_150ms_and_on() {
        val p = LauncherPrefs()
        assertEquals(150, p.hotCornerDwellMs); assertTrue(p.hotCornersOn)
    }
}
