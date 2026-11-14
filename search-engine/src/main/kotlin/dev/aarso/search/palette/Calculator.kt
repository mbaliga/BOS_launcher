package dev.aarso.search.palette

import kotlin.math.PI
import kotlin.math.E
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/** Result of an inline calculation (PAL-05): [text] is what is shown and what Enter copies. */
data class CalcResult(val expression: String, val text: String, val value: Double, val unit: String? = null)

/**
 * Inline calculator: + - * / ^, unary minus, parentheses, percent (`50%`, `200 + 10%`, `10% of 200`), sqrt and
 * pi/e, then unit conversion via [Units]. Pure; returns null for anything that is not a calculation, so ordinary
 * words and plain numbers fall through to the other providers.
 */
object Calculator {
    fun evaluate(input: String): CalcResult? {
        val q = input.trim()
        if (q.isEmpty() || q.length > 120) return null
        Units.convert(q)?.let { return it }
        return arithmetic(q)
    }

    private fun arithmetic(q: String): CalcResult? {
        val s = q.replace('×', '*').replace('÷', '/').replace('−', '-').replace(" x ", " * ").trimEnd('=').trim()
        if (!s.any { it.isDigit() } || s.any { !(it.isLetterOrDigit() || it in " +-*/^().,%") }) return null
        val p = Parser(s)
        val v = try { p.parseAll() } catch (_: CalcError) { return null }
        if (v.isNaN() || v.isInfinite()) return null
        // A bare number or a bare percent is not worth an answer row.
        if (!p.sawOperation) return null
        return CalcResult(q, format(v), v)
    }

    internal class CalcError : RuntimeException()

    private class Parser(val s: String) {
        var i = 0
        var sawOperation = false

        fun parseAll(): Double { val v = expr(); skip(); if (i != s.length) throw CalcError(); return v }

        private fun skip() { while (i < s.length && s[i] == ' ') i++ }
        private fun peek(): Char { skip(); return if (i < s.length) s[i] else '\u0000' }

        // expr := term (('+'|'-') term)* ; "x + n%" means x + x*n/100
        private fun expr(): Double {
            var v = term()
            while (true) {
                val c = peek()
                if (c != '+' && c != '-') return v
                i++; sawOperation = true
                val (r, isPercent) = termWithPercent()
                val add = if (isPercent) v * r else r
                v = if (c == '+') v + add else v - add
            }
        }

        private fun termWithPercent(): Pair<Double, Boolean> {
            val start = i
            val v = term()
            val text = s.substring(start, i).trim()
            val pure = text.endsWith("%") && text.dropLast(1).trim().let { t -> t.isNotEmpty() && t.all { it.isDigit() || it == '.' } }
            return v to pure
        }

        private fun term(): Double {
            var v = unary()
            while (true) {
                val c = peek()
                when {
                    c == '*' -> { i++; sawOperation = true; v *= unary() }
                    c == '/' -> { i++; sawOperation = true; val d = unary(); if (d == 0.0) throw CalcError(); v /= d }
                    c == 'o' && s.startsWith("of", i) -> { i += 2; sawOperation = true; v *= unary() }
                    else -> return v
                }
            }
        }

        private fun unary(): Double {
            val c = peek()
            if (c == '-') { i++; return -unary() }
            if (c == '+') { i++; return unary() }
            return power()
        }

        private fun power(): Double {
            val base = postfix()
            if (peek() == '^') { i++; sawOperation = true; return base.pow(unary()) }
            return base
        }

        private fun postfix(): Double {
            var v = atom()
            while (peek() == '%') { i++; sawOperation = true; v /= 100.0 }
            return v
        }

