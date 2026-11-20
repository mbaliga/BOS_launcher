package com.bos.sphere.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.TimelineKind

/**
 * Wave 41 (timelineRich): the icon of each Timeline event kind: a white line glyph drawn in code (24-unit grid, round caps) on a muted category colour.
 * One entry per [TimelineKind], so a new kind cannot ship without an icon (a test walks the enum).
 */
internal class TimelineIcon(val glyph: ImageVector, val tile: Color, val nameKey: String)

internal object TimelineGlyphs {
    private fun glyph(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder("timeline.$name", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)
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

    private fun PathBuilder.phone() {
        moveTo(6.5f, 3.5f); lineTo(9.5f, 3.5f); lineTo(11f, 8f); lineTo(8.8f, 9.6f)
        curveTo(9.9f, 11.9f, 12.1f, 14.1f, 14.4f, 15.2f)
        lineTo(16f, 13f); lineTo(20.5f, 14.5f); lineTo(20.5f, 17.5f)
        curveTo(20.5f, 19.2f, 19.2f, 20.5f, 17.5f, 20.5f)
        curveTo(10f, 20f, 4f, 14f, 3.5f, 6.5f)
        curveTo(3.5f, 4.8f, 4.8f, 3.5f, 6.5f, 3.5f); close()
    }

    /** A box seen from the corner, drawn small at the top left so a sign fits at the bottom right. */
    private fun PathBuilder.smallBox() {
        moveTo(9f, 2.5f); lineTo(16f, 6.5f); lineTo(16f, 13.5f); lineTo(9f, 17.5f); lineTo(2f, 13.5f); lineTo(2f, 6.5f); close()
        moveTo(2f, 6.5f); lineTo(9f, 10.5f); lineTo(16f, 6.5f); moveTo(9f, 10.5f); lineTo(9f, 17.5f)
    }

    val Open = glyph("open") { moveTo(10f, 5f); lineTo(5f, 5f); lineTo(5f, 19f); lineTo(19f, 19f); lineTo(19f, 14f); moveTo(13f, 4f); lineTo(20f, 4f); lineTo(20f, 11f); moveTo(20f, 4f); lineTo(11f, 13f) }
    val Scheduled = glyph("scheduled") {
        circle(10.5f, 13.5f, 7f); moveTo(10.5f, 9.5f); lineTo(10.5f, 13.5f); lineTo(13.5f, 15f)
        moveTo(17f, 3.5f); lineTo(21f, 3.5f); lineTo(21f, 7.5f); moveTo(21f, 3.5f); lineTo(17.5f, 7f)
    }
    val Bell = glyph("bell") {
        moveTo(6f, 16.5f); lineTo(6f, 11f); curveTo(6f, 7.5f, 8.7f, 5f, 12f, 5f); curveTo(15.3f, 5f, 18f, 7.5f, 18f, 11f)
        lineTo(18f, 16.5f); lineTo(19.5f, 18f); lineTo(4.5f, 18f); close(); moveTo(10f, 20.5f); lineTo(14f, 20.5f); moveTo(12f, 3f); lineTo(12f, 5f)
    }
    val Alert = glyph("alert") { moveTo(12f, 3.5f); lineTo(21.5f, 19.5f); lineTo(2.5f, 19.5f); close(); moveTo(12f, 9.5f); lineTo(12f, 14f); moveTo(12f, 16.6f); lineTo(12.01f, 16.6f) }
    val Call = glyph("call") { phone() }
    val MissedCall = glyph("missed") { phone(); moveTo(21f, 3f); lineTo(14.5f, 9.5f); moveTo(14.5f, 5f); lineTo(14.5f, 9.5f); lineTo(19f, 9.5f) }
    val Chat = glyph("chat") { rrect(3f, 4f, 21f, 16f, 3f); moveTo(8f, 16f); lineTo(8f, 20.5f); lineTo(12.5f, 16f) }
    val Envelope = glyph("envelope") { rrect(3f, 5.5f, 21f, 18.5f, 2f); moveTo(3.5f, 7f); lineTo(12f, 13.5f); lineTo(20.5f, 7f) }
    val Calendar = glyph("calendar") { rrect(3.5f, 5f, 20.5f, 20f, 2.5f); moveTo(3.5f, 10f); lineTo(20.5f, 10f); moveTo(8f, 3f); lineTo(8f, 7f); moveTo(16f, 3f); lineTo(16f, 7f); moveTo(8f, 14f); lineTo(8.01f, 14f); moveTo(12f, 14f); lineTo(12.01f, 14f); moveTo(16f, 14f); lineTo(16.01f, 14f) }
    val AlarmClock = glyph("alarm") { circle(12f, 13.5f, 7f); moveTo(12f, 10f); lineTo(12f, 13.5f); lineTo(14.5f, 15f); moveTo(3.5f, 6.5f); lineTo(7f, 3.5f); moveTo(20.5f, 6.5f); lineTo(17f, 3.5f) }
    val DownArrow = glyph("download") { moveTo(12f, 4f); lineTo(12f, 15f); moveTo(7f, 10.5f); lineTo(12f, 15.5f); lineTo(17f, 10.5f); moveTo(5f, 20f); lineTo(19f, 20f) }
    val Shield = glyph("shield") { moveTo(12f, 3f); lineTo(20f, 6f); lineTo(20f, 12f); quadTo(20f, 18f, 12f, 21f); quadTo(4f, 18f, 4f, 12f); lineTo(4f, 6f); close() }
    val Tag = glyph("tag") { moveTo(3f, 3f); lineTo(12f, 3f); lineTo(21f, 12f); lineTo(12f, 21f); lineTo(3f, 12f); close(); moveTo(8f, 8f); lineTo(8.01f, 8f) }
    val BoxPlus = glyph("boxplus") { smallBox(); moveTo(18f, 14.5f); lineTo(18f, 21.5f); moveTo(14.5f, 18f); lineTo(21.5f, 18f) }
    val BoxMinus = glyph("boxminus") { smallBox(); moveTo(14.5f, 18f); lineTo(21.5f, 18f) }
    val BoxRefresh = glyph("boxrefresh") {
        smallBox()
        moveTo(21.2f, 17f); arcTo(3.4f, 3.4f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 18f, y1 = 21.4f)
        moveTo(21.2f, 14.6f); lineTo(21.2f, 17.2f); lineTo(18.6f, 17.2f)
    }
    val Eye = glyph("eye") { moveTo(2f, 12f); quadTo(12f, 3f, 22f, 12f); quadTo(12f, 21f, 2f, 12f); close(); circle(12f, 12f, 3f) }
    val EyeOff = glyph("eyeoff") { moveTo(2f, 12f); quadTo(12f, 3f, 22f, 12f); quadTo(12f, 21f, 2f, 12f); close(); circle(12f, 12f, 3f); moveTo(4f, 4f); lineTo(20f, 20f) }
    val ChevronDown = glyph("chevdown") { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) }
    val ChevronUp = glyph("chevup") { moveTo(6f, 15f); lineTo(12f, 9f); lineTo(18f, 15f) }

