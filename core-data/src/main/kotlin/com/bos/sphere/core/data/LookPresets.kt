package com.bos.sphere.core.data

/**
 * Wave 56 (looksPresets): the built-in Looks, whole looks you apply with one tap from the top of the Look page. A Look is DATA: a [ThemePack]
 * (the same model a `.liftofftheme` file has), so applying one is the ordinary theme path ([ThemeApplier.applyLook]: one in-memory undo, the
 * same "Undo last theme change" row), and adding one is a new entry in [LookPresets.all] with no UI code (the card picture is drawn from the values).
 *
 * A Look sets the light / dark mode, accent, icon shape, icon colour, icon plate, dock style, UI font and calm style, and a Solid or Gradient Glance
 * wallpaper. It never replaces the owner's own wallpaper: while the Glance wallpaper is a picture, the Signal (live) wallpaper or the system live
 * wallpaper, a Look leaves the wallpaper alone, and then does not count the wallpaper when it decides whether it is the picked Look.
 */
data class LookPreset(val id: String, val name: String, val pack: ThemePack) {
    val settings: ThemeSettings get() = pack.settings
}

object LookPresets {
    private fun solid(c: Long) = ThemeWallpaper.Solid(c)
    private fun grad(a: Long, b: Long, angle: Int = 90) = ThemeWallpaper.Gradient(a, b, angle)

    private fun look(id: String, name: String, s: ThemeSettings) = LookPreset(id, name, ThemePack(name, "Liftoff", "1", s))

    /** The bundled Looks, in the order of the rail. Every one sets every field a Look carries (a test holds this). */
    val all: List<LookPreset> = listOf(
        look("midnight", "Midnight", ThemeSettings(ThemeMode.Amoled, 0xFF8A7BFF, IconShape.Rounded, IconTint.None, DockStyle.Standard, solid(0xFF000000), "space_grotesk", CalmStyle.EdgeDots, true)),
        look("paper", "Paper", ThemeSettings(ThemeMode.Light, 0xFF4A4F5C, IconShape.Rounded, IconTint.None, DockStyle.Standard, solid(0xFFF1EFE8), "system", CalmStyle.None, true)),
        look("terminal", "Terminal", ThemeSettings(ThemeMode.Dark, 0xFF3DDC84, IconShape.Square, IconTint.Accent, DockStyle.Standard, solid(0xFF08100B), "jetbrains_mono", CalmStyle.None, false)),
        look("dusk", "Dusk", ThemeSettings(ThemeMode.Dark, 0xFF6EC1FF, IconShape.Circle, IconTint.None, DockStyle.Transparent, grad(0xFF1E3350, 0xFF0A0E18), "inter", CalmStyle.BottomGlow, false)),
        look("sunrise", "Sunrise", ThemeSettings(ThemeMode.Light, 0xFFFF7A3D, IconShape.Squircle, IconTint.None, DockStyle.Standard, grad(0xFFFFE6C8, 0xFFFFB48C), "manrope", CalmStyle.CornerBloom, true)),
        look("mono", "Mono", ThemeSettings(ThemeMode.Dark, 0xFFBDBDBD, IconShape.Circle, IconTint.Mono, DockStyle.ThreeD, solid(0xFF111111), "dm_sans", CalmStyle.Regular, false)),
    )

    fun byId(id: String?): LookPreset? = all.firstOrNull { it.id == id }

    /** True when [kind] is a wallpaper a Look may replace (a Solid colour or a Gradient); every other kind is the owner's own. */
    fun mayReplace(kind: WallpaperKind): Boolean = kind == WallpaperKind.Solid || kind == WallpaperKind.Gradient

    /** What applying [preset] to [p] changes: the Look's settings, without the wallpaper when the Glance wallpaper is not a Solid or Gradient. */
    fun settingsFor(preset: LookPreset, p: LauncherPrefs): ThemeSettings =
        if (mayReplace(p.rawWallpaper(WallpaperSurface.Glance).kind)) preset.settings else preset.settings.copy(wallpaper = null)

    /** Whether the current look IS [preset] (Clackpad's QuickTheme.matches): every field the Look sets equals the stored value. */
    fun matches(p: LauncherPrefs, dock: DockStyle, preset: LookPreset): Boolean {
        val s = preset.settings
        if (s.themeMode != null && s.themeMode != p.themeMode) return false
        if (s.accent != null && (s.accent or GradientSpec.OPAQUE) != (p.accentColor or GradientSpec.OPAQUE)) return false
        if (s.iconShape != null && s.iconShape != p.iconShape) return false
        if (s.iconTint != null && s.iconTint != p.iconTint) return false
        if (s.dockStyle != null && s.dockStyle != dock) return false
        if (s.uiFont != null && UiFonts.normalize(s.uiFont) != UiFonts.normalize(p.uiFont)) return false
        if (s.calmStyle != null && s.calmStyle != p.calmStyle) return false
        if (s.iconPlate != null && s.iconPlate != p.iconPlate) return false
        val w = p.rawWallpaper(WallpaperSurface.Glance)
        if (mayReplace(w.kind)) when (val want = s.wallpaper) {
            is ThemeWallpaper.Solid -> if (w.kind != WallpaperKind.Solid || (w.solid or GradientSpec.OPAQUE) != want.color) return false
            is ThemeWallpaper.Gradient -> {
                val g = w.gradient.normalised()
                if (w.kind != WallpaperKind.Gradient || g.from != want.from || g.to != want.to || g.angle != want.angle % 360) return false
            }
            null -> Unit
        }
        return true
    }

    /** The Look the settings add up to, or null for a custom look. The first of [all] that matches (a test holds that at most one does). */
    fun current(p: LauncherPrefs, dock: DockStyle): LookPreset? = all.firstOrNull { matches(p, dock, it) }
}
