package com.bos.sphere

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.bos.sphere.feature.grid.PageEditorGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 36 (pageEditor): the live page's card frame with the swing added; plain JVM (no composition needed). */
class EditCardStateW36Test {
    private val layout = PageEditorGeometry.layout(1080f, 2340f, 2.625f, 1f, false, 63f, 63f)
    private fun state(reduced: Boolean = false) = EditCardState(mutableFloatStateOf(1f), layout, reduced, continuous = false)

    @Test fun HOM_12_pe40_atRestTheFrameIsExactlyTheCardTransform() {
        val s = state()
        val f = s.frame()
        assertEquals(layout.target.scale, f.scale, 0f)
        assertEquals(layout.target.translationX, f.translationX, 0f)
        assertEquals(0f, f.rotationX, 0f); assertEquals(0f, f.rotationY, 0f); assertEquals(1f, f.layerAlpha, 0f)
    }

    @Test fun HOM_12_pe41_midSwingTheLivePageIsTiltedAndOffsetFromTheSlotAndAtTheEndItIsBackAtRest() {
        val s = state()
        Snapshot.withMutableSnapshot { s.swingDx.floatValue = 0f; s.swingDy.floatValue = 700f; s.swingProgress.floatValue = 0.3f }
        val mid = s.frame()
        assertTrue(mid.translationY > layout.target.translationY + 100f)
        assertTrue(mid.rotationX != 0f)
        assertTrue(mid.scale < layout.target.scale)
        Snapshot.withMutableSnapshot { s.swingProgress.floatValue = 1f }
        val end = s.frame()
        assertEquals(layout.target.translationY, end.translationY, 0f)
        assertEquals(layout.target.scale, end.scale, 0f)
        assertEquals(0f, end.rotationX, 0f)
    }

    @Test fun HOM_12_pe42_reducedMotionOnlyFadesTheLivePage() {
        val s = state(reduced = true)
        Snapshot.withMutableSnapshot { s.swingDy.floatValue = 700f; s.swingProgress.floatValue = 0.5f }
        val f = s.frame()
        assertEquals(layout.target.translationY, f.translationY, 0f)
        assertEquals(0f, f.rotationX, 0f)
        assertEquals(0.5f, f.layerAlpha, 0.0001f)
    }

    @Test fun HOM_12_pe43_theGlanceFocusMovesTheLivePageOutOfTheWayAndBackWhenLeft() {
        val s = state()
        Snapshot.withMutableSnapshot { s.glanceFocus.value = true }
        assertTrue("off screen, so no touch reaches it", s.frame().translationX > 50_000f)
        Snapshot.withMutableSnapshot { s.glanceFocus.value = false }
        assertEquals(layout.target.translationX, s.frame().translationX, 0f)
    }
}
