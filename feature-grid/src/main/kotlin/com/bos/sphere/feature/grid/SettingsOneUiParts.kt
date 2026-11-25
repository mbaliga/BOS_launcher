@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.bos.sphere.feature.grid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.rememberMotionPrefs

/** Wave 40 (settingsOneUI): the One UI style building blocks of the settings: cards, rows, the switch, the slider, the radio. */
internal object OneUi {
    val TileShape = RoundedCornerShape(12.dp)
    val Margin = 16.dp
    /** The empty space between two groups of rows (wave 54: there is no box, so the gap and the grey header do the separating). */
    val GroupGap = 28.dp
    /** The start and end padding of a row: text starts here (and the divider under it), the section header too. */
    val RowPad = 24.dp
    /** Width of the icon tile of a root row plus the gap after it: where the text of such a row starts, from the row padding. */
    val TileAdvance = 56.dp
    val RowMin = 56.dp
    val RootRowMin = 64.dp
    val Page: Color get() = HyleColors.Background
    /** The one filled field left in settings: the search field (a control, not a box around rows). */
    val Field: Color get() = HyleColors.Surface
    /** The unchecked track of the switch and the unfilled part of a slider. */
    val Track: Color get() = if (com.bos.sphere.core.design.HyleTheme.isLight) Color(0xFFC9CCD4) else Color(0xFF454A57)
    val Focus: Color get() = HyleColors.Violet.copy(alpha = 0.18f)
    val Highlight: Color get() = HyleColors.Violet.copy(alpha = 0.26f)
}

/**
 * Wave 54 (settingsLines): a group of rows, flat on the page (no filled rounded box). The rows are a list for the screen reader; thin
 * lines between them come from [OneUiDivider], the grey [OneUiSectionHeader] above names the group.
 */
@Composable
internal fun OneUiRows(rowCount: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .testTag(OneUiTags.GROUP)
            .semantics { collectionInfo = CollectionInfo(rowCount, 1) },
    ) { content() }
}

/**
 * The hairline between two rows, inset from the start so it begins where the text begins. [inset] is a legacy size: 20 dp or less means a
 * row without a tile (text at the row padding), more means a root row with an icon tile (text after the tile). Mirrors in RTL.
 */
@Composable
internal fun OneUiDivider(inset: androidx.compose.ui.unit.Dp) {
    val start = OneUi.RowPad + if (inset > 20.dp) OneUi.TileAdvance else 0.dp
    Box(Modifier.padding(start = start, end = OneUi.RowPad).fillMaxWidth().height(1.dp).background(HyleColors.Hairline).testTag(OneUiTags.DIVIDER))
}

