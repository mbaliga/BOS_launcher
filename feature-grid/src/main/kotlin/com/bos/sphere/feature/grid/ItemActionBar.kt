package com.bos.sphere.feature.grid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.delay

object ItemBarTags {
    const val BAR = "item-bar"
    const val RESIZE = "item-bar.resize"
    const val REMOVE = "item-bar.remove"
    const val SETTINGS = "item-bar.settings"
    const val OUTLINE = "item-bar.outline"
    const val UNDO = "item-bar.undo"
    const val SNACK = "item-bar.snack"
}

/** A removal that can be taken back while the snackbar shows (6 s). */
class RemovedNotice(val message: String, val onUndo: () -> Unit, val id: Long = System.nanoTime())

/**
 * Wave 45 (editSheet), HOM-12: the item action layer of home edit mode. While [source] has a selection it draws a 2 dp outline round the item and
 * a 48 dp action bar [Resize] [Remove] [Settings] below it (above when there is no room), clamped inside the window area and clear of the handles
 * and of [avoid] (the dock). Settings opens the item sheet; Remove runs [onRemove] and offers a 6 s Undo.
 *
 * [onResize] is null for items that cannot be resized (the Resize button is then left out rather than dead). The handles stream draws its own outline:
 * it is the default now: [showOutline] = true only for hosts without the handles' outline.
 */
@Composable
fun ItemActionLayer(
    source: ItemSelectionSource,
    notice: RemovedNotice?,
    onNoticeDone: () -> Unit,
    onResize: ((key: String) -> Unit)?,
    onRemove: (key: String) -> Unit,
    onSettings: (key: String) -> Unit,
    label: (key: String) -> String,
    modifier: Modifier = Modifier,
    avoid: List<Rect> = emptyList(),
    showOutline: Boolean = false,
    reserveTop: androidx.compose.ui.unit.Dp = 0.dp,
    reserveBottom: androidx.compose.ui.unit.Dp = 0.dp,
    /** W48: the scroll surface (Pages Continuous): the bar keeps clear of the selected item and the cell under it, above first. */
    scrollSurface: Boolean = false,
) {
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }.onSizeChanged { area = it }) {
        val key = source.selectedKey
        val b = source.bounds
        if (key != null) {
            val kind = ItemSheetModel.kindOf(key)
            val name = label(key)
            if (b != null && showOutline) {
                val local = Rect(b.left - origin.x, b.top - origin.y, b.right - origin.x, b.bottom - origin.y)
                val pad = with(density) { 3.dp.toPx() }
                Box(
                    Modifier.offset { IntOffset((local.left - pad).toInt(), (local.top - pad).toInt()) }
                        .size(with(density) { (local.width + 2 * pad).toDp() }, with(density) { (local.height + 2 * pad).toDp() })
                        .border(BorderStroke(2.dp, HyleColors.Violet), RoundedCornerShape(16.dp)).testTag(ItemBarTags.OUTLINE),
                )
            }
            val areaRect = Rect(0f, with(density) { reserveTop.toPx() }, area.width.toFloat(), (area.height - with(density) { reserveBottom.toPx() }).coerceAtLeast(0f))
            val item = b?.let { Rect(it.left - origin.x, it.top - origin.y, it.right - origin.x, it.bottom - origin.y) }
                ?: Rect(areaRect.center.x, areaRect.center.y, areaRect.center.x, areaRect.center.y)
            val w = if (barSize.width > 0) barSize.width.toFloat() else with(density) { 288.dp.toPx() }
            val h = with(density) { 48.dp.toPx() }
            val avoidLocal = avoid.map { Rect(it.left - origin.x, it.top - origin.y, it.right - origin.x, it.bottom - origin.y) }
            val p = if (scrollSurface) ActionBarMath.placeOnScroll(item, w, h, areaRect, avoidLocal, gap = with(density) { 8.dp.toPx() }, labelRoom = with(density) { 24.dp.toPx() })
            else ActionBarMath.place(
                item, w, h, areaRect, avoidLocal,
                gap = with(density) { 8.dp.toPx() }, handleReach = with(density) { 30.dp.toPx() },
            )
            if (!source.dragging) Row(
                Modifier.offset { IntOffset(p.x.toInt(), p.y.toInt()) }
                    .onSizeChanged { barSize = it }
                    .shadow(8.dp, RoundedCornerShape(24.dp))
                    .background(Color(0xF21C1D26), RoundedCornerShape(24.dp))
                    .height(48.dp)
                    .testTag(ItemBarTags.BAR)
                    .semantics { contentDescription = L10n.tr("Actions for %s", name) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onResize != null && ItemSheetModel.canResize(kind)) BarButton(ItemBarTags.RESIZE, Glyphs.Pinch, L10n.tr("Resize"), L10n.tr("Resize %s", name)) { onResize(key) }
                BarButton(ItemBarTags.REMOVE, Glyphs.Close, L10n.tr("Remove"), L10n.tr("Remove %s", name)) { onRemove(key) }
                BarButton(ItemBarTags.SETTINGS, Glyphs.Gear, L10n.tr("Settings"), L10n.tr("Settings for %s", name)) { onSettings(key) }
            }
        }
        if (notice != null) {
            LaunchedEffect(notice.id) { delay(6000); onNoticeDone() }
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
                    .background(Color(0xF2262833), RoundedCornerShape(12.dp)).testTag(ItemBarTags.SNACK)
                    .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = notice.message }
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(notice.message, color = HyleColors.InkBright, fontSize = 14.sp)
                Box(
                    Modifier.defaultMinSize(minWidth = 72.dp, minHeight = 48.dp).testTag(ItemBarTags.UNDO)
                        .clickable(role = Role.Button) { notice.onUndo(); onNoticeDone() }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(L10n.tr("Undo"), color = HyleColors.Violet, fontSize = 14.sp) }
            }
        }
    }
}

@Composable
private fun BarButton(tag: String, glyph: ImageVector, text: String, description: String, onClick: () -> Unit) {
    Column(
        Modifier.defaultMinSize(minWidth = 72.dp, minHeight = 48.dp).height(48.dp).testTag(tag)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(glyph, null, tint = HyleColors.InkBright, modifier = Modifier.size(20.dp))
        Text(text, color = HyleColors.InkBright, fontSize = 11.sp, maxLines = 1)
    }
}

/** Set by the home in edit mode: opens the item sheet of a key. Item nodes then carry the "Open item settings" custom action (HOM-12 / NFR-13). */
val LocalItemSettingsOpener = androidx.compose.runtime.staticCompositionLocalOf<((String) -> Unit)?> { null }

/** The extra custom actions an item node carries in edit mode. */
fun itemSettingsActions(opener: ((String) -> Unit)?, key: String, editing: Boolean): List<CustomAccessibilityAction> =
    if (editing && opener != null) listOf(CustomAccessibilityAction(L10n.tr("Open item settings")) { opener(key); true }) else emptyList()
