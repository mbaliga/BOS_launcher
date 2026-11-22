package com.bos.sphere.feature.grid

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.bos.sphere.core.catalog.UsageRange
import com.bos.sphere.core.data.CalmStyle
import com.bos.sphere.core.data.ClockFormat
import com.bos.sphere.core.data.ClockSize
import com.bos.sphere.core.data.ClockStyle
import com.bos.sphere.core.data.IconShape
import com.bos.sphere.core.data.IconTint
import com.bos.sphere.core.data.IndicatorMetric
import com.bos.sphere.core.data.RadarIntensity
import com.bos.sphere.core.data.SettingsBackdrop
import com.bos.sphere.core.data.ThemeMode
import com.bos.sphere.core.data.WallpaperKind
import com.bos.sphere.core.data.WebEngine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Wave 54 (settingsVisual): the illustrations of the Glance clock, the Look page, the indicator effects, Radar's search engine and the wallpaper
 * type. Same rules as [SettingsArtHome]: one [ArtSet] per setting over every value, drawn from the ink, static.
 */
object SettingsArtLook {
    val clockStyle = ArtSet(ClockStyle.entries) { clockStyleArt(it) }
    val clockFormat = ArtSet(ClockFormat.entries) { clockFormatArt(it) }
    val clockSize = ArtSet(ClockSize.entries) { clockSizeArt(it) }
    /** Clock colour is a yes or no (false = automatic, true = the accent). */
    val clockColour = ArtSet(listOf(false, true)) { clockColourArt(it) }
    val themeMode = ArtSet(ThemeMode.entries) { themeModeArt(it) }
    val iconShape = ArtSet(IconShape.entries) { iconShapeArt(it) }
    val iconTint = ArtSet(IconTint.entries) { iconTintArt(it) }
    val backdrop = ArtSet(SettingsBackdrop.entries) { backdropArt(it) }
    /** Calm style (wave 55): Off, Regular and the four ambient styles. */
    val calm = ArtSet(CalmStyle.entries) { calmArt(it) }
    val metric = ArtSet(IndicatorMetric.entries) { metricArt(it) }
    val webEngine = ArtSet(WebEngine.entries) { webEngineArt(it) }
    val wallpaperKind = ArtSet(WallpaperKind.entries) { wallpaperKindArt(it) }
    val radarIntensity = ArtSet(RadarIntensity.entries) { radarIntensityArt(it) }
    val usageRange = ArtSet(UsageRange.entries) { usageRangeArt(it) }

    private val SampleHues = listOf(Color(0xFFFF6B6B), Color(0xFF3A86FF), Color(0xFFFFB703), Color(0xFF06D6A0))

    // ---- clock ------------------------------------------------------------------------------------------------------------------------------------

