package com.bos.sphere

import com.bos.sphere.feature.grid.CardSide
import com.bos.sphere.feature.grid.EditCardGeometry
import com.bos.sphere.ui.CardEdge
import com.bos.sphere.ui.LaunchpadCardGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 24 (owner design): the home shrinks to a card on the right with the notifications behind it on the left (Launchpad), and
 * the edit card is its mirror on the other side. The two geometries live in different modules (feature-launchpad and
 * feature-grid); this ties them together so neither can drift to the same side as the other.
 */
class CardMirrorW24Test {
    private val sizes = listOf(1080f to 2400f, 2400f to 1080f, 720f to 1280f)

    @Test fun STR_01_theLaunchpadCardRestsOnTheRightAndTheNotificationsAreOnTheLeftInLeftToRight() {
        sizes.forEach { (w, h) ->
            val g = LaunchpadCardGeometry(w, h, CardEdge.forLaunchpad(rtl = false), 2.75f)
            val card = g.visibleCardBounds(1f)
            assertTrue("$w x $h: only a sliver at the right edge (${card.left}..${card.right})", card.left > w * 0.85f && card.right >= w - 1f)
            assertTrue("the notifications' content (0..${g.contentWidthPx}) ends where the card's sliver starts", g.contentWidthPx <= card.left + 1f)
        }
    }

    @Test fun STR_01_theEditCardMirrorsTheLaunchpadCardOnTheOtherSideInBothDirections() {
        for (rtl in listOf(false, true)) sizes.forEach { (w, h) ->
            val edit = EditCardGeometry.target(w, h, rtl)
            val lp = LaunchpadCardGeometry(w, h, CardEdge.forLaunchpad(rtl), 2.75f).cardBounds(1f)
            val editCentre = (edit.card.left + edit.card.right) / 2f
            val lpCentre = (lp.left + lp.right) / 2f
            if (!rtl) {
                assertEquals(CardSide.Left, edit.side); assertTrue(editCentre < w / 2f); assertTrue(lpCentre > w / 2f)
            } else {
                assertEquals(CardSide.Right, edit.side); assertTrue(editCentre > w / 2f); assertTrue(lpCentre < w / 2f)
            }
            // The edge the shared card enum names for edit is the side the real edit geometry uses.
            assertEquals(CardEdge.forEdit(rtl).name, edit.side.name)
        }
    }
}
