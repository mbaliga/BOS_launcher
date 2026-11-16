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

    @Test
    fun CFG_04_aBurstOfManyDistinctKindsKeepsTheStateBeforeTheFirstEditAndTheLatestKinds() {
        val limiter = DestructiveLimiter(5_000L, Int.MAX_VALUE)
        var now = 100_000L
        val store = SnapshotStore(tmp.newFolder("p"), SnapshotStore.MAX_SNAPSHOTS) { now }
        for (i in 1..3) { store.take("old $i", "state-old-$i", force = true); now += 10_000L }
        now += 10_000L
        val kinds = listOf("hide app", "clear corner", "clear gesture", "remove from home", "remove Launchpad rule", "clear Launchpad policy", "delete page", "remove tile")
        for ((i, k) in kinds.withIndex()) {
            val d = limiter.decide(k, now)
            if (d.take) store.take(k, "state-before-$i", pin = d.burstStart)
            now += 300L
        }
        val texts = store.list().mapNotNull { store.read(it.id) }
        assertEquals(SnapshotStore.MAX_SNAPSHOTS, texts.size)
        assertTrue("state before the first edit of the burst: $texts", "state-before-0" in texts)
        assertTrue("state before the latest distinct kind: $texts", "state-before-7" in texts)
        assertFalse("an old pre-burst snapshot was pushed out instead", "state-old-1" in texts)
    }

    @Test
    fun CFG_04_aNewBurstPinsItsOwnFirstSnapshotAndReleasesTheOldPin() {
        val limiter = DestructiveLimiter(5_000L, Int.MAX_VALUE)
        var now = 0L
        val store = SnapshotStore(tmp.newFolder("q"), 3) { now }
        fun edit(kind: String, state: String) { val d = limiter.decide(kind, now); if (d.take) store.take(kind, state, pin = d.burstStart); now += 100 }
        edit("a", "burst1-a"); edit("b", "burst1-b"); edit("c", "burst1-c"); edit("d", "burst1-d")
        assertTrue("burst1-a" in store.list().mapNotNull { store.read(it.id) })
        now += 10_000
        edit("a", "burst2-a"); edit("b", "burst2-b"); edit("c", "burst2-c")
        val texts = store.list().mapNotNull { store.read(it.id) }
        assertTrue("burst2-a" in texts); assertTrue("burst2-c" in texts)
        assertFalse("old burst no longer pinned", "burst1-a" in texts)
    }

    @Test
    fun CFG_04_aPinnedSnapshotIdenticalToTheNewestKeepsThatFileSafe() {
        var now = 0L
        val store = SnapshotStore(tmp.newFolder("r"), 2) { now }
        store.take("start", "same", force = true); now += 10
        assertEquals(null, store.take("hide app", "same", pin = true))
        now += 10; store.take("x", "other1"); now += 10; store.take("y", "other2")
        assertTrue("same" in store.list().mapNotNull { store.read(it.id) })
    }
}
