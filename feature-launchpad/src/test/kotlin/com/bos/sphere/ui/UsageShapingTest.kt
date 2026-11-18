package com.bos.sphere.ui

import com.bos.sphere.core.design.L10n
import com.bos.sphere.ui.viz.VizFormat
import com.bos.sphere.ui.viz.VizBarGeometry
import com.bos.sphere.ui.viz.VizStepsGeometry
import com.bos.sphere.ui.viz.VizTexture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 28 (usageTimeline): the data shaping behind the usage block, on the JVM. */
class UsageShapingTest {
    @After fun reset() { L10n.setLanguage("en") }
    private val M = 60_000L
    private fun day(vararg a: Pair<String, Int>) = DayTime(0, a.map { AppTime(it.first, it.second * M) })

    @Test fun USG_01_theWeekHasSevenColumnsOldestFirstAndTodayLast() {
        val w = UsageFixtures.week()
        assertEquals(7, w.columns.size)
        assertEquals(listOf(false, false, false, false, false, false, true), w.columns.map { it.isToday })
        assertEquals((6 downTo 0).map { UsageFixtures.dayStart(-it) }, w.columns.map { it.dayStartMs })
        assertEquals(154 * M, w.columns.last().totalMs)
        assertEquals("S", w.columns.last().initial.take(1)) // Sunday
        assertEquals("Sun", w.columns.last().dayName)
        assertEquals(0L, w.columns[3].totalMs)
    }

    @Test fun USG_02_theTopFourAppsAreKeptAndTheRestFoldIntoOtherWithStipple() {
        val p = UsageFixtures.week().parts(6)
        assertEquals(listOf("demo.chrome", "demo.chat", "demo.maps", "demo.mail", ""), p.map { it.key })
        assertEquals(9 * M, p.last().ms)
        assertEquals(VizTexture.Stipple, p.last().texture)
        assertEquals(154 * M, p.sumOf { it.ms })
        assertTrue(p.sumOf { it.percent } in 99..101)
    }

    @Test fun USG_02_fourAppsOrFewerHaveNoOtherPart() {
        val p = UsageFixtures.week().parts(5) // yesterday: chat 80, browser 30, mail 15
        assertEquals(listOf("demo.chat", "demo.chrome", "demo.mail"), p.map { it.key })
        assertFalse(p.any { it.isOther })
        assertEquals(64, p[0].percent)
        val one = UsageFixtures.week().parts(1) // five days ago: mail only
        assertEquals(100, one.single().percent)
    }

    // ---- wave 29: the ranked rows ----

    @Test fun USG_40_rowsAreTheTopFourAppsThenOtherEachWithItsShareOfTheLongest() {
        val r = UsageFixtures.week().rows(6)
        assertEquals(listOf("demo.chrome", "demo.chat", "demo.maps", "demo.mail", ""), r.map { it.key })
        assertEquals(listOf(70L, 45L, 20L, 10L, 9L).map { it * M }, r.map { it.ms })
        assertEquals("the longest is a full bar", 1f, r[0].share, 0f)
        assertEquals(45f / 70f, r[1].share, 1e-6f); assertEquals(9f / 70f, r[4].share, 1e-6f)
        assertEquals(listOf(VizTexture.Solid, VizTexture.Hatch, VizTexture.Dots, VizTexture.Fade, VizTexture.Stipple), r.map { it.texture })
        assertTrue(r.last().isOther)
    }

    @Test fun USG_40_otherIsTheFullBarWhenTheRestAddsUpToMoreThanTheTopApp() {
        val d = day("a" to 10, "b" to 9, "c" to 8, "d" to 7, "e" to 6, "f" to 6, "g" to 6)
        val r = UsageShaping.rows(d, UsageShaping.weekRank(listOf(d)))
        assertEquals(5, r.size)
        assertEquals(18 * M, r.last().ms); assertEquals(1f, r.last().share, 0f); assertEquals(10f / 18f, r[0].share, 1e-6f)
    }

    @Test fun USG_40_tiesKeepTheirKeyOrderAndShareOne() {
        val d = day("b" to 10, "a" to 10, "c" to 10)
        val r = UsageShaping.rows(d, UsageShaping.weekRank(listOf(d)))
        assertEquals(listOf("a", "b", "c"), r.map { it.key }); assertEquals(listOf(1f, 1f, 1f), r.map { it.share })
    }

