package com.bos.sphere.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import com.bos.sphere.core.data.OnboardingFlow
import com.bos.sphere.core.data.StateFormatException
import com.bos.sphere.core.data.StateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateOf
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.design.L10n

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
            }.onSuccess { reloadAfterStateChange(context); onDone() }
                .onFailure { importMessage = L10n.tr("That file could not be imported: %s", it.message ?: "") }
        }
    }
    com.bos.sphere.feature.grid.OnboardingView(
        step = step,
        prefs = prefs,
        importMessage = importMessage,
        onStyle = { s -> update { it.copy(homeStyle = s) } },
        onSetDefault = setDefaultHome,
        onImport = { importPicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
        onNext = { if (step < steps.lastIndex) step++ else onDone() },
        onSkip = onDone,
    )
}
