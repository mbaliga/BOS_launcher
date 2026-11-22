package com.bos.sphere.feature.grid

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerIconMode
import com.bos.sphere.core.data.CornerStyle
import com.bos.sphere.core.data.DensityPreset
import com.bos.sphere.core.data.GlanceHotspots
import com.bos.sphere.core.data.HomeOrder
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.HotspotPreset
import com.bos.sphere.core.data.LeftPage
import com.bos.sphere.core.data.PageMode
import com.bos.sphere.core.data.PageShape
import com.bos.sphere.core.data.PagingAxis
import com.bos.sphere.core.data.RightSide
import com.bos.sphere.core.data.ScrollAxis
import com.bos.sphere.core.data.SearchLayout
import com.bos.sphere.core.data.RadarBackground
import com.bos.sphere.core.data.WheelGrouping
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Wave 54 (settingsVisual): the illustrations of the Home, Pages, Corners, Radar and App drawer choices. Each provider is an [ArtSet] over every
 * value of its enum (a new value without a drawing does not compile, and the coverage test draws each one). Static, no animation.
 */
object SettingsArtHome {
    val pages = ArtSet(HomePages.entries) { pagesArt(it) }
    val order = ArtSet(HomeOrder.entries) { orderArt(it.rtl) }
    val rightSide = ArtSet(RightSide.entries) { rightSideArt(it) }
    val homeStyle = ArtSet(HomeStyle.entries) { homeStyleArt(it) }
    val pageShape = ArtSet(PageShape.entries) { pageShapeArt(it) }
    val paging = ArtSet(PagingAxis.entries) { pagingArt(it) }
    val scrollAxis = ArtSet(ScrollAxis.entries) { scrollAxisArt(it) }
    val density = ArtSet(DensityPreset.entries) { densityArt(it) }
    val hotspots = ArtSet(HotspotPreset.entries) { hotspotsPresetArt(it) }
    val cornerStyle = ArtSet(CornerStyle.entries) { cornerStyleArt(it) }
    val cornerIcon = ArtSet(CornerIconMode.entries) { cornerIconArt(it) }
    val leftPage = ArtSet(LeftPage.entries) { leftPageArt(it) }
    val searchLayout = ArtSet(SearchLayout.entries) { searchLayoutArt(it) }
    val radarBackground = ArtSet(RadarBackground.entries) { radarBackgroundArt(it) }
    val wheelGrouping = ArtSet(WheelGrouping.entries) { wheelGroupingArt(it) }
    val pageMode = ArtSet(PageMode.entries) { pageModeArt(it) }
    /** The shortcut ring of the all-apps disc with 6, 7 or 8 segments (every size the setting offers). */
    val ringSegments = ArtSet(com.bos.sphere.core.data.PuckActions.SEGMENT_CHOICES) { ringArt(it) }

    // ---- Pages: Off, Paginated, Continuous (shared with the Home quick sheet) ----------------------------------------------------------------------

    fun pagesArt(mode: HomePages): VisualArt = { ink ->
        val box = phoneFrame(ink)
        when (mode) {
            HomePages.Off -> appGridIn(box, ink.soft, 2, 3)
            HomePages.Paginated -> {
                appGridIn(box, ink.soft, 2, 3)
                for (i in 0 until 3) dotIn(box, if (i == 0) ink.strong else ink.faint, 0.5f + (i - 1) * 0.16f, 0.93f, if (i == 0) 1.9f else 1.6f)
            }
            HomePages.Continuous -> {
                val (o, sz) = box
                val gap = sz.width * 0.1f; val cell = sz.width * 0.26f
                for (c in 0 until 3) for (r in 0 until 3) {
                    val x = o.x + sz.width * 0.12f + c * (cell + gap)
                    if (x + cell <= o.x + sz.width - dp(1f)) drawRoundRect(ink.soft, Offset(x, o.y + sz.height * 0.14f + r * (cell + gap)), Size(cell, cell), CornerRadius(cell * 0.3f))
                }
                val y = o.y + sz.height - dp(7f)
                drawLine(ink.strong, Offset(o.x + sz.width * 0.2f, y), Offset(o.x + sz.width * 0.8f, y), dp(2f))
                val head = Path().apply { moveTo(o.x + sz.width * 0.8f, y - dp(3f)); lineTo(o.x + sz.width * 0.8f + dp(4f), y); lineTo(o.x + sz.width * 0.8f, y + dp(3f)) }
                drawPath(head, ink.strong, style = Stroke(dp(1.5f)))
            }
        }
    }

