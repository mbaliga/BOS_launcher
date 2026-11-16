package com.bos.sphere.core.data

import java.text.Normalizer
import java.util.Locale

/**
 * STR-13: sender-name normalisation and last-name logic. Pure and offline; nothing here touches the network.
 *
 * [clean] removes decoration (emoji, symbols, bracketed tags such as "(WhatsApp)", a trailing " - WhatsApp") but keeps
 * case and diacritics, so a surname can be shown back to the user. [normalise] then folds case and diacritics so
 * "JOSÉ" and "jose" are the same. [key] additionally turns "Last, First" into "first last", the identity key used to
 * merge one human across apps.
 */
object PersonNames {
    private val APP_WORDS = listOf(
        "whatsapp business", "whatsapp", "telegram", "signal", "messenger", "messages", "sms", "instagram", "slack",
        "teams", "discord", "viber", "line", "wechat", "skype", "gmail", "email", "mail",
    )
    private val BRACKETS = Regex("""[(\[{（［【][^)\]}）］】]*[)\]}）］】]""")
    private val SUFFIX = Regex(
        """\s*(?:[-–—|·•:@]|\bvia\b|\bon\b|\bfrom\b)\s*(?:${APP_WORDS.joinToString("|") { Regex.escape(it) }})\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private const val EDGE = "~*_.•·-–—:|\"'`"

    /** Generational and title suffixes that are not part of a surname. */
    internal val NAME_SUFFIXES = setOf("jr", "sr", "ii", "iii", "iv", "phd", "md", "esq", "dds")

    private fun isDecoration(cp: Int): Boolean {
        if (cp in 0xFE00..0xFE0F || cp == 0x200D || cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF) return true
        return when (Character.getType(cp).toByte()) {
            Character.OTHER_SYMBOL, Character.MODIFIER_SYMBOL, Character.FORMAT, Character.PRIVATE_USE,
            Character.SURROGATE, Character.UNASSIGNED -> true
            else -> false
        }
    }

    /** A person's name is never longer than this; anything longer is cut before the regexes run (bracket matching is quadratic on unbalanced text). */
    const val MAX_NAME_CHARS = 512

    fun clean(raw: String): String {
        var s = (if (raw.length > MAX_NAME_CHARS) raw.substring(0, MAX_NAME_CHARS) else raw).replace("™", "").replace("®", "").replace("©", "")
        s = Normalizer.normalize(s, Normalizer.Form.NFKC)
        s = BRACKETS.replace(s, " ")
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            when {
                Character.isISOControl(cp) || Character.isWhitespace(cp) -> sb.append(' ')
                isDecoration(cp) -> Unit
                else -> sb.appendCodePoint(cp)
            }
        }
        var out = sb.toString().replace(Regex("\\s+"), " ").trim { it.isWhitespace() || it in EDGE }
        while (true) {
            val stripped = SUFFIX.replace(out, "").trim { it.isWhitespace() || it in EDGE }
            if (stripped == out || stripped.isEmpty()) break
            out = stripped
        }
        return out
    }

    private val KEEP_MARKS = setOf(
        Character.UnicodeScript.LATIN, Character.UnicodeScript.GREEK, Character.UnicodeScript.CYRILLIC,
        Character.UnicodeScript.ARABIC, Character.UnicodeScript.HEBREW,
    )

    /** Case and diacritics folded; punctuation other than '+', '-' and ',' becomes a space. */
    fun fold(s: String): String {
        val d = Normalizer.normalize(s, Normalizer.Form.NFKD)
        val sb = StringBuilder(d.length)
        var base: Character.UnicodeScript? = null
        var i = 0
        while (i < d.length) {
            val cp = d.codePointAt(i)
            i += Character.charCount(cp)
            val type = Character.getType(cp).toByte()
            when {
                type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK ->
                    if (base == null || base !in KEEP_MARKS) sb.appendCodePoint(cp)
                type == Character.COMBINING_SPACING_MARK -> sb.appendCodePoint(cp) // vowel signs of Indic scripts are part of the letters
                cp == '\''.code || cp == 0x2019 || cp == '`'.code || cp == 0x02BC -> Unit
                cp == 0x131 -> { sb.append('i'); base = Character.UnicodeScript.LATIN } // Turkish dotless i: "ISIK" and "ısık" are one name
                Character.isLetterOrDigit(cp) -> {
                    sb.append(String(Character.toChars(cp)).lowercase(Locale.ROOT))
                    base = runCatching { Character.UnicodeScript.of(cp) }.getOrNull()
                }
                cp == '+'.code || cp == '-'.code || cp == ','.code -> sb.appendCodePoint(cp)
                else -> sb.append(' ')
            }
        }
        return sb.toString().replace(Regex("\\s*,\\s*"), ", ").replace(Regex("\\s+"), " ").trim(' ', ',', '-')
    }

    fun normalise(raw: String): String = fold(clean(raw))

    /** The identity key of a sender name: normalised, with "Last, First" turned around. Blank for a blank name. */
    fun key(raw: String): String {
        val n = normalise(raw)
        if (n.count { it == ',' } == 1) {
            val (a, b) = n.split(',').map { it.trim() }
            if (a.isNotEmpty() && b.isNotEmpty()) return if (b in NAME_SUFFIXES) a else "$b $a"
        }
        return n.replace(",", " ").replace(Regex("\\s+"), " ").trim()
    }
}

object LastNames {
    private val PARTICLES = setOf(
        "de", "del", "della", "di", "da", "dos", "das", "do", "du", "van", "von", "der", "den", "ter", "ten", "op", "het",
        "la", "le", "les", "el", "al", "bin", "ibn", "bint", "st", "saint", "mac", "af", "zu", "y", "e",
    )

    private fun isParticle(t: String) = PersonNames.fold(t) in PARTICLES
    private fun isSuffix(t: String) = PersonNames.fold(t).replace(".", "") in PersonNames.NAME_SUFFIXES

    private class Parts(val surname: List<String>, val before: String?, val fromComma: Boolean)

    private fun parts(raw: String): Parts? {
        var c = PersonNames.clean(raw)
        if (c.count { it == ',' } == 1) {
            val (a, b) = c.split(',').map { it.trim() }
            if (a.isNotEmpty() && b.isNotEmpty() && !isSuffix(b)) return Parts(a.split(' ').filter { it.isNotEmpty() }, null, true)
            c = a
        }
        c = c.replace(",", " ")
        val tokens = c.split(' ').filter { it.isNotEmpty() }.toMutableList()
        while (tokens.size > 1 && isSuffix(tokens.last())) tokens.removeAt(tokens.size - 1)
        if (tokens.size < 2) return null
        var idx = tokens.size - 1
        while (idx - 1 >= 1 && isParticle(tokens[idx - 1])) idx--
        val before = if (idx - 1 >= 1 && !isParticle(tokens[idx - 1])) tokens[idx - 1] else null
        return Parts(tokens.subList(idx, tokens.size), before, false)
    }

    /** The surname as written (case kept), for prefilling the setting; null for a single name. */
    fun suggestFor(raw: String): String? = parts(raw)?.surname?.joinToString(" ")?.takeIf { it.isNotBlank() }

    private fun forms(tokens: List<String>): MutableSet<String> {
        val out = LinkedHashSet<String>()
        if (tokens.isEmpty()) return out
        out += PersonNames.fold(tokens.joinToString(" "))
        val last = tokens.last()
        out += PersonNames.fold(last)
        if (last.contains('-')) last.split('-').forEach { out += PersonNames.fold(it) }
        return out
    }

    /** Every surname form a sender name could be read as (folded). Empty for a single name: it never matches, it never crashes. */
    fun candidates(raw: String): Set<String> {
        val p = parts(raw) ?: return emptySet()
        val out = forms(p.surname)
        if (p.fromComma) p.surname.filter { !isParticle(it) }.forEach { out += PersonNames.fold(it) }
        p.before?.let { out += PersonNames.fold(it + " " + p.surname.joinToString(" ")) }
        out.removeAll { it.isBlank() || it in PARTICLES }
        return out
    }

    /** The forms the user's own last name stands for: the whole, its last word, hyphen parts and each non-particle word. */
    fun mineForms(my: String): Set<String> {
        val tokens = PersonNames.clean(my).replace(",", " ").split(' ').filter { it.isNotEmpty() && !isSuffix(it) }
        val out = forms(tokens)
        if (tokens.size > 1) tokens.filter { !isParticle(it) }.forEach { out += PersonNames.fold(it) }
        out.removeAll { it.isBlank() || it in PARTICLES }
        return out
    }

    fun matches(senderRaw: String, myLastName: String): Boolean {
        if (myLastName.isBlank()) return false
        val s = candidates(senderRaw)
        if (s.isEmpty()) return false
        return mineForms(myLastName).any { it in s }
    }
}
