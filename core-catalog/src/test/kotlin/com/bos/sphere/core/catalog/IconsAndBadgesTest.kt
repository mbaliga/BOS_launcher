package com.bos.sphere.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconsAndBadgesTest {
    private val xml = """
        <resources>
          <iconback img1="back"/>
          <item component="ComponentInfo{com.a/com.a.Main}" drawable="a_icon" />
          <item drawable="b_icon" component="ComponentInfo{com.b/.Home}"/>
          <item component="ComponentInfo{com.a/com.a.Main}" drawable="dup"/>
          <item component=":CALENDAR_ICON" drawable="cal"/>
        </resources>"""

    @Test
    fun HOM_03_appfilterParses() {
        val m = IconPackMap.parse("pack", xml)
        assertEquals(2, m.size)
        assertEquals("a_icon", m.drawableFor("com.a/com.a.Main"))
        assertEquals("b_icon", m.drawableFor("com.b", "com.b.Home"))
        assertNull(m.drawableFor("com.c/x"))
    }

    @Test
    fun HOM_03_precedenceOverrideThenPackThenThemedThenOwn() {
        val pack = IconPackMap.parse("pack", xml)
        val c = "com.a/com.a.Main"
        assertEquals(IconSource.Override("pack:x"), IconPolicy.resolve("k", c, mapOf("k" to "pack:x"), pack, true, true, true))
        assertEquals(IconSource.Pack("pack", "a_icon"), IconPolicy.resolve("k", c, emptyMap(), pack, true, true, true))
        assertEquals(IconSource.Themed, IconPolicy.resolve("k", "q/q", emptyMap(), pack, true, true, true))
        assertEquals(IconSource.Adaptive, IconPolicy.resolve("k", "q/q", emptyMap(), null, true, false, true))
        assertEquals(IconSource.Legacy, IconPolicy.resolve("k", "q/q", emptyMap(), null, false, true, false))
    }

    @Test
    fun HOM_03_overrideCodecRoundTrip() {
        val m = mapOf("a#0" to "pack:p/x", "b#0" to "color:ff00ff")
        assertEquals(m, IconOverrideCodec.decode(IconOverrideCodec.encode(m)))
    }

    @Test
    fun HOM_03_legacyPlateIsNeverDark() {
        for (avg in listOf(0xFF000000.toInt(), 0xFF101820.toInt(), 0xFF2244AA.toInt(), 0xFFFFFFFF.toInt(), 0xFFEEDD00.toInt(), 0xFF800000.toInt())) {
            val plate = IconPlate.forLegacy(avg)
            val r = (plate shr 16 and 0xFF) / 255f; val g = (plate shr 8 and 0xFF) / 255f; val b = (plate and 0xFF) / 255f
            assertTrue("plate for ${Integer.toHexString(avg)} too dark", 0.2126f * r + 0.7152f * g + 0.0722f * b >= IconPlate.MIN_LUMINANCE)
        }
    }

    @Test
    fun HOM_03_themedIconKeepsContrast() {
        for (dark in listOf(true, false)) for (accent in listOf(0xFF3366FF.toInt(), 0xFFFFCC00.toInt(), 0xFF22AA66.toInt())) {
            val (fg, bg) = IconPlate.themed(accent, dark)
            assertTrue(IconPlate.contrast(fg, bg) >= 4.5f)
        }
    }

    @Test
    fun HOM_09_badgeCountsUnreadNonOngoingPerPackageAndProfile() {
        val b = Badges.compute(listOf(
            NoteRef("a"), NoteRef("a"), NoteRef("a", unread = false), NoteRef("a", ongoing = true),
            NoteRef("a", profileId = 10), NoteRef("b"),
        ))
        assertEquals(2, b[Badges.keyFor("a", 0)]!!.count)
        assertEquals(1, b[Badges.keyFor("a", 10)]!!.count)
        assertEquals(1, b[Badges.keyFor("b", 0)]!!.count)
    }

    @Test
    fun HOM_09_badgeLabelDotOrCountAndFolderSum() {
        assertEquals("", Badge(5).label(showCount = false))
        assertEquals("5", Badge(5).label(true))
        assertEquals("99+", Badge(150).label(true))
        val f = Folder("f", "F", listOf("x", "y", "z"))
        val by = mapOf("x" to Badge(2), "y" to Badge(3))
        assertEquals(5, Badges.forFolder(f) { by[it] }!!.count)
        assertNull(Badges.forFolder(f) { null })
    }

    @Test
    fun HOM_10_diffCoversInstallUpdateUninstallSuspendDisable() {
        val a = AppRecord("a", "pa", "A")
        val b = AppRecord("b", "pb", "B")
        val c = AppRecord("c", "pc", "C")
        val old = listOf(a, b, c)
        val new = listOf(a.copy(versionStamp = 2), b.copy(suspended = true), AppRecord("d", "pd", "D"))
        val d = PackageReconcile.diff(old, new)
        assertEquals(listOf("d"), d.added.map { it.key })
        assertEquals(listOf("c"), d.removed.map { it.key })
        assertEquals(listOf("a", "b"), d.changed.map { it.key })
        assertEquals(setOf("a", "b", "c"), d.iconKeysToEvict())
        assertTrue(PackageReconcile.diff(old, old).isEmpty)
        assertTrue(!PackageReconcile.launchable(new[1]))
    }

    @Test
    fun HOM_10_uninstallAppliedToArrangementLeavesNoGap() {
        val cap: (com.bos.sphere.core.data.PageLayout) -> Int = { 4 }
        val s = HomeState(pages = listOf(com.bos.sphere.core.data.HomePage(listOf("a", "b", "c", "d"), id = 1L)))
        val d = PackageReconcile.diff(listOf("a", "b", "c", "d").map { AppRecord(it, "p$it", it) }, listOf("a", "c", "d").map { AppRecord(it, "p$it", it) })
        val next = PackageReconcile.applyToState(s, d, cap)
        assertEquals(listOf("a", "c", "d"), next.pages[0].keys)
        val shown = PackageReconcile.visible(next, listOf("a", "c", "d").map { AppRecord(it, "p$it", it) }, cap)
        assertEquals(listOf("a", "c", "d"), shown.flatMap { it.keys })
    }

    @Test
    fun HOM_10_burstOfEventsRefreshesWithinOneSecond() {
        val c = RefreshCoalescer(250)
        val due = c.onEvent(1000)
        for (t in 1010L..1200L step 10) assertTrue(c.onEvent(t) <= 1250)
        assertTrue(due - 1000 < 1000)
        assertTrue(!c.due(1100))
        assertTrue(c.due(1250))
        assertTrue(!c.due(1300))
    }
}
