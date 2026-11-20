package com.bos.sphere.feature.grid

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.ClockFormat
import com.bos.sphere.core.data.ClockSize
import com.bos.sphere.core.data.ClockStyle
import com.bos.sphere.core.design.L10n
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Pure formatting, words and geometry of the Glance wallpaper clock (JVM-testable). */
object ClockText {
    fun timePattern(format: ClockFormat, system24: Boolean): String = when (format) {
        ClockFormat.Hour12 -> "h:mm"
        ClockFormat.Hour24 -> "HH:mm"
        ClockFormat.System -> if (system24) "HH:mm" else "h:mm"
    }

    /** True when the digits are shown 24-hour for [format] on a device whose own setting is [system24]. */
    fun is24(format: ClockFormat, system24: Boolean): Boolean = when (format) { ClockFormat.Hour12 -> false; ClockFormat.Hour24 -> true; ClockFormat.System -> system24 }

    /** The hours digits of the stacked clock: 24-hour is two digits ("07"), 12-hour has no leading zero ("7", 12 at noon and midnight). */
    fun hoursText(hour: Int, is24: Boolean): String = if (is24) "%02d".format(hour % 24) else (if (hour % 12 == 0) 12 else hour % 12).toString()

    fun minutesText(minute: Int): String = "%02d".format(minute % 60)

    /** Hand angles in radians clockwise from 12 o'clock: (hour hand, minute hand). */
    fun handAngles(hour: Int, minute: Int): Pair<Float, Float> {
        val m = (minute % 60) / 60f
        val h = ((hour % 12) + m) / 12f
        return (h * 2f * Math.PI).toFloat() to (m * 2f * Math.PI).toFloat()
    }

    private val HOURS = listOf("twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven")

    /** The time in English words, rounded DOWN to the five minutes: "It's ten o'clock", "It's ten past six", "It's quarter to seven", "It's five to one". */
    fun wordsEnglish(hour: Int, minute: Int): String {
        val b = (minute % 60) / 5
        val h = hour % 12
        fun name(x: Int) = HOURS[x % 12]
        val core = when (b) {
            0 -> "${name(h)} o'clock"
            1 -> "five past ${name(h)}"
            2 -> "ten past ${name(h)}"
            3 -> "quarter past ${name(h)}"
            4 -> "twenty past ${name(h)}"
            5 -> "twenty-five past ${name(h)}"
            6 -> "half past ${name(h)}"
            7 -> "twenty-five to ${name(h + 1)}"
            8 -> "twenty to ${name(h + 1)}"
            9 -> "quarter to ${name(h + 1)}"
            10 -> "ten to ${name(h + 1)}"
            else -> "five to ${name(h + 1)}"
        }
        return "It's $core"
    }

    /** The word clock exists for English only; every other language shows [fallback]. */
    fun effectiveStyle(style: ClockStyle, language: String): ClockStyle = if (style == ClockStyle.Word && language != "en") ClockStyle.BigDigital else style

    /** Largest size in dp/sp of each style at [ClockSize.Large]; the other sizes scale it, the fit shrinks it further. */
    const val BIG_DIGITS_SP = 132f
    const val BIG_LINE_SP = 96f
    const val WORD_SP = 44f
    const val DIAL_DP = 260f
    const val MIN_SP = 20f

    /** The area the clock may use, as fractions of the page (it scales to fit in landscape). */
    const val AREA_WIDTH = 0.82f
    const val AREA_HEIGHT = 0.42f

    /** The top of the clock area as a fraction of the page height: the visual upper-middle, like a lock-screen clock. */
    const val TOP_FRACTION = 0.14f

    /** The largest size in 1..[maxSize] steps that still has [fits] true (binary search; [minSize] when none does). */
    fun fit(maxSize: Float, minSize: Float, fits: (Float) -> Boolean): Float {
        if (fits(maxSize)) return maxSize
        var lo = minSize; var hi = maxSize
        repeat(12) { val mid = (lo + hi) / 2f; if (fits(mid)) lo = mid else hi = mid }
        return lo
    }
}

