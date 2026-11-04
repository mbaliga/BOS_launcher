package com.bos.sphere.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Hyle theme. The Material scheme is rebuilt from the live [HyleColors] on every composition
 * (no `remember`), so changing [HyleTheme] restyles everything. [darkTheme] is ignored: the
 * palette comes from [HyleTheme.variant].
 */
@Composable
fun SphereTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = if (HyleTheme.isLight) {
        lightColorScheme(
            primary = HyleColors.Violet,
            onPrimary = HyleColors.OnAccent,
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
    } else {
        darkColorScheme(
            primary = HyleColors.Violet,
            onPrimary = HyleColors.OnAccent,
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
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = HyleTypography,
        shapes = HyleShapes,
        content = content,
    )
}
