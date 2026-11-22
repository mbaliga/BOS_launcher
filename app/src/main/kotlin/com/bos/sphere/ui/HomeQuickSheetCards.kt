package com.bos.sphere.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.PageTransition
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.Glyphs

/**
 * Wave 51 (homeMenuVisual): the Home quick sheet's choices as visual cards. Every card is a small drawn illustration (Canvas, no bitmap) over a short
 * label; the selected one carries an accent ring and a check. The illustrations are static (the owner's bonus of a looped animation on the selected
 * card was left out: nothing here runs a frame loop, so there is nothing for reduced motion to switch off). They draw in PHYSICAL coordinates, so a
 * "Right to left" card shows its arrow to the left whatever the app language is.
 */
internal object QuickCards {
    val CardWidth = 80.dp
    val ArtHeight = 64.dp
    val TileFill = Color(0xFF15161B)
    val Ink = Color(0xFFE7E9F0)
    val InkSoft = Color(0x80E7E9F0)
    val InkFaint = Color(0x33E7E9F0)
}

/** One group of cards: a row that wraps (it follows the layout direction), so a large font or a narrow window never hides a card. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun CardRow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

/** A choice card: [art] over [label]; role radio button with the selected state, the label is what TalkBack reads, 48 dp or more, a focus ring for keys and controllers. */
@Composable
internal fun OptionCard(label: String, selected: Boolean, tag: String, onClick: () -> Unit, art: DrawScope.(Size) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val accent = HyleColors.Violet
    val tile = RoundedCornerShape(16.dp)
    Column(
        Modifier.width(QuickCards.CardWidth).testTag(tag).onFocusChanged { focused = it.isFocused }
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().height(QuickCards.ArtHeight).clip(tile).background(QuickCards.TileFill)
                .border(if (selected) 2.5.dp else 1.dp, if (selected) accent else if (focused) Color.White else QuickCards.InkFaint, tile),
        ) {
            Canvas(Modifier.fillMaxWidth().height(QuickCards.ArtHeight)) { art(size) }
            if (selected) {
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Icon(Glyphs.Check, null, tint = HyleColors.OnAccent, modifier = Modifier.size(13.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            L10n.tr(label), color = if (selected) Color.White else Color(0xB3FFFFFF), fontSize = 12.sp, lineHeight = 15.sp, textAlign = TextAlign.Center,
            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 18.dp),
        )
        Spacer(Modifier.height(4.dp))
    }
}

/** A top entry tile (Add apps, Add widgets, Edit home): glyph over label, a button, not a choice. */
@Composable
internal fun EntryTile(glyph: ImageVector, label: String, tag: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.testTag(tag).heightIn(min = 76.dp).clip(shape).background(QuickCards.TileFill).border(1.dp, QuickCards.InkFaint, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(glyph, null, tint = HyleColors.Violet, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text(L10n.tr(label), color = Color.White, fontSize = 13.sp, lineHeight = 16.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

// ---- illustrations -------------------------------------------------------------------------------------------------------------------------------

private fun DrawScope.px(dp: Float) = dp * density

/** The phone outline, centred in the art area: returns its top-left and size. */
private fun DrawScope.phone(s: Size): Pair<Offset, Size> {
    val h = s.height * 0.86f; val w = h * 0.55f
    val o = Offset((s.width - w) / 2f, (s.height - h) / 2f)
    drawRoundRect(QuickCards.InkSoft, o, Size(w, h), CornerRadius(px(5f)), style = Stroke(px(1.5f)))
    return o to Size(w, h)
}

/** A grid of little app squares inside [box] ([cols] by [rows]) starting at the top, or at the bottom when [fromBottom]. */
private fun DrawScope.appGrid(box: Pair<Offset, Size>, cols: Int, rows: Int, accentCell: Int = -1, fromBottom: Boolean = false, color: Color = QuickCards.InkSoft) {
    val (o, sz) = box
    val pad = sz.width * 0.14f; val gap = sz.width * 0.1f
    val cell = (sz.width - 2 * pad - gap * (cols - 1)) / cols
    val blockH = rows * cell + (rows - 1) * gap
    val top = if (fromBottom) o.y + sz.height - pad - blockH - sz.width * 0.1f else o.y + pad + sz.width * 0.12f
    var n = 0
    for (r in 0 until rows) for (c in 0 until cols) {
        val col = if (n == accentCell) Color(0xFFFFFFFF) else color
        drawRoundRect(col, Offset(o.x + pad + c * (cell + gap), top + r * (cell + gap)), Size(cell, cell), CornerRadius(cell * 0.3f))
        n++
    }
}

private fun DrawScope.dots(box: Pair<Offset, Size>, count: Int, current: Int) {
    val (o, sz) = box
    val r = px(1.6f)
    for (i in 0 until count) {
        val cx = o.x + sz.width / 2f + (i - (count - 1) / 2f) * px(6f)
        drawCircle(if (i == current) Color.White else QuickCards.InkFaint, if (i == current) r * 1.2f else r, Offset(cx, o.y + sz.height - px(6f)))
    }
}

internal fun pagesArt(mode: HomePages): DrawScope.(Size) -> Unit = { s ->
    val box = phone(s)
    when (mode) {
        HomePages.Off -> appGrid(box, 2, 3, accentCell = -1)
        HomePages.Paginated -> { appGrid(box, 2, 3); dots(box, 3, 0) }
        HomePages.Continuous -> {
            // columns running off the right edge, with a scroll arrow
            val (o, sz) = box
            val gap = sz.width * 0.1f; val cell = sz.width * 0.26f
            for (c in 0 until 3) for (r in 0 until 3) {
                val x = o.x + sz.width * 0.12f + c * (cell + gap)
                if (x + cell <= o.x + sz.width - px(1f)) drawRoundRect(QuickCards.InkSoft, Offset(x, o.y + sz.height * 0.14f + r * (cell + gap)), Size(cell, cell), CornerRadius(cell * 0.3f))
            }
            val y = o.y + sz.height - px(7f)
            drawLine(Color.White, Offset(o.x + sz.width * 0.2f, y), Offset(o.x + sz.width * 0.8f, y), px(2f))
            val head = Path().apply { moveTo(o.x + sz.width * 0.8f, y - px(3f)); lineTo(o.x + sz.width * 0.8f + px(4f), y); lineTo(o.x + sz.width * 0.8f, y + px(3f)) }
            drawPath(head, Color.White, style = Stroke(px(1.5f)))
        }
    }
}

internal fun orderArt(rtl: Boolean): DrawScope.(Size) -> Unit = { s ->
    val tw = s.width * 0.2f; val th = s.height * 0.3f; val gap = s.width * 0.07f
    val total = 3 * tw + 2 * gap; val x0 = (s.width - total) / 2f; val y0 = s.height * 0.3f
    for (i in 0 until 3) {
        val first = if (rtl) i == 2 else i == 0 // the page that comes first is the one at the start side
        val x = x0 + i * (tw + gap)
        drawRoundRect(if (first) Color.White else QuickCards.InkSoft, Offset(x, y0), Size(tw, th), CornerRadius(px(4f)), style = if (first) androidx.compose.ui.graphics.drawscope.Fill else Stroke(px(1.5f)))
    }
    val y = s.height * 0.8f; val a = x0; val b = x0 + total
    val (from, to) = if (rtl) b to a else a to b
    drawLine(Color.White, Offset(from, y), Offset(to, y), px(2f))
    val d = if (rtl) px(4f) else -px(4f)
    val head = Path().apply { moveTo(to + d, y - px(3.5f)); lineTo(to, y); lineTo(to + d, y + px(3.5f)) }
    drawPath(head, Color.White, style = Stroke(px(2f)))
}

internal fun hotspotsArt(on: Set<Corner>): DrawScope.(Size) -> Unit = { s ->
    val (o, sz) = phone(s)
    val inset = px(6f); val r = px(3.4f)
    for (c in Corner.entries) {
        val cx = if (c == Corner.TopLeft || c == Corner.BottomLeft) o.x + inset else o.x + sz.width - inset
        val cy = if (c == Corner.TopLeft || c == Corner.TopRight) o.y + inset else o.y + sz.height - inset
        if (c in on) drawCircle(Color.White, r, Offset(cx, cy)) else drawCircle(QuickCards.InkFaint, r * 0.7f, Offset(cx, cy), style = Stroke(px(1f)))
    }
}

internal fun alignArt(top: Boolean): DrawScope.(Size) -> Unit = { s ->
    val box = phone(s)
    appGrid(box, 2, 2, fromBottom = !top, color = Color.White)
    // the empty side is drawn as a faint dashed band so the contrast reads at a glance
    val (o, sz) = box
    val y = if (top) o.y + sz.height - px(8f) else o.y + px(8f)
    drawLine(QuickCards.InkFaint, Offset(o.x + sz.width * 0.2f, y), Offset(o.x + sz.width * 0.8f, y), px(1.5f))
}

internal fun makeHomeArt(): DrawScope.(Size) -> Unit = { s ->
    val cx = s.width / 2f; val base = s.height * 0.74f; val roofY = s.height * 0.22f; val half = s.height * 0.3f
    val house = Path().apply {
        moveTo(cx - half, base); lineTo(cx - half, s.height * 0.46f); lineTo(cx, roofY); lineTo(cx + half, s.height * 0.46f); lineTo(cx + half, base); close()
    }
    drawPath(house, Color.White, style = Stroke(px(2f), join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawRoundRect(Color.White, Offset(cx - half * 0.28f, base - half * 0.7f), Size(half * 0.56f, half * 0.7f), CornerRadius(px(2f)))
}

/** Two cards showing the effect: the page leaving (dim) and the page arriving (bright). */
internal fun transitionArt(t: PageTransition): DrawScope.(Size) -> Unit = { s ->
    val cw = s.width * 0.3f; val ch = s.height * 0.62f; val cy = (s.height - ch) / 2f; val cx = s.width / 2f
    val rr = CornerRadius(px(4f))
    when (t) {
        PageTransition.Slide -> {
            drawRoundRect(QuickCards.InkSoft, Offset(cx - cw * 1.05f, cy), Size(cw, ch), rr, style = Stroke(px(1.5f)))
            drawRoundRect(Color.White, Offset(cx + cw * 0.05f, cy), Size(cw, ch), rr)
            drawLine(Color.White, Offset(cx - cw * 0.6f, s.height * 0.5f), Offset(cx - cw * 0.05f, s.height * 0.5f), px(1.5f))
        }
        PageTransition.Fade -> {
            drawRoundRect(QuickCards.InkSoft, Offset(cx - cw * 0.85f, cy), Size(cw, ch), rr, style = Stroke(px(1.5f)))
            drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(cx - cw * 0.15f, cy), Size(cw, ch), rr)
        }
        PageTransition.Zoom -> {
            drawRoundRect(QuickCards.InkSoft, Offset(cx - cw * 0.95f, cy), Size(cw * 1.9f, ch), rr, style = Stroke(px(1.5f)))
            drawRoundRect(Color.White, Offset(cx - cw * 0.45f, cy + ch * 0.25f), Size(cw * 0.9f, ch * 0.5f), CornerRadius(px(3f)))
        }
        PageTransition.Cube -> {
            val x0 = cx - cw * 1.0f; val x1 = cx; val x2 = cx + cw * 0.8f
            val front = Path().apply { moveTo(x0, cy + ch * 0.05f); lineTo(x1, cy); lineTo(x1, cy + ch); lineTo(x0, cy + ch * 0.95f); close() }
            val side = Path().apply { moveTo(x1, cy); lineTo(x2, cy + ch * 0.1f); lineTo(x2, cy + ch * 0.9f); lineTo(x1, cy + ch); close() }
            drawPath(front, QuickCards.InkSoft, style = Stroke(px(1.5f), join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawPath(side, Color.White)
        }
        PageTransition.Flip -> {
            drawRoundRect(QuickCards.InkFaint, Offset(cx - cw * 0.5f, cy), Size(cw, ch), rr, style = Stroke(px(1f)))
            val half = Path().apply { moveTo(cx - px(1f), cy - px(1f)); lineTo(cx - cw * 0.5f, cy + ch * 0.1f); lineTo(cx - cw * 0.5f, cy + ch * 0.9f); lineTo(cx - px(1f), cy + ch + px(1f)); close() }
            val half2 = Path().apply { moveTo(cx + px(1f), cy + ch * 0.12f); lineTo(cx + cw * 0.5f, cy); lineTo(cx + cw * 0.5f, cy + ch); lineTo(cx + px(1f), cy + ch * 0.88f); close() }
            drawPath(half, QuickCards.InkSoft); drawPath(half2, Color.White)
        }
        PageTransition.Stack -> {
            val off = px(5f)
            drawRoundRect(QuickCards.InkFaint, Offset(cx - cw * 0.5f + 2 * off, cy - 2 * off + px(4f)), Size(cw, ch), rr, style = Stroke(px(1.5f)))
            drawRoundRect(QuickCards.InkSoft, Offset(cx - cw * 0.5f + off, cy - off + px(4f)), Size(cw, ch), rr, style = Stroke(px(1.5f)))
            drawRoundRect(Color.White, Offset(cx - cw * 0.5f, cy + px(4f)), Size(cw, ch), rr)
        }
    }
}
