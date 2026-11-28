package com.bos.sphere.core.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wave 27 (soundPopup): how strong the launcher's OWN haptic feedback is. Android's system vibration intensity cannot be written
 * without WRITE_SETTINGS (which the launcher does not request), so the Vibration slider of the Sound and Vibration sheet controls this
 * instead, and the sheet labels the row "Launcher vibration". Every haptic the launcher performs goes through [HapticPolicy]
 * (docs/decisions/soundPopup.md).
 */
enum class HapticStrength(val id: String) {
    Off("off"), Light("light"), Medium("medium"), Strong("strong");

    companion object {
        /** Medium keeps the behaviour of every earlier build. */
        val DEFAULT = Medium
        fun forId(id: String?): HapticStrength = entries.firstOrNull { it.id == id } ?: DEFAULT
        fun forStep(step: Int): HapticStrength = entries[step.coerceIn(0, entries.lastIndex)]
    }
}

/** The two kinds of haptic the launcher performs: a tick while sliding over targets, and a long press confirmation. */
enum class HapticKind { Tick, LongPress }

/** Pure mapping from a strength and the kind a call site asks for to the kind actually performed (null = nothing). */
object HapticPolicy {
    fun resolve(strength: HapticStrength, kind: HapticKind): HapticKind? = when (strength) {
        HapticStrength.Off -> null
        HapticStrength.Light -> if (kind == HapticKind.Tick) HapticKind.Tick else null
        HapticStrength.Medium -> kind
        HapticStrength.Strong -> HapticKind.LongPress // the firmer effect for every event
    }
}

/**
 * The persisted [HapticStrength] (file "launcher_haptics", key "strength", read through [TolerantPrefs]; exported with the other
 * launcher settings, see [PrefFilesSource.ALLOWED]). A process-wide store because haptics are performed from many surfaces.
 */
object LauncherHapticSettings {
    const val FILE = "launcher_haptics"
    const val KEY = "strength"
    private val _strength = MutableStateFlow(HapticStrength.DEFAULT)
    val strength: StateFlow<HapticStrength> = _strength.asStateFlow()
    @Volatile private var sp: SharedPreferences? = null

    /** Opens the file once (idempotent); later calls keep the first context's file. */
    fun init(context: Context) {
        if (sp != null) return
        synchronized(this) {
            if (sp != null) return
            val p = TolerantPrefs.of(context.applicationContext, FILE)
            sp = p
            _strength.value = HapticStrength.forId(p.getString(KEY, null))
        }
    }

    fun set(next: HapticStrength) {
        _strength.value = next
        sp?.edit()?.putString(KEY, next.id)?.apply()
    }

    /** Tests: drop the file binding and go back to the default. */
    fun resetForTest() { sp = null; _strength.value = HapticStrength.DEFAULT }
}
