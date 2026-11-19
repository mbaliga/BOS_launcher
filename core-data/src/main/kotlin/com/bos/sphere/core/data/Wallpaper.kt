package com.bos.sphere.core.data

/**
 * HOM-16 (wave 26, owner clause 10; wave 34 adds two): the wallpaper of a surface is exactly one of five kinds: a solid colour, a two-stop
 * linear gradient, a signal wallpaper (the signal-driven [BackgroundChoice], stored as "live"), the system's own live wallpaper (the launcher
 * draws nothing, so what the system shows behind it shows through) or a picture from the gallery (a still or an animated GIF / WebP).
 * The Glance has one, the pages (paginated or scroll alike) either share it or have their own. Nothing else is a wallpaper.
 */
enum class WallpaperKind(val id: String, val label: String) {
    Solid("solid", "Solid colour"),
    Gradient("gradient", "Gradient"),
    Live("live", "Signal wallpaper"),
    System("system", "System live wallpaper"),
    Picture("picture", "Picture");

    companion object { fun forId(id: String?): WallpaperKind? = entries.firstOrNull { it.id == id } }
}

/** The two places a wallpaper is drawn: the Glance screen, and the pages / scroll that follow it. */
enum class WallpaperSurface { Glance, Pages }

/** A linear gradient of two stops; [angle] in degrees, 0 = left to right, 90 = top to bottom. Colours are 0xAARRGGBB, always opaque. */
data class GradientSpec(val from: Long = DEFAULT_FROM, val to: Long = DEFAULT_TO, val angle: Int = DEFAULT_ANGLE) {
    fun normalised() = GradientSpec(from or OPAQUE, to or OPAQUE, ((angle % 360) + 360) % 360)

    companion object {
        const val OPAQUE = 0xFF000000L
        const val DEFAULT_FROM = 0xFF2A2F6BL
        const val DEFAULT_TO = 0xFF0B0B10L
        const val DEFAULT_ANGLE = 90
    }
}

/** One wallpaper: all kinds' settings are kept so switching kind never loses the others; [kind] says which one is drawn. */
data class Wallpaper(
    val kind: WallpaperKind = WallpaperKind.Live,
    val solid: Long = DEFAULT_SOLID,
    val gradient: GradientSpec = GradientSpec(),
    val live: BackgroundChoice = BackgroundChoice.DEFAULT,
    val picture: PictureSpec = PictureSpec(),
    /** Percent of black over the system wallpaper (kind [WallpaperKind.System]), one of [PictureLimits.DIMS]. */
    val systemDim: Int = 0,
) {
    companion object { const val DEFAULT_SOLID = 0xFF16161CL }
}

/** The stored form of one surface's wallpaper (see [LauncherPrefs.wallpaper]). */
fun LauncherPrefs.rawWallpaper(surface: WallpaperSurface): Wallpaper = when (surface) {
    WallpaperSurface.Glance -> Wallpaper(glanceWallpaperKind, glanceSolid, GradientSpec(glanceGradFrom, glanceGradTo, glanceGradAngle), BackgroundChoice(backgroundSlot1, backgroundSlot2), glancePicture, glanceSystemDim)
    WallpaperSurface.Pages -> Wallpaper(pagesWallpaperKind, pagesSolid, GradientSpec(pagesGradFrom, pagesGradTo, pagesGradAngle), BackgroundChoice(pagesSlot1, pagesSlot2), pagesPicture, pagesSystemDim)
}

/** What is drawn on [surface]: the pages show the Glance wallpaper while [LauncherPrefs.pagesSameAsGlance] is on. */
fun LauncherPrefs.wallpaper(surface: WallpaperSurface): Wallpaper =
    if (surface == WallpaperSurface.Pages && !pagesSameAsGlance) rawWallpaper(WallpaperSurface.Pages) else rawWallpaper(WallpaperSurface.Glance)

/** These prefs with [w] stored as [surface]'s own wallpaper. */
fun LauncherPrefs.withWallpaper(surface: WallpaperSurface, w: Wallpaper): LauncherPrefs {
    val g = w.gradient.normalised()
    val c = BackgroundChoice.normalised(w.live.slot1, w.live.slot2)
    return when (surface) {
        WallpaperSurface.Glance -> copy(glanceWallpaperKind = w.kind, glanceSolid = w.solid or GradientSpec.OPAQUE, glanceGradFrom = g.from, glanceGradTo = g.to, glanceGradAngle = g.angle, backgroundSlot1 = c.slot1, backgroundSlot2 = c.slot2, glancePicture = w.picture.normalised(), glanceSystemDim = PictureLimits.nearestDim(w.systemDim))
        WallpaperSurface.Pages -> copy(pagesWallpaperKind = w.kind, pagesSolid = w.solid or GradientSpec.OPAQUE, pagesGradFrom = g.from, pagesGradTo = g.to, pagesGradAngle = g.angle, pagesSlot1 = c.slot1, pagesSlot2 = c.slot2, pagesPicture = w.picture.normalised(), pagesSystemDim = PictureLimits.nearestDim(w.systemDim))
    }
}

/** True when [surface] and Glance draw the very same wallpaper, so one background instance can serve both. */
fun LauncherPrefs.sharesWallpaper(surface: WallpaperSurface): Boolean = surface == WallpaperSurface.Glance || pagesSameAsGlance || wallpaper(surface) == wallpaper(WallpaperSurface.Glance)
