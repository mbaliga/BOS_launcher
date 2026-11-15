package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** CFG-04: the destructive-edit limiter is per kind of edit and never loses the state before a burst. */
class DestructiveLimiterTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun CFG_04_aSecondEditOfTheSameKindInsideTheWindowTakesNoSnapshot() {
        val l = DestructiveLimiter(5_000L)
        assertTrue(l.allow("hide app", 1_000L))
        assertFalse(l.allow("hide app", 2_000L))
        assertFalse(l.allow("hide app", 5_999L))
        assertTrue("window passed", l.allow("hide app", 6_000L))
    }

    @Test
    fun CFG_04_aDifferentKindOfEditInsideTheWindowStillGetsItsOwnSnapshot() {
        val l = DestructiveLimiter(5_000L)
        assertTrue(l.allow("hide app", 1_000L))
        assertTrue("state before the corner edit includes the hidden app", l.allow("clear corner", 1_500L))
        assertFalse(l.allow("clear corner", 2_000L))
        assertTrue(l.allow("remove Launchpad rule", 2_500L))
    }

    @Test
    fun CFG_04_aPauseStartsAFreshBurst() {
        val l = DestructiveLimiter(5_000L, maxPerBurst = 2)
        assertTrue(l.allow("a", 0L)); assertTrue(l.allow("b", 1L))
        assertFalse("cap reached inside the burst", l.allow("c", 2L))
        assertTrue("after a pause of the gap the cap resets", l.allow("c", 5_002L))
    }

    @Test
    fun CFG_04_theSnapshotOfTheStateBeforeABurstSurvivesAnyBurstLength() {
        val limiter = DestructiveLimiter(5_000L)
        var now = 100_000L
        val store = SnapshotStore(tmp.newFolder("s"), SnapshotStore.MAX_SNAPSHOTS) { now }
        // Older snapshots exist from earlier in the day.
        for (i in 1..3) { store.take("old $i", "state-old-$i", force = true); now += 10_000L }
        now += 10_000L
        val kinds = listOf("hide app", "clear corner", "clear gesture", "remove from home", "remove Launchpad rule", "clear Launchpad policy")
        var state = "state-before-burst"
        for ((i, k) in kinds.withIndex()) {
            if (limiter.allow(k, now)) store.take(k, state)
            state = "state-after-$i"
            now += 300L
        }
        // Many more edits of the same kinds in the same burst.
        repeat(20) { n -> if (limiter.allow(kinds[n % kinds.size], now)) store.take(kinds[n % kinds.size], "later-$n"); now += 100L }
        val texts = store.list().mapNotNull { store.read(it.id) }
        assertTrue("pre-burst state kept: $texts", "state-before-burst" in texts)
        assertEquals(SnapshotStore.MAX_SNAPSHOTS, store.list().size)
    }
}
