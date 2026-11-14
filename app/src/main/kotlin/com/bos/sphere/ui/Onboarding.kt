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
import com.bos.sphere.core.data.OnboardingFlow
import com.bos.sphere.core.data.OnboardingStep
import com.bos.sphere.core.data.StateFormatException
import com.bos.sphere.core.data.StateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateOf
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.HomeStylePreview
import com.bos.sphere.feature.grid.TextButtonPill
import com.bos.sphere.feature.grid.VisualChoice

/**
 * CFG-06: three short, skippable cards in the order of [OnboardingFlow]: make it home, pick a look, optionally import a
 * setup file. Nothing else is asked up front.
 */
@Composable
fun Onboarding(
    prefs: LauncherPrefs,
    update: ((LauncherPrefs) -> LauncherPrefs) -> Unit,
    setDefaultHome: () -> Unit,
    onDone: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    val steps = OnboardingFlow.steps
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var importMessage by remember { mutableStateOf<String?>(null) }
    val importPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw StateFormatException("Could not open the file")
                    if (bytes.size > 8 * 1024 * 1024) throw StateFormatException("The file is too large to be a Liftoff setup")
                    StateService.get(context).import(bytes.toString(Charsets.UTF_8), emptySet())
                }
            }.onSuccess { onDone() }
                .onFailure { importMessage = L10n.tr("That file could not be imported: %s", it.message ?: "") }
        }
    }
    Box(Modifier.fillMaxSize().background(HyleColors.Background.copy(alpha = 0.97f)).pointerInput(Unit) { detectTapGestures { } }) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                repeat(steps.size) { i ->
                    Box(Modifier.padding(end = 6.dp).height(6.dp).weight(1f).background(if (i <= step) HyleColors.Violet else HyleColors.Hairline, CircleShape))
                }
                Text(L10n.tr("Skip"), color = HyleColors.InkDim, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp).pointerInput(Unit) { detectTapGestures { onDone() } }.padding(8.dp))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 20.dp)) {
                Title(steps[step].title)
                when (steps[step]) {
                    OnboardingStep.SetAsHome -> {
                        Text(L10n.tr("Liftoff works best as your home app. You can change this later in Android settings."), color = HyleColors.Ink, fontSize = 14.sp, modifier = Modifier.padding(bottom = 18.dp))
                        TextButtonPill(L10n.tr("Set as default"), primary = true, onClick = setDefaultHome)
                    }
                    OnboardingStep.HomeStyle -> {
                        VisualChoice(HomeStyle.entries, prefs.homeStyle, { s -> update { it.copy(homeStyle = s) } }, {
                            L10n.tr(when (it) { HomeStyle.Grid -> "Pages"; HomeStyle.List -> "List"; HomeStyle.Psp -> "PSP"; HomeStyle.Dsi -> "DSi" })
                        }, previewHeight = 140.dp) { HomeStylePreview(it) }
                        Text(L10n.tr("Press and hold anywhere to change things."), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    OnboardingStep.Import -> {
                        Text(L10n.tr("Have a Liftoff setup file from another phone? Bring it in. Apps that are not installed yet return to their place when they are."), color = HyleColors.Ink, fontSize = 14.sp, modifier = Modifier.padding(bottom = 18.dp))
                        TextButtonPill(L10n.tr("Import a setup file…"), primary = true) { importPicker.launch(arrayOf("application/json", "text/plain", "*/*")) }
                        importMessage?.let { Text(it, color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                val last = step == steps.lastIndex
                TextButtonPill(if (!last) L10n.tr("Next") else L10n.tr("Done"), primary = true) { if (!last) step++ else onDone() }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(L10n.tr(text), color = HyleColors.InkBright, fontSize = 26.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(bottom = 16.dp))
}