    private val neutral = Color(0xFF5C6773)
    private val blue = Color(0xFF3F6FB5)
    private val amber = Color(0xFFB07A22)
    private val red = Color(0xFFC0453F)
    private val green = Color(0xFF3F8A52)
    private val teal = Color(0xFF2F8582)
    private val violet = Color(0xFF6A5FB0)
    private val pink = Color(0xFFA5568A)
    private val indigo = Color(0xFF4F5BB0)
    private val orange = Color(0xFFC96F25)
    private val steel = Color(0xFF3E7C9C)
    private val rose = Color(0xFF9A5252)
    private val slate = Color(0xFF68687A)

    /** The icon, tile colour and English name of each kind. */
    fun of(kind: TimelineKind): TimelineIcon = when (kind) {
        TimelineKind.AppOpened -> TimelineIcon(Open, neutral, "App opened")
        TimelineKind.ScheduledLaunch -> TimelineIcon(Scheduled, blue, "Scheduled launch")
        TimelineKind.Notification -> TimelineIcon(Bell, amber, "Notification")
        TimelineKind.Message -> TimelineIcon(Chat, teal, "Message")
        TimelineKind.Email -> TimelineIcon(Envelope, violet, "Email")
        TimelineKind.Promo -> TimelineIcon(Tag, pink, "Promotion")
        TimelineKind.Download -> TimelineIcon(DownArrow, steel, "Download")
        TimelineKind.Alert -> TimelineIcon(Alert, red, "Alert")
        TimelineKind.Call -> TimelineIcon(Call, green, "Phone call")
        TimelineKind.MissedCall -> TimelineIcon(MissedCall, red, "Missed call")
        TimelineKind.Calendar -> TimelineIcon(Calendar, indigo, "Calendar")
        TimelineKind.Alarm -> TimelineIcon(AlarmClock, orange, "Alarm")
        TimelineKind.Security -> TimelineIcon(Shield, rose, "Security")
        TimelineKind.PackageInstalled -> TimelineIcon(BoxPlus, green, "App installed")
        TimelineKind.PackageUpdated -> TimelineIcon(BoxRefresh, steel, "App updated")
        TimelineKind.PackageRemoved -> TimelineIcon(BoxMinus, rose, "App removed")
        TimelineKind.AppHidden -> TimelineIcon(EyeOff, slate, "App hidden")
        TimelineKind.AppUnhidden -> TimelineIcon(Eye, slate, "App shown")
    }

    /** The "Next alarm" row's icon. */
    val NextAlarm = TimelineIcon(AlarmClock, orange, "Alarm")
}
