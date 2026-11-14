package com.bos.sphere.feature.grid

import android.content.Context
import android.os.Build
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.capabilities.CapState
import com.bos.sphere.core.capabilities.CapabilityEvaluator
import com.bos.sphere.core.capabilities.CapabilityRegistry
import com.bos.sphere.core.catalog.AppCatalog
import com.bos.sphere.core.catalog.DrawerApp
import com.bos.sphere.core.catalog.DrawerModel
import com.bos.sphere.core.catalog.DrawerTab
import com.bos.sphere.core.catalog.ProfileInfo
import com.bos.sphere.core.catalog.ProfileKind
import com.bos.sphere.core.catalog.ProfilePolicy
import com.bos.sphere.core.catalog.TagStore
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/** HOM-05 / HOM-06: the drawer's tab, type-to-filter text and tag filter, as state the drawer reads. */
class DrawerFilterState {
    var tab by mutableStateOf(DrawerTab.Personal)
    var filter by mutableStateOf("")
    var tag by mutableStateOf<String?>(null)
}

@Composable
fun rememberDrawerFilter(): DrawerFilterState = remember { DrawerFilterState() }

/** Profiles visible now, gated by API level and the private-space capability (never a device list). */
internal fun visibleProfiles(context: Context, catalog: AppCatalog): List<ProfileInfo> {
    val privateCapable = runCatching {
        CapabilityEvaluator.evaluate(CapabilityRegistry(context).snapshot()).firstOrNull { it.id == "private_space" }?.state == CapState.On
    }.getOrDefault(false)
    return ProfilePolicy.visibleProfiles(catalog.profiles().map { it.second }, Build.VERSION.SDK_INT, privateCapable)
}

/**
 * Applies tab, filter and tag to [apps], keeping the host's order (the drawer's own sort still
 * decides sections). Apps of a profile that is not visible (locked private space) are left out.
 */
@Composable
fun rememberFilteredApps(apps: List<AppEntry>, state: DrawerFilterState): List<AppEntry> {
    val context = LocalContext.current
    val catalog = remember(context) { AppCatalog(context) }
    val tags by remember(context) { TagStore(context) }.tags.collectAsState()
    val kinds = remember(apps) { apps.map { it.user }.distinct().associateWith { catalog.kindOf(it) } }
    val visible = remember(apps) { visibleProfiles(context, catalog) }
    val allowed = remember(visible) { visible.map { it.kind }.toSet() + ProfileKind.Personal + ProfileKind.Other }
    return remember(apps, state.tab, state.filter, state.tag, tags, kinds, allowed) {
        val model = apps.filter { (kinds[it.user] ?: ProfileKind.Personal) in allowed }
            .map { DrawerApp(it.key, it.label, kinds[it.user] ?: ProfileKind.Personal) }
        val keep = DrawerModel.build(model, state.tab, state.filter, tags = tags, tag = state.tag).flat.map { it.key }.toHashSet()
        apps.filter { it.key in keep }
    }
}

/** Tabs (personal / work / private), the work pause switch, tag chips and the type-to-filter field. */
@Composable
fun DrawerFilterBar(state: DrawerFilterState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val catalog = remember(context) { AppCatalog(context) }
    val tags by remember(context) { TagStore(context) }.tags.collectAsState()
    val profiles = remember { visibleProfiles(context, catalog) }
    val tabs = ProfilePolicy.tabs(profiles)
    val allTags = remember(tags) { com.bos.sphere.core.catalog.TagCodec.allTags(tags) }
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        BasicTextField(
            value = state.filter, onValueChange = { state.filter = it }, singleLine = true,
            textStyle = TextStyle(color = HyleColors.InkBright, fontSize = 14.sp),
            cursorBrush = SolidColor(HyleColors.InkBright),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF)).padding(horizontal = 12.dp, vertical = 10.dp),
            decorationBox = { inner -> Box { if (state.filter.isEmpty()) Text(L10n.tr("Type to filter apps"), color = HyleColors.InkDim, fontSize = 14.sp); inner() } },
        )
        if (tabs.size > 1 || allTags.isNotEmpty()) {
            Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                if (tabs.size > 1) for (t in tabs) Chip(L10n.tr(t.label), state.tab == t) { state.tab = t }
                if (state.tab == DrawerTab.Work) {
                    val work = catalog.profiles().firstOrNull { it.second.kind == ProfileKind.Work }
                    if (work != null) {
                        var paused by remember { mutableStateOf(work.second.quiet) }
                        Chip(L10n.tr(if (paused) "Resume work apps" else "Pause work apps"), false) {
                            if (catalog.setPaused(work.first, !paused)) paused = !paused
                        }
                    }
                }
                for (tg in allTags) Chip("#$tg", state.tag == tg) { state.tag = if (state.tag == tg) null else tg }
            }
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text, color = if (on) HyleColors.InkBright else HyleColors.InkDim, fontSize = 12.sp,
        modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(50))
            .background(if (on) Color(0x26FFFFFF) else Color(0x14FFFFFF)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** HOM-05: add or remove user tags of one app. */
@Composable
fun TagDialog(app: AppEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { TagStore(context) }
    val tags by store.tags.collectAsState()
    var draft by remember { mutableStateOf("") }
    val mine = tags[app.key].orEmpty()
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.foundation.layout.Column(
            Modifier.clip(RoundedCornerShape(20.dp)).background(HyleColors.Surface).padding(16.dp),
        ) {
            Text(L10n.tr("Tags for") + " " + app.label, color = HyleColors.InkBright, fontSize = 16.sp)
            Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState())) {
                for (t in com.bos.sphere.core.catalog.TagCodec.allTags(tags)) Chip(t, t in mine) { store.toggle(app.key, t) }
            }
            BasicTextField(
                value = draft, onValueChange = { draft = it }, singleLine = true,
                textStyle = TextStyle(color = HyleColors.InkBright, fontSize = 14.sp), cursorBrush = SolidColor(HyleColors.InkBright),
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF)).padding(12.dp),
                decorationBox = { inner -> Box { if (draft.isEmpty()) Text(L10n.tr("New tag"), color = HyleColors.InkDim, fontSize = 14.sp); inner() } },
            )
            Row(Modifier.padding(top = 10.dp)) {
                Chip(L10n.tr("Add"), false) { if (draft.isNotBlank()) { if (draft.trim() !in mine) store.toggle(app.key, draft); draft = "" } }
                Chip(L10n.tr("Done"), false, onDismiss)
            }
        }
    }
}
