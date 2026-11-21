package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.WidgetIdSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** NFR-18 / HOM-07: a home arrangement stripped of its widget ids still lists every widget, and startup rebinds them. */
class WidgetMarkerRebindTest {
    private fun state(): HomeState {
        val a = ItemDef(ItemKeys.widget(12), ItemKind.Widget, "Clock", "com.x/.ClockWidget")
        val b = ItemDef(ItemKeys.widget(13), ItemKind.Widget, "Weather", "com.y/.WeatherWidget")
        val stackKey = WidgetStacks.stackKey("s1")
        val stack = ItemDef(stackKey, ItemKind.Widget, "Stack", WidgetStacks.encode(WidgetStack(listOf(a.key, b.key), 0)))
        return HomeState(
            pages = listOf(HomePage(keys = listOf(stackKey, "app:z"), id = 1L)),
            items = linkedMapOf(a.key to a, b.key to b, stackKey to stack),
            widgets = mapOf(a.key to WidgetSpan(2, 1), b.key to WidgetSpan(2, 2), stackKey to WidgetSpan(2, 2)),
            dock = listOf(a.key),
        )
    }

    @Test fun NFR_18_theEncodedArrangementHasNoWidgetIdAndStillDecodesWithEveryWidgetSpanAndStackMember() {
        val clean = WidgetIdSplit.sanitize(HomeStateCodec.encode(state()))
        assertFalse(WidgetIdSplit.hasIds(clean))
        val back = HomeStateCodec.decode(clean)
        assertEquals(setOf("widget:-13", "widget:-14", WidgetStacks.stackKey("s1")), back.items.keys)
        assertEquals(WidgetSpan(2, 2), back.widgets["widget:-14"])
        assertEquals(listOf("widget:-13", "widget:-14"), WidgetStacks.stackAt(back, WidgetStacks.stackKey("s1"))!!.keys)
        assertEquals(listOf("widget:-13"), back.dock)
    }

    @Test fun HOM_07_aMarkedWidgetIsNeverHostedSoStartupPlansARebindFromItsProviderAndAnUninstalledOneKeepsItsSlot() {
        val back = HomeStateCodec.decode(WidgetIdSplit.sanitize(HomeStateCodec.encode(state())))
        val saved = WidgetStartup.saved(back)
        assertTrue(saved.all { it.appWidgetId < 0 && WidgetIdSplit.isMarker(it.key) })
        val plan = WidgetStartup.plan(back, hostedIds = emptySet(), installedProviders = setOf("com.x/.ClockWidget"))
        assertTrue(plan.single { it.widget.provider == "com.x/.ClockWidget" } is RestoreAction.Rebind)
        assertTrue(plan.single { it.widget.provider == "com.y/.WeatherWidget" } is RestoreAction.Placeholder)
    }
}
