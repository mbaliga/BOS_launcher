package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bos.sphere.core.catalog.HomeEdit
import com.bos.sphere.core.catalog.HomeState
import com.bos.sphere.core.catalog.ItemDef
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind
import com.bos.sphere.core.catalog.WidgetSpan
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** HOM-01 / INP-01: the live band items (strip items and widget cells) are nodes of the focus graph and can take focus. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeBandFocusTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_01_bandItemsJoinTheFocusGraphAndTakeFocus() {
        val cap: (com.bos.sphere.core.data.PageLayout) -> Int = { 12 }
        val w = ItemKeys.widget(9)
        var s = HomeState(pages = listOf(HomePage(listOf("a", "b"), id = 1)))
        s = HomeEdit.add(s, ItemDef(w, ItemKind.Widget, "W"), 0, cap, WidgetSpan(2, 2))
        s = HomeEdit.add(s, ItemDef(ItemKeys.link("https://x.org"), ItemKind.Link, "x"), 0, cap)
        s = HomeEdit.createFolder(s, listOf("a", "b"), "f", "F", cap)
        val menus = mutableListOf<String>()
        val e = HomeExtras(s.copy(pages = emptyList()), { null }, { null }, { _, m -> Box(m) }, onLongPress = { menus += it })
        val (items, widgets) = bandKeys(e, s.topLevelKeys(), HomeStyle.List, 411, 891)
        assertEquals(listOf(w), widgets)
        assertTrue(items.isNotEmpty())
        rule.setContent { InputModeProvider { ScrollExtrasBand(e, items, widgets, 411f, 891f) } }
        rule.waitForIdle()
        val ids = LauncherInput.focus.nodes().map { it.id }.toSet()
        for (k in items + widgets) assertTrue("band.$k registered in $ids", "band.$k" in ids)
        for (k in items + widgets) {
            assertTrue(LauncherInput.focus.request("band.$k")); rule.waitForIdle()
            assertEquals("band.$k", LauncherInput.focus.currentId)
        }
    }
}
