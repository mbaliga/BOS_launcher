package com.bos.sphere.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One row of the import preview: what a setting is now and what the theme would make it. Colours are 0xFFRRGGBB swatches when the setting is a colour. */
data class ThemeChange(
    val field: ThemeField,
    val before: String,
    val after: String,
    val beforeColors: List<Long> = emptyList(),
    val afterColors: List<Long> = emptyList(),
) { val changes: Boolean get() = before != after || beforeColors != afterColors }

/** The look before a theme was applied (or reset): everything a pack can change, including a wallpaper kind a pack cannot carry. */
data class LookSnapshot(
    val themeMode: ThemeMode,
    val accent: Long,
    val iconShape: IconShape,
    val iconTint: IconTint,
    val dockStyle: DockStyle,
    val glanceWallpaper: Wallpaper,
    val uiFont: String = UiFonts.DEFAULT,
    /** Wave 56: a Look also sets these two; null calm style = automatic. */
    val calmStyle: CalmStyle? = null,
    val iconPlate: Boolean = true,
)

/** Reading a look from the settings, describing a pack's effect, and applying it. All pure. */
object ThemeLook {
    private val defaults = LauncherPrefs()

    fun snapshot(p: LauncherPrefs, dock: DockStyle) = LookSnapshot(p.themeMode, p.accentColor, p.iconShape, p.iconTint, dock, p.rawWallpaper(WallpaperSurface.Glance), p.uiFont, p.calmStyle, p.iconPlate)

    fun restore(p: LauncherPrefs, s: LookSnapshot): LauncherPrefs =
        p.copy(themeMode = s.themeMode, accentColor = s.accent, iconShape = s.iconShape, iconTint = s.iconTint, uiFont = s.uiFont, calmStyle = s.calmStyle, iconPlate = s.iconPlate).withWallpaper(WallpaperSurface.Glance, s.glanceWallpaper)

    /** The look a fresh install has. */
    fun defaultSnapshot() = snapshot(defaults, DockStyle.Standard)

    /** The current look as a pack. A wallpaper that is not a solid colour or a gradient (signal, system, picture) cannot be carried, so it is left out. */
    fun capture(p: LauncherPrefs, dock: DockStyle, name: String, author: String? = null): ThemePack {
        val w = p.rawWallpaper(WallpaperSurface.Glance)
        val wp = when (w.kind) {
            WallpaperKind.Solid -> ThemeWallpaper.Solid(w.solid or GradientSpec.OPAQUE)
            WallpaperKind.Gradient -> w.gradient.normalised().let { ThemeWallpaper.Gradient(it.from, it.to, it.angle) }
            else -> null
        }
        return ThemePack(name.take(ThemePacks.MAX_TEXT), author?.take(ThemePacks.MAX_TEXT), null, ThemeSettings(p.themeMode, p.accentColor or GradientSpec.OPAQUE, p.iconShape, p.iconTint, dock, wp, p.uiFont))
    }

    /** The settings of [s] applied to [p] (the dock style is applied separately, it lives with the dock settings). The glance wallpaper is the one a pack sets; the pages follow it while they share it. */
    fun apply(p: LauncherPrefs, s: ThemeSettings): LauncherPrefs {
        var out = p
        s.themeMode?.let { out = out.copy(themeMode = it) }
        s.accent?.let { out = out.copy(accentColor = it or GradientSpec.OPAQUE) }
        s.iconShape?.let { out = out.copy(iconShape = it) }
        s.iconTint?.let { out = out.copy(iconTint = it) }
        s.uiFont?.let { out = out.copy(uiFont = UiFonts.normalize(it)) }
        s.calmStyle?.let { out = out.copy(calmStyle = it) }
        s.iconPlate?.let { out = out.copy(iconPlate = it) }
        when (val w = s.wallpaper) {
            is ThemeWallpaper.Solid -> out = out.withWallpaper(WallpaperSurface.Glance, out.rawWallpaper(WallpaperSurface.Glance).copy(kind = WallpaperKind.Solid, solid = w.color))
            is ThemeWallpaper.Gradient -> out = out.withWallpaper(WallpaperSurface.Glance, out.rawWallpaper(WallpaperSurface.Glance).copy(kind = WallpaperKind.Gradient, gradient = GradientSpec(w.from, w.to, w.angle)))
            null -> Unit
        }
        return out
    }

