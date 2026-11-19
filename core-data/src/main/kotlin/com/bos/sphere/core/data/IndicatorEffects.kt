package com.bos.sphere.core.data

/** Wave 35, the calm group: lit while the reading is healthy. At most one is on. */
enum class CalmEffect(val label: String) {
    Bloom("Dot bloom"),
    AnimatedDots("Animated dots"),
}

/** Wave 35, the alert group: the inverse of calm, invisible while healthy, flaring as the reading falls, urgent when it is lost. At most one is on. */
enum class AlertEffect(val label: String) {
    Vignette("Coloured vignette"),
    ComicLines("Comic-book lines"),
}

/** The two selections together; both may be null, so no state outside the allowed pairs exists. */
data class IndicatorSelection(val calm: CalmEffect?, val alert: AlertEffect?)

/**
 * Wave 35: the one-time migration of the pre-wave-35 effect set (`indEffects`, a comma list of the names of the old IndicatorEffect enum) to the two
 * selections. The first calm effect found wins (DotsBreathe -> AnimatedDots, DotsBloomCentre / DotsBloomEdges -> Bloom), then the first alert effect
 * (Vignette, ComicLines). Snake outline and Edge meter are gone and read as nothing. Pure.
 */
object IndicatorMigration {
    /** [raw] is the stored comma list; null means the key was never written (a fresh install keeps the default, Animated dots only). */
    fun fromLegacy(raw: String?): IndicatorSelection? {
        if (raw == null) return null
        val names = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val calm = names.firstNotNullOfOrNull {
            when (it) { "DotsBreathe" -> CalmEffect.AnimatedDots; "DotsBloomCentre", "DotsBloomEdges" -> CalmEffect.Bloom; else -> null }
        }
        val alert = names.firstNotNullOfOrNull {
            when (it) { "Vignette" -> AlertEffect.Vignette; "ComicLines" -> AlertEffect.ComicLines; else -> null }
        }
        return IndicatorSelection(calm, alert)
    }

    /** The stored value of a selection: the enum name, or [OFF]. */
    const val OFF = "Off"
    fun <E : Enum<E>> store(e: E?): String = e?.name ?: OFF
    inline fun <reified E : Enum<E>> parse(s: String?): E? = if (s == null) null else enumValues<E>().firstOrNull { it.name == s }
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
