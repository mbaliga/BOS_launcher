package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.Corner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

class ComplicationLayoutTest {
    private val radius = 200f
    private val item = 60f
    private val side = radius + item

    @Test fun anglesAreEvenAndInset() {
        assertEquals(listOf(45.0), ComplicationArc.angles(1))
        assertTrue(ComplicationArc.angles(0).isEmpty())
        for (n in 2..4) {
            val a = ComplicationArc.angles(n)
            assertEquals(n, a.size)
            assertEquals(ComplicationArc.EDGE_INSET_DEG, a.first(), 1e-9)
            assertEquals(90.0 - ComplicationArc.EDGE_INSET_DEG, a.last(), 1e-9)
            val steps = a.zipWithNext { x, y -> y - x }
            steps.forEach { assertEquals(steps.first(), it, 1e-9) }
        }
    }

    @Test fun centersStayInBoxAndOnArc() {
        for (anchor in Corner.entries) for (n in 1..4) {
            val (cx, cy) = ComplicationArc.cornerPoint(anchor, side)
            val pts = ComplicationArc.centers(n, anchor, radius, item)
            assertEquals(n, pts.size)
            pts.forEach { p ->
                assertTrue("$anchor $p", p.x >= item / 2 - 1e-3 && p.x <= side - item / 2 + 1e-3)
                assertTrue("$anchor $p", p.y >= item / 2 - 1e-3 && p.y <= side - item / 2 + 1e-3)
                assertEquals("$anchor $p", radius, hypot(p.x - cx, p.y - cy), 1f)
            }
        }
    }

    @Test fun singleSitsOnDiagonal() {
        for (anchor in Corner.entries) {
            val p = ComplicationArc.centers(1, anchor, radius, item).single()
            val (cx, cy) = ComplicationArc.cornerPoint(anchor, side)
            assertEquals(abs(p.x - cx), abs(p.y - cy), 1e-3f)
        }
    }

    @Test fun orderIsClockwiseAroundScreen() {
        val tl = ComplicationArc.centers(3, Corner.TopLeft, radius, item)
        assertTrue(tl.first().x < tl.last().x) // left edge -> top edge
        assertTrue(tl.first().y > tl.last().y)
        val tr = ComplicationArc.centers(3, Corner.TopRight, radius, item)
        assertTrue(tr.first().y < tr.last().y) // top edge -> right edge
        val br = ComplicationArc.centers(3, Corner.BottomRight, radius, item)
        assertTrue(br.first().x > br.last().x) // right edge -> bottom edge
        val bl = ComplicationArc.centers(3, Corner.BottomLeft, radius, item)
        assertTrue(bl.first().y > bl.last().y) // bottom edge -> left edge
    }

    @Test fun batteryFormatting() {
        val low = ComplicationFormat.battery(12, charging = false)
        assertEquals("12", low.text)
        assertEquals(0.12f, low.progress!!, 1e-6f)
        assertEquals(ComplicationColors.Amber, low.accent)
        assertEquals(ComplicationColors.Charging, ComplicationFormat.battery(12, charging = true).accent)
        assertNull(ComplicationFormat.battery(-1, false).progress)
    }

    @Test fun clockAndAlarmFormatting() {
        val t = LocalDateTime.of(2026, 11, 4, 15, 7)
        assertEquals("15:07", ComplicationFormat.clockText(t, true, Locale.US))
        assertEquals("3:07", ComplicationFormat.clockText(t, false, Locale.US))
        assertEquals("PM", ComplicationFormat.time(t, false, Locale.US).caption)
        assertEquals("—", ComplicationFormat.nextAlarm(null, t, false, Locale.US).text)
        assertEquals("ALARM", ComplicationFormat.nextAlarm(t.plusHours(2), t, true, Locale.US).caption)
        assertEquals("SUN", ComplicationFormat.nextAlarm(t.plusDays(4), t, true, Locale.US).caption)
    }

    @Test fun dateAndStorageFormatting() {
        val d = ComplicationFormat.date(LocalDate.of(2026, 11, 4), Locale.US)
        assertEquals("4", d.text)
        assertEquals("WED", d.caption)
        val s = ComplicationFormat.storage(100L, 25L)
        assertEquals("75%", s.text)
        assertEquals(0.75f, s.progress!!, 1e-6f)
    }
}
