package dev.aarso.search.palette

import java.text.Normalizer

/** Shared fuzzy text scoring (PAL-02): prefix, word prefix, initials, substring and a single typo. */
object TextMatch {
    private val MARKS = Regex("\\p{M}+")
    private val SPLIT = Regex("[^\\p{L}\\p{N}]+")

    fun norm(s: String): String = MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase().trim()

    fun words(normalized: String): List<String> = normalized.split(SPLIT).filter { it.isNotEmpty() }

    fun initials(words: List<String>): String = words.joinToString("") { it.take(1) }

    /** True when [a] and [b] differ by at most one insertion, deletion, substitution or adjacent swap (and are not equal). */
    fun oneEdit(a: String, b: String): Boolean {
        if (a == b) return false
        val d = a.length - b.length
        if (d > 1 || d < -1) return false
        if (d == 0) {
            val diff = a.indices.filter { a[it] != b[it] }
            if (diff.size == 1) return true
            return diff.size == 2 && diff[1] == diff[0] + 1 && a[diff[0]] == b[diff[1]] && a[diff[1]] == b[diff[0]]
        }
        val (long, short) = if (d > 0) a to b else b to a
        var i = 0
        while (i < short.length && long[i] == short[i]) i++
        return long.substring(i + 1) == short.substring(i)
    }

    /** A pre-normalised target; build once per app and reuse for every keystroke. */
    class Target(raw: String) {
        val text = norm(raw)
        val words = words(text)
        val initials = initials(words)
    }

    /** 0 = no match. [q] must already be [norm]alised. */
    fun score(q: String, t: Target): Double {
        if (q.isEmpty() || t.text.isEmpty()) return 0.0
        if (t.text == q) return 1.0
        if (t.words.size >= 2 && q.length >= 2 && t.initials == q) return 0.95
        if (t.text.startsWith(q)) return 0.92
        val qs = words(q)
        if (qs.size > 1) {
            if (qs.all { w -> t.words.any { it.startsWith(w) } }) return 0.8
        } else if (t.words.any { it.startsWith(q) }) return 0.82
        if (qs.size == 1 && q.length >= 2 && t.initials.startsWith(q)) return 0.78
        if (q.length >= 2 && t.text.contains(q)) return 0.6
        if (q.length >= 4 && qs.size == 1) {
            val near = t.words.any { w ->
                (q.length - 1..q.length + 1).any { len -> w.length >= len && oneEdit(q, w.take(len)) }
            }
            if (near) return 0.5
        }
        return 0.0
    }

    fun score(query: String, text: String): Double = score(norm(query), Target(text))
}