    /** Order: three page tiles and an arrow; the first page is filled and sits at the start side. Physical coordinates, so right to left points left. */
    fun orderArt(rtl: Boolean): VisualArt = { ink ->
        val s = size
        val tw = s.width * 0.2f; val th = s.height * 0.3f; val gap = s.width * 0.07f
        val total = 3 * tw + 2 * gap; val x0 = (s.width - total) / 2f; val y0 = s.height * 0.3f
        for (i in 0 until 3) {
            val first = if (rtl) i == 2 else i == 0
            val x = x0 + i * (tw + gap)
            if (first) drawRoundRect(ink.strong, Offset(x, y0), Size(tw, th), CornerRadius(dp(4f)))
            else drawRoundRect(ink.soft, Offset(x, y0), Size(tw, th), CornerRadius(dp(4f)), style = Stroke(dp(1.5f)))
        }
        val y = s.height * 0.8f; val a = x0; val b = x0 + total
        val (from, to) = if (rtl) b to a else a to b
        drawLine(ink.strong, Offset(from, y), Offset(to, y), dp(2f))
        val d = if (rtl) dp(4f) else -dp(4f)
        val head = Path().apply { moveTo(to + d, y - dp(3.5f)); lineTo(to, y); lineTo(to + d, y + dp(3.5f)) }
        drawPath(head, ink.strong, style = Stroke(dp(2f)))
    }

    /** Hotspots: the phone with a dot in each corner the preset turns on (shared with the Home quick sheet). */
    fun hotspotsArt(on: Set<Corner>): VisualArt = { ink ->
        val (o, sz) = phoneFrame(ink)
        val inset = dp(6f); val r = dp(3.4f)
        for (c in Corner.entries) {
            val cx = if (c == Corner.TopLeft || c == Corner.BottomLeft) o.x + inset else o.x + sz.width - inset
            val cy = if (c == Corner.TopLeft || c == Corner.TopRight) o.y + inset else o.y + sz.height - inset
            if (c in on) drawCircle(ink.strong, r, Offset(cx, cy)) else drawCircle(ink.faint, r * 0.7f, Offset(cx, cy), style = Stroke(dp(1f)))
        }
    }

    private fun hotspotsPresetArt(p: HotspotPreset): VisualArt = hotspotsArt(GlanceHotspots.preset(p) ?: setOf(Corner.TopLeft, Corner.BottomRight))

    // ---- Right side: the panel, the dock rail, nothing --------------------------------------------------------------------------------------------

    private fun rightSideArt(r: RightSide): VisualArt = { ink ->
        val box = phoneFrame(ink)
        arrow(ink.strong, 0.98f, 0.5f, 0.78f, 0.5f, 1.6f) // the swipe in from the right edge
        when (r) {
            RightSide.RecentsPanel -> {
                inBox(box, ink.faint, 0.1f, 0.14f, 0.34f, 0.72f, 0.15f)
                inBox(box, ink.strong, 0.5f, 0.14f, 0.4f, 0.72f, 0.12f)
                for (i in 0 until 3) inBox(box, ink.tile, 0.58f, 0.22f + i * 0.2f, 0.24f, 0.12f, 0.3f)
            }
            RightSide.DockRail -> {
                inBox(box, ink.faint, 0.12f, 0.14f, 0.5f, 0.72f, 0.1f)
                inBox(box, ink.strong, 0.78f, 0.2f, 0.14f, 0.6f, 0.45f)
                for (i in 0 until 3) dotIn(box, ink.tile, 0.85f, 0.32f + i * 0.17f, 1.7f)
            }
            RightSide.Off -> {
                appGridIn(box, ink.faint, 2, 3)
                line(ink.strong, 0.58f, 0.4f, 0.74f, 0.6f, 2f); line(ink.strong, 0.74f, 0.4f, 0.58f, 0.6f, 2f)
            }
        }
    }

