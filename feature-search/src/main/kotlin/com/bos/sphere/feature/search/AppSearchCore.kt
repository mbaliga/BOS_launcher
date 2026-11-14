package com.bos.sphere.feature.search

import dev.aarso.search.CoverageRecencyScorer
import dev.aarso.search.DocId
import dev.aarso.search.EvalContext
import dev.aarso.search.FacetField
import dev.aarso.search.FacetValueKind
import dev.aarso.search.FieldName
import dev.aarso.search.FieldRegistry
import dev.aarso.search.Matcher
import dev.aarso.search.NaturalQuery
import dev.aarso.search.NaturalVocabulary
import dev.aarso.search.Op
import dev.aarso.search.ParsedQuery
import dev.aarso.search.QueryParser
import dev.aarso.search.RelativeDate
import dev.aarso.search.SearchDoc
import java.time.ZoneId
import kotlin.math.ln

/** What the app search knows about one app — plain data, so the engine wiring is testable on a JVM. */
data class SearchEntry(
    val key: String,
    val label: String,
    val packageName: String,
    /** Human category name ("Games", "Audio" …), "Other" if unknown. */
    val category: String,
    /** Epoch millis of the last launch from this launcher; 0 = never. */
    val lastUsedMillis: Long,
    val launches: Int,
    /** In a work profile / secondary user. */
    val work: Boolean,
)

/**
 * App search on the shared `search-core` engine (vendored as `:search-engine`): the same query
 * language, facet evaluation, relative dates, natural-language interpretation and coverage × recency
 * ranking the rest of the constellation uses, with an app vocabulary:
 *
 * - `type:games` (also `kind:`, `category:`) · `pkg:google` (`package:`) · `is:work|recent|frequent|never`
 * - `used:today`, `used:yesterday`, `used:>7d`, `used:2026-03-01..2026-03-31` (`opened:`, `last:`)
 * - `launches:>5` (`opens:`) · quoted phrases, `OR`, `-negation`, parentheses, `/regex/`
 * - plain words match the name and the package as you type; if a query finds nothing as written it
 *   is retried as natural language (“games I used yesterday”, “music apps”).
 *
 * Results are ordered by relevance, with a small boost for apps you open a lot.
 */
class AppSearchCore(private val zone: ZoneId = ZoneId.systemDefault()) {

    private class Subject(val e: SearchEntry)

    private val registry: FieldRegistry<Subject> = FieldRegistry(
        listOf(
            facet("type", FacetValueKind.ENUM, setOf("kind", "category")) { s, op, v, _ ->
                val eq = s.e.category.equals(v.trim(), true) || s.e.category.trimEnd('s').equals(v.trim().trimEnd('s'), true)
                if (op == Op.NE) !eq else eq
            },
            facet("pkg", FacetValueKind.TEXT, setOf("package")) { s, op, v, _ ->
                val has = s.e.packageName.contains(v.trim(), true)
                if (op == Op.NE) !has else has
            },
            facet("is", FacetValueKind.ENUM) { s, op, v, ctx ->
                val yes = when (v.trim().lowercase()) {
                    "work" -> s.e.work
                    "never" -> s.e.launches == 0
                    "frequent" -> s.e.launches >= FREQUENT
                    "recent" -> s.e.lastUsedMillis > 0 && ctx.nowMillis - s.e.lastUsedMillis <= RECENT_MS
                    else -> false
                }
                if (op == Op.NE) !yes else yes
            },
            // "modified" is the key natural-language date phrases are rewritten to.
            facet("used", FacetValueKind.DATE, setOf("opened", "last", "modified")) { s, op, v, ctx ->
                val t = s.e.lastUsedMillis
                if (t <= 0L) return@facet false
                val range = RelativeDate.resolveRange(v, ctx, zone) ?: return@facet false
                when (op) {
                    Op.GTE, Op.GT -> t >= range.startInclusiveMillis
                    Op.LTE, Op.LT -> t < range.endExclusiveMillis
                    Op.NE -> t !in range
                    Op.EQ -> t in range
                }
            },
            facet("launches", FacetValueKind.NUMBER, setOf("opens")) { s, op, v, _ ->
                val n = v.trim().toIntOrNull() ?: return@facet false
                when (op) {
                    Op.EQ -> s.e.launches == n
                    Op.NE -> s.e.launches != n
                    Op.GT -> s.e.launches > n
                    Op.GTE -> s.e.launches >= n
                    Op.LT -> s.e.launches < n
                    Op.LTE -> s.e.launches <= n
                }
            },
        ),
    )

