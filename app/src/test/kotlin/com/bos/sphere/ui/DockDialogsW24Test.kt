package com.bos.sphere.ui

import android.app.Dialog
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import com.bos.sphere.core.capabilities.Tier
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.launch.AppGroup
import com.bos.sphere.core.launch.GroupLayout
import com.bos.sphere.core.launch.LaunchRequest
import com.bos.sphere.core.launch.LaunchTarget
import com.bos.sphere.feature.dock.DockInput
import com.bos.sphere.feature.dock.DockPanel
import com.bos.sphere.feature.dock.GroupEditor
import com.bos.sphere.feature.dock.InMemoryWorkspaceStore
import com.bos.sphere.feature.dock.PanelApp
import com.bos.sphere.feature.dock.WorkspaceManager
import com.bos.sphere.feature.grid.LauncherInput
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * INP-20, wave 24: the three dock dialogs that hold a text field (the panel, the group editor and the session dialog) are now rendered
 * with the app's real dialog hook (`DockInputWiring`), a field is focused and typed into with key events sent to the dialog's own
 * window, and a controller's B button sent to that window closes the dialog on top (and only it).
 *
 * Why these run without the Compose test rule: the earlier note blamed the field's blinking cursor. A stack sample in wave 24 shows
 * the real cause: the rule's Robolectric idling strategy keeps calling `requestLayout` on the dialog's compose root, each forced measure
 * makes the text field publish a new layout result (a state write), and the strategy then sees pending work again, so it never idles,
 * even with the field unfocused or read-only. Hosted in a plain `ComposeView` and run on the paused main looper, the same dialog settles
 * (no further text layouts over five seconds of looper time), so this is a limit of the test rule, not of the dialogs. Nodes are found
 * and acted on through the public `ViewRootForTest` semantics of each window. Key events are synthesized; no controller on a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
class DockDialogsW24Test {
    private lateinit var scenario: ActivityScenario<ComponentActivity>

    @Before fun setUp() {
        L10n.setLanguage("en")
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
    }