        private fun atom(): Double {
            val c = peek()
            if (c == '(') {
                i++
                val v = expr()
                if (peek() != ')') throw CalcError()
                i++
                return v
            }
            if (c.isDigit() || c == '.') {
                val st = i
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                // Thousands separators ("1,000") are not accepted: the comma is a decimal mark in many locales.
                return s.substring(st, i).toDoubleOrNull() ?: throw CalcError()
            }
            if (c.isLetter()) {
                val st = i
                while (i < s.length && s[i].isLetter()) i++
                return when (s.substring(st, i).lowercase()) {
                    "pi" -> PI
                    "e" -> E
                    "sqrt" -> { sawOperation = true; if (peek() != '(') throw CalcError(); i++; val v = expr(); if (peek() != ')') throw CalcError(); i++; if (v < 0) throw CalcError(); sqrt(v) }
                    else -> throw CalcError()
                }
            }
            throw CalcError()
        }
    }

    /** Up to 10 significant digits, no trailing zeros, no exponent unless the number is huge or tiny. */
    fun format(v: Double): String {
        if (v == floor(v) && abs(v) < 1e15) return v.toLong().toString()
        if (abs(v) >= 1e15 || abs(v) < 1e-9) return String.format(java.util.Locale.ROOT, "%.6e", v)
        val digits = 10
        val mag = floor(kotlin.math.log10(abs(v))).toInt()
        val decimals = (digits - 1 - mag).coerceIn(0, 12)
        return String.format(java.util.Locale.ROOT, "%.${decimals}f", v).trimEnd('0').trimEnd('.')
    }
}

/** Common unit conversions: length, mass, temperature, volume, speed, data. `5 km to mi`, `100 f in c`. */
object Units {
    private enum class Kind { Length, Mass, Volume, Speed, Data, Temperature }

    private class U(val kind: Kind, val toBase: Double, val label: String)

    private val table = HashMap<String, U>().apply {
        fun add(kind: Kind, f: Double, label: String, vararg names: String) { val u = U(kind, f, label); names.forEach { put(it, u) } }
        // length (base metre)
        add(Kind.Length, 0.001, "mm", "mm", "millimeter", "millimeters", "millimetre", "millimetres")
        add(Kind.Length, 0.01, "cm", "cm", "centimeter", "centimeters", "centimetre", "centimetres")
        add(Kind.Length, 1.0, "m", "m", "meter", "meters", "metre", "metres")
        add(Kind.Length, 1000.0, "km", "km", "kilometer", "kilometers", "kilometre", "kilometres")
        add(Kind.Length, 0.0254, "in", "in", "inch", "inches")
        add(Kind.Length, 0.3048, "ft", "ft", "foot", "feet")
        add(Kind.Length, 0.9144, "yd", "yd", "yard", "yards")
        add(Kind.Length, 1609.344, "mi", "mi", "mile", "miles")
        add(Kind.Length, 1852.0, "nmi", "nmi", "nm")
        // mass (base gram)
        add(Kind.Mass, 0.001, "mg", "mg", "milligram", "milligrams")
        add(Kind.Mass, 1.0, "g", "g", "gram", "grams")
        add(Kind.Mass, 1000.0, "kg", "kg", "kilogram", "kilograms", "kilo", "kilos")
        add(Kind.Mass, 1_000_000.0, "t", "t", "tonne", "tonnes")
        add(Kind.Mass, 28.349523125, "oz", "oz", "ounce", "ounces")
        add(Kind.Mass, 453.59237, "lb", "lb", "lbs", "pound", "pounds")
        add(Kind.Mass, 6350.29318, "st", "st", "stone")
        // volume (base litre; US customary)
        add(Kind.Volume, 0.001, "ml", "ml", "milliliter", "milliliters", "millilitre", "millilitres")
        add(Kind.Volume, 1.0, "l", "l", "liter", "liters", "litre", "litres")
        add(Kind.Volume, 3.785411784, "gal", "gal", "gallon", "gallons")
        add(Kind.Volume, 0.946352946, "qt", "qt", "quart", "quarts")
        add(Kind.Volume, 0.473176473, "pt", "pt", "pint", "pints")
        add(Kind.Volume, 0.2365882365, "cup", "cup", "cups")
        add(Kind.Volume, 0.0295735295625, "fl oz", "floz", "fl-oz")
        add(Kind.Volume, 0.01478676478125, "tbsp", "tbsp", "tablespoon", "tablespoons")
        add(Kind.Volume, 0.00492892159375, "tsp", "tsp", "teaspoon", "teaspoons")
        // speed (base m/s)
        add(Kind.Speed, 1.0, "m/s", "m/s", "mps")
        add(Kind.Speed, 1000.0 / 3600.0, "km/h", "km/h", "kmh", "kph", "kmph")
        add(Kind.Speed, 0.44704, "mph", "mph")
        add(Kind.Speed, 0.514444444, "kn", "kn", "kt", "knot", "knots")
        add(Kind.Speed, 0.3048, "ft/s", "ft/s", "fps")
        // data (base byte): kB/MB/GB/TB are decimal (1000), KiB/MiB/GiB/TiB binary (1024), as storage vendors label them
        add(Kind.Data, 0.125, "bit", "bit", "bits")
        add(Kind.Data, 1.0, "B", "b", "byte", "bytes")
        add(Kind.Data, 1e3, "kB", "kb", "kilobyte", "kilobytes")
        add(Kind.Data, 1e6, "MB", "mb", "megabyte", "megabytes")
        add(Kind.Data, 1e9, "GB", "gb", "gigabyte", "gigabytes")
        add(Kind.Data, 1e12, "TB", "tb", "terabyte", "terabytes")
        add(Kind.Data, 1024.0, "KiB", "kib")
        add(Kind.Data, 1048576.0, "MiB", "mib")
        add(Kind.Data, 1073741824.0, "GiB", "gib")
        add(Kind.Data, 1099511627776.0, "TiB", "tib")
        // temperature handled by name, factor unused
        for (n in listOf("c", "°c", "celsius", "centigrade")) put(n, U(Kind.Temperature, 0.0, "°C"))
        for (n in listOf("f", "°f", "fahrenheit")) put(n, U(Kind.Temperature, 1.0, "°F"))
        for (n in listOf("k", "kelvin")) put(n, U(Kind.Temperature, 2.0, "K"))
    }

