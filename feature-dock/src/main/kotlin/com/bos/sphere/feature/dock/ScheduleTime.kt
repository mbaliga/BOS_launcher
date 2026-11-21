package com.bos.sphere.feature.dock

import java.util.Calendar
import java.util.TimeZone

/** Time maths for scheduled launches (WSP-11). Pure and zone-explicit so it is testable. */
object ScheduleTime {
    /** The next moment the wall clock reads [hour]:[minute] after [nowMillis] (today if still ahead, else tomorrow). */
    fun nextOccurrence(nowMillis: Long, hour: Int, minute: Int, zone: TimeZone = TimeZone.getDefault()): Long {
        val c = Calendar.getInstance(zone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23)); set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= nowMillis) c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    fun inMinutes(nowMillis: Long, minutes: Int): Long = nowMillis + minutes.coerceAtLeast(1) * 60_000L

    /** "14:05", zero padded; the picker shows the same text it schedules for. */
    fun clock(hour: Int, minute: Int) = "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    fun wrap(value: Int, delta: Int, size: Int) = ((value + delta) % size + size) % size
}
