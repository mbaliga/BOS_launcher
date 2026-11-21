package com.bos.sphere.core.catalog

/**
 * HOM-06: profile rules as pure data. Android calls feed [ProfileInfo]; the rules decide what the
 * drawer and home show, so they can be tested without a device.
 */
enum class ProfileKind { Personal, Work, Private, Other }

data class ProfileInfo(
    val id: Int,
    val kind: ProfileKind,
    /** True when apps of this profile are paused (work mode off, or private space locked). */
    val quiet: Boolean = false,
    val label: String = "",
)

object ProfilePolicy {

    const val PRIVATE_SPACE_MIN_API = 35

    /**
     * Whether apps of [p] are listed. Private space shows only on Android 15+, only when the
     * capability gate allows it, and only while unlocked (not quiet). Work apps stay listed while
     * paused (greyed), so that they can be resumed from the drawer.
     */
    fun visible(p: ProfileInfo, apiLevel: Int, privateSpaceCapable: Boolean): Boolean = when (p.kind) {
        ProfileKind.Private -> apiLevel >= PRIVATE_SPACE_MIN_API && privateSpaceCapable && !p.quiet
        else -> true
    }

    fun visibleProfiles(all: List<ProfileInfo>, apiLevel: Int, privateSpaceCapable: Boolean): List<ProfileInfo> =
        all.filter { visible(it, apiLevel, privateSpaceCapable) }

    /** Work apps carry the briefcase badge; private ones the lock. */
    fun badged(kind: ProfileKind): Boolean = kind == ProfileKind.Work || kind == ProfileKind.Private

    fun canPause(kind: ProfileKind): Boolean = kind == ProfileKind.Work

    /** Apps of a paused profile are shown dimmed and cannot be launched until it resumes. */
    fun launchable(p: ProfileInfo): Boolean = !p.quiet

    /** Drawer tabs: personal always, then work and private only when such a profile is visible. */
    fun tabs(visible: List<ProfileInfo>): List<DrawerTab> = buildList {
        add(DrawerTab.Personal)
        if (visible.any { it.kind == ProfileKind.Work }) add(DrawerTab.Work)
        if (visible.any { it.kind == ProfileKind.Private }) add(DrawerTab.Private)
    }
}

enum class DrawerTab(val label: String) {
    Personal("Personal"), Work("Work"), Private("Private");

    fun matches(kind: ProfileKind): Boolean = when (this) {
        Personal -> kind == ProfileKind.Personal || kind == ProfileKind.Other
        Work -> kind == ProfileKind.Work
        Private -> kind == ProfileKind.Private
    }
}