/** The small grey title above a group. A heading for the screen reader. */
@Composable
internal fun OneUiSectionHeader(text: String) {
    Text(
        text, color = HyleColors.InkDim, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().padding(start = OneUi.RowPad, end = OneUi.RowPad, top = 24.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
internal fun OneUiChevron() {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(Glyphs.Down, null, tint = HyleColors.InkDim, modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = if (rtl) 90f else -90f })
}

/** The back arrow of the top bar: points to the start, so it mirrors in a right-to-left language. */
@Composable
internal fun OneUiBackArrow(modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(Glyphs.Down, null, tint = HyleColors.InkBright, modifier = modifier.size(24.dp).graphicsLayer { rotationZ = if (rtl) -90f else 90f })
}

/**
 * The shared row: a 56 dp (or [min]) row with the title (17 sp), the grey line beneath it, a trailing control, and optional content
 * below (a slider). Clickable rows take focus from the keyboard and show it; [highlighted] marks the row a search result opened.
 */
@Composable
internal fun OneUiRowFrame(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    highlighted: Boolean = false,
    selected: Boolean = false,
    min: androidx.compose.ui.unit.Dp = OneUi.RowMin,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    role: Role? = Role.Button,
    /** Wave 56 (CFG-31): the row stays on the page but cannot be used right now; the reason replaces the summary line and the row is dimmed and does not react. */
    disabledReason: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val reveal = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    // Wave 57: two frames later, so the list's own jump to the row's group (a deep link into a tab) has landed first and the row then comes fully into sight.
    LaunchedEffect(highlighted) { if (highlighted) { repeat(2) { androidx.compose.runtime.withFrameNanos { } }; runCatching { reveal.bringIntoView() } } }
    val big = LocalDensity.current.fontScale >= 1.5f
    val fill = when {
        highlighted -> OneUi.Highlight
        selected -> OneUi.Focus
        focused -> OneUi.Focus
        else -> Color.Transparent
    }
    Column(
        modifier
            .fillMaxWidth()
            .androidxBringIntoView(reveal)
            .semantics { if (highlighted) this.selected = true }
            .background(fill)
            .then(if (focused) Modifier.border(2.dp, HyleColors.Violet).testTag(OneUiTags.FOCUS_RING) else Modifier)
            .then(if (onClick != null && disabledReason == null) Modifier.onFocusChanged { focused = it.isFocused }.clickable(role = role, onClick = onClick) else Modifier)
            .then(if (disabledReason != null) Modifier.testTag(OneUiTags.DISABLED_ROW).semantics { disabled(); stateDescription = disabledReason } else Modifier)
            .padding(horizontal = OneUi.RowPad, vertical = 10.dp),
    ) {
        val dim = if (disabledReason != null) Modifier.graphicsLayer { alpha = Disabled.ALPHA } else Modifier
        Row(Modifier.heightIn(min = min - 20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) { Box(dim) { leading() }; Spacer(Modifier.size(16.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, color = HyleColors.InkBright, fontSize = 17.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = dim)
                val line = disabledReason ?: summary
                if (!line.isNullOrEmpty()) Text(line, color = HyleColors.InkDim, fontSize = 13.sp, maxLines = if (leading != null && !big) 1 else 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp).testTag(if (disabledReason != null) OneUiTags.DISABLED_REASON else "oneui.summary"))
            }
            if (trailing != null) { Spacer(Modifier.size(12.dp)); Box(dim) { Row(verticalAlignment = Alignment.CenterVertically) { trailing() } } }
        }
        if (below != null) Box(dim) { below() }
    }
}

private fun Modifier.androidxBringIntoView(r: androidx.compose.foundation.relocation.BringIntoViewRequester) = this.bringIntoViewRequester(r)

/** The coloured rounded-square tile with a white glyph, at the start of a root row. */
@Composable
internal fun OneUiIconTile(glyph: ImageVector, accent: Color) {
    Box(Modifier.size(40.dp).clip(OneUi.TileShape).background(accent), contentAlignment = Alignment.Center) {
        Icon(glyph, null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** One UI's switch: a rounded track and a white thumb that slides. No animation under reduced motion. */
@Composable
internal fun OneUiSwitch(checked: Boolean) {
    val reduced = rememberMotionPrefs().reduced
    val pos = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) {
        val t = if (checked) 1f else 0f
        if (reduced) pos.snapTo(t) else pos.animateTo(t, tween(150))
    }
    val track = lerp(OneUi.Track, HyleColors.Violet, pos.value)
    Box(Modifier.size(width = 50.dp, height = 28.dp).clip(CircleShape).background(track), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.offset(x = 4.dp + 22.dp * pos.value).size(20.dp).clip(CircleShape).background(Color.White))
    }
}

/** What a screen reader says for a row: the title and its value, or, while the row is off, the title, that it is unavailable and why. */
internal fun rowDescription(title: String, value: String, reason: String?): String =
    if (reason == null) "$title, $value" else Disabled.description(title, reason, L10n.tr("Unavailable"))

@Composable
internal fun OneUiToggleRow(item: SettingItem.Toggle, highlighted: Boolean, onToggle: (Boolean) -> Unit) {
    val off = item.meta.disabledReason
    OneUiRowFrame(
        item.title, summary = item.hint, highlighted = highlighted,
        role = Role.Switch, disabledReason = off,
        onClick = { onToggle(!item.checked) },
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = rowDescription(item.title, item.value, off)
            stateDescription = off ?: item.value
            toggleableState = ToggleableState(item.checked)
        },
        trailing = { OneUiSwitch(item.checked) },
    )
}

/**
 * A slider row: the title and the live readout (with its unit), the slider beneath. Wave 56 (CFG-29): a small tick on the track marks the
 * default, a flat "Default" text action shows while the value differs from it (no box), and a derived preference reads "Auto" and offers no default.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun OneUiSliderRow(item: SettingItem.Slider, highlighted: Boolean, onTick: () -> Unit) {
    val step = if (item.step > 0f) item.step else SettingsMath.autoStep(item.range)
    var band by remember { mutableIntStateOf(SettingsMath.tickBand(item.amount, item.range)) }
    val span = (item.range.endInclusive - item.range.start).coerceAtLeast(1e-6f)
    val off = item.meta.disabledReason
    val defaultAt = item.default?.takeIf { !item.auto }
    OneUiRowFrame(
        item.title, summary = item.hint, highlighted = highlighted, disabledReason = off,
        trailing = {
            if (item.offersDefault && off == null) {
                Text(
                    L10n.tr("Default"), color = HyleColors.Violet, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.testTag(OneUiTags.SLIDER_DEFAULT).heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClickLabel = L10n.tr("Reset %s to its default", item.title)) { item.default?.let { d -> item.onChange(SettingsMath.snap(d, item.range, step)); onTick() } }
                        .padding(horizontal = 10.dp, vertical = 14.dp),
                )
            }
            Text(item.valueText, color = HyleColors.InkDim, fontSize = 15.sp, modifier = Modifier.testTag(OneUiTags.SLIDER_READOUT))
        },
        below = {
            Slider(
                value = item.amount.coerceIn(item.range.start, item.range.endInclusive),
                onValueChange = { raw ->
                    val v = SettingsMath.snap(raw, item.range, step)
                    val b = SettingsMath.tickBand(v, item.range)
                    if (b != band) { band = b; onTick() }
                    if (v != item.amount) item.onChange(v)
                },
                enabled = off == null,
                valueRange = item.range,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                    val d = if (item.auto) L10n.tr("Auto") else item.valueText
                    contentDescription = rowDescription(item.title, d, off); stateDescription = off ?: d
                },
                thumb = { Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White).padding(3.dp).clip(CircleShape).background(HyleColors.Violet.copy(alpha = if (item.auto) 0.5f else 1f))) },
                track = { st ->
                    val f = ((st.value - item.range.start) / span).coerceIn(0f, 1f)
                    BoxWithConstraints(Modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.CenterStart) {
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(OneUi.Track)) {
                            Box(Modifier.fillMaxWidth(f).height(4.dp).background(HyleColors.Violet))
                        }
                        if (defaultAt != null) {
                            // The default tick: a thin mark where the default sits, drawn between the thumb centres at both ends of the range.
                            val x = 11.dp + (maxWidth - 22.dp) * SettingsMath.fraction(defaultAt, item.range) - 1.dp
                            Box(Modifier.offset(x = x).size(width = 2.dp, height = 12.dp).clip(CircleShape).background(HyleColors.InkDim).testTag(OneUiTags.SLIDER_TICK))
                        }
                    }
                },
                colors = SliderDefaults.colors(thumbColor = HyleColors.Violet, activeTrackColor = HyleColors.Violet, inactiveTrackColor = OneUi.Track),
            )
        },
    )
}

@Composable
internal fun OneUiStepperRow(item: SettingItem.Stepper, highlighted: Boolean, onTick: () -> Unit) {
    val off = item.meta.disabledReason
    val canDown = item.count > item.range.first && off == null
    val canUp = item.count < item.range.last && off == null
    val control: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", L10n.tr("Previous %s", item.title), canDown) { onTick(); item.onChange((item.count - item.step).coerceAtLeast(item.range.first)) }
            Text(item.value, color = HyleColors.InkBright, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 44.dp))
            StepButton("+", L10n.tr("Next %s", item.title), canUp) { onTick(); item.onChange((item.count + item.step).coerceAtMost(item.range.last)) }
        }
    }
    val big = LocalDensity.current.fontScale >= 1.5f
    val sem = Modifier.semantics {
        stateDescription = off ?: item.value
        if (off != null) contentDescription = rowDescription(item.title, item.value, off)
        progressBarRangeInfo = ProgressBarRangeInfo(item.count.toFloat(), item.range.first.toFloat()..item.range.last.toFloat(), 0)
        if (off == null) setProgress { v -> item.onChange(item.fromProgress(v)); true }
    }
    OneUiRowFrame(
        item.title, summary = item.hint, highlighted = highlighted, modifier = sem, disabledReason = off,
        trailing = if (big) null else ({ control() }),
        below = if (big) ({ Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.CenterEnd) { control() } }) else null,
    )
}

@Composable
private fun StepButton(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(OneUi.Track.copy(alpha = if (enabled) 0.55f else 0.25f)), contentAlignment = Alignment.Center) {
            Text(glyph, color = HyleColors.InkBright.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 20.sp)
        }
    }
}

/** A choice without cards: the title, its hint, and the current value in grey at the end with a chevron; a tap opens its full radio page. */
@Composable
internal fun OneUiChoiceRow(item: SettingItem.Choice<*>, highlighted: Boolean, onOpen: () -> Unit) {
    val off = item.meta.disabledReason
    OneUiRowFrame(
        item.title, summary = item.hint, highlighted = highlighted, onClick = onOpen, disabledReason = off,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = rowDescription(item.title, item.value, off) },
        trailing = {
            Text(item.value, color = HyleColors.InkDim, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 150.dp))
            Spacer(Modifier.size(4.dp)); OneUiChevron()
        },
    )
}

