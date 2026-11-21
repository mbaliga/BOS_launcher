package com.bos.sphere.ui

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import com.bos.sphere.core.data.HapticStrength
import com.bos.sphere.core.data.LauncherHapticSettings
import kotlin.math.roundToInt

/** The four rows of the Sound and Vibration sheet, top to bottom. */
enum class SoundRowKind(val english: String, val stream: Int?) {
    Media("Music, Video and Games", AudioManager.STREAM_MUSIC),
    Notifications("Notifications", AudioManager.STREAM_NOTIFICATION),
    Alarms("Alarms", AudioManager.STREAM_ALARM),
    Vibration("Vibration", null),
}

/** Reads and writes the three audio stream volumes. The real one is [AndroidVolumeController]; tests fake it. */
interface VolumeController {
    fun volume(kind: SoundRowKind): Int
    fun max(kind: SoundRowKind): Int
    /** Sets the volume without the system panel or a sound. False when the system refused (Do Not Disturb policy access). */
    fun setVolume(kind: SoundRowKind, value: Int): Boolean
    /** True when the stream cannot be changed right now (Do Not Disturb is on and the app has no policy access). */
    fun isLocked(kind: SoundRowKind): Boolean = false
}

/** The launcher's own haptic strength as the Vibration row sees it (steps 0 to 3: Off, Light, Medium, Strong). */
interface HapticControl {
    fun get(): HapticStrength
    fun set(value: HapticStrength)
}

object LauncherHapticControl : HapticControl {
    override fun get() = LauncherHapticSettings.strength.value
    override fun set(value: HapticStrength) = LauncherHapticSettings.set(value)
}

/** One row as drawn: the value on a 0..max scale, whether the user can change it, and why not. */
data class SoundRow(val kind: SoundRowKind, val value: Int, val max: Int, val enabled: Boolean = true, val reason: String? = null) {
    val fraction: Float get() = if (max <= 0) 0f else (value.toFloat() / max).coerceIn(0f, 1f)
    val percent: Int get() = (fraction * 100).roundToInt()
}

/**
 * Wave 27: the immutable state of the Sound and Vibration sheet. Every change goes through a [VolumeController] / [HapticControl] and
 * the result is read back, so what is shown is what the system has. A refused change (SecurityException under Do Not Disturb) turns the
 * row into a disabled one that says why instead of crashing.
 */
data class SoundVibrationState(val rows: List<SoundRow>) {
    fun row(kind: SoundRowKind): SoundRow = rows.first { it.kind == kind }

    /** The state after asking for [requested] on [kind]: clamped to 0..max, applied, read back. */
    fun set(kind: SoundRowKind, requested: Int, volumes: VolumeController, haptics: HapticControl): SoundVibrationState {
        val r = row(kind)
        if (!r.enabled) return this
        val v = requested.coerceIn(0, r.max)
        if (kind == SoundRowKind.Vibration) { haptics.set(HapticStrength.forStep(v)); return read(volumes, haptics) }
        val ok = try { volumes.setVolume(kind, v) } catch (_: SecurityException) { false }
        return if (!ok) read(volumes, haptics, refused = setOf(kind)) else read(volumes, haptics)
    }

    fun step(kind: SoundRowKind, delta: Int, volumes: VolumeController, haptics: HapticControl) = set(kind, row(kind).value + delta, volumes, haptics)

    companion object {
        const val DND_REASON = "Do Not Disturb is on"
        fun read(volumes: VolumeController, haptics: HapticControl, refused: Set<SoundRowKind> = emptySet()): SoundVibrationState = SoundVibrationState(
            SoundRowKind.entries.map { k ->
                if (k == SoundRowKind.Vibration) SoundRow(k, haptics.get().ordinal, HapticStrength.entries.lastIndex)
                else {
                    val max = runCatching { volumes.max(k) }.getOrDefault(0).coerceAtLeast(0)
                    val v = runCatching { volumes.volume(k) }.getOrDefault(0).coerceIn(0, max)
                    val locked = k in refused || runCatching { volumes.isLocked(k) }.getOrDefault(false)
                    SoundRow(k, v, max, enabled = !locked, reason = if (locked) DND_REASON else null)
                }
            },
        )
    }
}

/** The public AudioManager implementation: no hidden API, no permission. The notification stream is locked under Do Not Disturb without policy access. */
class AndroidVolumeController(context: Context) : VolumeController {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val nm = app.getSystemService(NotificationManager::class.java)
    private var refused = false

    override fun volume(kind: SoundRowKind) = audio.getStreamVolume(kind.stream ?: AudioManager.STREAM_MUSIC)
    override fun max(kind: SoundRowKind) = audio.getStreamMaxVolume(kind.stream ?: AudioManager.STREAM_MUSIC)
    override fun setVolume(kind: SoundRowKind, value: Int): Boolean {
        val stream = kind.stream ?: return false
        return try { audio.setStreamVolume(stream, value, 0); true } catch (_: SecurityException) {
            if (kind == SoundRowKind.Notifications) refused = true
            false
        }
    }
    override fun isLocked(kind: SoundRowKind): Boolean = kind == SoundRowKind.Notifications &&
        (refused || (nm != null && nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL && !nm.isNotificationPolicyAccessGranted))
}
