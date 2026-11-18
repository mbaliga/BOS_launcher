package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.CommsScale
import com.bos.sphere.core.design.L10n
import kotlin.math.roundToInt

/**
 * Wave 28 (commsLayout): the Comms size control of the Comms menu's Options: a minus key, the percent, a plus key. Both keys are at least 48 dp of real
 * screen at any scale; Left and Right step one detent while it has focus; TalkBack reads and sets it as a progress range (75 to 125 in 5 steps).
 */
@Composable
internal fun CommsScaleStepper(percent: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val lp = LocalLp.current
    val p = CommsScale.snap(percent)
    val target = commsTarget()
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val label = L10n.tr("Comms size")
    Row(
        modifier.fillMaxWidth().heightIn(min = target)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) false
                else when (e.key) {
                    Key.DirectionLeft -> { onChange(CommsScale.step(p, -1)); true }
                    Key.DirectionRight -> { onChange(CommsScale.step(p, +1)); true }
                    else -> false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(2.dp, FocusRing, shape) else Modifier)
            .focusable()
            .semantics {
                contentDescription = label
                stateDescription = L10n.tr("%d percent", p)
                progressBarRangeInfo = ProgressBarRangeInfo(p.toFloat(), CommsScale.MIN.toFloat()..CommsScale.MAX.toFloat(), CommsScale.steps.size - 2)
                setProgress { v -> onChange(CommsScale.snap(v.roundToInt())); true }
            }
            .testTag("lp.menu.scale"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScaleKey("−", p > CommsScale.MIN, L10n.tr("Smaller Comms size"), "lp.menu.scale.down") { onChange(CommsScale.step(p, -1)) }
        Text(
            "$p%", color = lp.ink, fontSize = CommsStyle.SHOW_SP.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f).testTag("lp.menu.scale.value"),
        )
        ScaleKey("+", p < CommsScale.MAX, L10n.tr("Bigger Comms size"), "lp.menu.scale.up") { onChange(CommsScale.step(p, +1)) }
    }
}

@Composable
private fun ScaleKey(glyph: String, enabled: Boolean, description: String, tag: String, onClick: () -> Unit) {
    val lp = LocalLp.current
    val target = commsTarget()
    Box(
        Modifier.heightIn(min = target).widthIn(min = target).clip(RoundedCornerShape(8.dp)).background(lp.ink.copy(alpha = 0.07f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { role = Role.Button; contentDescription = description }.testTag(tag),
        Alignment.Center,
    ) { Text(glyph, color = if (enabled) lp.ink else lp.dim.copy(alpha = 0.4f), fontSize = 20.sp, fontWeight = FontWeight.Medium) }
}
