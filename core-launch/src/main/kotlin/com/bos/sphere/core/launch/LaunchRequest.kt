package com.bos.sphere.core.launch

/** A window rectangle in pixels on the target display. */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    init { require(right > left && bottom > top) { "empty bounds" } }
}

/** Preset placements on a display (WSP-04). */
enum class BoundsPreset { LeftHalf, RightHalf, Centred, Full;
    fun on(width: Int, height: Int): Bounds = when (this) {
        LeftHalf -> Bounds(0, 0, width / 2, height)
        RightHalf -> Bounds(width / 2, 0, width, height)
        Centred -> Bounds(width / 6, height / 8, width - width / 6, height - height / 8)
        Full -> Bounds(0, 0, width, height)
    }
}

enum class Placement { Default, Adjacent }

/** What to open: an installed app activity, optionally for a profile user id. */
data class LaunchTarget(val packageName: String, val className: String? = null, val userId: Int = 0)

/** Every launch in the launcher is one of these (spec section 5, WSP-01). */
data class LaunchRequest(
    val target: LaunchTarget,
    val displayId: Int? = null,
    val bounds: Bounds? = null,
    val placement: Placement = Placement.Default,
)

/** Why a requested option was not applied. The UI shows each reason at most once per session (WSP-01). */
enum class DropReason(val message: String) {
    DisplayMissing("That display is not connected, so the app opened here"),
    DisplayCannotHost("That display cannot host apps, so the app opened here"),
    BoundsIgnored("This display does not support window bounds, so the app opened normally"),
    AdjacentUnsupported("Opening beside is not supported here, so the app opened normally"),
}

/** What the platform will be asked to do, and what had to be dropped. */
data class Resolution(
    val displayId: Int?,
    val bounds: Bounds?,
    val adjacent: Boolean,
    val dropped: List<DropReason>,
)

/** What the device can do for launches; filled from `core-capabilities` plus spike results. */
data class LaunchCaps(
    val displayIds: Set<Int>,
    val hostingDisplayIds: Set<Int>,
    val boundsHonoured: Boolean,
    /** Null = not yet proven by SPK-02; treated as unsupported so the action stays hidden (WSP-03). */
    val adjacentSupported: Boolean?,
    val defaultDisplayId: Int = 0,
)

object LaunchResolver {
    fun resolve(req: LaunchRequest, caps: LaunchCaps): Resolution {
        val dropped = mutableListOf<DropReason>()
        var display = req.displayId
        if (display != null && display != caps.defaultDisplayId) {
            when {
                display !in caps.displayIds -> { dropped += DropReason.DisplayMissing; display = null }
                display !in caps.hostingDisplayIds -> { dropped += DropReason.DisplayCannotHost; display = null }
            }
        }
        var bounds = req.bounds
        if (bounds != null && !caps.boundsHonoured) { dropped += DropReason.BoundsIgnored; bounds = null }
        var adjacent = req.placement == Placement.Adjacent
        if (adjacent && caps.adjacentSupported != true) { dropped += DropReason.AdjacentUnsupported; adjacent = false }
        return Resolution(display, bounds, adjacent, dropped)
    }

    /** Whether "Open beside" should appear in an action list (WSP-03). */
    fun offersAdjacent(caps: LaunchCaps) = caps.adjacentSupported == true

    /** Display ids to offer as "Open on <display>" (WSP-02): every hosting display other than where the launcher is. */
    fun offeredDisplays(caps: LaunchCaps, current: Int): List<Int> = caps.hostingDisplayIds.filter { it != current && it in caps.displayIds }.sorted()
}

/** Shows each drop reason once per session. */
class DropNotices {
    private val seen = mutableSetOf<DropReason>()
    fun fresh(dropped: List<DropReason>): List<DropReason> = dropped.filter { seen.add(it) }
}

/** A saved group of 2 to 4 apps (WSP-05) with a layout preset. */
data class AppGroup(val name: String, val members: List<LaunchRequest>) {
    init { require(members.size in 2..4) { "a group has 2 to 4 apps" } }
}
