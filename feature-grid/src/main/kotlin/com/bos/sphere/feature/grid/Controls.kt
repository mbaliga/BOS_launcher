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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.text.style.TextOverflow
import com.bos.sphere.core.design.L10n
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
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

/** True inside the footer of a [GlassDialog]: there a [TextButtonPill] is drawn as One UI's flat accent text button instead of a pill (wave 41). */
internal val LocalDialogFooter = androidx.compose.runtime.compositionLocalOf { false }

@Composable
fun TextButtonPill(text: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    if (LocalDialogFooter.current) {
        Text(
            text, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = when { !enabled -> HyleColors.InkDim.copy(alpha = 0.5f); primary -> HyleColors.Violet; else -> HyleColors.Violet },
            modifier = Modifier.heightIn(min = 56.dp).clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        )
    } else TactilePill(text, primary, enabled, onClick)
}

/**
 * A full-screen editor in One UI's page chrome (waves 41 and 48): the standard page (back arrow, the title large and centred in the empty
 * band under the bar, collapsing into the bar on scroll), the [content] in one rounded-free column, and a footer of flat accent text
 * buttons (Save, Cancel) under a hairline that stays visible while the content scrolls. Used for the editors that hold more than a
 * choice (the corner and gesture editors, the drawer colour); a choice from a list is a radio or check page, a short question a
 * [OneUiDialog]. The page is its own window ([OneUiFullPage]) so the host's dialog hook (the dock's controller layer) applies.
 */
@Composable
fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    width: Dp = 560.dp,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    OneUiFullPage(onDismiss) {
        Column(Modifier.fillMaxSize().semantics { paneTitle = title }.imePadding()) {
            OneUiPageFrame(title, onDismiss, modifier = Modifier.weight(1f), tag = OneUiDialogTags.EDITOR_PAGE) {
                item(key = "editor") {
                    Column(Modifier.fillMaxWidth().widthIn(max = width).padding(horizontal = 18.dp, vertical = 4.dp), content = content)
                }
            }
            if (footer != null) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(HyleColors.Hairline))
                androidx.compose.runtime.CompositionLocalProvider(LocalDialogFooter provides true) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().background(OneUi.Page).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal + androidx.compose.foundation.layout.WindowInsetsSides.Bottom)).padding(horizontal = 10.dp),
                    ) { footer() }
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
    destructive: Boolean = false,
) = OneUiConfirmDialog(title, text, confirm, onConfirm, onDismiss, cancelLabel = dismiss, destructive = destructive)
