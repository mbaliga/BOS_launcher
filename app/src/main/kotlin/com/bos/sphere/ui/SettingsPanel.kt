package com.bos.sphere.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.feature.sphere.RowMode
import com.bos.sphere.feature.sphere.SphereParams
import kotlin.math.roundToInt

/**
 * The Surface settings panel — the prototype's slider column wired to live [SphereParams]. Tuning
 * any control updates the equator immediately (the params are hoisted in the home root).
 */
@Composable
fun SettingsPanel(
    visible: Boolean,
    params: SphereParams,
    rowMode: RowMode,
    tilesMode: Boolean = false,
    rubikMode: Boolean = false,
    onParamsChange: (SphereParams) -> Unit,
    onRowModeChange: (RowMode) -> Unit,
    onTilesModeChange: (Boolean) -> Unit = {},
    onRubikModeChange: (Boolean) -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally { it },
        exit = slideOutHorizontally { it },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .safeContentPadding()
                .padding(12.dp)
                .width(260.dp)
                .background(HyleColors.SurfaceTranslucent, RoundedCornerShape(14.dp))
                .border(1.dp, HyleColors.Hairline, RoundedCornerShape(14.dp))
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Header("SURFACE")
                Text("Done", color = HyleColors.Violet, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onClose))
            }

            Toggle("Tiles", tilesMode, onTilesModeChange)
            Toggle("Per-band spin", rubikMode, onRubikModeChange)

            ParamSlider("Perspective", params.perspective, 600f, 2400f) { onParamsChange(params.copy(perspective = it)) }
            ParamSlider("Spacing", params.radius, 380f, 980f) { onParamsChange(params.copy(radius = it)) }
            ParamSlider("Turn", params.maxRotation, 0f, 80f) { onParamsChange(params.copy(maxRotation = it)) }
            ParamSlider("Depth", params.depth, 0f, 1.6f, decimals = 2) { onParamsChange(params.copy(depth = it)) }
            ParamSlider("Min scale", params.minScale, 0.4f, 1f, decimals = 2) { onParamsChange(params.copy(minScale = it)) }
            ParamSlider("Focus", params.focus, 1f, 2f, decimals = 2) { onParamsChange(params.copy(focus = it)) }
            ParamSlider("Arc", params.arc, 0f, 160f) { onParamsChange(params.copy(arc = it)) }
            ParamSlider("Tilt", params.tilt, 0f, 24f) { onParamsChange(params.copy(tilt = it)) }

            Header("ROWS")
            RowModeSelector(rowMode, onRowModeChange)
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        color = HyleColors.InkDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
    )
}

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    decimals: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = HyleColors.Ink, fontSize = 11.5.sp)
            Text(format(value, decimals), color = HyleColors.InkBright, fontSize = 11.5.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = min..max,
            colors = SliderDefaults.colors(
                thumbColor = HyleColors.Violet,
                activeTrackColor = HyleColors.Violet,
                inactiveTrackColor = HyleColors.Hairline,
            ),
        )
    }
}

@Composable
private fun Toggle(label: String, isOn: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!isOn) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = HyleColors.Ink, fontSize = 11.5.sp)
        Box(
            modifier = Modifier
                .background(
                    if (isOn) HyleColors.Violet else HyleColors.Surface.copy(alpha = 0.5f),
                    RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                if (isOn) "ON" else "OFF",
                color = if (isOn) HyleColors.Background else HyleColors.InkDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun RowModeSelector(selected: RowMode, onSelect: (RowMode) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RowMode.entries.forEach { mode ->
            val isSel = mode == selected
            Text(
                text = mode.name,
                color = if (isSel) HyleColors.Background else HyleColors.Ink,
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable { onSelect(mode) }
                    .background(
                        if (isSel) HyleColors.Violet else HyleColors.Surface.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private fun format(value: Float, decimals: Int): String =
    if (decimals == 0) value.roundToInt().toString()
    else "%.${decimals}f".format(value)
