package com.bos.sphere.ui

import android.content.Context
import androidx.compose.runtime.snapshots.Snapshot
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Wave 52 (panelWidgets, STR-15): the Widgets section's model: the tab order, the list operations, the saved text and the hub state around them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PanelWidgetsModelTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    @After fun reset() { ctx.getSharedPreferences("launchpad_hub", 0).edit().clear().commit(); CommsOptions.setWidgetsSection(false); CommsOptions.setWidgetsSlot(WidgetsSlot.Last) }

    private fun w(vararg ids: Int) = ids.map { PanelWidget(it, "w$it") }

    @Test fun STR_15_withTheSwitchOffThePanelHasTheOldFourSectionsInTheOldOrder() {
        assertEquals(listOf(HubView.All, HubView.Unread, HubView.Files, HubView.Timeline), PanelSections.order(false, WidgetsSlot.Last))
        assertEquals(listOf(HubView.All, HubView.Unread, HubView.Files, HubView.Timeline), HubState(HubPrefs(null), null, true).tabs)
        assertFalse(HubView.Widgets in HubState(HubPrefs(null), null, true).views)
    }

    @Test fun STR_15_withTheSwitchOnWidgetsIsTheFifthSectionByDefaultOrFirstOrBeforeFiles() {
        assertEquals(PanelSections.BASE + HubView.Widgets, PanelSections.order(true, WidgetsSlot.Last))
        assertEquals(HubView.Widgets, PanelSections.order(true, WidgetsSlot.Last)[4])
        assertEquals(listOf(HubView.Widgets) + PanelSections.BASE, PanelSections.order(true, WidgetsSlot.First))
        assertEquals(listOf(HubView.All, HubView.Unread, HubView.Widgets, HubView.Files, HubView.Timeline), PanelSections.order(true, WidgetsSlot.BeforeFiles))
        assertEquals(WidgetsSlot.Last, WidgetsSlot.fromId(null)); assertEquals(WidgetsSlot.Last, WidgetsSlot.fromId("garbage"))
    }

    @Test fun STR_15_theSwitchAndTheSlotArePersistedAndDefaultToOffAndLast() {
        val p = HubPrefs.of(ctx)
        assertFalse(p.widgetsSection); assertEquals(WidgetsSlot.Last, p.widgetsSlot)
        p.widgetsSection = true; p.widgetsSlot = WidgetsSlot.BeforeFiles
        val again = HubPrefs.of(ctx)
        assertTrue(again.widgetsSection); assertEquals(WidgetsSlot.BeforeFiles, again.widgetsSlot)
        val hub = HubState(again, null, true)
        assertEquals(PanelSections.order(true, WidgetsSlot.BeforeFiles), hub.tabs)
    }

    @Test fun STR_15_theHubFollowsTheSettingWhileOpenAndLeavesTheSectionWhenItIsTurnedOff() {
        val hub = HubState(HubPrefs(null), null, true)
        Snapshot.withMutableSnapshot { hub.applyWidgets(true, WidgetsSlot.Last); hub.select(HubView.Widgets) }
        assertEquals(HubView.Widgets, hub.view)
        assertNull("Widgets is the last section", hub.step(+1))
        assertEquals(HubView.Timeline, hub.step(-1))
        Snapshot.withMutableSnapshot { hub.applyWidgets(false, WidgetsSlot.Last) }
        assertEquals(HubView.All, hub.view)
        Snapshot.withMutableSnapshot { hub.select(HubView.Widgets) }
        assertEquals("a view that does not exist cannot be selected", HubView.All, hub.view)
    }

    @Test fun STR_15_theSectionSurvivesProcessDeathLikeAnyTab() {
        val hub = HubState(HubPrefs(null), null, true)
        Snapshot.withMutableSnapshot { hub.applyWidgets(true, WidgetsSlot.Last); hub.select(HubView.Widgets) }
        val fresh = HubState(HubPrefs(null), null, true)
        Snapshot.withMutableSnapshot { fresh.applyWidgets(true, WidgetsSlot.Last); fresh.restoreState(hub.savedState()) }
        assertEquals(HubView.Widgets, fresh.view)
    }

    @Test fun STR_15_aPanelLinkToWidgetsLandsThereOnlyWhenTheSectionExists() {
        assertEquals(HubView.Widgets, HubView.forPanel("widgets"))
        val hub = HubState(HubPrefs(null), null, true)
        Snapshot.withMutableSnapshot { hub.select(HubView.forPanel("widgets")) }
        assertEquals(HubView.All, hub.view)
    }

    @Test fun STR_15_theDefaultViewChoiceOffersWidgetsOnlyWhileTheSectionExists() {
        CommsOptions.setWidgetsSection(false)
        assertFalse(HubView.Widgets in CommsOptions.selectableViews())
        CommsOptions.setWidgetsSection(true)
        assertTrue(HubView.Widgets in CommsOptions.selectableViews())
    }

    @Test fun STR_15_addRemoveAndMoveKeepTheListOrderedAndWithoutDuplicates() {
        var l = w(1, 2, 3)
        assertEquals(l, PanelWidgetList.add(l, PanelWidget(2, "again")))
        l = PanelWidgetList.add(l, PanelWidget(4, "w4")); assertEquals(listOf(1, 2, 3, 4), l.map { it.appWidgetId })
        assertEquals(listOf(2, 1, 3, 4), PanelWidgetList.move(l, 2, -1).map { it.appWidgetId })
        assertEquals("the first cannot move up", l, PanelWidgetList.move(l, 1, -1))
        assertEquals("the last cannot move down", l, PanelWidgetList.move(l, 4, +1))
        assertFalse(PanelWidgetList.canMove(l, 1, -1)); assertTrue(PanelWidgetList.canMove(l, 1, +1)); assertFalse(PanelWidgetList.canMove(l, 9, +1))
        assertEquals(listOf(1, 3, 4), PanelWidgetList.remove(l, 2).map { it.appWidgetId })
    }

    @Test fun STR_15_theSavedListRoundTripsAwkwardLabelsAndSkipsDamagedLines() {
        val l = listOf(PanelWidget(7, "Clock, 100%\nsecond line"), PanelWidget(8, ""), PanelWidget(9, "Wetter ü"))
        assertEquals(l, PanelWidgetList.decode(PanelWidgetList.encode(l)))
        assertEquals(listOf(PanelWidget(3, "ok")), PanelWidgetList.decode("junk\n,x\n-4,neg\n3,ok\n3,dup"))
        assertEquals(emptyList<PanelWidget>(), PanelWidgetList.decode(null))
    }

    @Test fun STR_15_theListIsStoredInThePrefsAndComesBackAfterARestart() {
        val a = PanelWidgetStore(HubPrefs.of(ctx))
        a.add(PanelWidget(11, "Weather")); a.add(PanelWidget(12, "Notes")); a.move(12, -1)
        val b = PanelWidgetStore(HubPrefs.of(ctx))
        assertEquals(listOf(12, 11), b.widgets.map { it.appWidgetId })
        b.remove(12)
        assertEquals(listOf(11), PanelWidgetStore(HubPrefs.of(ctx)).widgets.map { it.appWidgetId })
    }

    @Test fun STR_15_aCardIsAsHighAsTheProviderMinimumWithinTheResizeLimitsAndTheCaps() {
        assertEquals(PanelWidgetSizing.MIN_DP, PanelWidgetSizing.heightDp(20f, 0f, 0f), 0f)
        assertEquals(110f, PanelWidgetSizing.heightDp(110f, 0f, 0f), 0f)
        assertEquals(140f, PanelWidgetSizing.heightDp(110f, 140f, 0f), 0f)
        assertEquals(PanelWidgetSizing.MAX_DP, PanelWidgetSizing.heightDp(900f, 0f, 0f), 0f)
        assertEquals("the provider's own maximum wins over the minimum", 200f, PanelWidgetSizing.heightDp(250f, 0f, 200f), 0f)
    }
}
