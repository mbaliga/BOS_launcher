package com.bos.sphere.feature.pages

/**
 * A small, strict JSON reader and writer with no dependencies, so the page list, widget manifests
 * and host-API messages are handled by pure code that runs in JVM unit tests (`org.json` is only a
 * stub there). Values map to Kotlin as: object → `Map<String, Any?>` (insertion order kept),
 * array → `List<Any?>`, string → `String`, integral number → `Long`, other number → `Double`,
 * `true`/`false` → `Boolean`, `null` → `null`.
 */
object MiniJson {

    class ParseException(message: String) : IllegalArgumentException(message)

    /** Nesting deeper than this is refused (a widget message can't exhaust the stack). */
    const val MAX_DEPTH = 64

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val value = p.value(0)
        p.skipWs()
        if (!p.atEnd()) throw ParseException("unexpected content at ${p.pos}")
        return value
    }

    /** [parse], or null when [text] is not valid JSON. */
    fun parseOrNull(text: String?): Any? =
        if (text == null) null else runCatching { parse(text) }.getOrNull()

    fun write(value: Any?): String = StringBuilder().also { writeTo(it, value, 0) }.toString()

    private fun writeTo(sb: StringBuilder, value: Any?, depth: Int) {
        require(depth <= MAX_DEPTH) { "nesting too deep" }
        when (value) {
            null -> sb.append("null")
            is String -> quote(sb, value)
            is Boolean -> sb.append(if (value) "true" else "false")
            is Int, is Long, is Short, is Byte -> sb.append(value.toString())
            is Double -> sb.append(if (value.isFinite()) number(value) else "null")
            is Float -> sb.append(if (value.isFinite()) number(value.toDouble()) else "null")
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    quote(sb, k.toString())
                    sb.append(':')
                    writeTo(sb, v, depth + 1)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (v in value) {
                    if (!first) sb.append(',')
                    first = false
                    writeTo(sb, v, depth + 1)
                }
                sb.append(']')
            }
            is Array<*> -> writeTo(sb, value.asList(), depth)
            else -> quote(sb, value.toString())
        }
    }

    private fun number(d: Double): String =
        if (d == Math.rint(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString()

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                // U+2028/2029 are valid JSON but end a line in older JavaScript; escape them too.
                ' ' -> sb.append("\\u2028")
                ' ' -> sb.append("\\u2029")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var pos = 0

        fun atEnd() = pos >= s.length

        fun skipWs() {
            while (pos < s.length && (s[pos] == ' ' || s[pos] == '\n' || s[pos] == '\r' || s[pos] == '\t')) pos++
        }

        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw ParseException("nesting too deep")
            if (atEnd()) throw ParseException("unexpected end")
            return when (val c = s[pos]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c in '0'..'9') num() else throw ParseException("unexpected '$c' at $pos")
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, pos)) throw ParseException("bad literal at $pos")
            pos += word.length
            return v
        }

        private fun obj(depth: Int): Map<String, Any?> {
            pos++ // {
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (peek() == '}') { pos++; return out }
            while (true) {
                skipWs()
                if (peek() != '"') throw ParseException("expected a key at $pos")
                val key = str()
                skipWs()
                if (peek() != ':') throw ParseException("expected ':' at $pos")
                pos++
                skipWs()
                out[key] = value(depth + 1)
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    '}' -> { pos++; return out }
                    else -> throw ParseException("expected ',' or '}' at $pos")
                }
            }
        }

        private fun arr(depth: Int): List<Any?> {
            pos++ // [
            val out = ArrayList<Any?>()
            skipWs()
            if (peek() == ']') { pos++; return out }
            while (true) {
                skipWs()
                out += value(depth + 1)
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    ']' -> { pos++; return out }
                    else -> throw ParseException("expected ',' or ']' at $pos")
                }
            }
        }

        private fun str(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw ParseException("unterminated string")
                val c = s[pos++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (atEnd()) throw ParseException("bad escape")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw ParseException("bad unicode escape")
                                val hex = s.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16) ?: throw ParseException("bad unicode escape")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw ParseException("bad escape '\\$e'")
                        }
                    }
                    c < ' ' -> throw ParseException("control character in string")
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = pos
            if (peek() == '-') pos++
            if (peek() == '0') {
                pos++
            } else {
                if (peek() !in '1'..'9') throw ParseException("bad number at $start")
                while (peek() in '0'..'9') pos++
            }
            var integral = true
            if (peek() == '.') {
                integral = false
                pos++
                if (peek() !in '0'..'9') throw ParseException("bad number at $start")
                while (peek() in '0'..'9') pos++
            }
            if (peek() == 'e' || peek() == 'E') {
                integral = false
                pos++
                if (peek() == '+' || peek() == '-') pos++
                if (peek() !in '0'..'9') throw ParseException("bad number at $start")
                while (peek() in '0'..'9') pos++
            }
            val text = s.substring(start, pos)
            if (integral) text.toLongOrNull()?.let { return it }
            return text.toDouble()
        }

        private fun peek(): Char = if (pos < s.length) s[pos] else '\u0000'
    }
}

/** Typed reads from a parsed JSON object (`Map<String, Any?>`). */
internal fun Map<String, Any?>.str(key: String): String? = this[key] as? String
internal fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Long) ?: (this[key] as? Double)?.takeIf { it == Math.rint(it) }?.toLong()
internal fun Map<String, Any?>.bool(key: String): Boolean? = this[key] as? Boolean

@Suppress("UNCHECKED_CAST")
internal fun Any?.asObject(): Map<String, Any?>? = this as? Map<String, Any?>
