package com.bos.sphere.core.data

import org.json.JSONObject

/**
 * Wave 39 (dockThemes): the theme pack, a small versioned JSON file (`.liftofftheme`, also accepted as `.json`) that carries a look and nothing else.
 *
 * ```
 * { "format": "liftoff-theme", "formatVersion": 1, "name": "Dusk", "author": "Someone", "version": "1.0",
 *   "settings": { "themeMode": "dark|amoled|light", "accent": "#RRGGBB", "iconShape": "original|rounded|squircle|circle|square",
 *                 "iconTint": "none|accent|mono", "dockStyle": "standard|transparent|3d",
 *                 "wallpaper": { "kind": "solid", "color": "#RRGGBB" } | { "kind": "gradient", "from": "#RRGGBB", "to": "#RRGGBB", "angle": 90 } } }
 * ```
 *
 * The file is untrusted data. It is parsed, never executed; only the keys above are read (any other key is ignored); every value is checked
 * against a closed set or a strict shape; the file is at most [ThemePacks.MAX_BYTES] bytes, a string at most [ThemePacks.MAX_TEXT] characters.
 * No picture, video, file, live wallpaper, link or number outside the allowed ranges can be carried. A pack is applied once and is not stored;
 * the only state it can change is ordinary settings (and the previous values are kept for one in-memory undo, see [ThemeUndo]).
 */
sealed interface ThemeWallpaper {
    /** Colours are 0xFFRRGGBB. */
    data class Solid(val color: Long) : ThemeWallpaper
    data class Gradient(val from: Long, val to: Long, val angle: Int = GradientSpec.DEFAULT_ANGLE) : ThemeWallpaper
}

/** What a theme may carry; each is null when the pack does not set it. */
data class ThemeSettings(
    val themeMode: ThemeMode? = null,
    val accent: Long? = null,
    val iconShape: IconShape? = null,
    val iconTint: IconTint? = null,
    val dockStyle: DockStyle? = null,
    val wallpaper: ThemeWallpaper? = null,
) {
    val isEmpty: Boolean get() = themeMode == null && accent == null && iconShape == null && iconTint == null && dockStyle == null && wallpaper == null
}

data class ThemePack(
    val name: String,
    val author: String? = null,
    val version: String? = null,
    val settings: ThemeSettings = ThemeSettings(),
    val formatVersion: Int = ThemePacks.FORMAT_VERSION,
)

/** The pack fields errors and the preview talk about; [label] is the English text (translated by the screen). */
enum class ThemeField(val label: String) {
    Name("name"), Author("author"), Version("version"), Mode("light / dark mode"), Accent("accent colour"), IconShape("icon shape"),
    IconTint("icon colour"), DockStyle("dock style"), Wallpaper("wallpaper"),
}

/** Why a file was refused; [english] is the message (a `%s` is the field's label), translated by the screen. */
enum class ThemeErrorCode(val english: String) {
    Unreadable("Liftoff could not read that file."),
    TooLarge("This file is larger than 64 KB, so it is not a theme."),
    NotJson("This file is not valid JSON."),
    NotATheme("This is not a Liftoff theme file."),
    TooNew("This theme was made for a newer version of Liftoff."),
    BadFormatVersion("The theme's format version is not a whole number from 1."),
    Missing("The theme has no %s."),
    TextTooLong("The %s is longer than 80 characters."),
    BadText("The %s has characters that are not allowed."),
    BadValue("The %s is not a value Liftoff knows."),
    BadColour("The %s is not a colour like #RRGGBB."),
    BadWallpaper("A theme's wallpaper can only be a solid colour or a gradient."),
    NothingToApply("This theme does not change anything."),
}

data class ThemeError(val code: ThemeErrorCode, val field: ThemeField? = null)

sealed interface ThemeParse {
    data class Ok(val pack: ThemePack) : ThemeParse
    data class Failed(val errors: List<ThemeError>) : ThemeParse
}

