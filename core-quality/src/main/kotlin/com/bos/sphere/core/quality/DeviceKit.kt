package com.bos.sphere.core.quality

/**
 * Wave 23 (NFR-05, NFR-06, NFR-12 to NFR-14, INP-12, CFG-17, SPK-01; docs/decisions/deviceKit.md): the pure part of the Lab's
 * "Device checks" page. Nothing here touches Android, so the maths is unit-tested. The page produces EVIDENCE for the owner to paste
 * back; no spec row is closed by a number in this file.
 */

/** Accumulates the total duration (nanoseconds) of each frame a probe sees, and summarises them. */
class FrameStats(private val cap: Int = MAX_FRAMES) {
    private val totals = LongArray(cap)
    var count: Int = 0
        private set

    /** Records one frame; frames beyond [cap] are ignored (a 10 second probe at 240 Hz is 2400 frames). Negative values are dropped. */
    fun add(totalNs: Long) {
        if (totalNs < 0 || count >= cap) return
        totals[count++] = totalNs
    }

    /** The summary against a display refresh interval; null when no frame was seen. */
    fun summary(intervalNs: Long, atMs: Long): ProbeResult? {
        if (count == 0 || intervalNs <= 0) return null
        val sorted = totals.copyOf(count).also { it.sort() }
        val janky = sorted.count { it > intervalNs }
        return ProbeResult(
            frames = count, p50Ns = percentile(sorted, 50), p95Ns = percentile(sorted, 95), p99Ns = percentile(sorted, 99),
            jankyPercent = janky * 100.0 / count, slowestNs = sorted.last(), intervalNs = intervalNs, atMs = atMs,
        )
    }

    companion object {
        const val MAX_FRAMES = 20_000

        /** Nearest-rank percentile of an ascending array. */
        fun percentile(sorted: LongArray, p: Int): Long {
            require(sorted.isNotEmpty())
            val rank = Math.ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
            return sorted[rank - 1]
        }

        /** The frame interval of a display refresh rate in Hz; 60 Hz when the rate is unknown. */
        fun intervalNs(refreshHz: Float): Long = (1_000_000_000.0 / (if (refreshHz in 10f..500f) refreshHz else 60f)).toLong()
    }
}

/** One finished probe run. [jankyPercent] counts frames whose total duration exceeded [intervalNs] (the display's refresh interval). */
data class ProbeResult(
    val frames: Int, val p50Ns: Long, val p95Ns: Long, val p99Ns: Long, val jankyPercent: Double, val slowestNs: Long, val intervalNs: Long, val atMs: Long,
) {
    fun encode(): String = listOf(VERSION, frames, p50Ns, p95Ns, p99Ns, jankyPercent, slowestNs, intervalNs, atMs).joinToString("|")

    /** One line: `412 frames, p50 7.1 ms, p95 12.0 ms, p99 20.3 ms, janky 2.4% (over 16.7 ms), slowest 48.0 ms`. */
    fun line(): String = "$frames frames, p50 ${ms(p50Ns)}, p95 ${ms(p95Ns)}, p99 ${ms(p99Ns)}, janky ${"%.1f".format(java.util.Locale.ROOT, jankyPercent)}% (over ${ms(intervalNs)}), slowest ${ms(slowestNs)}"

    companion object {
        private const val VERSION = "v1"
        fun ms(ns: Long): String = "%.1f ms".format(java.util.Locale.ROOT, ns / 1_000_000.0)

        /** Reads [encode] back; null for anything that is not exactly that format (a hand-edited or older file is ignored, never thrown on). */
        fun decode(s: String?): ProbeResult? {
            val p = s?.split("|") ?: return null
            if (p.size != 9 || p[0] != VERSION) return null
            return runCatching {
                ProbeResult(p[1].toInt(), p[2].toLong(), p[3].toLong(), p[4].toLong(), p[5].toDouble(), p[6].toLong(), p[7].toLong(), p[8].toLong())
            }.getOrNull()
        }
    }
}

/** The surfaces a probe can be run on; [id] is the stored key suffix (never renamed). */
enum class ProbeSurface(val id: String, val english: String) {
    Glance("glance", "Glance"),
    DrawerScroll("drawer", "Drawer scroll"),
    RadarTyping("radar", "Radar: open and type"),
    LaunchpadOpenClose("launchpad", "Launchpad: open and close"),
    PageSwipe("pages", "Page swipe"),
}

enum class CheckStatus(val id: String, val english: String) {
    Unset("unset", "Not checked"), Pass("pass", "Pass"), Fail("fail", "Fail"), Skip("skip", "Skip");

    companion object { fun of(id: String?): CheckStatus = entries.firstOrNull { it.id == id } ?: Unset }
}

/** The device-only checks; [id] is the stored key and [closes] names the spec rows the result is evidence for. */
enum class DeviceCheck(val id: String, val english: String, val closes: String) {
    TalkBack("talkback", "TalkBack order", "NFR-13"),
    GestureNav("gesturenav", "Gesture navigation: edge swipe against the back gesture", "NFR-20, STR-01"),
    NoAnimations("noanim", "Animations removed", "NFR-14"),
    Font200("font200", "Font size 200 percent", "NFR-13"),
    DisplaySize("dispsize", "Display size large", "NFR-13"),
    GlassLook("glass", "Radar glass in daylight, dark and light", "docs/decisions/radarGlass.md"),
    RealCrash("crash", "Real crash path", "NFR-09, CFG-09"),
    Speech("speech", "Speech engine", "SPK-07, INP-13"),
    WorkBadge("workbadge", "Work profile badge", "HOM-06"),
    NotificationTap("notiftap", "Notification tap opens the app", "HOM-09"),
    ExternalDisplay("extdisplay", "External display", "SPK-01, EXT-02"),
}

data class CheckEntry(val check: DeviceCheck, val status: CheckStatus, val note: String)

/** Cold start: milliseconds from process start to the first drawn home frame, in the same process. Null when either time is unknown. */
object ColdStartMath {
    fun elapsedMs(processStartElapsedMs: Long, firstFrameElapsedMs: Long): Long? =
        if (processStartElapsedMs <= 0 || firstFrameElapsedMs < processStartElapsedMs) null else firstFrameElapsedMs - processStartElapsedMs
}

/** The plain-text report the owner pastes back. English on purpose: it is read by the developer, not shown in the UI. */
object DeviceReport {
    fun build(
        appVersion: String, device: String, android: String, coldStartMs: Long?,
        probes: Map<ProbeSurface, ProbeResult?>, checks: List<CheckEntry>, coldStartThisProcess: Boolean = true,
    ): String = buildString {
        appendLine("Liftoff device report")
        appendLine("App: $appVersion")
        appendLine("Device: $device")
        appendLine("Android: $android")
        appendLine("Evidence only: no spec row is closed by this report.")
        appendLine()
        appendLine("Cold start (process start to first home frame, ${if (coldStartThisProcess) "this process" else "an EARLIER process, not this one"}): ${coldStartMs?.let { "$it ms" } ?: "not captured"}")
        appendLine()
        appendLine("Frame timing (10 s per surface, last run)")
        for (s in ProbeSurface.entries) appendLine("- ${s.english}: ${probes[s]?.line() ?: "not run"}")
        appendLine()
        appendLine("Checklist")
        for (c in checks) appendLine("- [${c.status.english}] ${c.check.english} (${c.check.closes})" + if (c.note.isNotBlank()) ": ${c.note.trim().replace('\n', ' ')}" else "")
    }.trimEnd()
}
