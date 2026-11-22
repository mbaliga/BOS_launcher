package com.bos.sphere.core.data

/**
 * Wave 55 (calmStyles): how the launcher shows signal and battery state. A handful of calm ambient styles, each drawn ON TOP of the wallpaper as an edge,
 * corner or line accent at a low alpha (never a fill, scrim, vignette or tint), plus [Regular], the ordinary small battery glyph with a percentage and the
 * signal bars in the Glance chrome. [None] shows nothing. Replaces the wave 35 pairs (Dot bloom, Animated dots, Coloured vignette, Comic-book lines).
 */
enum class CalmStyle(val label: String, val ambient: Boolean) {
    None("Off", false),
    Regular("Regular", false),
    EdgeDots("Edge dots", true),
    CornerBloom("Corner bloom", true),
    BottomGlow("Bottom glow", true),
    Beam("Edge beam", true),
}

/** The pure rules of the calm styles: the non-occlusion budget, what the default is for a wallpaper, what shows the regular indicators and the migrations. */
object CalmStyles {
    /** No calm style ever paints a pixel with more alpha than this (owner: the live wallpapers must never be interrupted). Intensity scales it down. */
    const val MAX_ALPHA = 0.35f

    /** The highest alpha a style may use at slider [intensity] 0..1. */
    fun alphaCap(intensity: Float): Float = MAX_ALPHA * (if (intensity.isNaN()) 0f else intensity.coerceIn(0f, 1f))

    /**
     * The style in force: the user's explicit choice, or, when none was ever made, the subtlest sensible default for the wallpaper: the Signal wallpaper
     * (made of the same kind of light) gets Edge dots; every other kind (Solid, Gradient, System live wallpaper, Picture, GIF, video) gets nothing, so
     * the user's own wallpaper is not touched until they ask.
     */
    fun resolve(stored: CalmStyle?, wallpaper: WallpaperKind): CalmStyle = stored ?: if (wallpaper == WallpaperKind.Live) CalmStyle.EdgeDots else CalmStyle.None

    /** The regular battery and signal indicators show for [CalmStyle.Regular], or beside an ambient style when [also] is on. */
    fun showsRegular(style: CalmStyle, also: Boolean): Boolean = style == CalmStyle.Regular || (also && style.ambient)

    /** The stored value of a choice; null (never chosen: automatic) is not stored at all. */
    fun parse(s: String?): CalmStyle? = if (s == null) null else CalmStyle.entries.firstOrNull { it.name == s }

    /**
     * The nearest style of a wave 35 pair of stored names (`indCalm`, `indAlert`, an enum name or "Off"): Dot bloom -> Corner bloom, Animated dots -> Edge dots;
     * with no calm effect an alert maps by its look (Coloured vignette -> Bottom glow, Comic-book lines -> Edge beam); both off -> Off. Any unknown name reads as nothing.
     */
    fun fromW35(calm: String?, alert: String?): CalmStyle = when (calm) {
        "Bloom" -> CalmStyle.CornerBloom
        "AnimatedDots" -> CalmStyle.EdgeDots
        else -> when (alert) { "Vignette" -> CalmStyle.BottomGlow; "ComicLines" -> CalmStyle.Beam; else -> CalmStyle.None }
    }
}

/**
 * The one-time migration of the pre-wave-35 effect set (`indEffects`, a comma list of the old IndicatorEffect enum names) to a [CalmStyle]: the first calm
 * effect found wins (DotsBreathe -> Edge dots, DotsBloomCentre / DotsBloomEdges -> Corner bloom), else the first alert (Vignette, ComicLines), else Off. Pure.
 */
object IndicatorMigration {
    /** [raw] is the stored comma list; null means the key was never written (nothing to migrate: the automatic default applies). */
    fun fromLegacy(raw: String?): CalmStyle? {
        if (raw == null) return null
        val names = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val calm = names.firstNotNullOfOrNull { when (it) { "DotsBreathe" -> "AnimatedDots"; "DotsBloomCentre", "DotsBloomEdges" -> "Bloom"; else -> null } }
        val alert = names.firstOrNull { it == "Vignette" || it == "ComicLines" }
        return CalmStyles.fromW35(calm, alert)
    }
}

