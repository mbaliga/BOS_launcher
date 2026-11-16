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
}
