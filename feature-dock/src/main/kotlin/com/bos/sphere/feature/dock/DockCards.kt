package com.bos.sphere.feature.dock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.Glyphs

/**
 * W55 (dockSheet): the choice cards of the dock's sheet and of Settings > Desktop & dock > Dock. They are drawn like the Home quick sheet's cards
 * (a small illustration over a short label, the selected one with an accent ring and a check); the two surfaces use the same composables, so they
 * cannot drift. Every colour follows the app theme (the cards sit on a One UI card, which is the theme's surface, so their tile is the theme's background).
 */
internal object DockCardTokens {
    val ArtHeight = 64.dp
    val NarrowWidth = 80.dp
    val StyleWidth = 104.dp
    val tile: Color get() = HyleColors.Background
    val entryTile: Color get() = HyleColors.Surface
    val faint: Color get() = HyleColors.InkDim.copy(alpha = 0.35f)
    fun label(selected: Boolean) = if (selected) HyleColors.InkBright else HyleColors.InkDim
}

/** One group of cards: a row that wraps (it follows the layout direction), so a large font or a narrow window never hides a card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DockCardRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

/** A choice card: [art] over [label]; role radio button with the selected state, 48 dp or more, a focus ring for keys and controllers. */
@Composable
internal fun DockOptionCard(
    label: String, selected: Boolean, tag: String, onClick: () -> Unit,
    width: Dp = DockCardTokens.NarrowWidth, art: @Composable (Modifier) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = HyleColors.Violet
    val tile = RoundedCornerShape(16.dp)
    Column(
        Modifier.width(width).testTag(tag).onFocusChanged { focused = it.isFocused }
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().height(DockCardTokens.ArtHeight).clip(tile).background(DockCardTokens.tile)
                .border(if (selected) 2.5.dp else 1.dp, if (selected) accent else if (focused) HyleColors.InkBright else DockCardTokens.faint, tile),
            contentAlignment = Alignment.Center,
        ) {
            art(Modifier.fillMaxWidth().height(DockCardTokens.ArtHeight))
            if (selected) {
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Icon(Glyphs.Check, null, tint = HyleColors.OnAccent, modifier = Modifier.size(13.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label, color = DockCardTokens.label(selected), fontSize = 12.sp, lineHeight = 15.sp, textAlign = TextAlign.Center,
            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 18.dp),
        )
        Spacer(Modifier.height(4.dp))
    }
}

/** A top entry tile (Edit dock, Workspaces and groups, About the dock): glyph over label, a button, not a choice. */
@Composable
internal fun DockEntryTile(glyph: ImageVector, label: String, tag: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.testTag(tag).heightIn(min = 76.dp).clip(shape).background(DockCardTokens.entryTile).border(1.dp, DockCardTokens.faint, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(glyph, null, tint = if (enabled) HyleColors.Violet else HyleColors.InkDim.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (enabled) HyleColors.InkBright else HyleColors.InkDim.copy(alpha = 0.5f), fontSize = 13.sp, lineHeight = 16.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

// ---- illustrations (physical coordinates, Canvas only, no bitmap) -----------------------------------------------------------------------------

private fun DrawScope.px(dp: Float) = dp * density

private fun DrawScope.phone(s: Size, ink: Color): Pair<Offset, Size> {
    val w = px(30f); val h = px(52f)
    val o = Offset((s.width - w) / 2f, (s.height - h) / 2f)
    drawRoundRect(ink.copy(alpha = 0.55f), o, Size(w, h), CornerRadius(px(6f)), style = androidx.compose.ui.graphics.drawscope.Stroke(px(1.5f)))
    return o to Size(w, h)
}

/** The dock's position: a phone outline with the dock bar on the bottom, the left or the right edge. Physical, so "Left" is on the left in every language. */
internal fun positionArt(edge: DockEdge, ink: Color, accent: Color): DrawScope.(Size) -> Unit = { s ->
    val (o, sz) = phone(s, ink)
    val bar = px(5f); val inset = px(4f); val round = CornerRadius(px(3f))
    when (edge) {
        DockEdge.Bottom -> drawRoundRect(accent, Offset(o.x + sz.width * 0.18f, o.y + sz.height - inset - bar), Size(sz.width * 0.64f, bar), round)
        DockEdge.Left -> drawRoundRect(accent, Offset(o.x + inset, o.y + sz.height * 0.22f), Size(bar, sz.height * 0.56f), round)
        DockEdge.Right -> drawRoundRect(accent, Offset(o.x + sz.width - inset - bar, o.y + sz.height * 0.22f), Size(bar, sz.height * 0.56f), round)
    }
}

/** The dock's mode: a thin grab strip (collapsed band) or a row of icons, a rule, then recent ones (pinned and recent). */
internal fun modeArt(mode: DockMode, ink: Color, accent: Color): DrawScope.(Size) -> Unit = { s ->
    val (o, sz) = phone(s, ink)
    val y = o.y + sz.height - px(10f)
    when (mode) {
        DockMode.Collapsed -> drawRoundRect(accent, Offset(o.x + sz.width * 0.3f, y), Size(sz.width * 0.4f, px(3f)), CornerRadius(px(2f)))
        DockMode.PinnedPlusRecent -> {
            val d = px(5f); val gap = px(2.5f)
            val total = 5 * d + 4 * gap + px(3f)
            var x = o.x + (sz.width - total) / 2f
            repeat(3) { drawRoundRect(accent, Offset(x, y - d / 2f), Size(d, d), CornerRadius(px(1.5f))); x += d + gap }
            drawRect(ink.copy(alpha = 0.6f), Offset(x - gap / 2f, y - d / 2f), Size(px(0.8f), d)); x += px(3f)
            repeat(2) { drawRoundRect(ink.copy(alpha = 0.45f), Offset(x, y - d / 2f), Size(d, d), CornerRadius(px(1.5f))); x += d + gap }
        }
    }
}

/**
 * W56 (dockMagnify): a strength card's art: a shelf line with a row of icons, the middle one under the pointer, grown by the card's factor with the
 * same raised-cosine falloff and spread the dock uses (the same [DockMagnify] functions), so the card shows what the dock will do.
 */
internal fun strengthArt(strength: MagnifyStrength, ink: Color, accent: Color): DrawScope.(Size) -> Unit = { s ->
    val n = 7
    val pitch = s.width * 0.86f / n
    val r = DockMagnify.radius(pitch)
    val a = strength.factor - 1f
    val baseline = s.height * 0.80f
    val x0 = (s.width - pitch * n) / 2f + pitch / 2f
    val pointer = x0 + pitch * 3
    drawRoundRect(ink.copy(alpha = 0.5f), Offset(s.width * 0.06f, baseline + px(2.5f)), Size(s.width * 0.88f, px(2.5f)), CornerRadius(px(1.5f)))
    for (i in 0 until n) {
        val c = x0 + pitch * i
        val x = c - pointer
        val sc = 1f + a * DockMagnify.weight(x, r)
        val side = pitch * 0.62f * sc
        val cx = c + a * DockMagnify.displacement(x, r)
        val lift = px(1.5f) * (sc - 1f) / a.coerceAtLeast(0.01f)
        val mid = i == 3
        drawRoundRect(if (mid) accent else ink.copy(alpha = 0.55f), Offset(cx - side / 2f, baseline - side - lift), Size(side, side), CornerRadius(side * 0.24f))
    }
}