    private val vocabulary = NaturalVocabulary(
        mapOf(
            "games" to "Games", "gaming" to "Games",
            "music" to "Audio", "audio" to "Audio", "podcasts" to "Audio",
            "videos" to "Video", "movies" to "Video", "video" to "Video",
            "photos" to "Images", "pictures" to "Images", "images" to "Images", "camera" to "Images",
            "social" to "Social", "chat" to "Social", "messaging" to "Social",
            "news" to "News", "maps" to "Maps", "navigation" to "Maps",
            "productivity" to "Productivity", "office" to "Productivity",
        ),
    )

    private val matcher = Matcher(
        registry = registry,
        scorer = CoverageRecencyScorer(
            fieldWeights = mapOf(TITLE to 3.0, PACKAGE to 0.6),
            recencyWeight = 0.35,
        ),
    )

    /** An index over [entries]; build once per inventory/usage change and reuse for each keystroke. */
    inner class Index(entries: List<SearchEntry>) {
        private val subjects: List<Subject> = entries.map { Subject(it) }
        private val docs: List<SearchDoc> = entries.map { e ->
            SearchDoc(
                id = DocId(e.key),
                timestampMillis = e.lastUsedMillis,
                text = mapOf(TITLE to e.label, PACKAGE to e.packageName),
            )
        }

        /** Keys of matching apps, best first. Blank query → none. */
        fun search(query: String, nowMillis: Long = System.currentTimeMillis(), limit: Int = 14): List<String> {
            val q = query.trim()
            if (q.isEmpty()) return emptyList()
            val ctx = EvalContext(nowMillis)
            val plain = QueryParser.parse(q, registry, ctx, zone)
            var hits = rank(plain, ctx)
            if (hits.isEmpty()) {
                // Nothing as written: read it as natural language ("games I used yesterday").
                val natural = NaturalQuery.interpret(stripFiller(q), vocabulary, registry, ctx, zone)
                if (natural.root != plain.root) hits = rank(natural, ctx)
            }
            return hits.take(limit)
        }

        private fun rank(parsed: ParsedQuery, ctx: EvalContext): List<String> {
            class Hit(val e: SearchEntry, val score: Double)
            val out = ArrayList<Hit>()
            for (i in subjects.indices) {
                val scored = matcher.score(subjects[i], docs[i], parsed, ctx) ?: continue
                // A little favour for apps you actually open, so ties go to your habits.
                out += Hit(subjects[i].e, scored.score + 0.12 * ln(1.0 + subjects[i].e.launches))
            }
            return out.sortedWith(compareByDescending<Hit> { it.score }.thenBy { it.e.label.lowercase() }).map { it.e.key }
        }
    }

    /** Drops chatty words ("games I used yesterday" → "games yesterday") before natural-language reading. */
    private fun stripFiller(q: String): String =
        q.split(Regex("\\s+")).filter { it.lowercase() !in FILLER }.joinToString(" ").ifBlank { q }

    private fun facet(
        key: String,
        kind: FacetValueKind,
        aliases: Set<String> = emptySet(),
        match: (Subject, Op, String, EvalContext) -> Boolean,
    ): FacetField<Subject> = object : FacetField<Subject> {
        override val key = key
        override val valueKind = kind
        override val aliases = aliases
        override fun isBacked(value: String) = true
        override fun matches(subject: Subject, op: Op, value: String, ctx: EvalContext) = runCatching { match(subject, op, value, ctx) }.getOrDefault(false)
    }

    companion object {
        private val TITLE = FieldName("title")
        private val PACKAGE = FieldName("package")
        private val FILLER = setOf("i", "ive", "i've", "used", "use", "opened", "open", "launched", "apps", "app", "that", "which", "have", "had", "from", "the", "my", "me", "show", "find", "all")
        private const val FREQUENT = 5
        private const val RECENT_MS = 3L * 24 * 3600 * 1000
    }
}
