package com.bos.sphere.core.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 47 (editIntegrate), HOM-07 / HOM-12: the audit that keeps the home editor ONE editor (source level: no second selection, no leftovers). */
class EditIntegrateW47AuditTest {
    private fun text(path: String) = Repo.file(path).readText()
    private val grid = "feature-grid/src/main/kotlin/com/bos/sphere/feature/grid"
    private val home get() = text("app/src/main/kotlin/com/bos/sphere/HomeScreen.kt")

    @Test fun HOM_12_thereIsOneSelectionModelAndNoSecondHolder() {
        assertFalse("HomeItemSelection is gone", Repo.file("$grid/ItemSheetModel.kt").readText().contains("class HomeItemSelection"))
        val all = listOf("$grid/EditSelection.kt", "$grid/ItemSheetModel.kt", "app/src/main/kotlin/com/bos/sphere/ItemEditHost.kt").joinToString("\n") { text(it) }
        assertFalse(all.contains("HomeItemSelection"))
        assertTrue("the one state implements the bar's source", text("$grid/EditSelection.kt").contains("class EditSelectionState : ItemSelectionSource"))
        assertTrue("the home shares one state with the host and the bar", home.contains("rememberItemEditState(editSelection)") && home.contains("selection = editSelection"))
    }

    @Test fun HOM_12_theFallbackRemoveButtonIsOffAndTheBarDrawsNoSecondOutline() {
        assertTrue(home.contains("it.fallbackBar = false"))
        assertTrue("the bar's own outline is off by default", text("$grid/ItemActionBar.kt").contains("showOutline: Boolean = false"))
    }

    @Test fun HOM_07_noResizeModeAndNoInWidgetDoneAreLeft() {
        val host = text("app/src/main/kotlin/com/bos/sphere/HomeItemsHost.kt")
        assertFalse("the old resizing mode", host.contains("var resizing") || host.contains("resizing = resizing") || host.contains("onResizeDone"))
        assertFalse(text("$grid/HomeExtrasUi.kt").contains("resizing"))
        assertTrue("Resize focuses the handles", host.contains("selection.focusResize()"))
    }

    @Test fun HOM_12_theRemoveBadgesAndTheFourPillBarAreGone() {
        assertFalse(Repo.file("$grid/HomeEditHandle.kt").exists())
        assertFalse(Repo.file("$grid/HomeEditBar.kt").exists())
        listOf("GridHome.kt", "ColumnScrollHome.kt").forEach { assertFalse("$it still draws a badge", text("$grid/$it").contains("EditHandleBox")) }
    }

    @Test fun HOM_12_theActionBarReadsTheMeasuredChromeInsetsNotFixedReserves() {
        assertTrue(home.contains("actionBarReserveDp("))
        assertFalse("the fixed 64 dp / 72 dp reserves are gone", home.contains("reserveTop = 64.dp"))
    }

    @Test fun HOM_12_thePageEditorUsesTheOneBarAndReservesNothingAtTheFoot() {
        assertTrue(home.contains("EditTopBar(") && home.contains("Editing pages"))
        assertTrue(text("$grid/PageEditorGeometry.kt").contains("const val BOTTOM_BAR_DP = 0f"))
        assertTrue("Back in the page editor is Cancel", home.contains("BackHandler { chromeState.requestCancel(homeEdit.hasChanges)"))
    }

    @Test fun HOM_12_theGhostsFeedTheGridDotsAndTheDotsLayerPublishesItsCoordinates() {
        assertTrue(text("$grid/WidgetEditUi.kt").contains("LocalEditGrid.current") && text("$grid/WidgetEditUi.kt").contains("grid.update("))
        assertTrue(text("$grid/GridHome.kt").contains("editGridRef.value?.update("))
        assertTrue(text("$grid/EditGridDotsLayer.kt").contains("state?.layer = it"))
    }

    @Test fun HOM_12_hapticsOfThePageEditorGoThroughTheOneHelper() {
        assertFalse(text("$grid/PageEditorStrip.kt").contains("LocalHapticFeedback"))
        assertEquals(1, Regex("rememberLauncherHaptics\\(\\)").findAll(text("$grid/PageEditorStrip.kt")).count().coerceAtMost(1))
    }
}
