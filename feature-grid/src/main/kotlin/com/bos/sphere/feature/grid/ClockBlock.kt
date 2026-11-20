package com.bos.sphere.feature.grid

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.ClockFormat
import com.bos.sphere.core.data.ClockStyle
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.rememberMotionPrefs
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Pure formatting and geometry for the Glance clock (testable). */
object ClockText {
    fun timePattern(format: ClockFormat, system24: Boolean): String = when (format) {
        ClockFormat.Hour12 -> "h:mm"
        ClockFormat.Hour24 -> "HH:mm"
        ClockFormat.System -> if (system24) "HH:mm" else "h:mm"
    }

    /** The digital clock is exactly as large as the music line's text (the owner's rule). */
    const val DIGITAL_SP = GlanceText.MUSIC_SP

    /** The analog dial: 48 dp (the owner asked for 44 to 56 dp, about 1.6 times the music text's height plus its touch room); it is also the 48 dp touch target. */
    const val ANALOG_DP = 48f

    /** Hand angles in radians clockwise from 12 o'clock: (hour hand, minute hand). */
    fun handAngles(hour: Int, minute: Int): Pair<Float, Float> {
        val m = (minute % 60) / 60f
        val h = ((hour % 12) + m) / 12f
        return (h * 2f * Math.PI).toFloat() to (m * 2f * Math.PI).toFloat()
    }
}

/**
 * Wave 38: the one clock, in the fixed chrome at the top of EVERY page (Glance and the pages to its right), above the music line. Analog (the default): a thin
 * ring with 12 small ticks, a thin hour hand in the ink and a thin minute hand in the accent, no numerals, no seconds hand. Digital: the time in the music
 * text's size and ink, 12 or 24 hour by the setting (System follows the device). No date, no weather. Both carry a soft dark halo so they read on any
 * wallpaper. The hands move once a minute (the system's minute tick from [rememberNow], never a frame loop), so reduced motion needs nothing extra. It is not
 * mirrored in right-to-left. A tap opens the clock app ([onTap]). It fades out while the music controls notch is in, which covers the top edge.
 */
@Composable
fun GlanceClock(
    style: ClockStyle, format: ClockFormat, ink: Color, accent: Color, hidden: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reduced = rememberMotionPrefs().reduced
    val now = rememberNow()
    val is24 = DateFormat.is24HourFormat(context)
    val alpha by animateFloatAsState(if (hidden) 0f else 1f, if (reduced) snap() else tween(160), label = "glanceClockAlpha")
    val spoken = remember(now, format, is24) { SimpleDateFormat(ClockText.timePattern(format, is24), Locale.getDefault()).format(now) }
    val label = L10n.tr("Open clock")
    val halo = Color.Black.copy(alpha = 0.55f)
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
    Box(
        modifier.graphicsLayer { this.alpha = alpha }.clip(RoundedCornerShape(24.dp)).size(width = if (style == ClockStyle.Analog) 48.dp else 72.dp, height = 48.dp)
            .launcherItem("glance.clock", focusable = alpha > 0.5f, onActivate = onTap, ringRadiusDp = 24f)
            .clickable(enabled = alpha > 0.5f, onClickLabel = label, role = Role.Button, onClick = onTap)
            .then(if (style == ClockStyle.Analog) Modifier.semantics { contentDescription = spoken } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (style == ClockStyle.Analog) {
            val cal = remember(now) { Calendar.getInstance().apply { time = now } }
            val (ha, ma) = ClockText.handAngles(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            Canvas(Modifier.size(ClockText.ANALOG_DP.dp - 8.dp).testTag("glance.clock.analog").clearAndSetSemantics { }) {
                val c = Offset(size.width / 2f, size.height / 2f); val r = size.minDimension / 2f - 1.dp.toPx()
                val w = 1.2.dp.toPx()
                fun ring(color: Color, extra: Float) {
                    drawCircle(color, r, c, style = Stroke(w + extra))
                    for (i in 0 until 12) {
                        val a = (i * 30f) * (Math.PI.toFloat() / 180f); val inner = r - (if (i % 3 == 0) 4.dp.toPx() else 2.5.dp.toPx())
                        drawLine(color, Offset(c.x + sin(a) * inner, c.y - cos(a) * inner), Offset(c.x + sin(a) * r, c.y - cos(a) * r), w + extra, StrokeCap.Round)
                    }
                }
                fun hand(color: Color, a: Float, len: Float, width: Float) = drawLine(color, c, Offset(c.x + sin(a) * r * len, c.y - cos(a) * r * len), width, StrokeCap.Round)
                // the halo first: the same shapes a little thicker in a soft dark, so the clock reads on a bright wallpaper too
                ring(halo, 1.5.dp.toPx()); hand(halo, ha, 0.5f, w + 1.5.dp.toPx()); hand(halo, ma, 0.8f, w + 1.5.dp.toPx())
                ring(ink, 0f); hand(ink, ha, 0.5f, w * 1.3f); hand(accent, ma, 0.8f, w)
                drawCircle(accent, 1.8.dp.toPx(), c)
            }
        } else {
            Text(
                spoken, color = ink, fontSize = ClockText.DIGITAL_SP.sp, maxLines = 1, softWrap = false,
                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(halo, Offset(0f, 1f), 4f), fontSize = ClockText.DIGITAL_SP.sp, color = ink),
                modifier = Modifier.padding(horizontal = 8.dp).testTag("glance.clock.digital"),
            )
        }
    }
    }
}
