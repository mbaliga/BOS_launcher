@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.titleCaps
import kotlin.math.abs
import kotlin.math.floor

/** Wave 57: test tags of the section tabs. */
object SettingsTabTags {
    const val ROW = "oneui.tabs"
    const val RAIL = "oneui.tabRail"
    const val INDICATOR = "oneui.tabIndicator"
    const val PAGER = "oneui.tabPager"
    fun tab(title: String) = "oneui.tab.$title"
    fun list(title: String) = "oneui.tabList.$title"
}

/** Wave 57: the look of Liftoff's text tabs (the Comms ALL / UNREAD / FILES / TIMELINE row, the page editor's Apps / Widgets / Folders / Settings). */
internal object LiftoffTabStyle {
    val LabelSize = 14.sp
    val Tracking = 1.2.sp
    val Height = 48.dp
    val Pad = 12.dp
    val IndicatorHeight = 2.dp
}

/** Where the indicator is drawn: between tab [floor(position)] and the next one, by the fraction of [position]. Pure, so the follow-the-swipe maths is tested. */
internal object TabIndicator {
    /** Left edge and width (px) of the indicator for [position] (the pager's page plus its offset fraction) over tabs of [lefts] and [widths]. */
    fun place(position: Float, lefts: List<Float>, widths: List<Float>): Pair<Float, Float>? {
        if (lefts.isEmpty() || lefts.size != widths.size) return null
        val p = position.coerceIn(0f, (lefts.size - 1).toFloat())
        val i = floor(p).toInt().coerceIn(0, lefts.lastIndex)
        val j = (i + 1).coerceAtMost(lefts.lastIndex)
        val f = p - i
        return (lefts[i] + (lefts[j] - lefts[i]) * f) to (widths[i] + (widths[j] - widths[i]) * f)
    }

    /**
     * Wave 57: the padding beside each label and the letter spacing that make the six labels fit the width without scrolling: the full style first,
     * then tighter padding, then tighter tracking. [labelPx] is each label's width at tracking 0, [chars] its letters. Null: nothing fits, the row scrolls.
     */
    fun fit(labelPx: List<Float>, chars: List<Int>, availablePx: Float, dpPx: Float, spPx: Float): Pair<Float, Float>? {
        val plans = listOf(12f to 1.2f, 9f to 1.2f, 7f to 1f, 5f to 0.6f, 4f to 0.2f) // (padding dp, tracking sp)
        for ((pad, track) in plans) {
            val total = labelPx.indices.sumOf { (labelPx[it] + chars[it] * track * spPx + 2 * pad * dpPx).toDouble() } + 2 * ROW_PAD_DP * dpPx
            if (total <= availablePx) return pad to track
        }
        return null
    }
    const val ROW_PAD_DP = 8f

    /** How bright a label is (0 dim .. 1 full ink) at [position]: the selected tab is bright, a neighbour brightens as the swipe reaches it. */
    fun brightness(index: Int, position: Float): Float = (1f - abs(position - index)).coerceIn(0f, 1f)
}

/**
 * Wave 57 (CFG-41): the six section tabs in a row: upper-case, letter-spaced labels, the selected one in full ink with the accent line under it,
 * the others dim. [position] is the pager's page plus its offset, so the line slides with a swipe. The row is a tab list for a screen reader
 * (each tab: role Tab, selected, its place in a collection of [titles] size, so TalkBack says "Tab, 2 of 6"). The labels spread over the width;
 * when they do not fit (large font, a long translation) the row scrolls and keeps the selected tab in view. A right-to-left layout mirrors the order.
 */
