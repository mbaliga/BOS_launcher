package com.bos.sphere.feature.grid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.CalmStyle
import com.bos.sphere.core.data.DockStyle
import com.bos.sphere.core.data.GradientSpec
import com.bos.sphere.core.data.IconShape
import com.bos.sphere.core.data.IconTint
import com.bos.sphere.core.data.LookPreset
import com.bos.sphere.core.data.ThemeMode
import com.bos.sphere.core.data.ThemeSettings
import com.bos.sphere.core.data.ThemeWallpaper
import com.bos.sphere.core.design.FontCatalog
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/** Wave 56 (looksPresets), CFG-26: test tags of the Looks rail. */
object LooksTags {
    const val RAIL = "looks.rail"
    const val APPLIED = "looks.applied"
    const val UNDO = "looks.undo"
    fun card(id: String) = "looks.card.$id"
}

/** The pure choices the miniature makes from a Look's values, so a test can hold them without pixels. */
object LookArt {
    /** The corner radius of one icon as a fraction of its side, per shape. */
    fun radius(s: IconShape): Float = when (s) { IconShape.Original -> 0.12f; IconShape.Rounded -> 0.26f; IconShape.Squircle -> 0.38f; IconShape.Circle -> 0.5f; IconShape.Square -> 0.04f }

    /** The ink on the wallpaper: light text on a dark look, dark text on a light one. */
    fun foreground(m: ThemeMode): Color = if (m == ThemeMode.Light) Color(0xFF1B1E29) else Color(0xFFF2F3F8)

    /** The behind-everything colour when a Look has no wallpaper of its own. */
    fun fallbackBackground(m: ThemeMode): Color = when (m) { ThemeMode.Light -> Color(0xFFF3F4F8); ThemeMode.Amoled -> Color.Black; ThemeMode.Dark -> Color(0xFF14151A) }

    private val Hues = listOf(Color(0xFFFF6B6B), Color(0xFF3A86FF), Color(0xFFFFB703), Color(0xFF06D6A0))

    /** The colour of the [i]th icon: its own hue, the accent, or the monochrome ink. */
    fun iconColour(t: IconTint, accent: Color, fg: Color, i: Int): Color = when (t) {
        IconTint.None -> Hues[i % Hues.size]
        IconTint.Accent -> accent.copy(alpha = 1f - (i % 3) * 0.14f)
        IconTint.Mono -> fg.copy(alpha = 0.9f - (i % 3) * 0.16f)
    }

    fun background(s: ThemeSettings): ThemeWallpaper? = s.wallpaper

