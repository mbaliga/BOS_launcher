package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.CalmStyles
import com.bos.sphere.core.data.IndicatorColors
import com.bos.sphere.core.data.IndicatorMetric
import com.bos.sphere.core.data.WallpaperKind
import androidx.compose.ui.Alignment
import com.bos.sphere.core.data.IndicatorPreviewReading
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

const val INDICATOR_PREVIEW_TAG = "indicator-preview"

/**
 * Wave 35 (wave 55: the calm style and the regular indicators): the Indicator settings preview. A "simulated level" slider drives a small box with the same [IndicatorLayer] the home uses (the chosen calm style, mapping, colours, intensity and speed). It is local state only: nothing real changes, and reduced motion is respected by the layer itself.
 */
@Composable
fun IndicatorPreview(prefs: LauncherPrefs, modifier: Modifier = Modifier, initialLevel: Float = 1f) {
    var level by remember { mutableFloatStateOf(initialLevel) }
    val reading = IndicatorPreviewReading.of(prefs.indicatorMetric, level)
    val color = IndicatorColors.argb(reading, prefs.indicatorLow, prefs.indicatorMid, prefs.indicatorHigh, prefs.indicatorIdle, false)
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black).testTag(INDICATOR_PREVIEW_TAG)) {
            val style = CalmStyles.resolve(prefs.calmStyle, WallpaperKind.Live)
            IndicatorLayer(
                reading = reading, style = style, metric = prefs.indicatorMetric,
                colorArgb = color, intensity = prefs.indicatorIntensity, speed = prefs.indicatorSpeed,
                modifier = Modifier.fillMaxSize(), low = prefs.indicatorLow, mid = prefs.indicatorMid,
            )
            if (CalmStyles.showsRegular(style, prefs.calmAlsoRegular)) {
                val sim = IndicatorPreviewReading.of(prefs.indicatorMetric, level)
                val other = IndicatorPreviewReading.of(if (prefs.indicatorMetric == IndicatorMetric.Signal) IndicatorMetric.Battery else IndicatorMetric.Signal, level)
                val (sig, bat) = if (prefs.indicatorMetric == IndicatorMetric.Signal) sim to other else other to sim
                RegularIndicators(sig, bat, dark = true, modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp), low = prefs.indicatorLow)
            }
        }
        LabeledSlider(L10n.tr("Simulated level"), level, 0f..1f, "${(level * 100).toInt()}%") { level = it }
        Text(L10n.tr("Preview only: nothing on your phone changes"), color = HyleColors.InkDim, fontSize = 11.sp)
    }
}

private fun Modifier.fillMaxSize(): Modifier = this.fillMaxWidth().height(180.dp)
