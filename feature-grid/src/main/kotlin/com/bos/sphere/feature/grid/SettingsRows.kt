package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import kotlin.math.floor
import kotlin.math.roundToInt

/** Pure maths behind the inline settings controls (kept separate so it is unit-tested). */
object SettingsMath {
    /** Index reached by moving [delta] from [index] in a list of [count]; wraps round when [wrap], else clamps. */
    fun stepIndex(count: Int, index: Int, delta: Int, wrap: Boolean = true): Int {
        if (count <= 0) return 0
        val cur = index.coerceIn(0, count - 1)
        return if (wrap) ((cur + delta) % count + count) % count else (cur + delta).coerceIn(0, count - 1)
    }

    /** The default snap step for a slider range: 1% for fractions, whole numbers for wide ranges. */
    fun autoStep(range: ClosedFloatingPointRange<Float>): Float = if (range.endInclusive - range.start <= 2f) 0.01f else 1f

    /** [value] snapped to the nearest multiple of [step] from the range start, clamped into [range]. */
    fun snap(value: Float, range: ClosedFloatingPointRange<Float>, step: Float = autoStep(range)): Float {
        val v = value.coerceIn(range.start, range.endInclusive)
        if (step <= 0f) return v
        val n = ((v - range.start) / step).roundToInt()
        return (range.start + n * step).coerceIn(range.start, range.endInclusive)
    }

    /** Which of [ticks] equal bands of the range [value] sits in — a haptic tick fires when this changes. */
    fun tickBand(value: Float, range: ClosedFloatingPointRange<Float>, ticks: Int = 20): Int {
        val span = range.endInclusive - range.start
        if (span <= 0f) return 0
        return floor((value - range.start) / span * ticks).toInt().coerceIn(0, ticks)
    }
}

internal val RowShape = RoundedCornerShape(16.dp)

/** Shared frame of every settings row: glyph, title (+ hint), a trailing control, and optional content below. */
@Composable
internal fun SettingRowFrame(
    glyph: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
    hintExtra: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(HyleColors.Surface.copy(alpha = if (highlighted) 0.9f else 0.62f), RowShape)
            .border(1.dp, if (highlighted) HyleColors.Violet.copy(alpha = 0.6f) else HyleColors.Hairline, RowShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 42.dp)) {
            Box(
                Modifier.size(34.dp).background(HyleColors.Violet.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(glyph, null, tint = HyleColors.InkBright, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                Text(title, color = HyleColors.InkBright, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!hint.isNullOrEmpty()) Text(hint, color = HyleColors.InkDim, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                hintExtra?.invoke()
            }
            trailing?.invoke()
        }
        below?.invoke()
    }
}

@Composable
internal fun ChevronRight() {
    Icon(Glyphs.Down, null, tint = HyleColors.InkDim, modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = -90f })
}

@Composable
internal fun ToggleSettingRow(item: SettingItem.Toggle, onToggle: (Boolean) -> Unit) {
    SettingRowFrame(
        item.glyph, item.title, hint = item.hint,
        onClick = { onToggle(!item.checked) },
        modifier = Modifier.semantics(mergeDescendants = true) {
            role = Role.Switch
            contentDescription = "${item.title}, ${item.value}"
            stateDescription = item.value
        },
        trailing = {
            Switch(
                checked = item.checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = HyleColors.OnAccent,
                    checkedTrackColor = HyleColors.Violet,
                    uncheckedThumbColor = HyleColors.Ink,
                    uncheckedTrackColor = HyleColors.Background.copy(alpha = 0.6f),
                    uncheckedBorderColor = HyleColors.Hairline,
                ),
            )
        },
    )
}