/**
 * A choice with a row of live preview cards (wave 46): the title and its hint, then the cards. The cards ARE the control: they apply as they
 * are tapped, so the row has no chevron, no page of its own and no caption repeating the picked value under them. The picked value is still
 * read out through the row's description.
 */
@Composable
internal fun OneUiRailRow(item: SettingItem.Choice<*>, highlighted: Boolean) {
    val off = item.meta.disabledReason
    Column(Modifier.fillMaxWidth().background(if (highlighted) OneUi.Highlight else Color.Transparent).padding(bottom = 12.dp).testTag(OneUiTags.RAIL)) {
        OneUiRowFrame(
            item.title, summary = item.hint, role = null, disabledReason = off,
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = rowDescription(item.title, item.value, off) },
        )
        // A rail that is off keeps its cards on the page, dimmed and out of reach: a transparent layer over them takes the touches.
        Box(if (off != null) Modifier.graphicsLayer { alpha = Disabled.ALPHA } else Modifier) {
            CompositionLocalProvider(LocalRailInset provides 0.dp) { Column(Modifier.fillMaxWidth().padding(horizontal = OneUi.Margin)) { item.preview?.invoke(this) } }
            if (off != null) Box(Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() } } }.clearAndSetSemantics { })
        }
    }
}

@Composable
internal fun OneUiActionRow(item: SettingItem.Action, highlighted: Boolean, onClick: () -> Unit) {
    val sub = listOfNotNull(item.value.takeIf { it.isNotEmpty() }, item.hint?.takeIf { it.isNotEmpty() }).joinToString(" · ")
    val off = item.meta.disabledReason
    OneUiRowFrame(
        item.title, summary = sub, highlighted = highlighted, onClick = onClick, disabledReason = off,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = rowDescription(item.title, item.value, off) },
        trailing = if (item.navigates) ({ OneUiChevron() }) else null,
    )
}

