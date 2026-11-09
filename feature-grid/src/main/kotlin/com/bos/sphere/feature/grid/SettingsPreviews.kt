package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.ClockPosition
import com.bos.sphere.core.data.CornerStyle
import com.bos.sphere.core.data.DrawerView
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.IconShape
import com.bos.sphere.core.data.IconTint
import com.bos.sphere.core.data.PageShape
import com.bos.sphere.core.data.PagingAxis
import com.bos.sphere.core.data.ThemeMode
import com.bos.sphere.core.design.HyleColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Option previews for the settings detail screens. Every preview is drawn by the same code the home
 * screen uses (AppIcon with the real icon theme, GridGeometry, StatusStrip, ClockBlock, IndicatorLayer…)
 * fed with SANITIZED sample content — placeholder icons and values, never your real apps — so what you
 * see in the picker is what you get on the home screen.
 */

/** Placeholder apps for previews: generated glyph icons, no real app names or artwork. */
object SampleApps {
    private class GlyphDrawable(private val kind: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2FFFFFF.toInt() }
        override fun draw(c: Canvas) {
            val b = bounds
            val cx = b.exactCenterX(); val cy = b.exactCenterY(); val r = b.width() * 0.17f
            paint.style = Paint.Style.FILL
            when (kind % 8) {
                0 -> { paint.style = Paint.Style.STROKE; paint.strokeWidth = r * 0.5f; c.drawCircle(cx, cy, r, paint) }
                1 -> { val p = android.graphics.Path(); for (i in 0 until 8) { val a = Math.PI / 4 * i; val rr = if (i % 2 == 0) r * 1.2f else r * 0.45f; val x = cx + (rr * cos(a)).toFloat(); val y = cy + (rr * sin(a)).toFloat(); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }; p.close(); c.drawPath(p, paint) }
                2 -> { val p = android.graphics.Path(); p.moveTo(cx, cy - r); p.lineTo(cx + r, cy + r * 0.8f); p.lineTo(cx - r, cy + r * 0.8f); p.close(); c.drawPath(p, paint) }
                3 -> { paint.style = Paint.Style.STROKE; paint.strokeWidth = r * 0.45f; val p = android.graphics.Path(); p.moveTo(cx - r * 1.2f, cy); for (i in 1..12) { val t = i / 12f; p.lineTo(cx - r * 1.2f + t * r * 2.4f, cy + (sin(t * Math.PI * 2) * r * 0.6f).toFloat()) }; c.drawPath(p, paint) }
                4 -> c.drawRoundRect(cx - r, cy - r, cx + r, cy + r, r * 0.35f, r * 0.35f, paint)
                5 -> { val p = android.graphics.Path(); p.moveTo(cx, cy - r * 1.2f); p.lineTo(cx + r, cy); p.lineTo(cx, cy + r * 1.2f); p.lineTo(cx - r, cy); p.close(); c.drawPath(p, paint) }
                6 -> for (i in -1..1) c.drawCircle(cx + i * r * 0.95f, cy, r * 0.28f, paint)
                else -> for (i in -1..1) c.drawRoundRect(cx + i * r * 0.9f - r * 0.25f, cy - r * (0.7f + 0.3f * (i + 1)), cx + i * r * 0.9f + r * 0.25f, cy + r, r * 0.2f, r * 0.2f, paint)
            }
        }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private val palette = longArrayOf(
        0xFF6D5BFF, 0xFFFF6B6B, 0xFF2EC4B6, 0xFFFFB703, 0xFF3A86FF, 0xFFE76F51, 0xFF8338EC, 0xFF06D6A0,
        0xFFEF476F, 0xFF118AB2, 0xFFF4A261, 0xFF9B5DE5,
    )

    val list: List<AppEntry> by lazy {
        val user = Process.myUserHandle()
        palette.mapIndexed { i, c ->
            AppEntry(
                packageName = "sample.$i",
                componentName = ComponentName("sample.$i", "Sample"),
                label = "App ${i + 1}",
                user = user,
                iconLoader = { AdaptiveIconDrawable(ColorDrawable(c.toInt()), GlyphDrawable(i)) },
            )
        }
    }

    fun color(i: Int): Color = Color(palette[i % palette.size].toInt())
}

/** A small phone-shaped frame for previews. */
@Composable
fun MiniPhone(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .aspectRatio(9f / 17f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
            .border(1.dp, HyleColors.InsetHighlight, RoundedCornerShape(14.dp)),
        content = content,
    )
}

