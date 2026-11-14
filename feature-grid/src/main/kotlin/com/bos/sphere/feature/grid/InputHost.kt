package com.bos.sphere.feature.grid

import android.view.KeyEvent
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.input.ControllerKeys
import com.bos.sphere.core.input.FRect
import com.bos.sphere.core.input.FocusDir
import com.bos.sphere.core.input.FocusGraph
import com.bos.sphere.core.input.FocusKind
import com.bos.sphere.core.input.FocusNode
import com.bos.sphere.core.input.InputMode
import com.bos.sphere.core.input.InputModeTracker
import com.bos.sphere.core.input.KeyRoute
import com.bos.sphere.core.input.KeyRouter
import com.bos.sphere.core.input.Keys
import com.bos.sphere.core.input.LauncherCommand
import com.bos.sphere.core.input.ShortcutRegistry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The launcher's one input hub (INP-01, INP-04, INP-05, INP-06, INP-20). The activity hands every key event to
 * [onKeyEvent]; pointer and touch events reach [tracker] through [inputSensing]; items register themselves in
 * [focus] with [launcherItem]. Nothing here touches what has focus when the mode changes.
 */
object LauncherInput {
    val tracker = InputModeTracker()
    val registry = ShortcutRegistry()
    private val router = KeyRouter(registry)
    val focus = FocusRegistry()

    /** Set by the home while it is showing: hot corners read their labels and actions from here (INP-16). */
    val hotCorner = mutableStateOf<HotCornerBinding?>(null)

    /** Keyboard edit mode (INP-09): arrows move the focused item instead of the focus. */
    val editMode = mutableStateOf(false)
    /** Set by the paged home while it is composed; returns true when it consumed the move. */
    @Volatile var moveHook: ((FocusDir) -> Boolean)? = null

    /** The shortcut sheet is open (F1, or from settings). */
    val sheetOpen = mutableStateOf(false)

    private val _commands = MutableSharedFlow<LauncherCommand>(extraBufferCapacity = 16)
    /** Registered shortcuts, fired on every launcher surface. */
    val commands: SharedFlow<LauncherCommand> = _commands.asSharedFlow()

    private val _emptyMenu = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    /** The Menu key with no item focused, or a right-click on empty home: the home's quick sheet (HOM-21). */
    val emptyMenu = _emptyMenu

    /** Surfaces that handle raw keys before shortcuts (the open puck). Last pushed is asked first. */
    private val keyHandlers = ArrayList<(KeyEvent) -> Boolean>()
    fun pushKeyHandler(h: (KeyEvent) -> Boolean) { keyHandlers += h }
    fun removeKeyHandler(h: (KeyEvent) -> Boolean) { keyHandlers -= h }

    /** True while a full-screen surface that does not register focus nodes is open (drawer, Radar, settings). */
    @Volatile var overlayOpen: Boolean = false

    private val swallowedUps = HashSet<Int>()

    /** PAL-01: the home sets this to open Radar when a printable key arrives on a launcher surface that has no use for it. */
    @Volatile var typeHook: ((KeyEvent) -> Boolean)? = null

    /** Runs [c] as if its shortcut had been pressed (a Radar result asking for a launcher command, PAL-03). */
    fun dispatch(c: LauncherCommand) { _commands.tryEmit(c) }

    /**
     * A controller button becomes the key the surfaces understand (INP-20); every other event is returned as is.
     * The activity should dispatch the returned event.
     */
    fun translate(e: KeyEvent): KeyEvent {
        val mapped = ControllerKeys.translate(e.keyCode)
        if (mapped == e.keyCode) return e
        val meta = if (ControllerKeys.shiftFor(e.keyCode)) e.metaState or KeyEvent.META_SHIFT_ON else e.metaState
        return KeyEvent(e.downTime, e.eventTime, e.action, mapped, e.repeatCount, meta, e.deviceId, e.scanCode, e.flags, e.source)
    }

    /** Returns true when the launcher consumed the event. Call before the default dispatch. */
    fun onKeyEvent(e: KeyEvent): Boolean {
        val down = e.action == KeyEvent.ACTION_DOWN
        if (!Keys.isModifier(e.keyCode)) tracker.onKey()
        if (!down) return swallowedUps.remove(e.keyCode)
        for (i in keyHandlers.indices.reversed()) if (keyHandlers[i](e)) { swallowedUps += e.keyCode; return true }
        val route = router.route(e.keyCode, e.isCtrlPressed, e.isAltPressed, e.isShiftPressed, e.isMetaPressed)
        val consumed = when (route) {
            is KeyRoute.Command -> { if (e.repeatCount == 0) _commands.tryEmit(route.command); true }
            is KeyRoute.Move -> if (editMode.value && moveHook != null) { moveHook?.invoke(route.dir); true } else focus.move(route.dir)
            is KeyRoute.Tab -> focus.tab(route.backwards)
            KeyRoute.Menu -> if (focus.currentId == null && !overlayOpen) { emptyMenu.tryEmit(Unit); true } else false
            KeyRoute.Activate -> false
            KeyRoute.None -> !overlayOpen && !focus.anyLayerActive && typeHook?.invoke(e) == true
        }
        if (consumed) swallowedUps += e.keyCode
        return consumed
    }

