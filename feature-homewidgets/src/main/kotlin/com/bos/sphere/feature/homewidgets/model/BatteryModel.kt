package com.bos.sphere.feature.homewidgets.model

/** Where power comes from, from `BatteryManager.EXTRA_PLUGGED`. */
enum class PlugSource { NONE, AC, USB, WIRELESS, DOCK }

/**
 * The battery's state as the widget names it. Each state has its own glyph and word; no state is
 * told by colour.
 */
enum class BatteryState {
    /** Charging now. */
    CHARGING,

    /** Plugged in and full. */
    FULL,

    /** Plugged in but not charging (charging paused or limited by the system). */
    PLUGGED,

    /** Running on battery. */
    ON_BATTERY,

    /** Running on battery and the system calls the level low. */
    LOW,
}

/** One reading of the battery, taken at [at] (epoch ms). */
data class BatteryReading(
    val percent: Int,
    val state: BatteryState,
    val plug: PlugSource,
    val temperatureC: Float?,
    val minutesToFull: Int?,
    val at: Long,
)

object BatteryModel {
    // BatteryManager.BATTERY_STATUS_* and BATTERY_PLUGGED_* values (stable platform constants),
    // repeated here so this model stays plain JVM code.
    const val STATUS_CHARGING = 2
    const val STATUS_DISCHARGING = 3
    const val STATUS_NOT_CHARGING = 4
    const val STATUS_FULL = 5
    const val PLUGGED_AC = 1
    const val PLUGGED_USB = 2
    const val PLUGGED_WIRELESS = 4
    const val PLUGGED_DOCK = 8

    /** Below this the level counts as low when the system doesn't say (Android's default warning level). */
    const val LOW_PERCENT = 15

    /** While nothing the widget shows changes, it is still redrawn this often (the "as of" time). */
    const val REFRESH_AFTER_MS = 15 * 60_000L

    fun percent(level: Int, scale: Int): Int? =
        if (level < 0 || scale <= 0) null else (level * 100 / scale).coerceIn(0, 100)

    fun plug(plugged: Int): PlugSource = when {
        plugged and PLUGGED_AC != 0 -> PlugSource.AC
        plugged and PLUGGED_USB != 0 -> PlugSource.USB
        plugged and PLUGGED_WIRELESS != 0 -> PlugSource.WIRELESS
        plugged and PLUGGED_DOCK != 0 -> PlugSource.DOCK
        else -> PlugSource.NONE
    }

    /** [systemLow]: `BatteryManager.EXTRA_BATTERY_LOW` when the broadcast carries it. */
    fun state(percent: Int, status: Int, plug: PlugSource, systemLow: Boolean?): BatteryState {
        val plugged = plug != PlugSource.NONE
        return when {
            status == STATUS_CHARGING -> BatteryState.CHARGING
            plugged && (status == STATUS_FULL || percent >= 100) -> BatteryState.FULL
            plugged -> BatteryState.PLUGGED
            systemLow ?: (percent <= LOW_PERCENT) -> BatteryState.LOW
            else -> BatteryState.ON_BATTERY
        }
    }

    /** `BatteryManager.computeChargeTimeRemaining()` as whole minutes, only while charging (it returns -1 when unknown). */
    fun minutesToFull(remainingMs: Long, state: BatteryState): Int? =
        if (state != BatteryState.CHARGING || remainingMs <= 0) null else ((remainingMs + 59_999L) / 60_000L).toInt()

    fun reading(
        level: Int,
        scale: Int,
        status: Int,
        plugged: Int,
        temperatureTenths: Int?,
        systemLow: Boolean?,
        chargeRemainingMs: Long,
        at: Long,
    ): BatteryReading? {
        val pct = percent(level, scale) ?: return null
        val plug = plug(plugged)
        val state = state(pct, status, plug, systemLow)
        // EXTRA_TEMPERATURE is tenths of a degree Celsius; 0 or absurd values mean "not reported".
        val temp = temperatureTenths?.takeIf { it in -400..1000 && it != 0 }?.let { it / 10f }
        return BatteryReading(pct, state, plug, temp, minutesToFull(chargeRemainingMs, state), at)
    }

    /** What a redraw depends on. A broadcast that changes only voltage or temperature leaves it unchanged. */
    fun redrawKey(r: BatteryReading?): String = if (r == null) "none" else "${r.percent}|${r.state}|${r.plug}"

    /**
     * Whether a new reading should redraw the widgets: when something they show changed, or the
     * last redraw is old enough that its "as of" time understates how current the value is.
     */
    fun shouldRedraw(lastKey: String?, lastAt: Long, next: BatteryReading?, now: Long): Boolean =
        lastKey != redrawKey(next) || now - lastAt >= REFRESH_AFTER_MS || now < lastAt

    /** [minutes] as hours and minutes, for "full in about 1 h 10 min". */
    fun hoursAndMinutes(minutes: Int): Pair<Int, Int> = (minutes / 60) to (minutes % 60)
}
