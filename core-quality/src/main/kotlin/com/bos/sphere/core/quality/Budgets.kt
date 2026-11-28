package com.bos.sphere.core.quality

/** One non-functional budget from spec section 13. Values are the proposed targets; confirm after the first benchmark run. */
data class Budget(val id: String, val metric: String, val limit: Double, val unit: String, val note: String = "")

object Budgets {
    val HOME_WARM = Budget("NFR-01", "Home press to interactive home, warm (p95)", 150.0, "ms")
    val COLD_START = Budget("NFR-02", "Cold start to interactive home (p95)", 700.0, "ms")
    val PALETTE_OPEN = Budget("NFR-03", "Palette open to first results (p95)", 100.0, "ms")
    val PALETTE_KEYSTROKE = Budget("NFR-04", "Palette keystroke to updated app results (p95)", 50.0, "ms")
    val JANK = Budget("NFR-05", "Janky frames on home, drawer and transitions", 1.0, "percent")
    val PSS = Budget("NFR-06", "Memory at rest on home with 10 widgets (PSS)", 180.0, "MB")
    val BATTERY = Budget("NFR-07", "Battery use per day at rest", 0.5, "percent")
    val INSTALL = Budget("NFR-08", "Download size of the core flavour", 25.0, "MB")

    val all = listOf(HOME_WARM, COLD_START, PALETTE_OPEN, PALETTE_KEYSTROKE, JANK, PSS, BATTERY, INSTALL)

    /** Nearest-rank percentile (p in 0..100) of [samples]; NaN for an empty list. */
    fun percentile(samples: List<Double>, p: Double): Double {
        if (samples.isEmpty()) return Double.NaN
        val s = samples.sorted()
        val rank = Math.ceil(p / 100.0 * s.size).toInt().coerceIn(1, s.size)
        return s[rank - 1]
    }

    /** Percent of frames over their deadline; frames at or under [frameBudgetMs] are not janky. */
    fun jankPercent(frameTimesMs: List<Double>, refreshHz: Double): Double {
        if (frameTimesMs.isEmpty()) return 0.0
        val budget = 1000.0 / refreshHz
        return frameTimesMs.count { it > budget } * 100.0 / frameTimesMs.size
    }

    data class Verdict(val budget: Budget, val measured: Double, val pass: Boolean)

    fun judge(budget: Budget, measured: Double): Verdict = Verdict(budget, measured, !measured.isNaN() && measured <= budget.limit)

    /** Judge a latency series at p95 against [budget]. */
    fun judgeP95(budget: Budget, samplesMs: List<Double>): Verdict = judge(budget, percentile(samplesMs, 95.0))
}

/** NFR-08: the release APK (which stands for the download size) must stay under 25 MB. Decimal MB, the stricter reading. */
object ApkSizeBudget {
    const val MAX_BYTES: Long = 25_000_000L
    fun withinBudget(bytes: Long): Boolean = bytes in 1 until MAX_BYTES
    fun describe(bytes: Long): String = "%.2f MB of %.0f MB".format(bytes / 1_000_000.0, MAX_BYTES / 1_000_000.0)
}
