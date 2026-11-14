package com.bos.sphere.core.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetsTest {
    @Test fun NFR_01_warmHomeBudgetIs150msP95() {
        assertEquals(150.0, Budgets.HOME_WARM.limit, 0.0)
        assertTrue(Budgets.judgeP95(Budgets.HOME_WARM, List(100) { 120.0 }).pass)
        // 6 of 100 samples over budget puts p95 over the line
        assertFalse(Budgets.judgeP95(Budgets.HOME_WARM, List(94) { 100.0 } + List(6) { 400.0 }).pass)
    }

    @Test fun NFR_02_coldStartBudgetIs700msP95() {
        assertEquals(700.0, Budgets.COLD_START.limit, 0.0)
        assertFalse(Budgets.judgeP95(Budgets.COLD_START, listOf(900.0)).pass)
    }

    @Test fun NFR_03_paletteOpenBudgetIs100msP95() = assertEquals(100.0, Budgets.PALETTE_OPEN.limit, 0.0)

    @Test fun NFR_04_keystrokeBudgetIs50msP95() {
        assertEquals(50.0, Budgets.PALETTE_KEYSTROKE.limit, 0.0)
        assertTrue(Budgets.judgeP95(Budgets.PALETTE_KEYSTROKE, listOf(10.0, 20.0, 30.0, 49.0)).pass)
    }

    @Test fun NFR_05_jankIsPercentOfFramesOverTheRefreshBudget() {
        // 120 Hz: budget 8.33 ms; 1 of 100 frames over is exactly 1% and passes, 2 of 100 fails
        assertTrue(Budgets.judge(Budgets.JANK, Budgets.jankPercent(List(99) { 8.0 } + 20.0, 120.0)).pass)
        assertFalse(Budgets.judge(Budgets.JANK, Budgets.jankPercent(List(98) { 8.0 } + listOf(20.0, 20.0), 120.0)).pass)
        assertEquals(0.0, Budgets.jankPercent(emptyList(), 60.0), 0.0)
    }

    @Test fun NFR_06_pssBudgetIs180Mb() {
        assertEquals(180.0, Budgets.PSS.limit, 0.0)
        assertTrue(Budgets.judge(Budgets.PSS, 150.0).pass)
        assertFalse(Budgets.judge(Budgets.PSS, 181.0).pass)
    }

    @Test fun NFR_07_batteryBudgetIsHalfAPercentPerDay() {
        assertEquals(0.5, Budgets.BATTERY.limit, 0.0)
        assertFalse(Budgets.judge(Budgets.BATTERY, 0.8).pass)
    }

    @Test fun NFR_07_noWakeLocksInProductionSources() {
        val offenders = Repo.mainSources().filter { f ->
            f.readText().let { it.contains("WakeLock") || it.contains("WAKE_LOCK") || it.contains("newWakeLock") }
        }
        assertTrue("wake locks in: $offenders", offenders.isEmpty())
        assertFalse(Repo.file("app/src/main/AndroidManifest.xml").readText().contains("WAKE_LOCK"))
    }

    @Test fun NFR_08_apkSizeBudget() {
        assertTrue(ApkSizeBudget.withinBudget(24_999_999))
        assertFalse(ApkSizeBudget.withinBudget(25_000_000))
        assertFalse(ApkSizeBudget.withinBudget(0))
    }

    @Test fun NFR_08_gradleTaskLimitMatchesTheBudgetConstant() {
        val script = Repo.file("app/apk-size.gradle.kts").readText()
        assertTrue(script.contains("val apkSizeLimitBytes = ${ApkSizeBudget.MAX_BYTES.toString().reversed().chunked(3).joinToString("_").reversed()}L"))
        assertTrue(script.contains("checkReleaseApkSize"))
        assertTrue(Repo.file("app/build.gradle.kts").readText().contains("apk-size.gradle.kts"))
    }

    @Test fun NFR_01_toNFR_08_budgetTableMatchesTheSpec() {
        assertEquals((1..8).map { "NFR-0$it" }, Budgets.all.map { it.id })
    }

    @Test fun NFR_01_percentileUsesNearestRank() {
        assertEquals(95.0, Budgets.percentile((1..100).map { it.toDouble() }, 95.0), 0.0)
        assertTrue(Budgets.percentile(emptyList(), 95.0).isNaN())
    }

    @Test fun NFR_01_benchmarkSourcesAndBaselineProfileShip() {
        assertTrue(Repo.file("benchmark/README.md").exists())
        val bench = Repo.file("benchmark/src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        listOf("NFR-01", "NFR-02", "NFR-03", "NFR-04", "NFR-05").forEach { assertTrue(it, bench.contains(it)) }
        assertTrue(bench.contains("BaselineProfileRule"))
        val prof = Repo.file("app/src/main/baseline-prof.txt").readText()
        assertTrue(prof.contains("MainActivity") && prof.contains("HomeScreenKt"))
    }

    @Test fun NFR_06_toNFR_08_measurementScriptsExist() {
        listOf("tools/measure_pss.sh", "tools/measure_battery.sh", "tools/soak.sh", "tools/perf.sh").forEach {
            val f = Repo.file(it)
            assertTrue(it, f.exists() && f.readText().contains("adb"))
        }
    }
}