/** A selectable card: a live preview over a caption; the selected one glows in the accent colour. */
@Composable
fun PreviewCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    previewHeight: Dp = 84.dp,
    preview: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) HyleColors.Violet.copy(alpha = 0.14f) else HyleColors.Surface.copy(alpha = 0.7f))
            .border(if (selected) 2.dp else 1.dp, if (selected) HyleColors.Violet else HyleColors.Hairline, shape)
            .clickable(onClick = onClick)
            .semantics { role = Role.RadioButton; this.selected = selected; contentDescription = label }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(previewHeight), contentAlignment = Alignment.Center, content = preview)
        Text(
            label,
            color = if (selected) HyleColors.InkBright else HyleColors.Ink,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** A grid of [PreviewCard]s for a single-choice setting. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> VisualChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    columns: Int = 3,
    previewHeight: Dp = 84.dp,
    preview: @Composable BoxScope.(T) -> Unit,
) {
    FlowRow(
        maxItemsInEachRow = columns,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        options.forEach { o ->
            PreviewCard(label(o), o == selected, { onSelect(o) }, Modifier.weight(1f), previewHeight) { preview(o) }
        }
        repeat((columns - options.size % columns) % columns) { Spacer(Modifier.weight(1f)) }
    }
}

/** A grid of [PreviewCard]s for a multi-choice setting. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> VisualMultiChoice(
    options: List<T>,
    selected: Set<T>,
    onToggle: (T, Boolean) -> Unit,
    label: (T) -> String,
    columns: Int = 3,
    previewHeight: Dp = 84.dp,
    preview: @Composable BoxScope.(T) -> Unit,
) {
    FlowRow(
        maxItemsInEachRow = columns,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        options.forEach { o ->
            val on = o in selected
            PreviewCard(label(o), on, { onToggle(o, !on) }, Modifier.weight(1f), previewHeight) { preview(o) }
        }
        repeat((columns - options.size % columns) % columns) { Spacer(Modifier.weight(1f)) }
    }
}

// ----------------------------------------------------------------------------------------------
// Real-component previews
// ----------------------------------------------------------------------------------------------

/** One sample icon drawn by the real [AppIcon] with the given theme overrides. */
@Composable
fun ThemedSampleIcon(size: Dp, index: Int = 0, shape: IconShape? = null, tint: IconTint? = null, plate: Boolean? = null) {
    val base = LocalIconTheme.current
    val theme = base.copy(shape = shape ?: base.shape, tint = tint ?: base.tint, plate = plate ?: base.plate)
    CompositionLocalProvider(LocalIconTheme provides theme) { AppIcon(SampleApps.list[index % SampleApps.list.size], size) }
}

/** A tiny home screen of the given style (sample apps). */
@Composable
fun HomeStylePreview(style: HomeStyle) {
    when (style) {
        HomeStyle.Grid -> ComposeCanvas(Modifier.fillMaxSize().padding(6.dp)) {
            val cols = 3; val rows = 4; val cell = min(size.width / cols, size.height / rows); val icon = cell * 0.74f
            for (r in 0 until rows) for (c in 0 until cols) {
                val i = r * cols + c
                drawRoundRect(SampleApps.color(i), Offset((size.width - cell * cols) / 2 + c * cell + (cell - icon) / 2, r * cell + (cell - icon) / 2), Size(icon, icon), CornerRadius(icon * 0.25f))
            }
        }
        HomeStyle.Psp -> XmbPreview()
        HomeStyle.Dsi -> DsiPreview()
        HomeStyle.List -> ComposeCanvas(Modifier.fillMaxSize().padding(6.dp)) {
            val rowH = size.height / 6f
            for (i in 0 until 5) {
                val y = i * rowH + rowH * 0.5f
                drawRoundRect(SampleApps.color(i), Offset(2f, y - rowH * 0.3f), Size(rowH * 0.6f, rowH * 0.6f), CornerRadius(rowH * 0.15f))
                drawRoundRect(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f), Offset(rowH * 0.9f, y - 2f), Size(size.width * 0.5f, 4f), CornerRadius(2f))
            }
            drawRoundRect(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f), Offset(size.width - 4f, 8f), Size(3f, size.height * 0.5f), CornerRadius(2f))
        }
    }
}

