package com.bos.sphere.feature.grid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.HyleTheme

/**
 * Wave 54 (settingsVisual): the one visual choice of the app. Every pick from a small fixed set (two to eight options) is a row of drawn cards:
 * a Canvas illustration (no bitmaps) over a short label; the picked card has an accent ring and a check badge; the card is a radio button (or a
 * checkbox when [multi]) whose visible label is what a screen reader reads, at least 48 dp in both directions, with a ring for keyboard and
 * controller focus. A group wraps ([FlowRow]) so a card is never off screen, whatever the font scale or window width.
 * The Settings rows, the Home quick sheet, the Files sort and group sheet and the dock style page all use [OneUiVisualCard], so they cannot drift.
 */
object VisualCards {
    /** Card and art sizes: the same everywhere (the Home sheet's cards of wave 51 set them). */
    val CardWidth = 80.dp
    val ArtHeight = 64.dp
    /** The wide card (fonts: a sample line needs more room than a picture). */
    val WideWidth = 148.dp
    val MinTouch = 48.dp
    val Gap = 10.dp
    val RowGap = 6.dp
    val Radius = 16.dp
}

/** The colours an illustration draws with; it never reads a fixed colour, so one drawing serves the dark and the light theme. */
class VisualInk(
    /** Main strokes and fills. */
    val strong: Color,
    /** Secondary shapes. */
    val soft: Color,
    /** Hairlines and the "off" or empty parts. */
    val faint: Color,
    /** The tile behind the picture. */
    val tile: Color,
    /** The accent of the theme (the ring of the picked card and any highlighted part). */
    val accent: Color,
    val onAccent: Color,
    /** Label colours under the tile. */
    val label: Color,
    val labelPicked: Color,
    val dark: Boolean,
) {
    companion object {
        /** The ink of a dark surface (the Home sheet, the Files sheet, tests): fixed, no theme read. */
        val Dark = VisualInk(
            strong = Color(0xFFE7E9F0), soft = Color(0x80E7E9F0), faint = Color(0x33E7E9F0), tile = Color(0xFF15161B),
            accent = Color(0xFF8A7BFF), onAccent = Color.White, label = Color(0xB3FFFFFF), labelPicked = Color.White, dark = true,
        )
        val Light = VisualInk(
            strong = Color(0xFF1B1E29), soft = Color(0x801B1E29), faint = Color(0x331B1E29), tile = Color(0xFFE9EBF2),
            accent = Color(0xFF5B4BD6), onAccent = Color.White, label = Color(0xFF666C7C), labelPicked = Color(0xFF0E1018), dark = false,
        )

        /** The ink of the current theme on the settings surface: the page background as the tile, so the picture stands out of the card it sits in. */
        @Composable
        fun current(forceDark: Boolean? = null): VisualInk {
            val dark = forceDark ?: !HyleTheme.isLight
            val base = if (dark) Dark else Light
            val tile = if (forceDark == null) HyleColors.Background else base.tile
            return VisualInk(
                strong = base.strong, soft = base.soft, faint = base.faint, tile = tile, accent = HyleColors.Violet, onAccent = HyleColors.OnAccent,
                label = if (forceDark == null) HyleColors.InkDim else base.label, labelPicked = if (forceDark == null) HyleColors.InkBright else base.labelPicked, dark = dark,
            )
        }
    }
}

/** What an illustration is: it draws into the whole of the art area (read [DrawScope.size]) with the ink it is given; dp via [DrawScope.density]. */
typealias VisualArt = DrawScope.(VisualInk) -> Unit

/**
 * One card: [art] over [label]. Used directly for a card that is not a plain enum value (a font, a dock style) and by [OneUiVisualChoice].
 * [modifier] is applied before the selection semantics (a focus node id, for one). [dark] null follows the theme; true and false force an ink.
 */
