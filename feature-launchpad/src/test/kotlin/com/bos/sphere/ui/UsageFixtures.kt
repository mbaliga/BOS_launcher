package com.bos.sphere.ui

import java.time.LocalDate
import java.time.ZoneOffset

/** Wave 28: the week the usage block tests and screenshots draw. Today is Sunday 10 March 2024, noon, UTC. */
object UsageFixtures {
    val zone: ZoneOffset = ZoneOffset.UTC
    val today: LocalDate = LocalDate.of(2024, 3, 10)
    val now: Long = today.atTime(12, 0).toInstant(zone).toEpochMilli()
    fun dayStart(offsetFromToday: Int): Long = today.plusDays(offsetFromToday.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
    private const val MIN = 60_000L

    val labels = mapOf("demo.chrome" to "Browser", "demo.chat" to "Chat", "demo.maps" to "Maps", "demo.mail" to "Mail", "demo.news" to "News", "demo.camera" to "Camera")
    val labelOf: (String) -> String = { labels[it] ?: it }

    /** Minutes per app, by days before today (0 = today). */
    val minutes: Map<Int, Map<String, Int>> = mapOf(
        0 to mapOf("demo.chrome" to 70, "demo.chat" to 45, "demo.maps" to 20, "demo.mail" to 10, "demo.camera" to 6, "demo.news" to 3),
        1 to mapOf("demo.chat" to 80, "demo.chrome" to 30, "demo.mail" to 15),
        2 to mapOf("demo.maps" to 90, "demo.chrome" to 20),
        3 to emptyMap(),
        4 to mapOf("demo.chrome" to 100, "demo.news" to 40, "demo.camera" to 30, "demo.chat" to 20, "demo.mail" to 10),
        5 to mapOf("demo.mail" to 25),
        6 to mapOf("demo.chrome" to 5),
    )

    class FakeSource(var granted: Boolean = true, val data: Map<Int, Map<String, Int>> = minutes) : UsageWeekSource {
        var reads = 0
        override fun granted() = granted
        override fun samples(fromMs: Long, toMs: Long): List<AppTime> {
            reads++
            val off = (0..6).firstOrNull { dayStart(-it) == fromMs } ?: return emptyList()
            return data[off].orEmpty().map { (k, v) -> AppTime(k, v * MIN) }
        }
    }

    fun repo(source: UsageWeekSource = FakeSource()) = UsageWeekRepo(source, zone = { zone })

    fun week(data: Map<Int, Map<String, Int>> = minutes): UsageWeek =
        UsageShaping.week(UsageWeekRepo(FakeSource(data = data), zone = { zone }).load(now), zone, today, java.util.Locale.ENGLISH)
}
