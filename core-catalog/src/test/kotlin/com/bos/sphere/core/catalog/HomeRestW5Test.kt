package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomeLayoutMath
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.PageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 5, widgets and stacks on a real arrangement: HOM-07 (restore and rebind), HOM-11 (class-keyed layouts), HOM-14. */
class HomeRestW5Test {
    private val cap: (PageLayout) -> Int = { 12 }
    private fun w(i: Int) = ItemKeys.widget(i)
    private fun layout(vararg keys: String) = HomeLayout(listOf(HomePage(keys.toList(), PageLayout(), 1)), seeded = true)

    private fun withWidgets(vararg ids: Int, dock: List<String> = emptyList()): HomeState = HomeState(
        pages = listOf(HomePage(ids.map { w(it) }, PageLayout(), 1)),
        items = ids.associate { w(it) to ItemDef(w(it), ItemKind.Widget, "W$it", "com.p/.Provider$it") },
        widgets = ids.associate { w(it) to WidgetSpan(2, 1) },
        dock = dock,
    )

    // ---- HOM-07: a rebound widget keeps every place that points at it ----------------------------------

    @Test fun HOM_07_aReboundWidgetPinnedToTheRailKeepsItsPin() {
        val s = withWidgets(5, 6, dock = listOf("pin:app:a", "pin:widget:${w(5)}"))
        val after = HomeRekey.widget(s, w(5), w(50))
        assertEquals(listOf("pin:app:a", "pin:widget:${w(50)}"), after.dock)
    }

    @Test fun HOM_07_aPlainWidgetKeyOnTheDockIsRekeyedToo() {
        val after = HomeRekey.widget(withWidgets(5, dock = listOf(w(5))), w(5), w(9))
        assertEquals(listOf(w(9)), after.dock)
    }

    @Test fun HOM_07_aReboundStackMemberKeepsTheStackItsActiveMemberAndItsPage() {
        var s = withWidgets(1, 2, 3)
        s = WidgetStacks.create(s, w(1), w(2), "s")
        s = WidgetStacks.setActive(s, WidgetStacks.stackKey("s"), WidgetStacks.stackAt(s, WidgetStacks.stackKey("s"))!!.select(0))
        val after = HomeRekey.widget(s, w(1), w(11))
        val st = WidgetStacks.stackAt(after, WidgetStacks.stackKey("s"))!!
        assertEquals(listOf(w(11), w(2)), st.keys)
        assertEquals(0, st.active)
        assertEquals(setOf(w(11), w(2)), WidgetStacks.members(after))
        assertTrue(WidgetStacks.stackKey("s") in after.pages.flatMap { it.keys })
        assertTrue(after.items.containsKey(w(11)) && !after.items.containsKey(w(1)))
    }

    @Test fun HOM_07_everyStackMemberIsPlannedForRestoreAndTheStackItselfIsNot() {
        var s = withWidgets(1, 2)
        s = WidgetStacks.create(s, w(1), w(2), "s")
        val plan = WidgetStartup.plan(s, hostedIds = setOf(2), installedProviders = setOf("com.p/.Provider1", "com.p/.Provider2"))
        assertEquals(setOf(w(1), w(2)), plan.map { it.widget.key }.toSet())
        assertTrue(plan.single { it.widget.key == w(1) } is RestoreAction.Rebind)
        assertTrue(plan.single { it.widget.key == w(2) } is RestoreAction.Keep)
    }

    @Test fun HOM_07_aWidgetWhoseProviderIsGoneStaysAPlaceholderInItsSlotNotRemoved() {
        val s = withWidgets(1, 2)
        val plan = WidgetStartup.plan(s, hostedIds = emptySet(), installedProviders = setOf("com.p/.Provider2"))
        assertTrue(plan.single { it.widget.key == w(1) } is RestoreAction.Placeholder)
        // nothing in the plan removes it: the state is untouched by planning
        assertEquals(listOf(w(1), w(2)), s.pages.flatMap { it.keys })
    }

    // ---- HOM-11: stacks are part of the per-class arrangement ---------------------------------------------

    @Test fun HOM_11_aStackMadeInOneClassShowsOnceInAnotherClassAndItsMembersNeverShowAlone() {
        val s = WidgetStacks.create(withWidgets(1, 2, 3), w(1), w(2), "s")
        val stack = WidgetStacks.stackKey("s")
        // the other class still has the old per-widget arrangement
        val other = layout(w(1), w(2), w(3))
        val shown = HomeLayoutMath.visualPages(HomeResolve.layoutFor(other, s), HomeResolve.available(emptyList(), s), cap).flatMap { it.keys }
        assertEquals(listOf(w(3), stack).sorted(), shown.sorted())
        assertEquals(1, shown.count { it == stack })
    }

    @Test fun HOM_11_aStackThatWasDissolvedElsewhereLeavesNoGhostInAnotherClass() {
        val s = withWidgets(1, 2) // the stack no longer exists
        val stale = layout(w(1), WidgetStacks.stackKey("gone"), w(2))
        val shown = HomeLayoutMath.visualPages(HomeResolve.layoutFor(stale, s), HomeResolve.available(emptyList(), s), cap).flatMap { it.keys }
        assertEquals(listOf(w(1), w(2)), shown)
    }

    @Test fun HOM_11_aWidgetTakenOutOfAStackInOneClassIsPlacedOnceInTheOther() {
        var s = WidgetStacks.create(withWidgets(1, 2, 3), w(1), w(2), "s")
        s = WidgetStacks.takeOut(s, WidgetStacks.stackKey("s"), w(1), 0, cap)
        val other = layout(w(3), WidgetStacks.stackKey("s"))
        val shown = HomeLayoutMath.visualPages(HomeResolve.layoutFor(other, s), HomeResolve.available(emptyList(), s), cap).flatMap { it.keys }
        // the stack dissolved into w2; w1 is a page widget again; nothing is shown twice or lost
        assertEquals(listOf(w(1), w(2), w(3)), shown.sorted())
    }
}
