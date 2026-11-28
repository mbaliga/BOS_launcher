package com.bos.sphere.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/** STR-02, STR-05 (wave 23 sweep): a late release by the old service instance never unbinds the new one. */
class BoundSlotTest {
    @Test fun STR_05_aLateReleaseByTheOldInstanceKeepsTheNewOneBound() {
        val slot = BoundSlot<Any>()
        val old = Any(); val new = Any()
        slot.bind(old); slot.bind(new)
        assertFalse(slot.release(old))
        assertSame(new, slot.current)
        assertTrue(slot.release(new))
        assertNull(slot.current)
    }
}