@Composable
internal fun OneUiPaneRow(item: SettingItem.Pane, highlighted: Boolean, onClick: () -> Unit) {
    val off = item.meta.disabledReason
    OneUiRowFrame(
        item.title, summary = item.value.takeIf { it.isNotEmpty() }, highlighted = highlighted, onClick = onClick, disabledReason = off,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = rowDescription(item.title, item.value, off) },
        trailing = { OneUiChevron() },
    )
}

/** A row of the radio list page: the label (and a grey line) and a radio dot at the end. */
@Composable
internal fun OneUiRadioRow(label: String, selected: Boolean, detail: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OneUiRowFrame(
        label, summary = detail, role = Role.RadioButton, onClick = onClick,
        modifier = modifier.semantics(mergeDescendants = true) { this.selected = selected; contentDescription = label },
        trailing = {
            Box(Modifier.size(22.dp).clip(CircleShape).background(if (selected) HyleColors.Violet else OneUi.Track), contentAlignment = Alignment.Center) {
                Box(Modifier.size(if (selected) 8.dp else 16.dp).clip(CircleShape).background(if (selected) Color.White else OneUi.Page))
            }
        },
    )
}

/** A row of a multi-select page: the label and a checkbox at the end. */
@Composable
internal fun OneUiCheckRow(label: String, checked: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OneUiRowFrame(
        label, role = Role.Checkbox, onClick = onClick,
        modifier = modifier.semantics(mergeDescendants = true) { this.selected = checked; contentDescription = label },
        trailing = { OneUiCheckMark(checked) },
    )
}

/** The rounded search field under the large title. */
@Composable
internal fun OneUiSearchField(query: String, onQuery: (String) -> Unit, hint: String, modifier: Modifier = Modifier, focusRequester: androidx.compose.ui.focus.FocusRequester? = null) {
    Row(
        modifier.padding(horizontal = OneUi.Margin).fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(OneUi.Field).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Glyphs.Search, null, tint = HyleColors.InkDim, modifier = Modifier.size(22.dp))
        // Wave 57: the field itself is the whole 48 dp height of the row (it is always on Settings home now), so a tap anywhere on it focuses it.
        Box(Modifier.weight(1f).padding(start = 12.dp)) {
            androidx.compose.foundation.text.BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = HyleColors.InkBright, fontSize = 16.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(HyleColors.Violet),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier).testTag(OneUiTags.SEARCH).semantics { contentDescription = hint },
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text(hint, color = HyleColors.InkDim, fontSize = 16.sp, maxLines = 1)
                        inner()
                    }
                },
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Glyphs.Close, L10n.tr("Clear"), tint = HyleColors.InkDim,
                modifier = Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { onQuery("") }.padding(13.dp),
            )
        }
    }
}