/**
 * Wave 41 (glanceClock): the big wallpaper clock of the calm Glance page. It is part of the PAGE content (not the fixed chrome), so it scrolls away with the
 * page; it sits centred in the upper-middle. Styles: [ClockStyle.BigDigital] (hours over minutes with a thin accent line), [ClockStyle.BigDigitalLine],
 * [ClockStyle.AnalogClassic], [ClockStyle.AnalogMinimal], [ClockStyle.Word] (English only, else Big digital) and [ClockStyle.Off] (nothing drawn). It ticks
 * on the system minute tick ([rememberNow], never a frame loop), has no seconds, does not mirror in right-to-left, fits its area with a measured size so
 * large font scales never overflow, and a tap opens the clock app. The ink is the legibility ink of the wallpaper (white, or near-black on a light surface)
 * with a soft shadow, or the accent. The accessibility description is the time and changes once a minute.
 */
@Composable
fun GlanceWallpaperClock(
    style: ClockStyle, format: ClockFormat, size: ClockSize, showDate: Boolean, useAccent: Boolean, dark: Boolean, accent: Color, onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (style == ClockStyle.Off) return
    val context = LocalContext.current
    val now = rememberNow()
    val is24 = ClockText.is24(format, DateFormat.is24HourFormat(context))
    val shown = ClockText.effectiveStyle(style, L10n.effectiveLanguage())
    val cal = remember(now) { Calendar.getInstance().apply { time = now } }
    val hour = cal.get(Calendar.HOUR_OF_DAY); val minute = cal.get(Calendar.MINUTE)
    val spokenTime = remember(now, is24) { SimpleDateFormat(if (is24) "HH:mm" else "h:mm a", Locale.getDefault()).format(now) }
    val dateText = remember(now) { SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMd"), Locale.getDefault()).format(now) }
    val ink = if (useAccent) accent else GlanceInk.clock(dark)
    val shadow = Shadow(if (dark) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.6f), Offset(0f, 2f), 14f)
    val open = L10n.tr("Open clock")
    val spoken = if (showDate) "$spokenTime, $dateText" else spokenTime
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier.fillMaxSize().testTag("glance.clock.area")) {
            val areaW = maxWidth * ClockText.AREA_WIDTH
            val areaH = maxHeight * ClockText.AREA_HEIGHT
            val f = size.factor
            val dateH = if (showDate) 28.dp else 0.dp
            Column(
                Modifier.align(Alignment.TopCenter).padding(top = maxHeight * ClockText.TOP_FRACTION)
                    .clickable(onClickLabel = open, role = Role.Button, onClick = onTap)
                    .launcherItem("glance.clock", focusable = true, onActivate = onTap, ringRadiusDp = 24f)
                    .testTag("glance.clock")
                    .clearAndSetSemantics { contentDescription = spoken }, // innermost: it clears the children's semantics, not the tag or the click above it
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val maxWpx = with(density) { areaW.toPx() }.toInt(); val maxHpx = with(density) { (areaH - dateH).toPx() }
                when (shown) {
                    ClockStyle.BigDigital -> {
                        val hh = ClockText.hoursText(hour, is24); val mm = ClockText.minutesText(minute)
                        val sp = ClockText.fit(ClockText.BIG_DIGITS_SP * f, ClockText.MIN_SP) { s ->
                            val st = TextStyle(fontSize = s.sp, fontWeight = FontWeight.ExtraLight, lineHeight = (s * 0.92f).sp)
                            val r = measurer.measure("00", st, softWrap = false, maxLines = 1)
                            r.size.width <= maxWpx && (2 * r.size.height * 0.92f + with(density) { 20.dp.toPx() }) <= maxHpx
                        }
                        val st = TextStyle(color = ink, fontSize = sp.sp, fontWeight = FontWeight.ExtraLight, lineHeight = (sp * 0.92f).sp, textAlign = TextAlign.Center, shadow = shadow)
                        Text(hh, style = st, maxLines = 1, softWrap = false, modifier = Modifier.testTag("glance.clock.hours"))
                        Box(Modifier.padding(vertical = 4.dp).width(48.dp).height(2.dp).testTag("glance.clock.line").background(accent))
                        Text(mm, style = st, maxLines = 1, softWrap = false, modifier = Modifier.testTag("glance.clock.minutes"))
                    }
                    ClockStyle.BigDigitalLine -> {
                        val text = "%s:%s".format(ClockText.hoursText(hour, is24), ClockText.minutesText(minute))
                        val sp = ClockText.fit(ClockText.BIG_LINE_SP * f, ClockText.MIN_SP) { s ->
                            val r = measurer.measure("00:00", TextStyle(fontSize = s.sp, fontWeight = FontWeight.ExtraLight), softWrap = false, maxLines = 1)
                            r.size.width <= maxWpx && r.size.height <= maxHpx
                        }
                        Text(text, style = TextStyle(color = ink, fontSize = sp.sp, fontWeight = FontWeight.ExtraLight, textAlign = TextAlign.Center, shadow = shadow), maxLines = 1, softWrap = false,
                            modifier = Modifier.testTag("glance.clock.line.text"))
                    }
                    ClockStyle.Word -> {
                        val text = ClockText.wordsEnglish(hour, minute)
                        val sp = ClockText.fit(ClockText.WORD_SP * f, ClockText.MIN_SP) { s ->
                            val r = measurer.measure(text, TextStyle(fontSize = s.sp, fontWeight = FontWeight.Light, lineHeight = (s * 1.15f).sp), constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxWpx))
                            r.size.height <= maxHpx
                        }
                        Text(text, style = TextStyle(color = ink, fontSize = sp.sp, fontWeight = FontWeight.Light, lineHeight = (sp * 1.15f).sp, textAlign = TextAlign.Center, shadow = shadow),
                            modifier = Modifier.width(areaW).testTag("glance.clock.word"))
                    }
                    else -> {
                        val minimal = shown == ClockStyle.AnalogMinimal
                        val dia = minOf(areaW, (areaH - dateH), ClockText.DIAL_DP.dp * f)
                        val (ha, ma) = ClockText.handAngles(hour, minute)
                        Canvas(Modifier.size(dia).testTag(if (minimal) "glance.clock.analogMinimal" else "glance.clock.analogClassic")) {
                            val c = Offset(this.size.width / 2f, this.size.height / 2f); val r = this.size.minDimension / 2f - 4.dp.toPx()
                            val w = (if (minimal) 3f else 2f).dp.toPx() * (dia.value / 200f).coerceIn(0.6f, 1.4f)
                            val soft = if (dark) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.45f)
                            fun draw(color: Color, extra: Float, dy: Float) {
                                val o = Offset(0f, dy)
                                if (!minimal) {
                                    drawCircle(color, r, c + o, style = Stroke(w * 0.6f + extra))
                                    for (i in 0 until 12) {
                                        val a = (i * 30f) * (Math.PI.toFloat() / 180f); val inner = r - (if (i % 3 == 0) r * 0.12f else r * 0.07f)
                                        drawLine(color, Offset(c.x + sin(a) * inner, c.y - cos(a) * inner) + o, Offset(c.x + sin(a) * r, c.y - cos(a) * r) + o, w * 0.7f + extra, StrokeCap.Round)
                                    }
                                }
                                drawLine(color, c + o, Offset(c.x + sin(ha) * r * 0.5f, c.y - cos(ha) * r * 0.5f) + o, w + extra, StrokeCap.Round)
                                drawLine(color, c + o, Offset(c.x + sin(ma) * r * 0.78f, c.y - cos(ma) * r * 0.78f) + o, w * 0.8f + extra, StrokeCap.Round)
                            }
                            draw(soft, 4.dp.toPx(), 2.dp.toPx())
                            draw(ink, 0f, 0f)
                            drawCircle(accent, (if (minimal) 3f else 4.5f).dp.toPx(), c)
                        }
                    }
                }
                if (showDate) Text(dateText, color = ink, fontSize = 14.sp, maxLines = 1, style = TextStyle(shadow = shadow, color = ink, fontSize = 14.sp), modifier = Modifier.padding(top = 8.dp).testTag("glance.clock.date"))
            }
        }
    }
}