object ThemePacks {
    const val FORMAT = "liftoff-theme"
    const val FORMAT_VERSION = 1
    const val EXTENSION = "liftofftheme"
    const val MIME = "application/json"
    const val MAX_BYTES = 64 * 1024
    const val MAX_TEXT = 80
    private const val MAX_ERRORS = 8

    fun parse(bytes: ByteArray): ThemeParse =
        if (bytes.size > MAX_BYTES) ThemeParse.Failed(listOf(ThemeError(ThemeErrorCode.TooLarge))) else parse(String(bytes, Charsets.UTF_8))

    fun parse(text: String): ThemeParse {
        if (text.length > MAX_BYTES || text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return ThemeParse.Failed(listOf(ThemeError(ThemeErrorCode.TooLarge)))
        val root = runCatching { JSONObject(text.removePrefix("﻿")) }.getOrNull() ?: return ThemeParse.Failed(listOf(ThemeError(ThemeErrorCode.NotJson)))
        if ((root.opt("format") as? String) != FORMAT) return ThemeParse.Failed(listOf(ThemeError(ThemeErrorCode.NotATheme)))
        val errors = ArrayList<ThemeError>()
        fun err(c: ThemeErrorCode, f: ThemeField? = null) { if (errors.size < MAX_ERRORS) errors += ThemeError(c, f) }

        val fv = root.opt("formatVersion")
        val formatVersion = (fv as? Int) ?: (fv as? Long)?.takeIf { it in 0..Int.MAX_VALUE }?.toInt()
        if (formatVersion == null || formatVersion < 1) return ThemeParse.Failed(listOf(ThemeError(ThemeErrorCode.BadFormatVersion)))
        if (formatVersion > FORMAT_VERSION) return ThemeParse.Failed(listOf(ThemeError(ThemeErrorCode.TooNew)))

        /** A free-text field: a string of at most [MAX_TEXT] characters with no control or format characters; null when absent or refused. */
        fun text(o: JSONObject, key: String, f: ThemeField, required: Boolean, allowNumber: Boolean = false): String? {
            if (!o.has(key) || o.isNull(key)) { if (required) err(ThemeErrorCode.Missing, f); return null }
            val v = o.opt(key)
            val s = when { v is String -> v; allowNumber && v is Number -> v.toString(); else -> { err(ThemeErrorCode.BadValue, f); return null } }.trim()
            if (s.length > MAX_TEXT) { err(ThemeErrorCode.TextTooLong, f); return null }
            if (s.any { Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt() }) { err(ThemeErrorCode.BadText, f); return null }
            if (s.isEmpty()) { if (required) err(ThemeErrorCode.Missing, f); return null }
            return s
        }

        val name = text(root, "name", ThemeField.Name, required = true)
        val author = text(root, "author", ThemeField.Author, required = false)
        val version = text(root, "version", ThemeField.Version, required = false, allowNumber = true)

        val sObj = root.opt("settings") as? JSONObject
        if (sObj == null) { if (root.has("settings")) err(ThemeErrorCode.BadValue) else err(ThemeErrorCode.NothingToApply) }
        var settings = ThemeSettings()
        if (sObj != null) {
            fun <T> choice(key: String, f: ThemeField, all: Iterable<T>, id: (T) -> String): T? {
                if (!sObj.has(key) || sObj.isNull(key)) return null
                val s = sObj.opt(key) as? String
                val hit = if (s == null || s.length > MAX_TEXT) null else all.firstOrNull { id(it).equals(s.trim(), ignoreCase = true) }
                if (hit == null) err(ThemeErrorCode.BadValue, f)
                return hit
            }
            fun colour(o: JSONObject, key: String, f: ThemeField): Long? {
                if (!o.has(key) || o.isNull(key)) { err(ThemeErrorCode.Missing, f); return null }
                val c = parseColour(o.opt(key) as? String)
                if (c == null) err(ThemeErrorCode.BadColour, f)
                return c
            }
            val mode = choice("themeMode", ThemeField.Mode, ThemeMode.entries) { it.name }
            val accent = if (sObj.has("accent") && !sObj.isNull("accent")) colour(sObj, "accent", ThemeField.Accent) else null
            val shape = choice("iconShape", ThemeField.IconShape, IconShape.entries) { it.name }
            val tint = choice("iconTint", ThemeField.IconTint, IconTint.entries) { it.name }
            val dock = choice("dockStyle", ThemeField.DockStyle, DockStyle.entries) { it.id }
            var wp: ThemeWallpaper? = null
            if (sObj.has("wallpaper") && !sObj.isNull("wallpaper")) {
                val w = sObj.opt("wallpaper") as? JSONObject
                val kind = (w?.opt("kind") as? String)?.trim()?.lowercase()
                if (w == null) err(ThemeErrorCode.BadWallpaper, ThemeField.Wallpaper)
                else when (kind) {
                    "solid" -> colour(w, "color", ThemeField.Wallpaper)?.let { wp = ThemeWallpaper.Solid(it) }
                    "gradient" -> {
                        val from = colour(w, "from", ThemeField.Wallpaper)
                        val to = colour(w, "to", ThemeField.Wallpaper)
                        val av = if (w.has("angle") && !w.isNull("angle")) w.opt("angle") else GradientSpec.DEFAULT_ANGLE
                        val angle = (av as? Int) ?: (av as? Long)?.takeIf { it in -360..720 }?.toInt()
                        if (angle == null || angle !in 0..360) err(ThemeErrorCode.BadValue, ThemeField.Wallpaper)
                        else if (from != null && to != null) wp = ThemeWallpaper.Gradient(from, to, angle % 360)
                    }
                    else -> err(ThemeErrorCode.BadWallpaper, ThemeField.Wallpaper) // pictures, videos, files, system and live wallpapers are never carried
                }
            }
            settings = ThemeSettings(mode, accent, shape, tint, dock, wp)
            if (errors.isEmpty() && settings.isEmpty) err(ThemeErrorCode.NothingToApply)
        }
        if (errors.isNotEmpty() || name == null) return ThemeParse.Failed(errors.ifEmpty { listOf(ThemeError(ThemeErrorCode.Missing, ThemeField.Name)) })
        return ThemeParse.Ok(ThemePack(name, author, version, settings, formatVersion))
    }

    /** `#RRGGBB` or `#AARRGGBB` (the alpha is ignored: a theme colour is always opaque); null for anything else. */
    fun parseColour(s: String?): Long? {
        val t = s?.trim() ?: return null
        if (t.length != 7 && t.length != 9 || t[0] != '#') return null
        if (!t.drop(1).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return 0xFF000000L or (t.takeLast(6).toLong(16))
    }

    fun colourText(argb: Long): String = "#%06X".format(argb and 0xFFFFFFL)

    fun encode(pack: ThemePack): String {
        val s = pack.settings
        val settings = JSONObject().also { o ->
            s.themeMode?.let { o.put("themeMode", it.name.lowercase()) }
            s.accent?.let { o.put("accent", colourText(it)) }
            s.iconShape?.let { o.put("iconShape", it.name.lowercase()) }
            s.iconTint?.let { o.put("iconTint", it.name.lowercase()) }
            s.dockStyle?.let { o.put("dockStyle", it.id) }
            when (val w = s.wallpaper) {
                is ThemeWallpaper.Solid -> o.put("wallpaper", JSONObject().put("kind", "solid").put("color", colourText(w.color)))
                is ThemeWallpaper.Gradient -> o.put("wallpaper", JSONObject().put("kind", "gradient").put("from", colourText(w.from)).put("to", colourText(w.to)).put("angle", w.angle))
                null -> Unit
            }
        }
        return JSONObject().put("format", FORMAT).put("formatVersion", pack.formatVersion).put("name", pack.name)
            .also { o -> pack.author?.let { o.put("author", it) }; pack.version?.let { o.put("version", it) } }
            .put("settings", settings).toString(2)
    }

    /** A file name for an exported pack: letters, digits and dashes of the theme name, plus the extension. */
    fun fileName(name: String): String {
        val base = name.trim().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").trim('-').replace(Regex("-+"), "-").take(40).ifEmpty { "theme" }
        return "$base.$EXTENSION"
    }
}