    private val pattern = Regex("^(-?[0-9]*\\.?[0-9]+)\\s*([a-z°/ -]+?)\\s+(?:to|in|into|as|->)\\s+([a-z°/ -]+)$", RegexOption.IGNORE_CASE)

    fun convert(input: String): CalcResult? {
        val m = pattern.matchEntire(input.trim()) ?: return null
        val n = m.groupValues[1].toDoubleOrNull() ?: return null
        val from = table[m.groupValues[2].trim().lowercase()] ?: return null
        val to = table[m.groupValues[3].trim().lowercase()] ?: return null
        if (from.kind != to.kind) return null
        val out = if (from.kind == Kind.Temperature) temp(n, from.toBase.toInt(), to.toBase.toInt()) else n * from.toBase / to.toBase
        val text = Calculator.format(out) + (if (to.kind == Kind.Temperature) "" else " ") + to.label
        return CalcResult(input.trim(), text, out, to.label)
    }

    private fun temp(v: Double, from: Int, to: Int): Double {
        val c = when (from) { 0 -> v; 1 -> (v - 32) * 5 / 9; else -> v - 273.15 }
        return when (to) { 0 -> c; 1 -> c * 9 / 5 + 32; else -> c + 273.15 }
    }
}

/** Calculator as a provider (PAL-05): one pinned row whose primary action copies the result. */
class CalculatorProvider(private val strings: PaletteStrings = PaletteStrings()) : PaletteProvider {
    override val id = "calculator"
    override val scope = PaletteScope.Everything

    override suspend fun query(q: PaletteQuery, limit: Int): List<PaletteResult> {
        val r = Calculator.evaluate(q.text) ?: return emptyList()
        return listOf(
            PaletteResult(
                id = "calc:${r.expression}", provider = id, scope = scope, title = r.text,
                subtitle = r.expression + " · " + strings.calculatorHint,
                score = 10.0, actions = listOf(ResultAction(ResultActionId.Copy)), copyText = r.text, learn = false,
            ),
        )
    }
}
