package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors

/**
 * Wave 41 (settingsPopups): the One UI page pieces other modules need (the dock's workspaces and app groups pages). Thin public faces on
 * the internal wave 40 parts, so a page written elsewhere looks like the category pages and cannot drift from them.
 */

/** A full One UI page: the 56 dp bar with a back arrow, the large collapsing title (and a live [subtitle]), then [content]. */
@Composable
fun OneUiPageFrame(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, tag: String? = null, content: LazyListScope.() -> Unit) =
    OneUiPage(title = title, subtitle = subtitle, onBack = onBack, backLabel = com.bos.sphere.core.design.L10n.tr("Back"), modifier = modifier, tag = tag, content = content)

/** A flat group of [rowCount] rows (separate them with [OneUiRowDivider]). */
@Composable
fun OneUiGroup(rowCount: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) = OneUiRows(rowCount, modifier, content)

@Composable
fun OneUiRowDivider(inset: androidx.compose.ui.unit.Dp = 20.dp) = OneUiDivider(inset)

/** The small grey heading above a group. */
@Composable
fun OneUiGroupTitle(text: String) = OneUiSectionHeader(text)

/** Grey caption text under a group (the hint of the group above it). */
@Composable
fun OneUiCaption(text: String, modifier: Modifier = Modifier) {
    Text(text, color = HyleColors.InkDim, fontSize = 13.sp, lineHeight = 18.sp, modifier = modifier.fillMaxWidth().padding(start = OneUi.RowPad, end = OneUi.RowPad, top = 8.dp, bottom = 4.dp))
}

@Composable
fun OneUiGap() { Spacer(Modifier.size(OneUi.GroupGap)) }

/** A block of plain explanation text (no rows), flat on the page. */
@Composable
fun OneUiInfoCard(text: String, tag: String? = null) {
    OneUiRows(1) {
        Text(text, color = HyleColors.Ink, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(horizontal = OneUi.RowPad, vertical = 12.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier))
    }
}

/** A standard row: optional icon tile, title, grey summary, and a trailing slot; tapping it runs [onClick]. */
@Composable
fun OneUiListRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    tile: ImageVector? = null,
    tileColor: Color = Color(0xFF5B7FE8),
    onClick: (() -> Unit)? = null,
    chevron: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    role: Role? = Role.Button,
) {
    OneUiRowFrame(
        title, modifier = modifier, summary = summary, onClick = onClick, role = role,
        min = if (tile != null) OneUi.RootRowMin else OneUi.RowMin,
        leading = if (tile != null) ({ OneUiIconTile(tile, tileColor) }) else null,
        trailing = if (trailing != null || chevron) ({ trailing?.invoke(this); if (chevron) OneUiChevron() }) else null,
    )
}

/** A switch row. */
@Composable
fun OneUiSwitchRow(
    title: String, checked: Boolean, onChange: (Boolean) -> Unit, summary: String? = null, modifier: Modifier = Modifier,
    /** Wave 58 (CFG-66): the row stays, dimmed, with this reason as its line, and does not react (the CFG-31 mechanism of the settings rows). */
    disabledReason: String? = null,
) {
    OneUiRowFrame(
        title, summary = summary, role = Role.Switch, onClick = { onChange(!checked) }, disabledReason = disabledReason,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = if (disabledReason == null) title else Disabled.description(title, disabledReason, com.bos.sphere.core.design.L10n.tr("Unavailable"))
            toggleableState = androidx.compose.ui.state.ToggleableState(checked)
        },
        trailing = { OneUiSwitch(checked) },
    )
}

/**
 * Wave 58 (CFG-66): a block of controls (a row of drawn cards) that cannot be used right now: dimmed like a row that is off, a layer over it takes
 * the touches, and a screen reader hears one node, "[label], unavailable, [reason]". With a null [reason] the content is shown as it is.
 */
@Composable
fun OneUiDisabledBlock(reason: String?, label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (reason == null) { Box(modifier) { content() }; return }
    Box(modifier.testTag(OneUiTags.DISABLED_ROW)) {
        val said = Disabled.description(label, reason, com.bos.sphere.core.design.L10n.tr("Unavailable"))
        Box(Modifier.androidx_alpha(Disabled.ALPHA).clearAndSetSemantics { contentDescription = said; disabled(); stateDescription = reason }) { content() }
        Box(Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() } } })
    }
}

private fun Modifier.androidx_alpha(a: Float): Modifier = this.graphicsLayer { alpha = a }

/** A row that shows its current value in grey and a chevron; a tap opens a full page of options (never a popup). */
@Composable
fun OneUiValueRow(title: String, value: String, onClick: () -> Unit, summary: String? = null, modifier: Modifier = Modifier) {
    OneUiRowFrame(
        title, summary = summary, onClick = onClick,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$title, $value" },
        trailing = {
            Text(value, color = HyleColors.InkDim, fontSize = 15.sp, maxLines = 2, textAlign = TextAlign.End, modifier = Modifier.padding(end = 4.dp).heightIn(max = 48.dp))
            OneUiChevron()
        },
    )
}

/** The small pill that marks the active item ("In use"): soft accent fill, accent text, with a tick. */
@Composable
fun OneUiBadge(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(HyleColors.Violet.copy(alpha = 0.22f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Glyphs.Check, null, tint = HyleColors.Violet, modifier = Modifier.size(14.dp))
        Text(text, color = HyleColors.Violet, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** A prominent full-width button of a page: a rounded tonal bar with an icon and a label (a page's main action). */
@Composable
fun OneUiPrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, glyph: ImageVector = Glyphs.Plus, enabled: Boolean = true) {
    Row(
        modifier.padding(horizontal = OneUi.Margin).fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).background(HyleColors.Violet.copy(alpha = if (enabled) 0.24f else 0.1f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(glyph, null, tint = HyleColors.Violet, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(10.dp))
        Text(label, color = HyleColors.Violet, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}
