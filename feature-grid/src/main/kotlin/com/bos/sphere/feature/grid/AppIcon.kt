package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Renders app icons the way the reference shows them: full-bleed rounded squares. Adaptive
 * icons are drawn with their layers expanded to fill the tile (the 72/108 safe zone becomes the
 * whole tile); legacy icons sit on a dark tile. Bitmaps are rasterised off the main thread and
 * kept in a small LRU keyed by app (one shared bitmap per app). Shape, tint and plate come from
 * [LocalIconTheme] and are applied at draw time.
 */
object IconCache {
    private val cache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** HOM-03: tile colour behind a legacy (non-adaptive) icon, from the icon's own colours; absent for adaptive icons. */
    private val plates = java.util.concurrent.ConcurrentHashMap<String, Int>()
    fun plateFor(key: String): Int? = plates[key]

    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, bmp: ImageBitmap) { cache.put(key, bmp) }

    /** Drop everything (e.g. after a package update changed icons). */
    fun clear() = cache.evictAll()

    /** HOM-10: drop only the icons of [keys] (updated or removed apps). */
    fun evict(keys: Set<String>) { keys.forEach { cache.remove(it); plates.remove(it) } }

    internal fun rasterize(drawable: Drawable, px: Int, plateKey: String? = null): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (drawable is AdaptiveIconDrawable) {
            // Layers are 108dp with a 72dp visible window: expand so the window fills the tile.
            val inset = px / 4
            val bounds = android.graphics.Rect(-inset, -inset, px + inset, px + inset)
            drawable.background?.let { it.bounds = bounds; it.draw(canvas) }
            drawable.foreground?.let { it.bounds = bounds; it.draw(canvas) }
        } else {
            val pad = (px * 0.14f).roundToInt()
            drawable.setBounds(pad, pad, px - pad, px - pad)
            drawable.draw(canvas)
            if (plateKey != null) plates[plateKey] = com.bos.sphere.core.catalog.IconPlate.forLegacy(averageColor(bmp))
        }
        return bmp
    }
}

/** Mean colour of the opaque pixels of [bmp], sampled on a coarse grid. */
private fun averageColor(bmp: Bitmap): Int {
    var r = 0L; var g = 0L; var b = 0L; var n = 0L
    val step = (bmp.width / 24).coerceAtLeast(1)
    var y = 0
    while (y < bmp.height) {
        var x = 0
        while (x < bmp.width) {
            val c = bmp.getPixel(x, y)
            if ((c ushr 24) > 200) { r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++ }
            x += step
        }
        y += step
    }
    if (n == 0L) return 0xFF808080.toInt()
    return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
}

/** Edge of the one shared bitmap kept per app; every display size scales this down at draw time. */
private const val ICON_PX = 192

@Composable
fun AppIcon(
    app: AppEntry,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val sources = LocalIconSources.current
    val cacheKey = sources.cacheKey(app)
    // One bitmap per app: a reused slot never shows the previous app's bitmap, and no size or
    // theme change ever re-rasterises.
    var bitmap by remember(app, cacheKey) { mutableStateOf(IconCache.get(cacheKey)) }
    LaunchedEffect(app, cacheKey) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.Default) {
                runCatching {
                    sources.rasterize(app, ICON_PX, cacheKey)?.asImageBitmap()
                }.getOrNull()?.also { IconCache.put(cacheKey, it) }
            }
        }
    }
    val theme = LocalIconTheme.current
    val override = theme.overrides[app.key]
    val filter = remember(theme.tint, theme.accentArgb, override) { theme.filterFor(app.key) }
    val shape = theme.shape.toShape()
    // Sized here so every caller gets a visible icon (a zero-size box drew nothing).
    var box = modifier.size(size)
    if (shape != null) {
        box = box.clip(shape)
        // HOM-03: the plate comes from the icon's own colours (legacy icons only), never a dark tile.
        if (theme.plate) IconCache.plateFor(cacheKey)?.let { box = box.background(androidx.compose.ui.graphics.Color(it), shape) }
    }
    Box(modifier = box) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.Medium,
                colorFilter = filter,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** Icon edge used for compact list rows (drawer search results, pickers). */
internal val SmallIcon = 40.dp