/** A tiny page of the given layout shape. */
@Composable
fun PageShapePreview(shape: PageShape) {
    when (shape) {
        PageShape.Grid -> HomeStylePreview(HomeStyle.Grid)
        PageShape.Coverflow -> ComposeCanvas(Modifier.fillMaxSize().padding(4.dp)) {
            // The focused card big in the middle, neighbours shrinking and overlapping outward.
            val cx = size.width / 2; val cy = size.height / 2; val big = size.width * 0.3f
            for (d in listOf(-3, 3, -2, 2, -1, 1, 0)) {
                val a = kotlin.math.abs(d)
                val sz = big * (1f - 0.16f * minOf(a, 1) - 0.06f * (a - 1).coerceAtLeast(0))
                val x = cx + kotlin.math.sign(d.toFloat()) * (big * 0.62f * minOf(a, 1) + big * 0.36f * (a - 1).coerceAtLeast(0))
                drawRoundRect(SampleApps.color(d + 4), Offset(x - sz / 2, cy - sz / 2), Size(sz, sz), CornerRadius(sz * 0.26f), alpha = 1f - 0.12f * a)
            }
        }
        PageShape.Circle -> ComposeCanvas(Modifier.fillMaxSize().padding(4.dp)) {
            val cx = size.width / 2; val cy = size.height / 2; val r1 = size.width * 0.27f; val ic = size.width * 0.17f
            drawCircle(Color.White.copy(alpha = 0.12f), r1, Offset(cx, cy), style = Stroke(1f))
            drawRoundRect(SampleApps.color(0), Offset(cx - ic / 2, cy - ic / 2), Size(ic, ic), CornerRadius(ic * 0.3f))
            for (k in 0 until 6) {
                val a = Math.PI / 3 * k - Math.PI / 2
                drawRoundRect(SampleApps.color(k + 1), Offset(cx + (r1 * cos(a)).toFloat() - ic / 2, cy + (r1 * sin(a)).toFloat() - ic / 2), Size(ic, ic), CornerRadius(ic * 0.3f))
            }
        }
    }
}

/** A grid of [cols]×[rows] with the real geometry, icon scale and spacing. */
@Composable
fun DensityPreview(
    cols: Int, rows: Int, iconScale: Float, spacing: Float, modifier: Modifier = Modifier,
    spacingV: Float = spacing, insetX: Float = 0f, insetTop: Float = 0f, insetBottom: Float = 0f,
) {
    ComposeCanvas(modifier.fillMaxSize().padding(4.dp)) {
        val g = GridGeometry.compute(size.width, size.height, cols, rows, verticalBias = 0.5f, spread = spacing, spreadY = spacingV, insetX = insetX, insetTop = insetTop, insetBottom = insetBottom)
        val icon = g.base * iconScale
        for (i in 0 until g.capacity) {
            drawRoundRect(SampleApps.color(i), Offset(g.centreX(i) - icon / 2, g.centreY(i) - icon / 2), Size(icon, icon), CornerRadius(icon * 0.24f))
        }
    }
}

@Composable
fun PagingPreview(axis: PagingAxis) {
    ComposeCanvas(Modifier.fillMaxSize().padding(8.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val col = Color.White.copy(alpha = 0.85f)
        val len = size.minDimension * 0.42f
        if (axis == PagingAxis.Horizontal) {
            drawLine(col, Offset(c.x - len, c.y), Offset(c.x + len, c.y), 3f)
            drawLine(col, Offset(c.x + len, c.y), Offset(c.x + len - 8f, c.y - 8f), 3f); drawLine(col, Offset(c.x + len, c.y), Offset(c.x + len - 8f, c.y + 8f), 3f)
            for (i in -1..1) drawCircle(col.copy(alpha = if (i == 0) 0.9f else 0.3f), 3f, Offset(c.x + i * 12f, c.y + len * 0.7f))
        } else {
            drawLine(col, Offset(c.x, c.y - len), Offset(c.x, c.y + len), 3f)
            drawLine(col, Offset(c.x, c.y + len), Offset(c.x - 8f, c.y + len - 8f), 3f); drawLine(col, Offset(c.x, c.y + len), Offset(c.x + 8f, c.y + len - 8f), 3f)
            for (i in -1..1) drawCircle(col.copy(alpha = if (i == 0) 0.9f else 0.3f), 3f, Offset(c.x - len * 0.7f, c.y + i * 12f))
        }
    }
}

/** Where the strip sits on a mini screen. */
@Composable
fun StripPositionPreview(position: ClockPosition) {
    MiniPhone(Modifier.fillMaxSize()) {
        if (position != ClockPosition.Hidden) {
            Text(
                "12:41",
                color = HyleColors.InkBright,
                fontSize = 13.sp,
                modifier = Modifier.align(if (position == ClockPosition.Top) Alignment.TopCenter else Alignment.BottomCenter).padding(vertical = 6.dp),
            )
        }
        Box(Modifier.align(Alignment.Center).size(width = 30.dp, height = 36.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp)))
    }
}

@Composable
fun CornerStylePreview(style: CornerStyle) {
    MiniPhone(Modifier.fillMaxSize()) {
        for (al in listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd)) {
            Box(Modifier.align(al)) {
                if (style == CornerStyle.Bubble) {
                    ComposeCanvas(Modifier.size(26.dp)) {
                        val c = Offset(if (al == Alignment.TopStart || al == Alignment.BottomStart) 0f else size.width, if (al == Alignment.TopStart || al == Alignment.TopEnd) 0f else size.height)
                        drawCircle(HyleColors.SurfaceTranslucent, size.width, c); drawCircle(HyleColors.InsetHighlight, size.width, c, style = Stroke(1f))
                    }
                }
                Box(Modifier.padding(5.dp).size(9.dp).background(HyleColors.InkBright, CircleShape))
            }
        }
    }
}

