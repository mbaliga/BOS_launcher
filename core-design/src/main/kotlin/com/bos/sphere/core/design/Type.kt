package com.bos.sphere.core.design

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Hyle type ramp (§4): light weights for the clock hero / focal labels; tabular figures
 * for time and temperatures. The family is the user's UI font ([HyleFont], wave 52): every style of the ramp carries it, so every
 * `Text` below [SphereTheme] (labels, titles, Radar, sheets, the Glance clock text, icon labels) picks it up through the theme.
 */
fun hyleTypography(family: FontFamily): Typography = Typography(
    // Clock hero (portrait) — very light, large.
    displayLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Thin,
        fontSize = 76.sp,
        letterSpacing = 0.01.em,
    ),
    // Widget-tile heroes (clock / calendar number).
    headlineMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Light,
        fontSize = 27.sp,
        letterSpacing = 0.02.em,
    ),
    // App labels on the equator.
    bodyLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = 0.02.em,
    ),
    // Chrome / overline labels (weather, edge bars).
    labelSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.14.em,
    ),
)

/** The ramp in the platform default family (what "System default" and a missing font file give). */
val HyleTypography: Typography = hyleTypography(FontFamily.Default)

/**
 * Wave 52 (settingsFont), CFG-21: the fonts the launcher ships for its own UI text, all under the SIL Open Font License (texts in
 * `docs/licenses`). The files live in ONE place, `core-design/src/main/res/font`, named by [Entry.files]; a file that is missing (or a
 * device that cannot load it) makes the family fall back to the platform sans, never a crash and never an empty label. The pick is stored as
 * the [Entry.id] string (in `LauncherPrefs.uiFont`, so the settings file and Android backup carry it).
 */
object FontCatalog {
    /** One bundled file: its resource name (`R.font.<name>`) and, for a static file, the weight it is; null = a variable file (the `wght` axis). */
    data class File(val resName: String, val staticWeight: Int? = null)

    data class Entry(val id: String, val label: String, val files: List<File>)

    /** Space Grotesk: the default UI font and the face of every title. */
    const val SPACE_GROTESK = "space_grotesk"
    /** The platform's own sans (no bundled file). */
    const val SYSTEM = "system"
    const val DEFAULT = SPACE_GROTESK

    val entries: List<Entry> = listOf(
        Entry(SPACE_GROTESK, "Space Grotesk", listOf(File("space_grotesk"))),
        Entry(SYSTEM, "System default", emptyList()),
        Entry("inter", "Inter", listOf(File("inter"))),
        Entry("manrope", "Manrope", listOf(File("manrope"))),
        Entry("dm_sans", "DM Sans", listOf(File("dm_sans"))),
        Entry("jetbrains_mono", "JetBrains Mono", listOf(File("jetbrains_mono"))),
        Entry("atkinson", "Atkinson Hyperlegible", listOf(File("atkinson_hyperlegible_regular", 400), File("atkinson_hyperlegible_bold", 700))),
        Entry("lexend", "Lexend", listOf(File("lexend"))),
    )

    val ids: List<String> get() = entries.map { it.id }

    /** A stored id as a known id: anything unknown (an old build, a hand-edited file) is the default. */
    fun normalize(id: String?): String = entries.firstOrNull { it.id == id }?.id ?: DEFAULT

    fun entry(id: String?): Entry = entries.first { it.id == normalize(id) }

    /** Weights asked of a variable file; the closest one serves every other weight. */
    val VARIABLE_WEIGHTS = listOf(300, 400, 500, 600, 700)

    /**
     * The family of [id]. [fileExists] says whether a resource name is in the app (the build can lack a file); every file of the entry must
     * exist, otherwise the platform sans is returned. [font] builds the Compose font of one file at one weight.
     */
    fun family(id: String?, fileExists: (String) -> Boolean, font: (File, Int) -> Font): FontFamily {
        val e = entry(id)
        if (e.files.isEmpty() || e.files.any { !fileExists(it.resName) }) return FontFamily.Default
        return FontFamily(
            e.files.flatMap { f ->
                if (f.staticWeight != null) listOf(font(f, f.staticWeight)) else VARIABLE_WEIGHTS.map { font(f, it) }
            },
        )
    }

    /** The family from the app's resources (`res/font`, looked up by name so a missing file is a fallback, not a build failure). */
    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    fun family(ctx: Context, id: String?): FontFamily {
        val res = ctx.resources
        val pkg = ctx.packageName
        fun resId(name: String) = res.getIdentifier(name, "font", pkg)
        return family(id, { resId(it) != 0 }) { f, w ->
            Font(
                resId = resId(f.resName),
                weight = FontWeight(w),
                variationSettings = if (f.staticWeight == null) FontVariation.Settings(FontVariation.weight(w)) else FontVariation.Settings(),
            )
        }
    }
}

/** The live UI font choice read by [SphereTheme]; [ThemingStore] and the home apply it from the preference. */
object HyleFont {
    var id: String by mutableStateOf(FontCatalog.DEFAULT)
        private set

    fun apply(id: String?) { this.id = FontCatalog.normalize(id) }
}

/** The family of the chosen UI font (remembered per font). */
@Composable
fun rememberUiFontFamily(): FontFamily {
    val ctx = LocalContext.current
    val id = HyleFont.id
    return remember(ctx, id) { FontCatalog.family(ctx, id) }
}

/** The family of every title: Space Grotesk whatever the UI font is (the owner's ask: titles in caps, in Space Grotesk). */
@Composable
fun rememberTitleFontFamily(): FontFamily {
    val ctx = LocalContext.current
    return remember(ctx) { FontCatalog.family(ctx, FontCatalog.SPACE_GROTESK) }
}

/** Titles are shown in capitals, with the locale's own rules (German sharp s, Turkish dotted i); scripts without case (Arabic, Hindi, CJK) are unchanged. */
fun titleCaps(text: String, locale: java.util.Locale = java.util.Locale.forLanguageTag(L10n.effectiveLanguage())): String = text.uppercase(locale)
