package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.HyleTheme

/**
 * CFG-16: the rest of the tactile kit, drawn from the same recessed-groove language as the toggle, slider and
 * segment slab: a stepper made of two machined keys on a groove, a section header with a lit lip, and groove chips.
 * Colours come from [TactileTokens] so every control reads in light and dark.
 */
object TactileTokens {
    private val light get() = HyleTheme.isLight
    val groove: Color get() = if (light) Color(0xFFD9DBE4) else Color(0xFF15131D)
    val grooveSel: Color get() = if (light) Color(0xFFC3C7D6) else Color(0xFF241F37)
    val rimLight: Color get() = if (light) Color(0xCCFFFFFF) else Color(0x33FFFFFF)
    val rimDark: Color get() = if (light) Color(0x33000000) else Color(0xCC000000)
    val track: Color get() = if (light) Color(0xFFB4B8C8) else Color(0xFF3A3550)
    val keyTop: Color get() = if (light) Color(0xFFFFFFFF) else Color(0xFF3A3750)
    val keyBottom: Color get() = if (light) Color(0xFFD5D8E2) else Color(0xFF211F33)
    val chrome: Brush get() = Brush.horizontalGradient(
        if (light) listOf(Color(0xFFAEB2C2), Color(0xFFFFFFFF), Color(0xFFAEB2C2)) else listOf(Color(0xFF55555F), Color(0xFFD8D8E0), Color(0xFF55555F)),
    )
    /** Text on the selected segment. */
    val onSelected: Color get() = if (light) Color(0xFF14171F) else Color.White
}

/** A stepper: groove slab with a key on each side and the value engraved between. */
@Composable
internal fun TactileStepper(
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(TactileTokens.groove)
            .border(1.dp, TactileTokens.rimLight.copy(alpha = 0.25f), shape)
            .semantics { contentDescription = "$label $value"; stateDescription = value },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepKey("−", canDecrease, "Decrease $label", onDecrease)
        Box(Modifier.width(3.dp).fillMaxHeight(0.6f).clip(RoundedCornerShape(2.dp)).background(TactileTokens.chrome))
        Text(
            value, color = HyleColors.InkBright, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 44.dp).padding(horizontal = 6.dp),
        )
        Box(Modifier.width(3.dp).fillMaxHeight(0.6f).clip(RoundedCornerShape(2.dp)).background(TactileTokens.chrome))
        StepKey("+", canIncrease, "Increase $label", onIncrease)
    }
}

@Composable
private fun StepKey(glyph: String, enabled: Boolean, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .padding(4.dp)
            .shadow(if (enabled) 2.dp else 0.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(TactileTokens.keyTop, TactileTokens.keyBottom)))
            .clickable(enabled = enabled, indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .semantics { role = Role.Button; contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = if (enabled) HyleColors.InkBright else HyleColors.InkDim.copy(alpha = 0.4f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** A section header: engraved capitals over a recessed rule with a lit lip. */
@Composable
internal fun TactileSectionHeader(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp)) {
        Text(text.uppercase(), color = HyleColors.InkDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(TactileTokens.groove))
        Box(Modifier.fillMaxWidth().height(1.dp).background(TactileTokens.rimLight.copy(alpha = 0.35f)))
    }
}

/** Wrapping choice chips cut as small grooves; the chosen one is lit with the accent. */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
internal fun <T> TactileChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        options.forEach { opt ->
            val sel = opt == selected
            val shape = RoundedCornerShape(12.dp)
            Text(
                label(opt), maxLines = 1, softWrap = false, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (sel) HyleColors.OnAccent else HyleColors.Ink,
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(shape)
                    .background(if (sel) HyleColors.Violet else TactileTokens.groove)
                    .border(1.dp, if (sel) HyleColors.VioletSoft else TactileTokens.rimLight.copy(alpha = 0.25f), shape)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onSelect(opt) }
                    .semantics { role = Role.RadioButton; stateDescription = if (sel) "Selected" else "" }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/** A machined pill button on the same groove: primary is lit with the accent, secondary sits in the groove. */
@Composable
internal fun TactilePill(text: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        color = when { !enabled -> HyleColors.InkDim; primary -> HyleColors.OnAccent; else -> HyleColors.InkBright },
        modifier = modifier
            .heightIn(min = 40.dp)
            .shadow(if (enabled) 2.dp else 0.dp, shape)
            .clip(shape)
            .background(if (primary && enabled) Brush.verticalGradient(listOf(HyleColors.VioletSoft, HyleColors.Violet)) else Brush.verticalGradient(listOf(TactileTokens.keyTop, TactileTokens.keyBottom)))
            .border(1.dp, TactileTokens.rimLight.copy(alpha = 0.3f), shape)
            .clickable(enabled = enabled, indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}
