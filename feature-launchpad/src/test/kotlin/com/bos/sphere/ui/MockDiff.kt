package com.bos.sphere.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Wave 28 (commsLayout): the screenshot diff helper. [sideBySide] writes `mock | shot | overlay` (mock ink red, shot ink cyan, both white) to
 * `build/screenshots/<name>-diff.png`, and [ink] measures the bounding box of what differs from the background in a region, so a test can compare a
 * rendered element with the mock-up's own pixels. Both pictures are at 1 image pixel = 1 device pixel (411 dp wide, 352 dpi).
 */
object MockDiff {
    /** The owner's mock-ups, kept in the repository next to the design notes. */
    fun mock(name: String): Bitmap {
        val dir = File(System.getProperty("user.dir"), "../docs/design/comms")
        return BitmapFactory.decodeFile(File(dir, name).path) ?: error("missing mock-up $name")
    }

    private fun far(p: Int, bg: Int, threshold: Int): Boolean {
        val d = Math.abs(((p shr 16) and 255) - ((bg shr 16) and 255)) + Math.abs(((p shr 8) and 255) - ((bg shr 8) and 255)) + Math.abs((p and 255) - (bg and 255))
        return d > threshold
    }

    /** Bounding box (left, top, right, bottom, inclusive) of the pixels in the region that differ from [bg]; null when there are none. */
    fun ink(bmp: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int, bg: Int, threshold: Int = 40): IntArray? {
        var l = Int.MAX_VALUE; var t = Int.MAX_VALUE; var r = -1; var b = -1
        for (y in y0.coerceAtLeast(0)..y1.coerceAtMost(bmp.height - 1)) for (x in x0.coerceAtLeast(0)..x1.coerceAtMost(bmp.width - 1)) {
            if (far(bmp.getPixel(x, y), bg, threshold)) { if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y }
        }
        return if (r < 0) null else intArrayOf(l, t, r, b)
    }

    /** The rows of the region (inclusive) in which the whole span x0..x1 differs from [bg]: a hairline is such a row. */
    fun lineRows(bmp: Bitmap, x0: Int, x1: Int, y0: Int, y1: Int, bg: Int, threshold: Int = 8): List<Int> =
        (y0..y1.coerceAtMost(bmp.height - 1)).filter { y -> (x0..x1).all { x -> far(bmp.getPixel(x, y), bg, threshold) } }

    /** The columns of the region (inclusive) in which the whole span y0..y1 differs from [bg]. */
    fun lineCols(bmp: Bitmap, x0: Int, x1: Int, y0: Int, y1: Int, bg: Int, threshold: Int = 8): List<Int> =
        (x0..x1.coerceAtMost(bmp.width - 1)).filter { x -> (y0..y1).all { y -> far(bmp.getPixel(x, y), bg, threshold) } }

    fun sideBySide(mock: Bitmap, shot: Bitmap, out: File) {
        val w = minOf(mock.width, shot.width); val h = minOf(mock.height, shot.height)
        val bgM = mock.getPixel(5, h / 2); val bgS = shot.getPixel(5, h / 2)
        val res = Bitmap.createBitmap(w * 3 + 20, h, Bitmap.Config.ARGB_8888)
        res.eraseColor(0xFF282828.toInt())
        for (y in 0 until h) for (x in 0 until w) {
            val m = mock.getPixel(x, y); val s = shot.getPixel(x, y)
            res.setPixel(x, y, m); res.setPixel(w + 10 + x, y, s)
            val im = far(m, bgM, 60); val isn = far(s, bgS, 60)
            res.setPixel(2 * w + 20 + x, y, when { im && isn -> 0xFFFFFFFF.toInt(); im -> 0xFFFF3C3C.toInt(); isn -> 0xFF3CDCFF.toInt(); else -> 0xFF000000.toInt() })
        }
        out.parentFile?.mkdirs()
        out.outputStream().use { res.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
