package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CornerShortcutsTest {
    @Test fun INP_15_a_shortcut_key_round_trips_with_its_label() {
        val k = CornerShortcuts.make("com.x.mail", "compose", 7, "New message")
        assertTrue(CornerShortcuts.isShortcut(k))
        assertEquals(CornerShortcuts.Parsed("com.x.mail", "compose", 7, "New message"), CornerShortcuts.parse(k))
    }

    @Test fun INP_15_ids_with_slashes_and_labels_with_separators_survive() {
        val k = CornerShortcuts.make("com.x", "a/b#c", 0, "New | note\\nline")
        val p = CornerShortcuts.parse(k)!!
        assertEquals("com.x", p.packageName); assertEquals(0, p.userHash)
        assertFalse('|' in p.label)
    }

    @Test fun INP_15_a_corner_holding_a_shortcut_is_the_shortcut_kind_and_a_plain_app_key_is_not() {
        val slot = CornerSets.slotFor(CornerAssignKind.Shortcut, appKey = CornerShortcuts.make("p", "i", 1, "L"))
        assertEquals(CornerAssignKind.Shortcut, CornerSets.kindOf(slot))
        assertEquals(CornerAssignKind.App, CornerSets.kindOf(CornerSlot.Single(CornerItem.App("p/.Main#1"))))
        assertNull(CornerShortcuts.parse("p/.Main#1"))
        assertNull(CornerShortcuts.parse("shortcut:broken"))
    }
}
