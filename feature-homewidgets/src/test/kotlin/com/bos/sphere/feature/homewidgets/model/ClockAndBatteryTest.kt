package com.bos.sphere.feature.homewidgets.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ClockAndBatteryTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    // --- Clock: next alarm ---

    @Test
    fun noAlarmIsNoAlarm() {
        assertNull(ClockModel.nextAlarm(null, at(2026, 11, 4, 9, 0), zone))
    }

    @Test
    fun anAlarmInThePastIsNoAlarm() {
        assertNull(ClockModel.nextAlarm(at(2026, 11, 4, 7, 0), at(2026, 11, 4, 9, 0), zone))
    }

    @Test
    fun theComingSixDaysAreNamedByWeekday() {
        val now = at(2026, 11, 4, 23, 59) // a Wednesday
        val tomorrow = ClockModel.nextAlarm(at(2026, 11, 5, 7, 0), now, zone)!!
        assertEquals(ClockModel.AlarmDay.WEEKDAY, tomorrow.day)
        assertEquals(7, tomorrow.at.hour)
        val sixDays = ClockModel.nextAlarm(at(2026, 11, 10, 7, 0), now, zone)!!
        assertEquals(ClockModel.AlarmDay.WEEKDAY, sixDays.day)
    }

    @Test
    fun aWeekAheadIsNamedByDateBecauseItsWeekdayIsToday() {
        val now = at(2026, 11, 4, 9, 0)
        assertEquals(ClockModel.AlarmDay.DATE, ClockModel.nextAlarm(at(2026, 11, 11, 7, 0), now, zone)!!.day)
        assertEquals(ClockModel.AlarmDay.DATE, ClockModel.nextAlarm(at(2026, 12, 25, 7, 0), now, zone)!!.day)
    }

    @Test
    fun theAlarmIsShownInTheGivenZone() {
        val utc = ZoneId.of("UTC")
        val alarm = ClockModel.nextAlarm(at(2026, 11, 5, 7, 0), at(2026, 11, 4, 9, 0), utc)!!
        assertEquals(1, alarm.at.hour) // 07:00 IST = 01:30 UTC
        assertEquals(30, alarm.at.minute)
    }

    // --- Battery ---

    @Test
    fun percentFromLevelAndScale() {
        assertEquals(84, BatteryModel.percent(84, 100))
        assertEquals(50, BatteryModel.percent(128, 256))
        assertNull(BatteryModel.percent(-1, 100))
        assertNull(BatteryModel.percent(50, 0))
        assertEquals(100, BatteryModel.percent(120, 100))
    }

    @Test
    fun plugSources() {
        assertEquals(PlugSource.NONE, BatteryModel.plug(0))
        assertEquals(PlugSource.AC, BatteryModel.plug(BatteryModel.PLUGGED_AC))
        assertEquals(PlugSource.USB, BatteryModel.plug(BatteryModel.PLUGGED_USB))
        assertEquals(PlugSource.WIRELESS, BatteryModel.plug(BatteryModel.PLUGGED_WIRELESS))
        assertEquals(PlugSource.DOCK, BatteryModel.plug(BatteryModel.PLUGGED_DOCK))
    }

    @Test
    fun states() {
        val s = BatteryModel
        assertEquals(BatteryState.CHARGING, s.state(40, s.STATUS_CHARGING, PlugSource.USB, null))
        assertEquals(BatteryState.FULL, s.state(100, s.STATUS_FULL, PlugSource.AC, null))
        assertEquals(BatteryState.FULL, s.state(100, s.STATUS_NOT_CHARGING, PlugSource.AC, null))
        assertEquals(BatteryState.PLUGGED, s.state(80, s.STATUS_NOT_CHARGING, PlugSource.AC, null))
        assertEquals(BatteryState.ON_BATTERY, s.state(80, s.STATUS_DISCHARGING, PlugSource.NONE, null))
        // Full but unplugged is simply on battery.
        assertEquals(BatteryState.ON_BATTERY, s.state(100, s.STATUS_FULL, PlugSource.NONE, null))
    }

    @Test
    fun lowFollowsTheSystemWhenItSaysAndFifteenPercentOtherwise() {
        val s = BatteryModel
        assertEquals(BatteryState.LOW, s.state(15, s.STATUS_DISCHARGING, PlugSource.NONE, null))
        assertEquals(BatteryState.ON_BATTERY, s.state(16, s.STATUS_DISCHARGING, PlugSource.NONE, null))
        assertEquals(BatteryState.LOW, s.state(20, s.STATUS_DISCHARGING, PlugSource.NONE, true))
        assertEquals(BatteryState.ON_BATTERY, s.state(10, s.STATUS_DISCHARGING, PlugSource.NONE, false))
        // Never "low" while charging.
        assertEquals(BatteryState.CHARGING, s.state(5, s.STATUS_CHARGING, PlugSource.AC, true))
    }

    @Test
    fun timeToFullOnlyWhileChargingAndKnown() {
        assertEquals(70, BatteryModel.minutesToFull(70 * 60_000L, BatteryState.CHARGING))
        assertEquals(1, BatteryModel.minutesToFull(5_000L, BatteryState.CHARGING)) // rounds up
        assertNull(BatteryModel.minutesToFull(-1, BatteryState.CHARGING))
        assertNull(BatteryModel.minutesToFull(70 * 60_000L, BatteryState.ON_BATTERY))
        assertEquals(1 to 10, BatteryModel.hoursAndMinutes(70))
        assertEquals(0 to 5, BatteryModel.hoursAndMinutes(5))
    }

    @Test
    fun readingIgnoresUnreportedTemperature() {
        val r = BatteryModel.reading(84, 100, BatteryModel.STATUS_DISCHARGING, 0, 0, null, -1, 1000L)!!
        assertNull(r.temperatureC)
        assertEquals(31.5f, BatteryModel.reading(84, 100, 3, 0, 315, null, -1, 1L)!!.temperatureC!!, 0f)
        assertNull(BatteryModel.reading(-1, 100, 3, 0, 315, null, -1, 1L))
    }

    @Test
    fun redrawOnlyWhenSomethingShownChangesOrTheAsOfTimeAges() {
        val r1 = BatteryModel.reading(84, 100, 3, 0, 300, null, -1, 0L)
        val sameButWarmer = BatteryModel.reading(84, 100, 3, 0, 320, null, -1, 0L)
        val lower = BatteryModel.reading(83, 100, 3, 0, 300, null, -1, 0L)
        val plugged = BatteryModel.reading(84, 100, 2, 2, 300, null, -1, 0L)
        val key = BatteryModel.redrawKey(r1)
        val t = 1_000_000L
        assertFalse(BatteryModel.shouldRedraw(key, t, sameButWarmer, t + 60_000L))
        assertTrue(BatteryModel.shouldRedraw(key, t, lower, t + 1))
        assertTrue(BatteryModel.shouldRedraw(key, t, plugged, t + 1))
        assertTrue(BatteryModel.shouldRedraw(key, t, sameButWarmer, t + BatteryModel.REFRESH_AFTER_MS))
        assertTrue(BatteryModel.shouldRedraw(null, t, r1, t))
        assertTrue("clock moved back", BatteryModel.shouldRedraw(key, t, sameButWarmer, t - 1))
    }
}
