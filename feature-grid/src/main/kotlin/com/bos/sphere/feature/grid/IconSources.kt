package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.runtime.compositionLocalOf
import com.bos.sphere.core.catalog.IconPackMap
import com.bos.sphere.core.catalog.IconPlate
import com.bos.sphere.core.catalog.IconPolicy
import com.bos.sphere.core.catalog.IconSource
import com.bos.sphere.core.catalog.IconSpec
import com.bos.sphere.core.data.AppEntry

/**
 * HOM-03: where an app's icon comes from. [AppIcon] reads it from [LocalIconSources] and applies
 * the precedence of [IconPolicy]: per-item override, then the active icon pack, then the themed
 * (monochrome) layer when [themed] is on and the app ships one, then the app's own icon.
 * [drawable] loads a drawable out of an installed pack (package, drawable name).
 */
class IconSources(
    val overrides: Map<String, String> = emptyMap(),
    val pack: IconPackMap? = null,
    val themed: Boolean = false,
    val dark: Boolean = true,
    val accentArgb: Int = 0xFF8E7BFF.toInt(),
    val drawable: (String, String) -> Drawable? = { _, _ -> null },
) {
    private fun component(app: AppEntry) = app.componentName.packageName + "/" + app.componentName.className

    /** The part of an app's bitmap-cache key that depends on the sources; empty when nothing applies. */
    fun suffix(app: AppEntry): String {
        overrides[app.key]?.let { return "|o:$it" }
        pack?.drawableFor(component(app))?.let { return "|p:${pack.packageName}/$it" }
        return if (themed) "|t${if (dark) "d" else "l"}${accentArgb}" else ""
    }

    fun cacheKey(app: AppEntry): String = app.key + suffix(app)

    internal fun rasterize(app: AppEntry, px: Int, cacheKey: String): Bitmap? {
        val own = app.loadIcon()
        val mono = monochromeOf(own)
        val source = IconPolicy.resolve(app.key, component(app), overrides, pack, themed, mono != null, own is AdaptiveIconDrawable)
        return when (source) {
            is IconSource.Override -> IconSpec.decode(source.spec)?.let { (p, n) -> drawable(p, n) }?.let { rasterizeFull(it, px) }
                ?: own?.let { IconCache.rasterize(it, px, cacheKey) }
            is IconSource.Pack -> drawable(source.packName, source.drawable)?.let { rasterizeFull(it, px) }
                ?: own?.let { IconCache.rasterize(it, px, cacheKey) }
            IconSource.Themed -> {
                val (fg, bg) = IconPlate.themed(accentArgb, dark)
                rasterizeThemed(mono!!, px, fg, bg)
            }
            else -> own?.let { IconCache.rasterize(it, px, cacheKey) }
        }
    }

    companion object {
        fun monochromeOf(d: Drawable?): Drawable? =
            if (Build.VERSION.SDK_INT >= 33 && d is AdaptiveIconDrawable) d.monochrome else null

        /** A pack drawable fills the tile (no padding, no plate): the pack designed its own tile. */
        fun rasterizeFull(d: Drawable, px: Int): Bitmap {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            if (d is AdaptiveIconDrawable) {
                val inset = px / 4
                val b = android.graphics.Rect(-inset, -inset, px + inset, px + inset)
                d.background?.let { it.bounds = b; it.draw(c) }
                d.foreground?.let { it.bounds = b; it.draw(c) }
            } else {
                d.setBounds(0, 0, px, px)
                d.draw(c)
            }
            return bmp
        }

        /** The monochrome layer in [fg] on a [bg] tile, expanded like an adaptive icon. */
        fun rasterizeThemed(mono: Drawable, px: Int, fg: Int, bg: Int): Bitmap {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            ColorDrawable(bg).apply { setBounds(0, 0, px, px); draw(c) }
            // Draw the layer on its own, then paint its coverage in [fg]: independent of whether the drawable honours a colour filter.
            val mask = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val layer = mono.mutate()
            val inset = px / 4
            layer.bounds = android.graphics.Rect(-inset, -inset, px + inset, px + inset)
            layer.draw(Canvas(mask))
            c.drawBitmap(mask, 0f, 0f, android.graphics.Paint().apply { colorFilter = PorterDuffColorFilter(fg, PorterDuff.Mode.SRC_IN) })
            return bmp
        }
    }
}

val LocalIconSources = compositionLocalOf { IconSources() }
