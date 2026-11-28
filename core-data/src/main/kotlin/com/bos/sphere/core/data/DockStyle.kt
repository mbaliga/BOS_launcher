package com.bos.sphere.core.data

/**
 * Wave 39 (dockThemes): how the dock is drawn. Exactly three: the dark rounded pill it always was, no surface at all (icons float over the
 * wallpaper with a legibility halo), or a physical glossy shelf. The [id] is the stored and theme-pack spelling; never rename one.
 * Stored in the dock settings (`dock.style` of the workspace file), so it travels with backup and export like the other dock settings.
 */
enum class DockStyle(val id: String, val label: String) {
    Standard("standard", "Standard"),
    Transparent("transparent", "Transparent"),
    ThreeD("3d", "3D");

    companion object {
        fun forId(id: String?): DockStyle? = entries.firstOrNull { it.id == id }
        fun orDefault(id: String?): DockStyle = forId(id) ?: Standard
    }
}

/**
 * Where a future theme store would live (owner decision, wave 39: leave the link out for now). There is deliberately no address, no UI and no
 * network code: [ADDRESS] is null. When the owner has an address, setting it here (and adding the browse row that reads [isConfigured]) is the
 * whole change; theme packs themselves never carry or open a link. See docs/decisions/dockThemes.md.
 */
object ThemeStore {
    /** The store's web address, or null while there is none. */
    val ADDRESS: String? = null

    fun isConfigured(): Boolean = !ADDRESS.isNullOrBlank()
}