    // ---- Home style and page layout ---------------------------------------------------------------------------------------------------------------

    private fun homeStyleArt(s: HomeStyle): VisualArt = { ink ->
        val box = phoneFrame(ink)
        when (s) {
            HomeStyle.Grid -> appGridIn(box, ink.soft, 3, 4, top = 0.1f, pad = 0.12f, gap = 0.08f, accentCell = 4, accent = ink.strong)
            HomeStyle.List -> {
                for (i in 0 until 5) {
                    val y = 0.12f + i * 0.17f
                    inBox(box, if (i == 1) ink.strong else ink.soft, 0.12f, y, 0.16f, 0.11f, 0.3f)
                    inBox(box, ink.faint, 0.36f, y + 0.03f, 0.4f, 0.05f, 0.5f)
                }
                inBox(box, ink.faint, 0.9f, 0.12f, 0.03f, 0.7f, 0.5f)
            }
            HomeStyle.Psp -> {
                for (i in 0 until 5) dotIn(box, if (i == 1) ink.strong else ink.faint, 0.16f + i * 0.17f, 0.22f, if (i == 1) 3.2f else 2.4f)
                for (i in 0 until 3) {
                    inBox(box, if (i == 0) ink.strong else ink.soft, 0.3f, 0.42f + i * 0.17f, 0.12f, 0.1f, 0.3f)
                    inBox(box, ink.faint, 0.48f, 0.455f + i * 0.17f, 0.34f, 0.04f, 0.5f)
                }
            }
            HomeStyle.Dsi -> {
                appGridIn(box, ink.soft, 2, 3, top = 0.12f, pad = 0.12f, gap = 0.12f)
                line(ink.strong, 0.86f, 0.3f, 0.86f, 0.7f, 1.8f)
            }
        }
    }

