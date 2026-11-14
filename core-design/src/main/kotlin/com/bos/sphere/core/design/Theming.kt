package com.bos.sphere.core.design

import android.app.WallpaperManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

// CFG-10: light, dark and wallpaper-derived palettes, plus density and type scale, all resolved to the shared
// tokens in HyleTheme / HyleColors. Nothing here introduces a colour of its own beyond the derived accent.

/** Where the accent and light/dark choice come from. */
enum class PaletteSource(val label: String) { Fixed("Chosen here"), Wallpaper("From my wallpaper") }

/** The user's theming choices, persisted and exportable. */
data class ThemingSettings(
    val source: PaletteSource = PaletteSource.Fixed,
    /** Multiplies every dp: 1 = normal. */
    val density: Float = 1f,
    /** Multiplies text only: 1 = normal. */
    val type: Float = 1f,
) {
    fun toJson(): String = JSONObject().put("source", source.name).put("density", density.toDouble()).put("type", type.toDouble()).toString()

    companion object {
        fun fromJson(json: String?): ThemingSettings {
            if (json.isNullOrBlank()) return ThemingSettings()
            return runCatching {
                val o = JSONObject(json)
                ThemingSettings(
                    runCatching { PaletteSource.valueOf(o.optString("source")) }.getOrDefault(PaletteSource.Fixed),
                    ThemeScaleMath.clampDensity(o.optDouble("density", 1.0).toFloat()),
                    ThemeScaleMath.clampType(o.optDouble("type", 1.0).toFloat()),
                )
            }.getOrDefault(ThemingSettings())
        }
    }
}

object ThemeScaleMath {
    const val MIN_DENSITY = 0.85f; const val MAX_DENSITY = 1.25f
    const val MIN_TYPE = 0.85f; const val MAX_TYPE = 1.4f

    fun clampDensity(v: Float) = if (v.isNaN()) 1f else v.coerceIn(MIN_DENSITY, MAX_DENSITY)
    fun clampType(v: Float) = if (v.isNaN()) 1f else v.coerceIn(MIN_TYPE, MAX_TYPE)

    /**
     * The (density, fontScale) multipliers to apply on top of the system's. Text pixels are sp x density x fontScale, so
     * dividing the density factor out of the font factor makes the type scale independent of the density scale.
     */
    fun factors(density: Float, type: Float): Pair<Float, Float> {
        val d = clampDensity(density); val t = clampType(type)
        return d to t / d
    }
}

/** Live scale state read by [SphereTheme]. */
object HyleScale {
    var density: Float by mutableStateOf(1f)
        private set
    var type: Float by mutableStateOf(1f)
        private set

    fun apply(s: ThemingSettings) { density = ThemeScaleMath.clampDensity(s.density); type = ThemeScaleMath.clampType(s.type) }
}

/** What the wallpaper reports (from WallpaperColors), as plain ARGB ints so the derivation is testable on the JVM. */
data class WallpaperSample(val primary: Int, val secondary: Int?, val tertiary: Int?, val supportsDarkText: Boolean)

data class DerivedTheme(val variant: ThemeVariant, val accentArgb: Long)

