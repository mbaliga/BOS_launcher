package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** W42 (notifDeepLink), pure JVM: the intent memory and the order the opener tries things in. */
class NotificationOpenPlanW42Test {
    @Test fun STR_02_w42_cacheIsBoundedAndDropsTheLeastRecentlyUsed() {
        val c = RecentIntents<String>(max = 3)
        c.put("a", 1, "A"); c.put("b", 1, "B"); c.put("c", 1, "C")
        assertEquals("A", c.find("a", 1)) // a is now the most recently used
        c.put("d", 1, "D")
        assertEquals(3, c.size)
        assertNull("b was the least recently used", c.find("b", 1))
        assertEquals("A", c.find("a", 1)); assertEquals("D", c.find("d", 1))
    }

    @Test fun STR_02_w42_defaultBoundIs300() {
        val c = RecentIntents<Int>()
        repeat(450) { c.put("k$it", 1, it) }
        assertEquals(300, c.size)
        assertNull(c.find("k0", 1)); assertEquals(449, c.find("k449", 1))
    }

    @Test fun STR_02_w42_aNewerPostReplacesAndAnOlderOneDoesNot() {
        val c = RecentIntents<String>()
        assertTrue(c.put("chat", 10, "old"))
        assertTrue(c.put("chat", 20, "new"))
        assertEquals("new", c.find("chat", 20))
        assertFalse("a late, older post must not replace a newer intent", c.put("chat", 15, "stale"))
        assertEquals("new", c.find("chat", 10))
    }

    @Test fun STR_02_w42_anIntentOlderThanTheRowIsStale() {
        val c = RecentIntents<String>()
        c.put("chat", 10, "pi")
        assertEquals("pi", c.find("chat", 10))
        assertEquals("a row of an earlier post still opens the chat", "pi", c.find("chat", 5))
        assertNull("a row newer than anything remembered is not served an older intent", c.find("chat", 11))
        assertNull(c.find("other", 10))
    }

    @Test fun STR_02_w42_fallbackOrderIsLiveCachedShortcutApp() {
        assertEquals(listOf(OpenStep.Live, OpenStep.Cached, OpenStep.Shortcut, OpenStep.App), OpenPlan.steps(true, true, true, true))
        assertEquals(listOf(OpenStep.Cached, OpenStep.Shortcut, OpenStep.App), OpenPlan.steps(false, true, true, true))
        assertEquals(listOf(OpenStep.Shortcut, OpenStep.App), OpenPlan.steps(false, false, true, true))
        assertEquals(listOf(OpenStep.App), OpenPlan.steps(false, false, false, true))
        assertTrue(OpenPlan.steps(false, false, false, false).isEmpty())
    }

    @Test fun STR_02_w42_theFirstStepThatWorksDecides() {
        val tried = ArrayList<OpenStep>()
        val r = OpenPlan.run(listOf(OpenStep.Live, OpenStep.Cached, OpenStep.Shortcut, OpenStep.App)) { tried += it; it == OpenStep.Shortcut }
        assertEquals(listOf(OpenStep.Live, OpenStep.Cached, OpenStep.Shortcut), tried)
        assertEquals(HandOff.Sent, r.handOff); assertEquals(OpenStep.Shortcut, r.step)
    }

    @Test fun STR_02_w42_theAppIsAFallBackAndNothingWorkingIsAFailure() {
        assertEquals(HandOff.FellBack, OpenPlan.run(listOf(OpenStep.Live, OpenStep.App)) { it == OpenStep.App }.handOff)
        val none = OpenPlan.run(listOf(OpenStep.Live, OpenStep.App)) { false }
        assertEquals(HandOff.Failed, none.handOff); assertNull(none.step)
        assertEquals(HandOff.Failed, OpenPlan.run(emptyList()) { true }.handOff)
    }

    @Test fun STR_02_w42_aThrowingStepFallsThrough() {
        val r = OpenPlan.run(listOf(OpenStep.Shortcut, OpenStep.App)) { if (it == OpenStep.Shortcut) throw IllegalStateException("gone") else true }
        assertEquals(HandOff.FellBack, r.handOff)
    }

    @Test fun STR_02_w42_historyLinesKeepTheShortcutAndOldLinesStillRead() {
        val e = HistoryEntry("k|5", "k", "p", "App", "Ann", "Ann", "hi", "Ann", 5, shortcutId = "chat\t1", userId = 10)
        val back = NotificationHistoryCodec.decode(NotificationHistoryCodec.encode(e))!!
        assertEquals("chat\t1", back.shortcutId); assertEquals(10, back.userId)
        val plain = e.copy(shortcutId = "", userId = 0)
        assertEquals("an entry without a shortcut is written exactly as before", 9, NotificationHistoryCodec.encode(plain).split('\t').size)
        val old = NotificationHistoryCodec.decode("5\tua\tk\tp\tApp\tAnn\tAnn\thi\tAnn")!!
        assertEquals("", old.shortcutId); assertEquals(0, old.userId)
        assertNull(NotificationHistoryCodec.decode("5\tua\tk\tp\tApp\tAnn\tAnn\thi\tAnn\tx"))
    }
}
