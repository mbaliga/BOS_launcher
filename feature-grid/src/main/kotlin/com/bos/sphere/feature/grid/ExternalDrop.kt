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
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
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

    /** Set by Radar while it is open so a drag that starts on a result closes it, the same way the drawer closes (INP-11). */
    @Volatile var onDragStart: (() -> Unit)? = null

    /** The live home's handler for dropped link, file and search items; it places them on the current page through the home's own edit path (INP-11). */
    @Volatile var onItems: ((List<com.bos.sphere.core.catalog.ItemDef>) -> Unit)? = null

    /** Set by the home: a finger started dragging an app after a long press, so the long-press menu that opened with it must go (INP-11). */
    @Volatile var onTouchDragStart: (() -> Unit)? = null

    /** What the last drop looked like, for the SPK-05 spike row: types offered, clip items, what was made of them. */
    @Volatile var report: String? = null
}

/** Registers the home's handler for a launcher app key dropped on it (INP-11). Call once from the home. */
@Composable
fun HomeDropBinding(onApp: (String) -> Unit, onItems: ((List<com.bos.sphere.core.catalog.ItemDef>) -> Unit)? = null, onTouchDrag: (() -> Unit)? = null) {
    val latest by rememberUpdatedState(onApp)
    val latestTouch by rememberUpdatedState(onTouchDrag)
    val latestItems by rememberUpdatedState(onItems)
    DisposableEffect(Unit) {
        val h: (String) -> Unit = { latest(it) }
        val hi: (List<com.bos.sphere.core.catalog.ItemDef>) -> Unit = { latestItems?.invoke(it) }
        LauncherDrops.onApp = h
        if (onItems != null) LauncherDrops.onItems = hi
        val ht: () -> Unit = { latestTouch?.invoke() }
        if (onTouchDrag != null) LauncherDrops.onTouchDragStart = ht
        onDispose {
            if (LauncherDrops.onTouchDragStart === ht) LauncherDrops.onTouchDragStart = null
            if (LauncherDrops.onApp === h) LauncherDrops.onApp = null
            if (LauncherDrops.onItems === hi) LauncherDrops.onItems = null
        }
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
                val clips = readDrop(context, event) ?: return false
                val result = DropIngest.ingest(clips)
                LauncherDrops.report = "types=${event.mimeTypes()} items=${clips.size} made=${result.items.size + result.appKeys.size} ignored=${result.ignored}"
                result.appKeys.forEach { LauncherDrops.onApp?.invoke(it) }
                if (result.items.isNotEmpty()) {
                    // The live home places the items itself (so they render at once); with no home composed they go to the arrangement store.
                    LauncherDrops.onItems?.invoke(result.items) ?: HomeDropStore.add(context, sc, result)
                    android.widget.Toast.makeText(context, L10n.tr("Added %d to home", result.items.size), android.widget.Toast.LENGTH_SHORT).show()
                }
                return result.items.isNotEmpty() || result.appKeys.isNotEmpty()
            }
        }
    }
    return this.dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)
}

/** The clips of a drop, after taking the drag permission for content from other apps (held while the activity lives); null with no clip. */
private fun readDrop(context: Context, event: DragAndDropEvent): List<DropClip>? {
    val drag = event.toAndroidDragEvent()
    val clip = drag.clipData ?: return null
    runCatching { context.activity()?.let { ActivityCompat.requestDragAndDropPermissions(it, drag) } }
    val mime = clip.description.getMimeType(0)
    return (0 until clip.itemCount).map { i ->
        val it = clip.getItemAt(i)
        DropClip(it.text?.toString() ?: it.htmlText, it.uri?.toString(), mime, it.uri?.let { u -> displayName(context, u) })
    }
}

/**
 * INP-11: a drop target for one tile or rail of the launcher that takes launcher apps, links, files and text and hands
 * them to [onDrop] (app keys first, then the item definitions); true from it means the drop was used. Used by folder tiles
 * (file the dropped item into the folder) and the dock rail (pin it).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.launcherDropTarget(onDrop: (DropResult) -> Boolean): Modifier {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onDrop)
    val target = androidx.compose.runtime.remember(context) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val clips = readDrop(context, event) ?: return false
                val result = DropIngest.ingest(clips)
                LauncherDrops.report = "target types=${event.mimeTypes()} items=${clips.size} made=${result.items.size + result.appKeys.size} ignored=${result.ignored}"
                if (result.items.isEmpty() && result.appKeys.isEmpty()) return false
                return latest(result)
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
    private val io = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "home-drop-io").apply { isDaemon = true } }

    /** Reads and writes the store off the main thread (it loads every saved arrangement from disk). */
    fun add(context: Context, sc: SizeClass, result: DropResult) {
        val app = context.applicationContext
        io.execute {
            runCatching {
                val store = HomeStateStore(app)
                val state = store.arrangements.value.stateFor(sc) ?: HomeState()
                store.save(sc, apply(state, result))
            }
        }
    }

    /** Pure part, tested: each item goes on the first page unless it is already somewhere on the home. */
    fun apply(state: HomeState, result: DropResult, cap: (com.bos.sphere.core.data.PageLayout) -> Int = { 12 }): HomeState =
        result.items.fold(state) { s, def -> HomeEdit.add(s, def, 0, cap) }
}

/**
 * Starts a platform drag of this launcher app when a mouse (or stylus) drags it, or a finger drags it after a long press, so it
 * can be dropped on the home, the dock rail or a folder tile (INP-11). The long-press menu stays the other way to "Add to home" (INP-07).
 * [pointer] false leaves a mouse drag to the host (the Orbit turns its rings with it).
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.appDragSource(appKey: String, onStart: () -> Unit = {}, pointer: Boolean = true): Modifier = this.dragAndDropSource(drawDragDecoration = {}) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val clip = { ClipData(android.content.ClipDescription("app", arrayOf(DropIngest.APP_MIME)), ClipData.Item(appKey)) }
        if (down.type == PointerType.Mouse || down.type == PointerType.Stylus) {
            if (!pointer) return@awaitEachGesture
            val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
            if (slop != null) {
                onStart()
                startTransfer(DragAndDropTransferData(clip()))
            }
        } else {
            // INP-11: a finger drags after a long press, once it moves. The long press still opens the item's menu (the person may only
            // want that); starting the drag closes that menu again, so a press that stays put keeps the menu and one that moves drags.
            val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            val slop = awaitTouchSlopOrCancellation(held.id) { change, _ -> change.consume() }
            if (slop != null) {
                LauncherDrops.onTouchDragStart?.invoke()
                onStart()
                startTransfer(DragAndDropTransferData(clip()))
            }
        }
    }
}
