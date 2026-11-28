package com.bos.sphere.feature.dock

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Wave 60 (owner: "I want the default dock to have fully rounded ends, like a capsule"): the Standard dock's surface (DockRail.kt) and its
 * Settings-card preview (DockStyleDraw.kt) both use `RoundedCornerShape(50)`, a PERCENT corner size. Unlike a fixed-dp radius (which is only a
 * capsule by coincidence at one particular bar height), a percent corner always radii at min(width, height) / 2 for every corner, so the short
 * ends of the bar are true semicircles at whatever height the bar actually measures, including while it grows for the magnify effect's headroom.
 */
class DockCapsuleW60Test {
    private val density = Density(1f)

    private fun assertCapsule(w: Float, h: Float) {
        val shape = RoundedCornerShape(50)
        val expected = minOf(w, h) / 2f
        val size = Size(w, h)
        listOf(shape.topStart, shape.topEnd, shape.bottomStart, shape.bottomEnd).forEach { corner ->
            assertEquals("corner radius at ${w}x$h", expected, corner.toPx(size, density), 0.01f)
        }
    }

    @Test fun DCK_04_capsule_wideBar_radiusIsHalfTheHeight() {
        assertCapsule(320f, 64f) // a typical resting dock: wide and short
        assertCapsule(600f, 72f) // a long row of pinned apps, magnified-row headroom included
    }

    @Test fun DCK_04_capsule_squareOrTallBar_radiusIsHalfTheShortSide() {
        assertCapsule(80f, 80f) // a square card (never happens for the real dock, but the shape must stay correct)
        assertCapsule(56f, 180f) // a side dock (left/right edge): vertical bar, short side is width
    }
}
