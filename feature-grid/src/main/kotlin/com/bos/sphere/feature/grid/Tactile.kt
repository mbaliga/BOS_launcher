package com.bos.sphere.feature.grid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import kotlin.math.roundToInt

/*
 * A Compose port of the Hyle tactile kit's physical controls (groove, machined knob, car-button
 * segments, pill-thumb fader): recessed dark grooves, lit rims, one violet accent.
 */

internal val Groove: Color get() = TactileTokens.groove
internal val GrooveSel: Color get() = TactileTokens.grooveSel
private val RimLight: Color get() = TactileTokens.rimLight
private val RimDark: Color get() = TactileTokens.rimDark

/** The kit's toggle: a recessed groove with a machined knob that slides and lights the accent. */
@Composable
internal fun TactileToggle(checked: Boolean, modifier: Modifier = Modifier) {
    val knobX by animateDpAsState(if (checked) 26.dp else 3.dp, tween(160), label = "knob")
    val knobTop by animateColorAsState(if (checked) Color.White else Color(0xFFC9C9D4), tween(160), label = "knobc")
    Box(modifier.size(width = 54.dp, height = 30.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val r = CornerRadius(size.height / 2f)
            drawRoundRect(Groove, cornerRadius = r)
            if (checked) drawRoundRect(HyleColors.Violet.copy(alpha = 0.30f), cornerRadius = r)
            // inset shadow along the top and a lit lip along the bottom
            drawRoundRect(Brush.verticalGradient(0f to RimDark, 0.45f to Color.Transparent), cornerRadius = r)
            drawRoundRect(Brush.verticalGradient(0.7f to Color.Transparent, 1f to RimLight), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
        }
        Box(
            Modifier
                .offset(x = knobX, y = 3.dp)
                .size(24.dp)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(knobTop, if (checked) HyleColors.VioletSoft else Color(0xFF6E6E7C))))
                .border(1.dp, Color(0x55FFFFFF), CircleShape),
        )
    }
}

/** The kit's fader: a thin groove, accent fill, and a pill thumb; drag anywhere to set. */
@Composable
internal fun TactileSlider(
    fraction: Float,
    onFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    defaultFraction: Float? = null,
) {
    val density = LocalDensity.current
    val thumbW = with(density) { 11.dp.toPx() }
    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(Unit) { detectTapGestures { p -> onFraction(((p.x - thumbW / 2) / (size.width - thumbW)).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onFraction(((change.position.x - thumbW / 2) / (size.width - thumbW)).coerceIn(0f, 1f))
                }
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val cy = size.height / 2f
            val tw = size.width - thumbW
            val x = thumbW / 2 + tw * fraction.coerceIn(0f, 1f)
            val h = 3.dp.toPx()
            drawRoundRect(TactileTokens.track, Offset(thumbW / 2, cy - h / 2), Size(tw, h), CornerRadius(h))
            drawRoundRect(Color(0x55000000), Offset(thumbW / 2, cy - h / 2), Size(tw, h / 2), CornerRadius(h))
            drawRoundRect(HyleColors.Violet, Offset(thumbW / 2, cy - h / 2), Size(x - thumbW / 2, h), CornerRadius(h))
            if (defaultFraction != null) {
                val dx = thumbW / 2 + tw * defaultFraction
                drawRect(Color(0xFFB9B2D8), Offset(dx - 1.dp.toPx(), cy - 7.5.dp.toPx()), Size(2.dp.toPx(), 15.dp.toPx()))
            }
            // the pill thumb, lit from above
            val th = 26.dp.toPx()
            drawRoundRect(Brush.verticalGradient(listOf(HyleColors.VioletSoft, HyleColors.Violet), cy - th / 2, cy + th / 2), Offset(x - thumbW / 2, cy - th / 2), Size(thumbW, th), CornerRadius(6.dp.toPx()))
            drawRoundRect(Color(0x66FFFFFF), Offset(x - thumbW / 2 + 1.5.dp.toPx(), cy - th / 2 + 1.5.dp.toPx()), Size(thumbW - 3.dp.toPx(), 3.dp.toPx()), CornerRadius(2.dp.toPx()))
        }
    }
}

/**
 * The kit's "car-button" select: one recessed slab split into physical segments by chrome
 * dividers; the chosen label is bright, the rest dim. The current value is repeated underneath.
 */
@Composable
internal fun SegmentSlab(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column2(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .background(Groove)
                .border(1.dp, Color(0x14FFFFFF), shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { i, label ->
                if (i > 0) Box(
                    Modifier.width(3.dp).fillMaxHeight(0.62f).clip(RoundedCornerShape(2.dp))
                        .background(TactileTokens.chrome),
                )
                val sel = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (sel) GrooveSel else Color.Transparent)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onSelect(i) }
                        .semantics { role = Role.RadioButton; contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label.uppercase(), color = if (sel) TactileTokens.onSelected else HyleColors.InkDim,
                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                        maxLines = 1, softWrap = false, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }
        Text(labels.getOrNull(selected).orEmpty(), color = HyleColors.InkBright, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

@Composable
private fun Column2(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) =
    androidx.compose.foundation.layout.Column(modifier, content = content)

/** Uppercase section title in the kit's label style. */
@Composable
internal fun TactileLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = HyleColors.InkBright, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = modifier)
}
