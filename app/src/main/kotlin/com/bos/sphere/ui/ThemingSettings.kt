package com.bos.sphere.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bos.sphere.core.data.ThemeMode
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.PaletteSource
import com.bos.sphere.core.design.ThemeResolver
import com.bos.sphere.core.design.ThemeScaleMath
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.core.design.ThemingStore
import com.bos.sphere.core.design.WallpaperColorReader
import com.bos.sphere.core.design.WallpaperSample
import com.bos.sphere.feature.grid.ChipRow
import com.bos.sphere.feature.grid.LabeledSlider
import com.bos.sphere.feature.grid.SectionHeader
import kotlin.math.roundToInt

/**
 * CFG-10. Call once under the home: re-applies the palette from the wallpaper when the user chose that, and keeps the
 * density and type scale live. With the fixed palette it changes nothing the home already applied.
 */
@Composable
fun ApplyTheming(themeMode: ThemeMode, accent: Long) {
    val ctx = LocalContext.current
    remember(ctx) { ThemingStore.init(ctx); true }
    val s by ThemingStore.settings.collectAsStateWithLifecycle()
    var sample by remember { mutableStateOf<WallpaperSample?>(WallpaperColorReader.read(ctx)) }
    // The wallpaper can change while Liftoff is open; the system tells us.
    DisposableEffect(ctx, s.source) {
        val wm = ctx.getSystemService(android.app.WallpaperManager::class.java)
        val l = android.app.WallpaperManager.OnColorsChangedListener { _, _ -> sample = WallpaperColorReader.read(ctx) }
        if (s.source == PaletteSource.Wallpaper) runCatching { wm.addOnColorsChangedListener(l, android.os.Handler(android.os.Looper.getMainLooper())) }
        onDispose { runCatching { wm.removeOnColorsChangedListener(l) } }
    }
    SideEffect {
        if (s.source == PaletteSource.Wallpaper) {
            val fixed = when (themeMode) { ThemeMode.Dark -> ThemeVariant.Dark; ThemeMode.Amoled -> ThemeVariant.Amoled; ThemeMode.Light -> ThemeVariant.Light }
            val d = ThemeResolver.resolve(fixed, accent, s.source, sample)
            HyleTheme.apply(d.variant, d.accentArgb)
        }
    }
}

/** The Ground Control page for the wallpaper palette, density and text size. */
@Composable
fun ThemingPane() {
    val ctx = LocalContext.current
    remember(ctx) { ThemingStore.init(ctx); true }
    val s by ThemingStore.settings.collectAsStateWithLifecycle()
    Column {
        SectionHeader(L10n.tr("Palette"))
        ChipRow(PaletteSource.entries, s.source, { L10n.tr(it.label) }) { v -> ThemingStore.update { it.copy(source = v) } }
        Text(L10n.tr("From my wallpaper picks light or dark and an accent from your wallpaper's colours."), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        SectionHeader(L10n.tr("Size"))
        LabeledSlider(L10n.tr("Density"), s.density, ThemeScaleMath.MIN_DENSITY..ThemeScaleMath.MAX_DENSITY, "${(s.density * 100).roundToInt()}%") { v ->
            ThemingStore.update { it.copy(density = (v * 20).roundToInt() / 20f) }
        }
        LabeledSlider(L10n.tr("Text size"), s.type, ThemeScaleMath.MIN_TYPE..ThemeScaleMath.MAX_TYPE, "${(s.type * 100).roundToInt()}%") { v ->
            ThemingStore.update { it.copy(type = (v * 20).roundToInt() / 20f) }
        }
        Text(L10n.tr("Density changes the size of everything; text size changes only the words."), color = HyleColors.InkDim, fontSize = 12.sp)
    }
}
