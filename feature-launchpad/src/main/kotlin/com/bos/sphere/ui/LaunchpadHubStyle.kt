package com.bos.sphere.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.design.L10n
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Wave 25 (STR-02): measures and colours of the hub-style list, kept in one place so rows, tests and the hub views agree. */
internal object HubStyle {
    /** Wave 27: no stripe and no compose button any more (the Comms mock-ups show neither); the badge is the 56 dp app icon. */
    const val BADGE_DP = CommsStyle.ICON_DP
    /** Where the text of a row starts, from the row's leading edge: the padding, the icon and a gap. */
    const val TEXT_START_DP = CommsStyle.H_PAD_DP + BADGE_DP + CommsStyle.TEXT_GAP_DP

    /** The section header of a day section: "Thu 20 Aug 2015". Other sections keep their own title. */
    fun dayHeader(sectionKey: String, fallback: String, locale: Locale = Locale.getDefault()): String {
        if (!sectionKey.startsWith("day:")) return fallback
        val d = runCatching { LocalDate.parse(sectionKey.removePrefix("day:")) }.getOrNull() ?: return fallback
        return DateTimeFormatter.ofPattern("EEE d MMM yyyy", locale).format(d)
    }

    private val known = mapOf(
        "com.whatsapp" to 0xFF25D366, "org.telegram.messenger" to 0xFF2AABEE, "com.google.android.gm" to 0xFFD93025,
        "com.google.android.apps.messaging" to 0xFF1A73E8, "com.slack" to 0xFFE01E5A, "com.twitter.android" to 0xFF1DA1F2,
        "com.instagram.android" to 0xFFC13584, "com.pinterest" to 0xFFE60023, "com.google.android.calendar" to 0xFF34A853,
    )
    private val palette = longArrayOf(0xFF1E88E5, 0xFF43A047, 0xFFE53935, 0xFF8E24AA, 0xFFFB8C00, 0xFF00897B, 0xFF3949AB, 0xFFD81B60, 0xFF6D4C41, 0xFF546E7A)

    /** A stable colour per source app: a known brand colour, else one of ten hues chosen by the package name. */
    fun stripeFor(packageName: String): Color {
        if (packageName.isBlank()) return Color(0xFF9E9E9E)
        known[packageName]?.let { return Color(it) }
        return Color(palette[(packageName.hashCode() and 0x7fffffff) % palette.size])
    }
}
