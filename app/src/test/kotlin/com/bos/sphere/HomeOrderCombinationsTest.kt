package com.bos.sphere

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.test.onRoot
import com.bos.sphere.core.data.HomeOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOM-23 (wave 26): the home order is independent of the app language. The four combinations (left to right / right to left order x an LTR / RTL
 * language) put the Launchpad handle, its edge swipe and the Recents panel on the SAME physical edges per ORDER, whatever the language says.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class HomeOrderCombinationsTest : TourBase() {
    // The Arabic qualifier changes the JVM default locale; a later test class in the same JVM must not inherit it.
    @org.junit.After fun restoreLocale() { java.util.Locale.setDefault(java.util.Locale.US) }

    private fun start(order: HomeOrder) {
        AppSmokeHarness.seedApps("Alpha", "Bravo")
        AppSmokeHarness.configureFresh { it.copy(homeOrder = order) }
        launch()
    }

    private fun launchpadHandleLeft(): Float = rule.onNodeWithTag("glance.handle.left", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
    private fun rootWidth(): Float = rule.onRoot().fetchSemanticsNode().boundsInRoot.right

    private fun check(order: HomeOrder) {
        start(order)
        val w = rootWidth()
        val onRight = launchpadHandleLeft() > w / 2f
        assertEquals("Launchpad handle edge for $order", order.rtl, onRight)
        // an edge swipe from the Launchpad's edge opens Launchpad; from the other edge the Recents panel
        val lpEdgeIsRight = order.rtl
        if (lpEdgeIsRight) swipeFromRight() else swipeFromLeft()
        assertTrue("Launchpad opens from the ${if (lpEdgeIsRight) "right" else "left"} edge", launchpadShown()); back(); settle()
        if (lpEdgeIsRight) swipeFromLeft() else swipeFromRight()
        assertTrue("Recents opens from the other edge", recentsShown())
    }

    @Test fun HOM_23_leftToRightOrderWithAnLtrLanguage() = check(HomeOrder.LeftToRight)

    @Test @Config(qualifiers = "ar-ldrtl-w360dp-h720dp-xhdpi")
    fun HOM_23_leftToRightOrderWithAnRtlLanguageStaysLeftToRight() = check(HomeOrder.LeftToRight)

    @Test fun HOM_23_rightToLeftOrderWithAnLtrLanguage() = check(HomeOrder.RightToLeft)

    @Test @Config(qualifiers = "ar-ldrtl-w360dp-h720dp-xhdpi")
    fun HOM_23_rightToLeftOrderWithAnRtlLanguage() = check(HomeOrder.RightToLeft)
}
