package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.SearchHit
import com.bos.sphere.core.design.L10n

/**
 * PAL-03: an app's own launcher shortcuts that match the query sit inside the app's tile or row in all three Radar
 * layouts (Orbit, Filmstrip, Flat), instead of being separate rows. A shortcut whose app is not among the matched apps
 * (it matched on its own label) has no tile to sit in and stays an ordinary Actions row; so do shortcuts beyond [MAX].
 */
internal object NestedShortcuts {
    /** Most shortcuts one app tile carries; the rest stay Actions rows so none is lost. */
    const val MAX = 4

    /** Most circles one row shows before its "+N" circle, which opens the preview with all of them. */
    const val ROW_SHOWN = 3

    class Split(
        /** App key to its nested shortcuts, best match first. */
        val byApp: Map<String, List<SearchHit>>,
        /** The Actions hits that were not nested. */
        val rest: List<SearchHit>,
    )

    fun isAppShortcut(h: SearchHit): Boolean = h.extras["shortcutId"] != null && h.extras["package"] != null

    fun split(apps: List<AppEntry>, actionHits: List<SearchHit>): Split {
        if (actionHits.none(::isAppShortcut) || apps.isEmpty()) return Split(emptyMap(), actionHits)
        val byPackage = LinkedHashMap<String, AppEntry>()
        apps.forEach { byPackage.putIfAbsent(it.packageName, it) } // the first (best) tile of a package carries its shortcuts
        val nested = LinkedHashMap<String, MutableList<SearchHit>>()
        val rest = ArrayList<SearchHit>()
        for (h in actionHits) {
            val owner = if (isAppShortcut(h)) byPackage[h.extras["package"]] else null
            val list = owner?.let { nested.getOrPut(it.key) { ArrayList() } }
            if (owner != null && list!!.size < MAX) list += h else rest += h
        }
        return Split(nested.mapValues { (_, v) -> v.sortedByDescending { it.score } }, rest)
    }

    /** The action list of a result: for an app tile with nested shortcuts they follow "Open", each runs the shortcut (PAL-03, PAL-07). */
    fun menuFor(tile: Tile, base: List<ResultMenuEntry>): List<ResultMenuEntry> {
        val sc = (tile as? Tile.App)?.shortcuts.orEmpty()
        if (sc.isEmpty()) return base
        val at = base.indexOfFirst { it.action == com.bos.sphere.core.input.MenuAction.Open }.let { if (it < 0) 0 else it + 1 }
        return base.take(at) + sc.map { ResultMenuEntry(it.title, null, shortcut = it) } + base.drop(at)
    }
}

/** Wave 25 (shortcutForm): the one form of a shortcut, quicklink or action: a 24 dp glyph in a 40 dp circle with a 48 dp touch slot. */
internal object ShortcutForm {
    const val CIRCLE_DP = 40f
    const val GLYPH_DP = 24f
    /** The touch slot, and the pitch of the row: a 40 dp circle with 4 dp around it, so two neighbours are 8 dp apart and their targets never overlap. */
    const val SLOT_DP = 48f
    /** Longest the tooltip of a long-pressed circle stays up. */
    const val TIP_MILLIS = 1800L

    /** How many circles and how many "+N" a row of [total] shortcuts shows in [widthDp]: all of them when they fit, else the "+N" takes the last slot. */
    class Row(val shown: Int, val more: Int) { val slots get() = shown + if (more > 0) 1 else 0 }
    fun row(total: Int, widthDp: Float, maxShown: Int = NestedShortcuts.ROW_SHOWN): Row {
        val slots = maxOf(1, (widthDp / SLOT_DP).toInt())
        if (total <= minOf(maxShown, slots)) return Row(total, 0)
        val shown = minOf(maxShown, slots - 1).coerceAtLeast(0)
        return Row(shown, total - shown)
    }

    /** The width a row of [total] shortcuts needs in the small layouts (up to three slots). */
    fun smallRowWidth(total: Int): Float = SLOT_DP * minOf(total, NestedShortcuts.ROW_SHOWN)
}