    @Test fun USG_40_fewerThanFiveAppsHaveNoOtherRowAndOneAppIsOneFullRow() {
        assertEquals(listOf("demo.chat", "demo.chrome", "demo.mail"), UsageFixtures.week().rows(5).map { it.key })
        val one = UsageFixtures.week().rows(1).single()
        assertEquals("demo.mail", one.key); assertEquals(1f, one.share, 0f)
    }

    @Test fun USG_40_aDayWithNoTimeHasNoRowsAndNoNaN() {
        assertTrue(UsageFixtures.week().rows(3).isEmpty())
        assertTrue(UsageShaping.rows(null, emptyList()).isEmpty())
        assertTrue(UsageShaping.rows(day("a" to 0, "b" to -5), listOf("a", "b")).isEmpty())
    }

    @Test fun USG_03_tiesBreakByAppKeySoTheOrderNeverFlips() {
        val d = day("b" to 10, "a" to 10, "c" to 10, "e" to 10, "d" to 10, "f" to 10)
        val rank = UsageShaping.weekRank(listOf(d))
        assertEquals(listOf("a", "b", "c", "d", "e", "f"), rank)
        val p = UsageShaping.parts(d, rank)
        assertEquals(listOf("a", "b", "c", "d", ""), p.map { it.key })
        assertEquals(20 * M, p.last().ms)
        assertEquals(p, UsageShaping.parts(day("f" to 10, "d" to 10, "e" to 10, "c" to 10, "a" to 10, "b" to 10), rank))
    }

    @Test fun USG_04_theTextureOrderIsFixedPerWeekRank() {
        val w = UsageFixtures.week()
        assertEquals(listOf("demo.chrome", "demo.chat", "demo.maps", "demo.mail", "demo.news", "demo.camera"), w.weekRank)
        assertEquals(listOf(VizTexture.Solid, VizTexture.Hatch, VizTexture.Dots, VizTexture.Fade, VizTexture.Stipple), w.parts(6).map { it.texture })
    }

    @Test fun USG_04_anAppKeepsItsTextureWhenAnotherDayIsSelected() {
        val w = UsageFixtures.week()
        val tex = (0..6).flatMap { i -> w.parts(i).filter { !it.isOther } }.groupBy { it.key }
        tex.forEach { (k, list) -> if (w.weekRank.indexOf(k) < 4) assertEquals("$k keeps one texture on every day", 1, list.map { it.texture }.toSet().size) }
        assertEquals(VizTexture.Solid, w.parts(0).first { it.key == "demo.chrome" }.texture)  // six days ago
        assertEquals(VizTexture.Hatch, w.parts(5).first { it.key == "demo.chat" }.texture)    // yesterday
        assertEquals(VizTexture.Hatch, w.parts(6).first { it.key == "demo.chat" }.texture)    // today
    }

    @Test fun USG_04_aLowRankedAppTakesAFreeTextureAndNoTextureRepeatsWithinADay() {
        val w = UsageFixtures.week()
        val p = w.parts(2) // day index 2 = four days before today: chrome 100, news 40, camera 30, chat 20 + mail 10 folded
        assertEquals(listOf("demo.chrome", "demo.news", "demo.camera", "demo.chat", ""), p.map { it.key })
        assertEquals(VizTexture.Solid, p[0].texture); assertEquals(VizTexture.Hatch, p[3].texture) // chrome and chat keep theirs
        assertEquals(listOf(VizTexture.Dots, VizTexture.Fade), listOf(p[1].texture, p[2].texture)) // news and camera take what was left
        for (i in 0..6) assertEquals("day $i", w.parts(i).size, w.parts(i).map { it.texture }.toSet().size)
    }

    @Test fun USG_04_neverMoreThanEightTexturesAndAppsOnlyUseTheFirstFour() {
        val many = day(*(1..20).map { "app$it" to it }.toTypedArray())
        val p = UsageShaping.parts(many, UsageShaping.weekRank(listOf(many)))
        assertEquals(5, p.size)
        assertTrue(p.dropLast(1).all { it.texture in APP_TEXTURES })
        assertEquals(OTHER_TEXTURE, p.last().texture)
        val (kept, rest) = VizFormat.fold((1..9).toList())
        assertEquals(7, kept.size); assertEquals(2, rest.size) // a ninth part folds into Other: seven textures plus one Other
        assertEquals(VizTexture.Solid, VizTexture.at(8))
    }

