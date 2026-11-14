package com.bos.sphere.feature.grid

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.provider.OpenableColumns
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.bos.sphere.core.catalog.HomeEdit
import com.bos.sphere.core.catalog.HomeState
import com.bos.sphere.core.catalog.HomeStateStore
import com.bos.sphere.core.catalog.SizeClass
import com.bos.sphere.core.design.L10n

/** Where drops land. The home registers its handlers while it is composed ([HomeDropBinding]); the Lab page reads [report] (SPK-05). */
object LauncherDrops {
    @Volatile var onApp: ((String) -> Unit)? = null

    /** What the last drop looked like, for the SPK-05 spike row: types offered, clip items, what was made of them. */
    @Volatile var report: String? = null
}

/** Registers the home's handler for a launcher app key dropped on it (INP-11). Call once from the home. */
@Composable
fun HomeDropBinding(onApp: (String) -> Unit) {
    val latest by rememberUpdatedState(onApp)
    DisposableEffect(Unit) {
        val h: (String) -> Unit = { latest(it) }
        LauncherDrops.onApp = h
        onDispose { if (LauncherDrops.onApp === h) LauncherDrops.onApp = null }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

/**
 * INP-11: the home accepts drops from other apps (text, links, files) and from this launcher's drawer. Where the platform
 * delivers the drop (SPK-05 records whether it does), each becomes an item in the home arrangement store. Drops never
 * start without the platform handing one over; nothing is read from a file, only its name.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.homeDropTarget(sizeClass: SizeClass): Modifier {
    val context = LocalContext.current
    val sc by rememberUpdatedState(sizeClass)
    val target = androidx.compose.runtime.remember(context) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val drag = event.toAndroidDragEvent()
                val clip = drag.clipData ?: return false
                // Content from other apps stays readable only while this is held; it is released with the activity.
                runCatching { context.activity()?.let { ActivityCompat.requestDragAndDropPermissions(it, drag) } }
                val mime = clip.description.getMimeType(0)
                val clips = (0 until clip.itemCount).map { i ->
                    val it = clip.getItemAt(i)
                    DropClip(it.text?.toString() ?: it.htmlText, it.uri?.toString(), mime, it.uri?.let { u -> displayName(context, u) })
                }
                val result = DropIngest.ingest(clips)
                LauncherDrops.report = "types=${event.mimeTypes()} items=${clips.size} made=${result.items.size + result.appKeys.size} ignored=${result.ignored}"
                result.appKeys.forEach { LauncherDrops.onApp?.invoke(it) }
                if (result.items.isNotEmpty()) {
                    HomeDropStore.add(context, sc, result)
                    android.widget.Toast.makeText(context, L10n.tr("Added %d to home", result.items.size), android.widget.Toast.LENGTH_SHORT).show()
                }
                return result.items.isNotEmpty() || result.appKeys.isNotEmpty()
            }
        }
    }
    return this.dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)
}

private fun displayName(context: Context, uri: android.net.Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull()

/** [homeDropTarget] for the size class of the current window. */
@Composable
fun Modifier.homeDropTarget(): Modifier {
    val c = androidx.compose.ui.platform.LocalConfiguration.current
    return homeDropTarget(SizeClass.of(c.screenWidthDp, c.screenHeightDp))
}

/** Adds dropped items to the home arrangement of the current size class. */
object HomeDropStore {
    fun add(context: Context, sc: SizeClass, result: DropResult) {
        val store = HomeStateStore(context)
        val state = store.arrangements.value.stateFor(sc) ?: HomeState()
        store.save(sc, apply(state, result))
    }

    /** Pure part, tested: each item goes on the first page unless it is already somewhere on the home. */
    fun apply(state: HomeState, result: DropResult, cap: (com.bos.sphere.core.data.PageLayout) -> Int = { 12 }): HomeState =
        result.items.fold(state) { s, def -> HomeEdit.add(s, def, 0, cap) }
}

/**
 * Starts a platform drag of this launcher app when a mouse (or stylus) drags it, so it can be dropped on the home
 * (INP-11). A finger keeps its long-press menu, which has "Add to home" (INP-07); a drag by touch is not offered here.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.appDragSource(appKey: String, onStart: () -> Unit = {}): Modifier = this.dragAndDropSource(drawDragDecoration = {}) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.type == PointerType.Mouse || down.type == PointerType.Stylus) {
            val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
            if (slop != null) {
                onStart()
                val clip = ClipData(android.content.ClipDescription("app", arrayOf(DropIngest.APP_MIME)), ClipData.Item(appKey))
                startTransfer(DragAndDropTransferData(clip))
            }
        }
    }
}
