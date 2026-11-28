package com.bos.sphere.feature.desktop

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.design.SphereTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** HOM-13: resizing a hosted widget on the real desktop surface: the handle and the size menu, persisted per display. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesktopWidgetResizeUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val identity = "res:1920x1080:hom13-resize"
    private val controller get() = DesktopController.get(ApplicationProvider.getApplicationContext())

    private class FakeHost(val limits: ResizeLimits? = null) : DesktopWidgetHost {
        @Composable override fun Content(appWidgetId: Int, modifier: Modifier) { Text("fake widget $appWidgetId", modifier) }
        @Composable override fun rememberAdder(onAdded: (Int, Float, Float) -> Unit): () -> Unit = {}
        override fun isBound(appWidgetId: Int) = true
        override fun release(appWidgetId: Int) {}
        override fun resizeLimits(appWidgetId: Int, density: Float) = limits
    }

    @Before fun seed() {
        controller.profiles.save(identity, DesktopProfile(items = listOf(
            PlacedItem(PlacedItem.KIND_WIDGET, "aw:1", 40f, 120f, 160f, 160f),
            PlacedItem(PlacedItem.KIND_WIDGET, "aw:2", 900f, 120f, 160f, 160f),
        )))
    }

    @After fun clear() { DesktopWidgetSlot.provider = null; controller.profiles.save(identity, DesktopProfile()) }

    private fun show() {
        rule.setContent { SphereTheme { DesktopSurface(controller, { 2 }, identity, "Monitor") } }
        rule.waitForIdle()
    }

    private fun saved(id: String) = DesktopWidgets.widgetItem(controller.profiles.load(identity), id)!!

    @Test @Config(sdk = [35], qualifiers = "w1920dp-h1080dp-land-mdpi")
    fun HOM_13_draggingTheHandleResizesTheWidgetAndKeepsItInTheProfile() {
        DesktopWidgetSlot.provider = FakeHost()
        show()
        val before = saved("aw:1")
        rule.onNodeWithTag(resizeHandleTag("aw:1")).performTouchInput { swipe(center, center + Offset(240f, 80f), 300) }
        rule.waitForIdle()
        val after = saved("aw:1")
        assertTrue("wider: ${before.w} -> ${after.w}", after.w > before.w)
        assertTrue("taller: ${before.h} -> ${after.h}", after.h > before.h)
        assertEquals(before.x, after.x, 0.01f); assertEquals(before.y, after.y, 0.01f)
        assertFalse(after.rect().overlaps(saved("aw:2").rect()))
    }

    @Test @Config(sdk = [35], qualifiers = "w1920dp-h1080dp-land-mdpi")
    fun HOM_13_draggingTheHandleTowardsANeighbourStopsBeforeItAndKeepsTheOtherWidgetPut() {
        DesktopWidgetSlot.provider = FakeHost()
        show()
        val other = saved("aw:2")
        rule.onNodeWithTag(resizeHandleTag("aw:1")).performTouchInput { swipe(center, center + Offset(1500f, 0f), 300) }
        rule.waitForIdle()
        val a = saved("aw:1")
        assertFalse(a.rect().overlaps(other.rect()))
        assertTrue(a.rect().right <= other.x + 0.01f)
        assertEquals(other, saved("aw:2"))
    }

    @Test @Config(sdk = [35], qualifiers = "w1920dp-h1080dp-land-mdpi")
    fun HOM_13_aProviderThatCannotBeResizedGetsNoHandle() {
        DesktopWidgetSlot.provider = FakeHost(ResizeLimits(0f, 0f, horizontal = false, vertical = false))
        show()
        rule.onNodeWithTag(resizeHandleTag("aw:1")).assertDoesNotExistCompat()
    }

    @Test @Config(sdk = [35], qualifiers = "w1920dp-h1080dp-land-mdpi")
    fun HOM_13_theSizeMenuWidensTheWidgetByOneCell() {
        DesktopWidgetSlot.provider = FakeHost()
        show()
        val before = saved("aw:1")
        openMenuOf("aw:1")
        rule.onNodeWithText("Wider").performClick()
        rule.waitForIdle()
        assertTrue(saved("aw:1").w > before.w)
    }

    private fun openMenuOf(id: String) {
        rule.onNodeWithTag(moveHandleTag(id)).performTouchInput { longClick() }
        rule.waitForIdle()
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() {
    org.junit.Assert.assertTrue(runCatching { assertExists() }.isFailure)
}