    @Test fun USG_05_emptyAndBadInputGivesNoPartsAndNoNaN() {
        assertEquals(emptyList<UsagePart>(), UsageShaping.parts(null, emptyList()))
        assertEquals(emptyList<UsagePart>(), UsageShaping.parts(DayTime(0, emptyList()), emptyList()))
        val bad = DayTime(0, listOf(AppTime("a", -5), AppTime("b", 0), AppTime("c", 30 * M)))
        assertEquals(listOf("c"), UsageShaping.parts(bad, listOf("c")).map { it.key })
        assertEquals(0L, UsageShaping.total(DayTime(0, listOf(AppTime("a", -1)))))
        assertFalse(UsageFixtures.week(emptyMap()).hasData)
        assertEquals(0, VizFormat.percent(5, 0)); assertEquals(0, VizFormat.percent(-5, 10)); assertEquals(50, VizFormat.percent(5, 10))
        assertEquals(0f, VizFormat.barHeight(Double.NaN, 10.0, 30f)); assertEquals(3f, VizFormat.barHeight(5.0, Double.NaN, 30f)) // a value with no usable maximum is a stub, never NaN
        assertEquals(0f, VizFormat.barHeight(Double.POSITIVE_INFINITY, 10.0, 30f)); assertEquals(0f, VizFormat.barHeight(0.0, 10.0, 30f))
        assertEquals(3f, VizFormat.barHeight(0.001, 10.0, 30f)); assertEquals(30f, VizFormat.barHeight(10.0, 10.0, 30f))
        assertEquals(0f, VizBarGeometry.length(Float.NaN, 100f)); assertEquals(0f, VizBarGeometry.length(-1f, 100f)); assertEquals(0f, VizBarGeometry.length(0.5f, Float.NaN))
        assertEquals(100f, VizBarGeometry.length(7f, 100f)); assertEquals(3f, VizBarGeometry.length(0.001f, 100f)); assertEquals(50f, VizBarGeometry.length(0.5f, 100f))
        val g = VizStepsGeometry.of(Float.NaN, Float.POSITIVE_INFINITY, 0)
        assertEquals(0f, g.plot); assertEquals(0f, g.cell)
    }

    @Test fun USG_06_durationsAreFormattedAsMinutesAndHours() {
        assertEquals("<1 min", UsageFormat.full(0)); assertEquals("<1 min", UsageFormat.full(59_999)); assertEquals("<1 min", UsageFormat.full(-5))
        assertEquals("1 min", UsageFormat.full(60_000)); assertEquals("59 min", UsageFormat.full(59 * M))
        assertEquals("1 h 0 min", UsageFormat.full(60 * M)); assertEquals("2 h 5 min", UsageFormat.full(125 * M))
        assertEquals("0", UsageFormat.compact(0)); assertEquals("35 min", UsageFormat.compact(35 * M))
        assertEquals("2.1 h", UsageFormat.compact(125 * M).replace(',', '.'))
    }

    @Test fun USG_06_durationsAreTranslated() {
        L10n.setLanguage("de")
        assertEquals("2 Std. 5 Min.", UsageFormat.full(125 * M))
    }

    // ---- the repository ----

    @Test fun USG_07_pastDaysAreReadOnceAndTodayAgainOnlyAfterTheTtl() {
        val src = UsageFixtures.FakeSource(); val repo = UsageWeekRepo(src, ttlMs = 60_000, zone = { UsageFixtures.zone })
        val a = repo.load(UsageFixtures.now)
        assertEquals(7, src.reads)
        repo.load(UsageFixtures.now + 10_000)
        assertEquals("within the ttl nothing is read", 7, src.reads)
        val b = repo.load(UsageFixtures.now + 61_000)
        assertEquals("only today is read again after the ttl: the six earlier days had ended when they were read", 8, src.reads)
        assertEquals(a, b)
        repo.invalidate(); repo.load(UsageFixtures.now); assertEquals(15, src.reads)
    }

    @Test fun USG_07_aDayReadAfterItEndedIsNeverReadAgain() {
        val src = UsageFixtures.FakeSource(); val repo = UsageWeekRepo(src, ttlMs = 1_000, zone = { UsageFixtures.zone })
        repo.load(UsageFixtures.dayStart(1) + 5_000) // tomorrow 00:00:05: every day of the window, yesterday (today in fixture) included, has ended except tomorrow
        val first = src.reads
        repo.load(UsageFixtures.dayStart(1) + 3_600_000) // an hour later: the six finished days stay cached, only the new today is re-read
        assertEquals(first + 1, src.reads)
    }
}