    /** Test and tooling hook: what a key would do right now. */
    internal fun routeFor(keyCode: Int, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false): KeyRoute = router.route(keyCode, ctrl, alt, shift)
}

/** Items register here with their bounds so [FocusGraph] can pick the next one; focus itself stays with Compose. */
class FocusRegistry {
    private class Entry(var node: FocusNode, val requester: FocusRequester)
    private val entries = LinkedHashMap<String, Entry>()
    private val activeLayers = HashMap<Int, Int>()

    /** The registered item that currently has Compose focus, or null (also null while a text field has it). */
    var currentId: String? = null
        private set

    fun register(node: FocusNode, requester: FocusRequester) {
        val e = entries[node.id]
        if (e == null) entries[node.id] = Entry(node, requester) else e.node = node
    }

    fun unregister(id: String) { entries.remove(id); if (currentId == id) currentId = null }
    fun onFocus(id: String, focused: Boolean) { if (focused) currentId = id else if (currentId == id) currentId = null }

    /** A surface (drawer, puck, Radar...) that sits above the home while [active]. */
    fun layerActive(layer: Int, active: Boolean) {
        val n = (activeLayers[layer] ?: 0) + if (active) 1 else -1
        if (n <= 0) activeLayers.remove(layer) else activeLayers[layer] = n
    }

    val anyLayerActive: Boolean get() = activeLayers.isNotEmpty()

    val topLayer: Int get() = maxOf(activeLayers.keys.maxOrNull() ?: 0, entries.values.maxOfOrNull { it.node.layer } ?: 0)

    fun nodes(): List<FocusNode> = entries.values.map { it.node }

    private fun usable(): Boolean = (activeLayers.keys.maxOrNull() ?: 0) <= (entries.values.maxOfOrNull { it.node.layer } ?: 0)

    /** Arrow key. Only acts when a registered item has focus, so text fields keep their arrows. */
    fun move(dir: FocusDir): Boolean {
        val cur = currentId ?: return false
        if (!usable()) return false
        val next = FocusGraph.move(nodes(), cur, dir) ?: return true // edge of the graph: stay put, keep the key
        return request(next.id)
    }

    fun tab(backwards: Boolean): Boolean {
        val cur = currentId ?: return false
        if (!usable()) return false
        val next = FocusGraph.tab(nodes(), cur, backwards) ?: return false
        return request(next.id)
    }

    fun request(id: String): Boolean = entries[id]?.let { runCatching { it.requester.requestFocus() }.isSuccess } ?: false
}

val LocalInputMode = compositionLocalOf<State<InputMode>> { mutableStateOf(InputMode.Touch) }
val LocalFocusLayer = compositionLocalOf { 0 }

/** Marks a surface as sitting above the home for as long as it is composed with [active] true. */
@Composable
fun FocusLayer(layer: Int, active: Boolean = true, content: @Composable () -> Unit) {
    DisposableEffect(layer, active) {
        if (active) LauncherInput.focus.layerActive(layer, true)
        onDispose { if (active) LauncherInput.focus.layerActive(layer, false) }
    }
    CompositionLocalProvider(LocalFocusLayer provides layer, content = content)
}

/**
 * Root modifier: the last input event sets the mode (touch, keyboard, pointer), observed on the Initial pass so
 * nothing is consumed. Hover and mouse buttons are pointer; a finger or pen is touch.
 */
fun Modifier.inputSensing(tracker: InputModeTracker = LauncherInput.tracker): Modifier = this.pointerInput(Unit) {
    // HOM-21: a secondary click nobody claimed (items claim theirs on the Initial pass) is a click on empty home.
    awaitPointerEventScope {
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Final)
            val c = ev.changes.firstOrNull() ?: continue
            if (ev.type == PointerEventType.Press && c.type == PointerType.Mouse && ev.buttons.isSecondaryPressed && !c.isConsumed &&
                !LauncherInput.overlayOpen && !LauncherInput.focus.anyLayerActive
            ) LauncherInput.emptyMenu.tryEmit(Unit)
        }
    }
}.pointerInput(tracker) {
    awaitPointerEventScope {
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val c = ev.changes.firstOrNull() ?: continue
            when (c.type) {
                PointerType.Mouse -> if (ev.type != PointerEventType.Exit) tracker.onPointer()
                PointerType.Touch, PointerType.Stylus, PointerType.Eraser -> if (ev.type == PointerEventType.Press) tracker.onTouch()
                else -> Unit
            }
        }
    }
}