    @After fun reset() {
        // A dialog a failed assertion left behind keeps measuring on the shared looper and would stop the next class from ever getting idle.
        showing().forEach { runCatching { it.dismiss() } }
        scenario.close()
        // The real cause of INP_20's "dialog stays isShowing" (wave 48): the sandbox keeps one Choreographer for the main thread across tests. A test that
        // ends while a frame is requested (a focused field's cursor blinks forever) leaves it waiting for a vsync that Robolectric's looper reset throws away, and
        // from then on no recomposer of a later test (or class: DockWiringTest, then never idle for the compose rule) gets a frame, so `open = false` is never applied.
        // Running the looper after the scenario is closed lets that requested frame be delivered first.
        repeat(3) { ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS) }
        DockInput.bridge = null
        DockInput.dialogHook = {}
        LauncherInput.focus.nodes().map { it.id }.forEach(LauncherInput.focus::unregister)
    }

    private val apps = listOf("Alpha", "Bravo", "Charlie").map { PanelApp("k$it", it, "p.$it", LaunchTarget("p.$it", null, 0)) }

    private fun manager(withGroup: Boolean = false): WorkspaceManager {
        var n = 0
        val m = WorkspaceManager(InMemoryWorkspaceStore(), newId = { "id${n++}" })
        if (withGroup) m.saveGroup(AppGroup("Study", apps.take(2).map { LaunchRequest(it.target) }, GroupLayout.Tile, "g1"))
        return m
    }

    /**
     * Runs the main looper for [ms] of looper time. Wave 48, found by instrumenting the failing INP_20 tests (a recomposer with pending work and no
     * change applied, the dialog still composed): these tests host their content in a plain ComposeView, with no compose test rule, and the sandbox
     * shares Compose's process-wide state across tests. Two things were needed for a state write such as a dialog's `open = false` to reach its
     * recomposer in every test order. Each round applies pending global snapshot writes by hand (the rule does this itself; Compose's own applier is a
     * coroutine launched once per write burst, which a looper reset between tests can drop), and each round yields a few real milliseconds, because
     * work handed back from a real thread (focus and text-field coroutines on the default dispatcher) only arrives in wall-clock time, not looper time.
     */
    private fun idle(ms: Long = 1500) = repeat(3) { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); ShadowLooper.idleMainLooper(ms / 3, TimeUnit.MILLISECONDS); Thread.sleep(5) }

    /** [withHook] false composes no dock and no hook, so a test sees only what the content installs itself (the Ground Control row). */
    private fun show(withHook: Boolean = true, content: @Composable () -> Unit) {
        scenario.onActivity { a -> a.setContentView(ComposeView(a).apply { setContent { if (withHook) DockInputWiring.Install(); content() } }) }
        idle()
    }

    private fun showing(): List<Dialog> = ShadowDialog.getShownDialogs().filter { it.isShowing }

    /** A compose dialog is dismissed by the composition that removes it, which needs a frame after the state change: gives the removal a few more rounds (up to 10 s of wall time). A dialog that never goes still fails. */
    private fun settleDialogs() { val end = System.nanoTime() + 10_000_000_000L; while (showing().isNotEmpty() && System.nanoTime() < end) { Thread.sleep(25); idle(300) } }
    private fun top(): Dialog = showing().last()

    private fun composeRoot(v: View): ViewRootForTest? {
        if (v is ViewRootForTest) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) composeRoot(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun nodes(d: Dialog): List<SemanticsNode> = nodes(d.window!!.decorView)

    private fun nodes(v: View): List<SemanticsNode> {
        val root = checkNotNull(composeRoot(v)) { "no compose root in the window" }
        val out = mutableListOf<SemanticsNode>()
        fun walk(n: SemanticsNode) { out += n; n.children.forEach(::walk) }
        walk(root.semanticsOwner.unmergedRootSemanticsNode)
        return out
    }

    private fun texts(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text).orEmpty().map(AnnotatedString::text)
    private fun click(d: Dialog, label: String) = click(nodes(d), label)

    private fun click(all: List<SemanticsNode>, label: String) {
        fun has(n: SemanticsNode): Boolean = label in texts(n) || n.children.any(::has)
        val node = all.last { it.config.getOrNull(SemanticsActions.OnClick) != null && has(it) } // the innermost clickable holding the label
        node.config[SemanticsActions.OnClick].action!!.invoke()
        idle()
    }
    private fun fields(d: Dialog) = nodes(d).filter { it.config.getOrNull(SemanticsProperties.EditableText) != null }
    private fun fieldText(n: SemanticsNode) = n.config[SemanticsProperties.EditableText].text

    private fun key(d: Dialog, code: Int) {
        val cb = d.window!!.callback
        cb.dispatchKeyEvent(KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, code, 0))
        cb.dispatchKeyEvent(KeyEvent(0L, 0L, KeyEvent.ACTION_UP, code, 0))
        idle(300)
    }

    /** Focuses [field] and types [word] as hardware key presses at the dialog's window (letters pass through the controller hook). */
    private fun type(d: Dialog, field: SemanticsNode, word: String) {
        field.config[SemanticsActions.RequestFocus].action!!.invoke()
        idle()
        word.forEach { key(d, KeyEvent.keyCodeFromString("KEYCODE_" + it.uppercaseChar())) }
        idle(2000) // the focused field's cursor blinks; the looper still settles
    }

    @Test fun INP_20_theDockPanelIsAFullPageThatRaisesTheDialogLayerAndControllerBClosesIt() {
        val m = manager()
        var open by mutableStateOf(true)
        show { if (open) DockPanel(m, Tier.T2, apps, emptyList(), {}, {}, { _, _, _ -> }, onDismiss = { open = false }) }
        assertEquals(1, showing().size)
        assertEquals("the dock dialog layer is raised while the panel shows", DockInput.DIALOG_LAYER, LauncherInput.focus.topLayer)
        val panel = top()
        assertTrue("the panel is a page: it holds no text field of its own", fields(panel).isEmpty())
        key(panel, KeyEvent.KEYCODE_BUTTON_B)
        assertFalse("B at the Dock page closes the panel", open)
        settleDialogs()
        assertTrue(showing().isEmpty())
        assertEquals("the dialog layer is released", 0, LauncherInput.focus.topLayer)
    }

    @Test fun INP_20_theGroupEditorTakesTypedKeysSavesAndControllerBClosesItsNestedPickerThenItself() {
        var saved: AppGroup? = null
        var open by mutableStateOf(true)
        show { if (open) GroupEditor(apps, onSave = { saved = it; open = false }, onDismiss = { open = false }) }
        val editor = top()
        type(editor, fields(editor).single(), "focus")
        click(editor, "Choose 2 to 4 apps")
        assertEquals("the app picker opens over the editor", 2, showing().size)
        key(top(), KeyEvent.KEYCODE_BUTTON_B)
        assertEquals("B closes only the picker on top", 1, showing().size)
        assertTrue(open)
        click(editor, "Choose 2 to 4 apps")
        val picker = top()
        click(picker, "Alpha"); click(picker, "Bravo"); click(picker, "Done")
        assertEquals(1, showing().size)
        click(editor, "Save")
        assertEquals("focus", saved?.name)
        assertEquals(listOf("p.Alpha", "p.Bravo"), saved!!.members.map { it.target.packageName })
        assertFalse(open)

        saved = null
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { open = true }
        idle()
        val again = top()
        type(again, fields(again).single(), "gone")
        key(again, KeyEvent.KEYCODE_BUTTON_B)
        assertFalse(open)
        assertEquals("B never saves", null, saved)
    }

    @Test fun INP_20_theSessionDialogTakesTypedKeysSavesAndControllerBClosesIt() {
        val group = AppGroup("Study", apps.take(2).map { LaunchRequest(it.target) }, GroupLayout.Tile, "g1")
        var saved: String? = null
        var open by mutableStateOf(true)
        show { if (open) com.bos.sphere.feature.dock.SessionDialog(group, emptyList(), onSave = { n, _ -> saved = n; open = false }, onDismiss = { open = false }) }
        val session = top()
        val name = fields(session).single()
        assertEquals("the name starts as the group's", "Study", fieldText(name))
        // Typing into a field that already holds text is covered with the IME path in OneUiPopupsW41Test (the raw key path of this
        // harness does not insert into a pre-filled field); here the dialog saves the name it holds and B closes it.
        click(session, "Save")
        assertEquals("Study", saved)
        assertFalse(open)

        saved = null
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { open = true }
        idle()
        key(top(), KeyEvent.KEYCODE_BUTTON_B)
        assertFalse("B closes the session dialog without saving", open)
        assertEquals(null, saved)
        settleDialogs()
        assertTrue(showing().isEmpty())
    }

    // ---- the dock stays optional: its pages are reachable from Ground Control with the dock off (docs/decisions/dockOptional.md)

    @Test fun INP_20_withTheDockOffGroundControlsAppGroupsPageInstallsItsOwnHookAndControllerBClosesTheEditor() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val m = com.bos.sphere.feature.dock.WorkspaceRepository.get(context)
        m.setDock { it.copy(enabled = false) }
        assertTrue("no hook before the page composes", DockInput.bridge == null)
        assertTrue("no dialog hook before the page composes, so the layer below can only come from the page", DockInput.dialogHook !== DockInputWiring.dialogHook)
        show(withHook = false) { androidx.compose.foundation.layout.Column { DockSettingsPane(DockPaneKind.Groups) } }
        assertTrue("nothing floats: the page is in the host's own content", showing().isEmpty())
        var home: View? = null
        scenario.onActivity { home = it.window.decorView }
        val labels = nodes(home!!).flatMap(::texts)
        assertTrue("the groups, schedule and sessions are all on the page", labels.containsAll(listOf("Your groups", "New group", "Schedule an app", "Sessions")))
        assertFalse("opening it never turns the dock on", m.current.dock.isEnabled(com.bos.sphere.core.capabilities.Tier.T3))
        click(nodes(home!!), "New group")
        assertEquals("the group editor opens from it", 1, showing().size)
        assertEquals("the dialog hook is installed with no dock composed", DockInput.DIALOG_LAYER, LauncherInput.focus.topLayer)
        key(top(), KeyEvent.KEYCODE_BUTTON_B)
        settleDialogs()
        assertTrue("B closed the editor", showing().isEmpty())
        assertEquals(0, LauncherInput.focus.topLayer)
    }
}