@Composable
internal fun SliderSettingRow(item: SettingItem.Slider, onTick: () -> Unit) {
    val step = if (item.step > 0f) item.step else SettingsMath.autoStep(item.range)
    var band by remember { mutableIntStateOf(SettingsMath.tickBand(item.amount, item.range)) }
    SettingRowFrame(
        item.glyph, item.title, hint = item.hint,
        trailing = { Text(item.valueText, color = HyleColors.InkBright, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
        below = {
            Slider(
                value = SettingsMath.snap(item.amount, item.range, step),
                onValueChange = { raw ->
                    val v = SettingsMath.snap(raw, item.range, step)
                    val b = SettingsMath.tickBand(v, item.range)
                    if (b != band) { band = b; onTick() }
                    if (v != item.amount) item.onChange(v)
                },
                valueRange = item.range,
                colors = SliderDefaults.colors(
                    thumbColor = HyleColors.Violet,
                    activeTrackColor = HyleColors.Violet,
                    inactiveTrackColor = HyleColors.Hairline,
                ),
                modifier = Modifier.padding(start = 46.dp).semantics { contentDescription = "${item.title}, ${item.valueText}" },
            )
        },
    )
}

@Composable
internal fun StepperSettingRow(item: SettingItem.Stepper, onTick: () -> Unit) {
    SettingRowFrame(
        item.glyph, item.title, hint = item.hint,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundStep("−", item.count > item.range.first, "Decrease ${item.title}") { onTick(); item.onChange(item.count - 1) }
                Text(
                    item.value.toString(), color = HyleColors.InkBright, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.widthIn(min = 34.dp).padding(horizontal = 6.dp).semantics { contentDescription = "${item.title} ${item.count}" },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                RoundStep("+", item.count < item.range.last, "Increase ${item.title}") { onTick(); item.onChange(item.count + 1) }
            }
        },
    )
}

@Composable
private fun RoundStep(text: String, enabled: Boolean, description: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .background(HyleColors.Background.copy(alpha = 0.5f), CircleShape)
            .border(1.dp, HyleColors.Hairline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
    ) { Text(text, color = if (enabled) HyleColors.InkBright else HyleColors.InkDim.copy(alpha = 0.4f), fontSize = 18.sp) }
}

/** Up to this many options step in place with the ⌃⌄ arrows; more open a dropdown menu. */
internal const val INLINE_CHOICE_MAX = 6

@Composable
internal fun <T> ChoiceSettingRow(item: SettingItem.Choice<T>, onTick: () -> Unit, onPreview: () -> Unit) {
    val index = item.options.indexOf(item.selected).coerceAtLeast(0)
    val dropdown = item.options.size > INLINE_CHOICE_MAX
    var menu by remember { mutableStateOf(false) }
    fun step(delta: Int) {
        val n = SettingsMath.stepIndex(item.options.size, index, delta)
        if (item.options.isNotEmpty() && n != index) { onTick(); item.onSelect(item.options[n]) }
    }
    SettingRowFrame(
        item.glyph, item.title, hint = item.hint,
        onClick = { if (dropdown) menu = true else step(1) },
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${item.title}, ${item.value}" },
        hintExtra = if (item.preview != null) ({
            Text(
                com.bos.sphere.core.design.L10n.tr("Preview…"), color = HyleColors.VioletSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onPreview).padding(top = 3.dp, bottom = 1.dp),
            )
        }) else null,
        trailing = {
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(HyleColors.Background.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .border(1.dp, HyleColors.Hairline, RoundedCornerShape(12.dp))
                        .padding(start = 12.dp),
                ) {
                    Text(
                        item.value, color = HyleColors.InkBright, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 130.dp),
                    )
                    if (dropdown) {
                        Icon(Glyphs.Down, null, tint = HyleColors.Ink, modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp).size(22.dp))
                    } else {
                        Column {
                            Icon(
                                Glyphs.Up, "Previous ${item.title}", tint = HyleColors.Ink,
                                modifier = Modifier.clickable { step(-1) }.padding(horizontal = 8.dp, vertical = 3.dp).size(width = 22.dp, height = 18.dp),
                            )
                            Icon(
                                Glyphs.Down, "Next ${item.title}", tint = HyleColors.Ink,
                                modifier = Modifier.clickable { step(1) }.padding(horizontal = 8.dp, vertical = 3.dp).size(width = 22.dp, height = 18.dp),
                            )
                        }
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    item.options.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(item.label(opt), color = if (opt == item.selected) HyleColors.Violet else HyleColors.InkBright) },
                            onClick = { menu = false; if (opt != item.selected) { onTick(); item.onSelect(opt) } },
                        )
                    }
                }
            }
        },
    )
}

@Composable
internal fun NavigationSettingRow(item: SettingItem, chevron: Boolean, highlighted: Boolean, onClick: () -> Unit, hint: String? = null) {
    val sub = listOfNotNull(item.value.takeIf { it.isNotEmpty() }, hint?.takeIf { it.isNotEmpty() }).joinToString(" · ")
    SettingRowFrame(
        item.glyph, item.title, hint = sub, highlighted = highlighted, onClick = onClick,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${item.title}, ${item.value}" },
        trailing = if (chevron) ({ ChevronRight() }) else null,
    )
}
