package com.bos.sphere.core.signals

import android.app.usage.UsageEvents
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** STR-14: only the public UsageStatsManager event types count as "came to the foreground" and "went to the background". */
class UsageEventKindsTest {
    @Test fun STR_14_resumedAndPausedAreTheForegroundAndBackgroundEvents() {
        assertTrue(UsageEventKinds.isForeground(UsageEvents.Event.ACTIVITY_RESUMED))
        assertTrue(UsageEventKinds.isBackground(UsageEvents.Event.ACTIVITY_PAUSED))
        @Suppress("DEPRECATION") assertTrue(UsageEventKinds.isForeground(UsageEvents.Event.MOVE_TO_FOREGROUND))
        @Suppress("DEPRECATION") assertTrue(UsageEventKinds.isBackground(UsageEvents.Event.MOVE_TO_BACKGROUND))
    }

    @Test fun STR_14_otherEventTypesAreNeither() {
        for (t in listOf(UsageEvents.Event.ACTIVITY_STOPPED, UsageEvents.Event.NONE, UsageEvents.Event.SCREEN_INTERACTIVE, UsageEvents.Event.CONFIGURATION_CHANGE)) {
            assertFalse(UsageEventKinds.isForeground(t)); assertFalse(UsageEventKinds.isBackground(t))
        }
        assertFalse(UsageEventKinds.isForeground(UsageEvents.Event.ACTIVITY_PAUSED))
        assertFalse(UsageEventKinds.isBackground(UsageEvents.Event.ACTIVITY_RESUMED))
    }
}
