package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.os.Parcel
import android.os.Process
import android.os.UserHandle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import com.bos.sphere.core.catalog.WorkDrawerRules
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.WheelGrouping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * HOM-06, wave 48 (workProfile): the work-profile pause that the flat drawer's Work tab carried is back in the disc app drawer. The disc has a
 * "Work" scope label while a work profile exists; with it on only the work apps are listed and a pill pauses or resumes the work profile
 * (the system's quiet mode); a paused work app is dimmed, marked and cannot start until resumed. Quiet mode itself is a system call: device-only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.Config(sdk = [35])
class WorkProfileW48Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun handle(id: Int): UserHandle {
        val p = Parcel.obtain(); p.writeInt(id); p.setDataPosition(0)
        return UserHandle.CREATOR.createFromParcel(p).also { p.recycle() }
    }
    private val workUser = handle(10)
    private fun app(pkg: String, label: String, user: UserHandle) = AppEntry(pkg, ComponentName(pkg, "M"), label, user) { ColorDrawable(0xFF2F6BFF.toInt()) }
    private val personal = listOf(app("p.a", "Alpha", Process.myUserHandle()), app("p.b", "Bravo", Process.myUserHandle()))
    private val work = listOf(app("w.m", "Mail work", workUser), app("w.c", "Chat work", workUser))
    private val all = personal + work
    private val workIds = setOf(workUser.hashCode())

    private fun show(w: WheelWork?, launched: MutableList<String> = mutableListOf(), apps: List<AppEntry> = all) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                WheelDrawer(true, Corner.BottomRight, apps, WheelGrouping.Letter, {}, emptyList(), { launched += it.label }, {}, {}, {}, work = w, modifier = Modifier.fillMaxSize())
            }
        }
        rule.mainClock.advanceTimeBy(2_000)
    }

    private fun present(label: String) = rule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()

    // ---- the pure rules

    @Test fun HOM_06_workScopeListsOnlyWorkAppsAndOnlyWhileAWorkProfileExists() {
        assertEquals(all, WorkDrawerRules.scoped(all, workIds, on = false, workPaused = false))
        assertEquals(work, WorkDrawerRules.scoped(all, workIds, on = true, workPaused = false))
        assertEquals("paused work apps stay listed so they can be resumed", work, WorkDrawerRules.scoped(all, workIds, on = true, workPaused = true))
        assertEquals("a stored 'on' without a work profile shows everything", all, WorkDrawerRules.scoped(all, workIds, on = true, workPaused = null))
        assertFalse(WorkDrawerRules.effective(true, null))
    }

    @Test fun HOM_06_pausePillLabelFollowsTheState() {
        assertEquals("Pause work apps", WorkDrawerRules.pillLabel(true, false))
        assertEquals("Resume work apps", WorkDrawerRules.pillLabel(true, true))
        assertNull("no pill with the scope off", WorkDrawerRules.pillLabel(false, true))
        assertNull("no pill without a work profile", WorkDrawerRules.pillLabel(true, null))
        assertTrue(WorkDrawerRules.paused(work[0], setOf(workUser.hashCode())))
        assertFalse(WorkDrawerRules.paused(personal[0], setOf(workUser.hashCode())))
    }

    // ---- the disc

    @Test fun HOM_06_withoutAWorkProfileTheDiscHasNoWorkLabelAndNoPill() {
        show(null)
        assertFalse(present("Work"))
        assertFalse(present("Pause work apps")); assertFalse(present("Resume work apps"))
        assertTrue(present("Alpha"))
    }

    @Test fun HOM_06_theWorkLabelIsReachableFromTheDiscAndSwitchesTheScope() {
        val seen = mutableListOf<Boolean>()
        show(WheelWork(false, false, workIds, emptySet(), { seen += it }, {}))
        rule.onNodeWithContentDescription("Work").assertIsNotSelected()
        assertTrue("both kinds of apps are listed with the scope off", present("Alpha") && present("Mail work"))
        assertFalse("no pill with the scope off", present("Pause work apps"))
        rule.onNodeWithContentDescription("Work").performClick()
        assertEquals(listOf(true), seen)
    }

    @Test fun HOM_06_withTheScopeOnOnlyWorkAppsShowAndThePillPausesTheProfile() {
        var toggled = 0
        show(WheelWork(true, false, workIds, emptySet(), {}, { toggled++ }))
        rule.onNodeWithContentDescription("Work").assertIsSelected()
        assertTrue(present("Mail work") && present("Chat work"))
        assertFalse("personal apps are out of the work scope", present("Alpha"))
        rule.onNodeWithText("Pause work apps").performClick()
        assertEquals(1, toggled)
    }

    @Test fun HOM_06_pausedWorkAppsAreDimmedMarkedAndNotLaunchableAndThePillResumes() {
        var toggled = 0
        val launched = mutableListOf<String>()
        show(WheelWork(true, true, workIds, setOf(workUser.hashCode()), {}, { toggled++ }), launched)
        rule.onNodeWithText("Resume work apps").performClick()
        assertEquals(1, toggled)
        assertTrue("the paused app says so to TalkBack", present("Mail work, paused"))
        rule.onNodeWithContentDescription("Mail work, paused").performClick()
        assertTrue("a paused work app does not start", launched.isEmpty())
    }

    @Test fun HOM_06_resumedWorkAppsLaunchNormally() {
        val launched = mutableListOf<String>()
        show(WheelWork(true, false, workIds, emptySet(), {}, {}), launched)
        rule.onNodeWithContentDescription("Mail work").performClick()
        assertEquals(listOf("Mail work"), launched)
    }
}
