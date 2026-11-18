package com.bos.sphere.core.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Wave 28 (commsLayout): the size of the Comms content, 75 to 125 percent in detents of 5 (eleven steps), 100 by default. It replaces the
 * three-level row density of wave 25. Pure stepping rules, no Android types, tested on the JVM.
 */
object CommsScale {
    const val MIN = 75
    const val MAX = 125
    const val STEP = 5
    const val DEFAULT = 100

    /** 75, 80, ..., 125. */
    val steps: List<Int> = (MIN..MAX step STEP).toList()

    /** Nearest detent, clamped to the range; halves round up. */
    fun snap(percent: Int): Int = (((percent - MIN).toFloat() / STEP).let { Math.floor(it + 0.5).toInt() } * STEP + MIN).coerceIn(MIN, MAX)

    /** One detent up (+1) or down (-1) from [percent]; stops at the ends. */
    fun step(percent: Int, delta: Int): Int = (snap(percent) + delta * STEP).coerceIn(MIN, MAX)

    /** Stored text (any garbage, null, out of range, off a detent) to a valid percent: garbage is the default, numbers snap. */
    fun parse(raw: String?): Int {
        val v = raw?.trim()?.removeSuffix("%")?.trim()?.toDoubleOrNull() ?: return DEFAULT
        if (v.isNaN() || v.isInfinite()) return DEFAULT
        return snap(v.roundToInt())
    }

    /** The multiplier for dp and sp. */
    fun factor(percent: Int): Float = snap(percent) / 100f

    /** The detent a pinch lands on: the percent the pinch started at, times the finger spread ratio. */
    fun fromPinch(startPercent: Int, ratio: Float): Int =
        if (ratio.isNaN() || ratio <= 0f) snap(startPercent) else snap((startPercent * ratio).roundToInt())
}

/**
 * The persisted [CommsScale] percent (file "comms_scale", key "pct", read through [TolerantPrefs]; exported with the other launcher
 * settings, see [PrefFilesSource.ALLOWED]). A process-wide store: the Comms surfaces, the menu panel, Ground Control and the pinch all use it.
 */
object CommsScaleSettings {
    const val FILE = "comms_scale"
    const val KEY = "pct"
    private val _percent = MutableStateFlow(CommsScale.DEFAULT)
    val percent: StateFlow<Int> = _percent.asStateFlow()
    @Volatile private var sp: SharedPreferences? = null
    /** A size chosen before the file was opened: kept and written when [init] opens it. */
    @Volatile private var pending: Int? = null

    /** Opens the file once (idempotent); later calls keep the first context's file. */
    fun init(context: Context) {
        if (sp != null) return
        synchronized(this) {
            if (sp != null) return
            val p = TolerantPrefs.of(context.applicationContext, FILE)
            sp = p
            val chosen = pending
            if (chosen != null) { pending = null; _percent.value = chosen; p.edit().putInt(KEY, chosen).apply() }
            else _percent.value = CommsScale.parse(runCatching { p.all[KEY]?.toString() }.getOrNull())
        }
    }

    fun set(next: Int) {
        val v = CommsScale.snap(next)
        _percent.value = v
        val file = sp
        if (file == null) pending = v else file.edit().putInt(KEY, v).apply()
    }

    /** Tests: drop the file binding and go back to the default. */
    fun resetForTest() { sp = null; pending = null; _percent.value = CommsScale.DEFAULT }
}
