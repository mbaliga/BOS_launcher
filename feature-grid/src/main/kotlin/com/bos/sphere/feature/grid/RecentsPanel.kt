package com.bos.sphere.feature.grid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AgoBucket
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.RecentsAgo
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.input.FocusKind

/** The input-graph layer of the Recents panel: above the home (0), the drawer (2), the puck (3) and Launchpad (4), below menus (8). */
const val RECENTS_FOCUS_LAYER = 7

/** STR-14: one row of the Recents panel. */
data class RecentRow(val app: AppEntry, val timeMillis: Long)

/** STR-14: the words for "how long ago", in the user's language. */
object RecentsAgoText {
    fun text(b: AgoBucket): String = when (b) {
        AgoBucket.JustNow -> L10n.tr("Just now")
        is AgoBucket.Minutes -> L10n.tr("%d min ago", b.n)
        is AgoBucket.Hours -> L10n.tr("%d h ago", b.n)
        is AgoBucket.Days -> L10n.tr("%d d ago", b.n)
    }

    fun text(now: Long, then: Long): String = text(RecentsAgo.bucket(now, then))
}

/**
 * STR-14: the Recents panel, the right-hand mirror of Launchpad. It fills the window beneath the home card (the card sits on the left with a
 * sliver, see [com.bos.sphere.ui.LaunchpadCardGeometry]); [sliverInsetDp] keeps the content clear of that sliver on [sliverOnLeft]. Newest first,
 * a roomy list (48 dp icons, label, "Recent" and how long ago), "Clear all", a long press, secondary click or the Menu key opens the app menu.
 * One honest line says what the list can and cannot know. [contentAlpha] is read while drawing so a drag never recomposes the panel.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentsPanel(
    rows: List<RecentRow>,
    now: Long,
    usageAccessOn: Boolean,
    sliverInsetDp: Dp,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
    onClearAll: () -> Unit,
    onGrantUsage: () -> Unit,
    modifier: Modifier = Modifier,
    sliverOnLeft: Boolean = true,
    contentAlpha: () -> Float = { 1f },
) {
    val bg = HyleColors.Background
    Box(
        modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg, androidx.compose.ui.graphics.lerp(bg, HyleColors.Surface, 0.55f)))).testTag("recents.panel")
            .semantics { contentDescription = L10n.tr("Recent apps") },
    ) {
        FocusLayer(layer = RECENTS_FOCUS_LAYER) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding()
                    .absolutePadding(left = if (sliverOnLeft) sliverInsetDp else 0.dp, right = if (sliverOnLeft) 0.dp else sliverInsetDp)
                    .padding(horizontal = 16.dp)
                    .graphicsAlpha(contentAlpha),
            ) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        L10n.tr("Recent apps"), color = HyleColors.InkBright, fontSize = 22.sp, fontWeight = FontWeight.Light,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    if (rows.isNotEmpty()) {
                        Text(
                            L10n.tr("Clear all"), color = HyleColors.Violet, fontSize = 14.sp,
                            modifier = Modifier.testTag("recents.clearAll").clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onClearAll)
                                .launcherItem("recents.clearAll", FocusKind.Control, order = 0, layer = RECENTS_FOCUS_LAYER, focusable = false, onActivate = onClearAll, ringRadiusDp = 24f)
                                .defaultMinSize(minHeight = 48.dp, minWidth = 48.dp).padding(horizontal = 14.dp, vertical = 14.dp),
                        )
                    }
                }
                // What the list can and cannot know, in one line (Android gives a launcher no list of live tasks).
                Text(
                    L10n.tr("Apps you left most recently, from Liftoff and, with usage access, from elsewhere. Android does not tell a launcher which apps are still running."),
                    color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp).testTag("recents.honesty"),
                )
                if (rows.isEmpty()) {
                    Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        RecentsEmptyState(usageAccessOn, onGrantUsage)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("recents.list"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(rows, key = { it.app.key }) { r ->
                            RecentsRow(r, now, onLaunch = { onLaunch(r.app) }, onMenu = { onMenu(r.app) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentsRow(r: RecentRow, now: Long, onLaunch: () -> Unit, onMenu: () -> Unit) {
    val ago = RecentsAgoText.text(now, r.timeMillis)
    val recent = L10n.tr("Recent")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp)
            .launcherItem("recents.${r.app.key}", FocusKind.Item, layer = RECENTS_FOCUS_LAYER, focusable = false, onActivate = onLaunch, onMenu = onMenu, ringRadiusDp = 14f)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onLaunch, onLongClick = onMenu, onLongClickLabel = L10n.tr("Menu"))
            .testTag("recents.row.${r.app.key}")
            .semantics(mergeDescendants = true) { contentDescription = "${r.app.label}, $recent, $ago" }
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        AppIcon(r.app, 48.dp)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(r.app.label, color = HyleColors.InkBright, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$recent · $ago", color = HyleColors.InkDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun Modifier.graphicsAlpha(alpha: () -> Float): Modifier = this.then(androidx.compose.ui.Modifier.graphicsLayer { this.alpha = alpha() })