/** Provides [LocalInputMode] to the tree from the shared tracker. */
@Composable
fun InputModeProvider(content: @Composable () -> Unit) {
    val state = LauncherInput.tracker.mode.collectAsState()
    CompositionLocalProvider(LocalInputMode provides state, content = content)
}

/**
 * Makes an element a launcher focus-graph node: reachable by arrows and Tab, a visible focus ring only in keyboard
 * mode, a hover wash and hand pointer only in pointer mode, Enter or confirm activates, the Menu key and a secondary
 * click open [onMenu] (the same callback long-press calls, so the three can never differ, INP-03).
 *
 * When the element already has a clickable (which is itself the focus target), pass `focusable = false` and put this
 * modifier directly before the clickable.
 */
fun Modifier.launcherItem(
    id: String,
    kind: FocusKind = FocusKind.Item,
    order: Int? = null,
    layer: Int? = null,
    focusable: Boolean = true,
    onActivate: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    /** Called when keyboard focus arrives, so a scrolling or paged host can bring the item into view. */
    onFocused: (() -> Unit)? = null,
    ringRadiusDp: Float = 14f,
): Modifier = composed {
    val requester = remember { FocusRequester() }
    val modeState = LocalInputMode.current
    val ambientLayer = LocalFocusLayer.current
    val nodeLayer = layer ?: ambientLayer
    var focused by remember { mutableStateOf(false) }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val latestActivate by rememberUpdatedState(onActivate)
    val latestMenu by rememberUpdatedState(onMenu)
    val latestFocused by rememberUpdatedState(onFocused)
    DisposableEffect(id) { onDispose { LauncherInput.focus.unregister(id) } }
    val ring = Color.White
    val wash = Color.White.copy(alpha = 0.10f)
    var m = this
        .onGloballyPositioned {
            val b = it.boundsInRoot()
            LauncherInput.focus.register(FocusNode(id, FRect(b.left, b.top, b.right, b.bottom), nodeLayer, kind, order), requester)
        }
        .semantics { if (order != null) traversalIndex = order.toFloat() }
        .focusRequester(requester)
        .onFocusChanged { focused = it.isFocused || it.hasFocus; LauncherInput.focus.onFocus(id, focused); if (it.isFocused) latestFocused?.invoke() }
    if (focusable) m = m.focusable()
    m = m
        .onKeyEvent { e ->
            val native = e.nativeKeyEvent
            if (native.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
            val k = native.keyCode
            when {
                onActivate != null && (k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER) -> { latestActivate?.invoke(); true }
                onMenu != null && (k == KeyEvent.KEYCODE_MENU || (k == KeyEvent.KEYCODE_F10 && native.isShiftPressed)) -> { latestMenu?.invoke(); true }
                else -> false
            }
        }
        .hoverable(hover)
        .pointerHoverIcon(PointerIcon.Hand)
        .pointerInput(Unit) {
            // A secondary click is the same as a long-press: consume it first so the clickable below never sees a click.
            awaitPointerEventScope {
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val c = ev.changes.firstOrNull() ?: continue
                    if (ev.type == PointerEventType.Press && c.type == PointerType.Mouse && ev.buttons.isSecondaryPressed && latestMenu != null) {
                        c.consume()
                        latestMenu?.invoke()
                    }
                }
            }
        }
        .drawWithContent {
            drawContent()
            val mode = modeState.value
            if (mode == InputMode.Pointer && hovered) {
                drawRoundRect(wash, size = size, cornerRadius = CornerRadius(ringRadiusDp.dp.toPx()))
            }
            if (mode == InputMode.Keyboard && focused) {
                val w = 2.dp.toPx()
                drawRoundRect(
                    ring, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w), cornerRadius = CornerRadius(ringRadiusDp.dp.toPx()), style = Stroke(w),
                )
            }
        }
    m
}

/** A container whose children read in a fixed linear order for TalkBack and keyboard traversal (NFR-13). */
fun Modifier.linearTraversal(): Modifier = semantics { isTraversalGroup = true }

/** Window size in dp, used by hot corners and gesture exclusions. */
@Composable
fun rememberWindowDp(): Pair<Float, Float> {
    val c = LocalConfiguration.current
    return c.screenWidthDp.toFloat() to c.screenHeightDp.toFloat()
}
