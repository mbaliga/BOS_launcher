package com.bos.sphere.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.bos.sphere.core.data.SystemSnapshot
import com.bos.sphere.core.data.SystemStateLogic
import com.bos.sphere.core.data.SystemTileKind
import kotlinx.coroutines.delay

// Wave 27: the four-button system row of wave 9 is gone (the owner's Comms mock-up has the clock and four plain glyphs instead, see CommsHeader.kt);
// what stays here is the live snapshot and the public system-panel hand-off both use.

/** The live snapshot, refreshed every 20 seconds while composed. */
@Composable
fun rememberSystemSnapshot(): SystemSnapshot {
    val ctx = LocalContext.current
    val s by produceState(SystemStateLogic.read(ctx)) {
        while (true) { delay(20_000); value = SystemStateLogic.read(ctx) }
    }
    return s
}

/** Starts the public system panel for [kind]; tiles whose panel nothing handles simply do nothing. */
fun openSystemPanel(ctx: android.content.Context, kind: SystemTileKind) {
    SystemStateLogic.intentFor(ctx, kind)?.let { runCatching { ctx.startActivity(it) } }
}
