package com.bos.sphere.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bos.sphere.DefaultLauncher
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.CornerSlot
import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.PuckActionsStore
import com.bos.sphere.core.data.PuckAvailability
import com.bos.sphere.core.design.GrantKind
import com.bos.sphere.feature.grid.SetupState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wave 56 (settingsCore), CFG-32: where the Finish setup state comes from. A Do Not Disturb action is "bound" when a corner, a gesture or a section of
 * the drawer's shortcut ring runs Do Not Disturb or Silent mode; only then is the access something the user is missing. The state is read again each
 * time the app resumes (the user comes back from a system page) and off the main thread.
 */
object SetupStatus {
    private val DND_ACTIONS = setOf(CornerActionId.DoNotDisturb, CornerActionId.RingerSilent)

    private fun CornerItem.runsDnd() = this is CornerItem.Action && id in DND_ACTIONS

    /** Whether any corner, gesture or ring section is bound to a Do Not Disturb or Silent action. */
    fun dndActionBound(corners: Map<Corner, CornerSlot>, gestures: Map<HomeGesture, CornerItem>, ring: List<com.bos.sphere.core.data.PuckSlot>): Boolean =
        corners.values.any { slot ->
            when (slot) {
                is CornerSlot.Single -> slot.item.runsDnd()
                is CornerSlot.Palette -> slot.items.any { it.runsDnd() }
                else -> false
            }
        } || gestures.values.any { it.runsDnd() } || ring.any { it is com.bos.sphere.core.data.PuckSlot.Action && it.id in DND_ACTIONS }

    /** Reads the system state now. Blocking: call off the main thread. */
    fun read(context: Context, dndBound: Boolean): SetupState = SetupState(
        defaultHome = runCatching { DefaultLauncher.isDefault(context) }.getOrDefault(true),
        notificationAccess = GrantSupport.isGranted(context, GrantKind.Notifications),
        usageAccess = GrantSupport.isGranted(context, GrantKind.UsageAccess),
        dndAccess = PuckAvailability.policyAccess(context),
        dndActionBound = dndBound,
    )
}

/** The Finish setup state for the settings, refreshed on every resume. Starts as complete, so no row flashes in before the first read. */
@Composable
fun rememberSetupState(corners: Map<Corner, CornerSlot>, gestures: Map<HomeGesture, CornerItem>, active: Boolean): SetupState {
    val context = LocalContext.current
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val ring by remember(context) { PuckActionsStore.get(context) }.slots.collectAsState()
    val bound = remember(corners, gestures, ring) { SetupStatus.dndActionBound(corners, gestures, ring) }
    val state by produceState(SetupState.Complete, context, resumes, bound, active) {
        if (active) value = withContext(Dispatchers.IO) { SetupStatus.read(context, bound) }
    }
    return state
}
