package com.bos.sphere.ui

import androidx.activity.ComponentActivity
import com.bos.sphere.core.quality.ProbeSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.time.Duration

/** Wave 23 review (NFR-05): behaviour of the probe run, not a source scan: it starts after the lead-in, and a stopped activity discards it without a report. */
@RunWith(RobolectricTestRunner::class)
class DeviceProbeLifecycleTest {
    private fun resumed(): ActivityController<ComponentActivity> = Robolectric.buildActivity(ComponentActivity::class.java).setup()

    @Test fun NFR_05_aRunWaitsForTheLeadInAndStopsWithoutAResultWhenTheActivityStops() {
        val c = resumed()
        var reported = 0
        DeviceProbe.start(c.get(), ProbeSurface.Glance) { _, _ -> reported++ }
        assertEquals(DeviceProbe.Phase.LeadIn, DeviceProbe.phase)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(DeviceProbe.LEAD_IN_MS + 100))
        assertTrue(DeviceProbe.phase == DeviceProbe.Phase.Measuring || DeviceProbe.phase == DeviceProbe.Phase.Idle)
        c.pause().stop()
        assertEquals(DeviceProbe.Phase.Idle, DeviceProbe.phase)
        assertTrue(DeviceProbe.lastRunDiscarded)
        assertEquals(0, reported)
        // a new run is accepted afterwards (the old one did not stay registered)
        val d = resumed()
        DeviceProbe.start(d.get(), ProbeSurface.PageSwipe) { _, _ -> }
        assertEquals(DeviceProbe.Phase.LeadIn, DeviceProbe.phase)
        assertFalse(DeviceProbe.lastRunDiscarded)
        DeviceProbe.cancel()
        assertEquals(DeviceProbe.Phase.Idle, DeviceProbe.phase)
    }
}
