package com.bos.sphere.feature.grid

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.bos.sphere.core.data.IconShape
import com.bos.sphere.core.data.IconTint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * How app icons are drawn. Provide it with `CompositionLocalProvider(LocalIconTheme provides ...)`;
 * [AppIcon] applies it at draw time (clip + colour filter), so changing it never re-rasterises.
 * [overrides] maps `AppEntry.key` to an ARGB colour that wins over [tint].
 */
data class IconTheme(
    val shape: IconShape,
    val tint: IconTint,
    val plate: Boolean,
    val accentArgb: Long,
    val overrides: Map<String, Long>,
)

val LocalIconTheme = compositionLocalOf {
    IconTheme(IconShape.Rounded, IconTint.None, true, 0xFF8E7BFF, emptyMap())
}

/** Superellipse (n = 4) "squircle": rounder than a rounded square, squarer than a circle. */
internal object SquircleShape : Shape {
    private const val STEPS = 72
    private const val N = 4.0

    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val path = Path()
        for (i in 0..STEPS) {
            val t = 2.0 * PI * i / STEPS
            val c = cos(t)
            val s = sin(t)
            val x = cx + cx * (sign(c) * abs(c).pow(2.0 / N)).toFloat()
            val y = cy + cy * (sign(s) * abs(s).pow(2.0 / N)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** Duotone: maps the icon's luminance onto a dark-to-light ramp of [argb]; alpha is preserved. */
internal fun duotoneFilter(argb: Long): ColorFilter {
    val c = Color(argb.toInt())
    val dark = lerp(c, Color.Black, 0.78f)
    val light = lerp(c, Color.White, 0.82f)
    fun row(lo: Float, hi: Float): FloatArray {
        val d = hi - lo
        return floatArrayOf(0.2126f * d, 0.7152f * d, 0.0722f * d, 0f, lo * 255f)
    }
    val r = row(dark.red, light.red)
    val g = row(dark.green, light.green)
    val b = row(dark.blue, light.blue)
    val m = ColorMatrix(
        floatArrayOf(
            r[0], r[1], r[2], r[3], r[4],
            g[0], g[1], g[2], g[3], g[4],
            b[0], b[1], b[2], b[3], b[4],
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    return ColorFilter.colorMatrix(m)
}

internal fun monoFilter(): ColorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** The filter [theme] implies for the app with [key], or null for untouched icons. */
internal fun IconTheme.filterFor(key: String): ColorFilter? {
    overrides[key]?.let { return duotoneFilter(it) }
    return when (tint) {
        IconTint.None -> null
        IconTint.Accent -> duotoneFilter(accentArgb)
        IconTint.Mono -> monoFilter()
    }
}

internal fun IconShape.toShape(): Shape? = when (this) {
    IconShape.Original -> null
    IconShape.Rounded -> RoundedCornerShape(percent = 22)
    IconShape.Squircle -> SquircleShape
    IconShape.Circle -> CircleShape
    IconShape.Square -> RectangleShape
}