    /** The before / after chips for the settings [s] carries (only those), in the order the screen lists them. */
    fun changes(p: LauncherPrefs, dock: DockStyle, s: ThemeSettings): List<ThemeChange> = buildList {
        s.themeMode?.let { add(ThemeChange(ThemeField.Mode, p.themeMode.label, it.label)) }
        s.accent?.let { add(ThemeChange(ThemeField.Accent, ThemePacks.colourText(p.accentColor), ThemePacks.colourText(it), listOf(p.accentColor or GradientSpec.OPAQUE), listOf(it))) }
        s.iconShape?.let { add(ThemeChange(ThemeField.IconShape, p.iconShape.label, it.label)) }
        s.iconTint?.let { add(ThemeChange(ThemeField.IconTint, p.iconTint.label, it.label)) }
        s.dockStyle?.let { add(ThemeChange(ThemeField.DockStyle, dock.label, it.label)) }
        s.uiFont?.let { add(ThemeChange(ThemeField.Font, p.uiFont, it)) }
        s.calmStyle?.let { add(ThemeChange(ThemeField.CalmStyle, CalmStyles.resolve(p.calmStyle, p.rawWallpaper(WallpaperSurface.Glance).kind).label, it.label)) }
        s.iconPlate?.let { add(ThemeChange(ThemeField.IconPlate, if (p.iconPlate) "On" else "Off", if (it) "On" else "Off")) }
        s.wallpaper?.let { w ->
            val now = p.rawWallpaper(WallpaperSurface.Glance)
            val (bText, bCols) = when (now.kind) {
                WallpaperKind.Solid -> ThemePacks.colourText(now.solid) to listOf(now.solid or GradientSpec.OPAQUE)
                WallpaperKind.Gradient -> now.gradient.normalised().let { "Gradient" to listOf(it.from, it.to) }
                else -> now.kind.label to emptyList()
            }
            val (aText, aCols) = when (w) {
                is ThemeWallpaper.Solid -> ThemePacks.colourText(w.color) to listOf(w.color)
                is ThemeWallpaper.Gradient -> "Gradient" to listOf(w.from, w.to)
            }
            add(ThemeChange(ThemeField.Wallpaper, bText, aText, bCols, aCols))
        }
    }
}

/** The one in-memory undo: the look before the last theme was applied or the look reset. It is not stored; a restart forgets it. */
object ThemeUndo {
    private val state = MutableStateFlow<LookSnapshot?>(null)
    val snapshot: StateFlow<LookSnapshot?> = state.asStateFlow()
    fun remember(s: LookSnapshot) { state.value = s }
    fun clear() { state.value = null }
}

/**
 * Applies, undoes and resets a look through four seams (the settings store and the dock settings), so the same code runs in the app and in a JVM test.
 * Apply and reset keep the previous look for [ThemeUndo]; undo restores it and forgets it.
 */
class ThemeApplier(
    private val prefs: () -> LauncherPrefs,
    private val updatePrefs: ((LauncherPrefs) -> LauncherPrefs) -> Unit,
    private val dockStyle: () -> DockStyle,
    private val setDockStyle: (DockStyle) -> Unit,
) {
    fun current(): LookSnapshot = ThemeLook.snapshot(prefs(), dockStyle())

    fun apply(pack: ThemePack) {
        ThemeUndo.remember(current())
        updatePrefs { ThemeLook.apply(it, pack.settings) }
        pack.settings.dockStyle?.let(setDockStyle)
    }

    /** Applies a built-in Look through the theme path (one undo, the same "Undo last theme change"); it never replaces a wallpaper that is not a Solid or Gradient. */
    fun applyLook(preset: LookPreset) = apply(ThemePack(preset.name, preset.pack.author, preset.pack.version, LookPresets.settingsFor(preset, prefs())))

    /** The default look; a reset leaves the calm style and the icon plate as they are (they are not part of the wave 39 default look). */
    fun reset() = current().let { now -> restoreTo(ThemeLook.defaultSnapshot().copy(calmStyle = now.calmStyle, iconPlate = now.iconPlate), remember = true) }

    /** Restores the remembered look; false when there is none. */
    fun undo(): Boolean {
        val s = ThemeUndo.snapshot.value ?: return false
        restoreTo(s, remember = false)
        ThemeUndo.clear()
        return true
    }

    private fun restoreTo(s: LookSnapshot, remember: Boolean) {
        if (remember) ThemeUndo.remember(current())
        updatePrefs { ThemeLook.restore(it, s) }
        setDockStyle(s.dockStyle)
    }
}