    /** The artwork of a Look: a miniature home drawn from its own values (wallpaper, accent, shape, icon colour, plate, dock, calm style). */
    fun draw(scope: DrawScope, s: ThemeSettings) = with(scope) {
        val mode = s.themeMode ?: ThemeMode.Dark
        val accent = Color(((s.accent ?: 0xFF8A7BFF) or GradientSpec.OPAQUE).toInt())
        val fg = foreground(mode)
        val w = size.width; val h = size.height
        // wallpaper
        when (val wp = s.wallpaper) {
            is ThemeWallpaper.Solid -> drawRect(Color(wp.color.toInt()))
            is ThemeWallpaper.Gradient -> {
                val (a, b) = GradientGeometry.endpoints(w, h, wp.angle)
                drawRect(Brush.linearGradient(listOf(Color(wp.from.toInt()), Color(wp.to.toInt())), start = a, end = b))
            }
            null -> drawRect(fallbackBackground(mode))
        }
        // calm style, behind the icons like on the real home
        val calm = s.calmStyle ?: CalmStyle.None
        when (calm) {
            CalmStyle.EdgeDots -> {
                for (k in 0 until 9) { val y = h * (0.1f + k * 0.1f); drawCircle(accent.copy(alpha = 0.5f), 1.2f * density, Offset(w * 0.04f, y)); drawCircle(accent.copy(alpha = 0.5f), 1.2f * density, Offset(w * 0.96f, y)) }
                for (k in 0 until 5) { val x = w * (0.2f + k * 0.15f); drawCircle(accent.copy(alpha = 0.45f), 1.2f * density, Offset(x, h * 0.04f)) }
            }
            CalmStyle.CornerBloom -> {
                drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), Offset(0f, 0f), w * 0.5f))
                drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), Offset(w, h), w * 0.5f))
            }
            CalmStyle.BottomGlow -> drawRect(Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.35f)), startY = h * 0.55f, endY = h))
            CalmStyle.Beam -> drawRoundRect(accent.copy(alpha = 0.55f), Offset(w - 3f * density, h * 0.12f), Size(1.6f * density, h * 0.76f), CornerRadius(density))
            CalmStyle.Regular -> {
                for (i in 0 until 4) { val bh = h * (0.03f + i * 0.014f); drawRoundRect(fg.copy(alpha = if (i < 3) 0.8f else 0.3f), Offset(w * (0.7f + i * 0.04f), h * 0.2f - bh), Size(w * 0.025f, bh), CornerRadius(0.6f * density)) }
                drawRoundRect(fg.copy(alpha = 0.8f), Offset(w * 0.88f, h * 0.11f), Size(w * 0.08f, h * 0.09f), CornerRadius(1.2f * density), style = Stroke(1f * density))
            }
            CalmStyle.None -> Unit
        }
        // accent pill under the clock (the clock text itself is drawn over this in the card's own font)
        drawRoundRect(accent, Offset(w * 0.07f, h * 0.36f), Size(w * 0.2f, h * 0.045f), CornerRadius(h))
        // icons: a row of four, with an optional plate
        val e = h * 0.15f; val gx = (w - 4 * e) / 5f; val top = h * 0.5f
        val plate = s.iconPlate ?: true
        val r = radius(s.iconShape ?: IconShape.Rounded)
        for (i in 0 until 4) {
            val x = gx + i * (e + gx); val y = top
            if (plate) { val pad = e * 0.16f; drawRoundRect(fg.copy(alpha = 0.16f), Offset(x - pad, y - pad), Size(e + 2 * pad, e + 2 * pad), CornerRadius((e + 2 * pad) * (r.coerceAtLeast(0.2f))))}
            drawRoundRect(iconColour(s.iconTint ?: IconTint.None, accent, fg, i), Offset(x, y), Size(e, e), CornerRadius(e * r))
        }
        // dock
        val dockTop = h * 0.74f; val dockH = h * 0.2f; val dockX = w * 0.08f; val dockW = w * 0.84f
        when (s.dockStyle ?: DockStyle.Standard) {
            DockStyle.Standard -> drawRoundRect(fg.copy(alpha = 0.16f), Offset(dockX, dockTop), Size(dockW, dockH), CornerRadius(dockH * 0.4f))
            DockStyle.Transparent -> drawRoundRect(fg.copy(alpha = 0.3f), Offset(dockX, dockTop), Size(dockW, dockH), CornerRadius(dockH * 0.4f), style = Stroke(0.8f * density))
            DockStyle.ThreeD -> {
                drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(dockX, dockTop + dockH * 0.14f), Size(dockW, dockH), CornerRadius(dockH * 0.4f))
                drawRoundRect(Brush.verticalGradient(listOf(fg.copy(alpha = 0.34f), fg.copy(alpha = 0.12f)), startY = dockTop, endY = dockTop + dockH), Offset(dockX, dockTop), Size(dockW, dockH), CornerRadius(dockH * 0.4f))
                drawLine(fg.copy(alpha = 0.55f), Offset(dockX + dockH * 0.4f, dockTop + 0.6f * density), Offset(dockX + dockW - dockH * 0.4f, dockTop + 0.6f * density), 0.8f * density)
            }
        }
        val de = dockH * 0.52f; val dg = (dockW - 4 * de) / 5f
        for (i in 0 until 4) drawRoundRect(iconColour(s.iconTint ?: IconTint.None, accent, fg, i), Offset(dockX + dg + i * (de + dg), dockTop + (dockH - de) / 2f), Size(de, de), CornerRadius(de * r))
    }
}

/**
 * The Looks rail: one drawn card per Look in a wrapping row, each a miniature render of the Look's own values with the clock set in the Look's own font. The
 * picked card (the Look the settings add up to, [selectedId]) has the ring and the check; null means a custom look. After a tap the host shows [applied] and,
 * while there is something to undo, the "Undo last theme change" row under the cards (flat text rows, no box).
 */
@Composable
fun LooksRail(
    presets: List<LookPreset>,
    selectedId: String?,
    onSelect: (LookPreset) -> Unit,
    name: (LookPreset) -> String,
    applied: String?,
    canUndo: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    Column(modifier.fillMaxWidth().testTag(LooksTags.RAIL)) {
        VisualCardGroup {
            presets.forEach { p ->
                val family = remember(ctx, p.settings.uiFont) { FontCatalog.family(ctx, p.settings.uiFont) }
                val fg = LookArt.foreground(p.settings.themeMode ?: ThemeMode.Dark)
                OneUiVisualCard(name(p), p.id == selectedId, LooksTags.card(p.id), { onSelect(p) }, width = 100.dp) { ink ->
                    VisualCanvas(ink) { LookArt.draw(this, p.settings) }
                    Text("12:30", color = fg, fontSize = 11.sp, fontFamily = family, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.align(Alignment.TopStart).padding(start = 11.dp, top = 6.dp))
                }
            }
        }
        if (applied != null) Text(applied, color = HyleColors.InkBright, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp).testTag(LooksTags.APPLIED).semantics { liveRegion = LiveRegionMode.Polite })
        if (canUndo) Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(LooksTags.UNDO).clickable(role = Role.Button, onClick = onUndo), contentAlignment = Alignment.CenterStart) {
            Text(L10n.tr("Undo last theme change"), color = HyleColors.Violet, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
