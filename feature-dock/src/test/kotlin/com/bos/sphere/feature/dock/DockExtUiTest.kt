package com.bos.sphere.feature.dock

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.launch.AppGroup
import com.bos.sphere.core.launch.GroupLayout
import com.bos.sphere.core.launch.LaunchRequest
import com.bos.sphere.core.launch.LaunchTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Receivers and surfaces of wave 2 that need an Android runtime: WSP-05 shelf, WSP-11 notification, WSP-12 intents, DCK-07 docks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DockExtUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private fun group(n: String) = AppGroup(n, listOf(LaunchRequest(LaunchTarget("a.p", null, 0)), LaunchRequest(LaunchTarget("b.p", null, 0))), GroupLayout.Tile, "g$n")

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(300)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(outDir, "$name.png").length() > 500)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h800dp-port-xhdpi")
    fun WSP_05_groupShelfDrawsOneTappableChipPerGroup() {
        var ran: String? = null
        rule.mainClock.autoAdvance = false
        rule.setContent { Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) { GroupShelf(listOf(group("Study"), group("Work")), { ran = it.name }) } }
        rule.onNodeWithContentDescription("Open group Study, 2 apps").performClickFor()
        assertEquals("Study", ran)
        shoot("43-group-shelf")
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performClickFor() { performClick() }

    @Test fun WSP_11_aDueScheduleBecomesANotificationWithAnOpenActionAndIsRemoved() {
        val mgr = WorkspaceRepository.get(context)
        val id = mgr.idFor("sc")
        mgr.addSchedule(ScheduledLaunch(id, "Open Study", System.currentTimeMillis() + 1000, RunTarget(RunKind.Group, "gStudy")))
        ScheduledLaunchReceiver().onReceive(context, Intent().putExtra(AutomationIntents.EXTRA_ID, id))
        val nm = context.getSystemService(NotificationManager::class.java)
        val n = Shadows.shadowOf(nm).allNotifications.single { it.extras.getString("android.title") == "Open Study" }
        assertEquals("Open", n.actions.single().title.toString())
        assertTrue(mgr.current.schedules.none { it.id == id })
        // the action runs the explicit RunActivity intent; nothing opens by itself
        assertNotNull(Shadows.shadowOf(n.actions.single().actionIntent).savedIntent.component)
    }

    @Test fun WSP_12_explicitIntentsSwitchWorkspacesByNameAndByStepWithoutAnActivity() {
        val mgr = WorkspaceRepository.get(context)
        val work = mgr.create("Auto-" + System.nanoTime())
        val name = mgr.current.workspaces.first { it.id == work }.name
        AutomationReceiver().onReceive(context, Intent(AutomationIntents.ACTION_SWITCH_WORKSPACE).putExtra(AutomationIntents.EXTRA_NAME, name))
        assertEquals(work, mgr.current.activeId)
        AutomationReceiver().onReceive(context, Intent(AutomationIntents.ACTION_RUN).putExtra(AutomationIntents.EXTRA_KIND, "Workspace").putExtra(AutomationIntents.EXTRA_ID, WorkspaceRefs.NEXT))
        assertTrue(mgr.current.activeId != work || mgr.current.workspaces.size == 1)
        mgr.switchTo(DockWorkspaceState.DEFAULT_ID)
    }

    @Test fun WSP_12_aGroupRequestFromABroadcastPostsTheOpenNotificationBecauseTheSystemBlocksBackgroundLaunches() {
        val mgr = WorkspaceRepository.get(context)
        val g = group("Auto" + System.nanoTime())
        mgr.saveGroup(g)
        AutomationReceiver().onReceive(context, Intent(AutomationIntents.ACTION_LAUNCH_GROUP).putExtra(AutomationIntents.EXTRA_NAME, g.name))
        val nm = context.getSystemService(NotificationManager::class.java)
        assertTrue(Shadows.shadowOf(nm).allNotifications.any { it.extras.getString("android.title") == "Open ${g.name}" })
    }

    @Test fun WSP_12_theExplicitActivityIntentIsWhatATaskerStyleToolSendsAndItIsExported() {
        val i = ScheduleNotificationsApi.runIntent(context, RunTarget(RunKind.Group, "g1"))
        assertEquals(AutomationIntents.ACTION_RUN, i.action)
        assertEquals(RunActivity::class.java.name, i.component!!.className)
        val parsed = AutomationIntents.parse(i.action, null, i.getStringExtra(AutomationIntents.EXTRA_KIND), i.getStringExtra(AutomationIntents.EXTRA_ID))
        assertEquals(RunTarget(RunKind.Group, "g1"), parsed)
        val info = context.packageManager.getActivityInfo(ComponentName(context, RunActivity::class.java), 0)
        assertTrue(info.exported)
    }

    private val app = AppEntry("a.p", ComponentName("a.p", "a.p.Main"), "Alpha", android.os.Process.myUserHandle()) { null }

    @Composable
    private fun Dock(displayId: Int, edge: DockEdge, align: Alignment) {
        val launcher = AppLauncher(context)
        Box(Modifier.fillMaxSize().padding(8.dp)) {
            DockHost(
                apps = listOf(app), launcher = launcher, controller = DockController(), refreshKey = 0, onLaunchApp = {}, onOpenPanel = {},
                modifier = { Modifier.align(align) }, displayId = displayId, forceEnabled = true, edgeOverride = edge,
            )
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w900dp-h500dp-land-xhdpi")
    fun DCK_07_twoDocksOnTwoDisplaysShowTheSamePinsAndBothDrawEvenWhereTheTierDefaultIsOff() {
        val mgr = WorkspaceRepository.get(context)
        mgr.setPins(listOf(DockItemRef.app(app.key)))
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Dock(0, DockEdge.Bottom, Alignment.BottomCenter)
                Dock(5, DockEdge.Left, Alignment.CenterStart)
            }
        }
        rule.mainClock.advanceTimeBy(300)
        val nodes = rule.onAllNodesWithContentDescriptionStartingWith("Alpha")
        assertEquals(2, nodes)
        shoot("44-two-docks")
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithContentDescriptionStartingWith(prefix: String): Int =
    onAllNodes(androidx.compose.ui.test.SemanticsMatcher("desc starts with $prefix") { n ->
        (if (n.config.contains(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)) n.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].any { d -> d.startsWith(prefix) } else false)
    }).fetchSemanticsNodes().size
