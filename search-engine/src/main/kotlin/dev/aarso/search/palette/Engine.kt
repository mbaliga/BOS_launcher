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
 * The palette engine (PAL-06, PAL-09, PAL-12, PAL-14): parse the scope prefix, fan out to providers, stream each
 * answer as it lands, rank by match blended with local history, and always end with the web fallback.
 */
class PaletteEngine(
    val registry: ProviderRegistry,
    val prefixes: PrefixTable = PrefixTable(),
    val ranker: Ranker = Ranker(),
    val web: WebFallback = WebFallback(),
    private val slowTimeoutMs: Long = 1500,
    private val perProviderLimit: Int = 8,
) {
    fun plan(raw: String): Plan {
        val sq = prefixes.parse(raw)
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
    suspend fun search(raw: String): List<PaletteResult> {
        val plan = plan(raw)
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

/** Keeps the row under the selection where it is while late providers add results (PAL-12). */
object StableMerge {
    /**
     * [shown] is what the user sees now, [ideal] the freshly ranked list. Everything up to and including the selected
     * row keeps its position (its data is refreshed); the rest follows the new ranking; pinned-last rows stay last.
     */
    fun merge(shown: List<PaletteResult>, ideal: List<PaletteResult>, selectedId: String?): List<PaletteResult> {
        val body = ideal.filter { !it.pinnedLast }
        val tail = ideal.filter { it.pinnedLast }
        val shownBody = shown.filter { !it.pinnedLast }
        val at = if (selectedId == null) -1 else shownBody.indexOfFirst { it.id == selectedId }
        if (at < 0) return body + tail
        val fresh = body.associateBy { it.id }
        val prefix = shownBody.take(at + 1).map { fresh[it.id] ?: it }
        val used = prefix.map { it.id }.toSet()
        return prefix + body.filter { it.id !in used } + tail
    }
}
