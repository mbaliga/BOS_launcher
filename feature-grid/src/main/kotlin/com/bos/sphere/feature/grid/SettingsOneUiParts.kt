@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.bos.sphere.feature.grid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.contentDescription
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
    val CardShape = RoundedCornerShape(26.dp)
    val TileShape = RoundedCornerShape(12.dp)
    val Margin = 16.dp
    val CardGap = 12.dp
    val RowMin = 56.dp
    val RootRowMin = 64.dp
    val Page: Color get() = HyleColors.Background
    val Card: Color get() = HyleColors.Surface
    /** The unchecked track of the switch and the unfilled part of a slider. */
    val Track: Color get() = if (com.bos.sphere.core.design.HyleTheme.isLight) Color(0xFFC9CCD4) else Color(0xFF454A57)
    val Focus: Color get() = HyleColors.Violet.copy(alpha = 0.18f)
    val Highlight: Color get() = HyleColors.Violet.copy(alpha = 0.26f)
}

/** A rounded card: the rows inside it are a list for the screen reader; separators are drawn by [OneUiDivider] between the rows. */
@Composable
internal fun OneUiCard(rowCount: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .padding(horizontal = OneUi.Margin)
            .fillMaxWidth()
            .clip(OneUi.CardShape)
            .background(OneUi.Card)
            .semantics { collectionInfo = CollectionInfo(rowCount, 1) },
    ) { content() }
}

/** The hairline between two rows of a card, inset from the start by [inset] (the icon of a root row, the text margin of a page row). */
@Composable
internal fun OneUiDivider(inset: androidx.compose.ui.unit.Dp) {
    Box(Modifier.padding(start = inset, end = 20.dp).fillMaxWidth().height(1.dp).background(HyleColors.Hairline))
}