@Composable
fun OneUiVisualCard(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = VisualCards.CardWidth,
    multi: Boolean = false,
    dark: Boolean? = null,
    accent: Color? = null,
    art: @Composable BoxScope.(VisualInk) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val base = VisualInk.current(dark)
    val ring = accent ?: base.accent
    val onRing = if (accent == null) base.onAccent else HyleColors.OnAccent
    val tile = RoundedCornerShape(VisualCards.Radius)
    val select = if (multi) Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
    else Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    Column(
        Modifier.width(width).testTag(tag).onFocusChanged { focused = it.isFocused }.then(modifier).then(select).semantics(mergeDescendants = true) { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().height(VisualCards.ArtHeight).clip(tile).background(base.tile)
                .border(if (selected) 2.5.dp else 1.dp, if (selected) ring else if (focused) base.strong else base.faint, tile),
            contentAlignment = Alignment.Center,
        ) {
            art(base)
            if (selected) {
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(ring), contentAlignment = Alignment.Center) {
                    Icon(Glyphs.Check, null, tint = onRing, modifier = Modifier.size(13.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label, color = if (selected) base.labelPicked else base.label, fontSize = 12.sp, lineHeight = 15.sp, textAlign = TextAlign.Center,
            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 18.dp),
        )
        Spacer(Modifier.height(4.dp))
    }
}

/** A drawn illustration as the art of a card. */
@Composable
fun BoxScope.VisualCanvas(ink: VisualInk, art: VisualArt) {
    Canvas(Modifier.fillMaxSize()) { art(ink) }
}

/** One group of cards in a row that wraps. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisualCardGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier.fillMaxWidth().padding(vertical = 4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(VisualCards.Gap), verticalArrangement = Arrangement.spacedBy(VisualCards.RowGap),
    ) { content() }
}

/** The stable tag part of an option: an enum's name, else its text. */
fun visualId(option: Any?): String = if (option == null) "off" else (option as? Enum<*>)?.name ?: option.toString()

/**
 * A single choice as a row of drawn cards. [art] gives the illustration of each option; [tag] + "." + the option's [id] is the card's test tag.
 * Picking a card applies at once ([onSelect]); picking the picked one again does nothing new.
 */
@Composable
fun <T> OneUiVisualChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    art: (T) -> VisualArt,
    tag: String,
    modifier: Modifier = Modifier,
    id: (T) -> String = { visualId(it) },
    dark: Boolean? = null,
) {
    VisualCardGroup(modifier) {
        options.forEach { o ->
            OneUiVisualCard(label(o), o == selected, "$tag.${id(o)}", { onSelect(o) }, dark = dark) { ink -> VisualCanvas(ink, art(o)) }
        }
    }
}

/** The same row for options whose picture is a composable (a font sample): [width] widens the cards. */
@Composable
fun <T> OneUiComposedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    tag: String,
    modifier: Modifier = Modifier,
    width: Dp = VisualCards.CardWidth,
    id: (T) -> String = { visualId(it) },
    art: @Composable BoxScope.(T, VisualInk) -> Unit,
) {
    VisualCardGroup(modifier) {
        options.forEach { o ->
            OneUiVisualCard(label(o), o == selected, "$tag.${id(o)}", { onSelect(o) }, width = width) { ink -> art(o, ink) }
        }
    }
}

/**
 * A choice row of the settings list that shows its options as cards inline (no page, no chevron). The value text of the row, its hint and its
 * title stay what the Settings search and the screen reader read; only the way the options are offered changed.
 */
fun <T> visualChoiceItem(
    title: String,
    glyph: androidx.compose.ui.graphics.vector.ImageVector,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    art: (T) -> VisualArt,
    tag: String,
    hint: String? = null,
    id: (T) -> String = { visualId(it) },
    onSelect: (T) -> Unit,
): SettingItem.Choice<T> = SettingItem.Choice(
    title, glyph, options, selected, label, hint = hint,
    preview = { OneUiVisualChoice(options, selected, onSelect, label, art, tag, id = id) },
    onSelect = onSelect,
)

/** A choice inside a card of rows (the dock pages): the title, then the cards under it, the way [OneUiSegmentedRow] put its control. */
@Composable
fun <T> OneUiVisualChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    art: (T) -> VisualArt,
    tag: String,
    summary: String? = null,
    id: (T) -> String = { visualId(it) },
) {
    OneUiRowFrame(
        title, summary = summary, role = null,
        below = { Box(Modifier.padding(top = 8.dp, bottom = 8.dp)) { OneUiVisualChoice(options, selected, onSelect, label, art, tag, id = id) } },
    )
}

/** This choice shown as inline cards: the row keeps its title, hint, value text and [SettingItem.Choice.onSelect], and gains the cards as its control. */
fun <T> SettingItem.Choice<T>.asCards(art: (T) -> VisualArt, tag: String, id: (T) -> String = { visualId(it) }): SettingItem.Choice<T> {
    val src = this
    return SettingItem.Choice(
        src.title, src.glyph, src.options, src.selected, src.label, hint = src.hint,
        preview = { OneUiVisualChoice(src.options, src.selected, src.onSelect, src.label, art, tag, id = id) },
        stepped = false, onSelect = src.onSelect,
    )
}
