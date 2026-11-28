package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.PendingWidget
import com.bos.sphere.core.data.StateService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** CFG-02 / CFG-04: the Backup pane (now in feature-grid, so it runs under Robolectric) over the real [StateService]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2600dp-port-xhdpi")
class BackupPaneUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val service get() = StateService.get(ApplicationProvider.getApplicationContext())

    @Before fun clean() {
        service.extras.write(service.extras.read().copy(widgets = emptyList()))
        service.snapshots.list().forEach { service.snapshots.delete(it.id) }
    }

    private fun show(onChanged: () -> Unit = {}) = rule.setContent { Column { BackupPane { onChanged() } } }

    @Test fun CFG_02_thePaneOffersExportImportAndTheSnapshotList() {
        show()
        rule.onNodeWithText("Export setup…").assertIsDisplayed()
        rule.onNodeWithText("Export settings only…").assertIsDisplayed()
        rule.onNodeWithText("Import setup…").assertIsDisplayed()
        rule.onNodeWithText("Import layout from another launcher…").assertIsDisplayed()
        rule.onNodeWithText("No snapshots yet.").assertIsDisplayed()
    }

    @Test fun CFG_04_takingASnapshotListsItAndRestoringItTellsTheAppToReload() {
        var reloads = 0
        show { reloads++ }
        rule.onNodeWithText("Take a snapshot now").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Snapshot taken.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, service.snapshots.list().size)
        rule.onNodeWithText("Manual").assertIsDisplayed()
        rule.onNode(hasText("Restore ", substring = true)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Restored. The state before the restore is kept as the newest snapshot.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("the restore called the reload hook once", 1, reloads)
        assertTrue("a restore is itself undoable: it took a snapshot first", service.snapshots.list().size >= 2)
    }

    @Test fun CFG_02_aPendingWidgetWithoutAProviderExplainsItselfAndCanBeDismissed() {
        service.extras.write(service.extras.read().copy(widgets = listOf(PendingWidget("corner.TopLeft", "Clock", ""))))
        show()
        rule.onNodeWithText("Widgets to add again").assertIsDisplayed()
        rule.onAllNodesWithText("Clock")[0].performClick()
        rule.onNodeWithText("Add it again from the widget picker. This setup was restored without the widget's app.").assertIsDisplayed()
        rule.onNodeWithText("Dismiss").performClick()
        rule.waitForIdle()
        assertTrue(service.pendingWidgets().isEmpty())
        assertEquals(0, rule.onAllNodesWithText("Widgets to add again").fetchSemanticsNodes().size)
    }

    @Test fun CFG_02_aWidgetThatRanInAWorkProfileIsLabelledAsSo() {
        service.extras.write(service.extras.read().copy(widgets = listOf(
            PendingWidget("corner.TopRight", "Mail", "com.mail/.W", work = true),
            PendingWidget("leftPage", "Notes", "com.notes/.W", work = false),
        )))
        show()
        rule.onNode(hasText("work profile", substring = true)).assertIsDisplayed()
        assertEquals("only the work widget carries the label", 1, rule.onAllNodesWithText("work profile", substring = true).fetchSemanticsNodes().size)
    }

    @Test fun CFG_02_aWidgetWhoseAppIsNotInstalledSaysSoInsteadOfDoingNothing() {
        service.extras.write(service.extras.read().copy(widgets = listOf(PendingWidget("corner.TopLeft", "Ghost", "com.ghost/.W"))))
        show()
        rule.onAllNodesWithText("Ghost")[0].performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Ghost: the widget's app is not installed here yet.").fetchSemanticsNodes().isNotEmpty() }
        assertFalse(service.pendingWidgets().isEmpty()) // still listed, nothing was lost
    }
}
