package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.BackgroundKind
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.BackgroundLayer
import com.bos.sphere.feature.grid.BackgroundPresets
import com.bos.sphere.feature.grid.HomeStylePreview
import com.bos.sphere.feature.grid.TextButtonPill
import com.bos.sphere.feature.grid.VisualChoice

/** Three short, skippable cards: how it looks, what's behind it, make it home. */
@Composable
fun Onboarding(
    prefs: LauncherPrefs,
    update: ((LauncherPrefs) -> LauncherPrefs) -> Unit,
    setDefaultHome: () -> Unit,
    onDone: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(HyleColors.Background.copy(alpha = 0.97f)).pointerInput(Unit) { detectTapGestures { } }) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { i ->
                    Box(Modifier.padding(end = 6.dp).height(6.dp).weight(1f).background(if (i <= step) HyleColors.Violet else HyleColors.Hairline, CircleShape))
                }
                Text(L10n.tr("Skip"), color = HyleColors.InkDim, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp).pointerInput(Unit) { detectTapGestures { onDone() } }.padding(8.dp))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 20.dp)) {
                when (step) {
                    0 -> {
                        Title("Pick a look")
                        VisualChoice(HomeStyle.entries, prefs.homeStyle, { s -> update { it.copy(homeStyle = s) } }, {
                            L10n.tr(when (it) { HomeStyle.Grid -> "Pages"; HomeStyle.Sphere -> "Sphere"; HomeStyle.Bubbles -> "Bubbles" })
                        }, previewHeight = 140.dp) { HomeStylePreview(it) }
                    }
                    1 -> {
                        Title("Pick a backdrop")
                        val picks = listOf(
                            BackgroundKind.Black to "",
                            BackgroundKind.Gradient to "dusk",
                            BackgroundKind.Gradient to "aurora",
                            BackgroundKind.Live to "aurora",
                            BackgroundKind.Live to "starfield",
                            BackgroundKind.Texture to "grain",
                        )
                        VisualChoice(picks, picks.firstOrNull { it.first == prefs.backgroundKind && it.second == prefs.backgroundPreset } ?: picks[0], { (k, id) ->
                            update { it.copy(backgroundKind = k, backgroundPreset = id.ifEmpty { BackgroundPresets.default(k) }) }
                        }, { L10n.tr(if (it.second.isEmpty()) "Black" else it.second.replaceFirstChar { c -> c.uppercase() }) }, previewHeight = 120.dp) { (k, id) ->
                            BackgroundLayer(k, id.ifEmpty { BackgroundPresets.default(k) }, prefs.backgroundColorA, prefs.backgroundColorB, Modifier.fillMaxSize(), animate = false)
                        }
                        Text(L10n.tr("More in Settings → Theme → Background."), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    else -> {
                        Title("Make it home")
                        Text(L10n.tr("Press and hold anywhere to change things. That's all you need."), color = HyleColors.Ink, fontSize = 14.sp, modifier = Modifier.padding(bottom = 18.dp))
                        TextButtonPill(L10n.tr("Set as default"), primary = true, onClick = setDefaultHome)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButtonPill(if (step < 2) L10n.tr("Next") else L10n.tr("Done"), primary = true) { if (step < 2) step++ else onDone() }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(L10n.tr(text), color = HyleColors.InkBright, fontSize = 26.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(bottom = 16.dp))
}