    private fun pageShapeArt(s: PageShape): VisualArt = { ink ->
        val box = phoneFrame(ink)
        when (s) {
            PageShape.Grid -> appGridIn(box, ink.soft, 3, 4, top = 0.1f, pad = 0.12f, gap = 0.08f)
            PageShape.Circle -> {
                val (o, sz) = box
                val c = Offset(o.x + sz.width / 2f, o.y + sz.height / 2f); val r = sz.width * 0.34f
                drawCircle(ink.faint, r, c, style = Stroke(dp(1f)))
                drawCircle(ink.strong, dp(3.6f), c)
                for (k in 0 until 6) { val a = PI / 3 * k - PI / 2; drawCircle(ink.soft, dp(2.6f), Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat())) }
            }
            PageShape.Coverflow -> {
                for (d in listOf(-2, 2, -1, 1, 0)) {
                    val a = kotlin.math.abs(d)
                    val h = 0.36f - 0.08f * a; val w = 0.2f - 0.04f * a
                    inBox(box, if (d == 0) ink.strong else ink.soft, 0.5f + d * 0.19f - w / 2f, 0.5f - h / 2f, w, h, 0.2f)
                }
            }
        }
    }

    private fun pagingArt(a: PagingAxis): VisualArt = { ink ->
        val box = phoneFrame(ink)
        if (a == PagingAxis.Horizontal) {
            appGridIn(box, ink.faint, 2, 2, top = 0.18f)
            arrow(ink.strong, 0.22f, 0.7f, 0.8f, 0.7f)
            for (i in 0 until 3) dotIn(box, if (i == 1) ink.strong else ink.faint, 0.5f + (i - 1) * 0.16f, 0.9f, 1.6f)
        } else {
            appGridIn(box, ink.faint, 2, 2, top = 0.14f)
            arrow(ink.strong, 0.5f, 0.8f, 0.5f, 0.45f)
            for (i in 0 until 3) dotIn(box, if (i == 1) ink.strong else ink.faint, 0.92f, 0.4f + i * 0.14f, 1.6f)
        }
    }

    private fun scrollAxisArt(a: ScrollAxis): VisualArt = { ink ->
        when (a) {
            ScrollAxis.Long -> {
                val box = phoneFrame(ink)
                appGridIn(box, ink.faint, 2, 3)
                arrow(ink.strong, 0.5f, 0.82f, 0.5f, 0.3f)
                // the phone lying down, ghosted: the long side follows the phone
                rect(ink.faint, 0.64f, 0.58f, 0.3f, 0.2f, 0.2f, stroke = 1f)
            }
            ScrollAxis.Short -> {
                val box = phoneFrame(ink)
                appGridIn(box, ink.faint, 2, 3)
                arrow(ink.strong, 0.22f, 0.5f, 0.78f, 0.5f)
            }
            ScrollAxis.Vertical -> {
                val box = phoneFrame(ink)
                appGridIn(box, ink.faint, 2, 3)
                arrow(ink.strong, 0.5f, 0.3f, 0.5f, 0.82f)
            }
            ScrollAxis.Horizontal -> {
                val box = phoneFrame(ink, wide = true)
                appGridIn(box, ink.faint, 4, 1, top = 0.2f, pad = 0.1f)
                arrow(ink.strong, 0.3f, 0.78f, 0.7f, 0.78f)
            }
        }
    }

    private fun densityArt(d: DensityPreset): VisualArt = { ink ->
        val box = phoneFrame(ink)
        val (o, sz) = box
        val cols = d.portrait.columns; val rows = d.portrait.rows
        val padX = sz.width * 0.1f; val padY = sz.height * 0.08f
        val cell = minOf((sz.width - 2 * padX) / cols, (sz.height - 2 * padY) / rows)
        val ic = cell * 0.62f
        val gx = (sz.width - cell * cols) / 2f; val gy = (sz.height - cell * rows) / 2f
        for (r in 0 until rows) for (c in 0 until cols) drawRoundRect(ink.soft, Offset(o.x + gx + c * cell + (cell - ic) / 2f, o.y + gy + r * cell + (cell - ic) / 2f), Size(ic, ic), CornerRadius(ic * 0.3f))
    }

    // ---- Corners ----------------------------------------------------------------------------------------------------------------------------------

    private fun cornerStyleArt(s: CornerStyle): VisualArt = { ink ->
        val (o, sz) = phoneFrame(ink)
        for (c in Corner.entries) {
            val left = c == Corner.TopLeft || c == Corner.BottomLeft; val top = c == Corner.TopLeft || c == Corner.TopRight
            val cx = if (left) o.x else o.x + sz.width; val cy = if (top) o.y else o.y + sz.height
            val ix = if (left) o.x + dp(8f) else o.x + sz.width - dp(8f); val iy = if (top) o.y + dp(8f) else o.y + sz.height - dp(8f)
            when (s) {
                CornerStyle.Glyph -> Unit
                CornerStyle.Quarter -> clipRect(o.x, o.y, o.x + sz.width, o.y + sz.height) { drawCircle(ink.faint, dp(15f), Offset(cx, cy)); drawCircle(ink.soft, dp(15f), Offset(cx, cy), style = Stroke(dp(1f))) }
                CornerStyle.Bubble -> { drawCircle(ink.faint, dp(7f), Offset(ix, iy)); drawCircle(ink.soft, dp(7f), Offset(ix, iy), style = Stroke(dp(1f))) }
            }
            drawCircle(ink.strong, dp(2.6f), Offset(ix, iy))
        }
    }

    private fun cornerIconArt(m: CornerIconMode): VisualArt = { ink ->
        when (m) {
            CornerIconMode.Glyph -> {
                // a plain line glyph (a bell) in the ink
                val c = Offset(size.width / 2f, size.height * 0.48f); val r = dp(13f)
                val bell = Path().apply {
                    moveTo(c.x - r, c.y + r * 0.55f); lineTo(c.x - r * 0.8f, c.y + r * 0.55f)
                    cubicTo(c.x - r * 0.8f, c.y - r * 0.2f, c.x - r * 0.7f, c.y - r, c.x, c.y - r)
                    cubicTo(c.x + r * 0.7f, c.y - r, c.x + r * 0.8f, c.y - r * 0.2f, c.x + r * 0.8f, c.y + r * 0.55f)
                    lineTo(c.x + r, c.y + r * 0.55f)
                }
                drawPath(bell, ink.strong, style = Stroke(dp(2f), join = StrokeJoin.Round))
                drawCircle(ink.strong, dp(2.2f), Offset(c.x, c.y + r * 0.95f))
            }
            CornerIconMode.AppIcon -> {
                val cols = listOf(Color(0xFFFF6B6B), Color(0xFF3A86FF), Color(0xFF06D6A0))
                for (i in 0 until 3) drawRoundRect(cols[i], Offset(size.width * (0.2f + i * 0.22f), size.height * 0.34f), Size(dp(15f), dp(15f)), CornerRadius(dp(4.5f)))
            }
        }
    }

    // ---- Side panel -------------------------------------------------------------------------------------------------------------------------------

    private fun leftPageArt(p: LeftPage): VisualArt = { ink ->
        val box = phoneFrame(ink)
        arrow(ink.strong, 0.2f, 0.5f, 0.42f, 0.5f, 1.6f) // swipe right from the first page
        when (p) {
            LeftPage.Off -> { appGridIn(box, ink.faint, 2, 3); line(ink.strong, 0.58f, 0.4f, 0.76f, 0.6f, 2f); line(ink.strong, 0.76f, 0.4f, 0.58f, 0.6f, 2f) }
            LeftPage.Launchpad -> for (i in 0 until 4) { inBox(box, ink.soft, 0.52f, 0.14f + i * 0.2f, 0.36f, 0.14f, 0.3f); dotIn(box, ink.strong, 0.58f, 0.21f + i * 0.2f, 1.5f) }
            LeftPage.Widget -> { inBox(box, ink.soft, 0.52f, 0.14f, 0.36f, 0.34f, 0.15f); inBox(box, ink.strong, 0.52f, 0.54f, 0.36f, 0.3f, 0.15f, stroke = 1.2f) }
        }
    }

    // ---- Radar ------------------------------------------------------------------------------------------------------------------------------------

    private fun searchLayoutArt(l: SearchLayout): VisualArt = { ink ->
        val box = phoneFrame(ink)
        inBox(box, ink.soft, 0.14f, 0.08f, 0.72f, 0.1f, 0.5f) // the query
        when (l) {
            SearchLayout.Filmstrip -> {
                for (i in 0 until 3) inBox(box, if (i == 1) ink.strong else ink.soft, 0.1f + i * 0.28f, 0.42f, 0.24f, 0.2f, 0.3f)
                for (i in 0 until 3) inBox(box, ink.faint, 0.16f + i * 0.28f, 0.68f, 0.14f, 0.04f, 0.5f)
            }
            SearchLayout.Orbit -> {
                val (o, sz) = box
                val c = Offset(o.x + sz.width / 2f, o.y + sz.height * 0.78f)
                clipRect(o.x, o.y, o.x + sz.width, o.y + sz.height) { for (k in 1..3) drawCircle(ink.faint, sz.width * 0.2f * k, c, style = Stroke(dp(1f))) }
                dotIn(box, ink.strong, 0.5f, 0.52f, 3.4f); dotIn(box, ink.soft, 0.24f, 0.62f, 2.6f); dotIn(box, ink.soft, 0.78f, 0.58f, 2.6f)
            }
            SearchLayout.Flat -> for (i in 0 until 4) { inBox(box, ink.soft, 0.14f, 0.3f + i * 0.16f, 0.72f, 0.09f, 0.4f) }
        }
    }

    private fun radarBackgroundArt(b: RadarBackground): VisualArt = { ink ->
        val (o, sz) = phoneFrame(ink, fill = null)
        val shape = CornerRadius(dp(5f))
        when (b) {
            RadarBackground.Dimmed -> {
                drawRoundRect(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF3B4C7A), Color(0xFF6C4A7A)), Offset(o.x, o.y), Offset(o.x + sz.width, o.y + sz.height)), o, sz, shape, alpha = 0.55f)
                drawRoundRect(Color(0x99000000), o, sz, shape)
            }
            RadarBackground.TrueBlack -> drawRoundRect(Color.Black, o, sz, shape)
        }
        drawRoundRect(ink.soft, o, sz, shape, style = Stroke(dp(1.5f)))
        drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(o.x + sz.width * 0.16f, o.y + sz.height * 0.12f), Size(sz.width * 0.68f, sz.height * 0.08f), CornerRadius(dp(3f)))
        for (i in 0 until 3) drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(o.x + sz.width * 0.16f, o.y + sz.height * (0.34f + i * 0.14f)), Size(sz.width * 0.68f, sz.height * 0.07f), CornerRadius(dp(2.5f)))
    }

    // ---- App drawer -------------------------------------------------------------------------------------------------------------------------------

    private fun wheelGroupingArt(g: WheelGrouping): VisualArt = { ink ->
        val c = Offset(size.width / 2f, size.height / 2f); val r = size.height * 0.34f
        val hues = listOf(Color(0xFFFF6B6B), Color(0xFFFFB703), Color(0xFF06D6A0), Color(0xFF3A86FF))
        for (k in 0 until 8) {
            val a = (PI / 4 * k - PI / 2).toFloat()
            val ax = c.x + r * cos(a); val ay = c.y + r * sin(a)
            when (g) {
                // A to Z: spokes whose dots grow with the alphabet
                WheelGrouping.Letter -> drawCircle(if (k == 0) ink.strong else ink.soft, dp(1.6f + k * 0.28f), Offset(ax, ay))
                // by category: a colour per group of spokes
                WheelGrouping.Category -> drawCircle(hues[k / 2], dp(3.2f), Offset(ax, ay))
                // recent: the newest spoke is bright, the rest fade round the ring
                WheelGrouping.Recent -> drawCircle(ink.strong.copy(alpha = 1f - k * 0.11f), dp(3.2f), Offset(ax, ay))
            }
        }
        drawCircle(ink.faint, r, c, style = Stroke(dp(1f)))
        when (g) {
            WheelGrouping.Letter -> { line(ink.strong, 0.44f, 0.5f, 0.56f, 0.5f, 1.6f) }
            WheelGrouping.Category -> { dot(ink.soft, 0.5f, 0.5f, 3f, 1.4f) }
            WheelGrouping.Recent -> { line(ink.strong, 0.5f, 0.5f, 0.5f, 0.38f, 1.6f); line(ink.strong, 0.5f, 0.5f, 0.58f, 0.54f, 1.6f) }
        }
    }

    private fun pageModeArt(m: PageMode): VisualArt = { ink ->
        when (m) {
            PageMode.Multiple -> { rect(ink.faint, 0.2f, 0.2f, 0.34f, 0.6f, 0.12f, stroke = 1.2f); rect(ink.soft, 0.33f, 0.16f, 0.34f, 0.6f, 0.12f, stroke = 1.4f); rect(ink.strong, 0.46f, 0.2f, 0.34f, 0.6f, 0.12f) }
            PageMode.Single -> rect(ink.strong, 0.33f, 0.16f, 0.34f, 0.68f, 0.12f)
        }
    }

    /** The white ring around the Back hub: [n] segments with small gaps; the first is picked out. */
    fun ringArt(n: Int): VisualArt = { ink ->
        val c = Offset(size.width / 2f, size.height / 2f); val r = size.height * 0.36f
        val sweep = 360f / n; val gap = 7f
        for (i in 0 until n) {
            drawArc(
                if (i == 0) ink.strong else ink.soft, -90f + i * sweep + gap / 2f, sweep - gap, false,
                Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(dp(5f)),
            )
        }
        drawCircle(ink.faint, r * 0.42f, c)
        drawCircle(ink.strong, r * 0.14f, c)
    }
}
