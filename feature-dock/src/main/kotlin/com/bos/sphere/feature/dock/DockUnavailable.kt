package com.bos.sphere.feature.dock

/**
 * HOM-10 (wave 24, pulled out of [DockHost] so it is tested): a pinned app whose package is gone for now keeps its rail slot through
 * the reconcile grace period, labelled "App unavailable" and marked [DockItem.unavailable] (drawn dimmed, not launched).
 */
object DockUnavailable {
    /** The label of an app pin: its own label while installed, the unavailable label while it is in [unavailable], else none (no slot). */
    fun appLabel(id: String, labelOf: (String) -> String?, unavailable: Set<String>, unavailableLabel: String): String? =
        labelOf(id) ?: if (id in unavailable) unavailableLabel else null

    /** Every app pin is marked by whether it is installed right now (so a package that came back is available again); other kinds are left as they are. */
    fun mark(content: DockContent, installed: (String) -> Boolean): DockContent =
        content.copy(pinned = content.pinned.map { if (it.ref.kind == ItemKind.App) it.copy(unavailable = !installed(it.ref.id)) else it })

    /** An unavailable pin is not launched (there is nothing to launch); the rail keeps it until the grace period ends. */
    fun launchable(item: DockItem): Boolean = !item.unavailable
}
