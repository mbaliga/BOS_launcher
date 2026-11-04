package com.bos.sphere.feature.widgets

import com.bos.sphere.feature.widgets.BindEffect.DeleteId
import com.bos.sphere.feature.widgets.BindEffect.LaunchBindPermission
import com.bos.sphere.feature.widgets.BindEffect.LaunchConfigure
import com.bos.sphere.feature.widgets.BindEffect.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BindFlowTest {

    private val request = AddRequest(
        providerPackage = "com.example",
        providerClass = "com.example.Provider",
        userSerial = 0L,
        target = PlacementTarget(WidgetSurface.Equator, equatorIndex = 4),
        hubSize = HubSize.Medium,
    )

    @Test
    fun allowedWithoutConfigurePlacesAtOnce() {
        val flow = BindFlow()
        assertEquals(listOf(Place(7, request)), flow.onAllocated(7, request, bindAllowed = true, needsConfigure = false))
        assertEquals(BindStep.Idle, flow.step)
        assertTrue(flow.pendingIds.isEmpty())
    }

    @Test
    fun allowedWithConfigureRunsItThenPlaces() {
        val flow = BindFlow()
        assertEquals(listOf(LaunchConfigure(7)), flow.onAllocated(7, request, true, needsConfigure = true))
        assertEquals(setOf(7), flow.pendingIds)
        assertEquals(listOf(Place(7, request)), flow.onConfigureResult(ok = true))
        assertEquals(BindStep.Idle, flow.step)
    }

    @Test
    fun configureCancelledDeletesTheId() {
        val flow = BindFlow()
        flow.onAllocated(7, request, true, needsConfigure = true)
        assertEquals(listOf(DeleteId(7, DeleteReason.Cancelled)), flow.onConfigureResult(ok = false))
    }

    @Test
    fun needsPermissionShowsThePromptThenPlaces() {
        val flow = BindFlow()
        assertEquals(listOf(LaunchBindPermission(7, request)), flow.onAllocated(7, request, false, false))
        assertEquals(setOf(7), flow.pendingIds)
        assertEquals(listOf(Place(7, request)), flow.onPermissionResult(granted = true))
    }

    @Test
    fun needsPermissionAndConfigure() {
        val flow = BindFlow()
        flow.onAllocated(7, request, bindAllowed = false, needsConfigure = true)
        assertEquals(listOf(LaunchConfigure(7)), flow.onPermissionResult(granted = true))
        assertEquals(listOf(Place(7, request)), flow.onConfigureResult(ok = true))
    }

    @Test
    fun permissionRefusedDeletesTheId() {
        val flow = BindFlow()
        flow.onAllocated(7, request, false, true)
        assertEquals(listOf(DeleteId(7, DeleteReason.Cancelled)), flow.onPermissionResult(granted = false))
        assertEquals(BindStep.Idle, flow.step)
    }

    @Test
    fun promptOrConfigureThatCannotStartDeletesTheId() {
        val a = BindFlow()
        a.onAllocated(7, request, false, false)
        assertEquals(listOf(DeleteId(7, DeleteReason.Failed)), a.onLaunchFailed())

        val b = BindFlow()
        b.onAllocated(8, request, true, true)
        assertEquals(listOf(DeleteId(8, DeleteReason.Failed)), b.onLaunchFailed())
        assertEquals(BindStep.Idle, b.step)
    }

    @Test
    fun bindErrorDeletesTheId() {
        assertEquals(listOf(DeleteId(7, DeleteReason.Failed)), BindFlow().onBindError(7))
    }

    @Test
    fun reconfigureNeverDeletes() {
        val flow = BindFlow()
        assertEquals(listOf(LaunchConfigure(3)), flow.startReconfigure(3))
        assertEquals(setOf(3), flow.pendingIds)
        assertTrue(flow.onConfigureResult(ok = false).isEmpty())

        flow.startReconfigure(3)
        assertTrue(flow.onConfigureResult(ok = true).isEmpty())

        flow.startReconfigure(3)
        assertTrue(flow.onLaunchFailed().isEmpty())
        assertEquals(BindStep.Idle, flow.step)
    }

    @Test
    fun startingAnotherFlowGivesBackThePendingId() {
        val flow = BindFlow()
        flow.onAllocated(7, request, false, false)
        assertEquals(
            listOf(DeleteId(7, DeleteReason.Superseded), Place(8, request)),
            flow.onAllocated(8, request, true, false),
        )
        flow.onAllocated(9, request, true, true)
        assertEquals(listOf(DeleteId(9, DeleteReason.Superseded), LaunchConfigure(2)), flow.startReconfigure(2))
    }

    @Test
    fun aPendingReconfigureIsNotDeletedWhenSuperseded() {
        val flow = BindFlow()
        flow.startReconfigure(2)
        assertEquals(listOf(Place(8, request)), flow.onAllocated(8, request, true, false))
    }

    @Test
    fun resultsNobodyWaitsForAreIgnored() {
        val flow = BindFlow()
        assertTrue(flow.onPermissionResult(true).isEmpty())
        assertTrue(flow.onConfigureResult(true).isEmpty())
        assertTrue(flow.onLaunchFailed().isEmpty())
        flow.onAllocated(7, request, false, false)
        assertTrue(flow.onConfigureResult(true).isEmpty()) // waiting on the prompt, not configure
        assertEquals(setOf(7), flow.pendingIds)
    }

    @Test
    fun requestBecomesAPlacementAtItsTarget() {
        val hub = request.copy(target = PlacementTarget(WidgetSurface.Hub, -1), hubSize = HubSize.Large)
        val p = hub.toPlacement(11)
        assertEquals(11, p.appWidgetId)
        assertEquals(WidgetSurface.Hub, p.surface)
        assertEquals(0, p.slot)
        assertEquals(HubSize.Large, p.hubSize)
        assertEquals(4, request.toPlacement(11).slot)
    }
}
