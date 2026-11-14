package dev.aarso.search.palette

import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow

data class UsageRecord(val count: Int, val lastMillis: Long)

/**
 * Local selection history (PAL-09). Lives in app-private storage only; nothing here can reach a network
 * (the package has no client, see the PAL_06 test). [reset] forgets everything.
 */
class UsageStore(private val maxEntries: Int = 2000) {
    private val map = LinkedHashMap<String, UsageRecord>()

    @Synchronized fun get(key: String): UsageRecord? = map[key]

    @Synchronized fun record(key: String, nowMillis: Long) {
        val old = map.remove(key)
        map[key] = UsageRecord((old?.count ?: 0) + 1, nowMillis)
        while (map.size > maxEntries) map.remove(map.keys.first())
    }

    @Synchronized fun reset() = map.clear()
    @Synchronized fun size() = map.size

    @Synchronized fun encode(): String = map.entries.joinToString("\n") { (k, v) -> "${k.replace('\t', ' ').replace('\n', ' ')}\t${v.count}\t${v.lastMillis}" }

    companion object {
        fun decode(raw: String?, maxEntries: Int = 2000): UsageStore {
            val s = UsageStore(maxEntries)
            raw?.lineSequence()?.forEach { line ->
                val p = line.split('\t')
                if (p.size == 3) s.map[p[0]] = UsageRecord(p[1].toIntOrNull() ?: return@forEach, p[2].toLongOrNull() ?: return@forEach)
            }
            return s
        }
    }
}

/**
 * Blends match quality with frequency and recency. The boost is capped below the gap between a prefix match (0.92)
 * and a substring match (0.6), so history breaks ties and near-ties but never promotes a poor match over a clear one.
 */
class Ranker(
    val store: UsageStore = UsageStore(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val frequencyWeight: Double = 0.18,
    private val recencyWeight: Double = 0.12,
    private val halfLifeDays: Double = 7.0,
) {
    fun blend(match: Double, key: String?): Double {
        if (match <= 0.0 || key == null) return match
        val u = store.get(key) ?: return match
        val freq = min(1.0, ln(1.0 + u.count) / ln(21.0))
        val ageDays = ((clock() - u.lastMillis).coerceAtLeast(0)) / 86_400_000.0
        val rec = 0.5.pow(ageDays / halfLifeDays)
        return match + frequencyWeight * freq + recencyWeight * rec
    }

    fun learn(result: PaletteResult) {
        if (result.learn) store.record(result.usageKey, clock())
    }

    fun reset() = store.reset()
}
