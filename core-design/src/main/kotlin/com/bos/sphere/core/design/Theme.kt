package com.bos.sphere.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Hyle is a dark, glass-on-dot-grid system; there is no light variant. The [darkTheme]
 * parameter exists for previews/tests but resolves to the dark scheme either way for now.
 * Material You / Monet dynamic color (M1+) will be blended into this scheme behind a flag.
 */
private val HyleColorScheme = darkColorScheme(
    primary = HyleColors.Violet,
    onPrimary = HyleColors.Background,
    primaryContainer = HyleColors.Violet,
    secondary = HyleColors.VioletSoft,
    background = HyleColors.Background,
    onBackground = HyleColors.Ink,
    surface = HyleColors.Surface,
    onSurface = HyleColors.InkBright,
    surfaceVariant = HyleColors.SurfaceTranslucent,
    onSurfaceVariant = HyleColors.InkDim,
    outline = HyleColors.Hairline,
)

@Composable
fun SphereTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = HyleColorScheme,
        typography = HyleTypography,
        shapes = HyleShapes,
        content = content,
    )
}
