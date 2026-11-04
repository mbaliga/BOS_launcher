package com.bos.sphere.feature.widgets

/** Where a hosted widget lives: a tile on the coverflow equator, or the pull-down hub's stack. */
enum class WidgetSurface { Equator, Hub }

/**
 * Height presets for a widget in the hub stack, which is always the full panel width. Three
 * steps give responsive widgets room for their small, medium and large layouts.
 */
enum class HubSize(val heightDp: Float) {
    Small(120f),
    Medium(200f),
    Large(290f),
}

/**
 * One hosted App Widget as persisted. Pure data with no Android types, so it is JVM-testable.
 *
 * - [appWidgetId]: the id the system's widget service gave this launcher's host. **Negative while
 *   a backup restore is pending** ([isRestorePending]): the placement came from another device's
 *   backup and holds `-oldId`, the id it had there. Such a placement never touches the host (no
 *   view, no delete) until `ACTION_APPWIDGET_HOST_RESTORED` maps the old id to a new one here, so
 *   it can never be confused with an unrelated widget that happens to have the same number.
 * - [providerPackage] / [providerClass]: the provider's `ComponentName`.
 * - [userSerial]: `UserManager.getSerialNumberForUser` of the profile the provider belongs to
 *   (work-profile widgets keep their profile across restarts).
 * - [slot]: on the [WidgetSurface.Equator], the index the widget takes in the merged app + widget
 *   list ([EquatorMerge]); in the [WidgetSurface.Hub], its order in the stack.
 * - [hubSize]: the height chosen for the hub stack (kept while on the equator, so moving back to
 *   the hub restores it).
 */
data class WidgetPlacement(
    val appWidgetId: Int,
    val providerPackage: String,
    val providerClass: String,
    val userSerial: Long,
    val surface: WidgetSurface,
    val slot: Int,
    val hubSize: HubSize,
) {
    /** True while this placement waits for a backup restore to tell it its new id. */
    val isRestorePending: Boolean get() = appWidgetId < 0

    /** The id this placement had on the device its backup came from (while [isRestorePending]). */
    val restoredFromId: Int get() = -appWidgetId
}

/**
 * What [PlacementCodec.decodeAll] read: the placements it understood, the lines it could not parse
 * (kept verbatim so a newer format or one damaged line is never erased by a rewrite), and the
 * widget ids those lines still name (field 1), which reconciliation must not delete.
 */
data class DecodedPlacements(
    val placements: List<WidgetPlacement>,
    val unparsed: List<String>,
    val reservedIds: Set<Int>,
)

/**
 * Line codec for placements, stored as one SharedPreferences string (the same zero-dependency
 * approach as `HiddenAppsStore`). One placement per line, `;`-separated fields, led by a format
 * version; field 1 is always the widget id (negative = restore pending), and future formats must
 * keep it there. Lines this version can't read are kept as they are rather than dropped, and the
 * ids in them are reserved; duplicate ids keep the first.
 */
object PlacementCodec {
    private const val VERSION = "1"
    private const val SEP = ";"
    private const val FIELDS = 8

    fun encode(placements: List<WidgetPlacement>, unparsed: List<String> = emptyList()): String =
        (
            placements.filter { it.isEncodable() }.map { p ->
                listOf(
                    VERSION,
                    p.appWidgetId.toString(),
                    p.providerPackage,
                    p.providerClass,
                    p.userSerial.toString(),
                    p.surface.name,
                    p.slot.toString(),
                    p.hubSize.name,
                ).joinToString(SEP)
            } + unparsed.filter { it.isNotBlank() }
            ).joinToString("\n")

    fun decode(text: String?): List<WidgetPlacement> = decodeAll(text).placements

    fun decodeAll(text: String?): DecodedPlacements {
        if (text.isNullOrBlank()) return DecodedPlacements(emptyList(), emptyList(), emptySet())
        val seen = HashSet<Int>()
        val placements = ArrayList<WidgetPlacement>()
        val unparsed = ArrayList<String>()
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val p = decodeLine(line)
            when {
                p == null -> unparsed += line
                seen.add(p.appWidgetId) -> placements += p
                // A duplicate: the first line already holds this id, so nothing is lost.
            }
        }
        val reserved = unparsed.mapNotNullTo(LinkedHashSet()) { line ->
            line.trim().split(SEP).getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
        }
        return DecodedPlacements(placements, unparsed, reserved)
    }

    private fun decodeLine(line: String): WidgetPlacement? {
        val f = line.trim().split(SEP)
        if (f.size != FIELDS || f[0] != VERSION) return null
        val id = f[1].toIntOrNull()?.takeIf { it != 0 && it != Int.MIN_VALUE } ?: return null
        val pkg = f[2].takeIf { it.isNotBlank() } ?: return null
        val cls = f[3].takeIf { it.isNotBlank() } ?: return null
        val serial = f[4].toLongOrNull() ?: return null
        val surface = WidgetSurface.entries.firstOrNull { it.name == f[5] } ?: return null
        val slot = f[6].toIntOrNull()?.coerceAtLeast(0) ?: return null
        val size = HubSize.entries.firstOrNull { it.name == f[7] } ?: return null
        return WidgetPlacement(id, pkg, cls, serial, surface, slot, size)
    }

    private fun WidgetPlacement.isEncodable(): Boolean =
        appWidgetId != 0 && appWidgetId != Int.MIN_VALUE &&
            providerPackage.isNotBlank() && providerClass.isNotBlank() &&
            listOf(providerPackage, providerClass).none { SEP in it || '\n' in it }
}
