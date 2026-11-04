package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bos.sphere.core.design.HyleColors

/* Small Hyle-styled controls shared by the editors and the settings panel. */

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = HyleColors.InkDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, hint: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                stateDescription = if (checked) "On" else "Off"
            }
            .padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = HyleColors.Ink, fontSize = 13.sp)
            if (hint != null) Text(hint, color = HyleColors.InkDim, fontSize = 11.sp)
        }
        Box(
            Modifier
                .size(width = 36.dp, height = 20.dp)
                .background(if (checked) HyleColors.Violet else HyleColors.Surface, RoundedCornerShape(10.dp))
                .border(1.dp, HyleColors.Hairline, RoundedCornerShape(10.dp)),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.padding(3.dp).size(14.dp).background(HyleColors.InkBright, CircleShape))
        }
    }
}

@Composable
fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = HyleColors.Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
        StepButton("−", enabled = value > range.first, description = "Decrease $label") { onChange(value - 1) }
        Text(
            "$value",
            color = HyleColors.InkBright,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.widthIn(min = 30.dp).padding(horizontal = 8.dp).semantics { contentDescription = "$label $value" },
        )
        StepButton("+", enabled = value < range.last, description = "Increase $label") { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, description: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .background(HyleColors.Surface, CircleShape)
            .border(1.dp, HyleColors.Hairline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Text(text, color = if (enabled) HyleColors.InkBright else HyleColors.InkDim.copy(alpha = 0.4f), fontSize = 16.sp)
    }
}

@Composable
fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        options.forEach { opt ->
            val sel = opt == selected
            Text(
                label(opt),
                color = if (sel) HyleColors.Background else HyleColors.Ink,
                fontSize = 12.sp,
                modifier = Modifier
                    .background(if (sel) HyleColors.Violet else HyleColors.Surface, RoundedCornerShape(8.dp))
                    .border(1.dp, HyleColors.Hairline, RoundedCornerShape(8.dp))
                    .clickable { onSelect(opt) }
                    .semantics { role = Role.RadioButton; stateDescription = if (sel) "Selected" else "" }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = HyleColors.Ink, fontSize = 13.sp)
            Text(display, color = HyleColors.InkBright, fontSize = 12.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = HyleColors.Violet,
                activeTrackColor = HyleColors.Violet,
                inactiveTrackColor = HyleColors.Hairline,
            ),
        )
    }
}

/** Tappable row with an optional glyph and trailing summary. */
@Composable
fun ActionRow(
    label: String,
    summary: String? = null,
    glyph: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) HyleColors.Violet.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
    ) {
        when {
            leading != null -> Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { leading() }
            glyph != null -> Icon(glyph, null, tint = HyleColors.InkBright, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(start = if (leading != null || glyph != null) 12.dp else 0.dp)) {
            Text(label, color = HyleColors.InkBright, fontSize = 13.5.sp)
            if (summary != null) Text(summary, color = HyleColors.InkDim, fontSize = 11.5.sp, maxLines = 2)
        }
    }
}

@Composable
fun TextButtonPill(text: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        color = when {
            !enabled -> HyleColors.InkDim
            primary -> HyleColors.Background
            else -> HyleColors.InkBright
        },
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .background(if (primary && enabled) HyleColors.Violet else HyleColors.Surface, RoundedCornerShape(18.dp))
            .border(1.dp, HyleColors.Hairline, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

/** A glass dialog card with a title and a scrolling body. */
@Composable
fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    width: Dp = 360.dp,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.86f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(16.dp)
                .widthIn(max = width)
                .fillMaxWidth()
                .heightIn(max = maxH)
                .background(HyleColors.SurfaceTranslucent.copy(alpha = 0.97f), RoundedCornerShape(20.dp))
                .border(1.dp, HyleColors.Hairline, RoundedCornerShape(20.dp))
                .padding(18.dp),
        ) {
            Text(title, color = HyleColors.InkBright, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 6.dp), content = content)
            if (footer != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { footer() }
            }
        }
    }
}
