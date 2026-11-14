package com.bos.sphere.core.catalog

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * HOM-03: icon decisions as pure functions. Which source wins for an item, how an icon pack's
 * appfilter.xml maps components to drawables, the plate colour for legacy (non-adaptive) icons so
 * no dark tile shows behind them, and the colours of a themed (monochrome) icon.
 */

/** component ("pkg/cls", as in ComponentName.flattenToString()) -> drawable name inside the pack. */
class IconPackMap(val packageName: String, private val entries: Map<String, String>) {
    val size: Int get() = entries.size
    fun drawableFor(component: String): String? = entries[component]
    fun drawableFor(packageName: String, className: String): String? = entries["$packageName/$className"]

    companion object {
        private val ITEM = Regex("<item\\b[^>]*>", RegexOption.IGNORE_CASE)
        private val COMPONENT = Regex("component\\s*=\\s*\"ComponentInfo\\{([^}/\"]+)/([^}\"]+)}\"", RegexOption.IGNORE_CASE)
        private val DRAWABLE = Regex("drawable\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)

        /**
         * Parses an ADW / Nova style appfilter.xml (`<item component="ComponentInfo{pkg/cls}" drawable="name"/>`).
         * Entries that are not component mappings (calendar, scale, iconback...) are ignored; the
         * first mapping for a component wins. Class names starting with a dot are made absolute.
         */
        fun parse(packageName: String, xml: String): IconPackMap {
            val out = LinkedHashMap<String, String>()
            for (m in ITEM.findAll(xml)) {
                val tag = m.value
                val c = COMPONENT.find(tag) ?: continue
                val d = DRAWABLE.find(tag) ?: continue
                val pkg = c.groupValues[1].trim()
                var cls = c.groupValues[2].trim()
                if (cls.startsWith(".")) cls = pkg + cls
                out.putIfAbsent("$pkg/$cls", d.groupValues[1].trim())
            }
            return IconPackMap(packageName, out)
        }
    }
}

sealed interface IconSource {
    /** The user picked a drawable from [packName] for this one item. */
    data class Override(val spec: String) : IconSource
    data class Pack(val packName: String, val drawable: String) : IconSource
    /** Monochrome layer of an adaptive icon, tinted to the theme. */
    data object Themed : IconSource
    data object Adaptive : IconSource
    data object Legacy : IconSource
}

object IconPolicy {
    /**
     * Order of precedence: per-item override, then the active icon pack, then the themed
     * monochrome layer (only when enabled and the app ships one), then the app's own icon.
     */
    fun resolve(
        itemKey: String,
        component: String,
        overrides: Map<String, String>,
        pack: IconPackMap?,
        themed: Boolean,
        hasMonochrome: Boolean,
        isAdaptive: Boolean,
    ): IconSource {
        overrides[itemKey]?.let { return IconSource.Override(it) }
        pack?.drawableFor(component)?.let { return IconSource.Pack(pack.packageName, it) }
        if (themed && hasMonochrome) return IconSource.Themed
        return if (isAdaptive) IconSource.Adaptive else IconSource.Legacy
    }
}

/** Per-item icon overrides, one line per item: key TAB spec. */
object IconOverrideCodec {
    fun encode(m: Map<String, String>): String = m.entries.sortedBy { it.key }.joinToString("\n") { "${it.key.replace("\t", " ")}\t${it.value.replace("\n", " ")}" }
    fun decode(raw: String): Map<String, String> =
        raw.lineSequence().filter { '\t' in it }.associate { l -> l.substringBefore('\t') to l.substringAfter('\t') }
}

object IconPlate {

    private fun lum(argb: Int): Float {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = ((a shr s and 0xFF) * (1 - t) + (b shr s and 0xFF) * t).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * The tile behind a legacy icon, derived from the icon's own average colour so it reads as part
     * of the icon. It is never a dark neutral: a dark icon gets a light tint of its hue, a light icon
     * a slightly deeper one that stays above luminance [MIN_LUMINANCE].
     */
    fun forLegacy(averageArgb: Int): Int {
        val l = lum(averageArgb)
        val plate = if (l < 0.62f) mix(averageArgb, 0xFFFFFFFF.toInt(), 0.86f) else mix(averageArgb, 0xFF000000.toInt(), 0.12f)
        return if (lum(plate) < MIN_LUMINANCE) mix(plate, 0xFFFFFFFF.toInt(), 0.5f) else plate
    }

    const val MIN_LUMINANCE = 0.45f

    /** Foreground / background for a themed monochrome icon from the accent. */
    fun themed(accentArgb: Int, darkTheme: Boolean): Pair<Int, Int> {
        val bg = if (darkTheme) mix(accentArgb, 0xFF000000.toInt(), 0.78f) else mix(accentArgb, 0xFFFFFFFF.toInt(), 0.82f)
        val fg = if (darkTheme) mix(accentArgb, 0xFFFFFFFF.toInt(), 0.78f) else mix(accentArgb, 0xFF000000.toInt(), 0.6f)
        return fg to bg
    }

    /** WCAG-style contrast between two opaque colours. */
    fun contrast(a: Int, b: Int): Float {
        fun rl(c: Int): Float {
            fun f(v: Int): Float { val s = v / 255f; return if (s <= 0.03928f) s / 12.92f else Math.pow(((s + 0.055f) / 1.055f).toDouble(), 2.4).toFloat() }
            return 0.2126f * f(c shr 16 and 0xFF) + 0.7152f * f(c shr 8 and 0xFF) + 0.0722f * f(c and 0xFF)
        }
        val x = rl(a); val y = rl(b)
        return (max(x, y) + 0.05f) / (min(x, y) + 0.05f)
    }
}
