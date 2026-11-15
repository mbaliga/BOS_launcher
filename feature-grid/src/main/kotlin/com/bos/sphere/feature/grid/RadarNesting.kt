package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

    /** Pills drawn inside a small tile (strip, orbit) before the "+N" pill that opens the preview with all of them. */
    const val SMALL_SHOWN = 2

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

/** One nested shortcut: a small pill that runs it. */
@Composable
internal fun ShortcutPill(title: String, tag: String, scale: Float, modifier: Modifier = Modifier, describe: String = L10n.tr("App shortcut: %s", title), onClick: () -> Unit) {
    Text(
        title, color = Color.White, fontSize = (11 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.testTag(tag).clip(RoundedCornerShape(9.dp)).background(Color(0x33FFFFFF)).clickable(onClick = onClick)
            .semantics { contentDescription = describe }
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * The shortcuts of an app tile, drawn inside it. In the small layouts (strip, orbit) at most [shown] pills and a "+N"
 * pill that opens the preview, where every shortcut is listed; the flat list passes a large [shown] and scrolls sideways.
 */
@Composable
internal fun NestedShortcutPills(
    tile: Tile.App,
    shown: Int,
    width: Dp,
    onRun: (SearchHit) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tile.shortcuts.isEmpty()) return
    val z = LocalResultScale.current
    Column(modifier.widthIn(max = width).padding(top = 3.dp), verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        tile.shortcuts.take(shown).forEach { h -> ShortcutPill(h.title, "nested.${tile.app.key}.${h.id}", z) { onRun(h) } }
        val more = tile.shortcuts.size - shown
        if (more > 0) ShortcutPill("+$more", "nested.${tile.app.key}.more", z, describe = "+$more", onClick = onMore)
    }
}

/** The flat list's version: every shortcut in one row under the app name (scrolls sideways when narrow). */
@Composable
internal fun NestedShortcutRow(tile: Tile.App, onRun: (SearchHit) -> Unit, modifier: Modifier = Modifier) {
    if (tile.shortcuts.isEmpty()) return
    val z = LocalResultScale.current
    Row(modifier.horizontalScroll(rememberScrollState()).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tile.shortcuts.forEach { h -> ShortcutPill(h.title, "nested.${tile.app.key}.${h.id}", z) { onRun(h) } }
    }
}
