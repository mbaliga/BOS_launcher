package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
 * kept in a small LRU keyed by app + pixel size.
 */
object IconCache {
    private val cache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, bmp: ImageBitmap) { cache.put(key, bmp) }

    /** Drop everything (e.g. after a package update changed icons). */
    fun clear() = cache.evictAll()

    internal fun rasterize(drawable: Drawable, px: Int): Bitmap {
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
        }
        return bmp
    }
}

@Composable
fun AppIcon(
    app: AppEntry,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val cacheKey = "${app.key}@$px"
    // Keyed on app + size so a reused slot never shows the previous app's bitmap.
    var bitmap by remember(app, cacheKey) { mutableStateOf(IconCache.get(cacheKey)) }
    LaunchedEffect(app, cacheKey) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.Default) {
                runCatching {
                    app.loadIcon()?.let { IconCache.rasterize(it, px).asImageBitmap() }
                }.getOrNull()?.also { IconCache.put(cacheKey, it) }
            }
        }
    }
    val shape = RoundedCornerShape(size * 0.22f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(HyleColors.Surface, shape),
    ) {
        bitmap?.let {
            Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.matchParentSize())
        }
    }
}

/** Icon edge used for compact list rows (drawer search results, pickers). */
internal val SmallIcon = 40.dp
