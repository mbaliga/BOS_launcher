package com.bos.sphere.feature.grid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.SearchCategory
import com.bos.sphere.core.data.SearchHit
import com.bos.sphere.feature.search.palette.PaletteHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Wave 6 audit of PAL-09: "Forget what Radar learned" forgets people habits and remembered results too, not only app picks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class W6RadarLearningTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun PAL_09_forgetting_clears_selections_people_habits_and_remembered_results() {
        PaletteHost.learn(context, "app:demo.mail")
        ContactHabits.record(context, 7L, com.bos.sphere.core.data.Channel.Sms)
        RadarMemory.hits.put("ma", mapOf(SearchCategory.Files to listOf(SearchHit("f", SearchCategory.Files, "f"))))
        assertEquals(1, PaletteHost.usage(context).size())
        assertEquals(listOf(7L), ContactHabits.recentIds(context))

        RadarLearning.forget(context)

        assertEquals(0, PaletteHost.usage(context).size())
        assertTrue(ContactHabits.recentIds(context).isEmpty())
        assertNull(ContactHabits.last(context, 7L))
        assertNull(RadarMemory.hits.get("ma"))
    }

    /** Wave 20 audit: the rings also read the home's launch history, which "Forget" does not own, so forgetting must hide what came before it. */
    @Test fun PAL_09_forgettingAlsoHidesTheEarlierLaunchHistoryFromTheRings() {
        val now = 1_800_000_000_000L
        val log = listOf(com.bos.sphere.core.data.LaunchEvent("a", now - 5_000, 9), com.bos.sphere.core.data.LaunchEvent("a", now - 1_000, 9))
        assertEquals(0L, RadarLearning.forgottenAt(context))
        RadarLearning.forget(context, now - 2_000)
        assertEquals(now - 2_000, RadarLearning.forgottenAt(context))
        val scores = RadarHabits.scores(log, emptyMap(), now, since = RadarLearning.forgottenAt(context))
        assertEquals("only the launch after the reset still counts", 1.0, scores.getValue("app:a"), 0.01)
        assertTrue(RadarHabits.scores(log, emptyMap(), now, since = now).isEmpty())
        // The people habits clear wiped the file: the stamp must survive that clear and must not look like a person.
        assertTrue(ContactHabits.recentIds(context).isEmpty())
    }

    @Test fun PAL_09_aResetStampFromAClockThatHasSinceGoneBackNeverHidesLaunchesMadeAfterNow() {
        RadarLearning.forget(context, 9_000_000_000_000L) // stamped in a "future" the clock has since left
        val now = 1_800_000_000_000L
        val log = listOf(com.bos.sphere.core.data.LaunchEvent("old", now - 1_000, 9), com.bos.sphere.core.data.LaunchEvent("new", now + 500, 9))
        val s = RadarHabits.scores(log, emptyMap(), now, since = RadarLearning.forgottenAt(context).coerceAtMost(now)) // what SearchOverlay does
        assertTrue("before the clamped stamp: forgotten", "app:old" !in s)
        assertEquals("after it: counted", 1.0, s.getValue("app:new"), 0.01)
    }
}
