package com.bos.sphere

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bos.sphere.core.signals.UsageRecents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** HOM-22: whether the usage-access grant is on, read off the main thread and again after the home resumes (the user may have just granted it). */
@Composable
fun rememberUsageAccess(context: Context, active: Boolean): State<Boolean> {
    val resumes = remember { MutableStateFlow(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumes.value = resumes.value + 1 }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val n by resumes.collectAsStateWithLifecycle()
    return produceState(initialValue = false, context, n, active) { if (active) value = withContext(Dispatchers.IO) { UsageRecents(context).hasAccess() } }
}