    private fun clockStyleArt(s: ClockStyle): VisualArt = { ink ->
        val w = size.width; val h = size.height; val cx = w / 2f
        when (s) {
            ClockStyle.BigDigital -> { digits2(10, cx, h * 0.1f, w * 0.17f, h * 0.38f, dp(2.4f), ink.strong); digits2(9, cx, h * 0.54f, w * 0.17f, h * 0.38f, dp(2.4f), ink.soft) }
            ClockStyle.BigDigitalLine -> {
                val dw = w * 0.115f; val th = dp(1.9f); val y = h * 0.28f; val dh = h * 0.44f
                digit(1, cx - dw * 3.0f, y, dw, dh, th, ink.strong); digit(0, cx - dw * 1.6f, y, dw, dh, th, ink.strong)
                drawCircle(ink.strong, dp(1.6f), Offset(cx, h * 0.42f)); drawCircle(ink.strong, dp(1.6f), Offset(cx, h * 0.58f))
                digit(0, cx + dw * 0.5f, y, dw, dh, th, ink.strong); digit(9, cx + dw * 1.9f, y, dw, dh, th, ink.strong)
            }
            ClockStyle.AnalogClassic -> {
                val c = Offset(cx, h / 2f); val r = h * 0.4f
                drawCircle(ink.soft, r, c, style = Stroke(dp(1.5f)))
                for (k in 0 until 12) {
                    val a = (PI / 6 * k).toFloat(); val l = if (k % 3 == 0) 0.18f else 0.1f
                    drawLine(ink.soft, Offset(c.x + r * 0.82f * sin(a), c.y - r * 0.82f * cos(a)), Offset(c.x + r * (0.82f + l * 0.9f) * sin(a), c.y - r * (0.82f + l * 0.9f) * cos(a)), dp(1.2f))
                }
                drawLine(ink.strong, c, Offset(c.x + r * 0.45f * sin(2.4f), c.y - r * 0.45f * cos(2.4f)), dp(2.2f), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(ink.strong, c, Offset(c.x + r * 0.7f * sin(0.3f), c.y - r * 0.7f * cos(0.3f)), dp(1.6f), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawCircle(ink.strong, dp(2f), c)
            }
            ClockStyle.AnalogMinimal -> {
                val c = Offset(cx, h / 2f); val r = h * 0.4f
                drawLine(ink.strong, c, Offset(c.x + r * 0.5f * sin(2.4f), c.y - r * 0.5f * cos(2.4f)), dp(3f), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(ink.soft, c, Offset(c.x + r * 0.85f * sin(0.3f), c.y - r * 0.85f * cos(0.3f)), dp(2f), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawCircle(ink.strong, dp(2.4f), c)
            }
            ClockStyle.Word -> {
                // a word clock: lines of letters, the few that spell the time are lit
                val rows = 5; val cols = 6
                for (r in 0 until rows) for (c in 0 until cols) {
                    val lit = (r == 1 && c in 1..3) || (r == 3 && c in 2..5)
                    drawRoundRect(if (lit) ink.strong else ink.faint, Offset(w * (0.2f + c * 0.1f), h * (0.16f + r * 0.14f)), Size(dp(5f), dp(5f)), CornerRadius(dp(1.4f)))
                }
            }
            ClockStyle.Off -> {
                val c = Offset(cx, h / 2f); val r = h * 0.3f
                drawCircle(ink.faint, r, c, style = Stroke(dp(1.5f)))
                drawLine(ink.soft, Offset(c.x - r * 0.7f, c.y + r * 0.7f), Offset(c.x + r * 0.7f, c.y - r * 0.7f), dp(1.8f), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
    }

    private fun clockFormatArt(f: ClockFormat): VisualArt = { ink ->
        val w = size.width; val h = size.height; val dw = w * 0.1f; val th = dp(1.9f); val y = h * 0.2f; val dh = h * 0.42f
        val (a, b) = if (f == ClockFormat.Hour24) 2 to 2 else 1 to 0
        val x0 = w * 0.12f
        digit(a, x0, y, dw, dh, th, ink.strong); digit(b, x0 + dw * 1.5f, y, dw, dh, th, ink.strong)
        drawCircle(ink.strong, dp(1.3f), Offset(x0 + dw * 3.05f, h * 0.34f)); drawCircle(ink.strong, dp(1.3f), Offset(x0 + dw * 3.05f, h * 0.48f))
        digit(3, x0 + dw * 3.6f, y, dw, dh, th, ink.strong); digit(0, x0 + dw * 5.1f, y, dw, dh, th, ink.strong)
        if (f != ClockFormat.Hour24) rect(ink.soft, 0.6f, 0.72f, 0.22f, 0.14f, 0.5f) // the AM or PM mark
        else line(ink.faint, 0.18f, 0.79f, 0.82f, 0.79f, 1.2f)
    }

    private fun clockSizeArt(s: ClockSize): VisualArt = { ink ->
        val w = size.width; val h = size.height
        rect(ink.faint, 0.12f, 0.1f, 0.76f, 0.8f, 0.1f, stroke = 1f)
        val f = s.factor
        val dh = h * 0.56f * f; val dw = dh * 0.5f; val gap = dw * 0.4f
        val x = (w - (2 * dw + gap)) / 2f
        digit(1, x, (h - dh) / 2f, dw, dh, dp(2.2f) * (0.6f + f * 0.5f), ink.strong); digit(2, x + dw + gap, (h - dh) / 2f, dw, dh, dp(2.2f) * (0.6f + f * 0.5f), ink.strong)
    }

    private fun clockColourArt(accent: Boolean): VisualArt = { ink ->
        val w = size.width; val h = size.height; val dh = h * 0.5f; val dw = dh * 0.5f; val th = dp(3f)
        val x = (w - (2 * dw + dw * 0.4f)) / 2f; val y = (h - dh) / 2f
        if (accent) {
            digit(1, x, y, dw, dh, th, ink.accent); digit(2, x + dw * 1.4f, y, dw, dh, th, ink.accent)
        } else {
            // automatic: white on a dark wallpaper, dark on a light one
            drawRoundRect(Color(0xFF20232C), Offset(0f, 0f), Size(w / 2f, h))
            drawRoundRect(Color(0xFFE9EBF2), Offset(w / 2f, 0f), Size(w / 2f, h))
            digit(1, x, y, dw, dh, th, Color.White); digit(2, x + dw * 1.4f, y, dw, dh, th, Color(0xFF20232C))
        }
    }

    // ---- look -------------------------------------------------------------------------------------------------------------------------------------

    private fun themeModeArt(m: ThemeMode): VisualArt = { ink ->
        val (bg, surface, text) = when (m) {
            ThemeMode.Dark -> Triple(Color(0xFF0A0B10), Color(0xFF161922), Color(0xFFE7E9F0))
            ThemeMode.Amoled -> Triple(Color(0xFF000000), Color(0xFF0B0C10), Color(0xFFE7E9F0))
            ThemeMode.Light -> Triple(Color(0xFFF3F4F8), Color(0xFFFFFFFF), Color(0xFF0E1018))
        }
        val (o, sz) = phoneFrame(ink, fill = bg)
        drawRoundRect(surface, Offset(o.x + sz.width * 0.12f, o.y + sz.height * 0.12f), Size(sz.width * 0.76f, sz.height * 0.22f), CornerRadius(dp(3f)))
        drawRoundRect(text.copy(alpha = 0.8f), Offset(o.x + sz.width * 0.12f, o.y + sz.height * 0.44f), Size(sz.width * 0.5f, sz.height * 0.06f), CornerRadius(dp(2f)))
        drawRoundRect(ink.accent, Offset(o.x + sz.width * 0.12f, o.y + sz.height * 0.58f), Size(sz.width * 0.4f, sz.height * 0.14f), CornerRadius(dp(3f)))
    }

    private fun iconShapeArt(s: IconShape): VisualArt = { ink ->
        val e = size.height * 0.56f; val c = Offset(size.width / 2f, size.height / 2f); val o = Offset(c.x - e / 2f, c.y - e / 2f)
        val fill = ink.strong
        when (s) {
            IconShape.Original -> {
                // the app's own artwork, unclipped: a loose cluster of shapes
                drawRoundRect(SampleHues[0], Offset(o.x, o.y + e * 0.2f), Size(e * 0.6f, e * 0.6f), CornerRadius(e * 0.1f))
                drawCircle(SampleHues[1], e * 0.28f, Offset(o.x + e * 0.72f, o.y + e * 0.3f))
                drawCircle(SampleHues[2], e * 0.2f, Offset(o.x + e * 0.62f, o.y + e * 0.78f))
            }
            IconShape.Rounded -> drawRoundRect(fill, o, Size(e, e), CornerRadius(e * 0.24f))
            IconShape.Squircle -> {
                val p = Path()
                val n = 60
                for (i in 0..n) {
                    val t = (2 * PI * i / n).toFloat(); val ct = cos(t); val st = sin(t)
                    val x = c.x + e / 2f * Math.signum(ct) * Math.pow(Math.abs(ct).toDouble(), 0.5).toFloat()
                    val y = c.y + e / 2f * Math.signum(st) * Math.pow(Math.abs(st).toDouble(), 0.5).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close(); drawPath(p, fill)
            }
            IconShape.Circle -> drawCircle(fill, e / 2f, c)
            IconShape.Square -> drawRect(fill, o, Size(e, e))
        }
    }

    private fun iconTintArt(t: IconTint): VisualArt = { ink ->
        val e = size.height * 0.28f; val gap = e * 0.4f; val total = e * 2 + gap
        val x0 = (size.width - total) / 2f; val y0 = (size.height - total) / 2f
        for (i in 0 until 4) {
            val col = when (t) {
                IconTint.None -> SampleHues[i]
                IconTint.Accent -> ink.accent.copy(alpha = 1f - i * 0.12f)
                IconTint.Mono -> ink.strong.copy(alpha = 0.95f - i * 0.18f)
            }
            drawRoundRect(col, Offset(x0 + (i % 2) * (e + gap), y0 + (i / 2) * (e + gap)), Size(e, e), CornerRadius(e * 0.28f))
        }
    }

    private fun backdropArt(b: SettingsBackdrop): VisualArt = { ink ->
        val (o, sz) = phoneFrame(ink)
        val r = CornerRadius(dp(5f))
        when (b) {
            SettingsBackdrop.Wallpaper -> {
                drawRoundRect(Brush.linearGradient(listOf(Color(0xFF3B4C7A), Color(0xFF6C4A7A), Color(0xFFB36B5A)), Offset(o.x, o.y), Offset(o.x + sz.width, o.y + sz.height)), o, sz, r, alpha = 0.7f)
                drawRoundRect(Color(0x73000000), o, sz, r)
            }
            SettingsBackdrop.Black -> drawRoundRect(Color.Black, o, sz, r)
        }
        drawRoundRect(ink.soft, o, sz, r, style = Stroke(dp(1.5f)))
        for (i in 0 until 3) drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(o.x + sz.width * 0.14f, o.y + sz.height * (0.2f + i * 0.2f)), Size(sz.width * 0.72f, sz.height * 0.12f), CornerRadius(dp(3f)))
    }

    // ---- indicator effects ------------------------------------------------------------------------------------------------------------------------

    private val Healthy = Color(0xFF3DDC84)
    private val Warn = Color(0xFFFFB703)
    private val Bad = Color(0xFFFF5252)

    private fun calmArt(e: CalmStyle): VisualArt = { ink ->
        val box = phoneFrame(ink)
        val (o, sz) = box
        fun dotsAlong(xs: List<Pair<Float, Float>>) { for ((i, p) in xs.withIndex()) dotIn(box, Healthy.copy(alpha = 0.4f + 0.6f * ((i % 4) / 3f)), p.first, p.second, 1.2f + 1.0f * ((i % 4) / 3f)) }
        when (e) {
            CalmStyle.None -> { appGridIn(box, ink.faint, 2, 3); lineIn(box, ink.strong, 0.3f, 0.35f, 0.7f, 0.65f, 2f); lineIn(box, ink.strong, 0.7f, 0.35f, 0.3f, 0.65f, 2f) }
            CalmStyle.Regular -> {
                appGridIn(box, ink.faint, 2, 3, top = 0.3f)
                // signal bars and a small battery, as drawn at the top of the home
                for (i in 0 until 4) {
                    val bh = sz.height * (0.025f + i * 0.012f)
                    drawRoundRect(if (i < 3) ink.strong else ink.faint, Offset(o.x + sz.width * (0.2f + i * 0.07f), o.y + sz.height * 0.17f - bh), Size(sz.width * 0.05f, bh), CornerRadius(dp(0.8f)))
                }
                drawRoundRect(ink.strong, Offset(o.x + sz.width * 0.56f, o.y + sz.height * 0.115f), Size(sz.width * 0.24f, sz.height * 0.07f), CornerRadius(dp(1.5f)), style = Stroke(dp(1.1f)))
                drawRoundRect(Healthy, Offset(o.x + sz.width * 0.595f, o.y + sz.height * 0.13f), Size(sz.width * 0.12f, sz.height * 0.04f), CornerRadius(dp(0.8f)))
            }
            CalmStyle.EdgeDots -> {
                appGridIn(box, ink.faint, 2, 3)
                val pts = ArrayList<Pair<Float, Float>>()
                for (k in 0 until 9) { val f = 0.08f + k * 0.105f; pts += 0.07f to f; pts += 0.93f to f }
                for (k in 0 until 4) { val f = 0.25f + k * 0.17f; pts += f to 0.04f; pts += f to 0.96f }
                dotsAlong(pts)
            }
            CalmStyle.CornerBloom -> {
                appGridIn(box, ink.faint, 2, 3)
                // start angle of the quarter that points into the screen: top-left 0, top-right 90, bottom-right 180, bottom-left 270
                for ((cx, cy, start) in listOf(Triple(0f, 0f, 0f), Triple(1f, 0f, 90f), Triple(1f, 1f, 180f), Triple(0f, 1f, 270f))) {
                    val c = Offset(o.x + sz.width * cx, o.y + sz.height * cy)
                    for (k in 1..3) { val r = sz.width * 0.11f * k; drawArc(Healthy.copy(alpha = 0.8f - k * 0.22f), start, 90f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(dp(1.3f))) }
                    drawCircle(Healthy, dp(1.6f), c)
                }
            }
            CalmStyle.BottomGlow -> {
                appGridIn(box, ink.faint, 2, 3)
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Healthy.copy(alpha = 0.55f)), startY = o.y + sz.height * 0.8f, endY = o.y + sz.height), Offset(o.x, o.y + sz.height * 0.8f), Size(sz.width, sz.height * 0.2f))
                drawLine(Healthy, Offset(o.x + sz.width * 0.1f, o.y + sz.height - dp(1.2f)), Offset(o.x + sz.width * 0.9f, o.y + sz.height - dp(1.2f)), dp(1.6f))
            }
            CalmStyle.Beam -> {
                appGridIn(box, ink.faint, 2, 3)
                lineIn(box, ink.faint, 0.93f, 0.12f, 0.93f, 0.88f, 1.4f)
                lineIn(box, Healthy, 0.93f, 0.4f, 0.93f, 0.88f, 2.2f)
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Healthy.copy(alpha = 0.4f)), startX = o.x + sz.width * 0.8f, endX = o.x + sz.width * 0.93f), Offset(o.x + sz.width * 0.8f, o.y + sz.height * 0.4f), Size(sz.width * 0.13f, sz.height * 0.48f))
            }
        }
    }

    private fun metricArt(m: IndicatorMetric): VisualArt = { ink ->
        when (m) {
            IndicatorMetric.Signal -> for (i in 0 until 4) rect(if (i < 3) ink.strong else ink.faint, 0.26f + i * 0.14f, 0.7f - i * 0.15f, 0.09f, 0.15f * (i + 1) + 0.02f, 0.3f)
            IndicatorMetric.Battery -> {
                rect(ink.soft, 0.2f, 0.34f, 0.54f, 0.34f, 0.2f, stroke = 1.6f)
                rect(ink.soft, 0.76f, 0.44f, 0.06f, 0.14f, 0.3f)
                rect(Healthy, 0.24f, 0.4f, 0.32f, 0.22f, 0.15f)
            }
        }
    }

    // ---- Radar and wallpaper ----------------------------------------------------------------------------------------------------------------------

    /** A neutral magnifier. The engines are told apart by their name under the card: no logo of any search engine is drawn. */
    private fun webEngineArt(@Suppress("UNUSED_PARAMETER") e: WebEngine): VisualArt = { ink ->
        val c = Offset(size.width * 0.46f, size.height * 0.46f); val r = size.height * 0.2f
        drawCircle(ink.strong, r, c, style = Stroke(dp(2.4f)))
        drawLine(ink.strong, Offset(c.x + r * 0.72f, c.y + r * 0.72f), Offset(c.x + r * 1.75f, c.y + r * 1.75f), dp(3f), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawArc(ink.soft, 200f, 70f, false, Offset(c.x - r * 0.62f, c.y - r * 0.62f), Size(r * 1.24f, r * 1.24f), style = Stroke(dp(1.4f)))
    }

    private fun wallpaperKindArt(k: WallpaperKind): VisualArt = { ink ->
        val (o, sz) = phoneFrame(ink)
        val r = CornerRadius(dp(5f))
        when (k) {
            WallpaperKind.Solid -> drawRoundRect(ink.accent, o, sz, r)
            WallpaperKind.Gradient -> drawRoundRect(Brush.linearGradient(listOf(Color(0xFFFF8A65), Color(0xFF7C4DFF)), Offset(o.x, o.y), Offset(o.x + sz.width, o.y + sz.height)), o, sz, r)
            WallpaperKind.Live -> {
                drawRoundRect(Color(0xFF0B1220), o, sz, r)
                val c = Offset(o.x + sz.width / 2f, o.y + sz.height * 0.55f)
                for (i in 1..3) drawCircle(Color(0xFF42E8FF).copy(alpha = 0.7f - i * 0.15f), sz.width * 0.17f * i, c, style = Stroke(dp(1.2f)))
                drawCircle(Color(0xFF42E8FF), dp(2.4f), c)
            }
            WallpaperKind.System -> {
                drawRoundRect(Color(0xFF202028), o, sz, r)
                val c = Offset(o.x + sz.width / 2f, o.y + sz.height / 2f)
                for (i in 0 until 8) { val a = (PI / 4 * i).toFloat(); drawLine(Color.White.copy(alpha = 0.8f), Offset(c.x + dp(6f) * cos(a), c.y + dp(6f) * sin(a)), Offset(c.x + dp(9f) * cos(a), c.y + dp(9f) * sin(a)), dp(2f)) }
                drawCircle(Color.White.copy(alpha = 0.8f), dp(6f), c, style = Stroke(dp(1.8f)))
            }
            WallpaperKind.Picture -> {
                drawRoundRect(Color(0xFF3A5E8C), o, sz, r)
                val m = Path().apply {
                    moveTo(o.x, o.y + sz.height); lineTo(o.x + sz.width * 0.35f, o.y + sz.height * 0.45f); lineTo(o.x + sz.width * 0.6f, o.y + sz.height * 0.72f)
                    lineTo(o.x + sz.width * 0.78f, o.y + sz.height * 0.55f); lineTo(o.x + sz.width, o.y + sz.height * 0.8f); lineTo(o.x + sz.width, o.y + sz.height); close()
                }
                drawPath(m, Color(0xFF2C3E2E), style = androidx.compose.ui.graphics.drawscope.Fill)
                drawCircle(Color(0xFFFFE08A), dp(4f), Offset(o.x + sz.width * 0.74f, o.y + sz.height * 0.22f))
            }
        }
        drawRoundRect(ink.soft, o, sz, r, style = Stroke(dp(1.5f)))
    }

    private fun radarIntensityArt(i: RadarIntensity): VisualArt = { ink ->
        val amp = if (i == RadarIntensity.Subtle) 0.07f else 0.2f
        for (row in 0 until 3) {
            val p = Path()
            for (s in 0..24) {
                val x = size.width * (0.12f + 0.76f * s / 24f); val y = size.height * (0.28f + row * 0.22f) + size.height * amp * sin(s * 0.7f + row)
                if (s == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, if (row == 1) ink.strong else ink.soft, style = Stroke(dp(1.6f), join = StrokeJoin.Round))
        }
    }

    private fun usageRangeArt(r: UsageRange): VisualArt = { ink ->
        val n = if (r == UsageRange.Today) 1 else 7
        val bw = size.width * (if (n == 1) 0.2f else 0.075f); val gap = size.width * 0.03f
        val total = n * bw + (n - 1) * gap; val x0 = (size.width - total) / 2f
        for (k in 0 until n) {
            val h = size.height * (if (n == 1) 0.5f else 0.2f + 0.08f * ((k * 3) % 5))
            drawRoundRect(if (k == n - 1) ink.strong else ink.soft, Offset(x0 + k * (bw + gap), size.height * 0.8f - h), Size(bw, h), CornerRadius(dp(1.5f)))
        }
    }
}
