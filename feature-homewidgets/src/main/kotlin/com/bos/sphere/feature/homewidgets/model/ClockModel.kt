package com.bos.sphere.feature.homewidgets.model

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * The clock widget's one piece of data the platform doesn't draw by itself: the next alarm. The
 * time and the date are `TextClock`s, which the launcher keeps current without waking Sphere.
 */
object ClockModel {

    /** How the next alarm is named: by weekday while that is unambiguous (the coming six days), else by date. */
    enum class AlarmDay { WEEKDAY, DATE }

    data class NextAlarm(val at: ZonedDateTime, val day: AlarmDay)

    /**
     * The next alarm as the clock shows it, from `AlarmManager.getNextAlarmClock()` ([triggerMillis];
     * null = no alarm set). An absolute weekday ("Wed 07:00") rather than "tomorrow" keeps the label
     * true without a redraw at midnight; the system announces every change of the next alarm
     * (`NEXT_ALARM_CLOCK_CHANGED`), which redraws it. An alarm already in the past is no alarm.
     */
    fun nextAlarm(triggerMillis: Long?, nowMillis: Long, zone: ZoneId): NextAlarm? {
        if (triggerMillis == null || triggerMillis < nowMillis) return null
        val at = Instant.ofEpochMilli(triggerMillis).atZone(zone)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val daysAhead = ChronoUnit.DAYS.between(today, at.toLocalDate())
        return NextAlarm(at, if (daysAhead < 7) AlarmDay.WEEKDAY else AlarmDay.DATE)
    }
}