/** The small grey title above a card. A heading for the screen reader. */
@Composable
internal fun OneUiSectionHeader(text: String) {
    Text(
        text, color = HyleColors.InkDim, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().padding(start = OneUi.Margin + 16.dp, end = OneUi.Margin, top = 18.dp, bottom = 8.dp).semantics { heading() },
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
    trailing: (@Composable RowScope.() -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val reveal = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    LaunchedEffect(highlighted) { if (highlighted) runCatching { reveal.bringIntoView() } }
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
            .then(if (onClick != null) Modifier.onFocusChanged { focused = it.isFocused }.clickable(role = role, onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(Modifier.heightIn(min = min - 20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) { leading(); Spacer(Modifier.size(16.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, color = HyleColors.InkBright, fontSize = 17.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (!summary.isNullOrEmpty()) Text(summary, color = HyleColors.InkDim, fontSize = 13.sp, maxLines = if (leading != null && !big) 1 else 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            if (trailing != null) { Spacer(Modifier.size(12.dp)); trailing() }
        }
        below?.invoke()
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

@Composable
internal fun OneUiToggleRow(item: SettingItem.Toggle, highlighted: Boolean, onToggle: (Boolean) -> Unit) {
    OneUiRowFrame(
        item.title, summary = item.hint, highlighted = highlighted,
        role = Role.Switch,
        onClick = { onToggle(!item.checked) },
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "${item.title}, ${item.value}"
            stateDescription = item.value
            toggleableState = ToggleableState(item.checked)
        },
        trailing = { OneUiSwitch(item.checked) },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun OneUiSliderRow(item: SettingItem.Slider, highlighted: Boolean, onTick: () -> Unit) {
    val step = if (item.step > 0f) item.step else SettingsMath.autoStep(item.range)
    var band by remember { mutableIntStateOf(SettingsMath.tickBand(item.amount, item.range)) }
    val span = (item.range.endInclusive - item.range.start).coerceAtLeast(1e-6f)
    OneUiRowFrame(
        item.title, summary = item.hint, highlighted = highlighted,
        trailing = { Text(item.valueText, color = HyleColors.InkDim, fontSize = 15.sp) },
        below = {
            Slider(
                value = item.amount.coerceIn(item.range.start, item.range.endInclusive),
                onValueChange = { raw ->
                    val v = SettingsMath.snap(raw, item.range, step)
                    val b = SettingsMath.tickBand(v, item.range)
                    if (b != band) { band = b; onTick() }
                    if (v != item.amount) item.onChange(v)
                },
                valueRange = item.range,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "${item.title}, ${item.valueText}"; stateDescription = item.valueText },
                thumb = { Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White).padding(3.dp).clip(CircleShape).background(HyleColors.Violet)) },
                track = { st ->
                    val f = ((st.value - item.range.start) / span).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(OneUi.Track)) {
                        Box(Modifier.fillMaxWidth(f).height(4.dp).background(HyleColors.Violet))
                    }
                },
                colors = SliderDefaults.colors(thumbColor = HyleColors.Violet, activeTrackColor = HyleColors.Violet, inactiveTrackColor = OneUi.Track),
            )
        },
    )
}

@Composable
internal fun OneUiStepperRow(item: SettingItem.Stepper, highlighted: Boolean, onTick: () -> Unit) {
    val canDown = item.count > item.range.first
    val canUp = item.count < item.range.last
    val control: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", L10n.tr("Previous %s", item.title), canDown) { onTick(); item.onChange((item.count - item.step).coerceAtLeast(item.range.first)) }
            Text(item.value, color = HyleColors.InkBright, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 44.dp))
            StepButton("+", L10n.tr("Next %s", item.title), canUp) { onTick(); item.onChange((item.count + item.step).coerceAtMost(item.range.last)) }
        }
    }
    val big = LocalDensity.current.fontScale >= 1.5f
    val sem = Modifier.semantics {
        stateDescription = item.value
        progressBarRangeInfo = ProgressBarRangeInfo(item.count.toFloat(), item.range.first.toFloat()..item.range.last.toFloat(), 0)
        setProgress { v -> item.onChange(item.fromProgress(v)); true }
    }
    OneUiRowFrame(
        item.title, summary = item.hint, highlighted = highlighted, modifier = sem,
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

/** A choice: the title, its hint, and the current value in grey at the end; a tap opens the radio list page. */
@Composable
internal fun OneUiChoiceRow(item: SettingItem.Choice<*>, highlighted: Boolean, onOpen: () -> Unit) {
    OneUiRowFrame(
        item.title, summary = item.hint, highlighted = highlighted, onClick = onOpen,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${item.title}, ${item.value}" },
        trailing = {
            Text(item.value, color = HyleColors.InkDim, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 150.dp))
            Spacer(Modifier.size(4.dp)); OneUiChevron()
        },
    )
}

/** A choice with a row of live preview cards: the title (opens the radio list), the cards, the current value. The cards apply as they are tapped. */
@Composable
internal fun OneUiRailRow(item: SettingItem.Choice<*>, highlighted: Boolean, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(if (highlighted) OneUi.Highlight else Color.Transparent).padding(bottom = 12.dp)) {
        OneUiRowFrame(
            item.title, summary = item.hint, onClick = onOpen,
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${item.title}, ${item.value}" },
            trailing = { OneUiChevron() },
        )
        CompositionLocalProvider(LocalRailInset provides 0.dp) { Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { item.preview?.invoke(this) } }
        Text(item.value, color = HyleColors.InkDim, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}

@Composable
internal fun OneUiActionRow(item: SettingItem.Action, highlighted: Boolean, onClick: () -> Unit) {
    val sub = listOfNotNull(item.value.takeIf { it.isNotEmpty() }, item.hint?.takeIf { it.isNotEmpty() }).joinToString(" · ")
    OneUiRowFrame(
        item.title, summary = sub, highlighted = highlighted, onClick = onClick,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${item.title}, ${item.value}" },
        trailing = if (item.navigates) ({ OneUiChevron() }) else null,
    )
}

@Composable
internal fun OneUiPaneRow(item: SettingItem.Pane, highlighted: Boolean, onClick: () -> Unit) {
    OneUiRowFrame(
        item.title, summary = item.value.takeIf { it.isNotEmpty() }, highlighted = highlighted, onClick = onClick,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${item.title}, ${item.value}" },
        trailing = { OneUiChevron() },
    )
}

/** A row of the radio list page: the label and a radio dot at the end. */
@Composable
internal fun OneUiRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    OneUiRowFrame(
        label, role = Role.RadioButton, onClick = onClick,
        modifier = Modifier.semantics(mergeDescendants = true) { this.selected = selected; contentDescription = label },
        trailing = {
            Box(Modifier.size(22.dp).clip(CircleShape).background(if (selected) HyleColors.Violet else OneUi.Track), contentAlignment = Alignment.Center) {
                Box(Modifier.size(if (selected) 8.dp else 16.dp).clip(CircleShape).background(if (selected) Color.White else OneUi.Card))
            }
        },
    )
}

/** The rounded search field under the large title. */
@Composable
internal fun OneUiSearchField(query: String, onQuery: (String) -> Unit, hint: String, modifier: Modifier = Modifier, focusRequester: androidx.compose.ui.focus.FocusRequester? = null) {
    Row(
        modifier.padding(horizontal = OneUi.Margin).fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(OneUi.Card).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Glyphs.Search, null, tint = HyleColors.InkDim, modifier = Modifier.size(22.dp))
        Box(Modifier.weight(1f).padding(start = 12.dp, top = 12.dp, bottom = 12.dp)) {
            if (query.isEmpty()) Text(hint, color = HyleColors.InkDim, fontSize = 16.sp, maxLines = 1)
            androidx.compose.foundation.text.BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = HyleColors.InkBright, fontSize = 16.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(HyleColors.Violet),
                modifier = Modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier).testTag(OneUiTags.SEARCH).semantics { contentDescription = hint },
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
