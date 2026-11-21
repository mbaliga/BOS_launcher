package com.bos.sphere.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.bos.sphere.core.data.HistoryEntry
import com.bos.sphere.core.data.HubNotification
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wave 33 (commsDebt): where [CommsRead.merge] runs. The merge of the live feed, up to 5000 history entries and the twin collapse is linear but not free, so
 * for a large input it runs on [dispatcher] (never the main thread) while the screen keeps the previous result (or, before the first, the live list alone).
 * A small input (at most [instantLimit] live rows plus history entries) is merged in the composition itself: it is a fraction of a millisecond and the
 * list never shows a stale frame. Tests set [dispatcher] to an unconfined one (or lower [instantLimit]) to get a synchronous result.
 */
internal object CommsMergeConfig {
    @Volatile var dispatcher: CoroutineDispatcher = Dispatchers.Default
    @Volatile var instantLimit: Int = 400
    fun reset() { dispatcher = Dispatchers.Default; instantLimit = 400 }
}

/** The merged Comms list for [items] and [history]; see [CommsMergeConfig]. */
@Composable
internal fun rememberCommsMerged(items: List<HubNotification>, history: List<HistoryEntry>, readKeys: Set<String>, enabled: Boolean): CommsRead.Merged {
    if (!enabled) return remember(items, readKeys) { CommsRead.Merged(items, readKeys, emptyMap()) }
    if (items.size + history.size <= CommsMergeConfig.instantLimit) return remember(items, history, readKeys) { CommsRead.merge(items, history, readKeys) }
    val liveOnly = remember(items, readKeys) { CommsRead.Merged(items, readKeys, emptyMap()) }
    val merged by produceState(liveOnly, items, history, readKeys) {
        value = withContext(CommsMergeConfig.dispatcher) { CommsRead.merge(items, history, readKeys) }
    }
    return merged
}
