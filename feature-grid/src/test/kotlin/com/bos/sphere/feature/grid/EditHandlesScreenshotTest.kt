package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.bos.sphere.core.catalog.HomeState
import com.bos.sphere.core.catalog.ItemDef
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind
import com.bos.sphere.core.catalog.WidgetSpan
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Wave 45 (editHandles) HOM-07 / HOM-12: renders the edit selection states of the live grid page to `build/screenshots/` (read them). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditHandlesScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private val labels = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot")
    private val apps = labels.mapIndexed { i, n ->
        AppEntry("demo.p$i", ComponentName("demo.p$i", "demo.p$i.Main"), n, Process.myUserHandle()) { ColorDrawable(listOf(0xFFE5484D, 0xFF30A46C, 0xFF3E63DD, 0xFFF5A524, 0xFF8E4EC6, 0xFF12A594)[i].toInt()) }
    }
    private val byKey = apps.associateBy { it.key }
    private val w = ItemKeys.widget(5)
    private val sel = EditSelectionState()
    private fun build(keys: List<String>) = HomeExtras(
        HomeState(
            pages = listOf(HomePage(keys, id = 1L)),
            items = mapOf(w to ItemDef(w, ItemKind.Widget, "Weather", "p/.W")),
            widgets = mapOf(w to WidgetSpan(2, 2)),
        ),
        { byKey[it] }, { null },
        widget = { _, m ->
            Box(m.testTag("widget-content").background(Color(0xFF2B3350)), contentAlignment = Alignment.Center) { Text("Weather 21°", color = Color.White) }
        },
        selection = sel,
    )
    private var extras = build(listOf(w) + apps.map { it.key })

    @Composable private fun Scene() {
        val prefs = LauncherPrefs()
        val state = rememberGridHomeState()
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0D1A))) {
            GridHome(
                pages = extras.state.pages, appsByKey = byKey, prefs = prefs, isLandscape = false, state = state,
                gesturesBound = setOf(HomeGesture.PinchIn), capacityOf = { it.capacity(false, prefs) },
                onLaunch = {}, onAppLongPress = {}, onPagesChange = {}, onEmptyLongPress = {}, onGesture = {}, onClockTap = {}, onTileLongPress = { _, _ -> },
                extras = extras, editMode = true,
            )
        }
    }

    private fun settle() { rule.runOnUiThread { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }; rule.mainClock.advanceTimeBy(400) }

    private fun shoot(name: String): Bitmap {
        settle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out); c.drawColor(0xFF0B0D1A.toInt()); c.drawBitmap(bmp, 0f, 0f, null)
        File(outDir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return out
    }

    private fun begin() { rule.mainClock.autoAdvance = false; rule.setContent { Scene() }; rule.mainClock.advanceTimeBy(500) }
    private fun widgetCentre() = rule.onNodeWithTag("widget-content").fetchSemanticsNode().boundsInRoot.center
    private fun selectWidget() { val c = widgetCentre(); rule.onRoot().performTouchInput { down(c); advanceEventTime(40); up() }; settle() }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_screenshot_widgetSelected() {
        begin(); selectWidget()
        assertTrue(shoot("45-edit-widget-selected").width > 0)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_07_screenshot_midResizeWithGhostAndChip() {
        begin(); selectWidget()
        val h = rule.onNodeWithTag("resize-handle-SouthEast", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput {
            down(h)
            repeat(5) { advanceEventTime(30); moveTo(Offset(h.x + (it + 1) * 40f, h.y + (it + 1) * 30f)) }
        }
        shoot("45-edit-mid-resize-ghost-chip")
        rule.onRoot().performTouchInput { up() }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_07_screenshot_midResizeIntoFreeCells() {
        extras = build(listOf(w, apps[0].key))
        begin(); selectWidget()
        val h = rule.onNodeWithTag("resize-handle-SouthEast", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput {
            down(h)
            repeat(5) { advanceEventTime(30); moveTo(Offset(h.x + 2f, h.y + (it + 1) * 40f)) }
        }
        shoot("45-edit-mid-resize-free")
        rule.onRoot().performTouchInput { up() }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_screenshot_iconDragPushesOthersAside() {
        begin()
        val from = rule.onNodeWithContentDescription("Alpha").fetchSemanticsNode().boundsInRoot.center
        val to = rule.onNodeWithContentDescription("Delta").fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput { down(from); advanceEventTime(30); moveTo(Offset(from.x + 40f, from.y + 40f)); advanceEventTime(30); moveTo(Offset(to.x + 20f, to.y + 90f)); advanceEventTime(30) }
        shoot("45-edit-icon-drag-push-aside")
        rule.onRoot().performTouchInput { up() }
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_12_screenshot_landscapeWidgetSelected() {
        begin(); selectWidget()
        shoot("45-edit-widget-selected-landscape")
    }
}
