package dev.aarso.search.palette

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What one search will run: the parsed scope and text plus the providers that serve it. */
class Plan(val scoped: ScopedQuery, val providers: List<PaletteProvider>) {
    val text get() = scoped.rest
    val scope get() = scoped.scope
}

/** One provider's answer, as it arrives. [results] are unranked; the engine ranks the union. */
data class Batch(val providerId: String, val results: List<PaletteResult>, val timedOut: Boolean = false)

/**
 * The palette engine (PAL-06, PAL-09, PAL-12, PAL-14): take the scope from the wheel, fan out to providers, stream each
 * answer as it lands, rank by match blended with local history, and always end with the web fallback.
 */
class PaletteEngine(
    val registry: ProviderRegistry,
    val ranker: Ranker = Ranker(),
    val web: WebFallback = WebFallback(),
    private val slowTimeoutMs: Long = 1500,
    private val perProviderLimit: Int = 8,
) {
    /** [scope] comes from the category wheel; the text is searched as typed. */
    fun plan(raw: String, scope: PaletteScope = PaletteScope.Everything): Plan {
        val sq = ScopedQuery(scope, raw.trim())
        return Plan(sq, registry.forScope(sq.scope))
    }

    /** Emits one [Batch] per provider as it finishes. Cancelling the collector cancels every provider. */
    fun stream(plan: Plan, skip: Set<String> = emptySet()): Flow<Batch> = channelFlow {
        val blank = plan.text.isBlank()
        for (p in plan.providers) {
            if (p.id in skip) continue
            launch {
                try {
                    val q = PaletteQuery(plan.text, plan.scope)
                    val r = if (p.slow) {
                        withTimeoutOrNull(slowTimeoutMs) { if (blank) p.suggestions(perProviderLimit) else p.query(q, perProviderLimit) }
                    } else {
                        if (blank) p.suggestions(perProviderLimit) else p.query(q, perProviderLimit)
                    }
                    send(Batch(p.id, r ?: emptyList(), timedOut = r == null))
                } catch (e: CancellationException) {
                    p.cancel(); throw e
                } catch (_: Exception) {
                    // A broken provider must never take the palette down.
                    send(Batch(p.id, emptyList()))
                }
            }
        }
    }

    /** Awaits every provider and returns the final ranked list (used by tests and non-streaming callers). */
    suspend fun search(raw: String, scope: PaletteScope = PaletteScope.Everything): List<PaletteResult> {
        val plan = plan(raw, scope)
        val all = stream(plan).toList().flatMap { it.results }
        return finalList(plan, all)
    }

    /** Ranked results, then the web fallback (and store lookup) last. */
    fun finalList(plan: Plan, results: List<PaletteResult>): List<PaletteResult> {
        val ranked = rank(results)
        return ranked + listOfNotNull(web.row(plan.text))
    }

    fun rank(results: List<PaletteResult>): List<PaletteResult> =
        results.filter { !it.pinnedLast }
            .map { it to ranker.blend(it.score, it.usageKey.takeIf { _ -> it.learn }) }
            .sortedWith(compareByDescending<Pair<PaletteResult, Double>> { it.second }.thenBy { it.first.title.lowercase() }.thenBy { it.first.id })
            .map { it.first }

    /** The user picked [result]: learn from it (PAL-09). */
    fun onSelected(result: PaletteResult) = ranker.learn(result)

    fun resetLearning() = ranker.reset()

    fun cancelProviders() = registry.providers().forEach { runCatching { it.cancel() } }
}
