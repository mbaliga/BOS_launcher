package com.bos.sphere.feature.grid

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/** Interface tone kinds — the same palette as Orrery's `sfx.js` (orrery-kit). */
enum class UiSound { Move, Hover, Tick, Select, Back, Open, Close, Done, Nope }

/**
 * Console-style interface tones synthesised on the device: square and triangle oscillators
 * with fast exponential envelopes and optional pitch slides, a straight port of orrery-kit's
 * `sfx.js` so Sphere's settings space sounds like Orrery. Nothing is loaded from disk; each
 * sound is rendered once to PCM and kept in a static [AudioTrack].
 */
class UiSounds {
    var enabled: Boolean = true
    var volume: Float = 0.4f
        set(value) { field = value.coerceIn(0f, 1f); tracks.values.forEach { applyVolume(it) } }

    private val tracks = HashMap<UiSound, AudioTrack>()
    private var lastAt = 0L
    private var lastKind: UiSound? = null

    fun play(kind: UiSound) {
        if (!enabled || volume <= 0f) return
        val now = SystemClock.uptimeMillis()
        val gap = when (kind) { UiSound.Hover -> 70L; UiSound.Move -> 40L; else -> 30L }
        if (now - lastAt < gap && (kind == lastKind || kind == UiSound.Hover)) return
        lastAt = now; lastKind = kind
        runCatching {
            val t = tracks.getOrPut(kind) { build(kind) }
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            t.reloadStaticData()
            applyVolume(t)
            t.play()
        }
    }

    fun release() {
        tracks.values.forEach { runCatching { it.release() } }
        tracks.clear()
    }

    private fun applyVolume(t: AudioTrack) { runCatching { t.setVolume(volume) } }

    private fun build(kind: UiSound): AudioTrack {
        val pcm = render(kind)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        return track
    }

    private enum class Wave { Square, Triangle }

    private class Note(val wave: Wave, val f: Double, val at: Double, val len: Double, val level: Double, val slideTo: Double? = null)

    companion object {
        const val RATE = 44_100
        private const val MASTER = 0.25

        private fun notes(kind: UiSound): List<Note> = when (kind) {
            UiSound.Move -> listOf(Note(Wave.Square, 1480.0, 0.0, 0.045, 0.22))
            UiSound.Hover -> listOf(Note(Wave.Square, 1760.0, 0.0, 0.03, 0.09))
            UiSound.Tick -> listOf(Note(Wave.Triangle, 880.0, 0.0, 0.06, 0.4))
            UiSound.Select -> listOf(Note(Wave.Square, 659.0, 0.0, 0.06, 0.3), Note(Wave.Square, 988.0, 0.06, 0.08, 0.3))
            UiSound.Back -> listOf(Note(Wave.Square, 988.0, 0.0, 0.06, 0.26), Note(Wave.Square, 659.0, 0.06, 0.08, 0.26))
            UiSound.Open -> listOf(Note(Wave.Triangle, 330.0, 0.0, 0.14, 0.35, slideTo = 660.0))
            UiSound.Close -> listOf(Note(Wave.Triangle, 660.0, 0.0, 0.12, 0.3, slideTo = 330.0))
            UiSound.Done -> listOf(
                Note(Wave.Square, 523.0, 0.0, 0.07, 0.3),
                Note(Wave.Square, 659.0, 0.07, 0.07, 0.3),
                Note(Wave.Square, 784.0, 0.14, 0.12, 0.34),
            )
            UiSound.Nope -> listOf(Note(Wave.Square, 110.0, 0.0, 0.09, 0.3))
        }

        /** Renders [kind] to 16-bit mono PCM (exposed for tests). */
        fun render(kind: UiSound): ShortArray {
            val ns = notes(kind)
            val total = ns.maxOf { it.at + it.len } + 0.02
            val out = DoubleArray((total * RATE).toInt() + 1)
            for (n in ns) {
                val start = (n.at * RATE).toInt()
                val count = ((n.len + 0.02) * RATE).toInt()
                var phase = 0.0
                for (i in 0 until count) {
                    val t = i.toDouble() / RATE
                    // Exponential frequency slide, as WebAudio's exponentialRampToValueAtTime.
                    val f = if (n.slideTo != null) n.f * exp(ln(n.slideTo / n.f) * (t / n.len).coerceAtMost(1.0)) else n.f
                    phase += f / RATE
                    val p = phase - kotlin.math.floor(phase)
                    val osc = when (n.wave) {
                        Wave.Square -> if (p < 0.5) 1.0 else -1.0
                        Wave.Triangle -> 1.0 - 4.0 * abs(p - 0.5)
                    }
                    // 6 ms exponential attack to [level], exponential decay to silence at [len].
                    val env = when {
                        t < 0.006 -> 0.0001 * exp(ln(n.level / 0.0001) * t / 0.006)
                        t < n.len -> n.level * exp(ln(0.0001 / n.level) * (t - 0.006) / (n.len - 0.006))
                        else -> 0.0
                    }
                    val idx = start + i
                    if (idx < out.size) out[idx] += osc * env * MASTER
                }
            }
            return ShortArray(out.size) { (out[it].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort() }
        }
    }
}
