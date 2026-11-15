package com.bos.sphere.core.catalog

/** HOM-06: which profile mark a home icon shows. */
enum class ProfileBadgeKind { None, Work, Private }

/**
 * HOM-06: the second-badge decision. `LauncherActivityInfo.getBadgedIcon` returns an icon with the system's work badge
 * already on it, so the launcher normally adds nothing. The badge is lost whenever the launcher replaces the icon with
 * its own (a per-item override, an icon pack drawable, or the themed monochrome layer): then, and only then, the home
 * draws the profile badge itself. Drawing it on a system-badged icon would show two.
 */
object ProfileBadgeRule {
    /** True when the icon drawn for [source] is not the system's badged one. */
    fun systemBadgeLost(source: IconSource): Boolean = when (source) {
        is IconSource.Override, is IconSource.Pack, IconSource.Themed -> true
        IconSource.Adaptive, IconSource.Legacy -> false
    }

    fun decide(kind: ProfileKind, source: IconSource): ProfileBadgeKind = decide(kind, systemBadgeLost(source))

    fun decide(kind: ProfileKind, systemBadgeLost: Boolean): ProfileBadgeKind =
        if (!systemBadgeLost || !ProfilePolicy.badged(kind)) ProfileBadgeKind.None
        else if (kind == ProfileKind.Private) ProfileBadgeKind.Private else ProfileBadgeKind.Work
}

/** HOM-06: the private-space gate the drawer shows. Private space is Android 15 and later, behind the capability gate. */
enum class PrivateSpaceGate {
    /** No private profile, or this device or build cannot use it: nothing is shown. */
    Hidden,
    /** The private profile exists and is locked: its apps are not listed; a chip asks the system to unlock it. */
    Locked,
    /** Unlocked: its apps are listed in the Private tab, and a chip locks it again. */
    Unlocked;

    companion object {
        /** [quiet] is null when there is no private profile, true while it is locked. */
        fun of(quiet: Boolean?, apiLevel: Int, capable: Boolean): PrivateSpaceGate = when {
            quiet == null || apiLevel < ProfilePolicy.PRIVATE_SPACE_MIN_API || !capable -> Hidden
            quiet -> Locked
            else -> Unlocked
        }

        /** Chip text for a gate state; null when no chip is shown. */
        fun chipLabel(gate: PrivateSpaceGate, privateTabOpen: Boolean): String? = when (gate) {
            Hidden -> null
            Locked -> "Unlock private space"
            Unlocked -> if (privateTabOpen) "Lock private space" else null
        }
    }
}
