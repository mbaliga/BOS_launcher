package com.bos.sphere.ui

import android.content.Context
import com.bos.sphere.core.data.StateService
import com.bos.sphere.feature.dock.WorkspaceRepository
import com.bos.sphere.feature.grid.LauncherInput

/**
 * CFG-02: after a state import or restore, the parts that keep their own in-memory copy re-read what the import wrote: the
 * dock stream's workspace manager and the keyboard shortcut registry. Everything else reads its preference file when the
 * activity is recreated.
 */
fun reloadAfterStateChange(context: Context) {
    runCatching { WorkspaceRepository.get(context).reload() }
    runCatching { com.bos.sphere.core.data.PuckActionsStore.get(context).reload() }
    runCatching { LauncherInput.registry.import(StateService.get(context).keyboardOverrides()) }
}
