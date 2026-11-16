package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HomePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-10 and HOM-14: folders and the dock on package removal, and widget stacks that keep their own active member. */
class MemberReconcileTest {
    private val cap: (com.bos.sphere.core.data.PageLayout) -> Int = { 12 }
    private val day = 24L * 60 * 60 * 1000

    private fun home(): HomeState {
        val s = HomeState(pages = listOf(HomePage(listOf("a", "b", "c", "d"), id = 1L)), dock = listOf("pin:app:p", "pin:app:q", "pin:folder:f1"))
        return HomeEdit.createFolder(s, listOf("b", "c", "d"), "f1", "F", cap)
    }

    @Test fun HOM_10_aMissingFolderMemberIsUnavailableFirstAndKept() {
        val s = home()
        val r = MemberReconcile.reconcile(s, setOf("a", "b", "d", "p", "q"), emptyMap(), 1_000L, cap)
        assertEquals(s.folders, r.state.folders)
        assertEquals(setOf("c"), r.unavailable)
        assertEquals(mapOf("c" to 1_000L), r.missingSince)
        assertTrue(r.removed.isEmpty())
    }

    @Test fun HOM_10_afterTheGraceTheMemberLeavesTheFolderAndThePageHasNoGap() {
        val s = home()
        val inv = setOf("a", "b", "d", "p", "q")
        val r = MemberReconcile.reconcile(s, inv, mapOf("c" to 0L), MemberReconcile.GRACE_MS, cap)
        assertEquals(setOf("c"), r.removed)
        assertEquals(listOf("b", "d"), r.state.folders.getValue("f1").keys)
        assertTrue(r.unavailable.isEmpty() && r.missingSince.isEmpty())
        assertEquals(listOf("a", ItemKeys.folder("f1")), r.state.pages.flatMap { it.keys }.filter { it == "a" || it == ItemKeys.folder("f1") })
    }

    @Test fun HOM_10_aFolderLeftWithOneItemDissolvesAndAnEmptyOneGoes() {
        val s = home()
        val r = MemberReconcile.reconcile(s, setOf("a", "d", "p", "q"), mapOf("b" to 0L, "c" to 0L), 8 * day, cap)
        assertTrue(r.state.folders.isEmpty())
        assertTrue("d" in r.state.pages.flatMap { it.keys })
        assertTrue("pin:folder:f1" !in r.state.dock)
        val all = MemberReconcile.reconcile(s, setOf("a", "p", "q"), mapOf("b" to 0L, "c" to 0L, "d" to 0L), 8 * day, cap)
        assertTrue(all.state.folders.isEmpty() && ItemKeys.folder("f1") !in all.state.pages.flatMap { it.keys })
    }

    @Test fun HOM_10_aMissingDockPinIsKeptThenRemovedAfterTheGrace() {
        val s = home()
        val inv = setOf("a", "b", "c", "d", "p")
        val first = MemberReconcile.reconcile(s, inv, emptyMap(), 5L, cap)
        assertEquals(s.dock, first.state.dock)
        assertEquals(setOf("q"), first.unavailable)
        val later = MemberReconcile.reconcile(first.state, inv, first.missingSince, 5L + MemberReconcile.GRACE_MS, cap)
        assertEquals(listOf("pin:app:p", "pin:folder:f1"), later.state.dock)
        assertEquals(setOf("q"), later.removed)
    }

    @Test fun HOM_10_aPackageThatComesBackWithinTheGraceKeepsItsPlace() {
        val s = home()
        val gone = MemberReconcile.reconcile(s, setOf("a", "b", "d", "p", "q"), emptyMap(), 10L, cap)
        val back = MemberReconcile.reconcile(gone.state, setOf("a", "b", "c", "d", "p", "q"), gone.missingSince, 10L + 2 * day, cap)
        assertTrue(back.missingSince.isEmpty() && back.unavailable.isEmpty() && back.removed.isEmpty())
        assertEquals(s, back.state)
    }

    @Test fun HOM_10_anEmptyInventoryNeverWipesAnything() {
        val s = home()
        val r = MemberReconcile.reconcile(s, emptySet(), mapOf("c" to 0L), 99 * day, cap)
        assertEquals(s, r.state)
        assertTrue(r.removed.isEmpty())
    }

    @Test fun HOM_10_pageAppsThatAreNotInFoldersOrTheDockAreNotJudgedHere() {
        val s = home()
        val r = MemberReconcile.reconcile(s, setOf("b", "c", "d", "p", "q"), emptyMap(), 99 * day, cap)
        assertTrue("a" in r.state.pages.flatMap { it.keys })
    }

    @Test fun HOM_10_theMissingSinceMapRoundTripsAsText() {
        val m = mapOf("com.x/.A#0" to 5L, "k2" to 9L)
        assertEquals(m, MemberReconcile.decode(MemberReconcile.encode(m)))
        assertTrue(MemberReconcile.decode("garbage\nbad\tnum").isEmpty())
    }

    @Test fun HOM_10_aTimestampFromTheFutureIsTreatedAsNow() {
        val s = home()
        val r = MemberReconcile.reconcile(s, setOf("a", "b", "d", "p", "q"), mapOf("c" to 9_999_999L), 100L, cap)
        assertEquals(100L, r.missingSince["c"])
    }

    // ---- HOM-14: each stack keeps its own active member -------------------------------------

    private fun widgets(): HomeState {
        val w = { i: Int -> ItemKeys.widget(i) }
        val pages = listOf(HomePage(listOf(w(1), w(2), w(3)), id = 1L), HomePage(listOf(w(4), w(5), w(6)), id = 2L))
        val base = HomeState(pages = pages, widgets = (1..6).associate { w(it) to WidgetSpan(2, 1) })
        val withItems = base.copy(items = (1..6).associate { w(it) to ItemDef(w(it), ItemKind.Widget, "W$it") })
        val s1 = WidgetStacks.create(withItems, w(1), w(2), "s1")
        return WidgetStacks.create(s1, w(4), w(5), "s2")
    }

    @Test fun HOM_14_eachStackPositionKeepsItsOwnActiveMemberAcrossPages() {
        var s = widgets()
        val a = WidgetStacks.stackKey("s1"); val b = WidgetStacks.stackKey("s2")
        s = WidgetStacks.setActive(s, a, WidgetStacks.stackAt(s, a)!!.select(0))
        assertEquals(0, WidgetStacks.stackAt(s, a)!!.active)
        assertEquals(1, WidgetStacks.stackAt(s, b)!!.active) // the other stack on the other page did not move
        s = WidgetStacks.setActive(s, b, WidgetStacks.stackAt(s, b)!!.previous())
        assertEquals(0, WidgetStacks.stackAt(s, a)!!.active)
        assertEquals(0, WidgetStacks.stackAt(s, b)!!.active)
    }

    @Test fun HOM_14_theActiveMemberSurvivesMovingTheStackToAnotherPageAndAReload() {
        var s = widgets()
        val a = WidgetStacks.stackKey("s1")
        s = WidgetStacks.setActive(s, a, WidgetStacks.stackAt(s, a)!!.select(0))
        s = s.copy(pages = com.bos.sphere.core.data.HomeLayoutMath.move(s.pages, a, 1, 0, cap))
        assertEquals(1, com.bos.sphere.core.data.HomeLayoutMath.pageOf(s.pages, a))
        val round = HomeStateCodec.decode(HomeStateCodec.encode(s))
        assertEquals(0, WidgetStacks.stackAt(round, a)!!.active)
        assertEquals(1, WidgetStacks.stackAt(round, WidgetStacks.stackKey("s2"))!!.active)
    }
}