/**
 * The one shortcut button: a [ShortcutForm.CIRCLE_DP] circle (glyph or the shortcut's own icon in [content]) inside a 48 dp touch slot. The label is the
 * content description (TalkBack) and, on a long press, a short tooltip above it. [onClick] runs it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ActionCircle(
    label: String, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false,
    describe: String = label, content: @Composable () -> Unit,
) {
    var tip by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(tip) { if (tip) { kotlinx.coroutines.delay(ShortcutForm.TIP_MILLIS); tip = false } }
    Box(
        modifier.testTag(tag).size(ShortcutForm.SLOT_DP.dp)
            .combinedClickable(onClick = onClick, onLongClick = { tip = true })
            .clearAndSetSemantics {
                contentDescription = describe; role = Role.Button
                onClick { onClick(); true }
                onLongClick(label) { tip = true; true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.radarSelectedRing(selected, CircleShape).size(ShortcutForm.CIRCLE_DP.dp).clip(CircleShape).background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center,
        ) { content() }
        if (tip) androidx.compose.ui.window.Popup(
            popupPositionProvider = object : androidx.compose.ui.window.PopupPositionProvider {
                override fun calculatePosition(anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: androidx.compose.ui.unit.IntSize, layoutDirection: androidx.compose.ui.unit.LayoutDirection, popupContentSize: androidx.compose.ui.unit.IntSize) =
                    androidx.compose.ui.unit.IntOffset(
                        (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                        (anchorBounds.top - popupContentSize.height - 4).coerceAtLeast(0),
                    )
            },
            onDismissRequest = { tip = false },
        ) {
            Text(label, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 240.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xF2101018)).padding(horizontal = 10.dp, vertical = 6.dp))
        }
    }
}

/**
 * The shortcuts of an app tile in one tidy row of circles under the app (Orbit, Filmstrip, Flat; the preview lists them with labels). The row is part of the tile's
 * layout: it is [ShortcutForm.SLOT_DP] high, at most [NestedShortcuts.ROW_SHOWN] circles and a "+N" circle that opens the preview. [width] is the room the tile gives it.
 */
@Composable
internal fun ShortcutCircleRow(tile: Tile.App, width: Dp, onRun: (SearchHit) -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier, start: Boolean = false) {
    if (tile.shortcuts.isEmpty()) return
    val row = ShortcutForm.row(tile.shortcuts.size, width.value)
    Row(modifier.height(ShortcutForm.SLOT_DP.dp), horizontalArrangement = if (start) Arrangement.Start else Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        tile.shortcuts.take(row.shown).forEach { h ->
            ActionCircle(h.title, "nested.${tile.app.key}.${h.id}", onClick = { onRun(h) }, describe = L10n.tr("App shortcut: %s", h.title)) { HitLeadingIcon(h, ShortcutForm.GLYPH_DP.dp, Color.White, app = tile.app) }
        }
        if (row.more > 0) {
            val say = L10n.tr("%s more shortcuts", row.more.toString())
            ActionCircle("+${row.more}", "nested.${tile.app.key}.more", onClick = onMore, describe = say) {
                Text("+${row.more}", color = Color.White, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

/** The preview's list of an app's shortcuts: here there is room, so each one is a labelled row (the circles of the result carry the same icons). */
@Composable
internal fun ShortcutPreviewList(tile: Tile.App, onRun: (SearchHit) -> Unit, modifier: Modifier = Modifier) {
    if (tile.shortcuts.isEmpty()) return
    Column(modifier) {
        tile.shortcuts.forEach { h ->
            Row(
                Modifier.testTag("preview.shortcut.${h.id}").fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = { onRun(h) })
                    .clearAndSetSemantics { contentDescription = L10n.tr("App shortcut: %s", h.title); role = Role.Button; onClick { onRun(h); true } }.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(ShortcutForm.CIRCLE_DP.dp).clip(CircleShape).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) { HitLeadingIcon(h, ShortcutForm.GLYPH_DP.dp, Color.White, app = tile.app) }
                Text(h.title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}
