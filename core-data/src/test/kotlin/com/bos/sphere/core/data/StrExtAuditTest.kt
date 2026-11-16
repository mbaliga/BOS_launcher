package com.bos.sphere.core.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 6 audit of the Launchpad and media requirements (STR-03, STR-05, STR-06, STR-09). */
class StrExtAuditTest {
    @After fun clean() {
        LaunchpadSettings.pruneRead(emptySet())
        LaunchpadSettings.unsnooze("a"); LaunchpadSettings.unsnooze("b")
    }

    @Test fun STR_06_aStripThatStartedWithoutAccessReattachesWhenTheGrantComes() {
        assertTrue(MediaLogic.needsReattach(watchers = 1, needsAccess = true))
        assertTrue(MediaLogic.needsReattach(watchers = 3, needsAccess = true))
    }

    @Test fun STR_05_nothingReattachesWithoutAWatcherOrWhenAccessIsAlreadyThere() {
        assertFalse(MediaLogic.needsReattach(watchers = 0, needsAccess = true))
        assertFalse(MediaLogic.needsReattach(watchers = 2, needsAccess = false))
    }

    @Test fun STR_09_anEmptyFeedDoesNotForgetReadMarksOrSnoozes() {
        LaunchpadSettings.markRead("a")
        LaunchpadSettings.snooze("a", Long.MAX_VALUE)
        LaunchpadSettings.pruneToFeed(emptySet(), now = 1_000L)
        assertEquals(setOf("a"), LaunchpadSettings.readKeys.value)
        assertTrue("a" in LaunchpadSettings.snoozed.value)
    }

    @Test fun STR_09_aRealFeedStillDropsMarksOfNotificationsThatAreGone() {
        LaunchpadSettings.markRead("a"); LaunchpadSettings.markRead("b")
        LaunchpadSettings.snooze("a", Long.MAX_VALUE)
        LaunchpadSettings.pruneToFeed(setOf("b"), now = 1_000L)
        assertEquals(setOf("b"), LaunchpadSettings.readKeys.value)
        assertFalse("a" in LaunchpadSettings.snoozed.value)
    }
}
