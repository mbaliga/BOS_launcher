package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.catalog.Folder
import com.bos.sphere.core.catalog.HomeState
import com.bos.sphere.core.catalog.ItemDef
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind
import com.bos.sphere.core.catalog.WidgetSpan
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HomeStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** HOM-02 / HOM-04 / HOM-07 / HOM-08: renders the new home items (tiles, resize frame, the band on the scrolling homes) to `build/screenshots/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeItemsScreenshotTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private fun shoot(name: String): Bitmap {
        rule.mainClock.advanceTimeBy(400)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(0xFF1B2A55.toInt())
        c.drawBitmap(bmp, 0f, 0f, null)
        File(outDir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return out
    }

    private fun app(p: String, color: Int) = AppEntry(p, ComponentName(p, "$p.Main"), p, Process.myUserHandle()) { ColorDrawable(color) }

    private val a1 = app("com.one", Color.RED)
    private val a2 = app("com.two", Color.GREEN)
    private val w = ItemKeys.widget(11)
    private val link = ItemKeys.link("https://example.org")
    private val state = HomeState(
        folders = mapOf("f" to Folder("f", "Work", listOf(a1.key, a2.key))),
        items = mapOf(
            link to ItemDef(link, ItemKind.Link, "example.org", "https://example.org"),
            ItemKeys.shortcut("com.one", "s", 0) to ItemDef(ItemKeys.shortcut("com.one", "s", 0), ItemKind.Shortcut, "Compose", "s"),
            w to ItemDef(w, ItemKind.Widget, "Weather", "p/.W"),
        ),
        widgets = mapOf(w to WidgetSpan(2, 1)),
    )
    private fun extras(resizing: String? = null) = HomeExtras(
        state, { k -> listOf(a1, a2).firstOrNull { it.key == k } }, { null },
        widget = { _, m -> WidgetPlaceholder("Weather", {}, m) }, resizing = resizing,
    )

    @Test @Config(sdk = [35], qualifiers = "w411dp-h300dp-port-xhdpi")
    fun HOM_02_tiles() {
        rule.mainClock.autoAdvance = false
        val e = extras()
        rule.setContent {
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtraItemFace(ItemKeys.folder("f"), e, 64.dp)
                ExtraItemFace(link, e, 64.dp)
                ExtraItemFace(ItemKeys.shortcut("com.one", "s", 0), e, 64.dp)
                Box(Modifier.size(140.dp, 64.dp)) { HomeWidgetCell(w, e, 100f, 100f, Modifier.size(140.dp, 64.dp)) }
            }
        }
        val bmp = shoot("40-home-items-tiles")
        assertTrue(bmp.width > 0)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h300dp-port-xhdpi")
    fun HOM_07_resizeFrame() {
        rule.mainClock.autoAdvance = false
        val e = extras(resizing = w)
        rule.setContent { Box(Modifier.fillMaxSize().padding(24.dp)) { HomeWidgetCell(w, e, 100f, 100f, Modifier.size(220.dp, 110.dp)) } }
        shoot("41-widget-resize-frame")
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-xhdpi")
    fun HOM_08_bandOnScrollingHomes() {
        rule.mainClock.autoAdvance = false
        val e = extras()
        val top = listOf(ItemKeys.folder("f"), link, w)
        val (items, widgets) = bandKeys(e, top, HomeStyle.List, 360, 640)
        rule.setContent { ScrollExtrasBand(e, items, widgets, 360f, 640f) }
        shoot("42-widget-band-list")
    }
}
