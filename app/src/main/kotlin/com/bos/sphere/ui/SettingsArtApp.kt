package com.bos.sphere.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.bos.sphere.core.data.CommsCategory
import com.bos.sphere.core.data.LaunchpadTheme
import com.bos.sphere.feature.grid.ArtSet
import com.bos.sphere.feature.grid.VisualArt
import com.bos.sphere.feature.grid.dot
import com.bos.sphere.feature.grid.dotIn
import com.bos.sphere.feature.grid.dp
import com.bos.sphere.feature.grid.inBox
import com.bos.sphere.feature.grid.line
import com.bos.sphere.feature.grid.phoneFrame
import com.bos.sphere.feature.grid.rect

/**
 * Wave 54 (settingsVisual): the illustrations of the settings whose values live in the app and Comms modules: the Comms theme, the place of the
 * Widgets tab and the Default view. Same rules as the feature-grid sets: one drawing per value, drawn from the ink, covered by a test.
 */
object SettingsArtApp {
    val commsTheme = ArtSet(LaunchpadTheme.entries) { commsThemeArt(it) }
    val widgetsSlot = ArtSet(WidgetsSlot.entries) { widgetsSlotArt(it) }
    /** Every Default view the setting can offer: each view, and All narrowed to each category. */
    val defaultView = ArtSet(DefaultViewChoice.options(HubView.entries)) { defaultViewArt(it) }

    private val LpDarkBg = Color(0xFF14161F)
    private val LpLightBg = Color(0xFFF4F5F9)

    private fun commsThemeArt(t: LaunchpadTheme): VisualArt = { ink ->
        val box = phoneFrame(ink)
        val (o, sz) = box
        val half = sz.width / 2f
        fun panel(bg: Color, ox: Float, w: Float, dim: Color) {
            drawRect(bg, Offset(ox, o.y + dp(1f)), androidx.compose.ui.geometry.Size(w, sz.height - dp(2f)))
            drawRect(Color(0xFF3A86FF), Offset(ox, o.y + dp(1f)), androidx.compose.ui.geometry.Size(w, sz.height * 0.14f))
            for (i in 0 until 3) drawRoundRect(dim, Offset(ox + w * 0.14f, o.y + sz.height * (0.3f + i * 0.2f)), androidx.compose.ui.geometry.Size(w * 0.72f, sz.height * 0.09f), androidx.compose.ui.geometry.CornerRadius(dp(2.5f)))
        }
        when (t) {
            LaunchpadTheme.Dark -> panel(LpDarkBg, o.x + dp(1f), sz.width - dp(2f), Color(0x55FFFFFF))
            LaunchpadTheme.Light -> panel(LpLightBg, o.x + dp(1f), sz.width - dp(2f), Color(0x331B1E29))
            LaunchpadTheme.System -> { panel(LpDarkBg, o.x + dp(1f), half - dp(1f), Color(0x55FFFFFF)); panel(LpLightBg, o.x + half, half - dp(1f), Color(0x331B1E29)) }
        }
        drawRoundRect(ink.soft, o, sz, androidx.compose.ui.geometry.CornerRadius(dp(5f)), style = Stroke(dp(1.5f)))
    }

    private fun widgetsSlotArt(s: WidgetsSlot): VisualArt = { ink ->
        val order = PanelSections.order(true, s)
        val n = order.size
        val w = 0.78f / n
        for ((i, v) in order.withIndex()) rect(if (v == HubView.Widgets) ink.accent else ink.soft, 0.11f + i * w + 0.01f, 0.3f, w - 0.02f, 0.12f, 0.5f)
        rect(ink.faint, 0.11f, 0.52f, 0.78f, 0.12f, 0.3f); rect(ink.faint, 0.11f, 0.7f, 0.78f, 0.12f, 0.3f)
    }

    private fun defaultViewArt(c: DefaultViewChoice): VisualArt = { ink ->
        val box = phoneFrame(ink)
        val cat = c.category
        if (cat != null) {
            // All, narrowed to one category: a tag on top of the list
            val col = when (cat) { CommsCategory.Messages -> Color(0xFF3A86FF); CommsCategory.Notifications -> Color(0xFFFFB703); CommsCategory.Alerts -> Color(0xFFFF5252) }
            inBox(box, col, 0.14f, 0.12f, 0.4f, 0.1f, 0.5f)
            for (i in 0 until 3) { inBox(box, ink.soft, 0.14f, 0.32f + i * 0.2f, 0.72f, 0.12f, 0.3f); dotIn(box, col, 0.22f, 0.38f + i * 0.2f, 1.6f) }
        } else when (c.view) {
            HubView.All -> for (i in 0 until 4) inBox(box, ink.soft, 0.14f, 0.14f + i * 0.2f, 0.72f, 0.12f, 0.3f)
            HubView.Unread -> for (i in 0 until 4) { inBox(box, if (i < 2) ink.strong else ink.faint, 0.14f, 0.14f + i * 0.2f, 0.72f, 0.12f, 0.3f); if (i < 2) dotIn(box, ink.accent, 0.22f, 0.2f + i * 0.2f, 2f) }
            HubView.Read -> for (i in 0 until 4) { inBox(box, ink.faint, 0.14f, 0.14f + i * 0.2f, 0.72f, 0.12f, 0.3f); line(ink.soft, 0.4f, 0.2f + i * 0.2f, 0.6f, 0.2f + i * 0.2f, 1.2f) }
            HubView.Files -> for (i in 0 until 2) for (j in 0 until 2) { inBox(box, ink.soft, 0.16f + j * 0.38f, 0.18f + i * 0.38f, 0.3f, 0.3f, 0.15f); inBox(box, ink.faint, 0.2f + j * 0.38f, 0.24f + i * 0.38f, 0.22f, 0.04f, 0.5f) }
            HubView.Timeline -> {
                line(ink.soft, 0.3f, 0.12f, 0.3f, 0.88f, 1.5f)
                for (i in 0 until 4) { dotIn(box, ink.strong, 0.3f, 0.2f + i * 0.2f, 2.2f); inBox(box, ink.faint, 0.42f, 0.17f + i * 0.2f, 0.42f, 0.07f, 0.5f) }
            }
            HubView.Widgets -> { inBox(box, ink.soft, 0.14f, 0.14f, 0.72f, 0.32f, 0.15f); inBox(box, ink.strong, 0.14f, 0.54f, 0.34f, 0.3f, 0.15f); inBox(box, ink.soft, 0.52f, 0.54f, 0.34f, 0.3f, 0.15f) }
        }
    }
}
