package com.bos.sphere.feature.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.L10n

/** Edits of one display's profile (EXT-09). Pure, so the choices and their effect are tested without a screen. */
object ProfileEdits {
    /** null means "use the display's own density". */
    val densityChoices: List<Int?> = listOf(null, 120, 160, 213, 240, 320)

    fun densityLabel(dpi: Int?): String = if (dpi == null) "Automatic" else "$dpi dpi"

    fun withDensity(p: DesktopProfile, dpi: Int?) = p.copy(densityOverrideDpi = dpi?.takeIf { it > 0 })
    fun withDockMode(p: DesktopProfile, mode: DockMode) = p.copy(dockMode = mode)

    /** What the desktop layout looks like with this profile on a window, for the live summary line. */
    fun summary(p: DesktopProfile, widthPx: Int, heightPx: Int, densityDpi: Int): String {
        val l = DesktopMetrics.compute(widthPx, heightPx, densityDpi, dockMode = p.dockMode, densityOverrideDpi = p.densityOverrideDpi)
        return "${l.sizeClass.name}, ${l.columns} x ${l.rows}"
    }
}

/** Density and dock position of one display; changes apply and are stored per display at once (EXT-09). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DisplayProfileEditor(name: String, profile: DesktopProfile, onChange: (DesktopProfile) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
        Text(L10n.tr("Dock position"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DockMode.entries.forEach { m -> Pill(L10n.tr(m.label), profile.dockMode == m) { onChange(ProfileEdits.withDockMode(profile, m)) } }
        }
        Text(L10n.tr("Density"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ProfileEdits.densityChoices.forEach { d -> Pill(L10n.tr(ProfileEdits.densityLabel(d)), profile.densityOverrideDpi == d) { onChange(ProfileEdits.withDensity(profile, d)) } }
        }
    }
}

@Composable
internal fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Text(
        label, color = if (selected) c.onPrimary else c.onSurface, fontSize = 13.sp,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) c.primary else c.surfaceVariant)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp).semantics { role = Role.RadioButton },
    )
}
