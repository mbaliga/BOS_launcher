package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where a picture background comes from: the bundled Horizon image, or a file chosen by the user. */
sealed interface PictureSource {
    /** The bundled warm-planet-horizon image (`R.drawable.search_backdrop`). */
    data object Horizon : PictureSource

    /** An image file on disk, decoded at screen size off the main thread; blank if it cannot be read. */
    data class File(val path: String) : PictureSource
}

internal object PictureMath {
    /** Largest power-of-two sample size that still leaves the decoded image covering [reqW] x [reqH]. */
    fun sampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        if (srcW <= 0 || srcH <= 0 || reqW <= 0 || reqH <= 0) return 1
        var s = 1
        while (srcW / (s * 2) >= reqW && srcH / (s * 2) >= reqH) s *= 2
        return s
    }

    fun clampDim(dim: Float): Float = if (dim.isNaN()) 0f else dim.coerceIn(0f, 1f)

    /** Rotation in degrees for an EXIF orientation tag (1 = upright). */
    fun exifRotation(orientation: Int): Int = when (orientation) {
        6 -> 90   // ORIENTATION_ROTATE_90
        3 -> 180  // ORIENTATION_ROTATE_180
        8 -> 270  // ORIENTATION_ROTATE_270
        else -> 0
    }

    fun decode(path: String, reqW: Int, reqH: Int): ImageBitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val rot = try {
            exifRotation(android.media.ExifInterface(path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1))
        } catch (_: Throwable) { 0 }
        // With a quarter turn the width / height swap.
        val (rw, rh) = if (rot == 90 || rot == 270) reqH to reqW else reqW to reqH
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, rw, rh) }
        val raw = BitmapFactory.decodeFile(path, opts)
        val bmp: Bitmap? = if (raw != null && rot != 0) {
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
        } else raw
        bmp?.asImageBitmap()
    } catch (_: Throwable) { null }
}

/**
 * A picture filling its bounds with [ContentScale.Crop], darkened by a black scrim of [dim] (0..1).
 * A [PictureSource.File] is decoded at the layout size off the main thread and remembered by
 * path + size; while loading, or if it cannot be decoded, the area stays black.
 */
@Composable
fun PictureBackground(source: PictureSource, dim: Float, modifier: Modifier = Modifier) {
    val scrim = PictureMath.clampDim(dim)
    Box(modifier.fillMaxSize().clipToBounds()) {
        when (source) {
            PictureSource.Horizon -> Image(
                painter = painterResource(R.drawable.search_backdrop),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            is PictureSource.File -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = constraints.maxWidth.coerceAtLeast(1)
                val h = constraints.maxHeight.coerceAtLeast(1)
                val bmp: ImageBitmap? by produceState<ImageBitmap?>(null, source.path, w, h) {
                    value = withContext(Dispatchers.IO) { PictureMath.decode(source.path, w, h) }
                }
                val b = bmp
                if (b != null) {
                    Image(bitmap = b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black) }
                }
            }
        }
        if (scrim > 0f) Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = scrim)) }
    }
}
