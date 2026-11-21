package com.bos.sphere

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Wave 33 (commsDebt): whether Comms (the Launchpad card) is open. Comms stays open under the app a notification opens, so Back returns to it; if Android
 * kills the launcher meanwhile, the open state is saved with the activity's state and comes back with it instead of the launcher restarting on Glance.
 * A Home press ([homeSignal] changing) closes it, as it closes every other surface. A signal that is already non-zero when the screen is first composed
 * (a recreation) is not a press, so it does not close what was just restored.
 */
@Composable
internal fun rememberCommsOpen(homeSignal: Int): MutableState<Boolean> {
    val open = rememberSaveable { mutableStateOf(false) }
    val seen = remember { mutableIntStateOf(homeSignal) }
    LaunchedEffect(homeSignal) { if (homeSignal != seen.intValue) { seen.intValue = homeSignal; open.value = false } }
    return open
}
