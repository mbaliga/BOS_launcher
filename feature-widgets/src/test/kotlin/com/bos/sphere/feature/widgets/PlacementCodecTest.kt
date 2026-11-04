package com.bos.sphere.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacementCodecTest {

    @Test
    fun roundTripsEveryField() {
        val list = listOf(
            placement(7, WidgetSurface.Equator, slot = 3, hubSize = HubSize.Small, serial = 0L),
            placement(
                12, WidgetSurface.Hub, slot = 0, hubSize = HubSize.Large,
                pkg = "com.work.mail", cls = "com.work.mail.widget.Inbox\$Provider", serial = 10L,
            ),
        )
        assertEquals(list, PlacementCodec.decode(PlacementCodec.encode(list)))
    }

    @Test
    fun emptyAndNullDecodeToNothing() {
        assertTrue(PlacementCodec.decode(null).isEmpty())
        assertTrue(PlacementCodec.decode("").isEmpty())
        assertEquals("", PlacementCodec.encode(emptyList()))
    }

    @Test
    fun skipsMalformedLinesButKeepsGoodOnes() {
        val good = PlacementCodec.encode(listOf(placement(5, slot = 2)))
        val text = listOf(
            "garbage",
            "2;9;a;b;0;Equator;0;Small", // unknown format version
            "1;x;a;b;0;Equator;0;Small", // bad id
            "1;0;a;b;0;Equator;0;Small", // INVALID_APPWIDGET_ID
            "1;9;a;b;0;Sideways;0;Small", // unknown surface
            "1;9;a;b;0;Equator;0;Huge", // unknown size
            "1;9;;b;0;Equator;0;Small", // blank package
            good,
        ).joinToString("\n")
        assertEquals(listOf(placement(5, slot = 2)), PlacementCodec.decode(text))
    }

    @Test
    fun unreadableLinesSurviveARewriteAndReserveTheirIds() {
        val future = "2;9;a;b;0;Equator;0;Small;newField" // e.g. written by a newer version
        val damaged = "1;11;a;b;0;Sideways;0;Small"
        val text = listOf(future, PlacementCodec.encode(listOf(placement(5))), "garbage", damaged).joinToString("\n")
        val decoded = PlacementCodec.decodeAll(text)
        assertEquals(listOf(placement(5)), decoded.placements)
        assertEquals(listOf(future, "garbage", damaged), decoded.unparsed)
        assertEquals(setOf(9, 11), decoded.reservedIds)
        // A rewrite (here: a widget added) keeps them verbatim.
        val rewritten = PlacementCodec.encode(decoded.placements + placement(6), decoded.unparsed)
        val again = PlacementCodec.decodeAll(rewritten)
        assertEquals(listOf(placement(5), placement(6)), again.placements)
        assertEquals(decoded.unparsed, again.unparsed)
        assertEquals(setOf(9, 11), again.reservedIds)
    }

    @Test
    fun restorePendingIdsRoundTrip() {
        val list = listOf(placement(-5, slot = 1), placement(6))
        assertEquals(list, PlacementCodec.decode(PlacementCodec.encode(list)))
        assertTrue(PlacementCodec.decode(PlacementCodec.encode(list)).first().isRestorePending)
    }

    @Test
    fun duplicateIdsKeepTheFirst() {
        val text = PlacementCodec.encode(listOf(placement(4, slot = 1))) + "\n" +
            PlacementCodec.encode(listOf(placement(4, slot = 9)))
        assertEquals(listOf(placement(4, slot = 1)), PlacementCodec.decode(text))
    }

    @Test
    fun negativeSlotsClampToZero() {
        assertEquals(0, PlacementCodec.decode("1;3;a;b;0;Hub;-4;Small").single().slot)
    }

    @Test
    fun unencodablePlacementsAreNotWritten() {
        val bad = placement(8, cls = "a;b")
        assertEquals(listOf(placement(2)), PlacementCodec.decode(PlacementCodec.encode(listOf(bad, placement(2)))))
    }
}
