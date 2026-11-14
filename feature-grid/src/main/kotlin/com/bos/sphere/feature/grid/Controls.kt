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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
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
fun SectionHeader(text: String, modifier: Modifier = Modifier) = TactileSectionHeader(text, modifier)

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
        TactileToggle(checked)
    }
}

@Composable
fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = HyleColors.Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
        TactileStepper("$value", value > range.first, value < range.last, { onChange(value - 1) }, { onChange(value + 1) }, label)
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) = TactileChipRow(options, selected, label, onSelect)

@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = HyleColors.Ink, fontSize = 13.sp)
            Text(display, color = HyleColors.InkBright, fontSize = 12.sp)
        }
        val span = (range.endInclusive - range.start).coerceAtLeast(1e-6f)
        TactileSlider(
            fraction = (value - range.start) / span,
            onFraction = { f -> onChange(range.start + f * span) },
            modifier = Modifier.semantics { contentDescription = "$label, $display" },
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
            .background(if (selected) HyleColors.Violet.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
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
fun TextButtonPill(text: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) = TactilePill(text, primary, enabled, onClick)

/**
 * A full-screen sheet in the settings space's look (gradient, waves, spaced-caps header) used for
 * every picker and editor, so nothing floats over the UI as a Material dialog.
 */
@Composable
fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    width: Dp = 560.dp,
    footer: (@Composable () -> Unit)? = null,
    /** False when [content] manages its own scrolling (a LazyColumn that fills the space). */
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DialogControllerKeys()
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF07080D), androidx.compose.ui.graphics.Color(0xFF0E0C22), androidx.compose.ui.graphics.Color(0xFF071622)))),
            ) {
                WaveField(Modifier.fillMaxSize())
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing)
                        .imePadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp).fillMaxWidth(),
                    ) {
                        Text("LIFTOFF", color = HyleColors.InkBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp)
                        Text("  ·  ${title.uppercase()}", color = HyleColors.InkDim, fontSize = 12.sp, letterSpacing = 4.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        Icon(
                            Glyphs.Close,
                            contentDescription = "Close",
                            tint = HyleColors.Ink,
                            modifier = Modifier.size(40.dp).clickable(onClick = onDismiss).padding(10.dp),
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .widthIn(max = width)
                            .fillMaxWidth()
                            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                        content = content,
                    )
                    if (footer != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            modifier = Modifier.widthIn(max = width).fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        ) { footer() }
                    }
                }
            }
        }
    }
}

/** A small centred yes/no card in the same visual language (used for short questions). */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        DialogControllerKeys()
        Column(
            Modifier
                .background(HyleColors.SurfaceTranslucent.copy(alpha = 0.98f), RoundedCornerShape(20.dp))
                .border(1.dp, HyleColors.Hairline, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Text(title, color = HyleColors.InkBright, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(text, color = HyleColors.Ink, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                TextButtonPill(dismiss, onClick = onDismiss)
                TextButtonPill(confirm, primary = true, onClick = onConfirm)
            }
        }
    }
}
