package com.bos.sphere.feature.dock

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.DockStyle
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.rememberMotionPrefs
import com.bos.sphere.feature.grid.Glyphs

/**
 * W55 (dockSheet): the pieces of editing the dock in place. A pinned icon wiggles a little (a loop that asks the motion preference: reduced motion
 * draws it still), wears a small x, and drags to reorder with no long press first; a ring with a plus ends the row and opens the app picker.
 */
object DockEditTags {
    fun remove(item: DockItem) = "dock.remove.${item.ref.encode()}"
    const val ADD_TILE = "dock.addTile"
}

/** Degrees of the wiggle at this moment; 0 with reduced motion. [seed] gives neighbours different phases. */
@Composable
internal fun rememberDockWiggle(seed: Int): Float {
    if (rememberMotionPrefs().reduced) return 0f
    val t = rememberInfiniteTransition(label = "dockWiggle")
    val a by t.animateFloat(-1.8f, 1.8f, infiniteRepeatable(tween(120 + (seed % 3) * 23), RepeatMode.Reverse), label = "wiggle")
    return a
}

/** The x on a pinned icon: removes it from the dock. The touch area is 32 dp (inside the 52 dp slot), the drawn disc 20 dp. */
@Composable
internal fun BoxScope.DockRemoveBadge(item: DockItem, onRemove: (DockItem) -> Unit) {
    Box(
        Modifier.align(Alignment.TopEnd).size(32.dp).testTag(DockEditTags.remove(item))
            .clickable(role = Role.Button) { onRemove(item) }
            .semantics { contentDescription = L10n.tr("Remove %s from the dock", item.label) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF3A3D49)).border(1.dp, Color(0x99FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Glyphs.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

/** The plus tile at the end of the pinned icons while editing: opens the app picker as a page. */
@Composable
internal fun DockAddTile(onClick: () -> Unit, bridge: DockInput.FocusBridge?, style: DockStyle) {
    val tint = glyphColor(style, HyleColors.Ink)
    Box(
        Modifier.size(40.dp).clip(CircleShape).glyphHalo(style).border(1.5.dp, if (style == DockStyle.Transparent) tint else HyleColors.Violet, CircleShape)
            .testTag(DockEditTags.ADD_TILE).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = L10n.tr("Add apps to the dock") }
            .drawBehind {
                val c = Offset(size.width / 2, size.height / 2); val r = size.width * 0.22f
                drawLine(tint, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = 3f)
                drawLine(tint, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = 3f)
            },
    )
}
