package com.bos.sphere.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bos.sphere.DefaultLauncher
import com.bos.sphere.core.data.PuckAvailability
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.ActionRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wave 57 (CFG-42): the words of the two rows of Access and privacy that are not runtime grants. Pure, so a test reads them without a screen. */
object AccessPrivacyText {
    fun dnd(granted: Boolean) = if (granted) "Allowed · Review" else "Not allowed · Allow"
    fun defaultHome(isDefault: Boolean) = if (isDefault) "Liftoff is the default home · Change" else "Another app is the default home · Make Liftoff the default"
}

/**
 * Wave 57 (CFG-42), System > Access and privacy: one page listing every grant Liftoff uses: notification access, usage access, contacts, the
 * search folders and the microphone (the rows of [AccessPane], each behind its explainer), then Do Not Disturb access and the default home.
 * The states are read again each time the app resumes (the user comes back from Android's page), off the main thread.
 */
@Composable
fun AccessPrivacyPane(system: SystemLinks) {
    val ctx = LocalContext.current
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val state by produceState(true to true, ctx, resumes) {
        value = withContext(Dispatchers.IO) {
            runCatching { PuckAvailability.policyAccess(ctx) }.getOrDefault(true) to runCatching { DefaultLauncher.isDefault(ctx) }.getOrDefault(true)
        }
    }
    Column {
        AccessPane() // its own one-line intro heads the page
        ActionRow(L10n.tr("Do Not Disturb access"), L10n.tr(AccessPrivacyText.dnd(state.first)), flashId = SettingsRowMeta.PaneIds.ACCESS_DND, onClick = system.dndAccess)
        ActionRow(L10n.tr("Set as default home"), L10n.tr(AccessPrivacyText.defaultHome(state.second)), flashId = SettingsRowMeta.PaneIds.ACCESS_DEFAULT_HOME, onClick = system.setDefaultHome)
    }
}