@Composable
fun DrawerViewPreview(view: DrawerView) {
    ComposeCanvas(Modifier.fillMaxSize().padding(6.dp)) {
        when (view) {
            DrawerView.Grid -> for (r in 0 until 4) for (c in 0 until 3) { val s = size.width / 3.6f; drawRoundRect(SampleApps.color(r * 3 + c), Offset(c * (s + 6f), r * (s + 6f)), Size(s, s), CornerRadius(s * 0.25f)) }
            DrawerView.List -> for (r in 0 until 5) { val h = size.height / 6f; drawRoundRect(SampleApps.color(r), Offset(0f, r * (h + 4f)), Size(h, h), CornerRadius(h * 0.25f)); drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(h + 8f, r * (h + 4f) + h * 0.3f), Size(size.width * 0.5f, h * 0.35f), CornerRadius(4f)) }
            DrawerView.Details -> for (r in 0 until 4) { val h = size.height / 5f; drawRoundRect(SampleApps.color(r), Offset(0f, r * (h + 5f)), Size(h, h), CornerRadius(h * 0.25f)); drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(h + 8f, r * (h + 5f) + h * 0.12f), Size(size.width * 0.5f, h * 0.28f), CornerRadius(4f)); drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(h + 8f, r * (h + 5f) + h * 0.55f), Size(size.width * 0.38f, h * 0.22f), CornerRadius(4f)) }
        }
    }
}

/** The three theme modes as little palettes. */
@Composable
fun ThemeModePreview(mode: ThemeMode, accent: Color) {
    val (bg, surface, ink) = when (mode) {
        ThemeMode.Dark -> Triple(Color(0xFF0A0B10), Color(0xFF161922), Color(0xFFE7E9F0))
        ThemeMode.Amoled -> Triple(Color(0xFF000000), Color(0xFF0B0C10), Color(0xFFE7E9F0))
        ThemeMode.Light -> Triple(Color(0xFFF3F4F8), Color(0xFFFFFFFF), Color(0xFF0E1018))
    }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(bg).padding(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.fillMaxWidth().height(14.dp).background(surface, RoundedCornerShape(5.dp)))
            Box(Modifier.size(width = 36.dp, height = 6.dp).background(ink.copy(alpha = 0.8f), RoundedCornerShape(3.dp)))
            Box(Modifier.size(width = 22.dp, height = 12.dp).background(accent, RoundedCornerShape(5.dp)))
        }
    }
}

/** A glyph + label tile for choices that are better shown by an icon than a picture. */
@Composable
fun GlyphPreview(glyph: androidx.compose.ui.graphics.vector.ImageVector, caption: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(glyph, null, tint = HyleColors.InkBright, modifier = Modifier.size(30.dp))
        if (caption != null) Text(caption, color = HyleColors.InkDim, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

/** A miniature of a search layout: the category wheel, the query, and where the results sit. */
@Composable
fun SearchLayoutPreview(layout: com.bos.sphere.core.data.SearchLayout) {
    MiniPhone(Modifier.fillMaxSize()) {
        // category wheel + query
        Column(Modifier.align(Alignment.Center).padding(start = 26.dp, bottom = if (layout == com.bos.sphere.core.data.SearchLayout.Flat) 0.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(0.25f, 0.55f, 1f, 0.55f, 0.25f).forEach { a ->
                Box(Modifier.size(width = 26.dp, height = 3.dp).background(Color.White.copy(alpha = a), RoundedCornerShape(2.dp)))
            }
        }
        when (layout) {
            com.bos.sphere.core.data.SearchLayout.Filmstrip -> Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(4) { ThemedSampleIcon(16.dp, it) }
            }
            com.bos.sphere.core.data.SearchLayout.Orbit -> {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.3f)
                    listOf(0.18f, 0.32f, 0.46f).forEach { r -> drawCircle(Color.White.copy(alpha = 0.25f), r * size.width, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1f)) }
                }
                Box(Modifier.align(Alignment.TopCenter).padding(top = 20.dp)) { ThemedSampleIcon(14.dp, 3) }
                Row(Modifier.align(Alignment.TopCenter).padding(top = 42.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { ThemedSampleIcon(12.dp, 5); ThemedSampleIcon(12.dp, 7) }
            }
            com.bos.sphere.core.data.SearchLayout.Flat -> Column(Modifier.align(Alignment.TopCenter).padding(top = 14.dp, start = 8.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(4) { Box(Modifier.fillMaxWidth().height(5.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))) }
            }
        }
    }
}
