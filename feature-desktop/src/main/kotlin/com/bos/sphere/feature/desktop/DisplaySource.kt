package com.bos.sphere.feature.desktop

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Reads the connected displays through the public `DisplayManager` (EXT-01, EXT-09). */
class DisplaySource(context: Context) {
    private val dm = context.applicationContext.getSystemService(DisplayManager::class.java)

    fun snapshot(): List<TrackedDisplay> {
        val all = runCatching { dm.displays.toList() }.getOrDefault(emptyList())
        val raw = all.mapNotNull { runCatching { read(it) }.getOrNull() }
        val primary = raw.firstOrNull { it.isDefault }
        return raw.map { it.copy(mirrored = DisplayTracker.looksMirrored(it, primary)) }
    }

    private fun read(d: Display): TrackedDisplay {
        val m = android.util.DisplayMetrics().also { @Suppress("DEPRECATION") d.getRealMetrics(it) }
        val info = runCatching { d.deviceProductInfo }.getOrNull()
        val isDefault = d.displayId == Display.DEFAULT_DISPLAY
        return TrackedDisplay(
            id = d.displayId,
            name = d.name ?: "Display ${d.displayId}",
            widthPx = m.widthPixels,
            heightPx = m.heightPixels,
            densityDpi = m.densityDpi,
            isDefault = isDefault,
            // Same capability rule as core-capabilities: presentation and private displays cannot host ordinary tasks.
            canHostTasks = if (isDefault) true else d.flags and Display.FLAG_PRESENTATION == 0 && d.flags and Display.FLAG_PRIVATE == 0,
            identity = DisplayIdentity.key(d.name ?: "", m.widthPixels, m.heightPixels, info?.manufacturerPnpId, info?.productId, info?.modelYear),
        )
    }

    /** Emits a fresh snapshot now and after every add, remove or change callback; the collector reduces it with [DisplayTracker.sync]. */
    fun changes(): Flow<List<TrackedDisplay>> = callbackFlow {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { trySend(snapshot()) }
            override fun onDisplayRemoved(displayId: Int) { trySend(snapshot()) }
            override fun onDisplayChanged(displayId: Int) { trySend(snapshot()) }
        }
        dm.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        trySend(snapshot())
        awaitClose { dm.unregisterDisplayListener(listener) }
    }
}