object WallpaperPalette {
    private fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4) }
    fun luminance(argb: Int): Double = 0.2126 * lin((argb shr 16) and 255) + 0.7152 * lin((argb shr 8) and 255) + 0.0722 * lin(argb and 255)
    fun contrast(a: Int, b: Int): Double { val la = luminance(a); val lb = luminance(b); return (max(la, lb) + 0.05) / (min(la, lb) + 0.05) }

    private fun hsv(argb: Int): FloatArray {
        val r = ((argb shr 16) and 255) / 255f; val g = ((argb shr 8) and 255) / 255f; val b = (argb and 255) / 255f
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> ((g - b) / d % 6f) * 60f
            mx == g -> ((b - r) / d + 2f) * 60f
            else -> ((r - g) / d + 4f) * 60f
        }.let { if (it < 0) it + 360f else it }
        return floatArrayOf(h, if (mx == 0f) 0f else d / mx, mx)
    }

    private fun fromHsv(h: Float, s: Float, v: Float): Int {
        val c = v * s; val x = c * (1 - Math.abs((h / 60f) % 2 - 1)); val m = v - c
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f); h < 120 -> Triple(x, c, 0f); h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c); h < 300 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    /** Dark UI backgrounds to measure the accent against (the shared tokens' values). */
    private const val DARK_BG = 0xFF0A0B10.toInt()
    private const val LIGHT_BG = 0xFFF3F4F8.toInt()

    /**
     * A palette from [s]: a bright wallpaper gives the Light variant, otherwise Dark. The accent is the most vivid of the
     * wallpaper's colours, nudged in brightness until it keeps [MIN_CONTRAST] against the chosen background.
     */
    fun derive(s: WallpaperSample): DerivedTheme {
        val light = s.supportsDarkText || luminance(s.primary) > 0.55
        val bg = if (light) LIGHT_BG else DARK_BG
        val pick = listOfNotNull(s.primary, s.secondary, s.tertiary).maxByOrNull { hsv(it)[1] * hsv(it)[2] } ?: s.primary
        val (h, sat0, v0) = hsv(pick).let { Triple(it[0], max(it[1], 0.45f), it[2]) }
        var v = v0; var sat = sat0
        var c = fromHsv(h, sat, v)
        var guard = 0
        while (contrast(c, bg) < MIN_CONTRAST && guard++ < 120) {
            // Brighten (dark UI) or darken (light UI) first; once that runs out, wash the colour towards white or deepen it.
            if (light) { if (v > 0.15f) v -= 0.03f else sat = min(1f, sat + 0.05f) }
            else { if (v < 1f) v = min(1f, v + 0.03f) else sat = max(0f, sat - 0.05f) }
            c = fromHsv(h, sat, v)
        }
        return DerivedTheme(if (light) ThemeVariant.Light else ThemeVariant.Dark, c.toLong() and 0xFFFFFFFFL)
    }

    const val MIN_CONTRAST = 3.0
}

/** Resolves the palette the user asked for: their fixed choice, or the wallpaper-derived one when it can be read. */
object ThemeResolver {
    fun resolve(fixed: ThemeVariant, fixedAccent: Long, source: PaletteSource, wallpaper: WallpaperSample?): DerivedTheme =
        if (source == PaletteSource.Wallpaper && wallpaper != null) WallpaperPalette.derive(wallpaper) else DerivedTheme(fixed, fixedAccent)
}

/** Reads the system wallpaper's colours with the public API (API 27+). Null when the device does not report them. */
object WallpaperColorReader {
    fun read(ctx: Context): WallpaperSample? = runCatching {
        val c = ctx.getSystemService(WallpaperManager::class.java).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return null
        WallpaperSample(
            c.primaryColor.toArgb(), c.secondaryColor?.toArgb(), c.tertiaryColor?.toArgb(),
            (c.colorHints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0,
        )
    }.getOrNull()
}

/** Persistent theming choices. Call [init] once; collect [settings]. */
object ThemingStore {
    private const val FILE = "theming"
    private var sp: android.content.SharedPreferences? = null
    private val _s = MutableStateFlow(ThemingSettings())
    val settings: StateFlow<ThemingSettings> = _s.asStateFlow()

    @Synchronized fun init(ctx: Context) {
        if (sp != null) return
        sp = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        _s.value = ThemingSettings.fromJson(sp?.getString("settings", null))
        HyleScale.apply(_s.value)
    }

    fun update(f: (ThemingSettings) -> ThemingSettings) {
        val n = f(_s.value)
        _s.value = n
        HyleScale.apply(n)
        sp?.edit()?.putString("settings", n.toJson())?.apply()
    }
}
