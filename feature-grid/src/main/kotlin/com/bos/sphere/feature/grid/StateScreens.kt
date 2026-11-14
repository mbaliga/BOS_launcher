package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.OnboardingFlow
import com.bos.sphere.core.data.OnboardingStep
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/**
 * CFG-06: the three first-run cards, drawn from plain values so a screenshot test can render each one. The host
 * ([com.bos.sphere.ui.Onboarding]) owns the step counter and the import flow.
 */
@Composable
fun OnboardingView(
    step: Int,
    prefs: LauncherPrefs,
    importMessage: String?,
    onStyle: (HomeStyle) -> Unit,
    onSetDefault: () -> Unit,
    onImport: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val steps = OnboardingFlow.steps
    val current = steps[step.coerceIn(0, steps.lastIndex)]
    Box(Modifier.fillMaxSize().background(HyleColors.Background.copy(alpha = 0.97f)).pointerInput(Unit) { detectTapGestures { } }) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                repeat(steps.size) { i ->
                    Box(Modifier.padding(end = 6.dp).height(6.dp).weight(1f).background(if (i <= step) HyleColors.Violet else HyleColors.Hairline, CircleShape))
                }
                Text(L10n.tr("Skip"), color = HyleColors.InkDim, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp).clickable(onClick = onSkip).padding(8.dp))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 20.dp)) {
                Text(
                    when (current) {
                        OnboardingStep.SetAsHome -> L10n.tr("Make it home")
                        OnboardingStep.HomeStyle -> L10n.tr("Pick a look")
                        OnboardingStep.Import -> L10n.tr("Bring your setup")
                    },
                    color = HyleColors.InkBright, fontSize = 26.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(bottom = 16.dp),
                )
                when (current) {
                    OnboardingStep.SetAsHome -> {
                        Text(L10n.tr("Liftoff works best as your home app. You can change this later in Android settings."), color = HyleColors.Ink, fontSize = 14.sp, modifier = Modifier.padding(bottom = 18.dp))
                        TextButtonPill(L10n.tr("Set as default"), primary = true, onClick = onSetDefault)
                    }
                    OnboardingStep.HomeStyle -> {
                        VisualChoice(HomeStyle.entries, prefs.homeStyle, onStyle, {
                            L10n.tr(when (it) { HomeStyle.Grid -> "Pages"; HomeStyle.List -> "List"; HomeStyle.Psp -> "PSP"; HomeStyle.Dsi -> "DSi" })
                        }, previewHeight = 140.dp) { HomeStylePreview(it) }
                        Text(L10n.tr("Press and hold anywhere to change things."), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    OnboardingStep.Import -> {
                        Text(L10n.tr("Have a Liftoff setup file from another phone? Bring it in. Apps that are not installed yet return to their place when they are."), color = HyleColors.Ink, fontSize = 14.sp, modifier = Modifier.padding(bottom = 18.dp))
                        TextButtonPill(L10n.tr("Import a setup file…"), primary = true, onClick = onImport)
                        importMessage?.let { Text(it, color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButtonPill(if (step < steps.lastIndex) L10n.tr("Next") else L10n.tr("Done"), primary = true, onClick = onNext)
            }
        }
    }
}

/** One app in the safe-mode grid. */
class SafeApp(val key: String, val label: String)

/**
 * CFG-09: the minimal surface after a crash loop: alphabetical app names, no widgets, no wallpaper layers, no Radar. The
 * host ([com.bos.sphere.ui.SafeModeScreen]) supplies the apps and what the buttons do.
 */
@Composable
fun SafeModeView(
    apps: List<SafeApp>,
    restoreLabel: String?,
    message: String?,
    onOpen: (SafeApp) -> Unit,
    onRestore: () -> Unit,
    onCarryOn: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(HyleColors.Background)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
            Text(L10n.tr("Safe mode"), color = HyleColors.InkBright, fontSize = 24.sp)
            Text(
                L10n.tr("Liftoff stopped twice in a row right after starting, so widgets and extras are off. Your apps still open from here."),
                color = HyleColors.Ink, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (restoreLabel != null) TextButtonPill(L10n.tr("Restore last snapshot (%s)", restoreLabel), primary = true, onClick = onRestore)
                TextButtonPill(L10n.tr("Carry on"), onClick = onCarryOn)
            }
            if (restoreLabel == null) {
                Text(L10n.tr("There is no snapshot to restore yet."), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            message?.let { Text(it, color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            LazyVerticalGrid(GridCells.Adaptive(88.dp), Modifier.fillMaxSize().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(apps.sortedBy { it.label.lowercase() }, key = { it.key }) { app ->
                    Text(
                        app.label, color = HyleColors.InkBright, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(app) }.padding(vertical = 14.dp, horizontal = 4.dp),
                    )
                }
            }
        }
    }
}
