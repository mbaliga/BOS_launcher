package com.bos.sphere.feature.grid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.HomeGesture

/**
 * Hand-drawn 24-unit line glyphs for corner actions (bold, round-capped strokes like the
 * reference's corner icons). No icon-font dependency.
 */
object Glyphs {
    private fun glyph(name: String, fill: Boolean = false, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = if (fill) SolidColor(Color.White) else null,
                stroke = if (fill) null else SolidColor(Color.White),
                strokeLineWidth = 2.1f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
        close()
    }

    private fun PathBuilder.rrect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
        moveTo(l + rad, t); lineTo(r - rad, t); quadTo(r, t, r, t + rad)
        lineTo(r, b - rad); quadTo(r, b, r - rad, b)
        lineTo(l + rad, b); quadTo(l, b, l, b - rad)
        lineTo(l, t + rad); quadTo(l, t, l + rad, t); close()
    }

    val Music = glyph("music") {
        circle(7f, 18f, 2.6f); circle(17f, 16f, 2.6f)
        moveTo(9.6f, 18f); lineTo(9.6f, 6.5f); lineTo(19.6f, 4.5f); lineTo(19.6f, 16f)
        moveTo(9.6f, 9.5f); lineTo(19.6f, 7.5f)
    }
    val Camera = glyph("camera") {
        rrect(3f, 7f, 21f, 19f, 2.5f)
        moveTo(8f, 7f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(16f, 7f)
        circle(12f, 13f, 3.4f)
        moveTo(17.8f, 10f); lineTo(17.9f, 10f)
    }
    val Search = glyph("search") {
        circle(10.5f, 10.5f, 6.5f)
        moveTo(15.4f, 15.4f); lineTo(20.5f, 20.5f)
    }
    val Apps = glyph("apps", fill = true) {
        rrect(3f, 3f, 11f, 11f, 1.2f); rrect(13f, 3f, 21f, 11f, 1.2f)
        rrect(3f, 13f, 11f, 21f, 1.2f); rrect(13f, 13f, 21f, 21f, 1.2f)
    }
    val Bell = glyph("bell") {
        moveTo(6f, 16.5f); lineTo(6f, 11f); curveTo(6f, 7.5f, 8.7f, 5f, 12f, 5f); curveTo(15.3f, 5f, 18f, 7.5f, 18f, 11f)
        lineTo(18f, 16.5f); lineTo(19.5f, 18f); lineTo(4.5f, 18f); close()
        moveTo(10f, 20.5f); lineTo(14f, 20.5f)
        moveTo(12f, 3f); lineTo(12f, 5f)
    }
    val Sliders = glyph("sliders") {
        moveTo(4f, 7f); lineTo(20f, 7f); moveTo(4f, 17f); lineTo(20f, 17f)
        circle(9f, 7f, 2.2f); circle(15f, 17f, 2.2f)
    }
    val Phone = glyph("phone") {
        moveTo(6.5f, 3.5f); lineTo(9.5f, 3.5f); lineTo(11f, 8f); lineTo(8.8f, 9.6f)
        curveTo(9.9f, 11.9f, 12.1f, 14.1f, 14.4f, 15.2f)
        lineTo(16f, 13f); lineTo(20.5f, 14.5f); lineTo(20.5f, 17.5f)
        curveTo(20.5f, 19.2f, 19.2f, 20.5f, 17.5f, 20.5f)
        curveTo(10f, 20f, 4f, 14f, 3.5f, 6.5f)
        curveTo(3.5f, 4.8f, 4.8f, 3.5f, 6.5f, 3.5f); close()
    }
    val Message = glyph("message") {
        moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 16f); lineTo(10f, 16f); lineTo(5.5f, 20f); lineTo(5.5f, 16f)
        lineTo(4f, 16f); close()
    }
    val Globe = glyph("globe") {
        circle(12f, 12f, 8.5f)
        moveTo(3.5f, 12f); lineTo(20.5f, 12f)
        moveTo(12f, 3.5f); curveTo(8f, 8f, 8f, 16f, 12f, 20.5f)
        moveTo(12f, 3.5f); curveTo(16f, 8f, 16f, 16f, 12f, 20.5f)
    }
    val Mail = glyph("mail") {
        rrect(3f, 5.5f, 21f, 18.5f, 2f)
        moveTo(3.5f, 6.5f); lineTo(12f, 13f); lineTo(20.5f, 6.5f)
    }
    val Map = glyph("map") {
        moveTo(12f, 21f); curveTo(12f, 21f, 5f, 14.5f, 5f, 9.5f)
        curveTo(5f, 5.6f, 8.1f, 2.8f, 12f, 2.8f); curveTo(15.9f, 2.8f, 19f, 5.6f, 19f, 9.5f)
        curveTo(19f, 14.5f, 12f, 21f, 12f, 21f); close()
        circle(12f, 9.5f, 2.5f)
    }
    val Calendar = glyph("calendar") {
        rrect(3.5f, 5f, 20.5f, 20.5f, 2f)
        moveTo(3.5f, 10f); lineTo(20.5f, 10f)
        moveTo(8f, 3f); lineTo(8f, 6.5f); moveTo(16f, 3f); lineTo(16f, 6.5f)
    }
    val Clock = glyph("clock") {
        circle(12f, 12f, 8.5f)
        moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f)
    }
    val Image = glyph("image") {
        rrect(3f, 4.5f, 21f, 19.5f, 2f)
        moveTo(3.5f, 17f); lineTo(9f, 11.5f); lineTo(13f, 15.5f); lineTo(15.5f, 13f); lineTo(20.5f, 18f)
        circle(16f, 8.8f, 1.6f)
    }
    val Person = glyph("person") {
        circle(12f, 8f, 3.8f)
        moveTo(4.5f, 20.5f); curveTo(5f, 16.5f, 8f, 14f, 12f, 14f); curveTo(16f, 14f, 19f, 16.5f, 19.5f, 20.5f)
    }
    val Calculator = glyph("calculator") {
        rrect(5f, 2.8f, 19f, 21.2f, 2f)
        moveTo(8f, 6.5f); lineTo(16f, 6.5f)
        moveTo(8.5f, 11f); lineTo(8.6f, 11f); moveTo(12f, 11f); lineTo(12.1f, 11f); moveTo(15.5f, 11f); lineTo(15.6f, 11f)
        moveTo(8.5f, 14.5f); lineTo(8.6f, 14.5f); moveTo(12f, 14.5f); lineTo(12.1f, 14.5f); moveTo(15.5f, 14.5f); lineTo(15.6f, 14.5f)
        moveTo(8.5f, 18f); lineTo(8.6f, 18f); moveTo(12f, 18f); lineTo(15.6f, 18f)
    }
    val Mic = glyph("mic") {
        rrect(9f, 3f, 15f, 14f, 3f)
        moveTo(5.5f, 11f); curveTo(5.5f, 14.6f, 8.4f, 17.5f, 12f, 17.5f); curveTo(15.6f, 17.5f, 18.5f, 14.6f, 18.5f, 11f)
        moveTo(12f, 17.5f); lineTo(12f, 21f)
    }
    val Flashlight = glyph("flashlight") {
        moveTo(7f, 3f); lineTo(17f, 3f); lineTo(17f, 7f); lineTo(14.5f, 11f); lineTo(14.5f, 21f); lineTo(9.5f, 21f)
        lineTo(9.5f, 11f); lineTo(7f, 7f); close()
        moveTo(12f, 13.5f); lineTo(12f, 15.5f)
    }
    val Bolt = glyph("bolt", fill = true) {
        moveTo(13.5f, 2f); lineTo(5f, 13.5f); lineTo(11f, 13.5f); lineTo(10f, 22f); lineTo(19f, 10f); lineTo(13f, 10f); close()
    }
    val Vibrate = glyph("vibrate") {
        rrect(8f, 4f, 16f, 20f, 1.8f)
        moveTo(4.5f, 8f); lineTo(4.5f, 16f); moveTo(19.5f, 8f); lineTo(19.5f, 16f)
    }
    val Silent = glyph("silent") {
        moveTo(6f, 16.5f); lineTo(6f, 11f); curveTo(6f, 7.5f, 8.7f, 5f, 12f, 5f); curveTo(15.3f, 5f, 18f, 7.5f, 18f, 11f)
        lineTo(18f, 16.5f); lineTo(19.5f, 18f); lineTo(4.5f, 18f); close()
        moveTo(3.5f, 3.5f); lineTo(20.5f, 20.5f)
    }
    val Ring = glyph("ring") {
        moveTo(6f, 16.5f); lineTo(6f, 11f); curveTo(6f, 7.5f, 8.7f, 5f, 12f, 5f); curveTo(15.3f, 5f, 18f, 7.5f, 18f, 11f)
        lineTo(18f, 16.5f); lineTo(19.5f, 18f); lineTo(4.5f, 18f); close()
        moveTo(2.5f, 9f); curveTo(2.8f, 6.5f, 3.8f, 4.5f, 5.5f, 3f)
        moveTo(21.5f, 9f); curveTo(21.2f, 6.5f, 20.2f, 4.5f, 18.5f, 3f)
    }
    val Moon = glyph("moon") {
        moveTo(19.5f, 14.5f); curveTo(18.4f, 18f, 15.2f, 20.5f, 11.5f, 20.5f)
        curveTo(6.8f, 20.5f, 3.5f, 17.2f, 3.5f, 12.5f); curveTo(3.5f, 8.8f, 6f, 5.6f, 9.5f, 4.5f)
        curveTo(8.4f, 9.9f, 14.1f, 15.6f, 19.5f, 14.5f); close()
    }
    val Gear = glyph("gear") {
        circle(12f, 12f, 3.2f)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            moveTo(12f + 6.2f * kotlin.math.cos(a).toFloat(), 12f + 6.2f * kotlin.math.sin(a).toFloat())
            lineTo(12f + 8.8f * kotlin.math.cos(a).toFloat(), 12f + 8.8f * kotlin.math.sin(a).toFloat())
        }
        circle(12f, 12f, 6.2f)
    }
    val Palette = glyph("palette") {
        moveTo(4f, 20f); lineTo(4f, 11f); curveTo(4f, 7f, 7f, 4f, 11f, 4f); lineTo(20f, 4f)
        moveTo(4f, 20f); lineTo(16.5f, 7.5f)
        circle(9f, 9f, 1.2f); circle(14.5f, 13.5f, 1.2f); circle(8f, 15.5f, 1.2f)
    }
    val Widget = glyph("widget") {
        rrect(3f, 3f, 13f, 13f, 2f); rrect(15f, 3f, 21f, 9f, 1.5f); rrect(3f, 15f, 9f, 21f, 1.5f); rrect(11f, 15f, 21f, 21f, 2f)
    }
    val Dial = glyph("dial") {
        circle(12f, 12f, 8.5f)
        moveTo(12f, 3.5f); lineTo(12f, 6f)
        moveTo(12f, 12f); lineTo(16f, 8.5f)
    }
    val Denser = glyph("denser", fill = true) {
        for (r in 0..2) for (c in 0..2) rrect(3f + c * 6.4f, 3f + r * 6.4f, 7.4f + c * 6.4f, 7.4f + r * 6.4f, 0.9f)
    }
    val Sparser = glyph("sparser", fill = true) {
        for (r in 0..1) for (c in 0..1) rrect(3f + c * 10f, 3f + r * 10f, 11f + c * 10f, 11f + r * 10f, 1.6f)
    }
    val Steps = glyph("steps") {
        moveTo(7f, 13f); curveTo(7f, 9f, 9.5f, 5f, 12f, 5f); curveTo(13.8f, 5f, 14.3f, 7f, 14f, 9.5f); curveTo(13.8f, 11.5f, 13f, 13f, 13f, 14.5f)
        lineTo(7f, 14.5f); close()
        moveTo(7.5f, 17f); lineTo(13.5f, 17f); lineTo(13.5f, 19f); lineTo(7.5f, 19f); close()
        moveTo(17f, 10f); curveTo(18.5f, 10f, 19.5f, 11.5f, 19.5f, 13f); lineTo(19.5f, 15f); lineTo(16f, 15f)
    }
    val Battery = glyph("battery") {
        rrect(3f, 7f, 19f, 17f, 2f); moveTo(21f, 10.5f); lineTo(21f, 13.5f)
        moveTo(6f, 10f); lineTo(14f, 10f); lineTo(14f, 14f); lineTo(6f, 14f); close()
    }
    val Signal = glyph("signal") {
        moveTo(4f, 19f); lineTo(4f, 16f); moveTo(9f, 19f); lineTo(9f, 12f); moveTo(14f, 19f); lineTo(14f, 8f); moveTo(19f, 19f); lineTo(19f, 4f)
    }
    val Memory = glyph("memory") {
        rrect(5f, 6f, 19f, 18f, 1.5f); moveTo(9f, 3f); lineTo(9f, 6f); moveTo(15f, 3f); lineTo(15f, 6f)
        moveTo(9f, 18f); lineTo(9f, 21f); moveTo(15f, 18f); lineTo(15f, 21f)
    }
    val Storage = glyph("storage") {
        moveTo(4f, 8f); lineTo(20f, 8f); lineTo(20f, 17f); lineTo(4f, 17f); close(); moveTo(7f, 12.5f); lineTo(7.1f, 12.5f); moveTo(11f, 12.5f); lineTo(17f, 12.5f)
    }
    val Plus = glyph("plus") { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) }
    val Close = glyph("close") { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
    val Up = glyph("up") { moveTo(6f, 15f); lineTo(12f, 9f); lineTo(18f, 15f) }
    val Down = glyph("down") { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) }
    val Pinch = glyph("pinch") {
        moveTo(4f, 4f); lineTo(10f, 10f); moveTo(10f, 5.5f); lineTo(10f, 10f); lineTo(5.5f, 10f)
        moveTo(20f, 20f); lineTo(14f, 14f); moveTo(14f, 18.5f); lineTo(14f, 14f); lineTo(18.5f, 14f)
    }
    val Swipe = glyph("swipe") {
        moveTo(4f, 12f); lineTo(20f, 12f); moveTo(15f, 7f); lineTo(20f, 12f); lineTo(15f, 17f)
    }
    val Shake = glyph("shake") {
        rrect(8.5f, 4f, 15.5f, 20f, 1.8f)
        moveTo(5f, 7f); curveTo(3.5f, 10f, 3.5f, 14f, 5f, 17f)
        moveTo(19f, 7f); curveTo(20.5f, 10f, 20.5f, 14f, 19f, 17f)
    }
    val Flip = glyph("flip") {
        moveTo(4f, 9f); curveTo(5.5f, 5f, 9f, 3f, 12f, 3f); curveTo(16f, 3f, 19f, 6f, 20f, 9f)
        moveTo(20f, 4.5f); lineTo(20f, 9f); lineTo(15.5f, 9f)
        rrect(7f, 12f, 17f, 21f, 1.5f)
    }

    fun forAction(id: CornerActionId): ImageVector = when (id) {
        CornerActionId.Music -> Music
        CornerActionId.Camera -> Camera
        CornerActionId.Search -> Search
        CornerActionId.AllApps -> Apps
        CornerActionId.Notifications -> Bell
        CornerActionId.LauncherSettings -> Sliders
        CornerActionId.Phone -> Phone
        CornerActionId.Messages -> Message
        CornerActionId.Browser -> Globe
        CornerActionId.Email -> Mail
        CornerActionId.Maps -> Map
        CornerActionId.Calendar -> Calendar
        CornerActionId.Clock -> Clock
        CornerActionId.Gallery -> Image
        CornerActionId.Contacts -> Person
        CornerActionId.Calculator -> Calculator
        CornerActionId.Assistant -> Mic
        CornerActionId.Flashlight -> Flashlight
        CornerActionId.GridDenser -> Denser
        CornerActionId.GridSparser -> Sparser
        CornerActionId.RingerCycle -> Ring
        CornerActionId.RingerNormal -> Ring
        CornerActionId.RingerVibrate -> Vibrate
        CornerActionId.RingerSilent -> Silent
        CornerActionId.DoNotDisturb -> Moon
        CornerActionId.SystemSettings -> Gear
    }

    fun forGesture(g: HomeGesture): ImageVector = when (g) {
        HomeGesture.PinchIn, HomeGesture.PinchOut -> Pinch
        HomeGesture.SwipeUp, HomeGesture.SwipeDown, HomeGesture.SwipeLeft, HomeGesture.SwipeRight, HomeGesture.DoubleTap -> Swipe
        HomeGesture.Shake, HomeGesture.DoubleFlick -> Shake
        HomeGesture.DoubleFlip, HomeGesture.FaceDown -> Flip
    }
}