/**
 * How bad the reading is for the alert effects. [level] 0 = healthy (nothing drawn), 1 = worst; [urgent] = the value is lost
 * (Signal: offline; Battery: at or below 5 percent and not charging): full strength, red, pulsing.
 */
data class IndicatorSeverity(val level: Float, val urgent: Boolean) {
    val active: Boolean get() = urgent || level > 0f

    companion object {
        val Healthy = IndicatorSeverity(0f, false)

        /** Battery: healthy from 60 percent. */
        const val BATTERY_HEALTHY = 0.6f
        /** Signal: 4 of 5 bars (level 3 of 0..4, "good") or better is healthy. */
        const val SIGNAL_HEALTHY = 0.75f
        const val BATTERY_URGENT = 0.05f

        fun healthyAt(metric: IndicatorMetric): Float = if (metric == IndicatorMetric.Battery) BATTERY_HEALTHY else SIGNAL_HEALTHY

        /** Severity of a plain value 0..1 (the Preview slider uses it too). Falls linearly from 0 at the healthy line to 1 at nothing. */
        fun ofValue(metric: IndicatorMetric, value: Float, charging: Boolean = false): IndicatorSeverity {
            val v = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)
            if (metric == IndicatorMetric.Battery && charging) return Healthy
            val line = healthyAt(metric)
            val urgent = metric == IndicatorMetric.Battery && v <= BATTERY_URGENT
            if (urgent) return IndicatorSeverity(1f, true)
            return if (v >= line) Healthy else IndicatorSeverity(((line - v) / line).coerceIn(0f, 1f), false)
        }

        /** Severity of a live reading. Airplane mode (Idle) is deliberate, so it is not an alarm; Off is no connectivity. */
        fun of(metric: IndicatorMetric, reading: IndicatorReading): IndicatorSeverity = when (reading.kind) {
            ReadingKind.Idle -> Healthy
            ReadingKind.Off -> if (metric == IndicatorMetric.Signal) IndicatorSeverity(1f, true) else Healthy
            ReadingKind.Live -> ofValue(metric, reading.value, reading.charging)
        }
    }
}

/** The Preview pane's simulated reading for a slider [level] 0..1: 0 is "lost" (Signal: offline), anything else a live value. Never touches real state. */
object IndicatorPreviewReading {
    fun of(metric: IndicatorMetric, level: Float): IndicatorReading {
        val v = level.coerceIn(0f, 1f)
        return if (metric == IndicatorMetric.Signal && v <= 0f) IndicatorReading(ReadingKind.Off, 0f, "Offline")
        else IndicatorReading(ReadingKind.Live, v, "${(v * 100).toInt()}%")
    }
}

/** What the regular indicators draw, from the two live readings (pure; the Compose side only paints it). */
object RegularIndicator {
    /** Signal is drawn as this many bars. */
    const val BARS = 4

    /** Filled bars for a signal reading: 0..[BARS]; no connectivity, airplane mode and unknown read as none. */
    fun bars(r: IndicatorReading): Int = if (r.kind != ReadingKind.Live || r.value.isNaN()) 0 else (r.value.coerceIn(0f, 1f) * BARS).let { Math.round(it) }

    /** Battery percentage 0..100, or null while the reading is not known. */
    fun percent(r: IndicatorReading): Int? = if (r.kind != ReadingKind.Live || r.value.isNaN()) null else Math.round(r.value.coerceIn(0f, 1f) * 100f)

    /** The glyph turns the low colour at or under this percent while not charging. */
    const val LOW_PERCENT = 15

    fun batteryLow(r: IndicatorReading): Boolean = (percent(r) ?: 100) <= LOW_PERCENT && !r.charging

    /** Signal states that need a mark besides the bars: no connectivity (a slash) and airplane mode (nothing drawn but dimmed bars). */
    fun signalLost(r: IndicatorReading): Boolean = r.kind == ReadingKind.Off
}