@Composable
internal fun LiftoffTabRow(titles: List<String>, selected: Int, position: Float, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val lefts = remember(titles.size) { mutableStateListOf<Float>().apply { repeat(titles.size) { add(0f) } } }
    val widths = remember(titles.size) { mutableStateListOf<Float>().apply { repeat(titles.size) { add(0f) } } }
    val accent = HyleColors.Violet
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth().height(LiftoffTabStyle.Height).pageFill()) {
        val viewport = maxWidth
        val caps = titles.map { titleCaps(it) }
        val style = androidx.compose.ui.text.TextStyle(fontSize = LiftoffTabStyle.LabelSize, fontWeight = FontWeight.Medium)
        val plan = with(density) {
            TabIndicator.fit(caps.map { measurer.measure(it, style, maxLines = 1).size.width.toFloat() }, caps.map { it.length }, viewport.toPx() * 0.97f, 1.dp.toPx(), 1.sp.toPx()) // a little slack: the UI font may be wider than the measuring default
        }
        val pad = (plan?.first ?: 4f).dp
        val track = (plan?.second ?: 0.2f).sp
        Row(
            Modifier.horizontalScroll(rememberScrollState()).widthIn(min = viewport).height(LiftoffTabStyle.Height)
                .padding(horizontal = TabIndicator.ROW_PAD_DP.dp)
                .testTag(SettingsTabTags.ROW)
                .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = titles.size) }
                .selectableGroup()
                .drawBehind {
                    val at = TabIndicator.place(position, lefts, widths) ?: return@drawBehind
                    val h = LiftoffTabStyle.IndicatorHeight.toPx()
                    val inset = pad.toPx()
                    drawRect(accent, Offset(at.first + inset, size.height - h), Size((at.second - 2 * inset).coerceAtLeast(h), h))
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            titles.forEachIndexed { i, t ->
                val on = i == selected
                val bring = remember { BringIntoViewRequester() }
                LaunchedEffect(on) { if (on) runCatching { bring.bringIntoView() } }
                TabLabel(
                    t, i, titles.size, on, TabIndicator.brightness(i, position), onSelect, pad = pad, track = track, modifier =
                    Modifier.bringIntoViewRequester(bring).onPlaced { c -> lefts[i] = c.positionInParent().x; widths[i] = c.size.width.toFloat() },
                )
            }
        }
    }
}

/**
 * Wave 57 (CFG-41): the same six labels as a vertical list, for the start pane of the landscape two-pane layout: the same capitals, ink and accent
 * line (beside the label, at its start), the same tab semantics.
 */
@Composable
internal fun LiftoffTabRail(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag(SettingsTabTags.RAIL)
            .semantics { collectionInfo = CollectionInfo(rowCount = titles.size, columnCount = 1) }.selectableGroup(),
    ) {
        titles.forEachIndexed { i, t ->
            val on = i == selected
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(LiftoffTabStyle.IndicatorHeight * 1.5f).height(22.dp).background(if (on) HyleColors.Violet else androidx.compose.ui.graphics.Color.Transparent))
                TabLabel(t, i, titles.size, on, if (on) 1f else 0f, onSelect, modifier = Modifier.fillMaxWidth(), vertical = true)
            }
        }
    }
}

@Composable
private fun TabLabel(
    title: String, index: Int, count: Int, on: Boolean, bright: Float, onSelect: (Int) -> Unit, modifier: Modifier, vertical: Boolean = false,
    pad: androidx.compose.ui.unit.Dp = LiftoffTabStyle.Pad, track: androidx.compose.ui.unit.TextUnit = LiftoffTabStyle.Tracking,
) {
    val ink = androidx.compose.ui.graphics.lerp(HyleColors.InkDim, HyleColors.InkBright, bright)
    Box(
        modifier.defaultMinSize(minHeight = LiftoffTabStyle.Height, minWidth = 48.dp).testTag(SettingsTabTags.tab(title))
            .clickable(role = Role.Tab) { onSelect(index) }
            .clearAndSetSemantics {
                contentDescription = title
                role = Role.Tab
                selected = on
                collectionItemInfo = if (vertical) CollectionItemInfo(index, 1, 0, 1) else CollectionItemInfo(0, 1, index, 1)
                onClick { onSelect(index); true }
            }
            .padding(horizontal = pad),
        contentAlignment = if (vertical) Alignment.CenterStart else Alignment.Center,
    ) {
        Text(titleCaps(title), color = ink, fontSize = LiftoffTabStyle.LabelSize, letterSpacing = track, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
    }
}

/** The tabs sit on the page colour, so the list scrolling under the pinned row never shows through. */
private fun Modifier.pageFill(): Modifier = this.then(
    Modifier.drawBehind {
        drawRect(OneUi.Page)
        // a hairline under the row, so the list scrolling up to the pinned tabs has an edge
        drawRect(HyleColors.Hairline, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
    },
)
