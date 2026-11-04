package com.bos.sphere.feature.homewidgets.model

/**
 * A launcher entry chosen for a pad: the activity ([component], `ComponentName.flattenToString()`)
 * and its profile ([userSerial], `UserManager.getSerialNumberForUser`), so work-profile apps launch
 * in their own profile.
 */
data class PadRef(val component: String, val userSerial: Long) {
    val packageName: String get() = component.substringBefore('/')
}

/** A launchable app as the pads see it. [hidden]: hidden in Sphere (never shown on a pad). */
data class AppFact(val ref: PadRef, val label: String, val hidden: Boolean)

data class Pad(val ref: PadRef, val label: String)

/**
 * The pads to draw, in the order chosen. [unavailable]: chosen apps that are no longer installed
 * or launchable, or are now hidden in Sphere (not drawn; the options screen still lists them).
 */
data class PadsView(val pads: List<Pad>, val unavailable: Int)

/** Per-widget storage of the chosen apps: `1|component#serial|component#serial…`, order kept. */
object PadsCodec {
    const val MAX = 6
    private const val VERSION = "1"

    fun encode(refs: List<PadRef>): String =
        (listOf(VERSION) + refs.distinct().take(MAX).map { "${it.component}#${it.userSerial}" }).joinToString("|")

    /** Damaged entries are skipped, duplicates dropped, at most [MAX] kept; an unknown version reads as none. */
    fun decode(s: String?): List<PadRef> {
        if (s.isNullOrBlank()) return emptyList()
        val parts = s.split('|')
        if (parts.first() != VERSION) return emptyList()
        return parts.drop(1).mapNotNull { entry ->
            val component = entry.substringBeforeLast('#', "")
            val serial = entry.substringAfterLast('#', "").toLongOrNull()
            val pkg = component.substringBefore('/', "")
            val cls = component.substringAfter('/', "")
            if (serial == null || pkg.isEmpty() || cls.isEmpty()) null else PadRef(component, serial)
        }.distinct().take(MAX)
    }
}

object PadsModel {

    /** Pads each layout shows. */
    fun padsFor(tier: Tier): Int = when (tier) {
        Tier.SMALL -> 2
        Tier.MEDIUM -> 4
        Tier.LARGE -> 6
    }

    /** The chosen apps that can be drawn, in order, against what is installed now. */
    fun resolve(chosen: List<PadRef>, installed: Map<PadRef, AppFact>): PadsView {
        val pads = chosen.mapNotNull { ref -> installed[ref]?.takeIf { !it.hidden }?.let { Pad(ref, it.label) } }
        return PadsView(pads, chosen.size - pads.size)
    }

    /** The options screen's tap: adds [ref] at the end (while there is room) or removes it. */
    fun toggle(current: List<PadRef>, ref: PadRef): List<PadRef> = when {
        ref in current -> current - ref
        current.size >= PadsCodec.MAX -> current
        else -> current + ref
    }
}
