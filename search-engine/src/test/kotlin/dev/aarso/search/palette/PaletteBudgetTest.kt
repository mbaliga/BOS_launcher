package dev.aarso.search.palette

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NFR-03 / NFR-04: a deterministic JVM stand-in for the palette benchmark. The engine ranks 500 apps and 5000 index rows
 * through the real fan-out, ranking and fallback path. The budgets are the spec's (open to first results 100 ms p95,
 * keystroke to updated app results 50 ms p95); CI machines are shared and slower than a phone is expected to be, so the
 * bound asserted here is [CI_FACTOR] times the budget. It catches algorithmic regressions (an accidental quadratic
 * scan), not the device figure, which `PaletteBenchmarks` records on hardware.
 */
class PaletteBudgetTest {
    private companion object {
        const val BUDGET_OPEN_MS = 100.0
        const val BUDGET_KEYSTROKE_MS = 50.0
        const val CI_FACTOR = 6.0
        const val RUNS = 25
    }

    private val words = listOf("Photo", "Mail", "Maps", "Notes", "Music", "Chat", "Pay", "Bank", "Game", "Clock", "News", "Video", "Fit", "Travel")

    private val apps = (0 until 500).map {
        AppRecord("com.example.app$it/0", "${words[it % words.size]} ${words[(it * 7) % words.size]} $it", "com.example.app$it.${words[it % words.size].lowercase()}")
    }

    /** Stand-in for a settings / shortcut / contact index: 5000 rows, each scored against the query. */
    private class IndexRows(val rows: List<String>) : PaletteProvider {
        override val id = "index"
        override val scope = PaletteScope.Everything
        override suspend fun query(q: PaletteQuery, limit: Int): List<PaletteResult> {
            val n = TextMatch.norm(q.text)
            if (n.isEmpty()) return emptyList()
            return rows.withIndex().mapNotNull { (i, title) ->
                val s = TextMatch.score(n, TextMatch.Target(title))
                if (s > 0) PaletteResult("row:$i", id, scope, title, score = s * 0.9, actions = listOf(ResultAction(ResultActionId.Open))) else null
            }.sortedByDescending { it.score }.take(limit)
        }
    }

    private val rows = (0 until 5000).map { "${words[it % words.size]} ${words[(it * 5) % words.size]} setting $it" }

    private fun engine() = PaletteEngine(ProviderRegistry(listOf(AppProvider({ apps }), IndexRows(rows))))

    private fun p95(samples: List<Double>): Double = samples.sorted()[((samples.size * 95) / 100).coerceAtMost(samples.size - 1)]

    private fun timeMs(block: () -> Unit): Double {
        val t0 = System.nanoTime(); block(); return (System.nanoTime() - t0) / 1e6
    }

    @Test fun NFR_03_openToFirstResultsOver500AppsAnd5000RowsIsInsideTheBudget() {
        val e = engine()
        repeat(10) { runBlocking { e.search("photo") } }
        // "Open": the first query on a fresh engine and a fresh app index each time (the index is built, then searched).
        val samples = (0 until RUNS).map { timeMs { runBlocking { engine().search("pho") } } }
        assertTrue("open p95 ${p95(samples)} ms", p95(samples) < BUDGET_OPEN_MS * CI_FACTOR)
    }

    @Test fun NFR_04_keystrokeToUpdatedAppResultsOver500AppsIsInsideTheBudget() {
        val e = engine()
        repeat(10) { runBlocking { e.search("photo") } }
        val keystrokes = listOf("p", "ph", "pho", "phot", "photo", "gm", "mus", "nots", "vidoe")
        val samples = ArrayList<Double>()
        repeat(4) { keystrokes.forEach { q -> samples += timeMs { runBlocking { e.search(q) } } } }
        assertTrue("keystroke p95 ${p95(samples)} ms", p95(samples) < BUDGET_KEYSTROKE_MS * CI_FACTOR)
    }

    @Test fun NFR_03_NFR_04_theBenchmarkInputIsReallyThe500AppsAnd5000RowsAndRanksThemBestFirst() {
        assertEquals(500, apps.size); assertEquals(5000, rows.size)
        val out = runBlocking { engine().search("photo") }
        assertTrue(out.isNotEmpty())
        // Ranked best first: scores (blended with an empty history) never rise down the list, apart from the pinned web row.
        val scored = out.filter { !it.pinnedLast }
        assertTrue(scored.zipWithNext().all { (a, b) -> a.score >= b.score - 1e-9 })
        assertTrue(out.last().pinnedLast)
    }
}
