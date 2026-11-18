package com.bos.sphere.feature.grid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.input.FocusKind

/** HOM-22: where a corner's hotspot sits and what it runs; one entry per usable corner of the Minimal landing. */
class Hotspot(val screen: Corner, val item: CornerItem)

private fun Corner.cornerAlignment(): Alignment = when (this) {
    Corner.TopLeft -> Alignment.TopStart
    Corner.TopRight -> Alignment.TopEnd
    Corner.BottomLeft -> Alignment.BottomStart
    Corner.BottomRight -> Alignment.BottomEnd
}

private fun Corner.spoken(what: String): String = when (this) {
    Corner.TopLeft -> L10n.tr("Top left hotspot: %s", what)
    Corner.TopRight -> L10n.tr("Top right hotspot: %s", what)
    Corner.BottomLeft -> L10n.tr("Bottom left hotspot: %s", what)
    Corner.BottomRight -> L10n.tr("Bottom right hotspot: %s", what)
}

/**
 * HOM-22, INP-16, INP-17: the four hotspots of the Minimal landing. Each is a labelled button in the focus graph (arrows and Tab reach
 * it, Enter runs it, TalkBack reads "Top left hotspot: Radar"), a click runs it, and [showHints] draws one faint dot per corner.
 * The pointer's dwell reveal is the shared hot-corner layer on the root; this layer is what keyboard, touch and TalkBack use.
 * Corners are physical, so the layer lays out left to right whatever the language.
 */
@Composable
fun MinimalHotspotLayer(
    hotspots: List<Hotspot>,
    appsByKey: Map<String, AppEntry>,
    showHints: Boolean,
    onRun: (CornerItem, Corner) -> Unit,
    modifier: Modifier = Modifier,
    /** HOM-23: draw the assigned action's glyph in the corner (the Glance home); off on the plain hotspot layer. */
    showIcons: Boolean = false,
    iconTint: Color = Color.White.copy(alpha = 0.6f),
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            for (h in hotspots) {
                val what = itemLabel(h.item, appsByKey)
                val spoken = h.screen.spoken(what)
                Box(
                    Modifier.align(h.screen.cornerAlignment()).size(64.dp)
                        .launcherItem("minimal.corner.${h.screen.name}", kind = FocusKind.Corner, focusable = true, onActivate = { onRun(h.item, h.screen) }, ringRadiusDp = 32f)
                        .semantics { role = Role.Button; contentDescription = spoken }
                        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClickLabel = what) { onRun(h.item, h.screen) },
                ) {
                    if (showIcons) Box(Modifier.align(Alignment.Center)) { ItemGlyph(h.item, appsByKey, 24.dp, iconTint) }
                    if (showHints) {
                        val inset = 14.dp
                        Box(
                            Modifier.align(h.screen.cornerAlignment()).padding(inset).size(6.dp)
                                .background(Color.White.copy(alpha = 0.28f), CircleShape),
                        )
                    }
                }
            }
        }
    }
}

/** HOM-22: the quiet empty state of the Recent apps landing: one line, plus how to include apps opened from elsewhere. */
@Composable
fun RecentsEmptyState(usageAccessOn: Boolean, onGrantUsage: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(L10n.tr("No recent apps yet"), color = HyleColors.InkBright, fontSize = 17.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
        Text(
            L10n.tr("Apps you open from Liftoff show up here, newest first."), color = HyleColors.InkDim, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
        if (!usageAccessOn) {
            Text(
                L10n.tr("To include apps you open from elsewhere, allow usage access."), color = HyleColors.InkDim, fontSize = 13.sp, textAlign = TextAlign.Center,
            )
            Text(
                L10n.tr("Allow usage access"), color = HyleColors.Violet, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onGrantUsage).defaultMinSize(minHeight = 48.dp).padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}
